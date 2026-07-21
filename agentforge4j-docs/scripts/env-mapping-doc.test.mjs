// SPDX-License-Identifier: Apache-2.0
//
// Guards the documented environment-variable → property-key normalisation rule against the real
// ConfigReader contract: AGENTFORGE4J_<NAME> has its prefix replaced with 'agentforge4j.', then
// every '_' in the remainder becomes '.' and the remainder is lower-cased. Checks that (a) every
// envReachable fixture pair in env-sysprop-keys.json obeys that mapping, and (b) both shipped
// "Configure an LLM provider" pages (live and version-0.1.0) document the worked example with the
// key that mapping actually produces — a prior wording implied the prefix was stripped outright,
// which would yield 'llm.openai.api.key' instead of 'agentforge4j.llm.openai.api.key'.

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const docsRoot = join(dirname(fileURLToPath(import.meta.url)), '..');

const ENV_PREFIX = 'AGENTFORGE4J_';
const PROP_PREFIX = 'agentforge4j.';

/** The ConfigReader normalisation rule, restated independently of the fixture and the prose. */
function normaliseEnvVar(envVar) {
  return PROP_PREFIX + envVar.slice(ENV_PREFIX.length).replaceAll('_', '.').toLowerCase();
}

const HOW_TO_PAGES = [
  join(docsRoot, 'docs', 'how-to', 'configure-llm-provider.mdx'),
  join(docsRoot, 'versioned_docs', 'version-0.1.0', 'how-to', 'configure-llm-provider.mdx'),
];

const ENV_SYSPROP_REFERENCE_PAGES = [
  join(docsRoot, 'docs', 'reference', 'config', 'env-sysprop.mdx'),
  join(docsRoot, 'versioned_docs', 'version-0.1.0', 'reference', 'config', 'env-sysprop.mdx'),
];

test('every env-reachable fixture pair obeys the ConfigReader normalisation rule', () => {
  const fixture = JSON.parse(readFileSync(join(docsRoot, 'scripts', 'env-sysprop-keys.json'), 'utf8'));
  const reachable = fixture.keys.filter((entry) => entry.envReachable);
  assert.ok(reachable.length > 0, 'fixture must contain env-reachable entries');
  for (const entry of reachable) {
    assert.equal(
      normaliseEnvVar(entry.envVar),
      entry.key,
      `fixture entry ${entry.envVar} does not normalise to its declared key ${entry.key}`,
    );
  }
});

test('both LLM-provider how-to pages document the worked env-var example with its real normalised key', () => {
  const envVar = 'AGENTFORGE4J_LLM_OPENAI_API_KEY';
  const expectedKey = normaliseEnvVar(envVar);
  for (const page of HOW_TO_PAGES) {
    const source = readFileSync(page, 'utf8');
    assert.ok(source.includes(`\`${envVar}\``), `${page} must document the worked example ${envVar}`);
    assert.ok(
      source.includes(`\`${expectedKey}\``),
      `${page} must state the key the normalisation rule actually produces (${expectedKey})`,
    );
  }
});

test('every page describing the normalisation says the prefix is replaced, never stripped', () => {
  for (const page of [...HOW_TO_PAGES, ...ENV_SYSPROP_REFERENCE_PAGES]) {
    const source = readFileSync(page, 'utf8');
    assert.ok(
      !/prefix (is )?stripped/.test(source),
      `${page} must not describe the prefix as stripped — it is replaced with '${PROP_PREFIX}', ` +
        "and 'stripped' implies a key without the prefix",
    );
    assert.ok(
      /prefix (is )?replaced with/.test(source) && source.includes(`\`${PROP_PREFIX}\``),
      `${page} must state that the prefix is replaced with '${PROP_PREFIX}'`,
    );
  }
});
