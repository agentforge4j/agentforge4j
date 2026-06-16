// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.util.Validate;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * Transport for non-Anthropic Bedrock families (Llama, Nova, Titan) over the {@code Converse} API.
 * <p>
 * Supports synchronous text generation and token reporting only. Prompt-cache hints
 * ({@code promptLayerBoundaries}) are ignored for families whose capabilities report no
 * prompt-cache support — consistent with how other non-caching providers behave; a single DEBUG
 * breadcrumb is logged when a caching hint is dropped.
 */
final class ConverseTransport implements BedrockTransport {

  private static final System.Logger LOG = System.getLogger(ConverseTransport.class.getName());

  private final BedrockConfiguration config;
  private final BedrockRuntimeClient bedrockClient;

  ConverseTransport(BedrockConfiguration config, BedrockRuntimeClient bedrockClient) {
    this.config = Validate.notNull(config, "Bedrock configuration must not be null");
    this.bedrockClient = Validate.notNull(bedrockClient, "BedrockRuntimeClient must not be null");
  }

  @Override
  public LlmExecutionResponse execute(
      LlmExecutionRequest request, String modelId, BedrockModelCapabilities capabilities) {
    if (request.promptLayerBoundaries() != null && !capabilities.promptCache()) {
      LOG.log(System.Logger.Level.DEBUG,
          "prompt cache requested but model {0} does not support caching; ignoring", modelId);
    }
    ConverseRequest converseRequest = buildRequest(request, modelId);
    try {
      ConverseResponse response = bedrockClient.converse(converseRequest);
      return new LlmExecutionResponse(
          extractText(response, modelId),
          StringUtils.trimToNull(modelId),
          toTokenUsageReport(response.usage()));
    } catch (AwsServiceException e) {
      throw BedrockServiceExceptions.map(e);
    }
  }

  private ConverseRequest buildRequest(LlmExecutionRequest request, String modelId) {
    InferenceConfiguration.Builder inference = InferenceConfiguration.builder()
        .maxTokens(BedrockInference.resolveMaxTokens(request, config));
    Double temperature = config.getTemperature();
    if (temperature != null) {
      inference.temperature(temperature.floatValue());
    }
    return ConverseRequest.builder()
        .modelId(modelId)
        .system(SystemContentBlock.fromText(request.systemPrompt()))
        .messages(Message.builder()
            .role(ConversationRole.USER)
            .content(ContentBlock.fromText(request.userInput()))
            .build())
        .inferenceConfig(inference.build())
        .build();
  }

  private static String extractText(ConverseResponse response, String modelId) {
    ConverseOutput output = response.output();
    Message message = output == null ? null : output.message();
    List<ContentBlock> content = Validate.notEmpty(message == null ? null : message.content(),
        () -> new LlmInvocationException(
            "Bedrock Converse response missing content for model %s".formatted(modelId)));

    for (ContentBlock block : content) {
      if (block != null && block.type() == ContentBlock.Type.TEXT
          && StringUtils.isNotBlank(block.text())) {
        return LlmClient.stripCodeFence(block.text().strip());
      }
    }

    throw new LlmInvocationException(
        "Bedrock Converse response has no text content block for model %s".formatted(modelId));
  }

  private static TokenUsageReport toTokenUsageReport(TokenUsage usage) {
    if (usage == null) {
      return null;
    }
    return new TokenUsageReport(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cacheReadInputTokens(),
        usage.cacheWriteInputTokens());
  }
}
