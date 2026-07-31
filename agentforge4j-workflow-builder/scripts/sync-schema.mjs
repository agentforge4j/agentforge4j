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
// Which files are copied, from where, and the all-or-nothing copy itself live in
// schema-mirrors.mjs. Nothing here knows any individual schema by name; this file is the command
// line around syncMirrors, so the copy behaviour — including its abort-before-writing failure
// path — is exercisable from tests rather than only by running the script.

import { MissingCanonicalSchemaError, SCHEMA_MIRRORS, syncMirrors } from './schema-mirrors.mjs';

let result;
try {
  result = syncMirrors();
} catch (error) {
  if (error instanceof MissingCanonicalSchemaError) {
    // Fail-closed and say which document is missing: the alternative is a raw ENOENT naming a
    // path with no indication that the mirror table is what expected it to be there.
    console.error(`[sync-schema] ${error.message}`);
    console.error('[sync-schema] nothing was written; src/schemas is unchanged.');
    process.exit(1);
  }
  throw error;
}

for (const skipped of result.skipped) {
  console.log(`[sync-schema] skipped ${skipped.file} (${skipped.reason})`);
}
for (const copied of result.copied) {
  console.log(`[sync-schema] copied ${copied.from} -> ${copied.to}`);
}

console.log(
  `[sync-schema] ${result.copied.length} of ${SCHEMA_MIRRORS.length} schema(s) synchronised`,
);
