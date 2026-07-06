// SPDX-License-Identifier: Apache-2.0
//
// Shared paths + small fs helpers for the release-staging scripts (design §12, Phase 5a). Kept in one
// place so release-stage, release-cut, and release-scratch-cut agree on where things live.

import {createHash} from 'node:crypto';
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {dirname, join, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

/** The docs module root (agentforge4j-docs). */
export const MODULE_ROOT = resolve(here, '..');
/** The editable current-docs tree (`next`). */
export const DOCS_DIR = join(MODULE_ROOT, 'docs');
/** The gitignored scratch area for staging + swap backups. */
export const STAGING_ROOT = join(MODULE_ROOT, '.release-staging');
/** The materialised staged docs tree that a version is cut from. */
export const STAGED_DOCS = join(STAGING_ROOT, 'docs');
/** Docusaurus versioning outputs. */
export const VERSIONS_JSON = join(MODULE_ROOT, 'versions.json');
export const VERSIONED_DOCS = join(MODULE_ROOT, 'versioned_docs');
export const VERSIONED_SIDEBARS = join(MODULE_ROOT, 'versioned_sidebars');

/** List every file (recursively) under a directory, as repo-relative-to-`base` POSIX paths. */
export function listFiles(dir, base = dir) {
  const out = [];
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...listFiles(full, base));
    } else {
      out.push(relative(base, full).split(sep).join('/'));
    }
  }
  return out.sort();
}

/** A stable content hash of a directory tree (relative path + bytes of every file). */
export function hashTree(dir) {
  const hash = createHash('sha256');
  for (const rel of listFiles(dir)) {
    hash.update(rel);
    hash.update('\0');
    hash.update(readFileSync(join(dir, rel)));
    hash.update('\0');
  }
  return hash.digest('hex');
}

// A version must be a safe single path segment (no separators, no traversal) — it names directories
// (versioned_docs/version-<v>) and is passed to the CLI. Mirrors the shapes Docusaurus accepts.
const VERSION_RE = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

/** Validate a version string, returning it; throws on anything unsafe as a path segment. */
export function validateVersion(version) {
  if (typeof version !== 'string' || version === '') {
    throw new Error('release: a target version is required');
  }
  if (version === '.' || version === '..' || !VERSION_RE.test(version)) {
    throw new Error(`release: invalid version '${version}' (allowed: letters, digits, '.', '_', '-')`);
  }
  return version;
}

/** True if the path exists (file or directory). */
export function pathExists(path) {
  try {
    statSync(path);
    return true;
  } catch {
    return false;
  }
}
