# ADR-0015: MCP servers as first-class governed tool sources

## Status

Accepted

## Date

2026-07-09 (retrospective record date — the integration module was designed and merged earlier in 2026; no single decision date is separately recorded)

## Retrospective note

This ADR was written retrospectively to document an already accepted and implemented architectural direction.

## Context

The Model Context Protocol (MCP) is the emerging standard for exposing tools to AI systems; supporting it makes a large external tool ecosystem available to workflows without per-tool integration work. But MCP servers are external, third-party processes: their tool sets are only knowable at connection time, their metadata may be absent or unreliable, and the protocol's SDK evolves quickly. Two hazards had to be designed against: coupling the framework core to a fast-moving external SDK, and letting externally sourced tools reach a weaker governance path than built-in ones.

## Decision

MCP integration ships as a dedicated `agentforge4j-mcp` module, depending only on `agentforge4j-core` and `agentforge4j-util` among framework modules, plus the external MCP SDK — the SDK dependency is confined to this one module and never touches core or runtime. MCP-provided tools flow through the same governance chokepoint as every other tool (ADR-0003) and the same realised-capability model (ADR-0004); where an MCP server supplies no risk metadata, defaults are conservative.

The layering invariant: the workflow controls flow, the runtime controls permissions and audit, MCP provides tools — nothing more.

## Alternatives considered

- **MCP support inside core or runtime.** Welds a fast-moving external SDK to the framework's most stable modules; every SDK bump becomes a core change.
- **A separate governance path for MCP tools.** Two policy semantics and two audit shapes for one concept; external tools would be governed differently precisely where uniformity matters most.
- **Trusting MCP-declared metadata.** External declarations are exactly the capability-assertion problem ADR-0004 removed; conservative defaults keep unknown tools on the safe side.

## Consequences

### Positive

- The MCP ecosystem is available to workflows with the same policy, approval, and audit semantics as built-in tools — no weaker path in via an external server.
- SDK churn is contained to one optional module; installations not using MCP carry no MCP dependency.
- Conservative metadata defaults make honest servers cheap and vague servers restricted.

### Negative

- The framework's MCP surface can lag the protocol: features arrive when the confined SDK dependency is upgraded, not automatically.
- Conservative defaults mean well-behaved servers without metadata face friction until classified or opted in.

### Neutral / tradeoffs

- The integration is client-side only: workflows consume MCP tools; exposing workflows *as* MCP tools is a separate, future direction.

## Compatibility impact

`agentforge4j-mcp` is an optional module — presence by dependency, per the framework's composition principle. Its governance behaviour adds no new policy surface: existing `ToolPolicy` implementations govern MCP tools unchanged. Workflow definitions reference MCP-provided tools like any other tool.

## Implementation notes

`agentforge4j-mcp` depends only on `agentforge4j-core` and `agentforge4j-util` among framework modules, plus the external MCP SDK (verified in the module POM); tool flow through `DefaultToolExecutionService`; conservative risk defaults in the MCP tool descriptor mapping. Verified on `main @ 9ad289dd` (2026-07-09).

## Follow-up work

- Whether this module ships in the first public release is an open decision tied to an upstream SDK module-naming caveat — recorded in ADR-0009.
- Server-side exposure (workflows as MCP tools) remains a future direction.

## Related documents

- ADR-0003 (governance chokepoint), ADR-0004 (realised capability truth), ADR-0009 (module policy and the upstream naming caveat).
