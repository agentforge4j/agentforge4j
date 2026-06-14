// SPDX-License-Identifier: Apache-2.0
//
// Copies the canonical workflow JSON schema into src/generated so the builder
// bundles a build-time derivative instead of a hand-maintained duplicate.
//
// Canonical source of truth (JVM-enforced via the agentforge4j-schema contract
// tests): agentforge4j-schema/src/main/resources/schema/workflow.schema.json.
// The generated target is gitignored; never edit it by hand.

import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

const canonical = resolve(
  here,
  '../../agentforge4j-schema/src/main/resources/schema/workflow.schema.json',
);
const target = resolve(here, '../src/generated/workflow.schema.json');

mkdirSync(dirname(target), { recursive: true });
copyFileSync(canonical, target);

console.log(`[sync-schema] copied ${canonical} -> ${target}`);
