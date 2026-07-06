// SPDX-License-Identifier: Apache-2.0
//
// Source-backed snippet include (design §6/§8/§11). A fenced code block whose info string names a
// repo file (and optionally a named region) is filled, at build time, with the real source:
//
//   ```java file=agentforge4j-examples/.../QuickStartExample.java region=assemble
//   ```
//   ```json file=agentforge4j-examples/.../workflow.json
//   ```
//
// Constraints enforced (any violation throws → build fails, never a silent or stale snippet):
//   - path allowlist: only files under agentforge4j-examples/ may be included;
//   - named regions: `// tag::<name>[]` … `// end::<name>[]` markers, stripped from the output and
//     dedented; region omitted ⇒ whole file (still marker-stripped);
//   - deterministic: output is a pure function of (file, region);
//   - failure on missing file or missing/unclosed region;
//   - release-staging compatible: the directive is replaced by a plain fenced code block (the info
//     string keeps only the language + any title), so the resolved snapshot is static MD/MDX.
//
// The example build is the compile/run gate for the snippets themselves; this plugin only guarantees
// the doc shows the real, current source.

import {existsSync, readFileSync, realpathSync} from 'node:fs';
import {dirname, join, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DEFAULT_REPO_ROOT = resolve(here, '..', '..', '..'); // agentforge4j repo root
// Only source under these repo subdirectories may be included: the runnable examples, and the
// authoritative, test-guarded schema fixtures (the worked agent example lives there).
const ALLOWLIST_SUBDIRS = [
  'agentforge4j-examples',
  'agentforge4j-schema/src/test/resources/fixtures',
];

/** Parse `key=value` (value may be "quoted") pairs from a code-block info string. */
function parseMeta(meta) {
  const out = {};
  if (!meta) {
    return out;
  }
  for (const match of meta.matchAll(/(\w+)=("[^"]*"|\S+)/g)) {
    out[match[1]] = match[2].replace(/^"|"$/g, '');
  }
  return out;
}

/** Remove the leading indentation common to all non-blank lines. */
function dedent(lines) {
  const indents = lines
    .filter((line) => line.trim() !== '')
    .map((line) => line.match(/^[ \t]*/)[0].length);
  const min = indents.length === 0 ? 0 : Math.min(...indents);
  return lines.map((line) => line.slice(min));
}

const MARKER = /(?:tag|end)::([A-Za-z0-9_-]+)\[\]/;

/** Extract a named region (or the whole file), stripping all marker lines; deterministic. */
function extractRegion(content, region, fileLabel) {
  const lines = content.split(/\r?\n/);
  if (!region) {
    return dedent(lines.filter((line) => !MARKER.test(line))).join('\n').replace(/\n+$/, '');
  }
  const startIdx = lines.findIndex((line) => line.includes(`tag::${region}[]`));
  if (startIdx === -1) {
    throw new Error(`include: region '${region}' not found in ${fileLabel}`);
  }
  const endIdx = lines.findIndex((line, i) => i > startIdx && line.includes(`end::${region}[]`));
  if (endIdx === -1) {
    throw new Error(`include: region '${region}' is not closed (\`end::${region}[]\` missing) in ${fileLabel}`);
  }
  const body = lines.slice(startIdx + 1, endIdx).filter((line) => !MARKER.test(line));
  return dedent(body).join('\n').replace(/\n+$/, '');
}

function walk(node, visit) {
  if (!node || typeof node !== 'object') {
    return;
  }
  if (node.type === 'code') {
    visit(node);
  }
  if (Array.isArray(node.children)) {
    for (const child of node.children) {
      walk(child, visit);
    }
  }
}

/**
 * Remark plugin: resolve `file=…[ region=…]` code-block includes from allowlisted source.
 *
 * @param {{repoRoot?: string}} [options] optional repo-root override (for tests)
 */
export default function includeRemarkPlugin(options = {}) {
  const repoRoot = options.repoRoot || DEFAULT_REPO_ROOT;
  const allowBases = ALLOWLIST_SUBDIRS
    .map((sub) => resolve(repoRoot, sub))
    .filter((base) => existsSync(base))
    .map((base) => realpathSync(base));
  return (tree, file) => {
    walk(tree, (node) => {
      const meta = parseMeta(node.meta);
      if (!meta.file) {
        return;
      }
      const docLabel = file && file.path ? file.path : 'unknown';
      const target = resolve(repoRoot, meta.file);

      // Path allowlist — fail closed before any read.
      if (!existsSync(target)) {
        throw new Error(`include (${docLabel}): file not found: ${meta.file}`);
      }
      const real = realpathSync(target);
      const allowed = allowBases.some((base) => real === base || real.startsWith(base + sep));
      if (!allowed) {
        throw new Error(`include (${docLabel}): '${meta.file}' is outside the include allowlist (${ALLOWLIST_SUBDIRS.join(', ')})`);
      }

      node.value = extractRegion(readFileSync(real, 'utf8'), meta.region, meta.file);

      // Strip the include directives from the info string; keep language + any title — the result is
      // a plain fenced code block (release-staging materialisable).
      const kept = (node.meta || '')
        .replace(/\bfile=("[^"]*"|\S+)/, '')
        .replace(/\bregion=("[^"]*"|\S+)/, '')
        .trim();
      node.meta = kept === '' ? null : kept;
    });
  };
}
