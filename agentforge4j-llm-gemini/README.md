# agentforge4j-llm-gemini

The Google Gemini provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory` for
Gemini's REST API, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against Google's Gemini models. It maps Gemini's REST contract
to the shared `agentforge4j.llm` model and uses the JDK `java.net.http` client — no vendor SDK.

## Supported models

Shipped tier defaults for provider id `gemini`:

| Tier | Default model |
|---|---|
| `LITE` | `gemini-3.1-flash-lite` |
| `STANDARD` | `gemini-3.5-flash` |
| `POWERFUL` | `gemini-3.1-pro` |

## How it activates

Put `agentforge4j-llm-gemini` on the path. The module declares `provides LlmClientFactory with
GeminiLlmClientFactory` (provider id `"gemini"`, API key required).

## Configuration

Requires an API key. The base URL defaults to the Gemini multi-tenant host and may be overridden.

**Spring Boot starter** — bind under `agentforge4j.llm.gemini`:

```yaml
agentforge4j:
  llm:
    gemini:
      api-key: ${GEMINI_API_KEY}
      default-model: gemini-3.5-flash   # optional
      base-url:                         # optional; overrides the default host
      connect-timeout: 10s              # optional; defaults to 10s
      request-timeout: 2m               # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.gemini()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-gemini</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.gemini;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
