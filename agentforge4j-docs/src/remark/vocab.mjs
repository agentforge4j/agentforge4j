// SPDX-License-Identifier: Apache-2.0
//
// Vocabulary-lint remark plugin (design §11). Authors mark a generated identifier inline as
// `vocab:<set>:<VALUE>` (an inline code span), e.g. `vocab:behaviour:BRANCH`. At build time this
// plugin checks <VALUE> against the authoritative set emitted to src/vocab/<set>.json and:
//   - throws (failing the build) if the set is unknown or the value is not a member — so a reference
//     to a nonexistent behaviour/command/event/status/provider/config key cannot ship;
//   - otherwise rewrites the node to the bare value, so the page renders `BRANCH` as inline code.
//
// The vocab sets are generated (scripts/generate-vocab-lint.mjs) from the same source as the
// reference pages, so the lint can never disagree with the generated references.

import {existsSync, readFileSync, readdirSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DEFAULT_VOCAB_DIR = join(here, '..', 'vocab');

const cache = new Map();

function loadSets(vocabDir) {
  if (cache.has(vocabDir)) {
    return cache.get(vocabDir);
  }
  if (!existsSync(vocabDir)) {
    throw new Error(
      `vocab-lint: ${vocabDir} does not exist. Run \`npm run generate\` (after the docs emitter) before building.`,
    );
  }
  const sets = {};
  for (const file of readdirSync(vocabDir)) {
    if (file.endsWith('.json')) {
      const name = file.replace(/\.json$/, '');
      sets[name] = new Set(JSON.parse(readFileSync(join(vocabDir, file), 'utf8')));
    }
  }
  cache.set(vocabDir, sets);
  return sets;
}

const PATTERN = /^vocab:([a-z]+):(.+)$/;

function walk(node, onInlineCode) {
  if (!node || typeof node !== 'object') {
    return;
  }
  if (node.type === 'inlineCode') {
    onInlineCode(node);
    return;
  }
  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      walk(child, onInlineCode);
    }
  }
}

/**
 * Remark plugin: validate and rewrite `vocab:<set>:<value>` inline-code tags.
 *
 * @param {{vocabDir?: string}} [options] optional override of the generated vocab directory (for tests)
 */
export default function vocabRemarkPlugin(options = {}) {
  const vocabDir = options.vocabDir || DEFAULT_VOCAB_DIR;
  return (tree, file) => {
    const sets = loadSets(vocabDir);
    walk(tree, (node) => {
      const match = PATTERN.exec(node.value);
      if (match === null) {
        return;
      }
      const [, setName, value] = match;
      const set = sets[setName];
      if (set === undefined) {
        throw new Error(
          `vocab-lint (${file.path}): unknown vocabulary set '${setName}' in \`${node.value}\`. ` +
            `Known sets: ${Object.keys(sets).sort().join(', ')}.`,
        );
      }
      if (!set.has(value)) {
        throw new Error(
          `vocab-lint (${file.path}): '${value}' is not a member of the '${setName}' vocabulary ` +
            `(referenced as \`${node.value}\`). It is not a generated ${setName} identifier.`,
        );
      }
      node.value = value;
    });
  };
}
