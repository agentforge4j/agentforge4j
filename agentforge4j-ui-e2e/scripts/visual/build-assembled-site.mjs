// SPDX-License-Identifier: Apache-2.0
//
// Reproduces `.github/workflows/deploy.yml` + `.github/actions/build-docs-site/action.yml`
// locally, without GitHub Pages/Actions-specific steps (Configure Pages, upload-pages-artifact),
// to produce the same `agentforge4j-docs/_site` a real Pages deploy would publish. Slow (a full
// OSS reactor install + Javadoc + Docusaurus build) — meant to be run occasionally to refresh the
// composed site before a visual-review pass, not on every capture.
//
// Requires JDK 17 (`JAVA_HOME` must point at it — this repo's OSS builds are JDK 17-only; newer
// JDKs are known to crash SpotBugs and are not supported here) and Node (`.nvmrc`-pinned) already
// on PATH. Does not set JAVA_HOME itself — fails closed with a clear message if the active `java
// -version` isn't 17, rather than silently building with the wrong toolchain.
//
// Usage: node scripts/visual/build-assembled-site.mjs

import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { run as runShared } from './run-command.mjs';
import { getDirtyRelevantFiles } from './check-freshness.mjs';
import { writeProvenanceMarker } from './site-provenance.mjs';

const here = fileURLToPath(new URL('.', import.meta.url));
const REPO_ROOT = resolve(here, '..', '..', '..');
const IS_WINDOWS = process.platform === 'win32';
// cmd.exe (used via `shell: true` below) does not search the current directory for an executable
// unless it's explicitly relative-pathed — a bare `mvnw.cmd` silently resolves against PATH only
// and fails with "not recognized" even though the wrapper script sits right in REPO_ROOT.
const MVNW = IS_WINDOWS ? '.\\mvnw.cmd' : './mvnw';

const run = (command, args, cwd) => runShared('[build-assembled-site]', command, args, cwd, IS_WINDOWS);

function checkJdk17() {
  // `java -version` writes to stderr, not stdout — `execFileSync`'s return value only ever
  // carries stdout, so reading it here would silently see an empty string and always report "not
  // JDK 17" regardless of the real version. `spawnSync` exposes both streams directly.
  const result = spawnSync('java', ['-version'], { encoding: 'utf8' });
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`;
  if (!/version "17\./.test(output) && !/version "17"/.test(output)) {
    console.error('[build-assembled-site] the active `java -version` is not JDK 17:');
    console.error(output || '(no output captured — is `java` on PATH at all?)');
    console.error('  This repo\'s OSS build is JDK 17-only. Point JAVA_HOME at a JDK 17 install and retry.');
    process.exit(1);
  }
}

function main() {
  checkJdk17();

  run('npm', ['ci'], resolve(REPO_ROOT, 'agentforge4j-web-ui'));
  // agentforge4j-web-ui's own `build` script prerenders every route in headless Chromium
  // (scripts/prerender-routes.mjs) and provisions that browser automatically via its own
  // `prebuild` lifecycle hook (scripts/ensure-chromium.mjs) — no separate install step needed here.
  run('npm', ['run', 'build'], resolve(REPO_ROOT, 'agentforge4j-web-ui'));

  run(MVNW, ['-B', '-e', '-q', '-DskipTests', '-Dmaven.test.skip=true', 'install'], REPO_ROOT);

  // Relative arg, deliberately not an absolute `${REPO_ROOT}/...` path: exec-maven-plugin splits
  // `-Dexec.args` on whitespace with no quoting support, which silently truncates any argument
  // built from an absolute path that contains a space (this repo's own worktree paths routinely
  // do, e.g. "C:\Home Assistant\repo\..." — a real defect an earlier session hit and worked around
  // with a space-free directory junction). A relative path has no such risk regardless of where
  // the repo happens to be checked out. Resolved relative to REPO_ROOT, not the emitter module's
  // own basedir: confirmed empirically (not assumed from exec-maven-plugin's docs, which turned
  // out not to match observed behaviour here) — this whole command already runs with REPO_ROOT as
  // its cwd (the `run()` helper's own `cwd` argument, mvnw's invocation directory), and that's
  // what exec:java's `user.dir` actually reflects. A first attempt using `../agentforge4j-docs/...`
  // (assuming a module-basedir-relative resolution) landed one directory ABOVE the repo instead.
  run(
    MVNW,
    ['-B', '-q', '-f', 'agentforge4j-docs-emitter/pom.xml', 'compile', 'exec:java', '-Dexec.args=agentforge4j-docs/scripts/emitter-output'],
    REPO_ROOT,
  );

  run(MVNW, ['-B', '-q', '-f', 'agentforge4j-examples/pom.xml', '-Dmaven.test.skip=true', 'install'], REPO_ROOT);

  const docsDir = resolve(REPO_ROOT, 'agentforge4j-docs');
  run('npm', ['ci'], docsDir);
  run('npm', ['run', 'javadoc'], docsDir);
  run('npm', ['run', 'lint:javadoc-links'], docsDir);
  // `npm run check` (generate/lint/typecheck/test/build) rather than a bare `npm run build`: the
  // deploy workflow's own composite action runs the full check, and a docs-side regression should
  // fail this build the same way it would fail a real deploy, not be silently skipped for speed.
  run('npm', ['run', 'check'], docsDir);
  run('npm', ['run', 'a11y'], docsDir);

  run('node', ['scripts/build-javadoc-versions.mjs'], docsDir);
  run('node', ['scripts/assemble-site.mjs'], docsDir);

  const siteDir = resolve(docsDir, '_site');
  if (!existsSync(resolve(siteDir, 'index.html'))) {
    console.error(`[build-assembled-site] assemble-site.mjs reported success but ${siteDir}/index.html is missing`);
    process.exit(1);
  }

  // Records what this build actually reflects, so release-check.mjs can prove the composed site
  // is current before trusting it as evidence — existsSync(index.html) alone only proves *a* site
  // exists, never that it's fresh. commitSha is, since git is content-addressed, already a
  // deterministic hash over the entire relevant source tree at that commit; pairing it with the
  // dirty-relevant-files list taken at this exact moment is what lets a later check tell a build
  // taken from a clean, durable commit apart from one taken mid-edit.
  const commitSha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: REPO_ROOT, encoding: 'utf8' }).trim();
  const marker = writeProvenanceMarker(siteDir, { commitSha, dirtyRelevantFilesAtBuildTime: getDirtyRelevantFiles() });
  if (marker.dirtyRelevantFilesAtBuildTime.length > 0) {
    console.warn(
      `[build-assembled-site] WARNING: built while relevant source file(s) were uncommitted: ` +
        `${marker.dirtyRelevantFilesAtBuildTime.join(', ')} — release-check.mjs will refuse this build ` +
        'until those changes are committed and the site is rebuilt.',
    );
  }
  console.log(`[build-assembled-site] composed site ready at ${siteDir} (commit ${commitSha.slice(0, 12)})`);
}

main();
