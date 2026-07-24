// SPDX-License-Identifier: Apache-2.0
//
// Mechanical regression guard: proves the production build (`npm run build`) and the real-browser
// test suite (`npm run test:seo`) both provision Chromium automatically via their npm lifecycle
// hooks, so a future caller cannot silently reintroduce the "npm ci && npm run build on a clean
// checkout has no browser" defect this module's own README and CI once depended on a human
// remembering a separate manual step to avoid. Deliberately does not shell out to the real
// Playwright installer (no `pretest:ensure-chromium` hook exists, and none should — this test does
// not itself need a browser, and asserting real chromium behaviour is covered by the actual clean
// checkout verification performed for this fix, not by the automated suite).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ensureChromiumCommand } from './ensure-chromium.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const packageJsonPath = join(here, '..', 'package.json');

test('ensureChromiumCommand runs exactly `npx playwright install chromium` — no --with-deps (OS package installation is a Linux/CI-only concern, handled separately by CI\'s own explicit step)', () => {
  const { command, args } = ensureChromiumCommand();
  assert.equal(command, 'npx');
  assert.deepEqual(args, ['playwright', 'install', 'chromium']);
});

test('npm run build (via its prebuild hook) provisions Chromium automatically before the production build (and its prerender pass) can run', () => {
  const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  assert.match(
    pkg.scripts.prebuild ?? '',
    /\bensure-chromium\b/,
    'package.json\'s "prebuild" script must run "ensure-chromium" — npm invokes prebuild automatically before build, so this is the one place a future caller cannot bypass',
  );
});

test('npm run test:seo (via its pretest:seo hook) provisions Chromium automatically — prerender-routes.test.mjs launches a real browser exactly like the production build does', () => {
  const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  assert.match(
    pkg.scripts['pretest:seo'] ?? '',
    /\bensure-chromium\b/,
    'package.json\'s "pretest:seo" script must run "ensure-chromium" too — test:seo exercises the exact same chromium.launch() code path as the production build',
  );
});

test('the "ensure-chromium" npm script itself resolves to this module (single source of truth, not duplicated command strings in prebuild and pretest:seo)', () => {
  const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  assert.equal(pkg.scripts['ensure-chromium'], 'node scripts/ensure-chromium.mjs');
});

test('commands that never launch a browser (lint, typecheck, the plain unit test suite, the content gate) do not provision Chromium — provisioning is scoped to exactly the two scripts that need it', () => {
  const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  const noBrowserNeeded = ['lint', 'pretest', 'pretypecheck', 'pretest:content-gate', 'prelint:content-gate'];
  for (const scriptName of noBrowserNeeded) {
    const script = pkg.scripts[scriptName];
    if (script === undefined) {
      continue;
    }
    assert.doesNotMatch(
      script,
      /ensure-chromium/,
      `"${scriptName}" does not need a real browser and must not pay for Chromium provisioning`,
    );
  }
});
