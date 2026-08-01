// SPDX-License-Identifier: Apache-2.0
//
// Fails the build when any committed builder-side schema copy differs from its canonical source
// in agentforge4j-schema, when a declared copy is missing, or when a copy exists that the mirror
// table does not declare.
//
// This runs in build, typecheck and test — and therefore in the `builder` CI job — so a change
// to a canonical schema that forgets `npm run sync-schema` fails a pull request instead of
// shipping a builder that publishes a stale contract.

import { readdirSync } from 'node:fs';
import { relative } from 'node:path';
import {
  CANONICAL_ROOT,
  DRIFT_STATUS,
  MIRROR_ROOT,
  collectDrift,
  isClean,
} from './schema-mirrors.mjs';

function presentSchemaFiles() {
  try {
    return readdirSync(MIRROR_ROOT).filter((name) => name.endsWith('.json'));
  } catch {
    return [];
  }
}

const EXPLANATIONS = {
  [DRIFT_STATUS.DRIFTED]: 'differs from its canonical source — run `npm run sync-schema`',
  [DRIFT_STATUS.MISSING_MIRROR]: 'declared in the mirror table but absent from src/schemas',
  [DRIFT_STATUS.MISSING_CANONICAL]: 'has no canonical source at the path the mirror table names',
  [DRIFT_STATUS.UNDECLARED_MIRROR]:
    'sits in src/schemas without a row in the mirror table — declare it as MIRROR or BUILDER_OWNED',
};

const results = collectDrift({ presentFiles: presentSchemaFiles() });

if (isClean(results)) {
  console.log(
    `[verify-schema-mirrors] ${results.length} schema(s) match ${relative(process.cwd(), CANONICAL_ROOT)}`,
  );
  process.exit(0);
}

console.error('[verify-schema-mirrors] builder schema copies are out of sync with the canonical schemas:');
for (const result of results) {
  if (result.status === DRIFT_STATUS.OK) {
    continue;
  }
  console.error(`  - ${result.file}: ${EXPLANATIONS[result.status] ?? result.status}`);
  console.error(`      ${result.detail}`);
}
process.exit(1);
