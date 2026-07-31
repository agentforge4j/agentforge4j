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

const MIRROR_FILES = SCHEMA_MIRRORS.map((mirror) => mirror.file);

const temporaryRoots: string[] = [];

/**
 * Copies the real canonical and mirror trees into a scratch directory so a negative control can
 * corrupt one file without touching the repository. A drift check that can only be exercised
 * against a clean tree cannot demonstrate that it bites.
 */
function scratchTrees(): { canonicalRoot: string; mirrorRoot: string } {
  const root = mkdtempSync(join(tmpdir(), 'af4j-schema-mirrors-'));
  temporaryRoots.push(root);
  const canonicalRoot = join(root, 'canonical');
  const mirrorRoot = join(root, 'mirror');
  mkdirSync(canonicalRoot, { recursive: true });
  mkdirSync(mirrorRoot, { recursive: true });
  for (const file of MIRROR_FILES) {
    cpSync(resolve(CANONICAL_ROOT, file), join(canonicalRoot, file));
    cpSync(resolve(MIRROR_ROOT, file), join(mirrorRoot, file));
  }
  return { canonicalRoot, mirrorRoot };
}

afterEach(() => {
  while (temporaryRoots.length > 0) {
    rmSync(temporaryRoots.pop() as string, { recursive: true, force: true });
  }
});

describe('canonical -> builder schema mirrors', () => {
  it('declares every schema file that exists in src/schemas, and no others', () => {
    const present = readdirSync(MIRROR_ROOT).filter((name) => name.endsWith('.json'));
    expect([...present].sort()).toEqual([...MIRROR_FILES].sort());
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

  it.each(MIRROR_FILES)('reports %s as missing when the copy is deleted', (file) => {
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

  it('reports a missing canonical source rather than passing silently', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    rmSync(join(canonicalRoot, 'agent.schema.json'));

    const results = collectDrift({ canonicalRoot, mirrorRoot });
    const missing = results.filter((result) => result.status === DRIFT_STATUS.MISSING_CANONICAL);
    expect(missing.map((result) => result.file)).toEqual(['agent.schema.json']);
  });

  it('treats a builder-owned schema as having no canonical source to drift from', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    rmSync(join(canonicalRoot, 'agent.schema.json'));

    const results = collectDrift({
      canonicalRoot,
      mirrorRoot,
      mirrors: [{ file: 'agent.schema.json', classification: CLASSIFICATIONS.BUILDER_OWNED }],
    });
    expect(isClean(results)).toBe(true);
  });

  it('every declared mirror is byte-identical to its canonical source', () => {
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
    rmSync(join(mirrorRoot, 'agent.schema.json'));

    syncMirrors({ canonicalRoot, mirrorRoot });

    expect(readFileSync(join(mirrorRoot, 'agent.schema.json'))).toEqual(
      readFileSync(join(canonicalRoot, 'agent.schema.json')),
    );
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
    rmSync(join(canonicalRoot, 'agent.schema.json'));

    let thrown: unknown;
    try {
      syncMirrors({ canonicalRoot, mirrorRoot });
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toBeInstanceOf(MissingCanonicalSchemaError);
    expect((thrown as MissingCanonicalSchemaError).paths).toEqual([
      resolve(canonicalRoot, 'agent.schema.json'),
    ]);
  });

  it('skips a builder-owned schema instead of looking for a canonical source', () => {
    const { canonicalRoot, mirrorRoot } = scratchTrees();
    rmSync(join(canonicalRoot, 'agent.schema.json'));

    const result = syncMirrors({
      canonicalRoot,
      mirrorRoot,
      mirrors: [{ file: 'agent.schema.json', classification: CLASSIFICATIONS.BUILDER_OWNED }],
    });

    expect(result.copied).toEqual([]);
    expect(result.skipped.map((entry) => entry.file)).toEqual(['agent.schema.json']);
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
