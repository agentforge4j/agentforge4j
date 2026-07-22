// SPDX-License-Identifier: Apache-2.0
//
// Hermetic tests for the targeted versioned-reference regeneration mechanism. Fixture
// referenceDir/versionedDocsDir/vocabDir stand in for the real generated tree and an existing
// versioned snapshot — no real Maven/Docusaurus build required.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {regenerateVersionedReference} from './regenerate-versioned-reference.mjs';

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'regen-versioned-ref-'));
  const referenceDir = join(root, 'docs', 'reference');
  const versionedDocsDir = join(root, 'versioned_docs');
  const vocabDir = join(root, 'vocab');
  const repoRoot = root;
  mkdirSync(referenceDir, {recursive: true});
  mkdirSync(vocabDir, {recursive: true});
  // Minimal valid vocab set file (loadSets reads every *.json in the dir; an empty set is valid).
  writeFileSync(join(vocabDir, 'behaviour.json'), JSON.stringify(['AGENT']));
  return {root, referenceDir, versionedDocsDir, vocabDir, repoRoot};
}

function seedExistingVersion(versionedDocsDir, version, relPath, content) {
  const target = join(versionedDocsDir, `version-${version}`, 'reference', relPath);
  mkdirSync(join(target, '..'), {recursive: true});
  writeFileSync(target, content, 'utf8');
}

test('regenerates a plain (non-directive) generated page verbatim into the existing version', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), '# Behaviours\n\nplain generated content\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', '# Behaviours\n\nSTALE content\n');

  const written = regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  assert.equal(written, 1);
  assert.equal(
    readFileSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'behaviours.mdx'), 'utf8'),
    '# Behaviours\n\nplain generated content\n',
  );
});

test('a vocab: directive is resolved to its bare value, not left live, in the regenerated versioned output', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), 'A `vocab:behaviour:AGENT` step.\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');

  regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  const out = readFileSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'behaviours.mdx'), 'utf8');
  assert.doesNotMatch(out, /vocab:behaviour:AGENT/);
  assert.match(out, /`AGENT`/);
});

test('preserves the directory structure (nested config/ and schemas/ subfolders)', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  mkdirSync(join(referenceDir, 'config'), {recursive: true});
  mkdirSync(join(referenceDir, 'schemas'), {recursive: true});
  writeFileSync(join(referenceDir, 'config', 'spring.mdx'), '# Spring\n');
  writeFileSync(join(referenceDir, 'schemas', 'workflow.mdx'), '# Workflow\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'config/spring.mdx', 'stale\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'schemas/workflow.mdx', 'stale\n');

  const written = regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  assert.equal(written, 2);
  assert.equal(
    readFileSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'config', 'spring.mdx'), 'utf8'),
    '# Spring\n',
  );
  assert.equal(
    readFileSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'schemas', 'workflow.mdx'), 'utf8'),
    '# Workflow\n',
  );
});

test('never touches a hand-authored page elsewhere in the same version', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), 'fresh\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');
  const handAuthored = join(versionedDocsDir, 'version-0.1.0', 'how-to', 'configure-llm-provider.mdx');
  mkdirSync(join(handAuthored, '..'), {recursive: true});
  writeFileSync(handAuthored, 'hand-authored content, never touched\n');

  regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  assert.equal(readFileSync(handAuthored, 'utf8'), 'hand-authored content, never touched\n');
});

test('never touches a different version\'s reference subtree', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), 'fresh for 0.1.0\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');
  seedExistingVersion(versionedDocsDir, '1.0.0', 'behaviours.mdx', 'a different version entirely\n');

  regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  assert.equal(
    readFileSync(join(versionedDocsDir, 'version-1.0.0', 'reference', 'behaviours.mdx'), 'utf8'),
    'a different version entirely\n',
  );
});

test('fails closed when the target version does not exist', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), 'fresh\n');

  assert.throws(
    () => regenerateVersionedReference('9.9.9', {referenceDir, versionedDocsDir, vocabDir, repoRoot}),
    /version '9\.9\.9' does not exist/,
  );
});

test('fails closed when docs/reference/ has not been generated yet', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');
  // referenceDir exists (created by fixture()) but has no generated files at all — same
  // "not generated" precondition release-stage.mjs guards against, just via emptiness here
  // rather than a missing directory (both are real ways the precondition can be unmet).
  assert.throws(
    () =>
      regenerateVersionedReference('0.1.0', {
        referenceDir: join(referenceDir, 'does-not-exist'),
        versionedDocsDir,
        vocabDir,
        repoRoot,
      }),
    /generated references\/vocabulary are missing/,
  );
});

test('fails closed when the version exists but has no reference/ subtree at all', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), 'fresh\n');
  // A version directory that exists but was never given a reference/ subtree (e.g. a
  // pre-reference-generator-era version) — must not be silently created from scratch.
  mkdirSync(join(versionedDocsDir, 'version-0.1.0', 'how-to'), {recursive: true});

  assert.throws(
    () => regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot}),
    /no existing reference\/ subtree/,
  );
});

test('rejects an unsafe version string (path-traversal defence, shared with the rest of the release tooling)', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  assert.throws(() => regenerateVersionedReference('../../etc', {referenceDir, versionedDocsDir, vocabDir, repoRoot}));
  assert.ok(!existsSync(join(referenceDir, '..', '..', '..', 'etc')));
});

test('removes a stale generated page that no longer exists in the current generated reference set', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), '# Behaviours\n\nfresh\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');
  // 'obsolete.mdx' was generated at cut time but the generator no longer emits it (e.g. a schema
  // it documented was removed) — it must not survive regeneration.
  seedExistingVersion(versionedDocsDir, '0.1.0', 'obsolete.mdx', 'a page the generator no longer emits\n');

  const written = regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  assert.equal(written, 1);
  assert.ok(!existsSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'obsolete.mdx')));
  assert.equal(
    readFileSync(join(versionedDocsDir, 'version-0.1.0', 'reference', 'behaviours.mdx'), 'utf8'),
    '# Behaviours\n\nfresh\n',
  );
});

test('a renamed generated file leaves only the new path in the versioned target', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  // The generator now emits 'behaviours-reference.mdx' where it used to emit 'behaviours.mdx'.
  writeFileSync(join(referenceDir, 'behaviours-reference.mdx'), '# Behaviours\n\nrenamed content\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'content under the old name\n');

  regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  const targetReferenceDir = join(versionedDocsDir, 'version-0.1.0', 'reference');
  assert.ok(!existsSync(join(targetReferenceDir, 'behaviours.mdx')), 'old path must be gone');
  assert.equal(
    readFileSync(join(targetReferenceDir, 'behaviours-reference.mdx'), 'utf8'),
    '# Behaviours\n\nrenamed content\n',
  );
});

test('removes a nested directory that becomes empty because every generated page under it was renamed or deleted', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  writeFileSync(join(referenceDir, 'behaviours.mdx'), '# Behaviours\n\nfresh\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'stale\n');
  // The generator used to emit a whole 'config/' subtree; it no longer does (e.g. that reference
  // category was merged elsewhere) — the now-empty nested directory must not linger either.
  seedExistingVersion(versionedDocsDir, '0.1.0', 'config/spring.mdx', 'stale config page\n');

  regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot});

  const targetReferenceDir = join(versionedDocsDir, 'version-0.1.0', 'reference');
  assert.ok(!existsSync(join(targetReferenceDir, 'config', 'spring.mdx')));
  assert.ok(!existsSync(join(targetReferenceDir, 'config')), 'the now-empty config/ directory must be removed too');
});

test('a de-materialisation failure partway through leaves the original target subtree completely intact', () => {
  const {referenceDir, versionedDocsDir, vocabDir, repoRoot} = fixture();
  // Sorted processing order: 'behaviours.mdx' (valid) is generated and staged before 'broken.mdx'
  // (an unresolvable vocab reference) throws — proving a mid-run failure discards the whole partial
  // staging rather than partially replacing the real target.
  writeFileSync(join(referenceDir, 'behaviours.mdx'), '# Behaviours\n\nnewly generated\n');
  writeFileSync(join(referenceDir, 'broken.mdx'), 'A `vocab:behaviour:NOT_A_REAL_VALUE` step.\n');
  seedExistingVersion(versionedDocsDir, '0.1.0', 'behaviours.mdx', 'original stale content\n');

  assert.throws(
    () => regenerateVersionedReference('0.1.0', {referenceDir, versionedDocsDir, vocabDir, repoRoot}),
    /not a member of the 'behaviour' vocabulary/,
  );

  const targetReferenceDir = join(versionedDocsDir, 'version-0.1.0', 'reference');
  assert.equal(
    readFileSync(join(targetReferenceDir, 'behaviours.mdx'), 'utf8'),
    'original stale content\n',
    'the original subtree must be untouched, not partially replaced with the successfully-staged page',
  );
  assert.ok(
    !existsSync(join(targetReferenceDir, 'broken.mdx')),
    'no partially-staged page may leak into the real target',
  );
  const versionDirEntries = readdirSync(join(versionedDocsDir, 'version-0.1.0'));
  assert.ok(
    versionDirEntries.every((entry) => !entry.startsWith('.regenerate-versioned-reference-tmp-')),
    `staging directory must be cleaned up on failure, found: ${versionDirEntries.join(', ')}`,
  );
});
