package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmExecutionRequestValidator;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Amazon Bedrock LLM client supporting multiple model families within a single provider.
 * <p>
 * Each request's {@code modelId} is resolved to a family via {@link BedrockModelRegistry}, which
 * also selects the {@link BedrockTransport}: Anthropic Claude over {@code InvokeModel} (token
 * reporting and prompt caching unchanged) and other families (Llama, Nova, Titan) over
 * {@code Converse} (text generation and token reporting). Authentication follows the AWS default
 * credentials provider chain.
 */
@ToString(exclude = {"invokeModelTransport", "converseTransport"})
public final class BedrockLlmClient implements LlmClient {

  private final String providerName;
  private final String defaultModel;
  private final BedrockModelRegistry registry;
  private final InvokeModelTransport invokeModelTransport;
  private final ConverseTransport converseTransport;

  public BedrockLlmClient(
      ObjectMapper objectMapper,
      BedrockConfiguration config,
      BedrockRuntimeClient bedrockClient) {
    Validate.notNull(objectMapper, "Bedrock ObjectMapper must not be null");
    Validate.notNull(config, "Bedrock configuration must not be null");
    Validate.notNull(bedrockClient, "BedrockRuntimeClient must not be null");
    this.providerName = Validate.notBlank(config.getProviderName(),
        "Provider name must be provided");
    this.defaultModel = Validate.notBlank(config.getDefaultModel(),
        "%s default model must be provided".formatted(config.getProviderName()));
    // Legacy config debt: anthropicVersion is mandatory for all families even though Converse
    // families (Llama/Nova/Titan) do not use it. Flagged for a future config-cleanup PR.
    Validate.notBlank(config.getAnthropicVersion(), "Bedrock anthropicVersion must be provided");
    this.registry = new BedrockModelRegistry();
    // Config-time validation: an unsupported default model is operator misconfiguration, so it
    // fails construction with IllegalArgumentException (clean startup), unlike an unknown
    // per-request model id which fails execute() with LlmInvocationException.
    Validate.isTrue(registry.supports(defaultModel),
        "Bedrock default model is not a supported family: %s".formatted(defaultModel));
    Integer maxTokens = config.getMaxTokens();
    Validate.isTrue(maxTokens == null || maxTokens > 0,
        "Bedrock maxTokens must be positive when set");
    Double temperature = config.getTemperature();
    Validate.isTrue(temperature == null || (temperature >= 0.0 && temperature <= 1.0),
        "Bedrock temperature must be between 0.0 and 1.0 inclusive when set");
    this.invokeModelTransport = new InvokeModelTransport(objectMapper, config, bedrockClient);
    this.converseTransport = new ConverseTransport(config, bedrockClient);
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  /**
   * Executes an LLM request against Amazon Bedrock.
   * <p>
   * The request's {@code modelId} (or the configured default) is resolved to a family and
   * transport; the request is then dispatched to that transport. Generated length is capped by
   * {@link LlmExecutionRequest#maxOutputTokens()} when set; otherwise
   * {@link BedrockConfiguration#getMaxTokens()} when positive; otherwise an internal default.
   *
   * @param request the execution request containing system prompt, user input, and model details
   *
   * @return execution response with assistant text and provider token usage when reported
   *
   * @throws LlmInvocationException if the model id is unsupported, the request fails, or the
   *                                response cannot be parsed
   */
  @Override
  public LlmExecutionResponse execute(LlmExecutionRequest request) {
    LlmExecutionRequestValidator.validate(request, providerName);
    String modelId = StringUtils.defaultIfBlank(request.model(), defaultModel);
    BedrockModelResolution resolution = registry.resolve(modelId);
    BedrockTransport transport = switch (resolution.transport()) {
      case INVOKE_MODEL -> invokeModelTransport;
      case CONVERSE -> converseTransport;
    };
    return transport.execute(request, modelId, resolution.capabilities());
  }
}
