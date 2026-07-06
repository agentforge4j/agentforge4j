// SPDX-License-Identifier: Apache-2.0
//
// Frontmatter gate (design §11, enforceable). Walks every doc page and validates its
// frontmatter against the kind-conditional contract in frontmatter-contract.mjs.
// Exits non-zero on any violation so the build/CI fails closed.

import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {dirname} from 'node:path';
import matter from 'gray-matter';
import {validateFrontmatter} from './frontmatter-contract.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const DOCS_DIR = join(here, '..', 'docs');
const DOC_EXTENSIONS = ['.md', '.mdx'];

/** Recursively collect all Markdown/MDX files under a directory. */
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
const files = collectDocs(DOCS_DIR).sort();

for (const file of files) {
  const rel = relative(DOCS_DIR, file).split(sep).join('/');
  let fm;
  try {
    fm = matter(readFileSync(file, 'utf8')).data;
  } catch (err) {
    console.error(`✗ ${rel}: failed to parse frontmatter — ${err.message}`);
    violations += 1;
    continue;
  }
  const errors = validateFrontmatter(fm);
  if (errors.length > 0) {
    violations += errors.length;
    for (const error of errors) {
      console.error(`✗ ${rel}: ${error}`);
    }
  }
}

if (violations > 0) {
  console.error(`\nfrontmatter-lint: ${violations} violation(s) across ${files.length} page(s).`);
  process.exit(1);
}

console.log(`frontmatter-lint: ${files.length} page(s) OK.`);
