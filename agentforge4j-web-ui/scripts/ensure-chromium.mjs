// SPDX-License-Identifier: Apache-2.0
//
// Ensures the pinned Playwright Chromium build is present before any script that launches a real
// browser runs. The production build's own prerender pass (scripts/prerender-routes.mjs, invoked
// by scripts/build-seo.mjs) and `npm run test:seo` (which exercises that exact same code path via
// prerender-routes.test.mjs) both call `chromium.launch()` — `npm ci`/`npm install` installs the
// `playwright` npm package itself but never downloads the browser binary, so any caller relying on
// some OTHER, separate step having already primed an ambient browser cache is one missed step away
// from a "Executable doesn't exist" failure on a clean checkout. Known callers this closes the gap
// for: this repo's own CI (`web-ui.yml`, `deploy.yml`, `ui-e2e.yml`), the `agentforge4j-ui-e2e`
// visual/composed-site builder (`scripts/visual/build-assembled-site.mjs`), a local Docker-host
// build (`Dockerfile.local`'s documented `npm ci && npm run build` sequence), and a bare
// `npm ci && npm run build` from a clean clone with no other setup.
//
// Wired as the `prebuild`/`pretest:seo` npm lifecycle hooks (see package.json) — never called
// directly by a human or another script — so a future build-invoking script cannot silently forget
// it the way a separate, documented "run this first" manual step can be forgotten. `npm` invokes a
// `pre<script>` hook automatically immediately before `<script>` runs; there is no separate command
// to remember.
//
// `playwright install chromium` is itself already idempotent — a fast no-op when the exact pinned
// revision is already present (e.g. from a prior CI `--with-deps` step, or from this repo's own
// `agentforge4j-ui-e2e` sibling package, which pins the identical Playwright version and therefore
// shares the same on-disk browser cache) — so running this on every `build`/`test:seo` invocation
// costs nothing once the browser is already there.
//
// Deliberately no `--with-deps`: that flag shells out to the Linux system package manager (apt) to
// install OS-level shared libraries Chromium needs at runtime, which requires root and does not
// apply on Windows/macOS. CI keeps its own explicit `--with-deps` step (see
// .github/workflows/web-ui.yml, deploy.yml, ui-e2e.yml) for that Linux-specific concern — by the
// time this hook runs there, the browser binary is already present and this is a fast no-op; this
// script's own job is only to guarantee the binary itself is never silently absent anywhere else.

import { execFileSync, execSync } from 'node:child_process';

/** The exact `npx` invocation used to ensure the pinned Chromium build is present. Exported (not
 * inlined into `main`) so this is unit-testable without actually shelling out to Playwright's real
 * installer on every test run — there is no dry-run inspection API, so the meaningful, fast,
 * network-free thing to assert is that this is the exact command that would run. */
export function ensureChromiumCommand() {
  return { command: 'npx', args: ['playwright', 'install', 'chromium'] };
}

// On Windows, `npx` is a `.cmd` wrapper batch files cannot be spawned directly — it needs a shell.
// `execSync` takes the whole command as one string rather than `execFileSync(..., {shell: true})`
// with a separate args array, which Node deprecates (DEP0190) because the array elements are not
// shell-escaped there. Every argument here is a fixed token with no spaces, so plain concatenation
// into one string is unambiguous. POSIX spawns `npx` directly via `execFileSync` — no shell, no
// argument re-interpretation risk. Same pattern already established in this repo's
// agentforge4j-docs/scripts/build-javadoc.mjs.
function main() {
  const { command, args } = ensureChromiumCommand();
  if (process.platform === 'win32') {
    execSync(`${command} ${args.join(' ')}`, { stdio: 'inherit' });
  } else {
    execFileSync(command, args, { stdio: 'inherit' });
  }
}

if (process.argv[1]?.endsWith('ensure-chromium.mjs')) {
  main();
}
