# agentforge4j-docs

The AgentForge4j OSS documentation site, built with [Docusaurus](https://docusaurus.io/).

This is a standalone Node module (npm, not part of the Maven reactor). It uses the Node
version pinned by the repository root `.nvmrc`.

## Local development

```bash
npm ci          # install dependencies (uses the committed package-lock.json)
npm start       # start the dev server with hot reload
npm run build   # build the static site; fails on broken links or anchors
```

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
