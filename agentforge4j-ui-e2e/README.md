# agentforge4j-ui-e2e

Playwright UI end-to-end tests for the AgentForge4j JavaScript packages. Browser-level coverage
that jsdom unit tests cannot provide (layout, React Flow rendering, real user flows).

This module is **not** part of the Maven reactor and is **not** published. Test tooling lives
only here; the shipped packages gain no runtime dependency.

## Layout

- `playwright.config.ts` — single config; one Playwright project per target.
- `specs/builder/` — the `builder` project (drives `agentforge4j-workflow-builder`'s `dev/` harness).
- `support/` — shared page objects and selector constants.
- `fixtures/` — deterministic facts about the harness sample workflow.

Projects added as each target becomes release-critical: `builder` (v1, the 1.0.0 gate); `web-ui`
is deferred (additive, lands when `agentforge4j-web-ui` is a supported OSS application).

## Running locally

```bash
npm ci
npm run playwright:install      # one-off: download Chromium
npm run test:e2e:builder        # runs the builder project (starts the dev server automatically)
```

The `builder` project's `webServer` starts the workflow-builder dev server (`npm run dev`,
port 5173) and reuses an already-running one outside CI. The sibling
`../agentforge4j-workflow-builder` must have its dependencies installed (`npm ci` there).

Chromium only, headless by default. Use `--headed` / `--debug` for local debugging.
