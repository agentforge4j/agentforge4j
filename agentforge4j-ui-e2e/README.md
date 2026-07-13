# agentforge4j-ui-e2e

Playwright UI end-to-end tests for the AgentForge4j JavaScript packages. Browser-level coverage
that jsdom unit tests cannot provide (layout, React Flow rendering, real user flows).

This module is **not** part of the Maven reactor and is **not** published. Test tooling lives
only here; the shipped packages gain no runtime dependency.

## Layout

- `playwright.config.ts` — the `builder` project (drives `agentforge4j-workflow-builder`'s `dev/`
  harness).
- `playwright.web-ui.config.ts` — the `web-ui` project (drives the `agentforge4j-web-ui` public
  site's own production build/preview). A **separate config file**, not a second entry in
  `playwright.config.ts`'s `projects` array — Playwright starts every configured `webServer`
  regardless of which project `--project` selects, so one shared config would force each target's
  CI job to also build and boot the other. See that file's own header comment for the full
  rationale.
- `specs/builder/` — the `builder` project's specs.
- `specs/web-ui/` — the `web-ui` project's specs: every public route, desktop/mobile/footer
  navigation, responsive overflow across a representative viewport set, keyboard navigation, the
  Builder/Catalogue public entry surfaces (stops at "loads without crashing" — deep editor UX is
  the separate Workflow Builder usability workstream), and the GitHub-Pages-style static-hosting
  404 mechanism.
- `support/` — shared page objects and selector constants, one subfolder per project
  (`support/web-ui/routes.ts` is the single source of truth for the site's route list, expected
  headings, and representative viewport sizes).
- `fixtures/` — deterministic facts about the builder harness's sample workflow.

Projects added as each target becomes release-critical: `builder` (v1, the 1.0.0 gate); `web-ui`
(added for the 0.1.0 launch, once `agentforge4j-web-ui` had real routes/content to test against).

## Running locally

```bash
npm ci
npm run playwright:install      # one-off: download Chromium
npm run test:e2e:builder        # runs the builder project (starts its dev server automatically)
npm run test:e2e:web-ui         # runs the web-ui project (builds + previews the site automatically)
```

The `builder` project's `webServer` starts the workflow-builder dev server (`npm run dev`,
port 5173) and reuses an already-running one outside CI. The sibling
`../agentforge4j-workflow-builder` must have its dependencies installed (`npm ci` there).

The `web-ui` project's `webServer` runs `npm run build && npm run preview` inside
`../agentforge4j-web-ui` (port 4183) and reuses an already-running one outside CI. That sibling
module must have its own dependencies installed (`npm ci` there) first.

Chromium only, headless by default. Use `--headed` / `--debug` for local debugging (works with
either `npm run test:e2e:builder -- --headed` or `npm run test:e2e:web-ui -- --headed`).
