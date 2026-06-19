# @agentforge4j/workflow-builder-react

A reusable React component for building AgentForge4j workflows visually. Host applications embed
`WorkflowBuilder`, declare which capabilities are enabled, supply adapters for validation and
import/export, and own persistence, auth, and runtime integration themselves.

This package is published to npm and is independent of the Maven reactor at the repo root.

## Installation

```bash
npm install @agentforge4j/workflow-builder-react
```

Peer dependencies: `react` and `react-dom` (`^18` or `^19`). The package ships ESM + CommonJS builds
and TypeScript declarations (built with tsup).

## Usage

```tsx
import { WorkflowBuilder } from '@agentforge4j/workflow-builder-react';
import '@agentforge4j/workflow-builder-react/styles/tokens.css';

export function Editor() {
  return (
    <WorkflowBuilder
      capabilities={{
        import: true,
        export: true,
        save: false,
        run: false,
        publish: false,
        aiAssist: false,
      }}
      adapters={{
        validateWorkflow: (draft) => myValidate(draft),
      }}
      actions={{
        save: async (draft) => mySave(draft),
      }}
    />
  );
}
```

## Public API

The package's entry point exports the component, its prop types, the reducer/state hook, and the
bundle helpers.

| Export | Purpose |
|---|---|
| `WorkflowBuilder` | The builder component (also the default export). |
| `WorkflowBuilderProps` | Root props: `capabilities` (required), optional `adapters`, `actions`, `theme`, `initialWorkflow`, `agentCatalog`. |
| `BuilderCapabilities` | Feature flags: `import`, `export`, `save`, `run`, `publish`, `aiAssist` (all `boolean`). |
| `BuilderAdapters` | Host-supplied `validateWorkflow`, `importBundle`, `exportBundle`. |
| `BuilderActions` | Host-supplied `save`, `run`, `publish`. |
| `BuilderTheme` | Optional root `className` and CSS-variable overrides. |
| `useBuilderState` | Hook returning the builder's reducer state and dispatch (`UseBuilderStateResult`). |
| `validateWorkflow`, `builderReducer` | Default validator and state reducer. |
| `importBundle`, `exportBundle`, `parseWorkflowJson`, `serializeWorkflowJson` | Bundle I/O helpers (`WorkflowParseError` on malformed input). |

Supporting types include `WorkflowDefinition`, `AgentRef`, `ExportFormat`, `ValidationIssue`,
`ValidationResult`, `NodeKind`, `CanvasModel`, and the `NODE_KIND_META` constant.

## Design tokens

The package exposes a token contract as CSS custom properties prefixed with `--afb-*` in
`styles/tokens.css` (also bundled into `dist/index.css`). Host applications may override any variable
on a wrapper element or on `:root`.

| Group | Variables (summary) |
|---|---|
| Brand | `--afb-blue-*`, `--afb-navy-*` |
| Canvas | `--afb-canvas-bg`, `--afb-canvas-dot`, `--afb-canvas-glow` |
| Node surface | `--afb-node-surface`, `--afb-node-border`, `--afb-node-text`, `--afb-node-shadow`, … |
| Chrome | `--afb-chrome-bg`, `--afb-chrome-border`, `--afb-chrome-text`, `--afb-chrome-muted` |
| Semantic state | `--afb-accent`, `--afb-ok`, `--afb-warn`, `--afb-err`, `--afb-human` |
| Per-kind accent | `--afb-kind-input`, `--afb-kind-ai`, `--afb-kind-decision`, … |
| Type | `--afb-font-sans`, `--afb-font-mono` |
| Geometry | `--afb-radius`, `--afb-radius-sm` |

**Fonts:** the library declares `--afb-font-sans` (`Manrope`) and `--afb-font-mono` (`IBM Plex Mono`)
with system fallbacks but **does not load fonts over the network**. Host apps (or the local dev
playground) must supply those faces if they want the named typefaces.

## Local development / playground

```bash
cd agentforge4j-workflow-builder
npm install
npm run dev
```

Opens a Vite playground (`dev/`) that mounts `WorkflowBuilder` with a sample multi-node workflow. The
playground loads Manrope and IBM Plex Mono via Google Fonts; this is dev-only and excluded from the
published package.

## Build & test

```bash
npm run typecheck
npm run build
npm run test
```

## Versioning

This package is versioned independently of the Java modules (currently `0.3.0`) and follows semantic
versioning for its published API.

## License

Apache License 2.0 — see [LICENSE](./LICENSE) and the [project README](../README.md).
