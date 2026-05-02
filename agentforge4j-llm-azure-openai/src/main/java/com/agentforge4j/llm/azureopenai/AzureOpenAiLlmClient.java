package com.agentforge4j.llm.azureopenai;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.openai.dto.InputRole;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionChoiceDto;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionMessageDto;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionMessageResponseDto;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionRequestDto;
import com.agentforge4j.llm.openai.dto.OpenAiChatCompletionResponseDto;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class AzureOpenAiLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final String deploymentName;
  private final ObjectMapper objectMapper;
  private final URI chatCompletionsUri;
  private final Duration requestTimeout;

  public AzureOpenAiLlmClient(ObjectMapper objectMapper, AzureOpenAiConfiguration config) {
    super(config);
    Validate.notBlank(config.getEndpoint(), "Azure OpenAI endpoint must be provided");
    Validate.notBlank(config.getApiVersion(), "Azure OpenAI apiVersion must be provided");
    this.objectMapper = Validate.notNull(objectMapper,
        "Azure OpenAI ObjectMapper must not be null");
    this.apiKey = Validate.notBlank(config.getApiKey(), "Azure OpenAI apiKey must be provided");
    this.deploymentName = Validate.notBlank(config.getDeploymentName(),
        "Azure OpenAI deploymentName must be provided");
    this.requestTimeout = config.getRequestTimeout();
    this.chatCompletionsUri = buildChatUri(config);
  }

  private static URI buildChatUri(AzureOpenAiConfiguration config) {
    String root = StringUtils.stripEnd(config.getEndpoint(), "/");
    String deployment = config.getDeploymentName();
    String version = URLEncoder.encode(config.getApiVersion(), StandardCharsets.UTF_8)
        .replace("+", "%20");
    String url =
        root + "/openai/deployments/" + deployment + "/chat/completions?api-version=" + version;
    return URI.create(url);
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    return HttpRequest.newBuilder(chatCompletionsUri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(request)))
        .build();
  }

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be null"));
    OpenAiChatCompletionResponseDto dto = objectMapper.readValue(json,
        OpenAiChatCompletionResponseDto.class);
    validateResponse(json, dto);

    OpenAiChatCompletionChoiceDto firstChoice = retrieveFirstChoice(json, dto);
    OpenAiChatCompletionMessageResponseDto message = firstChoice.message();
    String content = message == null ? null : message.content();
    Validate.notBlank(content, () -> new
        LlmInvocationException(
        "azure-openai response first choice content is blank for deployment %s: %s".formatted(
            deploymentName, json)));
    return stripCodeFence(content.strip());
  }

  private OpenAiChatCompletionChoiceDto retrieveFirstChoice(String json,
      OpenAiChatCompletionResponseDto dto) {
    List<OpenAiChatCompletionChoiceDto> choices = Validate.notEmpty(dto.choices(), () -> new
        LlmInvocationException(
        "azure-openai response choices are empty for deployment %s: %s".formatted(deploymentName,
            json)));
    return Validate.notNull(choices.get(0), () -> new
        LlmInvocationException(
        "azure-openai first choice is null for deployment %s: %s".formatted(deploymentName, json)));
  }

  private static void validateResponse(String json, OpenAiChatCompletionResponseDto dto) {
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "azure-openai response deserialized to null: %s".formatted(json)));
    Validate.isTrue(dto.error() == null || StringUtils.isBlank(dto.error().message()),
        () -> new LlmInvocationException(
            "azure-openai error: %s".formatted(dto.error().message())));
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    OpenAiChatCompletionRequestDto body = new OpenAiChatCompletionRequestDto(
        deploymentName,
        List.of(
            new OpenAiChatCompletionMessageDto(InputRole.SYSTEM, request.systemPrompt()),
            new OpenAiChatCompletionMessageDto(InputRole.USER, request.userInput())));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException(
          "Failed to serialize azure-openai request for deployment %s".formatted(deploymentName),
          e);
    }
  }
}
