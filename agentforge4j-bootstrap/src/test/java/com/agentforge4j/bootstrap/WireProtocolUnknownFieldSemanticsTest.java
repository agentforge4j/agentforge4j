// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.wireprotocol.ChatCompletionsResponse;
import com.agentforge4j.llm.wireprotocol.ResponsesResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the <em>real</em> unknown-field semantics of the shared {@code wireprotocol} DTOs, through
 * the {@link ObjectMapper} the bootstrap layer actually hands to provider clients.
 *
 * <p>This exists because the shared chat-completions DTOs were once justified on the premise that
 * Azure OpenAI and Mistral reject unrecognized response fields while vLLM tolerates them. That
 * premise held only for unit tests constructing a bare {@code new ObjectMapper()}: the mapper the
 * framework really uses is {@link ConfigurationLoader#defaultObjectMapper()}, which disables
 * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}, and it reaches every provider client
 * via {@code AgentForge4jBootstrap.Builder.build()} → {@code LlmClientWiring.buildLlmClients}.
 *
 * <p>Tolerance is now a property of the DTOs themselves, so it holds on any host mapper. Both
 * halves are asserted here: what the default mapper does, and that the DTOs no longer depend on it.
 */
class WireProtocolUnknownFieldSemanticsTest {

  private static final String CHAT_COMPLETIONS_WITH_UNKNOWN_FIELDS = """
      {
        "id": "chatcmpl-test",
        "object": "chat.completion",
        "created": 1700000000,
        "system_fingerprint": "fp_test",
        "service_tier": "default",
        "choices": [
          {
            "index": 0,
            "finish_reason": "stop",
            "logprobs": null,
            "message": { "role": "assistant", "content": "ok", "refusal": null }
          }
        ],
        "usage": {
          "prompt_tokens": 11,
          "completion_tokens": 3,
          "total_tokens": 14,
          "prompt_tokens_details": { "cached_tokens": 7, "audio_tokens": 0 },
          "completion_tokens_details": { "reasoning_tokens": 0 }
        },
        "model": "some-model"
      }
      """;

  private static final String RESPONSES_WITH_UNKNOWN_FIELDS = """
      {
        "id": "resp-test",
        "object": "response",
        "status": "completed",
        "incomplete_details": null,
        "output": [
          {
            "id": "msg-1",
            "type": "message",
            "status": "completed",
            "role": "assistant",
            "content": [ { "type": "output_text", "text": "ok", "annotations": [] } ]
          }
        ],
        "usage": {
          "input_tokens": 11,
          "output_tokens": 3,
          "total_tokens": 14,
          "input_tokens_details": { "cached_tokens": 7 },
          "output_tokens_details": { "reasoning_tokens": 0 }
        },
        "model": "some-model"
      }
      """;

  @Test
  void bootstrap_default_mapper_is_lenient_about_unknown_properties() {
    ObjectMapper defaultMapper = ConfigurationLoader.defaultObjectMapper();

    assertThat(defaultMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        .as("the mapper handed to every provider client tolerates unknown properties")
        .isFalse();
  }

  @Test
  void chat_completions_response_parses_under_the_bootstrap_default_mapper() throws Exception {
    ObjectMapper defaultMapper = ConfigurationLoader.defaultObjectMapper();

    ChatCompletionsResponse response =
        defaultMapper.readValue(CHAT_COMPLETIONS_WITH_UNKNOWN_FIELDS, ChatCompletionsResponse.class);

    assertThat(response.choices()).hasSize(1);
    assertThat(response.choices().get(0).message().content()).isEqualTo("ok");
    assertThat(response.usage().promptTokens()).isEqualTo(11);
    assertThat(response.usage().promptTokensDetails().cachedTokens()).isEqualTo(7);
    assertThat(response.model()).isEqualTo("some-model");
  }

  @Test
  void chat_completions_response_parses_identically_under_a_strict_mapper() throws Exception {
    ObjectMapper strictMapper = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    ChatCompletionsResponse strict =
        strictMapper.readValue(CHAT_COMPLETIONS_WITH_UNKNOWN_FIELDS, ChatCompletionsResponse.class);
    ChatCompletionsResponse lenient = ConfigurationLoader.defaultObjectMapper()
        .readValue(CHAT_COMPLETIONS_WITH_UNKNOWN_FIELDS, ChatCompletionsResponse.class);

    assertThat(strict)
        .as("unknown-field tolerance is owned by the DTO, not by the host's mapper configuration")
        .isEqualTo(lenient);
  }

  @Test
  void responses_api_response_parses_under_the_bootstrap_default_mapper() throws Exception {
    ObjectMapper defaultMapper = ConfigurationLoader.defaultObjectMapper();

    ResponsesResponse response =
        defaultMapper.readValue(RESPONSES_WITH_UNKNOWN_FIELDS, ResponsesResponse.class);

    assertThat(response.output()).hasSize(1);
    assertThat(response.output().get(0).content().get(0).text()).isEqualTo("ok");
    assertThat(response.usage().inputTokens()).isEqualTo(11);
    assertThat(response.usage().inputTokensDetails().cachedTokens()).isEqualTo(7);
  }

  @Test
  void responses_api_response_parses_identically_under_a_strict_mapper() throws Exception {
    ObjectMapper strictMapper = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    ResponsesResponse strict =
        strictMapper.readValue(RESPONSES_WITH_UNKNOWN_FIELDS, ResponsesResponse.class);
    ResponsesResponse lenient = ConfigurationLoader.defaultObjectMapper()
        .readValue(RESPONSES_WITH_UNKNOWN_FIELDS, ResponsesResponse.class);

    assertThat(strict)
        .as("unknown-field tolerance is owned by the DTO, not by the host's mapper configuration")
        .isEqualTo(lenient);
  }
}
