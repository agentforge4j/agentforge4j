# agentforge4j-llm-ollama

The Ollama provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory` for a local or
remote Ollama server, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against models served locally by [Ollama](https://ollama.com).
It needs no API key — point it at your Ollama host and run fully offline. It uses the JDK
`java.net.http` client.

## Supported models

Shipped tier defaults for provider id `ollama`:

| Tier | Default model |
|---|---|
| `LITE` | `qwen3:4b` |
| `STANDARD` | `qwen3:14b` |
| `POWERFUL` | `qwen3:32b` |

Pull the corresponding models in Ollama (or override the tier mapping to models you have pulled).

## How it activates

Put `agentforge4j-llm-ollama` on the path. The module declares `provides LlmClientFactory with
OllamaLlmClientFactory` (provider id `"ollama"`, `requiresApiKey()` is `false`).

## Configuration

No API key. Set the server URL. The plain-Java bootstrap facade defaults it to `http://localhost:11434`
(vLLM similarly gets a bootstrap default, `http://localhost:8000`); under the Spring starter, set `url`
explicitly to point at your Ollama server. The provider itself requires a base URL — the bootstrap
default simply supplies one. Under the Spring starter, Ollama is gated by an `enabled` toggle.

**Spring Boot starter** — bind under `agentforge4j.llm.ollama`:

```yaml
agentforge4j:
  llm:
    ollama:
      enabled: true
      default-model: qwen3:14b      # optional
      url: http://localhost:11434   # required
      connect-timeout: 10s          # optional; defaults to 10s
      request-timeout: 5m           # optional; defaults to 5m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.ollama()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-ollama</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.ollama;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
