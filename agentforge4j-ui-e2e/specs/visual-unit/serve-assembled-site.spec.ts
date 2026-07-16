// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
// @ts-expect-error — plain .mjs, no type declarations; see this repo's other scripts/*.mjs.
import { resolveFile, isWithin } from '../../scripts/visual/serve-assembled-site.mjs';

// `isWithin` compares against the platform's own `path.sep` (backslash on Windows) — these fixture
// paths are built the same way real callers build them (via `path.join`), not hardcoded POSIX
// literals, so the assertions below match `isWithin`'s actual contract on every platform.
const ROOT = join('C:', 'site');
const CHILD = join(ROOT, 'docs', 'index.html');
const SIBLING = join('C:', 'site-evil', 'secret.txt');
const UNRELATED = join('C:', 'etc', 'passwd');

function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'serve-assembled-site-'));
  writeFileSync(join(dir, 'index.html'), '<html>root</html>');
  writeFileSync(join(dir, '404.html'), '<html>404</html>');
  mkdirSync(join(dir, 'docs'), { recursive: true });
  writeFileSync(join(dir, 'docs', 'index.html'), '<html>docs</html>');
  const secretDir = mkdtempSync(join(tmpdir(), 'serve-assembled-site-secret-'));
  writeFileSync(join(secretDir, 'secret.txt'), 'do not serve this');
  return { dir, secretDir };
}

test.describe('isWithin', () => {
  test('accepts the root itself', () => {
    expect(isWithin(ROOT, ROOT)).toBe(true);
  });

  test('accepts a genuine child path', () => {
    expect(isWithin(ROOT, CHILD)).toBe(true);
  });

  test('rejects a sibling directory that merely shares the root as a text prefix', () => {
    // The exact false-positive a naive `startsWith(root)` check (no separator) would accept.
    expect(isWithin(ROOT, SIBLING)).toBe(false);
  });

  test('rejects a fully unrelated path', () => {
    expect(isWithin(ROOT, UNRELATED)).toBe(false);
  });
});

test.describe('resolveFile — path traversal', () => {
  test('serves a real file inside the root', () => {
    const { dir, secretDir } = fixture();
    try {
      expect(resolveFile(dir, '/index.html')).toBe(join(dir, 'index.html'));
    } finally {
      rmSync(dir, { recursive: true, force: true });
      rmSync(secretDir, { recursive: true, force: true });
    }
  });

  test('serves a nested directory\'s own index.html for a client-side route', () => {
    const { dir, secretDir } = fixture();
    try {
      expect(resolveFile(dir, '/docs')).toBe(join(dir, 'docs', 'index.html'));
    } finally {
      rmSync(dir, { recursive: true, force: true });
      rmSync(secretDir, { recursive: true, force: true });
    }
  });

  test('a ../ traversal payload targeting a real file outside the root resolves to null, not the file', () => {
    const { dir, secretDir } = fixture();
    try {
      // secretDir sits alongside dir under the same temp root, so a generic "../../<secretDir
      // basename>/secret.txt" is guaranteed to exist on disk — this proves the check actually
      // rejects a real, resolvable escape, not just a path that happens not to exist anyway.
      const secretName = secretDir.split(/[\\/]/).pop();
      const traversal = `/../${secretName}/secret.txt`;
      expect(resolveFile(dir, traversal)).toBeNull();
    } finally {
      rmSync(dir, { recursive: true, force: true });
      rmSync(secretDir, { recursive: true, force: true });
    }
  });

  test('an encoded ../ traversal payload also resolves to null', () => {
    const { dir, secretDir } = fixture();
    try {
      const secretName = secretDir.split(/[\\/]/).pop();
      const traversal = `/${encodeURIComponent('..')}/${secretName}/secret.txt`;
      expect(resolveFile(dir, traversal)).toBeNull();
    } finally {
      rmSync(dir, { recursive: true, force: true });
      rmSync(secretDir, { recursive: true, force: true });
    }
  });

  test('a genuinely missing path resolves to null (not an error)', () => {
    const { dir, secretDir } = fixture();
    try {
      expect(resolveFile(dir, '/does/not/exist.html')).toBeNull();
    } finally {
      rmSync(dir, { recursive: true, force: true });
      rmSync(secretDir, { recursive: true, force: true });
    }
  });
});
