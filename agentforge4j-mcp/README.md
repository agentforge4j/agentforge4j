# agentforge4j-mcp

Model Context Protocol (MCP) integration for AgentForge4j: exposes the tools of a remote or local MCP
server to the runtime as core tool-SPI `ToolProvider`s.

## Why it exists

MCP is a standard way for AI systems to discover and call external tools. AgentForge4j already has a
governed tool SPI in `core`; this module bridges the two, so any MCP server — launched as a
subprocess over stdio or reached over Streamable HTTP — becomes a `ToolProvider` the runtime can
list, gate by policy, and invoke. It plugs in through the `IntegrationToolProviderFactory`
`ServiceLoader` seam, so no core or runtime code needs to know MCP exists.

## How it fits

`agentforge4j-mcp` depends on [`agentforge4j-core`](../agentforge4j-core/README.md) only (plus
`agentforge4j-util`) and the official MCP SDK. It is discovered at runtime via `ServiceLoader` and
contributes tool providers; nothing depends back on it.

## Key public types

| Type | Purpose |
|---|---|
| `McpToolProvider` | A `ToolProvider` backed by one `McpServerConnection`; maps the server's tools to `ToolDescriptor`s and delegates invocation. |
| `McpServerConnection` | Lifecycle wrapper around a single MCP transport (connect, reconnect, health, dispose). |
| `McpStdioToolProviderFactory` | Realises `IntegrationType.MCP_STDIO` integrations — launches a server subprocess over stdio. |
| `McpStreamableHttpToolProviderFactory` | Realises `IntegrationType.MCP_STREAMABLE_HTTP` integrations — connects to a server over Streamable HTTP. |

Both factories derive their provider id as `"mcp:" + integrationId` and open the connection lazily on
first use.

## Configuration

Servers are declared as integration definitions whose `config` JSON is parsed per transport:

- **`MCP_STDIO`** — required `command`, optional `args` (array), `env` (object), and `requestTimeout`
  (ISO-8601 duration; defaults to 30 seconds).
- **`MCP_STREAMABLE_HTTP`** — required `url`, optional `requestTimeout`, `staticHeaders` (object of
  literal header name to value, attached to every request) and `secretHeaders` (object of header name
  to secret-reference key, resolved through the embedder-supplied secret resolver at connect time). A
  header name must not appear in both maps, and header names are compared case-insensitively.

When using the Spring Boot starter, the same servers can be declared under `agentforge4j.mcp.servers`
(see the [starter README](../agentforge4j-spring-boot-starter/README.md)).

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-mcp</artifactId>
</dependency>
```

## JPMS

This module has **no** `module-info.java`. The MCP SDK publishes an `Automatic-Module-Name` that is
not a legal Java module name, so no JPMS module can `requires` it; `agentforge4j-mcp` therefore ships
as a classpath (automatic) module — a deliberate, documented carve-out.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
