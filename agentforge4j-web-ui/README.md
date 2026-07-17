# agentforge4j-web-ui

The public website for **agentforge4j.org** — a Vite/React single-page application composed with
the Docusaurus docs stack and Javadoc into one GitHub Pages artifact.

## What it is

This module was originally a thin standalone host for the
[`@agentforge4j/workflow-builder-react`](../agentforge4j-workflow-builder/README.md) component. Its
content and purpose have been superseded wholesale by the `.org` site, per design; the module
identity carries forward unchanged (`agentforge4j-web-ui`), and there is no separate sibling
module — this is it.

`/builder` is one route among several (Home, Docs handoff, Use, Catalogue, Builder, Architecture,
Releases, Community, Security, Legal, Contact); it is not the app's sole purpose any more. It is a
private application (not published to npm) and is independent of the Maven reactor.

## Structure

- **Routing** (`react-router-dom`): the launch-required routes listed above, plus a catch-all 404.
  `/builder` and `/catalogue` are real embeds (workflow-builder component; generated catalogue
  data), lazy-loaded on demand; the rest carry real authored copy.
- **Nav/footer**: data-driven from `src/config/nav.ts`, internal to this module for now (no
  cross-build sharing with the Docusaurus navbar yet).
- **Branding**: the canonical logo (`public/brand/logo-horizontal.svg`) and the palette recorded in
  the repository's `BRAND.md`; `favicon.ico`/`apple-touch-icon.png`/`brand/icon-512.png` are
  generated derivatives of it.
- **Committed-content gate**: `scripts/lint-content-gate.mjs` scans this module's own `.ts`/`.tsx`
  sources under `src/`, plus its other committed prose surfaces (`README.md`, `index.html`,
  `nginx.conf`, `Dockerfile.local`, `public/robots.txt`) against both term groups defined in
  `agentforge4j-docs/scripts/` (product-boundary + attribution), imported via a relative path —
  not a duplicated copy. Generated/build output (`dist/`, `node_modules/`, `.tsbuildinfo` caches)
  is never in scope.
- **404**: `scripts/copy-404.mjs` ships `dist/404.html` as a byte-identical copy of `dist/index.html`
  after every build, so GitHub Pages serves a real HTTP 404 with the site's own branded not-found
  page.
- **Styling**: Tailwind CSS 4, semantic design tokens in `src/styles/tokens.css`.

## Local development

```bash
cd agentforge4j-web-ui
npm install
npm run dev
```

To develop against the **unpublished** workflow-builder source instead of the released npm package,
use:

```bash
npm run dev:local
```

which sets `AFB_LOCAL_BUILDER=1` so Vite resolves the builder from
`../agentforge4j-workflow-builder/src`.

## Build and verify

```bash
npm run check
```

Runs lint, the committed-content gate, typecheck, the Vitest component/route/a11y suite, the
content-gate integration test, and the production build (including the 404 mechanism) end to end.

## License

Apache License 2.0 — see the root [LICENSE](../LICENSE) and the [project README](../README.md).
