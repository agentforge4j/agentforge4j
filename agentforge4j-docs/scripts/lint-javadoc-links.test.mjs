// SPDX-License-Identifier: Apache-2.0
//
// Tests for the Javadoc link-target existence gate. Hermetic: a fixture Javadoc build directory
// under a temp root stands in for a real `npm run javadoc` output.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {existsSync, mkdtempSync, mkdirSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {liveJavadocRefs, urlToBuildPath} from './lint-javadoc-links.mjs';

test('finds a live javadoc: reference with its line', () => {
  const src = [
    'line one',
    'See `javadoc:com.agentforge4j.core.workflow.Workflow` for details.',
  ].join('\n');
  const refs = liveJavadocRefs(src);
  assert.deepEqual(refs, [{fqcn: 'com.agentforge4j.core.workflow.Workflow', line: 2}]);
});

test('does NOT flag a javadoc: reference inside a fenced code block (documentation about the syntax)', () => {
  const src = [
    '```md',
    'Implement `javadoc:com.agentforge4j.llm.api.LlmClient` to add a provider.',
    '```',
  ].join('\n');
  assert.deepEqual(liveJavadocRefs(src), []);
});

test('finds multiple references across a document', () => {
  const src = [
    '`javadoc:com.agentforge4j.bootstrap.AgentForge4jBootstrap` is the entry point.',
    '',
    '`javadoc:com.agentforge4j.llm.api.LlmClient` is the client interface.',
  ].join('\n');
  const refs = liveJavadocRefs(src);
  assert.deepEqual(refs.map((r) => r.fqcn), [
    'com.agentforge4j.bootstrap.AgentForge4jBootstrap',
    'com.agentforge4j.llm.api.LlmClient',
  ]);
});

test('urlToBuildPath maps a /javadoc/next/ URL onto the build directory', () => {
  const path = urlToBuildPath(
    'pathname:///javadoc/next/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html',
    '/build-javadoc/next',
  );
  assert.equal(path, join('/build-javadoc/next', 'agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html'));
});

test('urlToBuildPath returns null for a version-pinned URL (nothing built to check)', () => {
  assert.equal(
    urlToBuildPath('pathname:///javadoc/0.1.0/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html'),
    null,
  );
});

test('end-to-end: a real class resolves to an existing fixture file, a misspelled one does not', () => {
  const root = mkdtempSync(join(tmpdir(), 'javadoc-lint-'));
  const buildDir = join(root, 'build-javadoc', 'next');
  mkdirSync(join(buildDir, 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow'), {recursive: true});
  writeFileSync(
    join(buildDir, 'agentforge4j.core', 'com', 'agentforge4j', 'core', 'workflow', 'Workflow.html'),
    '<html></html>',
  );

  const realUrl = 'pathname:///javadoc/next/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html';
  const typoUrl = 'pathname:///javadoc/next/agentforge4j.core/com/agentforge4j/core/workflow/Worklow.html';

  assert.ok(existsSync(urlToBuildPath(realUrl, buildDir)));
  assert.ok(!existsSync(urlToBuildPath(typoUrl, buildDir)));
});
