// SPDX-License-Identifier: Apache-2.0
//
// Tests for the source-backed include remark plugin. Dependency-free (node:test); hermetic — a
// temporary repo root with an agentforge4j-examples/ fixture stands in for the real tree.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import includeRemarkPlugin from '../src/remark/include.mjs';

function repo() {
  const root = mkdtempSync(join(tmpdir(), 'inc-'));
  const exDir = join(root, 'agentforge4j-examples', 'm', 'src');
  mkdirSync(exDir, {recursive: true});
  const java = [
    '// SPDX-License-Identifier: Apache-2.0',
    'class X {',
    '  void main() {',
    '    // tag::run[]',
    '    int a = 1;',
    '    int b = 2;',
    '    // end::run[]',
    '  }',
    '}',
  ].join('\n');
  writeFileSync(join(exDir, 'X.java'), java);
  mkdirSync(join(root, 'secret'), {recursive: true});
  writeFileSync(join(root, 'secret', 'S.java'), 'top secret');
  return root;
}

const root = repo();
const transform = includeRemarkPlugin({repoRoot: root});

function code(meta) {
  const node = {type: 'code', lang: 'java', meta, value: ''};
  const tree = {type: 'root', children: [node]};
  transform(tree, {path: 't.mdx'});
  return node;
}

test('extracts a named region, dedented, markers stripped', () => {
  const node = code('file=agentforge4j-examples/m/src/X.java region=run');
  assert.equal(node.value, 'int a = 1;\nint b = 2;');
  assert.equal(node.meta, null); // include directives stripped → plain fence
});

test('whole-file include strips marker lines', () => {
  const node = code('file=agentforge4j-examples/m/src/X.java');
  assert.match(node.value, /class X \{/);
  assert.doesNotMatch(node.value, /tag::run/);
});

test('keeps non-include meta like title', () => {
  const node = code('file=agentforge4j-examples/m/src/X.java region=run title="Run"');
  assert.equal(node.meta, 'title="Run"');
});

test('throws on missing file', () => {
  assert.throws(() => code('file=agentforge4j-examples/m/src/Nope.java'), /file not found/);
});

test('throws on missing region', () => {
  assert.throws(() => code('file=agentforge4j-examples/m/src/X.java region=ghost'), /not found/);
});

test('throws on a path outside the allowlist', () => {
  assert.throws(() => code('file=secret/S.java'), /outside the include allowlist/);
});

test('ignores code blocks without a file= directive', () => {
  const node = code('title="plain"');
  assert.equal(node.value, '');
});
