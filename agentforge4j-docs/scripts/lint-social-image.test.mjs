// SPDX-License-Identifier: Apache-2.0
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  findDeclarationProblems,
  findSocialImageProblems,
  parseSocialImagePath,
  SOCIAL_IMAGE_DECLARATIONS,
} from './lint-social-image.mjs';

const MODULE_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const [DOCS_DECLARATION, JAVADOC_DECLARATION] = SOCIAL_IMAGE_DECLARATIONS;

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

const DOCS_SOURCE = "    image: `${SITE_URL}/brand/social-preview.png`,\n";
const JAVADOC_SOURCE = 'const DEFAULT_OG_IMAGE = `${DEFAULT_SITE_URL}/brand/icon-512.png`;\n';

// --- The gate itself, run against the real committed sources and the real publishing module. ---

test('EVERY real committed declaration names a real, correctly-sized published image — this is the gate itself', () => {
  assert.deepEqual(findSocialImageProblems(MODULE_ROOT), []);
  // Non-vacuity: an empty declaration list would make the assertion above trivially true, which is
  // exactly the state this file was in when it covered themeConfig.image alone.
  assert.ok(SOCIAL_IMAGE_DECLARATIONS.length > 1, 'expected more than one declared social image');
});

test('the declaration list covers assemble-site.mjs, not only docusaurus.config.ts', () => {
  const files = SOCIAL_IMAGE_DECLARATIONS.map((entry) => entry.file.replace(/\\/g, '/'));
  assert.ok(files.includes('docusaurus.config.ts'), 'the docs default must be declared');
  assert.ok(files.includes('scripts/assemble-site.mjs'), 'the Javadoc default must be declared');
});

// --- Parsing. ---

test('parseSocialImagePath reads the ${SITE_URL} template form the docs config actually uses', () => {
  assert.equal(parseSocialImagePath(DOCS_SOURCE), '/brand/social-preview.png');
});

test('parseSocialImagePath reads a plain absolute URL too', () => {
  assert.equal(parseSocialImagePath("    image: 'https://agentforge4j.org/brand/card.png',\n"), '/brand/card.png');
});

test('parseSocialImagePath reads the DEFAULT_OG_IMAGE form assemble-site.mjs actually uses', () => {
  assert.equal(parseSocialImagePath(JAVADOC_SOURCE, JAVADOC_DECLARATION.pattern), '/brand/icon-512.png');
});

// --- Per-declaration rules. ---

test('a docs declaration with a published, large-enough image is clean', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', pngBytes(1280, 640));
  assert.deepEqual(findDeclarationProblems(DOCS_DECLARATION, DOCS_SOURCE, publicDir), []);
});

test('NEGATIVE CONTROL — an image the SPA does not publish is reported, not silently accepted because the URL looks fine', () => {
  const publicDir = fixturePublicDir('brand/something-else.png', pngBytes(1280, 640));
  const problems = findDeclarationProblems(DOCS_DECLARATION, DOCS_SOURCE, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /names \/brand\/social-preview\.png, which no file under .* publishes/);
});

test('NEGATIVE CONTROL — the Javadoc default naming an unpublished file is reported too, not just the docs one', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', pngBytes(1280, 640));
  const problems = findDeclarationProblems(JAVADOC_DECLARATION, JAVADOC_SOURCE, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /assemble-site\.mjs DEFAULT_OG_IMAGE .* names \/brand\/icon-512\.png, which no file/);
});

test('NEGATIVE CONTROL — the pre-fix state: a 512x512 app icon is rejected for the large summary card', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', pngBytes(512, 512));
  const problems = findDeclarationProblems(DOCS_DECLARATION, DOCS_SOURCE, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /is 512x512, under the 1200x630 its card type needs/);
});

test('the same 512x512 icon is CORRECT for the Javadoc pages — each declaration is held to its own card type, not one flattened rule', () => {
  const publicDir = fixturePublicDir('brand/icon-512.png', pngBytes(512, 512));
  assert.deepEqual(findDeclarationProblems(JAVADOC_DECLARATION, JAVADOC_SOURCE, publicDir), []);
});

test('an image too small even for the small summary card is still rejected — the Javadoc minimum is a real floor, not an exemption', () => {
  const publicDir = fixturePublicDir('brand/icon-512.png', pngBytes(64, 64));
  const problems = findDeclarationProblems(JAVADOC_DECLARATION, JAVADOC_SOURCE, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /is 64x64, under the 144x144 its card type needs/);
});

test('a non-PNG is reported rather than silently skipping the size check — the same stance verify-seo.mjs takes on the SPA side', () => {
  const publicDir = fixturePublicDir('brand/social-preview.png', Buffer.from('not really a png at all, just bytes'));
  const problems = findDeclarationProblems(DOCS_DECLARATION, DOCS_SOURCE, publicDir);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /which is not a PNG/);
});

test('a source declaring no site-absolute image at all is reported', () => {
  assert.match(
    findDeclarationProblems(DOCS_DECLARATION, '    title: "x",\n', fixturePublicDir(null))[0],
    /declares no site-absolute themeConfig\.image/,
  );
});

test('a declaration whose source file has vanished is reported rather than silently skipped', () => {
  const problems = findSocialImageProblems(MODULE_ROOT, fixturePublicDir(null), [
    { ...DOCS_DECLARATION, file: 'no-such-file.ts' },
  ]);
  assert.equal(problems.length, 1);
  assert.match(problems[0], /no-such-file\.ts does not exist/);
});
