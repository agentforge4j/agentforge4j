// SPDX-License-Identifier: Apache-2.0
//
// Product-name separation gate (design §11, Phase 5b). Scans every docs page (authored + generated,
// so run after `npm run generate`) for commercial Platform/Cloud identifiers and fails the build on
// any leak. Precise identifiers + allowlist live in product-name.mjs.
//
// Versioned snapshots (versioned_docs/) are scanned too: a snapshot is frozen from gate-passing
// content at cut time, but it stays editable in the repo afterwards, and a post-cut edit must not
// slip a commercial identifier into immutable history. The frozen archive/ artifacts are built HTML
// rendered from these snapshots (kept as provenance), so scanning the snapshot source covers them.

import {existsSync, readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, sep, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {findProductNameLeaks, BLOCKED} from './product-name.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = join(here, '..');
const DOC_EXTENSIONS = ['.md', '.mdx'];

// Scan roots: the editable docs tree always; versioned snapshots once any exist (absent pre-release).
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
    for (const {token, line, excerpt} of findProductNameLeaks(readFileSync(file, 'utf8'))) {
      console.error(`✗ ${name}/${rel}:${line} — commercial identifier '${token}': ${excerpt}`);
      violations += 1;
    }
  }
}

if (violations > 0) {
  console.error(
    `\nproduct-name: ${violations} commercial-identifier leak(s) across ${pages} page(s). ` +
      'The OSS docs must not name the Platform/Cloud product. If an occurrence is legitimate, add it to ' +
      'the reviewed ALLOWLIST in scripts/product-name.mjs.',
  );
  process.exit(1);
}

console.log(`product-name: ${pages} page(s) clean of ${BLOCKED.length} blocked identifier(s).`);
