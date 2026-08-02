// SPDX-License-Identifier: Apache-2.0
//
// Emits the authoritative identifier sets the vocabulary-lint remark plugin checks against
// (src/vocab/*.json), derived from the same generated sources as the reference pages. An author
// who writes `vocab:behaviour:NOPE` in MDX fails the build because NOPE is not in behaviour.json.
// Generated output is gitignored and regenerated at build time, so the vocab can never drift.

import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const REPO_ROOT = join(MODULE_ROOT, '..');
const EMITTER_OUT = join(here, 'emitter-output');
const VOCAB_DIR = join(MODULE_ROOT, 'src', 'vocab');
const SPRING_METADATA = join(
  REPO_ROOT,
  'agentforge4j-spring-boot-starter',
  'target',
  'classes',
  'META-INF',
  'spring-configuration-metadata.json',
);

function readJson(path, what) {
  if (!existsSync(path)) {
    console.error(`generate-vocab-lint: missing ${what}: ${path}`);
    process.exit(1);
  }
  return JSON.parse(readFileSync(path, 'utf8'));
}

function writeSet(name, values) {
  mkdirSync(VOCAB_DIR, {recursive: true});
  const unique = [...new Set(values)].sort();
  writeFileSync(join(VOCAB_DIR, `${name}.json`), `${JSON.stringify(unique, null, 2)}\n`, 'utf8');
  return unique.length;
}

function main() {
  const contracts = readJson(join(EMITTER_OUT, 'contract-sets.json'), 'contract-sets.json');
  const providers = readJson(join(EMITTER_OUT, 'providers.json'), 'providers.json');
  const envFixture = readJson(join(here, 'env-sysprop-keys.json'), 'env-sysprop-keys.json');
  const springMetadata = readJson(SPRING_METADATA, 'spring-configuration-metadata.json');

  const counts = {};
  counts.behaviour = writeSet('behaviour', contracts.stepBehaviours.map((b) => b.jsonType));
  counts.command = writeSet('command', contracts.llmCommands.map((c) => c.jsonType));
  counts.event = writeSet('event', contracts.workflowEventTypes);
  counts.status = writeSet('status', contracts.workflowStatuses);
  counts.tier = writeSet('tier', contracts.modelTiers);
  counts.provider = writeSet('provider', providers.map((p) => p.name));
  counts.config = writeSet('config', [
    ...(springMetadata.properties || []).map((p) => p.name),
    ...envFixture.keys.map((k) => k.key),
  ]);

  console.log(`generate-vocab-lint: wrote vocab sets ${JSON.stringify(counts)}.`);
}

main();
