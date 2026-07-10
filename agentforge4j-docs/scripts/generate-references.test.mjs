// SPDX-License-Identifier: Apache-2.0
//
// Tests for the pure table-building halves of generate-references.mjs. Proves the provider matrix
// derives its columns from the emitted model tiers rather than a hardcoded LITE/STANDARD/POWERFUL
// list, so a legitimate new tier (e.g. PREMIUM) appears in the docs without a generator edit.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {buildProviderTable} from './generate-references.mjs';

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
