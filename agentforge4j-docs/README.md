# agentforge4j-docs

The AgentForge4j OSS documentation site, built with [Docusaurus](https://docusaurus.io/).

This is a standalone Node module (npm, not part of the Maven reactor). It uses the Node
version pinned by the repository root `.nvmrc`.

## Local development

`npm start`/`npm run build` render generated content and validate `vocab:`-tagged identifiers
against sets the build produces from source — the vocabulary lint fails fast if `src/vocab` is
missing. Before starting or building for the first time (and after pulling framework changes),
generate that content from the OSS reactor once, from the repository root:

```bash
./mvnw install -Dmaven.test.skip=true                              # 1. install the OSS reactor (JDK 17)
./mvnw -f agentforge4j-docs-emitter/pom.xml exec:java \
  -Dexec.args="$(pwd)/agentforge4j-docs/scripts/emitter-output"    # 2. run the docs emitter
```

Then, from this module:

```bash
npm ci             # install dependencies (uses the committed package-lock.json)
npm run generate   # regenerate the reference pages + vocabulary sets from the emitter output
npm start          # start the dev server with hot reload
npm run build      # build the static site; fails on broken links or anchors
```

See [Contributing](docs/contributing/index.mdx) for the full authoring and gate-running guide.

### The build reads real Git history

Every page's sitemap `<lastmod>` is derived from its own source file's `git log`, and the build
fails rather than publish a page with a missing or invented date. Three consequences worth knowing
before you hit them:

- **Build from a real Git checkout, with `git` on `PATH`.** There is no fallback: an extracted
  source tarball, a release archive, or a container build context that leaves out `.git` cannot
  produce these dates, and the build says so rather than publishing pages with none.
- **Build from a full clone.** A shallow clone (`--depth`, or a CI checkout without
  `fetch-depth: 0`) has no per-file history to read; the build refuses to run against one rather
  than bake in wrong dates.
- **Commit a release cut before building it.** `npm run docs:scratch-cut` and a real release cut
  both add the new version to `versions.json` — so its pages are built and advertised — while
  `versioned_docs/version-<v>/` is still untracked and therefore has no history yet. Commit the
  cut first; building in between fails with a message saying exactly this.

## Quality gates

```bash
npm run lint:frontmatter   # validate page frontmatter (kind/audience/diataxis contract)
npm run lint:nav           # assert every page is reachable from a sidebar
npm run lint               # both lints
npm run typecheck          # TypeScript type-check
npm run check              # lint + typecheck + build
```

## Layout

- `docs/` — the current ("next") documentation set, served at `/docs/next`.
- `sidebars.ts` — the navigation, organised on the Diátaxis spine.
- `scripts/` — the frontmatter and navigation lint scripts.
- `src/` — custom CSS and (later) components.
- `docusaurus.config.ts` — site configuration (`baseUrl: '/docs/'`, docs `routeBasePath: '/'`).
