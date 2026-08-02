// SPDX-License-Identifier: Apache-2.0
//
// Tests for the pure table-building halves of generate-references.mjs. Proves the provider matrix
// derives its columns from the emitted model tiers rather than a hardcoded LITE/STANDARD/POWERFUL
// list, so a legitimate new tier (e.g. PREMIUM) appears in the docs without a generator edit.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {buildProviderTable, propertiesTable, cell, normalizeJavadocTags} from './generate-references.mjs';

test('every emitted tier becomes a table column, in declaration order', () => {
  const tiers = ['LITE', 'STANDARD', 'POWERFUL', 'PREMIUM'];
  const providers = [
    {name: 'openai', requiresApiKey: true, tiers: {LITE: 'gpt-mini', STANDARD: 'gpt-4', POWERFUL: 'gpt-5', PREMIUM: 'gpt-5-pro'}},
  ];
  const {header, separator, rows} = buildProviderTable(providers, tiers);
  assert.equal(header, '| Provider | API key required | LITE | STANDARD | POWERFUL | PREMIUM |');
  assert.equal(separator, '|---|---|---|---|---|---|');
  assert.equal(rows[0], '| `openai` | Yes | gpt-mini | gpt-4 | gpt-5 | gpt-5-pro |');
});

test('a missing per-provider tier value renders the empty-cell marker, not a crash', () => {
  const {rows} = buildProviderTable(
    [{name: 'ollama', requiresApiKey: false, tiers: {LITE: 'llama3'}}],
    ['LITE', 'STANDARD'],
  );
  assert.equal(rows[0], '| `ollama` | No | llama3 | — |');
});

test('the baseline three-tier shape still works (no new tier present)', () => {
  const {header, rows} = buildProviderTable(
    [{name: 'openai', requiresApiKey: true, tiers: {LITE: 'a', STANDARD: 'b', POWERFUL: 'c'}}],
    ['LITE', 'STANDARD', 'POWERFUL'],
  );
  assert.equal(header, '| Provider | API key required | LITE | STANDARD | POWERFUL |');
  assert.equal(rows[0], '| `openai` | Yes | a | b | c |');
});

test('zero tiers produces a table with only the fixed columns', () => {
  const {header, separator, rows} = buildProviderTable([{name: 'x', requiresApiKey: false, tiers: {}}], []);
  assert.equal(header, '| Provider | API key required |  |');
  assert.equal(separator, '|---|---||');
  assert.equal(rows[0], '| `x` | No |  |');
});

// --- propertiesTable(): oneOf/anyOf/enum/const must never render as "No properties." ----------
// Real shapes, taken verbatim from agentforge4j-schema's artifact.schema.json/workflow.schema.json
// — these three are the exact definitions that used to render "_No properties._" because the
// generator only ever looked at `.properties`.

test('ArtifactItem (a real oneOf union) renders its alternatives, not "No properties."', () => {
  const artifactItem = {
    oneOf: [
      {$ref: '#/$defs/TextItem'},
      {$ref: '#/$defs/TextAreaItem'},
      {$ref: '#/$defs/SingleChoiceItem'},
      {$ref: '#/$defs/MultiChoiceItem'},
      {$ref: '#/$defs/BooleanItem'},
      {$ref: '#/$defs/NumberItem'},
      {$ref: '#/$defs/DateItem'},
    ],
  };
  const lines = propertiesTable(artifactItem).join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /One of the following \(oneOf\)/);
  assert.match(lines, /\[TextItem\]\(#textitem\)/);
  assert.match(lines, /\[DateItem\]\(#dateitem\)/);
});

test('Executable (a real oneOf union with a self-$ref) renders its alternatives, not "No properties."', () => {
  const executable = {
    oneOf: [{$ref: '#/$defs/StepDefinition'}, {$ref: '#/$defs/BlueprintRef'}, {$ref: '#'}],
  };
  const lines = propertiesTable(executable).join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /One of the following \(oneOf\)/);
  assert.match(lines, /\[StepDefinition\]\(#stepdefinition\)/);
  assert.match(lines, /\[BlueprintRef\]\(#blueprintref\)/);
});

test('StepTransition (a real string enum) renders its enum values, not "No properties."', () => {
  const stepTransition = {type: 'string', enum: ['AUTO', 'HUMAN_REVIEW', 'HUMAN_APPROVAL']};
  const lines = propertiesTable(stepTransition).join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /Enum values/);
  assert.match(lines, /`AUTO`/);
  assert.match(lines, /`HUMAN_REVIEW`/);
  assert.match(lines, /`HUMAN_APPROVAL`/);
});

test('a const-only definition renders its constant value, not "No properties."', () => {
  const lines = propertiesTable({const: 'workflow'}).join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /Constant value.*`workflow`/);
});

test('a scalar-typed definition with no enum renders its type, not "No properties."', () => {
  const lines = propertiesTable({type: 'string'}).join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /Scalar type.*string/);
});

test('general regression: a genuinely property-less object schema is the only shape that renders "No properties."', () => {
  assert.deepEqual(propertiesTable({type: 'object'}), ['_No properties._', '']);
  assert.deepEqual(propertiesTable({}), ['_No properties._', '']);
});

// --- propertiesTable()/typeOf(): cross-file and whole-document $ref shapes must never produce a
// same-page anchor that does not exist there (the real broken-anchor build failure this fix closes:
// blueprint.schema.json's real Executable definition references workflow.schema.json's
// StepDefinition and the whole workflow.schema.json document, and workflow.schema.json's own
// Executable self-references "#") ------------------------------------------------------------

test('a cross-file $defs $ref (blueprint\'s real Executable, referencing workflow\'s StepDefinition) links to the OTHER page, not a same-page anchor', () => {
  // Real shape from agentforge4j-schema/src/main/resources/schema/blueprint.schema.json's $defs.Executable.
  const executable = {
    oneOf: [
      {$ref: 'workflow.schema.json#/$defs/StepDefinition'},
      {$ref: '#/$defs/BlueprintRef'},
      {$ref: 'workflow.schema.json'},
    ],
  };
  const lines = propertiesTable(executable, 'blueprint').join('\n');
  assert.doesNotMatch(lines, /No properties/);
  // Cross-file $defs ref: links into workflow.mdx's own anchor, not #stepdefinition on this page.
  assert.match(lines, /\[StepDefinition\]\(\.\/workflow\.mdx#stepdefinition\)/);
  assert.doesNotMatch(lines, /\]\(#stepdefinition\)/);
  // Same-file $defs ref: still a same-page anchor.
  assert.match(lines, /\[BlueprintRef\]\(#blueprintref\)/);
  // Whole-file ref (no $defs fragment at all): links to that schema's own page root, not `##`.
  assert.match(lines, /\[Workflow\]\(\.\/workflow\.mdx\)/);
  assert.doesNotMatch(lines, /\]\(##\)/);
});

test('a self-$ref ("#", workflow\'s own real Executable branch) links to the current schema\'s own page, not a literal "#" anchor', () => {
  const executable = {oneOf: [{$ref: '#/$defs/StepDefinition'}, {$ref: '#/$defs/BlueprintRef'}, {$ref: '#'}]};
  const lines = propertiesTable(executable, 'workflow').join('\n');
  assert.doesNotMatch(lines, /No properties/);
  assert.match(lines, /\[Workflow\]\(\.\/workflow\.mdx\)/);
  assert.doesNotMatch(lines, /\]\(##\)/);
  assert.doesNotMatch(lines, /\]\(#\)/);
});

test('a self-$ref with no current-schema-name context falls back to inert literal text, never a broken link', () => {
  const lines = propertiesTable({oneOf: [{$ref: '#'}]}).join('\n');
  assert.doesNotMatch(lines, /\]\(##?\)/);
  assert.match(lines, /`#`/);
});

test('an object schema with real properties still renders the properties table (no regression)', () => {
  const lines = propertiesTable({
    type: 'object',
    properties: {id: {type: 'string'}},
    required: ['id'],
  }).join('\n');
  assert.match(lines, /\| Property \| Type \| Required \| Description \|/);
  assert.match(lines, /`id`/);
});

// --- cell()/normalizeJavadocTags(): raw Javadoc block tags must never leak into generated docs ---

test('{@code x} normalizes to inline code, not raw tag markup', () => {
  assert.equal(normalizeJavadocTags('use {@code null} to disable it'), 'use `null` to disable it');
});

test('{@link fully.qualified.Type} normalizes to the short type name as inline code', () => {
  assert.equal(
    normalizeJavadocTags('see {@link com.agentforge4j.runtime.WorkflowRuntimeBuilder} for details'),
    'see `WorkflowRuntimeBuilder` for details',
  );
});

test('{@linkplain Type#member} normalizes to the short type name plus member', () => {
  assert.equal(
    normalizeJavadocTags('per {@linkplain FakeProviderAutoConfiguration#isEnabled}'),
    'per `FakeProviderAutoConfiguration#isEnabled`',
  );
});

test('cell() applies Javadoc-tag normalization before its own brace-escaping, so no raw {@... survives', () => {
  const rendered = cell('filesystem directory ({@code agentforge4j.integrations.dir}; or blank to skip)');
  assert.doesNotMatch(rendered, /\{@code/);
  assert.doesNotMatch(rendered, /\{@link/);
  assert.match(rendered, /`agentforge4j\.integrations\.dir`/);
});
