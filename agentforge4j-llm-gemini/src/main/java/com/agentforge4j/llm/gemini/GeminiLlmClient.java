// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.gemini;

import com.agentforge4j.llm.AbstractHttpLlmClient;
import com.agentforge4j.llm.LlmHttpErrorBodyTruncate;
import com.agentforge4j.llm.api.LlmClient;
import com.agentforge4j.llm.api.LlmExecutionRequest;
import com.agentforge4j.llm.api.LlmExecutionResponse;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.llm.api.TokenUsageReport;
import com.agentforge4j.llm.gemini.dto.GeminiCandidate;
import com.agentforge4j.llm.gemini.dto.GeminiContent;
import com.agentforge4j.llm.gemini.dto.GeminiErrorResponse;
import com.agentforge4j.llm.gemini.dto.GeminiGenerationConfig;
import com.agentforge4j.llm.gemini.dto.GeminiPart;
import com.agentforge4j.llm.gemini.dto.GeminiRequest;
import com.agentforge4j.llm.gemini.dto.GeminiResponse;
import com.agentforge4j.llm.gemini.dto.GeminiSystemInstruction;
import com.agentforge4j.llm.gemini.dto.GeminiUsageMetadata;
import com.agentforge4j.llm.wireprotocol.InputRole;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/**
 * Gemini implementation of {@link com.agentforge4j.llm.api.LlmClient}.
 */
@ToString(exclude = {"apiKey", "objectMapper"}, callSuper = true)
public final class GeminiLlmClient extends AbstractHttpLlmClient {

  private static final System.Logger LOG = System.getLogger(GeminiLlmClient.class.getName());

  private final GeminiConfiguration geminiConfiguration;
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
    this.geminiConfiguration = config;
    Integer configMaxOutputTokens = config.getMaxOutputTokens();
    Validate.isTrue(configMaxOutputTokens == null || configMaxOutputTokens > 0,
        "Gemini maxOutputTokens must be positive when set");
    this.objectMapper = Validate.notNull(objectMapper, "Gemini ObjectMapper must not be null");
    this.apiKey = Validate.notBlank(config.getApiKey(), "Gemini apiKey must be provided");
    this.baseUrl = StringUtils.stripEnd(
        Validate.notBlank(config.getBaseUrl(), "Gemini baseUrl must be provided"), "/");
    this.requestTimeout = Validate.notNull(config.getRequestTimeout(),
        "Gemini request timeout must be provided");
    warnIfApiKeyOverPlainHttp(this.baseUrl, this.apiKey);
  }

  @Override
  protected HttpRequest buildHttpRequest(LlmExecutionRequest request) {
    String model = StringUtils.defaultIfBlank(request.model(), getDefaultModel());
    URI uri = buildUri(model);
    String body = generateRequestBody(request);
    return HttpRequest.newBuilder(uri)
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("x-goog-api-key", apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  /**
   * Builds the generateContent URI for the given model. The API key is sent via the
   * {@code x-goog-api-key} header, never as a query parameter — query strings end up in access
   * logs, proxy logs, and debug output.
   */
  private URI buildUri(String model) {
    String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8)
        .replace("+", "%20");

    return URI.create("%s/v1beta/models/%s:generateContent".formatted(
        baseUrl,
        encodedModel
    ));
  }

  /**
   * Validates the Gemini generateContent payload and extracts text plus {@code usageMetadata}
   * ({@code usageMetadata.promptTokenCount}, {@code usageMetadata.candidatesTokenCount},
   * {@code usageMetadata.cachedContentTokenCount} when present) and root {@code modelVersion} for
   * {@link LlmExecutionResponse#modelUsed()}.
   *
   * @param json the raw JSON response from Gemini
   * @return execution response; {@link LlmExecutionResponse#tokenUsage()} is {@code null} when
   * {@code usageMetadata} is absent
   * @throws IOException if the response is invalid or cannot be parsed
   */
  @Override
  protected LlmExecutionResponse validateAndExtractResponse(String json) throws IOException {
    Validate.notBlank(json, () -> new LlmInvocationException("LLM client json must not be null"));
    LOG.log(System.Logger.Level.DEBUG, "Gemini response body (full) body={0}", json);
    String truncatedJson = LlmHttpErrorBodyTruncate.truncateForEmbeddedMessage(json);
    GeminiResponse dto = objectMapper.readValue(json, GeminiResponse.class);
    Validate.notNull(dto,
        () -> new LlmInvocationException(
            "Gemini response deserialized to null: %s".formatted(truncatedJson)));
    Validate.isTrue(dto.error() == null, () ->
        new LlmInvocationException("gemini error: %s".formatted(formatGeminiError(dto.error()))));
    List<GeminiCandidate> candidates = Validate.notEmpty(dto.candidates(),
        () -> new LlmInvocationException(
            "Gemini response has no candidates: %s".formatted(truncatedJson)));
    List<String> textSegments = new ArrayList<>();
    for (GeminiCandidate candidate : candidates) {
      Validate.notNull(candidate,
          () -> new LlmInvocationException("Gemini candidate is null: %s".formatted(truncatedJson)));
      Validate.isTrue(
          candidate.finishReason() == null || !"SAFETY".equalsIgnoreCase(candidate.finishReason()),
          () -> new LlmInvocationException(
              "Gemini blocked response for safety: %s".formatted(truncatedJson)));
      GeminiContent content = Validate.notNull(candidate.content(),
          () -> new LlmInvocationException(
              "Gemini candidate content is null: %s".formatted(truncatedJson)));
      List<GeminiPart> parts = Validate.notNull(content.parts(),
          () -> new LlmInvocationException(
              "Gemini candidate parts are null: %s".formatted(truncatedJson)));
      for (GeminiPart part : parts) {
        if (part == null) {
          continue;
        }
        if (StringUtils.isNotBlank(part.text())) {
          textSegments.add(part.text().strip());
        }
      }
    }
    String joined = String.join("\n", textSegments);
    String text = Validate.notBlank(joined,
        () -> new LlmInvocationException(
            "Gemini response text is blank: %s".formatted(truncatedJson)));
    return new LlmExecutionResponse(
        LlmClient.stripCodeFence(text.strip()),
        StringUtils.trimToNull(dto.modelVersion()),
        toTokenUsageReport(dto.usageMetadata()));
  }

  private static TokenUsageReport toTokenUsageReport(GeminiUsageMetadata usageMetadata) {
    if (usageMetadata == null) {
      return null;
    }
    return new TokenUsageReport(
        usageMetadata.promptTokenCount(),
        usageMetadata.candidatesTokenCount(),
        usageMetadata.cachedContentTokenCount(),
        null);
  }

  /**
   * Formats a Gemini API {@code error} object for exception messages without echoing raw JSON.
   */
  private static String formatGeminiError(GeminiErrorResponse error) {
    if (error == null) {
      return "unknown error";
    }
    StringJoiner joiner = new StringJoiner(", ");
    if (error.code() != null) {
      joiner.add("code=" + error.code());
    }
    if (StringUtils.isNotBlank(error.status())) {
      joiner.add("status=" + error.status().strip());
    }
    if (StringUtils.isNotBlank(error.message())) {
      joiner.add("message=" + error.message().strip());
    }
    if (joiner.length() == 0) {
      return "unspecified provider error";
    }
    return joiner.toString();
  }

  private String generateRequestBody(LlmExecutionRequest request) {
    Integer resolvedMax = resolveMaxOutputTokens(request, geminiConfiguration);
    GeminiGenerationConfig generationConfig =
        resolvedMax == null ? null : new GeminiGenerationConfig(resolvedMax);
    GeminiRequest body = new GeminiRequest(
        new GeminiSystemInstruction(List.of(new GeminiPart(request.systemPrompt()))),
        List.of(new GeminiContent(InputRole.USER, List.of(new GeminiPart(request.userInput())))),
        generationConfig);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new LlmInvocationException("Failed to serialize Gemini request", e);
    }
  }

  /**
   * Request {@code maxOutputTokens} overrides configuration; otherwise a positive configuration
   * value is used. When neither applies, {@code null} so the field is omitted from the payload.
   */
  private static Integer resolveMaxOutputTokens(
      LlmExecutionRequest request, GeminiConfiguration config) {
    if (request.maxOutputTokens() != null) {
      return request.maxOutputTokens();
    }
    Integer fromConfig = config.getMaxOutputTokens();
    if (fromConfig != null && fromConfig > 0) {
      return fromConfig;
    }
    return null;
  }
}
