package com.agentforge4j.bootstrap;

import com.agentforge4j.util.Validate;
import java.time.Duration;

/**
 * Immutable configuration for a single LLM provider, used by
 * {@link AgentForge4jBootstrap.Builder#withLlmProvider(LlmProviderConfig)}.
 *
 * <p>Construct via the per-provider static factory methods:
 * <pre>{@code
 * LlmProviderConfig config = LlmProviderConfig.openai()
 *     .defaults()
 *     .apiKey("sk-...")
 *     .build();
 * }</pre>
 *
 * <p>All fields except {@code provider} are nullable. A null {@code apiKey} means
 * "not configured at this layer; resolved from environment or system properties later." A null
 * {@code baseUrl} or {@code defaultModel} means "use the provider module's own default."
 *
 * @param provider       non-blank provider id
 * @param apiKey         optional API key for this layer
 * @param baseUrl        optional base URL; null uses the provider module default
 * @param defaultModel   optional default model; null uses the provider module default
 * @param connectTimeout optional connect timeout; null uses the provider module default
 */
public record LlmProviderConfig(
    String provider,
    String apiKey,
    String baseUrl,
    String defaultModel,
    Duration connectTimeout) {

  /**
   * Validates {@code provider} is non-blank.
   */
  public LlmProviderConfig {
    Validate.notBlank(provider, "provider cannot be blank");
  }

  /**
   * Returns a builder pre-populated with OpenAI defaults.
   *
   * @return builder for OpenAI configuration
   */
  public static ProviderBuilder openai() {
    return new ProviderBuilder(
        "openai",
        "https://api.openai.com/v1",
        "gpt-4o",
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder pre-populated with Anthropic Claude defaults.
   *
   * @return builder for Claude configuration
   */
  public static ProviderBuilder claude() {
    return new ProviderBuilder(
        "claude",
        "https://api.anthropic.com",
        "claude-sonnet-4-5",
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder pre-populated with Ollama defaults.
   *
   * @return builder for Ollama configuration
   */
  public static ProviderBuilder ollama() {
    return new ProviderBuilder(
        "ollama",
        "http://localhost:11434",
        "llama3",
        Duration.ofSeconds(60));
  }

  /**
   * Returns a builder pre-populated with vLLM defaults.
   *
   * @return builder for vLLM configuration
   */
  public static ProviderBuilder vllm() {
    return new ProviderBuilder(
        "vllm",
        "http://localhost:8000",
        null,
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder pre-populated with Google Gemini defaults.
   *
   * @return builder for Gemini configuration
   */
  public static ProviderBuilder gemini() {
    return new ProviderBuilder(
        "gemini",
        "https://generativelanguage.googleapis.com",
        "gemini-2.0-flash",
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder pre-populated with Mistral defaults.
   *
   * @return builder for Mistral configuration
   */
  public static ProviderBuilder mistral() {
    return new ProviderBuilder(
        "mistral",
        "https://api.mistral.ai/v1",
        "mistral-small-latest",
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder for Azure OpenAI. {@code baseUrl} and {@code defaultModel} are {@code null}
   * by default — both are deployment-specific and must be set by the caller.
   *
   * @return builder for Azure OpenAI configuration
   */
  public static ProviderBuilder azureOpenAi() {
    return new ProviderBuilder(
        "azure-openai",
        null,
        null,
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder for any OpenAI-compatible endpoint. {@code baseUrl} and {@code defaultModel}
   * are {@code null} by default — both are deployment-specific and must be set by the caller.
   *
   * @return builder for OpenAI-compatible configuration
   */
  public static ProviderBuilder openAiCompatible() {
    return new ProviderBuilder(
        "openai-compatible",
        null,
        null,
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder for AWS Bedrock. {@code baseUrl} and {@code defaultModel} are {@code null} by
   * default — both are deployment-specific and must be set by the caller.
   *
   * @return builder for Bedrock configuration
   */
  public static ProviderBuilder bedrock() {
    return new ProviderBuilder(
        "bedrock",
        null,
        null,
        Duration.ofSeconds(30));
  }

  /**
   * Builder for {@link LlmProviderConfig}. Obtain via {@link LlmProviderConfig#openai()},
   * {@link #claude()}, etc.
   */
  public static final class ProviderBuilder {

    private final String provider;
    private String apiKey;
    private String baseUrl;
    private String defaultModel;
    private Duration connectTimeout;

    private ProviderBuilder(
        String provider,
        String baseUrl,
        String defaultModel,
        Duration connectTimeout) {
      this.provider = provider;
      this.baseUrl = baseUrl;
      this.defaultModel = defaultModel;
      this.connectTimeout = connectTimeout;
    }

    /**
     * Pre-populates this builder with the provider's recommended defaults ({@code baseUrl},
     * {@code defaultModel}, {@code connectTimeout}). The builder is already pre-populated on
     * construction; this method exists for readability:
     * {@code openai().defaults().apiKey(...).build()}.
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder defaults() {
      return this;
    }

    /**
     * Sets the API key.
     *
     * @param apiKey the API key; must not be blank
     * @return {@code this} for chaining
     */
    public ProviderBuilder apiKey(String apiKey) {
      Validate.notBlank(apiKey, "apiKey");
      this.apiKey = apiKey;
      return this;
    }

    /**
     * Overrides the provider base URL.
     *
     * @param baseUrl override the provider base URL; must not be blank
     * @return {@code this} for chaining
     */
    public ProviderBuilder baseUrl(String baseUrl) {
      Validate.notBlank(baseUrl, "baseUrl");
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * Overrides the provider default model.
     *
     * @param defaultModel override the provider default model; must not be blank
     * @return {@code this} for chaining
     */
    public ProviderBuilder defaultModel(String defaultModel) {
      Validate.notBlank(defaultModel, "defaultModel");
      this.defaultModel = defaultModel;
      return this;
    }

    /**
     * Overrides the provider connect timeout.
     *
     * @param connectTimeout override the provider connect timeout; must not be null
     * @return {@code this} for chaining
     */
    public ProviderBuilder connectTimeout(Duration connectTimeout) {
      Validate.notNull(connectTimeout, "connectTimeout");
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Builds the {@link LlmProviderConfig}.
     *
     * @return immutable config; never {@code null}
     */
    public LlmProviderConfig build() {
      return new LlmProviderConfig(provider, apiKey, baseUrl, defaultModel, connectTimeout);
    }
  }
}
