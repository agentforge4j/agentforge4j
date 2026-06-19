# agentforge4j-llm-vllm

The vLLM provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory` for an
OpenAI-compatible [vLLM](https://docs.vllm.ai) server, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against models you serve yourself on a vLLM inference server
(self-hosted or private). It speaks vLLM's OpenAI-compatible chat endpoint over the JDK
`java.net.http` client and needs no API key — you point it at your server's URL.

## Supported models

Shipped tier defaults for provider id `vllm`:

| Tier | Default model |
|---|---|
| `LITE` | `Qwen/Qwen3-4B` |
| `STANDARD` | `Qwen/Qwen3-14B` |
| `POWERFUL` | `Qwen/Qwen3-32B` |

The model id must match what your vLLM server has loaded; override the tier mapping to your served
models as needed.

## How it activates

Put `agentforge4j-llm-vllm` on the path. The module declares `provides LlmClientFactory with
VllmLlmClientFactory` (provider id `"vllm"`, `requiresApiKey()` is `false`).

## Configuration

No API key. Set the server URL. The plain-Java bootstrap facade defaults it to `http://localhost:8000`
(as it does for Ollama); under the Spring starter, set `url` to point at your vLLM server. The
provider itself requires a base URL — the bootstrap default simply supplies one.

**Spring Boot starter** — bind under `agentforge4j.llm.vllm`:

```yaml
agentforge4j:
  llm:
    vllm:
      url: http://localhost:8000
      default-model: Qwen/Qwen3-14B   # optional
      connect-timeout: 10s            # optional; defaults to 10s
      request-timeout: 5m             # optional; defaults to 5m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.vllm()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-vllm</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.vllm;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
