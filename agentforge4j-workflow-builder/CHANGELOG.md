# Changelog — @agentforge4j/workflow-builder-react

All notable changes to this package are documented in this file. This is the `builder-v*`
release track's changelog — see the [root CHANGELOG](../CHANGELOG.md) for the framework track
and the [catalog CHANGELOG](../agentforge4j-workflows-catalog/CHANGELOG.md) for the shipped
workflow catalog.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this package adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Draft-recovery persistence: the canvas model is now saved automatically (debounced) as it is
  edited and silently restored — with a small dismissible "Restored your previous session"
  notice offering a "Start fresh" action — after a page reload, so in-progress work is no longer
  discarded on refresh. Falls back to a built-in `localStorage`-backed implementation when no
  `persistence` prop is supplied; the package never makes a network call for this either way, so
  the standalone builder never requires a server. Host applications can supply their own
  `persistence: { load, save, clear }` adapter on `WorkflowBuilderProps` to replace or intercept
  local persistence and save drafts to their own backend instead — `clear` is required (not
  optional): the built-in "Start fresh" action always offers itself once a draft has been
  restored, and its contract is to permanently discard the saved draft, so every adapter must
  be able to honor that. Restoring on mount is skipped whenever the host passes
  `initialWorkflow` (even a metadata-only seed) and in read-only mode. A pending debounced save
  is flushed when the builder unmounts (covering SPA navigation), and a best-effort
  `beforeunload` warning covers the remaining page-unload gap. The built-in adapter stores a
  version-stamped envelope and keeps a single global draft slot per origin; on load, a version
  mismatch or a draft failing the full structural gate — which validates every field the editor
  dereferences (per-kind step fields, decision cases, artifact definitions, edges), not just
  container presence — is discarded fail-closed (never restored into code that cannot render
  it): built-in drafts are cleared, host-adapter drafts are skipped without touching the host's
  storage. Adapter writes are serialized in invocation order, so "Start fresh" cannot be
  silently undone by a debounced save that was still in flight when the draft was cleared. This
  mechanism is independent of `capabilities.save`, which continues to gate a separate host
  backend-persistence action. The editing posture (`mode`) is treated as mount-stable for
  persistence purposes: whether drafts restore and save is decided by the mode supplied at
  mount, and changing `mode` at runtime is not a supported transition — remount the builder
  to change posture.
- Narrow-container gate: below a supported container width (`47.9375rem` / 767px, matching
  the narrow-viewport breakpoint already used elsewhere in `workflow-builder.css`), the builder
  no longer renders the editor at all. It takes a synchronous first measurement before paint
  (no editor flash on phones) and then tracks its own rendered root element via
  `ResizeObserver` (never `window.innerWidth`, since the builder may be embedded in a host panel
  of arbitrary size), showing a message directing the user to a larger screen instead. This is
  a full replacement, not an overlay — the canvas, palette, inspector, and toolbar are not
  mounted underneath it, and there is no reduced or read-only mobile editing surface (deferred,
  out of scope for 0.1.0). This is the resolution for the mobile "+ Add step" control, the
  obstructed name field, and the checklist overlay blocking the canvas: those interactions are
  now unreachable below the breakpoint rather than individually patched. The step palette's
  compact bottom-sheet variant now keys off the same container measurement (a prop fed from
  this gate) instead of a viewport media query, so the two narrowness axes can never disagree;
  the remaining narrow-viewport CSS blocks are cosmetic-only.
- Full undo/redo for every meaningful builder change: adding/deleting steps, step config edits,
  connections (create/delete/reroute), reordering a step in the chain, workflow name/id, and
  start-step changes. Toolbar Undo/Redo controls (each disabled when its stack is empty) plus
  Ctrl+Z / Ctrl+Shift+Z (or Ctrl+Y) keyboard shortcuts; shortcuts are skipped while a text field
  has focus so the browser's native per-field undo is unaffected while typing.
  Rapid-fire changes to the same field or the same node's config (typing, quick edits) coalesce
  into one undo step instead of one per keystroke; dragging a step coalesces into a single step
  for the whole drag gesture, sealed on release.
- A confirmation dialog before a step is actually deleted — a lightweight, complementary safety
  net alongside undo/redo for a first-time user who has not yet discovered Ctrl+Z. Shared by
  every deletion trigger: the inspector's "Delete step" button and the canvas Delete/Backspace
  key both resolve the same confirmation gate.
- Dragging an ordinary next-step edge's endpoint to a different step ("rerouting") now actually
  updates the workflow — previously `edgesReconnectable` was enabled but no `onReconnect` handler
  was wired, so the drag had no effect and the edge snapped back to its original endpoints.
  Decision-branch case edges still snap back on purpose: their routing lives in the decision
  step's case configuration (edited in the inspector), not in the drawn edge, so rerouting the
  drawing would silently diverge from what the exported workflow actually does.
- A persistent "Start" marker on whichever node is the workflow's current start step,
  visible on the canvas in both Guided and Advanced mode — previously the entry point could
  only be inferred from graph position, or was labeled explicitly in Advanced mode's
  starter hint alone.
- A direct "Start step" chooser in Guided mode, shown once a workflow has more than one
  node and at least one other node is eligible to become the start step: a dropdown
  listing every eligible node, reassigning the start step on selection.
  It reuses the same reposition-to-Start mechanism as the inspector's existing "Runs after:
  Start" option rather than a parallel one; the eligibility rules (top-level, not
  DECISION/RETRY, not branch-owned, at most one linear predecessor/successor) are shared
  with that selector too. The chooser also offers a node with no other valid position (a
  second, detached root) even though the inspector's own selector stays disabled for it —
  moving such a node to Start still detaches it from whatever it was chained to, the same
  way every other reposition target already does.
- A visible, persisted on-page confirmation after a successful Export, showing the produced
  filename (e.g. "Exported my-workflow.workflow.zip") and a dismiss control — replacing the
  previous silent revert to the button's resting state with no other feedback. The filename
  comes from the adapter itself: `BuilderAdapters.exportBundle` may now resolve an
  `ExportOutcome` (`{ filename?: string }`) — the built-in adapter does, as does the package's
  public `exportBundle` helper (which now delegates to the same implementation) — while a host
  adapter resolving `void` (every existing implementation remains valid) gets a generic
  confirmation instead of a fabricated filename. The confirmation is cleared automatically when a different
  workflow is imported, so it never describes a stale document.

### Changed
- Importing/loading a new workflow document now resets undo/redo history instead of leaving it
  in place — undoing after an import returns to the freshly-imported document, not back into a
  different, previously open workflow's shape.

### Fixed
- The step-library panel no longer silently clips step types with no scroll affordance:
  `.wf-palette__panel` (between `.wf-palette`, which has the real definite height, and
  `.wf-palette__body`, which declares `flex: 1; overflow: auto;`) had no `display: flex`
  of its own, so it sized to its content instead of filling `.wf-palette` and the flex
  sizing on `.wf-palette__body` never took effect. `.wf-palette__panel` is now a column
  flex container that fills its parent, and `.wf-palette__body` gained `min-height: 0` so
  it can shrink below its content size and actually scroll.
- The validation "N things to fix" popover no longer renders behind an already-open step
  inspector panel. The pill's own stacking context (established by the toolbar's non-`auto`
  `z-index`) could never be escaped by raising the popover's `z-index` alone, since a raised value
  stays scoped inside that ancestor context; the popover is now rendered via a `document.body`
  portal, positioned from the pill's own screen coordinates, so it always paints above open panels
  regardless of which ancestor stacking context the pill itself lives in.
- Guided mode's "Add approval" checklist item now genuinely reveals the field it checks for.
  `StepConfigPanel`'s Approval control (`TransitionField`) already existed and worked, but was
  hidden inside the "Behavior" section, which defaults to collapsed in guided mode; the checklist
  item's action now selects the relevant AI step, force-opens that section, and focuses the field
  instead of silently choosing "Requires human approval" on the user's behalf.
- The validation popover (which is portaled to `document.body`, outside the themed builder
  root) now carries the host's `theme.variables`/`theme.className`, so themed hosts no longer
  get a default-palette popover.
- The "Add approval" checklist action no longer silently does nothing on a repeat click after the
  user has manually collapsed the revealed Behavior section: the section is now force-opened
  imperatively, not just via a React prop the browser's own `<summary>` toggle can already have
  invalidated.
- Dismissing the validation popover by clicking elsewhere, or via its "Fix" action, no longer
  steals focus back to the pill toggle button — restoring focus to the toggle now only happens on
  dismissals (Escape, the popover's own close button) that don't already establish a different
  focus target.
- Pressing Escape while the validation popover is open now dismisses only the popover, even when a
  step inspector panel is open underneath it, instead of also closing the inspector.
- Activating "Fix" on a validation issue now moves focus into the step inspector it opens, instead
  of leaving it on the document body once the popover (which held it) unmounts.
- Pressing Escape after focus has already moved away from the open validation popover (e.g. via
  Tab to another control) no longer steals it back to the pill toggle button on close.

## [0.5.0] - 2026-07-12

(0.4.0's changes are folded in below — that version was merged to `main` but never published to
npm; 0.5.0 supersedes it in source too.)

### Added
- schemaVersion is now written on every exported workflow document (previously undeclared).
- Import now rejects a workflow document with a missing, non-integer, or unsupported
  schemaVersion before it is converted into the builder's internal draft shape; supported
  versions are declared in `package.json`'s `agentforge4j.supportedSchemaVersions` field.
- Inspector "Runs after" selector (including a Start option) that repositions a step in linear
  order with automatic chain re-stitching.
- Edge-insert "+" affordance that splits an ordinary linear edge and inserts the next step there.
- Scope-aware reachability validation: unreachable steps surface as warnings in the validation
  panel; joins and multi-predecessor graphs are valid and never warned.
- Read-only mode (`mode: 'editable' | 'readOnly'`, default `'editable'`), enforced at the
  canvas-state chokepoint with React Flow flags, handler gates, and callback/affordance
  suppression as defense-in-depth. Pan/zoom/select/inspect/validate/export remain available;
  Run is hidden regardless of the run capability, since it is side-effecting.
- Read-only badge, `aria-readonly`, and read-only inspector presentation.
- Playwright e2e coverage for read-only mode and the step-connection UX.

### Changed
- The default Export button and the built-in import file picker now use the zip format only;
  the unvalidated plain-JSON round trip (which carried no schemaVersion and was never
  schema-validated) is no longer the default. The general-purpose `exportBundle`/`importBundle`/
  `parseWorkflowJson`/`serializeWorkflowJson` library exports are unchanged for host apps that
  call them directly.

### Fixed
- Exporting a workflow with no name no longer downloads a dot-prefixed `.workflow.zip`; falls
  back to `workflow` when the id is blank.
- Deleting the entry step now reassigns `startNodeId` to a remaining node instead of leaving
  reachability validation meaningless.
- Loop-body reachability now seeds only the designated body entry node, rather than treating
  every body member as directly reachable from the loop node — a disconnected body member is
  now correctly flagged.
- RETRY routing (target and fallback) is now included in unreachable-node detection.
- Deleting a node now scrubs stale references to it from decision cases/defaults, retry
  targets/fallbacks, and loop-body membership, and promotes orphaned loop-body children; schema
  validation on delete is deferred so an incomplete routing target surfaces as a validation
  error instead of crashing the editor.
- Edge insert is now scope-safe: only offered on top-level, non-branch-owned linear edges, so a
  spliced node can no longer be misplaced into a loop body or branch chain.
- The toolbar no longer clips its buttons on a narrow viewport: `.workflow-builder__toolbar` set
  both `flex-wrap: wrap` and a fixed `height`, which are contradictory — wrapped content had no
  room to occupy a second row and was hidden by the embed's own `overflow: hidden`. The toolbar
  now uses `min-height` only, so it grows to fit wrapped rows instead of clipping them.
- The guided-mode step panel no longer visually overlaps the canvas on a short viewport:
  `.workflow-builder__guided` was unbounded in height (positioned with `top`/`left`/`right` but
  no `bottom`), so its content could grow past the available space and sit on top of the canvas
  underneath it. It is now bounded on all four sides, and its inner panel scrolls internally if
  its content is still taller than the available space.
- The canvas's "Select start step" starter-hint card no longer competes with the guided-mode
  step panel for the same space on a short viewport — the two overlays serve the same purpose
  and, together, don't fit regardless of alignment. `WorkflowCanvas` gained a `hideStarterHint`
  prop, which `WorkflowBuilder` now sets whenever guided mode's own stepper is already showing
  the same call to action; the starter card's own behavior outside guided mode is unchanged.

## [0.3.0] - 2026-06-02

### Added
- Two-layer validation: JSON Schema (ajv) + cross-reference (agentRef, artifactId, branch
  targets, retry ordering, loop config, duplicate stepIds).
- ZIP bundle export matching `ClasspathWorkflowLoader` layout.
- ZIP bundle import with security hardening (5 MB cap, 200-step cap, entry name sanitization,
  prototype-pollution stripping, schema validation on import).
- JSON import hardened with prototype-pollution stripping.
- `WorkflowParseError` exported from the public API.
- `validateWorkflow` signature extended with an optional `agentCatalog` parameter
  (backward-compatible).
- A "Delete step" button in the step inspector (steps could previously only be removed via the
  on-canvas toolbar or the Delete key).
- Newcomer-friendly first encounter: restyled seeded-step empty state with "Start here" and
  host-delegated "Use a template" shortcut; responsive palette sheet and full-width inspector on
  small screens.
- Human-in-the-loop edge markers: amber dashed gate pill for "Human approval" and slate dotted eye
  marker for "Human review".
- Full `--afb-*` design-token contract in `styles/tokens.css` (brand, canvas, node-surface, chrome,
  semantic-state, per-kind, type, and geometry tokens); host apps may override any variable.
- Dark navy canvas surface: dotted grid, soft blue top-glow, and dark-chrome React Flow
  Controls / MiniMap skinning.
- `dev/` Vite playground (`npm run dev`) mounting a sample multi-node workflow; excluded from the
  published package.
- Per-kind accent tokens + lucide-react icons on every node kind (`NODE_KIND_META` extended with
  `accentVar` and `Icon`).

### Changed
- Removed the redundant on-canvas delete button; steps are deleted from the step inspector.
- Restyled the guided stepper and empty-state surfaces onto the `--afb-*` design tokens (completing
  the token migration across all builder components).
- Default edges restyled to a blue gradient bezier with a refined arrowhead (now on `--afb-*` tokens).
- Edge labels reworded to "Human approval" / "Human review".
- Canvas background/grid now read the new `--afb-canvas-*` tokens.
- Rebuilt node cards (StepNode / DecisionNode / LoopNode via shared NodeChrome) to the redesign
  card language: left accent rail, filled accent icon square, `Ready` / `Needs attention` state
  chip, refreshed handles — now driven by `--afb-node-*` / `--afb-kind-*` tokens.

### Fixed
- Canvas nodes now render. React Flow node measurements were being discarded each render, leaving
  every node permanently hidden (canvas and minimap appeared empty); node state now preserves
  measured dimensions.
- Added steps now reliably appear on the canvas and minimap (a node-change handler could drop a
  newly added step).
- Builder buttons and form controls now render correctly without a host theme (previously relied on
  host CSS variables and could appear unstyled).
- The Behaviour section is hidden for steps that have no behaviour settings (e.g. Ask a Question).
- Form fields and decision cases can now be removed, not just added.
- Minimap nodes are clearly visible on the dark canvas.
- Newly added or selected steps are no longer hidden behind the inspector panel — the canvas now
  pans the selected node into the visible area beside it.
- Removed the raw transition-enum tag that leaked into node footers (introduced in the node redesign).

## [0.2.2]

### Changed
- Export `NodeKind`, `NODE_KIND_META`, and `CanvasModel` from the public API surface.
  Required by embedding applications that render node-kind metadata in `ValidationPanel`
  and mode-switching hooks.

## [0.2.1]

### Fixed
- dts emit now works; `.d.ts` files are included in the published package.
- `./styles/tokens.css` is now listed in the `exports` map and importable by consumers.

## [0.2.0]

### Added
- Introduce the visual workflow builder: React Flow canvas, step palette, inspector, guided
  stepper, validation UI, and client-side workflow model mapping.
- Expand `WorkflowDefinition` to the editable workflow shape used by the builder (steps,
  artifacts, blueprint bodies, schedule).
- Publish as `@agentforge4j/workflow-builder-react` (`private: false`, version `0.2.0`).
