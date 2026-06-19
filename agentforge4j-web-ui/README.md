# agentforge4j-web-ui

A minimal React single-page application that hosts the
[`@agentforge4j/workflow-builder-react`](../agentforge4j-workflow-builder/README.md) component as a
standalone, runnable app.

## What it is

`agentforge4j-web-ui` is a thin host around the workflow-builder package. Today it serves one
purpose: render the `WorkflowBuilder` in a browser so the component can be used and demonstrated
outside of any larger product shell. It is a private application (not published to npm) and is
independent of the Maven reactor.

The app mounts the builder with a deliberately minimal capability set — **import and export only**;
save, run, publish, and AI-assist are off — so it exercises the component without needing a backend,
persistence, or authentication. It also doubles as a reference for how a host wires the builder in.

## Role and audience

This is **not** the marketing / documentation website. Its audience is developers integrating or
demonstrating the workflow builder: it shows a working embed, supplies the builder's design tokens,
and provides a route to the builder plus a 404 fallback. As the programme progresses it is the
intended home for the public builder host; broader site responsibilities are out of scope here today.

## Structure

- **Routing** (`react-router-dom`): `/` renders the builder page; any other path renders a 404 page.
- **Builder page**: mounts `WorkflowBuilder` with `import`/`export` enabled and everything else
  disabled.
- **Styling**: Tailwind CSS 4 with the builder's `--afb-*` design tokens.

## Local development

```bash
cd agentforge4j-web-ui
npm install
npm run dev
```

To develop against the **unpublished** builder source instead of the released npm package, use:

```bash
npm run dev:local
```

which sets `AFB_LOCAL_BUILDER=1` so Vite resolves the builder from
`../agentforge4j-workflow-builder/src`.

## Build

```bash
npm run typecheck
npm run build
```

## License

Apache License 2.0 — see the root [LICENSE](../LICENSE) and the [project README](../README.md).
