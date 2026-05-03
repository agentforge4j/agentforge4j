package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.LlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Amazon Bedrock LLM client implementation for Anthropic Claude models.
 * <p>
 * Sends requests to AWS Bedrock Runtime and extracts the assistant's text response.
 */
@ToString(exclude = {"objectMapper", "bedrockClient"})
public final class BedrockLlmClient implements LlmClient {

  private static final System.Logger LOG = System.getLogger(BedrockLlmClient.class.getName());

  private final String providerName;
  private final String defaultModel;
  private final ObjectMapper objectMapper;
  private final BedrockConfiguration config;
  private final BedrockRuntimeClient bedrockClient;
  private final BedrockAnthropicInvokeSerializer serializer;
  private final BedrockAnthropicResponseParser responseParser;

  public BedrockLlmClient(
      ObjectMapper objectMapper,
      BedrockConfiguration config,
      BedrockRuntimeClient bedrockClient) {
    this.objectMapper = Validate.notNull(objectMapper, "Bedrock ObjectMapper must not be null");
    this.config = Validate.notNull(config, "Bedrock configuration must not be null");
    this.bedrockClient = Validate.notNull(bedrockClient, "BedrockRuntimeClient must not be null");
    this.providerName = Validate.notBlank(config.getProviderName(),
        "Provider name must be provided");
    this.defaultModel = Validate.notBlank(config.getDefaultModel(),
        "%s default model must be provided".formatted(config.getProviderName()));
    BedrockAnthropicInvokeSerializer.validateAnthropicModelId(defaultModel);
    Integer maxTokens = config.getMaxTokens();
    Validate.isTrue(maxTokens == null || maxTokens > 0,
        "Bedrock maxTokens must be positive when set");
    Double temperature = config.getTemperature();
    Validate.isTrue(temperature == null || (temperature >= 0.0 && temperature <= 1.0),
        "Bedrock temperature must be between 0.0 and 1.0 inclusive when set");
    this.serializer = new BedrockAnthropicInvokeSerializer(objectMapper);
    this.responseParser = new BedrockAnthropicResponseParser();
  }

  @Override
  public String getProviderName() {
    return providerName;
  }

  /**
   * Executes an LLM request against Amazon Bedrock.
   *
   * @param request the execution request containing system prompt, user input, and model details
   * @return the assistant's text response
   * @throws LlmInvocationException if the request fails or response cannot be parsed
   */
  @Override
  public String execute(LlmExecutionRequest request) {
    validateRequest(request);
    String modelId = StringUtils.defaultIfBlank(request.model(), defaultModel);
    BedrockAnthropicInvokeSerializer.validateAnthropicModelId(modelId);
    String bodyJson = serializer.toJson(request, modelId, config);
    try {
      InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
          .modelId(modelId)
          .body(SdkBytes.fromUtf8String(bodyJson))
          .build();
      LOG.log(System.Logger.Level.DEBUG, "Bedrock invokeModel modelId={0}", modelId);
      InvokeModelResponse response = bedrockClient.invokeModel(invokeRequest);
      String utf8 = response.body() == null ? "" : response.body().asUtf8String();
      return responseParser.extractAssistantText(utf8, objectMapper);
    } catch (AwsServiceException e) {
      throw mapAwsServiceException(e);
    } catch (IOException e) {
      LOG.log(System.Logger.Level.ERROR, "Bedrock response parse failure modelId={0}", modelId, e);
      throw new LlmInvocationException(
          "bedrock response could not be parsed for model %s".formatted(modelId),
          e);
    }
  }

  private void validateRequest(LlmExecutionRequest request) {
    Validate.notNull(request, "Request must not be null");
    Validate.notBlank(request.providerName(), "Request provider must be specified");
    Validate.isTrue(
        providerName.equalsIgnoreCase(request.providerName()),
        "Request provider '%s' does not match client provider '%s'".formatted(
            request.providerName(), providerName));
    Validate.notBlank(request.userInput(), "Request user input must be provided");
    Validate.notBlank(request.systemPrompt(), "Request system prompt must be provided");
  }

  private static LlmInvocationException mapAwsServiceException(AwsServiceException e) {
    String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "";
    String msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    int status = e.statusCode();
    String summary = "bedrock HTTP error: %s - %s %s".formatted(status, code, msg).strip();
    return new LlmInvocationException(summary, e);
  }
}
