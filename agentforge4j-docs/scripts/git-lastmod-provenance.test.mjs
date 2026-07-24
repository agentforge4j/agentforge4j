// SPDX-License-Identifier: Apache-2.0
//
// `verify-canonical.mjs` trusts that the sitemap plugin's `lastmod: 'date'` option (with
// `docusaurus.config.ts`'s `experimental_vcs: 'git-ad-hoc'`) genuinely reflects each page's own,
// individual last-modified date — not a single build-wide date. That guarantee comes entirely from
// `@docusaurus/utils`'s `getFileCommitDate`, the real per-file `git log` helper `git-ad-hoc` mode
// calls for every page. This proves that guarantee against a real, disposable git repository with
// controlled commit dates: an unrelated later commit must NOT move an untouched file's date, and a
// real modification must. A hand-rolled reimplementation of this check would only prove the test's
// own logic, not the actual mechanism the live build depends on — so this calls the real function.
//
// `age: 'newest'` (not `'update'` — `getFileCommitDate` supports only `'oldest'`/`'newest'`) matches
// what production actually passes: `@docusaurus/utils`'s own `getGitLastUpdate` calls
// `getGitCommitInfo(filePath, 'newest')` verbatim (`node_modules/@docusaurus/utils/lib/vcs/gitUtils.js`),
// which is what feeds each route's `lastUpdatedAt` and, from there, the sitemap's `<lastmod>`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { getFileCommitDate } from '@docusaurus/utils';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const D1 = '2026-01-05T00:00:00Z';
const D2 = '2026-01-10T00:00:00Z';

function commit(dir, message, isoDate) {
  execFileSync('git', ['add', '.'], { cwd: dir });
  execFileSync('git', ['commit', '--quiet', '-m', message], {
    cwd: dir,
    env: { ...process.env, GIT_AUTHOR_DATE: isoDate, GIT_COMMITTER_DATE: isoDate },
  });
}

function disposableRepo() {
  const dir = mkdtempSync(join(tmpdir(), 'git-lastmod-provenance-'));
  execFileSync('git', ['init', '--quiet'], { cwd: dir });
  execFileSync('git', ['config', 'user.email', 'test@example.invalid'], { cwd: dir });
  execFileSync('git', ['config', 'user.name', 'Test'], { cwd: dir });
  return dir;
}

test('an unrelated later commit does not move an untouched file\'s git-derived date (provenance is per-file, not build-wide)', async () => {
  const dir = disposableRepo();
  const fileA = join(dir, 'a.txt');
  writeFileSync(fileA, 'a');
  commit(dir, 'add a.txt', D1);

  const beforeUnrelatedCommit = await getFileCommitDate(fileA, { age: 'newest' });
  assert.equal(beforeUnrelatedCommit.date.toISOString().slice(0, 10), D1.slice(0, 10));

  // An unrelated commit, strictly later, touching a different file entirely.
  writeFileSync(join(dir, 'b.txt'), 'b');
  commit(dir, 'add unrelated b.txt', D2);

  const afterUnrelatedCommit = await getFileCommitDate(fileA, { age: 'newest' });
  assert.equal(
    afterUnrelatedCommit.date.toISOString().slice(0, 10),
    D1.slice(0, 10),
    'a.txt was not touched by the second commit — its date must still be D1, not D2',
  );
});

test('a real modification to the file itself does move its git-derived date to the new commit', async () => {
  const dir = disposableRepo();
  const fileA = join(dir, 'a.txt');
  writeFileSync(fileA, 'a');
  commit(dir, 'add a.txt', D1);

  writeFileSync(fileA, 'a, modified');
  commit(dir, 'modify a.txt', D2);

  const result = await getFileCommitDate(fileA, { age: 'newest' });
  assert.equal(
    result.date.toISOString().slice(0, 10),
    D2.slice(0, 10),
    'a.txt was modified at D2 — its date must now be D2, not the original D1',
  );
});
