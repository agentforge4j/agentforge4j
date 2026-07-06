// SPDX-License-Identifier: Apache-2.0
//
// Tests for the product-name separation vocabulary (design §11). Proves the gate blocks precise
// commercial identifiers, leaves the generic English words "platform"/"cloud" alone, and honours the
// reviewed allowlist.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {findProductNameLeaks, BLOCKED} from './product-name.mjs';

test('flags a proprietary artifact name', () => {
  const hits = findProductNameLeaks('Add the `agentforge4j-platform-engine` dependency.');
  assert.equal(hits.length, 1);
  assert.equal(hits[0].token, 'agentforge4j-platform');
});

test('flags commercial vendors and the paywall term', () => {
  assert.ok(findProductNameLeaks('Payments run through Stripe.').some((h) => h.token === 'Stripe'));
  assert.ok(findProductNameLeaks('Behind a paywall.').some((h) => h.token === 'paywall'));
  assert.ok(findProductNameLeaks('Add `billing-stripe`.').some((h) => h.token === 'billing-stripe'));
});

test('does NOT flag bare concept words (avoids third-party false positives, §11)', () => {
  // These occur in generated reference text describing third-party providers (e.g. Gemini's
  // "multi-tenant host") and must not block the build.
  assert.deepEqual(findProductNameLeaks('the default multi-tenant host'), []);
  assert.deepEqual(findProductNameLeaks('an event subscription in the queue'), []);
  assert.deepEqual(findProductNameLeaks('token metering per request'), []);
});

test('flags the hosted product names and the proprietary licence', () => {
  assert.ok(findProductNameLeaks('Try AgentForge4j Cloud today.').some((h) => h.token === 'AgentForge4j Cloud'));
  assert.ok(findProductNameLeaks('Licensed under the Business Source License.').length > 0);
});

test('does NOT flag the generic words platform/cloud', () => {
  assert.deepEqual(findProductNameLeaks('AgentForge4j is a platform for building agentic workflows.'), []);
  assert.deepEqual(findProductNameLeaks('Run it in your own cloud or on-prem.'), []);
});

test('does NOT flag a blocked token embedded in a larger word', () => {
  // "billingham" / "clerkship" must not trip billing / Clerk.
  assert.deepEqual(findProductNameLeaks('The town of Billingham and a clerkship.'), []);
});

test('reports the 1-based line number', () => {
  const hits = findProductNameLeaks('clean line\nuses Stripe here');
  assert.equal(hits[0].line, 2);
});

test('the block list is non-empty and includes the layer prefixes', () => {
  assert.ok(BLOCKED.includes('agentforge4j-platform'));
  assert.ok(BLOCKED.includes('agentforge4j-cloud'));
  assert.ok(BLOCKED.length >= 20);
});
