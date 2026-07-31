// SPDX-License-Identifier: Apache-2.0
//
// The single declaration of every JSON schema the workflow builder carries a copy of, and the
// only place that knows where a copy comes from. `sync-schema.mjs` writes the copies from this
// table; `verify-schema-mirrors.mjs` proves the committed copies still match. Neither script
// knows about an individual schema by name, so adding a fifth one is a row here rather than a
// new branch of copy logic.
//
// Canonical source of truth: agentforge4j-schema/src/main/resources/schema/. That module owns
// these documents; the builder only republishes them, and the JVM-side contract tests in
// agentforge4j-schema hold the canonical side honest.

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const builderRoot = resolve(here, '..');

/** Directory owning the canonical documents. */
export const CANONICAL_ROOT = resolve(
  builderRoot,
  '../agentforge4j-schema/src/main/resources/schema',
);

/** Directory holding the builder's committed copies. */
export const MIRROR_ROOT = resolve(builderRoot, 'src/schemas');

/**
 * How a builder-side schema relates to the canonical directory.
 *
 * - `MIRROR` — a byte-for-byte copy of the canonical document. It is never edited by hand, and
 *   any difference is drift by definition.
 * - `BUILDER_OWNED` — a schema the builder genuinely owns, with no canonical counterpart. There
 *   are none today; the classification exists so that a future exception has to be declared here
 *   deliberately rather than appear as an unexplained difference.
 */
export const CLASSIFICATIONS = Object.freeze({
  MIRROR: 'MIRROR',
  BUILDER_OWNED: 'BUILDER_OWNED',
});

/**
 * Every schema file under src/schemas, and where it comes from. The verifier cross-checks this
 * list against the directory in both directions, so a copy added without a row here — or a row
 * pointing at a file that no longer exists — fails the build rather than going unnoticed.
 */
export const SCHEMA_MIRRORS = Object.freeze([
  Object.freeze({ file: 'agent.schema.json', classification: CLASSIFICATIONS.MIRROR }),
  Object.freeze({ file: 'artifact.schema.json', classification: CLASSIFICATIONS.MIRROR }),
  Object.freeze({ file: 'blueprint.schema.json', classification: CLASSIFICATIONS.MIRROR }),
  Object.freeze({ file: 'workflow.schema.json', classification: CLASSIFICATIONS.MIRROR }),
]);

/** Status of one mirror, as reported by {@link collectDrift}. */
export const DRIFT_STATUS = Object.freeze({
  OK: 'OK',
  DRIFTED: 'DRIFTED',
  MISSING_MIRROR: 'MISSING_MIRROR',
  MISSING_CANONICAL: 'MISSING_CANONICAL',
  UNDECLARED_MIRROR: 'UNDECLARED_MIRROR',
});

function readOrNull(path) {
  try {
    return readFileSync(path);
  } catch {
    return null;
  }
}

/**
 * Compares every declared mirror against its canonical source and reports one result per schema.
 *
 * Roots are injectable so the drift check can be exercised against fixture directories — a test
 * that can only run against the real tree cannot prove the check bites.
 *
 * @param {{canonicalRoot?: string, mirrorRoot?: string, mirrors?: ReadonlyArray<{file: string, classification: string}>, presentFiles?: ReadonlyArray<string>}} [options]
 * @returns {Array<{file: string, classification: string, status: string, detail: string}>}
 */
export function collectDrift(options = {}) {
  const canonicalRoot = options.canonicalRoot ?? CANONICAL_ROOT;
  const mirrorRoot = options.mirrorRoot ?? MIRROR_ROOT;
  const mirrors = options.mirrors ?? SCHEMA_MIRRORS;

  const results = mirrors.map((mirror) => {
    const canonicalPath = resolve(canonicalRoot, mirror.file);
    const mirrorPath = resolve(mirrorRoot, mirror.file);

    if (mirror.classification === CLASSIFICATIONS.BUILDER_OWNED) {
      const owned = readOrNull(mirrorPath);
      return owned === null
        ? { ...mirror, status: DRIFT_STATUS.MISSING_MIRROR, detail: mirrorPath }
        : { ...mirror, status: DRIFT_STATUS.OK, detail: 'builder-owned; no canonical source' };
    }

    const canonical = readOrNull(canonicalPath);
    if (canonical === null) {
      return { ...mirror, status: DRIFT_STATUS.MISSING_CANONICAL, detail: canonicalPath };
    }
    const copy = readOrNull(mirrorPath);
    if (copy === null) {
      return { ...mirror, status: DRIFT_STATUS.MISSING_MIRROR, detail: mirrorPath };
    }
    return copy.equals(canonical)
      ? { ...mirror, status: DRIFT_STATUS.OK, detail: canonicalPath }
      : { ...mirror, status: DRIFT_STATUS.DRIFTED, detail: canonicalPath };
  });

  // A copy sitting in src/schemas with no row above is the failure mode this table exists to
  // prevent, so it is reported rather than ignored.
  if (options.presentFiles) {
    const declared = new Set(mirrors.map((mirror) => mirror.file));
    for (const file of options.presentFiles) {
      if (!declared.has(file)) {
        results.push({
          file,
          classification: 'UNDECLARED',
          status: DRIFT_STATUS.UNDECLARED_MIRROR,
          detail: resolve(mirrorRoot, file),
        });
      }
    }
  }

  return results;
}

/** True when every reported mirror is `OK`. */
export function isClean(results) {
  return results.every((result) => result.status === DRIFT_STATUS.OK);
}

/** Thrown by {@link syncMirrors} when a canonical source cannot be read. */
export class MissingCanonicalSchemaError extends Error {
  /** @param {ReadonlyArray<string>} paths canonical paths that could not be read */
  constructor(paths) {
    const listed = paths.map((path) => `  - ${path}`).join('\n');
    super(`canonical schema source(s) missing or unreadable:\n${listed}`);
    this.name = 'MissingCanonicalSchemaError';
    /** @type {ReadonlyArray<string>} */
    this.paths = Object.freeze([...paths]);
  }
}

/**
 * Rewrites every declared mirror from its canonical source.
 *
 * Every source is read before anything is written, so a missing or unreadable canonical document
 * aborts with nothing changed rather than leaving the tree half-synchronised — a partial sync is
 * indistinguishable from drift to the verifier, and would send the next reader chasing a
 * difference this script created.
 *
 * Roots are injectable for the same reason as {@link collectDrift}: the failure path has to be
 * exercisable without corrupting the repository.
 *
 * @param {{canonicalRoot?: string, mirrorRoot?: string, mirrors?: ReadonlyArray<{file: string, classification: string}>}} [options]
 * @returns {{copied: Array<{file: string, from: string, to: string}>, skipped: Array<{file: string, reason: string}>}}
 * @throws {MissingCanonicalSchemaError} when any `MIRROR` row has no readable canonical source
 */
export function syncMirrors(options = {}) {
  const canonicalRoot = options.canonicalRoot ?? CANONICAL_ROOT;
  const mirrorRoot = options.mirrorRoot ?? MIRROR_ROOT;
  const mirrors = options.mirrors ?? SCHEMA_MIRRORS;

  const pending = [];
  const skipped = [];
  const missing = [];

  for (const mirror of mirrors) {
    if (mirror.classification === CLASSIFICATIONS.BUILDER_OWNED) {
      skipped.push({ file: mirror.file, reason: 'builder-owned, no canonical source' });
      continue;
    }
    const from = resolve(canonicalRoot, mirror.file);
    const contents = readOrNull(from);
    if (contents === null) {
      missing.push(from);
      continue;
    }
    pending.push({ file: mirror.file, from, to: resolve(mirrorRoot, mirror.file), contents });
  }

  if (missing.length > 0) {
    throw new MissingCanonicalSchemaError(missing);
  }

  mkdirSync(mirrorRoot, { recursive: true });
  for (const entry of pending) {
    writeFileSync(entry.to, entry.contents);
  }

  return {
    copied: pending.map(({ file, from, to }) => ({ file, from, to })),
    skipped,
  };
}
