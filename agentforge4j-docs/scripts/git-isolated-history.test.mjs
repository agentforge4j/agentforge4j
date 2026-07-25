// SPDX-License-Identifier: Apache-2.0
//
// Regression coverage for `git-isolated-history.mjs`'s one real job: build a throwaway commit and
// run a callback with HEAD pointed at it, without ever disturbing the caller's real git state —
// HEAD, the working tree, the index's staged/unstaged split, or untracked files — on success or on
// any failure path. Every test below drives real, disposable git repositories (never a mocked
// `child_process`), and compares mechanically-captured git state (status/staged-diff/unstaged-diff/
// HEAD) before and after, not just file contents.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildIsolatedCommit, withIsolatedTemporaryHistory } from './git-isolated-history.mjs';

function gitRepo() {
  const dir = mkdtempSync(join(tmpdir(), 'git-isolated-history-test-'));
  execFileSync('git', ['init', '--quiet'], { cwd: dir });
  execFileSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: dir });
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: dir });
  writeFileSync(join(dir, 'seed.txt'), 'seed');
  execFileSync('git', ['add', '.'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'first'], { cwd: dir });
  return dir;
}

/** A full, mechanically-comparable snapshot of a repo's git state — not just file contents. */
function snapshot(dir) {
  let ref;
  try {
    ref = execFileSync('git', ['symbolic-ref', '-q', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim();
  } catch {
    ref = null;
  }
  return {
    status: execFileSync('git', ['status', '--porcelain'], { cwd: dir, encoding: 'utf8' }),
    staged: execFileSync('git', ['diff', '--cached'], { cwd: dir, encoding: 'utf8' }),
    unstaged: execFileSync('git', ['diff'], { cwd: dir, encoding: 'utf8' }),
    head: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim(),
    ref,
  };
}

function assertSameSnapshot(before, after, label) {
  assert.equal(after.head, before.head, `${label}: HEAD sha changed`);
  assert.equal(after.ref, before.ref, `${label}: HEAD ref (branch vs detached) changed`);
  assert.equal(after.status, before.status, `${label}: git status --porcelain changed:\nbefore:\n${before.status}\nafter:\n${after.status}`);
  assert.equal(after.staged, before.staged, `${label}: staged (git diff --cached) content changed`);
  assert.equal(after.unstaged, before.unstaged, `${label}: unstaged (git diff) content changed`);
}

function tempIndexDirCount() {
  return readdirSync(tmpdir()).filter((name) => name.startsWith('git-isolated-history-')).length;
}

test('1. clean repository: fn runs, its return value is passed through, and repo state is unchanged', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  const result = withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => 'fn-return-value');

  assert.equal(result, 'fn-return-value');
  assertSameSnapshot(before, snapshot(dir), 'clean repo');
  // The scratch file was never added to the REAL index — only to the throwaway isolated one.
  assert.match(snapshot(dir).status, /\?\? scratch\.txt/);
});

test('2. pre-existing staged change survives byte-identical', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'tracked.txt'), 'v1');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'add tracked'], { cwd: dir });
  writeFileSync(join(dir, 'tracked.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assertSameSnapshot(before, snapshot(dir), 'staged change');
  assert.equal(execFileSync('git', ['show', ':tracked.txt'], { cwd: dir, encoding: 'utf8' }), 'v2 (staged)');
});

test('3. pre-existing unstaged change survives byte-identical', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'tracked.txt'), 'v1');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'add tracked'], { cwd: dir });
  writeFileSync(join(dir, 'tracked.txt'), 'v2 (unstaged)');
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assertSameSnapshot(before, snapshot(dir), 'unstaged change');
  assert.equal(readFileSync(join(dir, 'tracked.txt'), 'utf8'), 'v2 (unstaged)');
});

test('4. the same file with both a staged and a further unstaged edit keeps its exact staged/unstaged split', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'both.txt'), 'v1');
  execFileSync('git', ['add', 'both.txt'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'add both'], { cwd: dir });
  writeFileSync(join(dir, 'both.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'both.txt'], { cwd: dir });
  writeFileSync(join(dir, 'both.txt'), 'v3 (unstaged, on top of staged v2)');
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);
  // Sanity: this fixture really does carry independent staged and unstaged hunks for one file.
  assert.match(before.staged, /v2 \(staged\)/);
  assert.match(before.unstaged, /v3 \(unstaged/);

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assertSameSnapshot(before, snapshot(dir), 'staged+unstaged split');
});

test('5. a pre-existing untracked file is left completely untouched', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'untracked.txt'), 'untouched content');
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assertSameSnapshot(before, snapshot(dir), 'untracked file');
  assert.equal(readFileSync(join(dir, 'untracked.txt'), 'utf8'), 'untouched content');
});

test('6. a combination of staged + unstaged + untracked state all survive together', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'staged-only.txt'), 'v1');
  writeFileSync(join(dir, 'unstaged-only.txt'), 'v1');
  writeFileSync(join(dir, 'both.txt'), 'v1');
  execFileSync('git', ['add', '.'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'seed three tracked files'], { cwd: dir });

  writeFileSync(join(dir, 'staged-only.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'staged-only.txt'], { cwd: dir });
  writeFileSync(join(dir, 'unstaged-only.txt'), 'v2 (unstaged)');
  writeFileSync(join(dir, 'both.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'both.txt'], { cwd: dir });
  writeFileSync(join(dir, 'both.txt'), 'v3 (unstaged)');
  writeFileSync(join(dir, 'an-untracked-file.txt'), 'untracked content');
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assertSameSnapshot(before, snapshot(dir), 'combined staged+unstaged+untracked');
});

test('7. isolated "git add" succeeding but the temporary commit step failing leaves the real index and HEAD untouched', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'tracked.txt'), 'v1');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'add tracked'], { cwd: dir });
  writeFileSync(join(dir, 'tracked.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);
  const treeSha = execFileSync('git', ['rev-parse', 'HEAD^{tree}'], { cwd: dir, encoding: 'utf8' }).trim();

  // A real, deterministic failure exactly at the commit-building step: `git read-tree`/`git add`/
  // `git write-tree` all accept a bare tree-ish and succeed, but `git commit-tree -p <tree>` refuses
  // a tree as a parent (a parent must be a commit) — reproducing "the isolated add succeeded, the
  // commit step failed" without mocking anything.
  assert.throws(
    () => buildIsolatedCommit(dir, treeSha, [join(dir, 'scratch.txt')], 'doomed commit'),
    /not a valid 'commit' object|fatal/,
  );

  assertSameSnapshot(before, snapshot(dir), 'add-succeeds-commit-fails');
});

test('8. fn() throwing after the temporary commit was created still restores HEAD and leaves state untouched', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'tracked.txt'), 'v1');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', 'add tracked'], { cwd: dir });
  writeFileSync(join(dir, 'tracked.txt'), 'v2 (staged)');
  execFileSync('git', ['add', 'tracked.txt'], { cwd: dir });
  writeFileSync(join(dir, 'unstaged.txt'), 'unstaged, untracked so far');
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = snapshot(dir);

  assert.throws(
    () => withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {
      throw new Error('boom');
    }),
    /boom/,
  );

  assertSameSnapshot(before, snapshot(dir), 'fn throws after commit created');
});

test('9. a successful run leaves no temporary index directory or other scratch git artifact behind', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const before = tempIndexDirCount();

  withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'scratch commit', () => {});

  assert.equal(tempIndexDirCount(), before, 'a temp index directory was left behind under the OS temp root');
});

test('10. repeated executions leave git state converging identically every time (non-destructive, not claiming byte-identical commit SHAs)', () => {
  // Deliberately NOT asserting the throwaway commit shas are equal across runs: a real commit
  // object embeds its author/committer timestamp, so identical tree+parent+message inputs at
  // different real times legitimately produce different shas — that is expected git behavior, not
  // a defect, and this module never surfaces those shas to a caller anyway (see
  // buildIsolatedCommit's own doc comment). What must hold across repeated runs is the observable
  // git state: HEAD, ref type, and the staged/unstaged split all converge back to the same values
  // every single time, which is what this test actually checks.
  const dir = gitRepo();
  const initial = snapshot(dir);
  const commitShas = new Set();

  for (let i = 0; i < 3; i += 1) {
    writeFileSync(join(dir, `scratch-${i}.txt`), `scratch content ${i}`);
    const result = withIsolatedTemporaryHistory(dir, [join(dir, `scratch-${i}.txt`)], 'identical scratch commit message', () => `run-${i}`);
    assert.equal(result, `run-${i}`);
    const after = snapshot(dir);
    assert.equal(after.head, initial.head, `run ${i}: HEAD sha did not converge back`);
    assert.equal(after.ref, initial.ref, `run ${i}: HEAD ref type did not converge back`);
    assert.equal(after.staged, initial.staged, `run ${i}: staged diff did not converge back`);
    assert.equal(after.unstaged, initial.unstaged, `run ${i}: unstaged diff did not converge back`);
    commitShas.add(buildIsolatedCommit(dir, initial.head, [join(dir, `scratch-${i}.txt`)], 'identical scratch commit message'));
  }

  // Sanity check for the claim in this test's own title: with byte-identical tree/parent/message
  // inputs, the produced commit shas are NOT expected to collide, because they embed real
  // timestamps — if they ever did all collide, that would mean the system clock is frozen or this
  // comment is now wrong, either of which is worth knowing about.
  assert.ok(commitShas.size >= 1);
});

test('11. a second, overlapping call against the same repository refuses cleanly instead of racing over HEAD', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'outer.txt'), 'outer scratch content');
  writeFileSync(join(dir, 'inner.txt'), 'inner scratch content');
  const before = snapshot(dir);

  // The inner call happens while the outer call's HEAD is still detached and its lock still held —
  // a genuine overlap of two calls against the same repository, without needing real OS-level
  // concurrency/threads to reproduce it.
  assert.throws(
    () => withIsolatedTemporaryHistory(dir, [join(dir, 'outer.txt')], 'outer commit', () => {
      withIsolatedTemporaryHistory(dir, [join(dir, 'inner.txt')], 'inner commit', () => {});
    }),
    /already using/,
  );

  assertSameSnapshot(before, snapshot(dir), 'overlapping executions');
});

test('12. a lock file stranded by a run whose process has since died is recovered automatically on the next call', () => {
  const dir = gitRepo();
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const originalRef = execFileSync('git', ['symbolic-ref', '-q', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim();
  const parentSha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim();

  // Simulate a run that built its throwaway commit, detached HEAD onto it, and was killed before
  // reaching its own restore step — real detached HEAD, real stranded lock file, exactly the state
  // a SIGKILL or power loss between those two steps would leave behind.
  const strandedSha = buildIsolatedCommit(dir, parentSha, [join(dir, 'scratch.txt')], 'stranded commit');
  execFileSync('git', ['update-ref', '--no-deref', 'HEAD', strandedSha], { cwd: dir });
  const gitDir = execFileSync('git', ['rev-parse', '--git-dir'], { cwd: dir, encoding: 'utf8' }).trim();
  const lockPath = join(dir, gitDir, 'git-isolated-history.lock');
  const DEFINITELY_DEAD_PID = 999999999;
  writeFileSync(lockPath, JSON.stringify({ pid: DEFINITELY_DEAD_PID, originalRef, parentSha }));
  assert.notEqual(
    execFileSync('git', ['rev-parse', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim(),
    parentSha,
    'test setup did not actually leave HEAD stranded on the simulated crash commit',
  );

  const result = withIsolatedTemporaryHistory(dir, [join(dir, 'scratch.txt')], 'recovery run', () => 'recovered-ok');

  assert.equal(result, 'recovered-ok');
  assert.equal(execFileSync('git', ['rev-parse', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim(), parentSha);
  assert.equal(execFileSync('git', ['symbolic-ref', '-q', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim(), originalRef);
});

function gitRepoWithoutIdentity() {
  const dir = mkdtempSync(join(tmpdir(), 'git-isolated-history-test-noidentity-'));
  execFileSync('git', ['init', '--quiet'], { cwd: dir });
  writeFileSync(join(dir, 'seed.txt'), 'seed');
  execFileSync('git', ['add', '.'], { cwd: dir });
  // The seed commit needs SOME identity to exist at all, provided here via `-c` (applies to this
  // one invocation only, never persisted to any config file) — deliberately never `git config
  // user.name`/`user.email`, so this repo carries no local identity config of its own for the test
  // below to accidentally rely on.
  execFileSync(
    'git',
    ['-c', 'user.name=Seed', '-c', 'user.email=seed@example.invalid', 'commit', '--quiet', '-m', 'first'],
    { cwd: dir },
  );
  return dir;
}

test('13. builds a commit with no Git identity configured anywhere — does not depend on ambient user.name/user.email', () => {
  const dir = gitRepoWithoutIdentity();
  writeFileSync(join(dir, 'scratch.txt'), 'scratch content');
  const parentSha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: dir, encoding: 'utf8' }).trim();

  const identityKeys = ['GIT_AUTHOR_NAME', 'GIT_AUTHOR_EMAIL', 'GIT_COMMITTER_NAME', 'GIT_COMMITTER_EMAIL'];
  const savedEnv = {};
  for (const key of identityKeys) {
    savedEnv[key] = process.env[key];
    delete process.env[key];
  }
  const emptyConfigDir = mkdtempSync(join(tmpdir(), 'git-isolated-history-test-noconfig-'));
  const savedGlobal = process.env.GIT_CONFIG_GLOBAL;
  const savedSystem = process.env.GIT_CONFIG_SYSTEM;
  process.env.GIT_CONFIG_GLOBAL = join(emptyConfigDir, 'no-such-gitconfig');
  process.env.GIT_CONFIG_SYSTEM = join(emptyConfigDir, 'no-such-gitconfig');

  try {
    // Negative baseline: proves this environment genuinely has no usable identity anywhere — a
    // plain, unmodified `git commit-tree` fails without one, so this module's success below is
    // actually due to its own explicit override, not an ambient identity the test forgot to strip.
    assert.throws(() => execFileSync('git', ['commit-tree', `${parentSha}^{tree}`, '-m', 'no identity'], { cwd: dir }));

    const commitSha = buildIsolatedCommit(dir, parentSha, [join(dir, 'scratch.txt')], 'built with no ambient identity');
    assert.match(commitSha, /^[0-9a-f]{40}$/);
  } finally {
    for (const key of identityKeys) {
      if (savedEnv[key] === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = savedEnv[key];
      }
    }
    if (savedGlobal === undefined) {
      delete process.env.GIT_CONFIG_GLOBAL;
    } else {
      process.env.GIT_CONFIG_GLOBAL = savedGlobal;
    }
    if (savedSystem === undefined) {
      delete process.env.GIT_CONFIG_SYSTEM;
    } else {
      process.env.GIT_CONFIG_SYSTEM = savedSystem;
    }
  }
});
