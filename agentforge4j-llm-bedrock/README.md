# agentforge4j-llm-bedrock

The AWS Bedrock provider for AgentForge4j — an adapter implementing `LlmClientFactory` for Anthropic
models served through Amazon Bedrock Runtime, discovered automatically via `ServiceLoader`.

## When to use it

Choose this provider to run workflows against Anthropic models hosted on AWS Bedrock, with
AWS-native authentication and region routing. Unlike the other providers, it authenticates with **AWS
SigV4 credentials** (not a bearer API key) and calls through the **AWS SDK for Java v2** Bedrock
Runtime client rather than the JDK HTTP client.

## Supported models

Shipped tier defaults for provider id `bedrock`:

| Tier | Default model |
|---|---|
| `LITE` | `anthropic.claude-haiku-4-5-20251001-v1:0` |
| `STANDARD` | `anthropic.claude-sonnet-4-6` |
| `POWERFUL` | `anthropic.claude-opus-4-8` |

## How it activates

Put `agentforge4j-llm-bedrock` on the path. The module declares `provides LlmClientFactory with
BedrockLlmClientFactory` (provider id `"bedrock"`). It overrides `requiresApiKey()` to `false` —
credentials come from AWS, not an API key.

## Configuration

No API key. AWS credentials are resolved through the standard AWS default credentials chain
(environment, profiles, container/instance roles). You supply the AWS region and the Anthropic
payload version; max-tokens and temperature are optional. Under the Spring starter, Bedrock is gated
by an `enabled` toggle.

**Spring Boot starter** — bind under `agentforge4j.llm.bedrock`:

```yaml
agentforge4j:
  llm:
    bedrock:
      enabled: true
      region: us-east-1
      anthropic-version: bedrock-2023-05-31
      model-id:               # optional; otherwise tier defaults apply
      max-tokens:             # optional
      temperature:            # optional
      connect-timeout: 10s    # optional; defaults to 10s
      request-timeout: 2m     # optional; defaults to 2m
```

**Plain Java** — configure through the bootstrap builder with `LlmProviderConfig.bedrock()` (see the
[bootstrap README](../agentforge4j-bootstrap/README.md)). Programmatic configuration always wins.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-llm-bedrock</artifactId>
</dependency>
```

This is the only provider that pulls the AWS SDK (Bedrock Runtime + URL-connection HTTP client).

## JPMS module name

```java
requires agentforge4j.llm.bedrock;
```

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
