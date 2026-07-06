// SPDX-License-Identifier: Apache-2.0
//
// Tests for the JavadocLink remark role + a drift guard tying its module table to the aggregator pom.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import plugin, {resolveJavadocUrl, DEFAULT_VERSION} from '../src/remark/javadoc.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(here, '..', '..');

test('resolves an aggregate (modular) class', () => {
  const {url, simpleName} = resolveJavadocUrl('com.agentforge4j.core.workflow.Workflow');
  assert.equal(url, 'pathname:///javadoc/next/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html');
  assert.equal(simpleName, 'Workflow');
});

test('prefers the longest module prefix (llm.api over llm)', () => {
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.llm.api.LlmClient').url,
    'pathname:///javadoc/next/agentforge4j.llm.api/com/agentforge4j/llm/api/LlmClient.html',
  );
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.llm.DefaultLlmClientResolver').url,
    'pathname:///javadoc/next/agentforge4j.llm/com/agentforge4j/llm/DefaultLlmClientResolver.html',
  );
});

test('routes the flat mcp and starter surfaces (no module dir)', () => {
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.mcp.client.McpServerRegistry').url,
    'pathname:///javadoc/next/mcp/com/agentforge4j/mcp/client/McpServerRegistry.html',
  );
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.starter.AgentForge4jProperties').url,
    'pathname:///javadoc/next/spring-boot-starter/com/agentforge4j/starter/AgentForge4jProperties.html',
  );
});

test('keeps the dot for a nested class', () => {
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.starter.AgentForge4jProperties.Integrations').url,
    'pathname:///javadoc/next/spring-boot-starter/com/agentforge4j/starter/AgentForge4jProperties.Integrations.html',
  );
});

test('throws on a non-AgentForge4j type', () => {
  assert.throws(() => resolveJavadocUrl('java.util.List'), /not a com\.agentforge4j/);
});

test('throws when no surface owns the package', () => {
  assert.throws(() => resolveJavadocUrl('com.agentforge4j.nope.Thing'), /no Javadoc surface owns/);
});

test('the plugin rewrites javadoc:<fqcn> inline code into a link', () => {
  const node = {type: 'inlineCode', value: 'javadoc:com.agentforge4j.core.workflow.Workflow'};
  const tree = {type: 'root', children: [{type: 'paragraph', children: [node]}]};
  plugin()(tree, {path: 't.mdx'});
  const link = tree.children[0].children[0];
  assert.equal(link.type, 'link');
  assert.match(link.url, /agentforge4j\.core\/com\/agentforge4j\/core\/workflow\/Workflow\.html$/);
  assert.equal(link.children[0].value, 'Workflow');
});

test('DEFAULT_VERSION is next (live builds pin to the current docs version)', () => {
  assert.equal(DEFAULT_VERSION, 'next');
});

test('pins the link to an explicit version (release-staging de-materialisation)', () => {
  // Aggregate surface.
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.core.workflow.Workflow', '0.1.0').url,
    'pathname:///javadoc/0.1.0/agentforge4j.core/com/agentforge4j/core/workflow/Workflow.html',
  );
  // Flat surface.
  assert.equal(
    resolveJavadocUrl('com.agentforge4j.mcp.client.McpServerRegistry', '0.1.0').url,
    'pathname:///javadoc/0.1.0/mcp/com/agentforge4j/mcp/client/McpServerRegistry.html',
  );
});

test('the plugin pins to a configured version', () => {
  const node = {type: 'inlineCode', value: 'javadoc:com.agentforge4j.core.workflow.Workflow'};
  const tree = {type: 'root', children: [{type: 'paragraph', children: [node]}]};
  plugin({version: '0.1.0'})(tree, {path: 't.mdx'});
  assert.match(tree.children[0].children[0].url, /^pathname:\/\/\/javadoc\/0\.1\.0\//);
});

test('drift guard: role module table matches the aggregator pom modules', () => {
  const pom = readFileSync(join(REPO_ROOT, 'agentforge4j-docs-javadoc', 'pom.xml'), 'utf8');
  const pomModules = [...pom.matchAll(/<module>\.\.\/(agentforge4j-[\w-]+)<\/module>/g)]
    .map((m) => m[1].replaceAll('-', '.'))
    .sort();
  // Resolve every pom module's a representative package to confirm the role places it in the aggregate.
  for (const moduleName of pomModules) {
    const url = resolveJavadocUrl(`com.${moduleName}.SomeType`).url;
    assert.ok(url.includes(`/javadoc/next/${moduleName}/`), `${moduleName} not routed to its module dir`);
  }
});
