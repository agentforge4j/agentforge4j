// SPDX-License-Identifier: Apache-2.0
//
// A minimal, GitHub-Pages-equivalent static file server for the fully composed Pages artifact
// (`agentforge4j-docs/_site`, produced by `assemble-site.mjs` — see `build-assembled-site.mjs` in
// this directory for how to produce it locally). Generalizes the same server shape
// `agentforge4j-ui-e2e/specs/web-ui/hosting.spec.ts` already uses inline for the un-assembled SPA
// (`agentforge4j-web-ui/dist`) into a reusable, standalone script serving any directory: a real,
// on-disk-file-or-404 server, never SPA-fallback middleware (which would mask whether the site's
// own `404.html`/routing actually works — the exact thing the Day 1.5 hosting contract cares
// about). Deliberately dependency-free (`node:http` only), matching this repo's other `scripts/
// *.mjs` tooling.
//
// Usage: node scripts/visual/serve-assembled-site.mjs [--dir <path>] [--port <port>]
//   --dir  Directory to serve (default: ../../agentforge4j-docs/_site relative to this file).
//   --port Port to bind (default: 4184 — distinct from web-ui's 4183 preview and the builder dev
//          harness's 5173, so all three Playwright projects in this module can run independently
//          without a port collision).

import { createServer } from 'node:http';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));

function parseArgs(argv) {
  const args = { dir: resolve(here, '..', '..', '..', 'agentforge4j-docs', '_site'), port: 4184 };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--dir') {
      args.dir = resolve(argv[i + 1]);
      i += 1;
    } else if (argv[i] === '--port') {
      args.port = Number(argv[i + 1]);
      i += 1;
    }
  }
  return args;
}

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.mjs': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain',
  '.xml': 'application/xml',
};

/** Resolves a request path to an on-disk file, the way GitHub Pages does: an exact file match, or
 *  (for a path with no extension, i.e. a client-side route) that directory's own `index.html` if
 *  one exists — never a blanket SPA-fallback to the site root, which is exactly the behaviour the
 *  Day 1.5 hosting contract exists to prove. */
function resolveFile(dir, requestPath) {
  const clean = decodeURIComponent(requestPath.split('?')[0]);
  const direct = join(dir, clean);
  if (existsSync(direct) && statSync(direct).isFile()) {
    return direct;
  }
  const asIndex = join(dir, clean, 'index.html');
  if (existsSync(asIndex)) {
    return asIndex;
  }
  return null;
}

export function startServer({ dir, port }) {
  if (!existsSync(dir)) {
    throw new Error(
      `[serve-assembled-site] missing composed site directory: ${dir}\n` +
        '  Run `node scripts/visual/build-assembled-site.mjs` first (see that script for the full ' +
        'local build chain), or pass --dir to point at an existing _site.',
    );
  }
  const server = createServer((req, res) => {
    const file = resolveFile(dir, req.url ?? '/');
    if (file) {
      const body = readFileSync(file);
      res.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream' });
      res.end(body);
      return;
    }
    // GitHub Pages' real behaviour for any path with no matching file: HTTP 404, served with the
    // site's own composed 404.html (the SPA shell — see assemble-site.mjs step 1 / copy-404.mjs).
    const notFoundPath = join(dir, '404.html');
    if (existsSync(notFoundPath)) {
      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(readFileSync(notFoundPath));
      return;
    }
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('404 Not Found (no 404.html present in the composed site)');
  });
  return new Promise((resolveReady) => {
    server.listen(port, () => resolveReady(server));
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const args = parseArgs(process.argv.slice(2));
  startServer(args)
    .then(() => {
      console.log(`[serve-assembled-site] serving ${args.dir} at http://localhost:${args.port}`);
    })
    .catch((error) => {
      console.error(error.message);
      process.exit(1);
    });
}
