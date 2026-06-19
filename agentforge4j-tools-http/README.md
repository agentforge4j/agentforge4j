# agentforge4j-tools-http

The `HTTP_TOOL` integration contributor: turns declared HTTP endpoints into governed core tool-SPI
`ToolProvider`s, with secrets resolved at call time and never inlined or logged.

## Why it exists

Many useful tools are just HTTP APIs. Rather than write a bespoke `ToolProvider` for each one, this
module lets you *declare* endpoints — method, URL template, input/output schema, headers — as
configuration and have them exposed as governed tools. It binds each logical capability to one HTTP
call, validates every definition up front, and resolves credentials through a `SecretResolver` so API
keys live in the environment, not in the definition or the model's arguments.

## How it fits

`agentforge4j-tools-http` depends on [`agentforge4j-core`](../agentforge4j-core/README.md) and
[`agentforge4j-util`](../agentforge4j-util/README.md), uses Jackson and the JDK `java.net.http`
client, and contributes through the `IntegrationToolProviderFactory` `ServiceLoader` seam. Credentials
are resolved through `core`'s `SecretResolver` SPI — the `EnvironmentSecretResolver` default declared
in `agentforge4j-core` — so secrets stay in the environment rather than in definitions.

## Key public types

| Type | Purpose |
|---|---|
| `HttpToolProviderFactory` | Realises `IntegrationType.HTTP_TOOL` integrations; builds an `HttpToolProvider` over the declared endpoints; provider id `"http:" + integrationId`. |
| `HttpToolProvider` | A `ToolProvider` mapping each capability to a single HTTP call; validates all definitions at construction. |
| `HttpEndpointDefinition` | Immutable binding of a capability to an endpoint: method, URL template, schemas, query args, body mode, static/secret headers, timeout, retries, response-size cap. |
| `HttpMethod` / `BodyMode` | The supported verbs (`GET`/`POST`/`PUT`/`PATCH`/`DELETE`) and body modes (`JSON`/`NONE`). |

## Configuration

The integration `config` is a JSON array of endpoint definitions. Headers split into `staticHeaders`
(non-secret values) and `secretHeaders` (header name → **secret-reference key**). Secret values are
resolved at invoke time via the supplied `SecretResolver` — by default `EnvironmentSecretResolver`,
which reads the process environment first and then JVM system properties.

```json
[
  {
    "capability": "github.get_issue",
    "method": "GET",
    "urlTemplate": "https://api.github.com/repos/{owner}/{repo}/issues/{number}",
    "secretHeaders": { "Authorization": "GITHUB_TOKEN" }
  }
]
```

### Security constraints

- **Secrets are never inlined and never logged.** Secret headers carry reference *keys*, never
  values; resolution happens at call time and error messages name the key, not the resolved value.
- URL placeholders are confined to the path and query — host and port are fixed by the template.
- Responses are capped (default 1 MiB per endpoint) and retried only on retryable status codes with
  backoff.

## Maven coordinates

```xml
<dependency>
  <groupId>org.agentforge4j</groupId>
  <artifactId>agentforge4j-tools-http</artifactId>
</dependency>
```

## JPMS module name

```java
requires agentforge4j.tools.http;
```

Exports `com.agentforge4j.tools.http` and declares `provides IntegrationToolProviderFactory with
HttpToolProviderFactory`.

## Licence

Apache 2.0. See the root [LICENSE](../LICENSE) and the [project README](../README.md).
