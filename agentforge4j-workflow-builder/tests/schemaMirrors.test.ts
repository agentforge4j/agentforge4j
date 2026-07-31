// SPDX-License-Identifier: Apache-2.0

import { cpSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import {
  CANONICAL_ROOT,
  CLASSIFICATIONS,
  DRIFT_STATUS,
  MIRROR_ROOT,
  MissingCanonicalSchemaError,
  SCHEMA_MIRRORS,
  collectDrift,
  isClean,
  syncMirrors,
} from '../scripts/schema-mirrors.mjs';

/**
 * Every file the mirror table declares, whatever its classification. Drives the checks that apply
 * to a declaration as such — directory completeness, undeclared/missing inventory.
 */
const DECLARED_FILES = SCHEMA_MIRRORS.map((mirror) => mirror.file);

/**
 * Only the files classified `MIRROR`. Drives every check that presupposes a canonical source:
 * drift detection, byte-identity, and what `syncMirrors` is expected to copy. A `BUILDER_OWNED`
 * entry has no canonical counterpart by definition, so folding it into these would assert a
 * contract the production code deliberately does not implement.
 *
 * There are no `BUILDER_OWNED` entries today, so the two lists are currently equal. They are kept
 * separate anyway: the distinction is what the production classification means, and a test suite
 * that only happens to be right while one branch of a two-branch contract is unused is not
 * testing that contract. `describe('mixed classification table')` below exercises the other
 * branch directly rather than waiting for a real builder-owned schema to appear.
 */
const MIRROR_FILES = SCHEMA_MIRRORS.filter(
  (mirror) => mirror.classification === CLASSIFICATIONS.MIRROR,
).map((mirror) => mirror.file);

const temporaryRoots: string[] = [];

function newScratchRoot(): { canonicalRoot: string; mirrorRoot: string } {
  const root = mkdtempSync(join(tmpdir(), 'af4j-schema-mirrors-'));
  temporaryRoots.push(root);
  const canonicalRoot = join(root, 'canonical');
  const mirrorRoot = join(root, 'mirror');
  mkdirSync(canonicalRoot, { recursive: true });
  mkdirSync(mirrorRoot, { recursive: true });
  return { canonicalRoot, mirrorRoot };
}

/**
 * Copies the real canonical and mirror trees into a scratch directory so a negative control can
 * corrupt one file without touching the repository. A drift check that can only be exercised
 * against a clean tree cannot demonstrate that it bites.
 *
 * Every declared file gets a mirror-side copy; only `MIRROR` entries get a canonical-side one,
 * because a builder-owned schema has no canonical source to copy from.
 */
function scratchTrees(): { canonicalRoot: string; mirrorRoot: string } {
  const { canonicalRoot, mirrorRoot } = newScratchRoot();
  for (const file of DECLARED_FILES) {
    cpSync(resolve(MIRROR_ROOT, file), join(mirrorRoot, file));
  }
  for (const file of MIRROR_FILES) {
    cpSync(resolve(CANONICAL_ROOT, file), join(canonicalRoot, file));
  }
  return { canonicalRoot, mirrorRoot };
}

afterEach(() => {
  while (temporaryRoots.length > 0) {
    rmSync(temporaryRoots.pop() as string, { recursive: true, force: true });
  }
});

describe('canonical -> builder schema mirrors', () => {
  // Declaration completeness applies to every declared file regardless of classification: a
  // builder-owned schema still has to sit in src/schemas and still has to have a row.
  it('declares every schema file that exists in src/schemas, and no others', () => {
    const present = readdirSync(MIRROR_ROOT).filter((name) => name.endsWith('.json'));
    expect([...present].sort()).toEqual([...DECLARED_FILES].sort());
  });

  it('has no drift in the committed tree', () => {
    const results = collectDrift({ presentFiles: readdirSync(MIRROR_ROOT).filter((n) => n.endsWith('.json')) });
    const offenders = results.filter((result) => result.status !== DRIFT_STATUS.OK);
    expect(offenders).toEqual([]);
    expect(isClean(results)).toBe(true);
  });

  // One negative control per schema: every mirror must independently cause a failure. A single
  // shared control would pass even if three of the four schemas were silently unchecked.
  it.each(MIRROR_FILES)('detects drift in %s on its own', (file) => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const corrupted = JSON.parse(readFileSync(join(mirrorRoot, file), 'utf8')) as Record<string, unknown>;
    corrupted.title = `${String(corrupted.title ?? 'Schema')} (drifted)`;
    writeFileSync(join(mirrorRoot, file), `${JSON.stringify(corrupted, null, 2)}\n`);

    const results = collectDrift({ canonicalRoot, mirrorRoot });
    expect(isClean(results)).toBe(false);
    const drifted = results.filter((result) => result.status === DRIFT_STATUS.DRIFTED);
    expect(drifted.map((result) => result.file)).toEqual([file]);
  });

  // Applies to every declared file: a missing copy is `MISSING_MIRROR` for a builder-owned entry
  // exactly as it is for a mirrored one, so this is driven by the full declaration list.
  it.each(DECLARED_FILES)('reports %s as missing when the copy is deleted', (file) => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    rmSync(join(mirrorRoot, file));

    const results = collectDrift({ canonicalRoot, mirrorRoot });
    const missing = results.filter((result) => result.status === DRIFT_STATUS.MISSING_MIRROR);
    expect(missing.map((result) => result.file)).toEqual([file]);
  });

  it('reports a copy that the mirror table does not declare', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    writeFileSync(join(mirrorRoot, 'rogue.schema.json'), '{}\n');

    const results = collectDrift({
      canonicalRoot,
      mirrorRoot,
      presentFiles: readdirSync(mirrorRoot).filter((name) => name.endsWith('.json')),
    });
    const undeclared = results.filter((result) => result.status === DRIFT_STATUS.UNDECLARED_MIRROR);
    expect(undeclared.map((result) => result.file)).toEqual(['rogue.schema.json']);
  });

  // Only meaningful for a MIRROR entry — a builder-owned schema has no canonical source whose
  // absence could be reported, which is the point of the next test.
  it('reports a missing canonical source rather than passing silently', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const file = MIRROR_FILES[0];
    rmSync(join(canonicalRoot, file));

    const results = collectDrift({ canonicalRoot, mirrorRoot });
    const missing = results.filter((result) => result.status === DRIFT_STATUS.MISSING_CANONICAL);
    expect(missing.map((result) => result.file)).toEqual([file]);
  });

  it('treats a builder-owned schema as having no canonical source to drift from', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const file = MIRROR_FILES[0];
    rmSync(join(canonicalRoot, file));

    const results = collectDrift({
      canonicalRoot,
      mirrorRoot,
      mirrors: [{ file, classification: CLASSIFICATIONS.BUILDER_OWNED }],
    });
    expect(isClean(results)).toBe(true);
  });

  it('every mirrored schema is byte-identical to its canonical source', () => {
    for (const file of MIRROR_FILES) {
      expect(readFileSync(resolve(MIRROR_ROOT, file))).toEqual(
        readFileSync(resolve(CANONICAL_ROOT, file)),
      );
    }
  });
});

describe('sync-schema', () => {
  it('repairs every drifted mirror, so the verifier passes afterwards', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    for (const file of MIRROR_FILES) {
      writeFileSync(join(mirrorRoot, file), '{"title":"drifted"}\n');
    }
    expect(isClean(collectDrift({ canonicalRoot, mirrorRoot }))).toBe(false);

    const result = syncMirrors({ canonicalRoot, mirrorRoot });

    expect(result.copied.map((entry) => entry.file).sort()).toEqual([...MIRROR_FILES].sort());
    expect(isClean(collectDrift({ canonicalRoot, mirrorRoot }))).toBe(true);
  });

  it('writes byte-identical copies, not re-serialized JSON', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const file = MIRROR_FILES[0];
    rmSync(join(mirrorRoot, file));

    syncMirrors({ canonicalRoot, mirrorRoot });

    expect(readFileSync(join(mirrorRoot, file))).toEqual(readFileSync(join(canonicalRoot, file)));
  });

  it('is idempotent — a second run leaves every byte alone', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    syncMirrors({ canonicalRoot, mirrorRoot });
    const first = MIRROR_FILES.map((file) => readFileSync(join(mirrorRoot, file)));

    syncMirrors({ canonicalRoot, mirrorRoot });

    MIRROR_FILES.forEach((file, index) => {
      expect(readFileSync(join(mirrorRoot, file))).toEqual(first[index]);
    });
  });

  // The failure this covers is a partial sync: an ENOENT part-way through the copy loop would
  // leave some mirrors rewritten and others stale, which the verifier then reports as ordinary
  // drift with no hint that the sync itself created it.
  it('aborts without writing anything when a canonical source is missing', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    // Drift every mirror, then remove a canonical source that is not the first row processed, so
    // a naive loop would already have rewritten at least one file before it failed.
    for (const file of MIRROR_FILES) {
      writeFileSync(join(mirrorRoot, file), '{"title":"drifted"}\n');
    }
    const absent = MIRROR_FILES[MIRROR_FILES.length - 1];
    rmSync(join(canonicalRoot, absent));

    expect(() => syncMirrors({ canonicalRoot, mirrorRoot })).toThrow(MissingCanonicalSchemaError);

    for (const file of MIRROR_FILES) {
      expect(readFileSync(join(mirrorRoot, file), 'utf8')).toBe('{"title":"drifted"}\n');
    }
  });

  it('names the missing canonical path in the error it throws', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const file = MIRROR_FILES[0];
    rmSync(join(canonicalRoot, file));

    let thrown: unknown;
    try {
      syncMirrors({ canonicalRoot, mirrorRoot });
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(MissingCanonicalSchemaError);
    expect((thrown as MissingCanonicalSchemaError).paths).toEqual([resolve(canonicalRoot, file)]);
  });

  it('skips a builder-owned schema instead of looking for a canonical source', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    const file = MIRROR_FILES[0];
    rmSync(join(canonicalRoot, file));

    const result = syncMirrors({
      canonicalRoot,
      mirrorRoot,
      mirrors: [{ file, classification: CLASSIFICATIONS.BUILDER_OWNED }],
    });

    expect(result.copied).toEqual([]);
    expect(result.skipped.map((entry) => entry.file)).toEqual([file]);
  });
});

/**
 * The table supports two classifications but currently declares only `MIRROR` rows, so every
 * suite above exercises one branch of a two-branch contract. These tests drive a table holding
 * one of each, proving the `BUILDER_OWNED` branch behaves as documented before a real
 * builder-owned schema exists — rather than discovering on the day one is added that the suite
 * had quietly assumed every declaration has a canonical source.
 */
describe('mixed classification table', () => {
  const MIRRORED = 'mirrored.schema.json';
  const OWNED = 'builder-owned.schema.json';
  const OWNED_BODY = '{\n  "title": "Builder-owned, no canonical counterpart"\n}\n';

  const MIXED = [
    { file: MIRRORED, classification: CLASSIFICATIONS.MIRROR },
    { file: OWNED, classification: CLASSIFICATIONS.BUILDER_OWNED },
  ];

  /**
   * Canonical holds only the mirrored file. The builder-owned file exists on the mirror side
   * alone — deliberately, since that absence is the condition under test.
   */
  function mixedTrees(): { canonicalRoot: string; mirrorRoot: string } {
    const { canonicalRoot, mirrorRoot } = newScratchRoot();
    cpSync(resolve(CANONICAL_ROOT, MIRROR_FILES[0]), join(canonicalRoot, MIRRORED));
    cpSync(resolve(CANONICAL_ROOT, MIRROR_FILES[0]), join(mirrorRoot, MIRRORED));
    writeFileSync(join(mirrorRoot, OWNED), OWNED_BODY);
    return { canonicalRoot, mirrorRoot };
  }

  it('requires no canonical file for the builder-owned entry', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();
    expect(readdirSync(canonicalRoot)).toEqual([MIRRORED]);

    const results = collectDrift({ canonicalRoot, mirrorRoot, mirrors: MIXED });

    expect(isClean(results)).toBe(true);
    const owned = results.find((result) => result.file === OWNED);
    expect(owned?.status).toBe(DRIFT_STATUS.OK);
    expect(owned?.detail).toBe('builder-owned; no canonical source');
  });

  it('never reports the builder-owned entry as drifted, whatever its content', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();
    writeFileSync(join(mirrorRoot, OWNED), '{"title":"edited by hand, and legitimately so"}\n');

    const results = collectDrift({ canonicalRoot, mirrorRoot, mirrors: MIXED });

    expect(results.filter((result) => result.status === DRIFT_STATUS.DRIFTED)).toEqual([]);
    expect(isClean(results)).toBe(true);
  });

  it('still reports the mirrored entry as drifted, so the mixed table is not simply inert', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();
    writeFileSync(join(mirrorRoot, MIRRORED), '{"title":"drifted"}\n');

    const results = collectDrift({ canonicalRoot, mirrorRoot, mirrors: MIXED });

    const drifted = results.filter((result) => result.status === DRIFT_STATUS.DRIFTED);
    expect(drifted.map((result) => result.file)).toEqual([MIRRORED]);
  });

  it('syncs only the mirrored entry and leaves the builder-owned file untouched', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();
    writeFileSync(join(mirrorRoot, MIRRORED), '{"title":"drifted"}\n');

    const result = syncMirrors({ canonicalRoot, mirrorRoot, mirrors: MIXED });

    expect(result.copied.map((entry) => entry.file)).toEqual([MIRRORED]);
    expect(result.skipped.map((entry) => entry.file)).toEqual([OWNED]);
    expect(readFileSync(join(mirrorRoot, MIRRORED))).toEqual(
      readFileSync(join(canonicalRoot, MIRRORED)),
    );
    expect(readFileSync(join(mirrorRoot, OWNED), 'utf8')).toBe(OWNED_BODY);
  });

  it('counts both files as declared, so neither is reported undeclared', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();

    const results = collectDrift({
      canonicalRoot,
      mirrorRoot,
      mirrors: MIXED,
      presentFiles: readdirSync(mirrorRoot).filter((name) => name.endsWith('.json')),
    });

    expect(results.filter((r) => r.status === DRIFT_STATUS.UNDECLARED_MIRROR)).toEqual([]);
    expect(results.map((result) => result.file).sort()).toEqual([OWNED, MIRRORED].sort());
  });

  it('reports a deleted builder-owned copy as missing, same as a mirrored one', () => {
    const { canonicalRoot, mirrorRoot } = mixedTrees();
    rmSync(join(mirrorRoot, OWNED));

    const results = collectDrift({ canonicalRoot, mirrorRoot, mirrors: MIXED });

    const missing = results.filter((result) => result.status === DRIFT_STATUS.MISSING_MIRROR);
    expect(missing.map((result) => result.file)).toEqual([OWNED]);
  });
});

describe('canonical blueprint contract', () => {
  it('declares no root-level kind, so the builder must not emit one', () => {
    const blueprint = JSON.parse(
      readFileSync(resolve(CANONICAL_ROOT, 'blueprint.schema.json'), 'utf8'),
    ) as { additionalProperties: boolean; properties: Record<string, unknown> };
    expect(blueprint.additionalProperties).toBe(false);
    expect(Object.keys(blueprint.properties)).not.toContain('kind');
  });

  it('permits no inline nested blueprint in the executable alternatives (ADR-0010)', () => {
    const blueprint = JSON.parse(
      readFileSync(resolve(CANONICAL_ROOT, 'blueprint.schema.json'), 'utf8'),
    ) as { $defs: { Executable: { oneOf: Array<{ $ref?: string }> } } };
    expect(blueprint.$defs.Executable.oneOf.map((alternative) => alternative.$ref)).not.toContain('#');
  });
});
