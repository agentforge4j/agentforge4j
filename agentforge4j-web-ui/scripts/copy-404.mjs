// SPDX-License-Identifier: Apache-2.0
// Ships dist/404.html as the same bundle as dist/index.html (design §10/P21): GitHub
// Pages serves this file for any unmatched path (real HTTP 404 status, from Pages
// itself), and because it boots the identical SPA bundle, React Router's `*` route
// then renders the branded NotFoundPage — satisfying both acceptance criteria from
// one artifact. Runs as part of `npm run build`, after `vite build` and deliberately
// BEFORE build-seo.mjs: build-seo.mjs rewrites dist/index.html to carry the home
// page's real prerendered body, and 404.html must stay the empty pre-prerender shell —
// an unmatched path must never serve a static copy of the full home page under a
// real HTTP 404 (verify-seo.mjs gates this ordering on every build). Fails the build
// (non-zero exit) if the two files are not byte-identical at copy time, so this is a
// build-time regression guard on the mechanism, not a separately-run test that can go stale.
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const distDir = join(import.meta.dirname, '..', 'dist');
const indexPath = join(distDir, 'index.html');
const notFoundPath = join(distDir, '404.html');

if (!existsSync(indexPath)) {
  throw new Error(`copy-404: ${indexPath} does not exist — run "vite build" first`);
}

const indexContent = readFileSync(indexPath);
writeFileSync(notFoundPath, indexContent);

const writtenContent = readFileSync(notFoundPath);
if (Buffer.compare(indexContent, writtenContent) !== 0) {
  throw new Error('copy-404: dist/404.html is not byte-identical to dist/index.html after copy');
}

console.log('copy-404: dist/404.html written, byte-identical to dist/index.html');
