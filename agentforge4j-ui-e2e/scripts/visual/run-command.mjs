// SPDX-License-Identifier: Apache-2.0
//
// Shared subprocess runner for scripts/visual/build-assembled-site.mjs and
// scripts/visual/release-check.mjs — both need "log the command, run it inheriting stdio, and fail
// closed with a one-line pointer instead of a raw Node stack trace"; each previously defined its
// own copy of this helper.

import { execFileSync } from 'node:child_process';

/**
 * Runs `command` with `args` in `cwd`, streaming its own stdout/stderr directly (`stdio:
 * 'inherit'`) so the failing step's real output is what a maintainer sees — a raw Node stack trace
 * on top of that would be noise, not signal. Exits the current process with the child's own exit
 * code (or 1) on failure.
 *
 * @param {string} logPrefix bracketed prefix for this caller's own log lines, e.g. "[release-check]"
 * @param {string} command
 * @param {readonly string[]} args
 * @param {string} cwd
 * @param {boolean} [shell] pass `true` on Windows callers that need cmd.exe (e.g. to resolve a
 *   `.cmd` wrapper script via PATH) — see each caller's own comment for why.
 */
export function run(logPrefix, command, args, cwd, shell = false) {
  console.log(`${logPrefix} $ ${command} ${args.join(' ')}  (cwd: ${cwd})`);
  try {
    execFileSync(command, args, { cwd, stdio: 'inherit', shell });
  } catch (error) {
    console.error(`${logPrefix} step failed: ${command} ${args.join(' ')} (exit ${error.status ?? 'unknown'})`);
    process.exit(typeof error.status === 'number' ? error.status : 1);
  }
}
