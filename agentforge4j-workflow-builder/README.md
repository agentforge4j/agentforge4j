# @agentforge4j/workflow-builder-react

Reusable React workflow builder library for the AgentForge4j ecosystem. Host applications embed `WorkflowBuilder`, supply capabilities and adapters, and own persistence, auth, and runtime integration.

**Planned consumers:**

- `agentforge4j-web-ui` (Phase 4 marketing / docs site)
- `agentforge4j-platform-ui` (platform shell; Phase 2 redesign in place, Phase 3 extraction into this package)

**Design documentation:** [docs/workflow-builder/DESIGN.md](../docs/workflow-builder/DESIGN.md) (to be added as the programme progresses).

## Design tokens

The package exposes a redesign token contract as CSS custom properties prefixed with `--afb-*` in `styles/tokens.css` (bundled into `dist/index.css`). Host applications may override any variable on a wrapper element or on `:root`.

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

The legacy `--builder-*` tokens remain unchanged for existing components; later redesign phases migrate surfaces onto `--afb-*`.

**Fonts:** the library declares `--afb-font-sans` (`Manrope`) and `--afb-font-mono` (`IBM Plex Mono`) with system fallbacks but **does not load fonts over the network**. Host apps (or the local dev playground) must supply those faces if they want the named typefaces.

## Local development / playground

```bash
cd agentforge4j-workflow-builder
npm install
npm run dev
```

Opens a Vite playground (`dev/`) that mounts `WorkflowBuilder` with a sample multi-node workflow. The playground loads Manrope and IBM Plex Mono via Google Fonts; this is dev-only and excluded from the published package.

## Build & test

```bash
npm run typecheck
npm run build
npm run test
```

This module is independent of the Maven reactor at the repo root.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
