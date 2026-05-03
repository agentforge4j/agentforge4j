package com.agentforge4j.llm.vllm;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.vllm.dto.InputRole;
import com.agentforge4j.llm.vllm.dto.VllmChoice;
import com.agentforge4j.llm.vllm.dto.VllmMessage;
import com.agentforge4j.llm.vllm.dto.VllmRequest;
import com.agentforge4j.llm.vllm.dto.VllmResponse;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public final class VllmLlmClient extends AbstractHttpLlmClient {

  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  public VllmLlmClient(ObjectMapper objectMapper, VllmConfiguration config) {
    super(config);
    this.objectMapper = Validate.notNull(objectMapper, "vLLM ObjectMapper must not be null");
    this.chatCompletionsUri = URI.create(
        Validate.notBlank(config.getUrl(), "vLLM URL must be provided"));
    this.requestTimeout = config.getRequestTimeout();
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatCompletionsUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    VllmResponse response = objectMapper.readValue(json, VllmResponse.class);
    List<VllmChoice> choices = response == null ? null : response.choices();
    Validate.notEmpty(choices, () -> new LlmInvocationException(
        "vLLM response choices are empty: %s".formatted(json)));

    String content = choices.get(0) == null || choices.get(0).message() == null
        ? null
        : choices.get(0).message().content();
    Validate.notBlank(content, () -> new LlmInvocationException(
        "vLLM response first choice content is blank: %s".formatted(json)));

    return stripCodeFence(content.strip());
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    VllmRequest body = new VllmRequest(
        model,
        List.of(
            new VllmMessage(InputRole.SYSTEM, request.systemPrompt()),
            new VllmMessage(InputRole.USER, request.userInput())),
        false);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize vLLM request for model %s".formatted(model), e);
    }
  }
}
