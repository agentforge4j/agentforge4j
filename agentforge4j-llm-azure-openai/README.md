# agentforge4j-llm-azure-openai

The Azure OpenAI Service provider for AgentForge4j — an HTTP adapter implementing `LlmClientFactory`
for models served from your Azure OpenAI resource, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider when your OpenAI models are hosted on Azure rather than on OpenAI's public API.
Unlike the plain OpenAI provider, Azure addresses models by a **deployment** on a **regional resource
endpoint** and requires an Azure **API version** — so this adapter takes those explicitly. It uses
the JDK `java.net.http` client.

## Supported models

Shipped tier defaults for provider id `azure-openai`:

| Tier | Default model |
|---|---|
| `LITE` | `gpt-5.4-nano` |
| `STANDARD` | `gpt-5.4-mini` |
| `POWERFUL` | `gpt-5.5` |

The deployment you create on Azure maps to the model selection; tier defaults name the underlying
model.

## How it activates

Put `agentforge4j-llm-azure-openai` on the path. The module declares `provides LlmClientFactory with
AzureOpenAiLlmClientFactory` (provider id `"azure-openai"`, API key required).

## Configuration

Requires the Azure API key, the deployment name, the resource endpoint, and the API version.

**Spring Boot starter** — bind under `agentforge4j.llm.azure-openai`:

```yaml
agentforge4j:
  llm:
    azure-openai:
      api-key: ${AZURE_OPENAI_API_KEY}
      deployment-name: my-gpt-deployment
      endpoint: https://my-resource.openai.azure.com
      api-version: 2024-10-21
      connect-timeout: 10s   # optional; defaults to 10s
      request-timeout: 2m    # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.azureOpenAi()` (see
the [bootstrap README](../agentforge4j-bootstrap/README.md)). Because the provider id contains a
hyphen, prefer the starter properties or the programmatic builder over environment-variable
configuration. Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-azure-openai</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.llm.azureopenai;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
