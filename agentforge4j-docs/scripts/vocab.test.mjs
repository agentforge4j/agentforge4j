// SPDX-License-Identifier: Apache-2.0
//
// Tests for the vocabulary-lint remark plugin. Dependency-free (node:test); hermetic — the plugin
// reads a temporary vocab directory rather than the generated one. Run with `npm test`.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import vocabRemarkPlugin from '../src/remark/vocab.mjs';

function fixtureDir() {
  const dir = mkdtempSync(join(tmpdir(), 'vocab-'));
  writeFileSync(join(dir, 'behaviour.json'), JSON.stringify(['AGENT', 'BRANCH', 'VALIDATE']));
  writeFileSync(join(dir, 'command.json'), JSON.stringify(['CREATE_FILE', 'COMPLETE']));
  return dir;
}

function paragraph(code) {
  return {type: 'root', children: [{type: 'paragraph', children: [{type: 'inlineCode', value: code}]}]};
}

const transform = vocabRemarkPlugin({vocabDir: fixtureDir()});

test('rewrites a valid tag to its bare value', () => {
  const tree = paragraph('vocab:behaviour:BRANCH');
  transform(tree, {path: 't.mdx'});
  assert.equal(tree.children[0].children[0].value, 'BRANCH');
});

test('leaves non-vocab inline code untouched', () => {
  const tree = paragraph('justCode');
  transform(tree, {path: 't.mdx'});
  assert.equal(tree.children[0].children[0].value, 'justCode');
});

test('throws on a value not in the set', () => {
  assert.throws(() => transform(paragraph('vocab:behaviour:NOPE'), {path: 't.mdx'}), /not a member/);
});

test('throws on an unknown set', () => {
  assert.throws(() => transform(paragraph('vocab:bogus:X'), {path: 't.mdx'}), /unknown vocabulary set/);
});

test('throws on an invalid command', () => {
  assert.throws(() => transform(paragraph('vocab:command:NOT_REAL'), {path: 't.mdx'}), /not a member/);
});
