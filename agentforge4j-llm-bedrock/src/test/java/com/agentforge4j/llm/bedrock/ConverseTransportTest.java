// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.PromptLayerBoundaries;
import com.agentforge4j.llm.api.TokenUsageReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConverseTransportTest {

  private static final String NOVA = "amazon.nova-lite-v1:0";
  private final BedrockModelCapabilities novaCaps = new BedrockModelCapabilities(false);

  private static ConverseResponse responseWith(String text, TokenUsage usage) {
    ConverseResponse.Builder builder = ConverseResponse.builder()
        .output(ConverseOutput.builder()
            .message(Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromText(text))
                .build())
            .build());
    if (usage != null) {
      builder.usage(usage);
    }
    return builder.build();
  }

  private static ConverseTransport transport(BedrockRuntimeClient client) {
    return new ConverseTransport(
        FixedBedrockConfiguration.builder().defaultModel(NOVA).build(), client);
  }

  @Test
  void mapsTextAndStripsCodeFence() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class)))
        .thenReturn(responseWith("```json\n{\"a\":1}\n```", null));

    LlmExecutionResponse response = transport(client).execute(
        new LlmExecutionRequest("bedrock", NOVA, "sys", "user", null, null, null), NOVA, novaCaps);

    assertThat(response.text()).isEqualTo("{\"a\":1}");
    assertThat(response.modelUsed()).isEqualTo(NOVA);
    assertThat(response.tokenUsage()).isNull();
  }

  @Test
  void mapsTokenUsageDroppingTotal() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    TokenUsage usage = TokenUsage.builder()
        .inputTokens(11).outputTokens(7).totalTokens(18)
        .cacheReadInputTokens(3).cacheWriteInputTokens(2).build();
    when(client.converse(isA(ConverseRequest.class))).thenReturn(responseWith("ok", usage));

    TokenUsageReport report = transport(client).execute(
        new LlmExecutionRequest("bedrock", NOVA, "s", "u", null, null, null), NOVA, novaCaps).tokenUsage();

    assertThat(report).isNotNull();
    assertThat(report.inputTokens()).isEqualTo(11);
    assertThat(report.outputTokens()).isEqualTo(7);
    assertThat(report.cachedInputTokens()).isEqualTo(3);
    assertThat(report.cacheWriteTokens()).isEqualTo(2);
  }

  @Test
  void buildsConverseRequestWithSystemUserAndInferenceConfig() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class))).thenReturn(responseWith("ok", null));
    ConverseTransport transport = new ConverseTransport(
        FixedBedrockConfiguration.builder().defaultModel(NOVA).temperature(0.5).build(), client);

    transport.execute(
        new LlmExecutionRequest("bedrock", NOVA, "the-system", "the-user", 321, null, null), NOVA, novaCaps);

    ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(client).converse(captor.capture());
    ConverseRequest sent = captor.getValue();
    assertThat(sent.modelId()).isEqualTo(NOVA);
    assertThat(sent.system().get(0).text()).isEqualTo("the-system");
    assertThat(sent.messages().get(0).role()).isEqualTo(ConversationRole.USER);
    assertThat(sent.messages().get(0).content().get(0).text()).isEqualTo("the-user");
    assertThat(sent.inferenceConfig().maxTokens()).isEqualTo(321);
    assertThat(sent.inferenceConfig().temperature()).isEqualTo(0.5f);
  }

  @Test
  void omitsTemperatureWhenNull() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class))).thenReturn(responseWith("ok", null));

    transport(client).execute(new LlmExecutionRequest("bedrock", NOVA, "s", "u", null, null, null), NOVA, novaCaps);

    ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(client).converse(captor.capture());
    assertThat(captor.getValue().inferenceConfig().temperature()).isNull();
  }

  @Test
  void gracefullyIgnoresPromptLayerBoundaries() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class))).thenReturn(responseWith("ok", null));
    LlmExecutionRequest request = new LlmExecutionRequest(
        "bedrock", NOVA, "sys", "user", null, new PromptLayerBoundaries(3, 3, null), null);

    LlmExecutionResponse response = transport(client).execute(request, NOVA, novaCaps);

    assertThat(response.text()).isEqualTo("ok");
    verify(client).converse(isA(ConverseRequest.class));
  }

  @Test
  void mapsAwsServiceExceptionToLlmInvocationException() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class))).thenThrow(ThrottlingException.builder()
        .awsErrorDetails(AwsErrorDetails.builder()
            .errorCode("ThrottlingException").errorMessage("slow").build())
        .statusCode(429).build());

    assertThatThrownBy(() -> transport(client).execute(
        new LlmExecutionRequest("bedrock", NOVA, "s", "u", null, null, null), NOVA, novaCaps))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("bedrock HTTP error")
        .hasMessageContaining("429");
  }

  @Test
  void failsWhenNoTextContentBlock() {
    BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
    when(client.converse(isA(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
        .output(ConverseOutput.builder()
            .message(Message.builder().role(ConversationRole.ASSISTANT).build())
            .build())
        .build());

    assertThatThrownBy(() -> transport(client).execute(
        new LlmExecutionRequest("bedrock", NOVA, "s", "u", null, null, null), NOVA, novaCaps))
        .isInstanceOf(LlmInvocationException.class)
        .hasMessageContaining("content");
  }
}
