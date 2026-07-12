// SPDX-License-Identifier: Apache-2.0
//
// Integration test proving the cross-directory relative import from
// agentforge4j-docs/scripts/ resolves correctly from this module — the actual matcher
// behaviour is exhaustively tested by attribution-terms.test.mjs and product-name.test.mjs
// in agentforge4j-docs itself; this only guards the relative-path wiring.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {findProductNameLeaks} from '../../agentforge4j-docs/scripts/product-name.mjs';
import {findAttributionLeaks} from '../../agentforge4j-docs/scripts/attribution-terms.mjs';

test('resolves the shared product-name matcher from agentforge4j-docs', () => {
  assert.ok(findProductNameLeaks('Uses `agentforge4j-platform-engine`.').length > 0);
});

test('resolves the shared attribution-terms matcher from agentforge4j-docs', () => {
  assert.ok(findAttributionLeaks('Co-Authored-By: Claude').length > 0);
});

test('does not flag legitimate provider mentions via the shared matcher', () => {
  assert.deepEqual(findAttributionLeaks('AgentForge4j supports Claude as a pluggable provider.'), []);
});
