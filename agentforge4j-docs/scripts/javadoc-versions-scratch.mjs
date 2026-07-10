// SPDX-License-Identifier: Apache-2.0
//
// withTagWorktree proof (design §7/§12). javadoc-versions.test.mjs exercises buildJavadocVersions'
// orchestration with a stub builder — cheap, but it never touches the one part of the real build with
// actual failure modes: checking out a RELEASE TAG into a detached git worktree and guaranteeing its
// cleanup, on success or on failure. This proves that against a real, manufactured tag:
//
//   1. record the pre-state (`git worktree list`);
//   2. create a throwaway tag at HEAD;
//   3. run withTagWorktree for real — assert the worktree exists and contains the real checked-out
//      repo tree while the callback runs, that it is gone immediately after a successful run, and that
//      a THROWING callback (what a failed `mvnw install` looks like) still leaves it removed;
//   4. assert a missing release tag fails BEFORE any worktree is created;
//   5. delete the throwaway tag and assert `git worktree list` is back to the pre-state.
//
// Does not run a real Maven install — the tag-checkout/cleanup lifecycle is what's proven here; the
// Maven install + the tag's own build-javadoc.mjs step is exercised for real only by an actual release
// deploy (see build-javadoc-versions.mjs). Run via `npm run docs:javadoc-versions-scratch`.

import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {existsSync} from 'node:fs';
import {join} from 'node:path';
import {releaseTag, withTagWorktree} from './build-javadoc-versions.mjs';
import {MODULE_ROOT, validateVersion} from './release-paths.mjs';

const REPO_ROOT = join(MODULE_ROOT, '..');
const SCRATCH_VERSION = validateVersion(process.argv[2] || '0.0.0-javadoc-versions-scratch');
const SCRATCH_TAG = releaseTag(SCRATCH_VERSION);
const MISSING_VERSION = '0.0.0-javadoc-versions-scratch-missing';

function git(args) {
  return execFileSync('git', args, {cwd: REPO_ROOT, encoding: 'utf8'});
}

function tagExists(tag) {
  try {
    git(['rev-parse', '--verify', `refs/tags/${tag}`]);
    return true;
  } catch {
    return false;
  }
}

function main() {
  if (tagExists(SCRATCH_TAG)) {
    throw new Error(`javadoc-versions-scratch: tag ${SCRATCH_TAG} already exists — refusing to run`);
  }
  const preWorktrees = git(['worktree', 'list', '--porcelain']);

  git(['tag', SCRATCH_TAG]);
  try {
    // Fails fast — before any worktree is created — when the release tag does not exist.
    assert.throws(
      () =>
        withTagWorktree(MISSING_VERSION, () => {
          throw new Error('withTagWorktree must not invoke the callback for a missing tag');
        }),
      /refs\/tags/,
      'a missing release tag must fail before the callback runs',
    );

    // The real checkout: the worktree exists and contains the actual repo tree while the callback
    // runs, and is gone immediately after a successful run.
    let srcDirDuringRun;
    withTagWorktree(SCRATCH_VERSION, (srcDir) => {
      srcDirDuringRun = srcDir;
      assert.ok(
        existsSync(join(srcDir, 'agentforge4j-docs', 'package.json')),
        'the tagged worktree must contain the real checked-out repo tree',
      );
    });
    assert.ok(!existsSync(srcDirDuringRun), 'the worktree must be removed after a successful run');

    // Cleanup-on-failure: a thrown build error (what a real `mvnw install` failure looks like) must
    // not leak the worktree — this is the failure mode buildFromTag actually depends on avoiding.
    let srcDirOnFailure;
    assert.throws(
      () =>
        withTagWorktree(SCRATCH_VERSION, (srcDir) => {
          srcDirOnFailure = srcDir;
          throw new Error('simulated build failure');
        }),
      /simulated build failure/,
    );
    assert.ok(!existsSync(srcDirOnFailure), 'the worktree must be removed even when the callback throws');

    console.log(
      '[javadoc-versions-scratch] withTagWorktree proven against a real tag: real checkout, ' +
        'cleanup on success, cleanup on failure, fails fast on a missing tag.',
    );
  } finally {
    git(['tag', '-d', SCRATCH_TAG]);
  }

  const postWorktrees = git(['worktree', 'list', '--porcelain']);
  assert.equal(
    postWorktrees,
    preWorktrees,
    'worktree list must be unchanged after revert — the simulation left residue',
  );
  console.log('[javadoc-versions-scratch] reverted cleanly — worktree list unchanged.');
}

main();
