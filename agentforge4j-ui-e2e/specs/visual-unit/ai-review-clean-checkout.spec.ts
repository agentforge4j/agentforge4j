// SPDX-License-Identifier: Apache-2.0
//
// Regression coverage for scripts/visual/ai-review.mjs on a genuinely clean checkout: before this
// fix, every write path (including the disabled/no-credentials skip path, explicitly documented as
// "never a hard failure") crashed with ENOENT because visual-output/ didn't exist yet and nothing
// created it first. ai-review.mjs resolves its output path relative to its OWN file location (not
// cwd), so this test can't run it against a fixture directory — it has to run the real script
// against the real module tree, with the real visual-output/ directory temporarily out of the way.
// visual-output/ is gitignored, purely-generated, ephemeral output — safe to move aside and restore.

import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, renameSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const E2E_ROOT = fileURLToPath(new URL('../..', import.meta.url));
const OUTPUT_DIR = join(E2E_ROOT, 'visual-output');
const AI_REVIEW_SCRIPT = join(E2E_ROOT, 'scripts', 'visual', 'ai-review.mjs');

// .serial: every test in this file manipulates the SAME real visual-output/ directory (ai-review.mjs
// resolves its output path relative to its own file location, not cwd, so a fixture/temp directory
// can't stand in for it) — fullyParallel's default concurrent execution would let two tests race to
// rename/recreate/delete that one shared directory out from under each other.
test.describe.serial('ai-review.mjs on a clean checkout (no visual-output/ directory yet)', () => {
  let hadExistingOutputDir = false;
  let backupDir: string | null = null;

  test.beforeEach(() => {
    hadExistingOutputDir = existsSync(OUTPUT_DIR);
    backupDir = hadExistingOutputDir ? mkdtempSync(join(tmpdir(), 'visual-output-backup-')) : null;
    if (hadExistingOutputDir && backupDir) {
      renameSync(OUTPUT_DIR, join(backupDir, 'visual-output'));
    }
    expect(existsSync(OUTPUT_DIR)).toBe(false);
  });

  // Runs AFTER each test body (including its own file-inspection assertions) — restoring the real
  // directory here, not inside the script-running helper itself, so the test body gets a chance to
  // read whatever the script just wrote before it's cleaned up.
  test.afterEach(() => {
    rmSync(OUTPUT_DIR, { recursive: true, force: true });
    if (hadExistingOutputDir && backupDir) {
      renameSync(join(backupDir, 'visual-output'), OUTPUT_DIR);
      rmSync(backupDir, { recursive: true, force: true });
    }
  });

  /** `envDeletions` are explicitly removed from the child's environment (NOT set to the string
   *  `"undefined"` — passing a literal `undefined` value through `execFileSync`'s `env` option is
   *  not reliably the same as unsetting a variable, so this deletes the key from a fresh copy
   *  instead). */
  function runScript(envOverrides: Record<string, string> = {}, envDeletions: string[] = []) {
    const env: Record<string, string | undefined> = { ...process.env, ...envOverrides };
    for (const key of envDeletions) {
      delete env[key];
    }
    return execFileSync('node', [AI_REVIEW_SCRIPT], { cwd: E2E_ROOT, encoding: 'utf8', env });
  }

  test('AI review disabled: does not crash, writes a valid skip result, exits 0', () => {
    const stdout = runScript({}, ['AI_VISUAL_REVIEW_ENABLED', 'AI_VISUAL_REVIEW_API_KEY']);
    expect(stdout).toContain('skipped');
    const written = JSON.parse(readFileSync(join(OUTPUT_DIR, 'ai-review-results.json'), 'utf8'));
    expect(written.enabled).toBe(false);
    expect(written.reviewed).toEqual([]);
  });

  test('AI review enabled but credentials missing: does not crash, writes a valid skip result, exits 0', () => {
    const stdout = runScript({ AI_VISUAL_REVIEW_ENABLED: 'true' }, ['AI_VISUAL_REVIEW_API_KEY']);
    expect(stdout).toContain('skipped');
    const written = JSON.parse(readFileSync(join(OUTPUT_DIR, 'ai-review-results.json'), 'utf8'));
    expect(written.enabled).toBe(false);
    expect(written.reason).toContain('API_KEY');
  });

  test('AI review enabled with credentials but no capture records yet: does not crash, writes a valid empty result, exits 0', () => {
    const stdout = runScript({ AI_VISUAL_REVIEW_ENABLED: 'true', AI_VISUAL_REVIEW_API_KEY: 'fake-key-for-this-test' });
    expect(stdout).toContain('no AI-review-enabled captures found');
    const written = JSON.parse(readFileSync(join(OUTPUT_DIR, 'ai-review-results.json'), 'utf8'));
    expect(written.enabled).toBe(true);
    expect(written.reviewed).toEqual([]);
  });
});
