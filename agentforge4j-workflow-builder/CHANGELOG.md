## [0.3.0] - 2026-06-02

### Added
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

- Two-layer validation: JSON Schema (ajv) + cross-reference (agentRef, artifactId, branch targets, retry ordering, loop config, duplicate stepIds).
- ZIP bundle export matching ClasspathWorkflowLoader layout.
- ZIP bundle import with security hardening (5 MB cap, 200-step cap, entry name sanitization, prototype-pollution stripping, schema validation on import).
- JSON import hardened with prototype-pollution stripping.
- WorkflowParseError exported from public API.
- validateWorkflow signature extended with optional agentCatalog parameter (backward-compatible).

## 0.2.2

- Export `NodeKind`, `NODE_KIND_META`, and `CanvasModel` from the public API surface.
  Required by embedding applications that render node-kind metadata in `ValidationPanel`
  and mode-switching hooks.

## 0.2.1

- Fix: dts emit now works; `.d.ts` files are included in the published package.
- Fix: `./styles/tokens.css` is now listed in the `exports` map and importable by consumers.

## 0.2.0

- Introduce the visual workflow builder: React Flow canvas, step palette, inspector, guided stepper, validation UI, and client-side workflow model mapping.
- Expand `WorkflowDefinition` to the editable workflow shape used by the builder (steps, artifacts, blueprint bodies, schedule).
- Publish as `@agentforge4j/workflow-builder-react` (`private: false`, version `0.2.0`).
