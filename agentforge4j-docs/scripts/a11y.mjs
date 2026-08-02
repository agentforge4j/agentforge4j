// SPDX-License-Identifier: Apache-2.0
//
// Accessibility gate — a BLOCKING WCAG 2.1 AA check (pa11y-ci: axe + HTML_CS)
// over every built page. It serves the production build locally and crawls the generated sitemap, so
// the gate exercises exactly what ships. Requires a prior `npm run build` (fails fast if `build/` is
// absent) and headless Chromium (bundled with pa11y-ci; CI must allow `--no-sandbox`).
//
// Contrast is enforced by the HTML_CS runner (code WCAG2AA…1_4_3.G18). axe's own `color-contrast`
// rule is ignored in .pa11yci.json because pa11y bundles axe-core 4.2, whose color-contrast returns
// false "incomplete" results on wrapping inline code tokens (verified: the rendered token colours are
// AA-compliant, and current axe-core reports zero) — HTML_CS still checks contrast, so coverage is
// intact while axe's remaining rules (ARIA, landmarks, names) keep gating.
//
// Run via `npm run a11y`.

import {spawn} from 'node:child_process';
import {execFileSync} from 'node:child_process';
import {existsSync} from 'node:fs';
import {get} from 'node:http';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const MODULE_ROOT = resolve(here, '..');
const BUILD_DIR = join(MODULE_ROOT, 'build');
const DOCUSAURUS_BIN = join(MODULE_ROOT, 'node_modules', '@docusaurus', 'core', 'bin', 'docusaurus.mjs');
const PA11Y_BIN = join(MODULE_ROOT, 'node_modules', 'pa11y-ci', 'bin', 'pa11y-ci.js');

const PORT = 3210;
const ORIGIN = `http://localhost:${PORT}`;
const BASE = `${ORIGIN}/docs`; // baseUrl is /docs/
const SITEMAP = `${BASE}/sitemap.xml`;
// The generated sitemap lists absolute PRODUCTION URLs (https://agentforge4j.org/...). Rewrite the
// origin to the local server so pa11y crawls the build under test, not the (unpublished) live site.
const PROD_ORIGIN = 'https://agentforge4j.org';

function fail(message) {
  console.error(`[a11y] ${message}`);
  process.exit(1);
}

/** Poll a URL until it responds 200, or time out. */
function waitForServer(url, attempts, delayMs) {
  return new Promise((resolvePromise, reject) => {
    let left = attempts;
    const tick = () => {
      const req = get(url, (res) => {
        res.resume();
        if (res.statusCode === 200) {
          resolvePromise();
        } else {
          retry(`status ${res.statusCode}`);
        }
      });
      req.on('error', (err) => retry(err.message));
      req.setTimeout(2000, () => req.destroy(new Error('timeout')));
    };
    const retry = (why) => {
      left -= 1;
      if (left <= 0) {
        reject(new Error(`server not ready at ${url} (${why})`));
      } else {
        setTimeout(tick, delayMs);
      }
    };
    tick();
  });
}

async function main() {
  if (!existsSync(BUILD_DIR)) {
    fail('no build/ found — run `npm run build` before the a11y gate.');
  }

  console.log(`[a11y] serving build on :${PORT}`);
  const server = spawn(
    process.execPath,
    [DOCUSAURUS_BIN, 'serve', '--port', String(PORT), '--no-open'],
    {cwd: MODULE_ROOT, stdio: 'ignore'},
  );
  let serverExited = false;
  server.on('exit', () => {
    serverExited = true;
  });

  try {
    await waitForServer(SITEMAP, 60, 1000);
    if (serverExited) {
      fail('docusaurus serve exited before becoming ready.');
    }
    console.log(`[a11y] running pa11y-ci over ${SITEMAP} (origin rewritten to ${ORIGIN})`);
    execFileSync(
      process.execPath,
      [PA11Y_BIN, '--sitemap', SITEMAP, '--sitemap-find', PROD_ORIGIN, '--sitemap-replace', ORIGIN],
      {cwd: MODULE_ROOT, stdio: 'inherit'},
    );
    console.log('[a11y] all pages pass WCAG 2.1 AA.');
  } finally {
    if (!serverExited) {
      server.kill();
    }
  }
}

main().catch((err) => fail(err.message));
