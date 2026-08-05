# ADR-0037: Shared wire-protocol DTOs own unknown-field tolerance, not the host's ObjectMapper

## Status
Accepted

## Date
2026-08-03

## Context

Every OpenAI-style provider adapter carried its own copy of the same request/response shapes:
eight near-identical `InputRole` enums, and parallel DTO families for the chat-completions API
(Azure OpenAI, Mistral, vLLM) and the Responses API (OpenAI, OpenAI-compatible). The duplication
was mechanical, and drift between copies was already visible — Mistral's usage record had no
`prompt_tokens_details` component while Azure's did, so the two providers reported cached input
tokens differently for the same wire payload.

Consolidating them raised one apparent obstacle. Azure OpenAI and Mistral each had a test asserting
that an unrecognized response field fails deserialization, while vLLM had a test asserting the
opposite. That looked like a genuine per-provider behavioural divergence, and the first version of
this consolidation cited it as the reason vLLM had to keep its own DTOs.

The divergence was not real. Strictness was never a property of the providers; it came from how
each test constructed its `ObjectMapper`. The mapper the framework actually uses is
`ConfigurationLoader.defaultObjectMapper()`, which disables
`DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` and reaches every provider client through
`AgentForge4jBootstrap.Builder.build()` → `LlmClientWiring.buildLlmClients`. Spring hosts get
Spring Boot's auto-configured mapper, which is also lenient. Only the unit tests, constructing a
bare `new ObjectMapper()`, ever saw strict behaviour. Three providers' DTO structure was being
decided by an artefact of test setup.

## Decision

Unknown-field tolerance is a property of the shared wire-protocol DTOs themselves. Every
response-side record in `com.agentforge4j.llm.wireprotocol` is annotated
`@JsonIgnoreProperties(ignoreUnknown = true)`, so a provider adding a response field cannot break
parsing on any host, whatever mapper the embedding application supplies.

With that settled, vLLM consolidates onto the shared chat-completions DTOs along with Azure OpenAI
and Mistral. The `agentforge4j-llm-vllm` module no longer has a `dto` package.

The one genuine wire difference between the three is retained explicitly rather than through
separate types: vLLM sends `"stream": false`, while Azure OpenAI and Mistral omit the field.
`ChatCompletionsRequest.stream` is a nullable `Boolean` and the record is annotated
`@JsonInclude(NON_NULL)`, so passing `null` drops the key and passing `Boolean.FALSE` emits it.
That annotation is load-bearing wire contract, not formatting.

## Alternatives considered

- **Keep vLLM's separate DTO family.** Rejected: the justification was a test artefact, and the
  duplication it preserved was the single largest block the consolidation set out to remove.
- **Leave the shared DTOs unannotated and let the host mapper decide.** Rejected: it makes parsing
  success depend on embedder configuration. An application supplying a strict mapper would start
  failing the first time a provider adds a response field — a failure mode outside its control and
  unrelated to anything it did.
- **Add a `stream` field to vLLM's own request DTO only.** Rejected: that is the duplication again,
  for one field that the shared record expresses with a nullable component.

## Consequences

### Positive
- One definition per wire shape; the Mistral/Azure cached-token drift is gone by construction.
- Forward compatibility is guaranteed rather than configuration-dependent: a provider adding a
  response field is a non-event on every host.
- `agentforge4j-llm-vllm` drops seven DTO records and its `opens ... to
  com.fasterxml.jackson.databind` directive.

### Negative
- A typo in a `@JsonProperty` name inside the shared DTOs now yields a silently `null` component
  instead of a deserialization failure. This is not a new risk — the shipped mapper was already
  lenient — but it is now guaranteed rather than incidental. Per-provider parsing tests assert
  extracted values, which is where such a typo is caught.
- The Azure and Mistral tests that asserted strict rejection were inverted to assert tolerance
  under a deliberately strict mapper. Anyone who read those tests as a provider contract will find
  the opposite now stated.

### Neutral / tradeoffs
- vLLM gains an `error` component on its response record that its client does not inspect; a failed
  vLLM call still surfaces as an empty `choices` array, unchanged.
- vLLM responses now deserialize a `role` on the message where its own DTO omitted it. The shared
  `InputRole` covers the `assistant` value vLLM reports.

## Compatibility impact

Public. `com.agentforge4j.llm.wireprotocol` is an exported package of the `agentforge4j.llm`
module. `com.agentforge4j.llm.vllm.dto` is removed — that package was only ever `opens`, never
`exports`, so it was not reachable API for consumers.

One outbound wire change ships with this: the OpenAI-compatible adapter no longer sends
`"max_output_tokens": null` when no output-token budget is requested; the key is omitted. This
aligns it with the OpenAI adapter, which already omitted it, and with servers that reject an
explicit null for a typed parameter.

## Implementation notes

- Shared DTOs and support classes: `agentforge4j-llm`,
  `com.agentforge4j.llm.wireprotocol` — `ChatCompletionsApiSupport`, `ResponsesApiSupport`, and the
  request/response records.
- Real semantics are pinned by
  `com.agentforge4j.bootstrap.WireProtocolUnknownFieldSemanticsTest`, which asserts both what
  `ConfigurationLoader.defaultObjectMapper()` does and that the DTOs no longer depend on it.
- Per-provider tolerance under a deliberately strict mapper: `AzureOpenAiLlmClientTest` and
  `MistralLlmClientTest`, `should_tolerate_unknown_json_fields_even_under_a_strict_object_mapper`.
- vLLM's exact request shape, including `"stream": false`, is pinned by
  `VllmLlmClientTest.should_serialize_exactly_the_pre_consolidation_wire_shape`.

## Related documents
- ADR-0009 (module export surface and JPMS carve-outs)
