package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmExecutionRequest;
import com.agentforge4j.llm.LlmInvocationException;
import com.agentforge4j.llm.gemini.dto.GeminiCandidate;
import com.agentforge4j.llm.gemini.dto.GeminiContent;
import com.agentforge4j.llm.gemini.dto.GeminiErrorResponse;
import com.agentforge4j.llm.gemini.dto.GeminiPart;
import com.agentforge4j.llm.gemini.dto.GeminiRequest;
import com.agentforge4j.llm.gemini.dto.GeminiResponse;
import com.agentforge4j.llm.gemini.dto.GeminiSystemInstruction;
import com.agentforge4j.llm.gemini.dto.InputRole;
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

/**
 * Gemini implementation of {@link com.agentforge4j.llm.LlmClient}.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class GeminiLlmClient extends AbstractHttpLlmClient {

  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final Duration requestTimeout;

  /**
   * Creates a new Gemini LLM client with the given configuration.
   *
   * @param objectMapper the ObjectMapper for JSON serialization and deserialization
   * @param config       the Gemini configuration
   */
  public GeminiLlmClient(ObjectMapper objectMapper, GeminiConfiguration config) {
    super(config);
    this.objectMapper = Validate.notNull(objectMapper, "Gemini ObjectMapper must not be null");
    this.apiKey = Validate.notBlank(config.getApiKey(), "Gemini apiKey must be provided");
    this.baseUrl = StringUtils.stripEnd(
        Validate.notBlank(config.getBaseUrl(), "Gemini baseUrl must be provided"), "/");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Gemini request timeout must be provided");
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    URI uri = buildUri(model);
    String body = generateRequestBody(request);
    return HttpRequest.newBuilder(uri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private URI buildUri(String model) {
    String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8)
        .replace("+", "%20");

    String encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

    return URI.create("%s/v1beta/models/%s:generateContent?key=%s".formatted(
        baseUrl,
        encodedModel,
        encodedApiKey
    ));
  }

  @Override
  protected String validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be null"));
    GeminiResponse dto = objectMapper.readValue(json, GeminiResponse.class);
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "Gemini response deserialized to null: %s".formatted(json)));
    Validate.isTrue(dto.error() == null, () ->
        new LlmInvocationException("gemini error: %s".formatted(formatError(dto.error()))));
    List<GeminiCandidate> candidates = Validate.notEmpty(dto.candidates(),
        () -> new LlmInvocationException("Gemini response has no candidates: %s".formatted(json)));
    GeminiCandidate first = Validate.notNull(candidates.get(0),
        () -> new LlmInvocationException("Gemini first candidate is null: %s".formatted(json)));
    Validate.isTrue(
        first.finishReason() == null || !"SAFETY".equalsIgnoreCase(first.finishReason()),
        () -> new LlmInvocationException("Gemini blocked response for safety: %s".formatted(json)));
    GeminiContent content = Validate.notNull(first.content(),
        () -> new LlmInvocationException("Gemini candidate content is null: %s".formatted(json)));
    List<GeminiPart> parts = Validate.notEmpty(content.parts(),
        () -> new LlmInvocationException("Gemini candidate parts are empty: %s".formatted(json)));
    GeminiPart part = Validate.notNull(parts.get(0),
        () -> new LlmInvocationException("Gemini first part is null: %s".formatted(json)));
    String text = Validate.notBlank(part.text(),
        () -> new LlmInvocationException("Gemini response text is blank: %s".formatted(json)));
    return stripCodeFence(text.strip());
  }

  private String formatError(GeminiErrorResponse error) {
    if (error == null) {
      return "unknown error";
    }

    String message = StringUtils.defaultIfBlank(error.message(), "no message");
    String status = StringUtils.defaultIfBlank(error.status(), "no status");

    return "status=%s, message=%s".formatted(status, message);
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    GeminiRequest body = new GeminiRequest(
        new GeminiSystemInstruction(List.of(new GeminiPart(request.systemPrompt()))),
        List.of(new GeminiContent(InputRole.USER, List.of(new GeminiPart(request.userInput())))));
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize Gemini request", e);
    }
  }
}
