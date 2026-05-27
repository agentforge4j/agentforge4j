# @agentforge4j/workflow-builder-react

Reusable React workflow builder library for the AgentForge4j ecosystem. Host applications embed `WorkflowBuilder`, supply capabilities and adapters, and own persistence, auth, and runtime integration.

**Status:** Phase 1 skeleton — not yet published to npm (`package.json` has `"private": true`).

**Planned consumers:**

- `agentforge4j-web-ui` (Phase 4 marketing / docs site)
- `agentforge4j-platform-ui` (platform shell; Phase 2 redesign in place, Phase 3 extraction into this package)

**Design documentation:** [docs/workflow-builder/DESIGN.md](../docs/workflow-builder/DESIGN.md) (to be added as the programme progresses).

## Development

```bash
cd agentforge4j-workflow-builder
npm install
npm run typecheck
npm run build
npm run test
```

This module is independent of the Maven reactor at the repo root.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
