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
- `playwright.builder-functional.config.ts` — the `desktop`/`tablet`/`mobile`/`known-issues`
  projects (drives `agentforge4j-workflow-builder`'s dev harness through a production-style
  build+preview) — see
  "Functional testkit (Builder)" below.
- `specs/builder/` — the `builder` project's specs.
- `specs/builder-functional/` — the functional-testkit specs (`desktop`/`tablet`/`mobile`
  projects) — see "Functional testkit (Builder)" below.
- `specs/web-ui/` — the `web-ui` project's specs: every public route, desktop/mobile/footer
  navigation, responsive overflow across a representative viewport set, keyboard navigation, the
  Builder/Catalogue public entry surfaces (stops at "loads without crashing" — deep editor UX is
  covered by the Workflow Builder's own suites), and `hosting.spec.ts`, which pins the plain SPA
  build's GitHub-Pages SPA-fallback behaviour — see "Hosting" below.
- `support/` — shared page objects and selector constants, one subfolder per project
  (`support/web-ui/routes.ts` is the single source of truth for the site's route list, expected
  headings, and representative viewport sizes; `support/viewports.ts` and `support/known-issues.ts`
  are the functional testkit's equivalents).
- `fixtures/` — deterministic facts about the builder harness's sample workflow and the functional
  testkit's named fixtures (`builder-functional-fixtures.ts`).

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

## Functional testkit (Builder)

Browser-level functional coverage of the Workflow Builder's user-visible behaviour — creation,
editing, validation, persistence, undo/redo, step-type coverage, accessibility, responsive
behaviour, and export/output correctness. Complements (does not replace) `specs/builder/` (desktop-
only, drives the dev harness's own `npm run dev`) and the package's own Vitest component tests
(`agentforge4j-workflow-builder/tests/`): this suite runs against a **production-style build**
(`vite build` + `vite preview`) across a **viewport matrix**, and is the only suite that
functionally exercises persistence, undo/redo, actionable validation, the start-step chooser, and
export confirmation end-to-end in a real browser.

**Origin and scope note:** this testkit was built to establish existing behaviour and expose
regressions for the Workflow Builder usability remediation (GitHub issues #94-#103). By the time it
was built, all six remediation PRs (#105-110) had already merged and every one of #94-#103 was
closed — so every test here asserts the **current, fixed** behaviour as a mandatory regression gate,
not a quarantined "known-broken" scenario. See "Known-issue mapping" below. This testkit does not
implement, redesign, or fix Builder behaviour — only tests it.

### Layout

- `playwright.builder-functional.config.ts` — 4 projects (`desktop`, `tablet`, `mobile` — mandatory
  regression tests; `known-issues` — quarantined tests only, see "Regression vs. quarantined tests"
  below), one `webServer` (`npm run build:dev-harness && npm run preview` in
  `agentforge4j-workflow-builder`, a production-style build+serve — separate from
  `playwright.config.ts`'s dev-server `builder` project for the same "don't couple unrelated CI
  jobs' webServers" reason `playwright.web-ui.config.ts` documents).
- `specs/builder-functional/f1-*.spec.ts` … `f12-*.spec.ts` — one file per behaviour area (see the
  inventory table below). Tests targeting a single viewport are tagged `@tablet-only`/`@mobile-only`
  in their title; tests meant to run at every viewport are tagged `@responsive`. Untagged tests run
  once, under the `desktop` project only.
- `agentforge4j-workflow-builder/dev/fixtures.ts` — the named `WorkflowDefinition` fixtures
  (`empty`, `simple-valid`, `multi-step`, `all-step-types`), selected via `?fixture=<name>` on the
  dev harness; `'none'` omits `initialWorkflow` entirely (see "Fixtures" below). Dev-harness-only,
  excluded from the published package, same category as the pre-existing `?mode=`/`?caps=` params.
- `fixtures/builder-functional-fixtures.ts` — deterministic facts about those fixtures (node
  counts, names) specs assert against, mirroring the existing `fixtures/builder-fixture.ts`
  convention (specs never import the `WorkflowDefinition` objects themselves).
- `support/viewports.ts` — the viewport matrix (`mobileNarrow`, `mobileStandard`, `tablet`,
  `desktop`, `largeDesktop`).
- `support/known-issues.ts` — the generic regression/quarantine mechanism: `knownIssueTest()` and
  a `KnownIssue` registry (`status: 'open' | 'closed'`, for traceability — not runtime branching).
  See "Regression vs. quarantined tests" below.
- `support/builder-page.ts` — extended (not replaced) with locators/helpers for the
  unreleased-at-testkit-creation-time surfaces: undo/redo, the draft-restored banner, the
  start-step chooser/badge, the delete-confirmation dialog, and the validation pill/popover.

### Behaviour inventory

| Spec | Behaviour area | Issue(s) covered |
|---|---|---|
| `f1-initial-state` | Load without errors, empty/starter state, deterministic load, initial selection, required controls | — |
| `f2-creation-journey` | Create → name → configure → add → connect → validate → export | — |
| `f3-existing-workflow-journey` | Load fixture → confirm nodes/edges → edit → add/remove → validate → re-export | — |
| `f4-invalid-workflow-and-validation` | Invalid state via UI, meaningful validation output, popover occlusion, checklist accuracy | #95, #101 |
| `f5-readonly-journey` | Inspection/pan/zoom/selection/validation/export work; editing affordances absent | — |
| `f6-step-type-coverage` | Every COMMON+FLOW palette kind: discoverable, addable, selectable, configurable | #99 (regression) |
| `f7-persistence` | Reload restores draft; Start fresh; Dismiss; fixture-seeded mount never restores | #94 |
| `f8-undo-redo-and-delete-safety` | Toolbar + keyboard undo/redo; delete confirmation Cancel/Confirm; deletion itself undoable | #96 |
| `f9-start-step-and-export-confirmation` | Start badge/chooser; mode/read-only scoping; export confirmation | #100, #102 |
| `f10-accessibility-keyboard` | Accessible names, keyboard activation, Escape, focus trap, focus restoration | — |
| `f11-responsive` | Mobile gate (notice replaces editor, no overflow), tablet full palette access, tablet creation smoke | #97, #98, #99, #103 (regression) |
| `f12-output-correctness` | Export contract shape, edited values present, deleted values absent, order, determinism, all-step-types round-trip | — |

Not in scope: the 4 `advanced`-group palette kinds (`AI_DEBATE`, `LOAD_RESOURCE`, `STOP`,
`RETRY`) as live palette-add-and-configure journeys — issue #99 frames "step types" as the 6
COMMON+FLOW kinds (`3 of 6 step types...`); extending `f6`'s loop to the 4 advanced kinds is a
natural, low-risk follow-up, not attempted here to stay within the stated behaviour-area scope.
(`f12`'s `all-step-types` fixture separately round-trips all 8 `WorkflowDefinition`-representable
behaviour types through export, including these 4 — see the Fixtures table below.)

### Fixtures

| Fixture | `?fixture=` | Canvas on mount | Notes |
|---|---|---|---|
| Empty | `empty` | 1 node | The Builder's own concept of "empty" (`emptyWorkflow()`, zero steps) still renders one untouched, unconfigured ASK_USER starter step (`createInitialCanvasModel()`), not a literally blank canvas — matches issue #94's "the default single unconfigured step" wording. Its node id is a random nanoid — use `canvasNodes.first()`, never a hardcoded id. |
| Simple valid | `simple-valid` | 1 node | One fully configured `INPUT` step; the minimal "Looks good" workflow. |
| Multi-step | `multi-step` | 5 nodes | The pre-existing `sampleWorkflow()` dev default — same fixture `specs/builder/*` already uses. |
| All step types | `all-step-types` | 8 nodes | One step per `WorkflowDefinition`-representable behaviour type. `SAVE_RESULT`/`REPEAT` are canvas-only constructs with no `behaviourType` of their own and can't be preloaded this way — covered by `f6` adding them live instead. |
| None | `none` | 1 node (same starter as Empty) | `initialWorkflow` entirely omitted — the **only** seed state that engages the built-in localStorage draft-recovery adapter (`allowRestore: !readOnly && !initialWorkflow` — any supplied `initialWorkflow`, fixtures included, suppresses restore). Persistence specs (`f7`) must use this, not `empty`. |

Deliberately not a fixture: "invalid". Built live instead (add an unconfigured Decision step via
the palette — the same technique `visual/interactions.ts`'s `addUnconfiguredDecisionStep` already
uses), since encoding a syntactically-invalid `WorkflowDefinition` risks the loader itself
rejecting or silently repairing it before a test ever gets to assert on it.

### Viewport coverage

`support/viewports.ts`: `mobileNarrow` (360×740), `mobileStandard` (390×844, the width issues
#97/#98/#103 were originally found at), `tablet` (768×1024), `desktop` (1440×900), `largeDesktop`
(1920×1080, spot-checked inline via `test.use()` where relevant rather than a full project). Full
critical-journey coverage runs once, on `desktop`; `tablet`/`mobile` run only `@responsive`- or
`@tablet-only`/`@mobile-only`-tagged targeted scenarios — no journey is duplicated at every
viewport.

### Regression vs. quarantined tests

Every test in this suite is one of exactly two kinds:

- **Mandatory regression test** — a plain `test(...)` call in an `f*.spec.ts` file. Runs in the
  `desktop`/`tablet`/`mobile` projects and gates normal CI (`npm run test:e2e:builder-functional`).
  This is what all 47 tests here are today, including every test that maps to issues #94-#103 (all
  ten were already closed — PRs #105-110, merged 2026-07-17 — before this testkit was built; see
  the Behaviour inventory table above for which spec covers each). #94-#103 are not special-cased
  anywhere in the mechanism — they're simply ten mandatory regression tests among the 47, the same
  as any other.
- **Quarantined test** — written via `knownIssueTest(issueNumber, title, fn)`
  (`support/known-issues.ts`) instead of plain `test(...)`, for a scenario that is *expected to
  fail* until some specific, currently-open issue is fixed. This tags the test's title
  `@known-issue #<n>`, which the `desktop`/`tablet`/`mobile` projects all `grepInvert` — so it is
  entirely absent from normal CI, not merely present-but-skipped. A dedicated fourth project,
  `known-issues`, is the exact complement (`grep: /@known-issue/`, no `grepInvert`) and is what
  `npm run test:e2e:builder-functional:known-issues` runs — a plain, fully-executing `test()`
  underneath (deliberately not `test.fixme()`, whose body never actually runs even under `--grep`,
  which would defeat the point of a *strict* command that can genuinely exercise it).
- **Promotion path**: once a quarantined test's issue is fixed, move it out of `knownIssueTest(...)`
  into a plain `test(...)` call (dropping the `@known-issue` tag) and flip the entry's `status` to
  `'closed'` in `known-issues.ts` for the record. It now runs in every normal project like any
  other test — no other code change needed.

Nothing is quarantined today: `npm run test:e2e:builder-functional:known-issues` currently matches
zero tests and exits non-zero with "No tests found" — expected, not a bug. This mechanism was
verified end-to-end while building it (a temporary quarantined test was added, confirmed absent
from the normal command and genuinely failing under the strict command, then removed) and exists
for whatever the *next* Builder defect turns out to be, not for #94-#103 specifically.

### Local commands

```bash
npm ci
npm run playwright:install                        # one-off: download Chromium
npm run test:e2e:builder-functional                # mandatory regression tests: desktop, tablet, mobile
npx playwright test --config=playwright.builder-functional.config.ts --project=desktop   # a single project
npm run test:e2e:builder-functional:known-issues    # strict: the known-issues project only (see above)
```

The `webServer` runs `npm run build:dev-harness && npm run preview -- --port 4193 --strictPort`
inside `../agentforge4j-workflow-builder` and reuses an already-running one outside CI. That
sibling module must have its dependencies installed (`npm ci` there) first.

### CI behaviour

Wired into `.github/workflows/ui-e2e.yml` as a new `builder-functional` job, gated on the same
`changes.outputs.builder` path filter as the existing `builder` job (same relevant paths: touching
the workflow-builder package or this module triggers both). Runs the mandatory-regression command
(`desktop`/`tablet`/`mobile`, never the `known-issues` project); uploads the Playwright HTML report
(`playwright-report/builder-functional`) as a build artifact on failure. The strict known-issues
command is not part of any CI job — run it manually during remediation work.

### Evidence locations

Same convention as the other suites: `test-results/builder-functional/` (screenshots, traces,
videos — gitignored, `only-on-failure`/`retain-on-failure`/`on-first-retry` respectively) and
`playwright-report/builder-functional/` (HTML report). Console errors and failed network requests
for a failing test are captured in its trace; open it with `npx playwright show-trace
test-results/builder-functional/<test-dir>/trace.zip`.

### Adding a regression test for a future Builder defect

**If it already passes against current `main`**: write a plain `test(...)` directly in the
relevant `f*.spec.ts` file (or a new file, if the behaviour area doesn't fit an existing one). It's
a mandatory regression test immediately, no registration needed.

**If it's expected to fail until a fix lands** (quarantine it):

1. Add the issue to `support/known-issues.ts`'s `KNOWN_ISSUES` with `status: 'open'`.
2. Write the test using `knownIssueTest(issueNumber, title, fn)` instead of plain `test(...)`.
3. Verify it fails against current `main` (proves it actually detects the bug) via
   `npm run test:e2e:builder-functional:known-issues`, and confirm it's absent from
   `npm run test:e2e:builder-functional`.
4. Once the fix merges: move the test out of `knownIssueTest(...)` into a plain `test(...)` call,
   and flip the issue to `status: 'closed'` in `known-issues.ts` for the record — it now runs in
   normal CI like every other test here.

### Troubleshooting

- **`webServer` fails to start / port 4193 in use**: another process (a prior interrupted run, a
  manual `npm run preview`) is holding the port. Find and stop it. Passing a different port on the
  command line is not supported by this config; edit the `PORT` constant in
  `playwright.builder-functional.config.ts` for a one-off local override.
- **`build:dev-harness` fails with a schema sync error**: run `npm run sync-schema` in
  `agentforge4j-workflow-builder` manually first — it copies `agentforge4j-schema`'s JSON Schema
  into the package; a stale/missing copy fails the Vite build outright.
- **A test fails only under `--repeat-each`/parallel runs, never alone**: check whether it shares
  browser-context state with another test in the same file via something outside Playwright's
  per-test isolation (e.g. a module-level mutable constant) — localStorage/cookies/sessionStorage
  are already isolated per test by default and are not usually the cause.
- **A `.react-flow__node` click times out with "element intercepts pointer events"**: almost always
  the inspector's own backdrop (`.wf-inspector__backdrop`) or the delete-confirmation dialog's
  backdrop is still open from a prior interaction — call `closeInspector()` (or resolve the dialog)
  before the next canvas interaction. `addStep()` already does this defensively before hovering the
  palette, since `appendNode` auto-selects (and thus auto-opens the inspector for) the node it just
  added.

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
