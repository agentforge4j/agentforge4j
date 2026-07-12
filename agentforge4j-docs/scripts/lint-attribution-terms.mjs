// SPDX-License-Identifier: Apache-2.0
//
// Attribution-term gate (design §4, group 2). Scans every docs page (authored + generated,
// so run after `npm run generate`) for phrasing that credits an assistant/model/tool with
// having authored the work, and fails the build on any leak. Patterns + allowlist live in
// attribution-terms.mjs, which agentforge4j-web-ui's own gate driver also imports directly.
//
// Versioned snapshots (versioned_docs/) are scanned too, for the same reason
// lint-product-name.mjs scans them: a snapshot stays editable in the repo after its cut, and
// a post-cut edit must not slip an attribution leak into immutable history.

import {existsSync, readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, sep, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {findAttributionLeaks, ATTRIBUTION_BLOCKED} from './attribution-terms.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const DOC_EXTENSIONS = ['.md', '.mdx'];

const ROOTS = ['docs', 'versioned_docs']
  .map((name) => ({name, dir: join(MODULE_ROOT, name)}))
  .filter(({dir}) => existsSync(dir));

function collectDocs(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...collectDocs(full));
    } else if (DOC_EXTENSIONS.some((ext) => entry.endsWith(ext))) {
      out.push(full);
    }
  }
  return out;
}

let violations = 0;
let pages = 0;
for (const {name, dir} of ROOTS) {
  for (const file of collectDocs(dir)) {
    pages += 1;
    const rel = relative(dir, file).split(sep).join('/');
    for (const {id, line, excerpt, description} of findAttributionLeaks(readFileSync(file, 'utf8'))) {
      console.error(`✗ ${name}/${rel}:${line} — ${description} (${id}): ${excerpt}`);
      violations += 1;
    }
  }
}

if (violations > 0) {
  console.error(
    `\nattribution-terms: ${violations} attribution leak(s) across ${pages} page(s). ` +
      'Committed material must never credit an assistant/model/tool with authoring the work. If an ' +
      'occurrence is legitimate, add it to the reviewed ALLOWLIST in scripts/attribution-terms.mjs.',
  );
  process.exit(1);
}

console.log(`attribution-terms: ${pages} page(s) clean of ${ATTRIBUTION_BLOCKED.length} blocked pattern(s).`);
