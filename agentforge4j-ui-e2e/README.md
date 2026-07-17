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
  covered by the Workflow Builder's own suites), and `hosting.spec.ts`, which pins the plain SPA
  build's GitHub-Pages SPA-fallback behaviour — see "Hosting" below.
- `support/` — shared page objects and selector constants, one subfolder per project
  (`support/web-ui/routes.ts` is the single source of truth for the site's route list, expected
  headings, and representative viewport sizes).
- `fixtures/` — deterministic facts about the builder harness's sample workflow.

## Running locally

```bash
npm ci
npm run playwright:install      # one-off: download Chromium
npm run test:e2e:builder        # runs the builder project (starts its dev server automatically)
npm run test:e2e:web-ui         # runs the web-ui project (builds + previews the site automatically)
npm run test:e2e                # runs both suites, one after the other
```

`test:e2e:builder` and `test:e2e:web-ui` use separate Playwright config files (see Layout above)
because each has its own `webServer`; `test:e2e` is a plain sequential aggregate of the two, not a
third config.

The `builder` project's `webServer` starts the workflow-builder dev server (`npm run dev`,
port 5173) and reuses an already-running one outside CI. The sibling
`../agentforge4j-workflow-builder` must have its dependencies installed (`npm ci` there).

The `web-ui` project's `webServer` runs `npm run build && npm run preview` inside
`../agentforge4j-web-ui` (port 4183) and reuses an already-running one outside CI. That sibling
module must have its own dependencies installed (`npm ci` there) first.

Chromium only, headless by default. Use `--headed` / `--debug` for local debugging (works with
either `npm run test:e2e:builder -- --headed` or `npm run test:e2e:web-ui -- --headed`).

## Hosting

`specs/web-ui/hosting.spec.ts` pins how the plain (un-assembled) `agentforge4j-web-ui` SPA build
behaves on GitHub-Pages-style static hosting: any known route with no matching on-disk file
(every route except `/`) is served via the `404.html` SPA-fallback (`scripts/copy-404.mjs` makes
it byte-identical to `index.html`), so the response carries a real HTTP 404 status and the page
then boots the SPA client-side to render the correct route. The spec runs a minimal
GitHub-Pages-equivalent static server against the already-built `dist/` rather than Vite's preview
server, whose built-in SPA-fallback middleware would return 200 and mask whether the `404.html`
mechanism itself works.

That 404-for-known-routes behaviour is an artifact of serving a client-side-routed SPA from plain
static hosting; it is **not** the production hosting contract. The deployable production artifact
is the composed site produced by the Assembler (`agentforge4j-docs/scripts/assemble-site.mjs` —
SPA + docs + Javadoc), whose hosting contract is:

- every known public route (e.g. `/architecture`, `/catalogue`, `/community`) returns HTTP 200;
- every known catalogue detail route (e.g. `/catalogue/<workflow-id>` for a real workflow id)
  returns HTTP 200;
- only genuinely unknown routes (no matching public or catalogue-detail route) return HTTP 404.

`hosting.spec.ts` covers the plain SPA build only; it does not assert the composed artifact's
route contract.

## Visual review

A local, repeatable visual-review system for the **assembled** agentforge4j.org site (SPA + docs +
Javadoc composed by `assemble-site.mjs` — not the plain SPA build `hosting.spec.ts` above tests)
and the Workflow Builder's public entry surface. Separate from the functional suites above: this
suite answers "does it *look* right", not "does it route/render correctly" (the functional suites'
job, reused rather than duplicated — see `visual/manifest.ts`, which imports
`support/web-ui/routes.ts` directly instead of re-listing routes).

**Scope note:** this tooling exposes visual problems on the Workflow Builder's public entry
surface; it does not implement, redesign, or fix anything tracked by the dedicated builder-
usability issues (#94-#103). Findings that intersect those issues are tagged `knownIssues` in the
manifest and called out separately in the generated report, never silently duplicated or fixed
here. `knownIssues` is informational only — a real GitHub issue number tagged on an entry does
not, by itself, exempt anything from the strict release check. The only thing that does is a
`checkId`-specific `acceptedFindings` entry (which may itself reference an issue number). This
distinction matters: tagging an entry with a known issue must never silently suppress a completely
unrelated failure that later appears on that same entry.

### Layout

- `visual/manifest.ts` — the single source of truth: every captured `.org` route/state and Builder
  state, its viewport group, any interaction needed to reach it, required/forbidden visible
  elements, masked (genuinely dynamic) regions, whether AI review applies, and release importance.
- `visual/interactions.ts` — named interaction functions (`addSampleSteps`, `openMobileNav`, …)
  the manifest references by name.
- `visual/checks.ts` — the non-AI deterministic checks (overflow, clipped panels, missing/zero-size
  required elements, forbidden elements present, blank content, invisible primary actions,
  overlapping fixed chrome, nav-covers-content). Pure pass/fail logic (`evaluateDeterministicChecks`,
  `evaluateRuntimeSignals`) is exercised without a browser by `specs/visual-unit/checks.spec.ts`.
- `playwright.visual.config.ts` — the `visual` project. Two `webServer`s: the composed site (via
  `scripts/visual/serve-assembled-site.mjs`, port 4184) and, for the one manifest entry that needs
  it, the workflow-builder dev harness (port 5173) — see the manifest's `builder-readonly-mode`
  entry for why read-only mode is captured there and not on the public site.
- `playwright.visual-unit.config.ts` — pure-logic tests for `visual/checks.ts`/`visual/manifest.ts`,
  no browser/webServer.
- `specs/visual/capture.spec.ts` — generates one test per manifest entry × viewport: navigates,
  disables animations, runs any interaction, screenshots, runs the deterministic checks, writes one
  result file per test to `visual-output/results/`.
- `scripts/visual/`:
  - `build-assembled-site.mjs` — reproduces `deploy.yml` locally (slow: full OSS reactor + Javadoc
    + Docusaurus build) to produce `agentforge4j-docs/_site`; also writes the build-provenance
    marker (see `site-provenance.mjs`).
  - `site-provenance.mjs` — writes/reads/evaluates `_site/.build-provenance.json`, the marker
    `release-check.mjs` uses to prove the composed site reflects current, durable (committed,
    non-dirty) relevant source — not just that `index.html` happens to exist.
  - `serve-assembled-site.mjs` — a real, GitHub-Pages-equivalent static server for `_site`
    (on-disk-file-or-404.html, never SPA-fallback middleware).
  - `ai-review.mjs` — optional, disabled-by-default AI vision review (see below).
  - `generate-report.mjs` — merges `visual-output/results/*.json` + the optional AI review output
    into `visual-output/report.md` (human) and `report.json` (machine).
  - `view-report.mjs` — prints the summary and best-effort opens `report.md`.
  - `write-attestation.mjs` — writes the small, committed `visual-evidence/attestation.json`.
  - `check-freshness.mjs` — compares the attestation against live repo state; warn-only by default,
    `--strict` for a blocking pre-release gate.
  - `release-check.mjs` — the one-shot "capture → report → attestation → strict freshness check"
    command.
- `.github/workflows/visual-freshness.yml` — CI warning (never blocking, never calls an AI model or
  a browser) when the committed attestation is missing, stale, or reports a failure.

### Local commands

```bash
npm ci
npm run playwright:install            # one-off: download Chromium

npm run visual:build-site             # slow — full local build of the composed .org site
npm run visual:serve-site             # serve an already-built _site standalone, for manual browsing

npm run visual:capture                # screenshots + deterministic checks (needs the site built+served,
                                       # or set VISUAL_TARGET_URL to an already-running instance).
                                       # Its own `previsual:capture` pre-hook clears every prior
                                       # run's results/screenshots/AI output first, so a stale or
                                       # removed manifest entry's evidence never lingers into a new
                                       # report.
npm run test:visual-unit              # pure-logic tests for checks.ts/manifest.ts/generate-report.mjs
                                       # /serve-assembled-site.mjs, no browser

npm run visual:ai-review              # optional — see "AI review" below; no-ops cleanly if unconfigured
npm run visual:report                 # merge results (+ AI review, if run) into report.md/report.json
npm run visual:view-report            # print the summary, open report.md

npm run visual:attestation            # write visual-evidence/attestation.json from the latest report
npm run visual:freshness-check        # warn-only staleness check (what CI runs)
npm run visual:release-check          # one-shot: capture -> report -> attestation -> STRICT freshness check
```

`npm run visual:capture` targets `http://localhost:4184` by default (the static server this
module's own `webServer` starts against `agentforge4j-docs/_site`). Set `VISUAL_TARGET_URL` to
point it at an already-running instance instead (e.g. a preview host), so capturing doesn't
require a fresh local build every run.

### The valid review → commit → attestation sequence

`npm run visual:release-check` (strict mode) refuses to run — with a clear, specific reason — unless
this sequence is followed, so the resulting `attestation.json` is durable evidence of a real,
reviewable source state, not a snapshot that goes stale the moment anyone looks away:

1. Make your relevant source changes (anything `check-freshness.mjs`'s `RELEVANT_PATH_PATTERN`
   matches: `agentforge4j-web-ui/`, `agentforge4j-workflow-builder/`, `agentforge4j-docs/`,
   `agentforge4j-ui-e2e/` outside `visual-evidence/`, `agentforge4j-workflows-catalog/`,
   `agentforge4j-schema/`, `.nvmrc`, `.github/workflows/ui-e2e.yml`).
2. **Commit them.** A strict release check reviews a durable, committed source state — not a working
   tree that can change again before anyone reads the result. `visual:release-check` fails immediately,
   naming the exact dirty file(s), if any relevant file is uncommitted (tracked or untracked) at
   either the assembled-site-build step or the freshness-check step.
3. `npm run visual:build-site` (slow — full local composed-site build). Records a
   `_site/.build-provenance.json` marker: the commit it was built from, plus (informationally) any
   relevant file that was dirty at that exact moment.
4. `npm run visual:release-check` — rebuilds capture evidence fresh, then fails closed unless: the
   assembled site's provenance marker matches the *current* commit with nothing dirty at build time
   *or since*, real capture evidence exists (not zero captures, not a missing
   `expected-inventory.json`), and every deterministic check passes (or is explicitly
   `acceptedFindings`-exempted).
5. On `PASS`, commit the refreshed `visual-evidence/attestation.json` — that's the artifact CI's
   warn-only freshness check (`.github/workflows/visual-freshness.yml`) reads.

Skipping step 2 (committing first) is the most common way to hit a `release-check` failure: both the
assembled-site provenance check and the freshness check's own dirty-tree gate exist specifically to
catch it, rather than silently attesting a source state that was never actually committed anywhere.

This commit-anchored design was reviewed against the alternative (attesting a hash of the dirty
working tree instead, letting strict review run before committing) and kept deliberately — see
`check-freshness.mjs`'s own `getDirtyRelevantFiles()` doc comment for the full trade-off analysis.
Short version: a commit sha's content is durably retrievable forever; a hash over uncommitted
content can attest to bytes that may never exist anywhere else, which is a weaker integrity
guarantee for a real workflow benefit (dirty-tree review) `visual:capture`/`visual:report`
(non-strict) already provide.

### AI review

Disabled by default. Enable with:

```bash
export AI_VISUAL_REVIEW_ENABLED=true
export AI_VISUAL_REVIEW_API_KEY=...        # required
export AI_VISUAL_REVIEW_MODEL=gpt-4o-mini  # default shown — cheapest OpenAI vision model at time of
                                            # writing; override rather than treat as permanent
export AI_VISUAL_REVIEW_BASE_URL=...       # default: https://api.openai.com/v1 — any OpenAI-
                                            # compatible vision endpoint works
export AI_VISUAL_REVIEW_MAX_SCREENSHOTS=20 # budget cap, highest release-importance reviewed first
npm run visual:ai-review
```

Judges visual presentation only (clipping, overlap, alignment, readability, responsive adaptation,
missing/distorted content, spacing, overlays hiding controls) — never backend functionality,
architecture, or feature completeness. Findings are review suggestions, not proven defects — the
report marks every finding `humanConfirmationRequired` and never auto-fails a build on an AI
finding alone (only deterministic checks affect `overallStatus`). **CI never calls this script.**

### What's committed vs. generated

| Path | Committed? |
|---|---|
| `visual/`, `playwright.visual*.config.ts`, `specs/visual*/`, `scripts/visual/` | Yes — the tooling itself |
| `visual-evidence/attestation.json` | Yes — small metadata only (commit, manifest hash, timestamp, pass/fail); this is what CI's freshness check reads |
| `visual-output/` (screenshots, per-capture results, `report.md`/`report.json`) | **No** — gitignored, regenerate locally with `npm run visual:capture && npm run visual:report` |

Nothing under `visual-output/` is uploaded as a CI artifact today (no CI job runs the capture
suite — see "CI never calls this script" above and `visual-freshness.yml`'s own scope). If a future
CI job does run `visual:capture` (e.g. as a manual `workflow_dispatch`), upload `visual-output/
report.md` + `report.json` as a build artifact rather than committing them, and never upload raw
screenshots as a permanent repository asset — retain them locally only.
