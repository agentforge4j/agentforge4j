# Changelog — @agentforge4j/workflow-builder-react

All notable changes to this package are documented in this file. This is the `builder-v*`
release track's changelog — see the [root CHANGELOG](../CHANGELOG.md) for the framework track
and the [catalog CHANGELOG](../agentforge4j-workflows-catalog/CHANGELOG.md) for the shipped
workflow catalog.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this package adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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
- Dragging an existing edge's endpoint to a different step/handle ("rerouting") now actually
  updates the workflow — previously `edgesReconnectable` was enabled but no `onReconnect` handler
  was wired, so the drag had no effect and the edge snapped back to its original endpoints.

### Changed
- Importing/loading a new workflow document now resets undo/redo history instead of leaving it
  in place — undoing after an import returns to the freshly-imported document, not back into a
  different, previously open workflow's shape.

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
