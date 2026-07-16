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
import { extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CONTENT_TYPES } from '../../support/content-types.mjs';

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

/** True only if `candidate` (already `resolve()`d to an absolute path) is `root` itself or
 *  genuinely inside it — a plain `startsWith(root)` string check would wrongly accept a sibling
 *  directory that happens to share `root` as a text prefix (e.g. root `/site` accepting
 *  `/site-evil/secret`), so this checks for the path separator boundary explicitly. */
export function isWithin(root, candidate) {
  return candidate === root || candidate.startsWith(root + sep);
}

/** Resolves a request path to an on-disk file, the way GitHub Pages does: an exact file match, or
 *  (for a path with no extension, i.e. a client-side route) that directory's own `index.html` if
 *  one exists — never a blanket SPA-fallback to the site root, which is exactly the behaviour the
 *  Day 1.5 hosting contract exists to prove. `dir` is the already-`resolve()`d site root; every
 *  candidate is resolved and re-checked against it before ever touching the filesystem, so a
 *  request path containing `..` (or, on Windows, an absolute drive path) can never escape it —
 *  this is local dev tooling, but it still serves whatever a browser or another local process
 *  asks it for, so it must not become an arbitrary local file reader. */
export function resolveFile(dir, requestPath) {
  let clean;
  try {
    clean = decodeURIComponent(requestPath.split('?')[0]);
  } catch {
    // Malformed percent-encoding (e.g. an unpaired '%') — treat exactly like "no matching file"
    // rather than letting a single bad request crash the whole server for the rest of the run.
    return null;
  }
  const direct = resolve(dir, `.${clean}`);
  if (!isWithin(dir, direct)) {
    return null;
  }
  if (existsSync(direct) && statSync(direct).isFile()) {
    return direct;
  }
  const asIndex = join(direct, 'index.html');
  if (isWithin(dir, asIndex) && existsSync(asIndex)) {
    return asIndex;
  }
  return null;
}

export function startServer({ dir: rawDir, port }) {
  const dir = resolve(rawDir);
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
  // Explicit '127.0.0.1', not the default all-interfaces bind: `hosting.spec.ts`'s own static
  // server (the pattern this script generalizes) already binds loopback-only, and there's no
  // reason for a local dev tool to be reachable from other devices on the same network.
  return new Promise((resolveReady) => {
    server.listen(port, '127.0.0.1', () => resolveReady(server));
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
