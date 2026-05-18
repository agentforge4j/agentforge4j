package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockLlmClientTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  class ConstructorTests {

    @Test
    void rejectsNullObjectMapper() {
      assertThatThrownBy(() -> new BedrockLlmClient(null, FixedBedrockConfiguration.defaults(),
          mock(BedrockRuntimeClient.class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ObjectMapper");
    }

    @Test
    void rejectsNullConfiguration() {
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, null, mock(BedrockRuntimeClient.class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("configuration");
    }

    @Test
    void rejectsNullBedrockClient() {
      assertThatThrownBy(
          () -> new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BedrockRuntimeClient");
    }

    @Test
    void rejectsBlankAnthropicVersion() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().anthropicVersion("  ").build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("anthropicVersion");
    }

    @Test
    void rejectsBlankProviderNameInConfiguration() {
      BedrockConfiguration cfg = new BedrockConfiguration() {
        @Override
        public String getRegion() {
          return "eu-west-1";
        }

        @Override
        public String getDefaultModel() {
          return "anthropic.claude-3-haiku-20240307-v1:0";
        }

        @Override
        public java.time.Duration getConnectTimeout() {
          return java.time.Duration.ofSeconds(1);
        }

        @Override
        public java.time.Duration getRequestTimeout() {
          return java.time.Duration.ofSeconds(1);
        }

        @Override
        public String getAnthropicVersion() {
          return "bedrock-2023-05-31";
        }

        @Override
        public String getProviderName() {
          return "  ";
        }
      };
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, mock(BedrockRuntimeClient.class)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Provider name");
    }

    @Test
    void acceptsBoundaryTemperatures() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg0 = FixedBedrockConfiguration.builder().temperature(0.0).build();
      var cfg1 = FixedBedrockConfiguration.builder().temperature(1.0).build();
      assertThat(new BedrockLlmClient(mapper, cfg0, client).getProviderName()).isEqualTo("bedrock");
      assertThat(new BedrockLlmClient(mapper, cfg1, client).getProviderName()).isEqualTo("bedrock");
    }

    @Test
    void rejectsNegativeTemperature() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().temperature(-0.01).build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("temperature");
    }

    @Test
    void rejectsBlankDefaultModel() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().defaultModel("  ").build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("default model");
    }

    @Test
    void rejectsNonAnthropicDefaultModel() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().defaultModel("amazon.titan-text-express-v1")
          .build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("anthropic.");
    }

    @Test
    void rejectsNonPositiveMaxTokens() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().maxTokens(0).build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxTokens");
    }

    @Test
    void rejectsNegativeMaxTokens() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().maxTokens(-1).build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxTokens");
    }

    @Test
    void rejectsTemperatureOutOfRange() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var cfg = FixedBedrockConfiguration.builder().temperature(1.5).build();
      assertThatThrownBy(() -> new BedrockLlmClient(mapper, cfg, client))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("temperature");
    }
  }

  @Nested
  class ExecuteTests {

    @Test
    void returnsAssistantTextFromInvokeModel() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      String responseJson = """
          {"role":"assistant","content":[{"type":"text","text":"OK"}]}
          """;
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(responseJson, StandardCharsets.UTF_8))
              .build());

      var cfg = FixedBedrockConfiguration.defaults();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      LlmExecutionRequest req = new LlmExecutionRequest(
          "bedrock", null, "system", "user");
      assertThat(llm.execute(req)).isEqualTo("OK");
    }

    @Test
    void invokeModelUsesRequestModelOverrideAndSerializesBody() throws Exception {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      String responseJson = """
          {"role":"assistant","content":[{"type":"text","text":"{\\"commands\\":[]}"}]}
          """;
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(responseJson, StandardCharsets.UTF_8))
              .build());

      String defaultModel = "anthropic.claude-3-haiku-20240307-v1:0";
      String requestedModel = "anthropic.claude-3-5-sonnet-20240620-v1:0";
      var cfg = FixedBedrockConfiguration.builder().defaultModel(defaultModel).build();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);

      String out = llm.execute(new LlmExecutionRequest(
          "bedrock",
          requestedModel,
          "You must reply with JSON only.",
          "Run workflow"));

      JsonNode parsed = mapper.readTree(out);
      assertThat(parsed.path("commands").isArray()).isTrue();

      ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
      verify(client).invokeModel(captor.capture());
      InvokeModelRequest sent = captor.getValue();
      assertThat(sent.modelId()).isEqualTo(requestedModel);
      JsonNode body = mapper.readTree(sent.body().asUtf8String());
      assertThat(body.path("anthropic_version").asText()).isEqualTo("bedrock-2023-05-31");
      assertThat(body.path("system").asText()).isEqualTo("You must reply with JSON only.");
      assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("Run workflow");
    }

    @Test
    void executeMatchesProviderNameCaseInsensitively() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(
                  "{\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}", StandardCharsets.UTF_8))
              .build());
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThat(llm.execute(new LlmExecutionRequest("BEDROCK", null, "s", "u"))).isEqualTo("x");
    }

    @Test
    void acceptsAnthropicModelIdWithVaryingCasePrefix() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(
                  "{\"content\":[{\"type\":\"text\",\"text\":\"y\"}]}", StandardCharsets.UTF_8))
              .build());
      String model = "ANTHROPIC.claude-3-haiku-20240307-v1:0";
      var cfg = FixedBedrockConfiguration.builder().defaultModel(model).build();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      assertThat(llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u"))).isEqualTo("y");
    }

    @Test
    void failsWhenResponseBodyIsBlank() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString("", StandardCharsets.UTF_8))
              .build());
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("blank");
    }

    @Test
    void failsWhenResponseBodyIsNull() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder().build());
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("blank");
    }

    @Test
    void failsWhenResponseJsonHasEmptyContentArray() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString("{\"content\":[]}", StandardCharsets.UTF_8))
              .build());
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("content");
    }

    @Test
    void wrapsIOExceptionFromJsonParsingAsLlmInvocationException() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString("not-json", StandardCharsets.UTF_8))
              .build());
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("could not be parsed")
          .cause()
          .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mapsAwsServiceExceptionToLlmInvocationException() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      var ex = software.amazon.awssdk.services.bedrockruntime.model.ValidationException.builder()
          .message("bad")
          .build();
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenThrow(ex);

      var cfg = FixedBedrockConfiguration.defaults();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      LlmExecutionRequest req = new LlmExecutionRequest("bedrock", null, "s", "u");
      assertThatThrownBy(() -> llm.execute(req))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("bedrock HTTP error");
    }

    @Test
    void mapsGenericAwsErrorDetails() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      AwsErrorDetails details = AwsErrorDetails.builder()
          .errorCode("ThrottlingException")
          .errorMessage("Slow down")
          .build();
      var ex = software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException.builder()
          .awsErrorDetails(details)
          .statusCode(429)
          .build();
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenThrow(ex);

      var cfg = FixedBedrockConfiguration.defaults();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      assertThatThrownBy(() -> llm.execute(
          new LlmExecutionRequest("bedrock", null, "s", "u")))
          .isInstanceOf(LlmInvocationException.class)
          .hasMessageContaining("429")
          .hasMessageContaining("ThrottlingException");
    }

    @Test
    void rejectsWrongProviderOnRequest() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(new LlmExecutionRequest("openai", null, "s", "u")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("provider");
    }

    @Test
    void rejectsNonAnthropicModelOverride() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(
          new LlmExecutionRequest("bedrock", "meta.foo", "s", "u")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("anthropic.");
    }

    @Test
    void invoke_model_prefers_request_max_output_tokens_over_configuration() throws Exception {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(
                  "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}", StandardCharsets.UTF_8))
              .build());

      BedrockConfiguration cfg =
          FixedBedrockConfiguration.builder().maxTokens(100).build();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u", 50));

      ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
      verify(client).invokeModel(captor.capture());
      JsonNode body = mapper.readTree(captor.getValue().body().asUtf8String());
      assertThat(body.path("max_tokens").asInt()).isEqualTo(50);
    }

    @Test
    void invoke_model_uses_config_max_tokens_when_request_max_output_tokens_absent()
        throws Exception {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      when(client.invokeModel(isA(InvokeModelRequest.class))).thenReturn(
          InvokeModelResponse.builder()
              .body(SdkBytes.fromString(
                  "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}", StandardCharsets.UTF_8))
              .build());

      BedrockConfiguration cfg =
          FixedBedrockConfiguration.builder().maxTokens(333).build();
      BedrockLlmClient llm = new BedrockLlmClient(mapper, cfg, client);
      llm.execute(new LlmExecutionRequest("bedrock", null, "s", "u"));

      ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
      verify(client).invokeModel(captor.capture());
      JsonNode body = mapper.readTree(captor.getValue().body().asUtf8String());
      assertThat(body.path("max_tokens").asInt()).isEqualTo(333);
    }

    @Test
    void rejects_non_positive_max_output_tokens_on_request() {
      BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
      BedrockLlmClient llm = new BedrockLlmClient(mapper, FixedBedrockConfiguration.defaults(),
          client);
      assertThatThrownBy(() -> llm.execute(
          new LlmExecutionRequest("bedrock", null, "s", "u", 0)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxOutputTokens");
    }
  }
}
