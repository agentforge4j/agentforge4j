## 0.2.2-B

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
