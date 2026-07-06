// SPDX-License-Identifier: Apache-2.0
//
// Product-name separation gate (design §11, Phase 5b). Scans every docs page (authored + generated,
// so run after `npm run generate`) for commercial Platform/Cloud identifiers and fails the build on
// any leak. Precise identifiers + allowlist live in product-name.mjs.

import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, sep, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {findProductNameLeaks, BLOCKED} from './product-name.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const DOCS_DIR = join(here, '..', 'docs');
const DOC_EXTENSIONS = ['.md', '.mdx'];

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

const docs = collectDocs(DOCS_DIR);
let violations = 0;
for (const file of docs) {
  const rel = relative(DOCS_DIR, file).split(sep).join('/');
  for (const {token, line, excerpt} of findProductNameLeaks(readFileSync(file, 'utf8'))) {
    console.error(`✗ docs/${rel}:${line} — commercial identifier '${token}': ${excerpt}`);
    violations += 1;
  }
}

if (violations > 0) {
  console.error(
    `\nproduct-name: ${violations} commercial-identifier leak(s) across ${docs.length} page(s). ` +
      'The OSS docs must not name the Platform/Cloud product. If an occurrence is legitimate, add it to ' +
      'the reviewed ALLOWLIST in scripts/product-name.mjs.',
  );
  process.exit(1);
}

console.log(`product-name: ${docs.length} page(s) clean of ${BLOCKED.length} blocked identifier(s).`);
