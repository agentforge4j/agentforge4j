// SPDX-License-Identifier: Apache-2.0
//
// Runs a callback with HEAD temporarily detached onto a real, throwaway commit built from specific
// paths' current on-disk content — giving a tool that shells out to `git log` (e.g. Docusaurus's
// `experimental_vcs: 'git-ad-hoc'` lastmod strategy) real per-file history to read for content that
// exists on disk but nowhere in the caller's actual commit history yet (see archive-scratch.mjs, the
// one current consumer). The caller's real index, staged/unstaged split, and untracked files are
// never read or written: the throwaway commit is built entirely through an isolated temporary index
// (`GIT_INDEX_FILE`), and HEAD is moved with `update-ref`/`symbolic-ref` alone — neither of which
// touches the index or working tree. HEAD is restored to exactly where it started once `fn` returns
// or throws.
//
// Deliberately never uses `git add`/`git commit`/`git reset` against the repository's real index:
// those porcelain commands operate on (and can silently absorb unrelated pre-existing staged content
// from, or be interrupted mid-way by a failing commit hook that still leaves the real index
// mutated) whatever the real index currently holds. `git read-tree`/`write-tree`/`commit-tree`
// (plumbing, no hooks) build the throwaway commit's tree from `parentSha`'s own committed content
// plus only the given `paths`' current working-tree content, entirely inside a separate index file
// this module creates and deletes itself — nothing else on the machine ever contends for or
// observes it, and the real index is never opened at all. `commit-tree` is given an explicit,
// synthetic author/committer identity (below) rather than relying on ambient `user.name`/
// `user.email` config, since a commit object cannot exist without one and a perfectly clean
// checkout (fresh machine, minimal CI image) may not have either configured.
//
// Two HEAD-moving calls (`withIsolatedTemporaryHistory` running twice concurrently against the same
// repository, or one that is killed — SIGKILL, power loss — between detaching HEAD and restoring it)
// would otherwise leave the repository's HEAD corrupted or permanently detached. A lock file under
// the repository's own git dir (never the working tree, so it never appears in `git status`) both
// serializes calls against the same repo (a second, still-live call fails fast rather than racing)
// and records what a run intends to restore, so if a run's process is no longer alive when the next
// call starts, that next call replays the stranded run's restore step before doing anything else —
// self-healing across even an unrecoverable kill, not merely a caught signal.
//
// That record is only useful if it OUTLIVES a failure to restore. The lock is therefore removed only
// once HEAD is provably back where it started: a run whose own `restoreHead` fails (a concurrent git
// process holding `HEAD.lock`, a read-only git dir, a full disk) deliberately leaves the lock behind
// and fails loudly, so the next call recovers it exactly as it would after a kill. Removing it
// unconditionally would delete the only recovery payload at precisely the moment it is needed, and
// the next run would then read the throwaway commit as its own "original" HEAD — cementing the
// detached HEAD instead of healing it.

import {execFileSync} from 'node:child_process';
import {mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {isAbsolute, join} from 'node:path';

const LOCK_FILENAME = 'git-isolated-history.lock';

// `commit-tree` cannot create a commit object without SOME author/committer identity; this module's
// commits are throwaway (never merged, never pushed, and unreferenced by any branch or tag once HEAD
// is restored — they do stay reachable through HEAD's own reflog until normal git housekeeping
// expires it, which is why both HEAD moves below carry an explicit reflog message rather than a
// blank one), so a fixed synthetic identity — never depending on `user.name`/`user.email` being
// configured anywhere — is exactly right, and keeps this module usable on a machine with no git
// identity set up at all.
const SYNTHETIC_IDENTITY_ENV = {
  GIT_AUTHOR_NAME: 'git-isolated-history',
  GIT_AUTHOR_EMAIL: 'git-isolated-history@localhost',
  GIT_COMMITTER_NAME: 'git-isolated-history',
  GIT_COMMITTER_EMAIL: 'git-isolated-history@localhost',
};

function lockPathFor(repoRoot) {
  const gitDir = execFileSync('git', ['rev-parse', '--git-dir'], {cwd: repoRoot, encoding: 'utf8'}).trim();
  const absoluteGitDir = isAbsolute(gitDir) ? gitDir : join(repoRoot, gitDir);
  return join(absoluteGitDir, LOCK_FILENAME);
}

function isProcessAlive(pid) {
  try {
    // Signal 0 sends nothing; it only probes whether the process (and its permission to be
    // signalled) exists.
    process.kill(pid, 0);
    return true;
  } catch (err) {
    // EPERM: the process exists but is owned by someone else — still alive, just not ours to see.
    return err.code === 'EPERM';
  }
}

const DETACH_REFLOG_MESSAGE = 'git-isolated-history: detach onto throwaway commit';
const RESTORE_REFLOG_MESSAGE = 'git-isolated-history: restore original HEAD';

function restoreHead(repoRoot, originalRef, parentSha) {
  if (originalRef) {
    execFileSync('git', ['symbolic-ref', '-m', RESTORE_REFLOG_MESSAGE, 'HEAD', originalRef], {cwd: repoRoot});
  } else {
    execFileSync('git', ['update-ref', '-m', RESTORE_REFLOG_MESSAGE, '--no-deref', 'HEAD', parentSha], {cwd: repoRoot});
  }
}

/**
 * Claims the per-repository lock with a bare placeholder (just this process's pid — the actual
 * restore payload is filled in by {@link recordRestoreStep} once it is known), recovering a
 * previous run's stranded HEAD first if that run's process is no longer alive. Throws if a
 * still-live run already holds the lock, or if the lock file exists but is unreadable (a narrow
 * crash window this refuses to guess through, rather than risking a wrong recovery).
 *
 * Deliberately done BEFORE reading the repository's current HEAD anywhere else in this module: a
 * stale lock's recovery mutates HEAD, so anything that reads "current HEAD" to decide what to
 * restore back to later must run strictly after this, never before it.
 *
 * @param {string} repoRoot
 * @returns {string} the lock file path, to be updated by {@link recordRestoreStep} and removed once
 *          this run finishes
 */
function claimLock(repoRoot) {
  const lockPath = lockPathFor(repoRoot);
  for (;;) {
    try {
      writeFileSync(lockPath, JSON.stringify({pid: process.pid}), {flag: 'wx'});
      return lockPath;
    } catch (err) {
      if (err.code !== 'EEXIST') {
        throw err;
      }
      let stale;
      try {
        stale = JSON.parse(readFileSync(lockPath, 'utf8'));
      } catch {
        throw new Error(
          `git-isolated-history: ${lockPath} exists but is not readable/parseable — refusing to guess whether ` +
            `it is safe to remove; confirm no other run is using ${repoRoot} and remove it manually, then retry`,
        );
      }
      if (isProcessAlive(stale.pid)) {
        // A pid is not a durable identity: once the recorded process exits, the operating system is
        // free to hand that same number to something entirely unrelated, and this probe would then
        // report the lock as live forever. So this branch says exactly where the lock is and how to
        // clear it, rather than leaving the caller to discover a file inside the git dir on their own.
        throw new Error(
          `git-isolated-history: ${lockPath} says a run (pid ${stale.pid}) is still using ${repoRoot}, so this ` +
            'call refuses to run concurrently against the same repository. If no such run is actually in ' +
            `progress, that pid has been reused by an unrelated process: confirm nothing is using ${repoRoot}, ` +
            'then delete that lock file and retry.',
        );
      }
      // That run's process is gone without releasing the lock (killed, crashed, or the machine
      // lost power). If it never got as far as recording a restore step, it also never touched
      // HEAD — nothing to recover, just reclaim. Otherwise replay its recorded restore step first,
      // so HEAD never stays stranded past the very next call.
      if (stale.originalRef !== undefined && stale.parentSha !== undefined) {
        restoreHead(repoRoot, stale.originalRef, stale.parentSha);
      }
      rmSync(lockPath, {force: true});
      // Loop back and retry acquisition now that the stale lock is gone.
    }
  }
}

/** Records this run's own restore step into its already-claimed lock file, once HEAD has been read
 * (safely, after any prior stale lock was already recovered by {@link claimLock}) — so if THIS run
 * is itself killed after this point, the next call can recover it in turn. */
function recordRestoreStep(lockPath, originalRef, parentSha) {
  writeFileSync(lockPath, JSON.stringify({pid: process.pid, originalRef, parentSha}));
}

/**
 * Builds a real commit object parented on `parentSha`, whose tree is `parentSha`'s own tree with
 * `paths`' CURRENT working-tree content added on top — entirely through an isolated temporary index
 * this function creates and always removes again (even on failure). The repository's real index
 * file is never opened or written. Returns the new commit's sha; throws (leaving no trace beyond an
 * unreferenced, harmless loose commit/tree object — the same residue any git plumbing failure
 * leaves) if any step fails, e.g. an invalid `parentSha`.
 *
 * @param {string} repoRoot
 * @param {string} parentSha the commit the throwaway commit is parented on
 * @param {string[]} paths absolute paths to stage into the throwaway commit, on top of whatever
 *        `parentSha` already carries for them
 * @param {string} message
 * @returns {string} the new commit's sha
 */
export function buildIsolatedCommit(repoRoot, parentSha, paths, message) {
  const tempDir = mkdtempSync(join(tmpdir(), 'git-isolated-history-'));
  const tempIndex = join(tempDir, 'index');
  const env = {...process.env, GIT_INDEX_FILE: tempIndex};
  try {
    execFileSync('git', ['read-tree', parentSha], {cwd: repoRoot, env});
    if (paths.length > 0) {
      execFileSync('git', ['add', ...paths], {cwd: repoRoot, env});
    }
    const treeSha = execFileSync('git', ['write-tree'], {cwd: repoRoot, env, encoding: 'utf8'}).trim();
    return execFileSync(
      'git',
      ['commit-tree', treeSha, '-p', parentSha, '-m', message],
      {cwd: repoRoot, encoding: 'utf8', env: {...process.env, ...SYNTHETIC_IDENTITY_ENV}},
    ).trim();
  } finally {
    // Removed unconditionally, success or failure — this temp index never needs to outlive the
    // single commit-build call above, unlike the real index it stands in for.
    rmSync(tempDir, {recursive: true, force: true});
  }
}

/**
 * Detaches HEAD onto a throwaway commit built by {@link buildIsolatedCommit}, runs `fn`, then
 * restores HEAD to exactly where it started — as a symbolic ref back onto the original branch if it
 * was one, or back onto the original commit sha if HEAD was already detached — regardless of whether
 * `fn` returns or throws. Moving HEAD with `update-ref`/`symbolic-ref` alone never touches the index
 * or working tree, so the caller's staged/unstaged split and untracked files are untouched by this
 * call in every case, including a failure partway through building the throwaway commit itself (HEAD
 * is only ever moved once that commit already exists).
 *
 * Serialized per-repository by a lock file (see {@link claimLock}): a second, still-live call
 * against the same repo fails fast instead of racing with this one over HEAD; a call that finds a
 * lock left by a run whose process has since died recovers that run's HEAD first, so a kill between
 * detach and restore heals itself on the next call rather than staying stranded forever.
 *
 * The lock is released only once HEAD is provably back where it started. If `restoreHead` itself
 * fails, the lock — which carries this run's own restore step — is deliberately left in place and
 * this function throws with the manual recovery command, so the stranded HEAD is recoverable by the
 * next call exactly as it would be after a kill. Releasing the lock in that case would destroy the
 * only recovery payload at the one moment it matters.
 *
 * @param {string} repoRoot
 * @param {string[]} paths see {@link buildIsolatedCommit}
 * @param {string} message
 * @param {() => any} fn
 * @returns {any} fn's return value
 */
export function withIsolatedTemporaryHistory(repoRoot, paths, message, fn) {
  // Claimed BEFORE reading HEAD: a stale lock's recovery (inside claimLock) mutates HEAD, so this
  // run's own idea of "current HEAD" below must be read only after any such recovery has already
  // happened, never before it.
  const lockPath = claimLock(repoRoot);
  // Nothing has been moved yet, so at this point "restored" is vacuously true: the lock may be
  // released on any failure before HEAD is actually detached below.
  let headRestored = true;
  try {
    const parentSha = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repoRoot, encoding: 'utf8'}).trim();
    let originalRef = null;
    try {
      originalRef = execFileSync('git', ['symbolic-ref', '-q', 'HEAD'], {cwd: repoRoot, encoding: 'utf8'}).trim();
    } catch {
      originalRef = null; // HEAD was already detached before this call — restored back to parentSha below.
    }
    recordRestoreStep(lockPath, originalRef, parentSha);

    const commitSha = buildIsolatedCommit(repoRoot, parentSha, paths, message);
    execFileSync('git', ['update-ref', '-m', DETACH_REFLOG_MESSAGE, '--no-deref', 'HEAD', commitSha], {cwd: repoRoot});
    headRestored = false;
    try {
      return fn();
    } finally {
      try {
        restoreHead(repoRoot, originalRef, parentSha);
        headRestored = true;
      } catch (err) {
        // Reported instead of `fn`'s own error if both failed: a stranded HEAD is the more urgent of
        // the two, and unlike `fn`'s failure it needs the caller to act on the repository itself.
        throw new Error(
          `git-isolated-history: FAILED to restore HEAD in ${repoRoot} — it is still detached onto the ` +
            `throwaway commit ${commitSha}. ${lockPath} has deliberately been left in place: it records this ` +
            'run\'s restore step, so the next call to this module replays it automatically. To recover by ' +
            `hand instead, run ${originalRef ? `\`git symbolic-ref HEAD ${originalRef}\`` : `\`git update-ref --no-deref HEAD ${parentSha}\``}` +
            ` in ${repoRoot} and then delete that lock file. Cause: ${err.message}`,
          {cause: err},
        );
      }
    }
  } finally {
    if (headRestored) {
      rmSync(lockPath, {force: true});
    }
  }
}
