# agentforge4j-llm-claude

The Anthropic Claude provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory` for
Claude's Messages API, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against Anthropic's Claude models. It maps the Messages API to
the shared `agentforge4j.llm` model and uses the JDK `java.net.http` client — no vendor SDK.

## Supported models

Shipped tier defaults for provider id `claude`:

| Tier | Default model |
|---|---|
| `LITE` | `claude-haiku-4-5-20251001` |
| `STANDARD` | `claude-sonnet-4-6` |
| `POWERFUL` | `claude-opus-4-8` |

## How it activates

Put `agentforge4j-llm-claude` on the path. The module declares `provides LlmClientFactory with
ClaudeLlmClientFactory` (provider id `"claude"`, API key required) and exports no packages.

## Configuration

Requires an API key. Claude also takes an API-version string and a max-token size for the Messages
API.

**Spring Boot starter** — bind under `agentforge4j.llm.claude`:

```yaml
agentforge4j:
  llm:
    claude:
      api-key: ${ANTHROPIC_API_KEY}
      default-model: claude-sonnet-4-6   # optional
      api-version:                       # Anthropic API version
      url:                               # optional HTTPS endpoint override
      max-token-size:                    # max tokens per request
      connect-timeout: 10s               # optional; defaults to 10s
      request-timeout: 2m                # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.claude()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-claude</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.claude;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
