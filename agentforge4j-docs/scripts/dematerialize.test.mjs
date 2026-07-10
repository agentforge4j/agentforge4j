// SPDX-License-Identifier: Apache-2.0
//
// Tests for the release-staging de-materialiser. Hermetic (node:test): a temporary repo root with an
// agentforge4j-examples/ fixture and an in-memory vocab set stand in for the real tree. The assertions
// prove the three properties the immutable-snapshot guarantee depends on: includes become static
// fences, vocab tags become bare values, Javadoc references become links pinned to the snapshot
// version (never `next`), no directive survives, and directive-looking text inside a fenced code block
// is left untouched.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {dematerialize, findLiveDirectives} from './dematerialize.mjs';

function repo() {
  const root = mkdtempSync(join(tmpdir(), 'demat-'));
  const exDir = join(root, 'agentforge4j-examples', 'm', 'src');
  mkdirSync(exDir, {recursive: true});
  writeFileSync(
    join(exDir, 'X.java'),
    ['class X {', '  // tag::run[]', '  int a = 1;', '  // end::run[]', '}'].join('\n'),
  );
  return root;
}

const root = repo();
const vocabSets = {behaviour: new Set(['BRANCH', 'CALL_AGENT'])};
const opts = {version: '0.1.0', repoRoot: root, vocabSets, docLabel: 't.mdx'};

test('resolves an include into a static fenced block (no directive left)', () => {
  const src = [
    '# Doc',
    '',
    '```java file=agentforge4j-examples/m/src/X.java region=run title="X.java"',
    '```',
    '',
  ].join('\n');
  const out = dematerialize(src, opts);
  assert.match(out, /```java title="X\.java"\nint a = 1;\n```/);
  assert.doesNotMatch(out, /file=/);
  assert.doesNotMatch(out, /region=/);
});

test('resolves a vocab tag to its bare value', () => {
  const out = dematerialize('The `vocab:behaviour:BRANCH` behaviour.', opts);
  assert.equal(out, 'The `BRANCH` behaviour.');
});

test('pins a javadoc reference to the snapshot version, not next', () => {
  const out = dematerialize('See `javadoc:com.agentforge4j.core.workflow.Workflow`.', opts);
  assert.match(
    out,
    /\[`Workflow`\]\(pathname:\/\/\/javadoc\/0\.1\.0\/agentforge4j\.core\/com\/agentforge4j\/core\/workflow\/Workflow\.html "com\.agentforge4j\.core\.workflow\.Workflow"\)/,
  );
  assert.doesNotMatch(out, /\/javadoc\/next\//);
});

test('pins a hardcoded /javadoc/next/ surface link/label to the snapshot version', () => {
  const src = [
    'See [`/javadoc/next/`](pathname:///javadoc/next/surfaces.html) and',
    'the [Core API](pathname:///javadoc/next/).',
    '',
  ].join('\n');
  const out = dematerialize(src, opts);
  assert.doesNotMatch(out, /\/javadoc\/next\//);
  assert.match(out, /\[`\/javadoc\/0\.1\.0\/`\]\(pathname:\/\/\/javadoc\/0\.1\.0\/surfaces\.html\)/);
  assert.match(out, /\(pathname:\/\/\/javadoc\/0\.1\.0\/\)/);
});

test('does NOT pin a /javadoc/next/ route inside a fenced code block', () => {
  const src = ['```text', 'Routes live under /javadoc/next/ before release.', '```', ''].join('\n');
  const out = dematerialize(src, opts);
  assert.equal(out, src); // untouched inside the fence
});

test('leaves directive-looking text inside a fenced code block untouched', () => {
  // A code block documenting the roles: the ```` ``` ```` fence makes this a literal `code` node with
  // no `file=` meta, and the `vocab:`/`javadoc:` text inside is not an inlineCode node.
  const src = [
    'Example markup:',
    '',
    '```text',
    'Write `vocab:behaviour:BRANCH` or `javadoc:com.agentforge4j.core.workflow.Workflow`.',
    '```',
    '',
  ].join('\n');
  const out = dematerialize(src, opts);
  assert.equal(out, src); // byte-identical: nothing inside the fence was rewritten
});

test('preserves frontmatter and MDX prose byte-for-byte outside directives', () => {
  const src = [
    '---',
    'title: Concepts',
    'kind: concept',
    '---',
    '',
    'import Foo from "@site/x";',
    '',
    'A `vocab:behaviour:CALL_AGENT` step. <Foo prop="keep" />',
    '',
  ].join('\n');
  const out = dematerialize(src, opts);
  assert.match(out, /^---\ntitle: Concepts\nkind: concept\n---/);
  assert.match(out, /import Foo from "@site\/x";/);
  assert.match(out, /A `CALL_AGENT` step\. <Foo prop="keep" \/>/);
});

test('requires a target version', () => {
  assert.throws(() => dematerialize('x', {repoRoot: root, vocabSets}), /a target `version` is required/);
});

test('fails closed on an unknown vocab value (a bad reference cannot ship in a snapshot)', () => {
  assert.throws(
    () => dematerialize('`vocab:behaviour:NOPE`', opts),
    /not a member of the 'behaviour' vocabulary/,
  );
});

test('findLiveDirectives reports every live directive kind with its line', () => {
  const src = [
    'A `vocab:behaviour:BRANCH` step,',
    'then `javadoc:com.agentforge4j.core.workflow.Workflow`,',
    'and [the surface](pathname:///javadoc/next/).',
    '',
    '```java file=agentforge4j-examples/m/src/X.java region=run',
    '```',
  ].join('\n');
  const live = findLiveDirectives(src);
  assert.deepEqual(
    live.map((d) => [d.type, d.line]),
    [
      ['vocab', 1],
      ['javadoc', 2],
      ['javadoc-route', 3],
      ['include', 5],
    ],
  );
});

test('findLiveDirectives is fence-aware: documentation about the directives is not live', () => {
  // The contributor guide's authoring examples: everything sits inside fenced code blocks, so none
  // of it is a live directive — the de-materialiser leaves it alone and the assertion must too.
  const src = [
    'Authoring examples:',
    '',
    '````md',
    '```java file=agentforge4j-examples/m/src/X.java region=run title="X.java"',
    '```',
    '````',
    '',
    '```text',
    'Write `vocab:behaviour:BRANCH` or `javadoc:com.agentforge4j.core.workflow.Workflow`.',
    'Routes live under /javadoc/next/ before release.',
    '```',
    '',
  ].join('\n');
  assert.deepEqual(findLiveDirectives(src), []);
  // And the same document passes through dematerialize byte-identical.
  assert.equal(dematerialize(src, opts), src);
});

test('findLiveDirectives returns empty on fully de-materialised output', () => {
  const src = [
    'A `vocab:behaviour:BRANCH` and `javadoc:com.agentforge4j.core.workflow.Workflow`.',
    '',
    '```java file=agentforge4j-examples/m/src/X.java region=run',
    '```',
  ].join('\n');
  assert.deepEqual(findLiveDirectives(dematerialize(src, opts)), []);
});

test('applies multiple directives in one document', () => {
  const src = [
    'A `vocab:behaviour:BRANCH` and `vocab:behaviour:CALL_AGENT`,',
    'plus `javadoc:com.agentforge4j.core.workflow.Workflow`.',
    '',
    '```java file=agentforge4j-examples/m/src/X.java region=run',
    '```',
  ].join('\n');
  const out = dematerialize(src, opts);
  assert.match(out, /`BRANCH`/);
  assert.match(out, /`CALL_AGENT`/);
  assert.match(out, /\/javadoc\/0\.1\.0\//);
  assert.match(out, /int a = 1;/);
  assert.doesNotMatch(out, /vocab:/);
  assert.doesNotMatch(out, /javadoc:com/);
  assert.doesNotMatch(out, /file=/);
});
