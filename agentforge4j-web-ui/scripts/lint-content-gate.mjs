// SPDX-License-Identifier: Apache-2.0
//
// Committed-content gate (design §4) for this module: scans every `.ts`/`.tsx` source file
// under `src/` for both term groups. Neither term list is authored here — both live in
// `agentforge4j-docs/scripts/` and are imported via a relative cross-directory path, since no
// npm workspace ties the OSS repo's top-level modules together (confirmed: no root
// package.json/workspace file). This keeps exactly one copy of each list instead of a second,
// independently-drifting one per module.
//
// Scans `.ts`/`.tsx` only, not `.md`/`.mdx` — this module's page/marketing copy lives in TS
// modules (design §35's "centralised copy in src/copy/"), not markdown, unlike the docs site.

import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, sep, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {findProductNameLeaks, BLOCKED} from '../../agentforge4j-docs/scripts/product-name.mjs';
import {findAttributionLeaks, ATTRIBUTION_BLOCKED} from '../../agentforge4j-docs/scripts/attribution-terms.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const SRC_DIR = join(MODULE_ROOT, 'src');
const SOURCE_EXTENSIONS = ['.ts', '.tsx'];

function collectSources(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectSources(full));
    } else if (SOURCE_EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      out.push(full);
    }
  }
  return out;
}

let violations = 0;
let files = 0;
for (const file of collectSources(SRC_DIR)) {
  files += 1;
  const rel = relative(MODULE_ROOT, file).split(sep).join('/');
  const text = readFileSync(file, 'utf8');

  for (const {token, line, excerpt} of findProductNameLeaks(text)) {
    console.error(`✗ ${rel}:${line} — commercial identifier '${token}': ${excerpt}`);
    violations += 1;
  }
  for (const {id, line, excerpt, description} of findAttributionLeaks(text)) {
    console.error(`✗ ${rel}:${line} — ${description} (${id}): ${excerpt}`);
    violations += 1;
  }
}

if (violations > 0) {
  console.error(
    `\ncontent-gate: ${violations} violation(s) across ${files} file(s). See ` +
      'agentforge4j-docs/scripts/product-name.mjs and attribution-terms.mjs for the reviewed term lists.',
  );
  process.exit(1);
}

console.log(
  `content-gate: ${files} file(s) clean of ${BLOCKED.length} product-boundary and ` +
    `${ATTRIBUTION_BLOCKED.length} attribution pattern(s).`,
);
