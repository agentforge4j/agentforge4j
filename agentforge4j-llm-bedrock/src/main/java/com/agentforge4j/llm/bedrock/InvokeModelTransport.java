package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Anthropic Claude transport over Bedrock {@code InvokeModel}. The serialize/invoke/parse/error-map
 * behaviour is unchanged from the original single-family client.
 */
final class InvokeModelTransport implements BedrockTransport {

  private static final System.Logger LOG = System.getLogger(InvokeModelTransport.class.getName());

  private final ObjectMapper objectMapper;
  private final BedrockConfiguration config;
  private final BedrockRuntimeClient bedrockClient;
  private final BedrockAnthropicInvokeSerializer serializer;
  private final BedrockAnthropicResponseParser responseParser;

  InvokeModelTransport(
      ObjectMapper objectMapper, BedrockConfiguration config, BedrockRuntimeClient bedrockClient) {
    this.objectMapper = Validate.notNull(objectMapper, "Bedrock ObjectMapper must not be null");
    this.config = Validate.notNull(config, "Bedrock configuration must not be null");
    this.bedrockClient = Validate.notNull(bedrockClient, "BedrockRuntimeClient must not be null");
    this.serializer = new BedrockAnthropicInvokeSerializer(objectMapper);
    this.responseParser = new BedrockAnthropicResponseParser();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Anthropic Claude on Bedrock always honours {@code promptLayerBoundaries} via its serializer, so
   * {@code capabilities} is not consulted here.
   */
  @Override
  public LlmExecutionResponse execute(
      LlmExecutionRequest request, String modelId, BedrockModelCapabilities capabilities) {
    String bodyJson = serializer.toJson(request, modelId, config);
    try {
      InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
          .modelId(modelId)
          .body(SdkBytes.fromUtf8String(bodyJson))
          .build();
      LOG.log(System.Logger.Level.DEBUG, "Bedrock invokeModel modelId={0}", modelId);
      InvokeModelResponse response = bedrockClient.invokeModel(invokeRequest);
      String utf8 = response.body() == null ? "" : response.body().asUtf8String();
      return responseParser.parse(utf8, objectMapper, modelId);
    } catch (AwsServiceException e) {
      throw BedrockServiceExceptions.map(e);
    } catch (IOException e) {
      LOG.log(System.Logger.Level.ERROR, "Bedrock response parse failure modelId={0}", modelId, e);
      throw new LlmInvocationException(
          "bedrock response could not be parsed for model %s".formatted(modelId),
          e);
    }
  }
}
