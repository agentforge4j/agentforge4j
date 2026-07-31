// SPDX-License-Identifier: Apache-2.0
//
// Rewrites every builder-side schema copy from its canonical source in agentforge4j-schema.
// Run it after changing a canonical schema; the copies are committed, so the change shows up in
// the same pull request as the canonical edit and a reviewer sees both halves at once.
//
// This script deliberately does NOT run as part of build/typecheck/test. Those run
// verify-schema-mirrors.mjs instead, which fails on drift rather than silently repairing it — a
// build that re-synced first could never observe the drift it is supposed to catch.
//
// Which files are copied, and from where, lives in schema-mirrors.mjs. Nothing here knows any
// individual schema by name.

import { copyFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  CANONICAL_ROOT,
  CLASSIFICATIONS,
  MIRROR_ROOT,
  SCHEMA_MIRRORS,
} from './schema-mirrors.mjs';

mkdirSync(MIRROR_ROOT, { recursive: true });

let copied = 0;
for (const mirror of SCHEMA_MIRRORS) {
  if (mirror.classification === CLASSIFICATIONS.BUILDER_OWNED) {
    console.log(`[sync-schema] skipped ${mirror.file} (builder-owned, no canonical source)`);
    continue;
  }
  const canonical = resolve(CANONICAL_ROOT, mirror.file);
  const target = resolve(MIRROR_ROOT, mirror.file);
  copyFileSync(canonical, target);
  copied += 1;
  console.log(`[sync-schema] copied ${canonical} -> ${target}`);
}

console.log(`[sync-schema] ${copied} of ${SCHEMA_MIRRORS.length} schema(s) synchronised`);
