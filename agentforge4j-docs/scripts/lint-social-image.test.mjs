// SPDX-License-Identifier: Apache-2.0
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { findSocialImageProblems, parseSocialImagePath } from './lint-social-image.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const REAL_CONFIG = readFileSync(join(MODULE_ROOT, 'docusaurus.config.ts'), 'utf8');

function pngBytes(width, height) {
  const bytes = Buffer.alloc(24);
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(bytes, 0);
  bytes.writeUInt32BE(13, 8);
  bytes.write('IHDR', 12, 'ascii');
  bytes.writeUInt32BE(width, 16);
  bytes.writeUInt32BE(height, 20);
  return bytes;
}

function fixturePublicDir(relPath, bytes) {
  const root = mkdtempSync(join(tmpdir(), 'social-image-'));
  if (relPath !== null) {
    mkdirSync(join(root, dirname(relPath)), { recursive: true });
    writeFileSync(join(root, relPath), bytes);
  }
  return root;
}

const CONFIG = "    image: `${SITE_URL}/brand/social-preview.png`,\n";

test('the REAL committed config names a real, large-card-sized published image — this is the gate itself', () => {
  assert.deepEqual(findSocialImageProblems(REAL_CONFIG), []);
});

test('parseSocialImagePath reads the ${SITE_URL} template form the config actually uses', () => {
  assert.equal(parseSocialImagePath(CONFIG), '/brand/social-preview.png');
});

test('parseSocialImagePath reads a plain absolute URL too', () => {
  assert.equal(parseSocialImagePath("    image: 'https://agentforge4j.org/brand/card.png',\n"), '/brand/card.png');
});

test('a config with a published, large-enough image is clean', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', pngBytes(1280, 640));
  assert.deepEqual(findSocialImageProblems(CONFIG, publicDir), []);
});

test('NEGATIVE CONTROL — an image the SPA does not publish is reported, not silently accepted because the URL looks fine', () => {
  const publicDir = fixturePublicDir('brand/something-else.png', pngBytes(1280, 640));
  const problems = findSocialImageProblems(CONFIG, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /names \/brand\/social-preview\.png, which no file under .* publishes/);
});

test('NEGATIVE CONTROL — the pre-fix state: a 512x512 app icon is rejected for a large summary card', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', pngBytes(512, 512));
  const problems = findSocialImageProblems(CONFIG, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /is 512x512, under the 1200x630 a large summary card needs/);
});

test('a config declaring no site-absolute image at all is reported', () => {
  assert.match(findSocialImageProblems('    title: "x",\n', fixturePublicDir(null))[0], /declares no site-absolute themeConfig.image/);
});
