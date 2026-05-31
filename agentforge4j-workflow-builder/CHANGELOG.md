## [Unreleased]

### Added
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
- Default edges restyled to a blue gradient bezier with a refined arrowhead (now on `--afb-*` tokens).
- Edge labels reworded to "Human approval" / "Human review".
- Canvas background/grid now read the new `--afb-canvas-*` tokens.
- Rebuilt node cards (StepNode / DecisionNode / LoopNode via shared NodeChrome) to the redesign
  card language: left accent rail, filled accent icon square, `Ready` / `Needs attention` state
  chip, refreshed handles — now driven by `--afb-node-*` / `--afb-kind-*` tokens.

### Fixed
- Removed the raw transition-enum tag that leaked into node footers (introduced in the node redesign).

- Two-layer validation: JSON Schema (ajv) + cross-reference (agentRef, artifactId, branch targets, retry ordering, loop config, duplicate stepIds).
- ZIP bundle export matching ClasspathWorkflowLoader layout.
- ZIP bundle import with security hardening (5 MB cap, 200-step cap, entry name sanitization, prototype-pollution stripping, schema validation on import).
- JSON import hardened with prototype-pollution stripping.
- WorkflowParseError exported from public API.
- validateWorkflow signature extended with optional agentCatalog parameter (backward-compatible).

## 0.2.2

- Export `NodeKind`, `NODE_KIND_META`, and `CanvasModel` from the public API surface.
  Required by platform consumers that render node-kind metadata in `ValidationPanel`
  and mode-switching hooks.

## 0.2.1

- Fix: dts emit now works; `.d.ts` files are included in the published package.
- Fix: `./styles/tokens.css` is now listed in the `exports` map and importable by consumers.

## 0.2.0

- Port the Phase 2 visual workflow builder from `agentforge4j-platform` into this package: React Flow canvas, step palette, inspector, guided stepper, validation UI, and client-side workflow model mapping.
- Expand `WorkflowDefinition` to the editable workflow shape used by the builder (steps, artifacts, blueprint bodies, schedule).
- Publish as `@agentforge4j/workflow-builder-react` (`private: false`, version `0.2.0`).
