// SPDX-License-Identifier: Apache-2.0
//
// Regression coverage for the exact hazard specs/visual/capture.spec.ts's expected-inventory.json
// write must avoid: multiple Playwright worker OS PROCESSES writing the same shared path
// concurrently, at module load, before any test runs. An earlier temp-file-then-rename approach
// could still throw EPERM on Windows when several processes rename into the same destination at
// once — reproduced directly against a real filesystem (not theoretical): Windows does not give
// concurrent same-destination renames from different processes the same crash-safe atomicity POSIX
// rename provides. The fix is a single atomic `writeFileSync(path, data, { flag: 'wx' })`
// (`O_CREAT|O_EXCL`): exactly one process wins the create; every other process's write predictably
// fails with `EEXIST` and is safely ignored, since the content is deterministic/identical across
// workers regardless of which one wins. This test exercises the real primitive under real
// concurrent OS processes, not a mocked or single-process simulation.

import { expect, test } from '@playwright/test';
import { mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

const WRITER_SOURCE = `
import { writeFileSync } from 'node:fs';
const path = process.env.WX_TARGET_PATH;
const payload = JSON.stringify(['org-home--laptop', 'builder-empty--mobile'], null, 2);
try {
  writeFileSync(path, payload, { flag: 'wx' });
} catch (error) {
  if (error.code !== 'EEXIST') {
    throw error;
  }
}
`;

function spawnWriter(path: string): Promise<void> {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(process.execPath, ['--input-type=module', '-e', WRITER_SOURCE], {
      env: { ...process.env, WX_TARGET_PATH: path },
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('exit', (code) => (code === 0 ? resolvePromise() : reject(new Error(`writer exited ${code}: ${stderr}`))));
  });
}

test('20 concurrent OS processes racing an exclusive-create write to the same path never throw, and the file ends up valid', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'expected-inventory-race-'));
  const path = join(dir, 'expected-inventory.json');
  try {
    await Promise.all(Array.from({ length: 20 }, () => spawnWriter(path)));
    const parsed = JSON.parse(readFileSync(path, 'utf8'));
    expect(parsed).toEqual(['org-home--laptop', 'builder-empty--mobile']);
    // No stray temp files left behind — the exclusive-create approach never creates one.
    expect(readdirSync(dir)).toEqual(['expected-inventory.json']);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
