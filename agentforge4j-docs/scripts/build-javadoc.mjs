// SPDX-License-Identifier: Apache-2.0
//
// Builds the docs-side Javadoc surface WITHOUT changing any OSS production
// artifact (no module-info, no Automatic-Module-Name, no manifest edit). It produces three surfaces
// and stitches them under one /javadoc/next/ tree:
//
//   /javadoc/next/                     aggregate of the named modules that EXPORT public API
//   /javadoc/next/mcp/                 agentforge4j-mcp           (intentionally unnamed, classpath mode)
//   /javadoc/next/spring-boot-starter/ agentforge4j-spring-boot-starter (intentionally unnamed)
//   /javadoc/next/surfaces.html        generated landing page that makes the split explicit
//
// ServiceLoader provider modules that export no package carry no public API and are intentionally
// absent — the surface is complete by design, not partial. Requires JDK 17 and the OSS artifacts
// installed (`./mvnw install -Dmaven.test.skip=true`). Run via `npm run javadoc`.

import {execFileSync} from 'node:child_process';
import {cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(here, '..', '..');
const JAVADOC_MODULE = join(REPO_ROOT, 'agentforge4j-docs-javadoc');
const OUT_NEXT = join(JAVADOC_MODULE, 'build-javadoc', 'next');

// The intentionally-unnamed public modules, documented standalone (classpath mode) and stitched in.
const UNNAMED_PUBLIC = [
  {module: 'agentforge4j-mcp', dest: 'mcp'},
  {module: 'agentforge4j-spring-boot-starter', dest: 'spring-boot-starter'},
];

// The repo Maven wrapper — never a globally-installed `mvn`, so the build uses the repo-pinned
// Maven version wherever it runs. Absolute path from the repo root so the per-surface cwd swap
// below keeps working.
const MVNW = join(REPO_ROOT, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');

// Run the wrapper from `cwd`. On Windows the .cmd wrapper must go through a shell (batch files
// cannot be spawned directly); the command path is quoted because the checkout path may contain
// spaces, and every argument is a fixed token without spaces, so plain concatenation is unambiguous.
// POSIX spawns the wrapper directly — no shell, no argument re-interpretation.
function run(args, cwd) {
  console.log(`[build-javadoc] (cwd=${cwd}) mvnw ${args.join(' ')}`);
  if (process.platform === 'win32') {
    execFileSync(`"${MVNW}"`, args, {cwd, stdio: 'inherit', shell: true});
  } else {
    execFileSync(MVNW, args, {cwd, stdio: 'inherit'});
  }
}

function assertSurface(dir, label, minHtml) {
  const index = join(dir, 'index.html');
  if (!existsSync(index)) {
    throw new Error(`build-javadoc: ${label} produced no index.html at ${dir}`);
  }
  // Guard against a silently-empty/partial surface (e.g. masked failures): require some class pages.
  let htmlCount = 0;
  const walk = (d) => {
    for (const e of readdirSync(d, {withFileTypes: true})) {
      if (e.isDirectory()) {
        walk(join(d, e.name));
      } else if (e.name.endsWith('.html')) {
        htmlCount += 1;
      }
    }
  };
  walk(dir);
  if (htmlCount < minHtml) {
    throw new Error(`build-javadoc: ${label} looks partial — only ${htmlCount} html files (< ${minHtml})`);
  }
  console.log(`[build-javadoc] ${label}: ${htmlCount} html files OK`);
}

function copyInto(src, dest) {
  rmSync(dest, {recursive: true, force: true});
  mkdirSync(dirname(dest), {recursive: true});
  cpSync(src, dest, {recursive: true});
}

function documentedModules() {
  const elementList = join(OUT_NEXT, 'element-list');
  if (!existsSync(elementList)) {
    return [];
  }
  return readFileSync(elementList, 'utf8')
    .split(/\r?\n/)
    .filter((l) => l.startsWith('module:'))
    .map((l) => l.slice('module:'.length));
}

/**
 * The aggregator pom's `<module>` list, converted to the dotted module-name form Javadoc emits
 * (e.g. `../agentforge4j-util` -> `agentforge4j.util`). Same transform as the reverse assertion in
 * scripts/javadoc.test.mjs, so the two checks agree on what "the live named-public set" means.
 */
function pomModules() {
  const pom = readFileSync(join(JAVADOC_MODULE, 'pom.xml'), 'utf8');
  return [...pom.matchAll(/<module>\.\.\/(agentforge4j-[\w-]+)<\/module>/g)]
    .map((m) => m[1].replaceAll('-', '.'));
}

/** Assert the aggregate actually documents exactly the pom's module list — no silent drift. */
function assertMatchesPomModules(documented) {
  const expected = new Set(pomModules());
  const actual = new Set(documented);
  const missing = [...expected].filter((m) => !actual.has(m));
  const extra = [...actual].filter((m) => !expected.has(m));
  if (missing.length > 0 || extra.length > 0) {
    throw new Error(
      'build-javadoc: the aggregate does not match the pom\'s <modules> list — ' +
        `missing: [${missing.join(', ')}], extra: [${extra.join(', ')}]`,
    );
  }
}

function writeSurfacesLanding(modules) {
  const moduleItems = modules.map((m) => `      <li><code>${m}</code></li>`).join('\n');
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>AgentForge4j API — surfaces</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 50rem; margin: 2rem auto; padding: 0 1rem; line-height: 1.5; }
    code { background: #f2f2f2; padding: 0 .25rem; border-radius: 3px; }
    a { color: #1a6; }
  </style>
</head>
<body>
  <h1>AgentForge4j API (next)</h1>
  <p>The API reference is split into three independently-generated surfaces, stitched here:</p>
  <ul>
    <li><a href="./index.html">Core API (aggregate)</a> — the named modules that export public API.</li>
    <li><a href="./mcp/index.html">MCP integration</a> — <code>agentforge4j-mcp</code> (classpath-mode module).</li>
    <li><a href="./spring-boot-starter/index.html">Spring Boot starter</a> — <code>agentforge4j-spring-boot-starter</code> (classpath-mode module).</li>
  </ul>
  <h2>Why the split</h2>
  <p>
    Most of the framework is built as named JPMS modules and is documented together in the
    <a href="./index.html">aggregate</a>. Two public modules — <code>agentforge4j-mcp</code> and
    <code>agentforge4j-spring-boot-starter</code> — are intentionally <em>unnamed</em> (no module
    descriptor), so Javadoc cannot place them in the same modular aggregate; they are documented
    separately and linked above. This is a documentation-generation constraint only; it changes
    nothing about the modules at runtime.
  </p>
  <p>
    Provider modules that only register a service-provider implementation and <strong>export no
    package</strong> (for example the OpenAI, Claude, and Bedrock providers) carry no public API, so
    they are intentionally absent from the aggregate — the surface is complete by design.
  </p>
  <h2>Modules in the aggregate</h2>
  <ul>
${moduleItems}
  </ul>
</body>
</html>
`;
  writeFileSync(join(OUT_NEXT, 'surfaces.html'), html, 'utf8');
}

function main() {
  rmSync(join(JAVADOC_MODULE, 'build-javadoc'), {recursive: true, force: true});
  mkdirSync(OUT_NEXT, {recursive: true});

  // 1. Aggregate of the named, exported-API modules (strict doclint, fails on real errors).
  run(['-q', 'javadoc:aggregate', '-Dmaven.test.skip=true'], JAVADOC_MODULE);
  copyInto(join(JAVADOC_MODULE, 'target', 'reports', 'apidocs'), OUT_NEXT);
  assertSurface(OUT_NEXT, 'aggregate', 100);

  const modules = documentedModules();
  assertMatchesPomModules(modules);
  console.log(`[build-javadoc] aggregate documents ${modules.length} modules (matches the pom): ${modules.join(', ')}`);

  // 2. The intentionally-unnamed public modules, standalone (classpath mode). doclint relaxed: their
  //    cross-module @links to the named API cannot resolve in isolation (no OSS pom change to wire
  //    -linkoffline); those degrade to non-fatal warnings, intra-module references are intact.
  for (const {module, dest} of UNNAMED_PUBLIC) {
    run(['-q', '-pl', module, 'javadoc:javadoc', '-Dmaven.test.skip=true',
      '-Ddoclint=none', '-Dmaven.javadoc.failOnError=false'], REPO_ROOT);
    copyInto(join(REPO_ROOT, module, 'target', 'reports', 'apidocs'), join(OUT_NEXT, dest));
    assertSurface(join(OUT_NEXT, dest), dest, 10);
  }

  // 3. The landing page that makes the split explicit.
  writeSurfacesLanding(modules);
  console.log(`[build-javadoc] stitched surface at ${OUT_NEXT} (entry: surfaces.html)`);
}

main();
