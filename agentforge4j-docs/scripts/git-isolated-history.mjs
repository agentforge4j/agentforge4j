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
// observes it, and the real index is never opened at all.

import {execFileSync} from 'node:child_process';
import {mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

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
      {cwd: repoRoot, encoding: 'utf8'},
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
 * @param {string} repoRoot
 * @param {string[]} paths see {@link buildIsolatedCommit}
 * @param {string} message
 * @param {() => any} fn
 * @returns {any} fn's return value
 */
export function withIsolatedTemporaryHistory(repoRoot, paths, message, fn) {
  const parentSha = execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repoRoot, encoding: 'utf8'}).trim();
  let originalRef = null;
  try {
    originalRef = execFileSync('git', ['symbolic-ref', '-q', 'HEAD'], {cwd: repoRoot, encoding: 'utf8'}).trim();
  } catch {
    originalRef = null; // HEAD was already detached before this call — restored back to parentSha below.
  }

  const commitSha = buildIsolatedCommit(repoRoot, parentSha, paths, message);
  execFileSync('git', ['update-ref', '--no-deref', 'HEAD', commitSha], {cwd: repoRoot});
  try {
    return fn();
  } finally {
    if (originalRef) {
      execFileSync('git', ['symbolic-ref', 'HEAD', originalRef], {cwd: repoRoot});
    } else {
      execFileSync('git', ['update-ref', '--no-deref', 'HEAD', parentSha], {cwd: repoRoot});
    }
  }
}
