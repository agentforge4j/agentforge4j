// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.ShippedModelTierDefaults;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration for a single LLM provider, used by
 * {@link AgentForge4jBootstrap.Builder#withLlmProvider(LlmProviderConfig)}.
 *
 * <p>Construct via the per-provider static factory methods, supplying the credential and any
 * provider-specific options the provider requires:
 * <pre>{@code
 * LlmProviderConfig config = LlmProviderConfig.openai()
 *     .defaults()
 *     .apiKey("sk-...")
 *     .option("request.timeout", "PT30S")
 *     .build();
 * }</pre>
 *
 * <p>All fields except {@code provider} are nullable/empty. A null {@code apiKeyReference} means
 * "no credential configured at this layer." A null {@code baseUrl} or {@code defaultModel} does <em>not</em> fall back
 * to a default supplied by the provider module itself — the provider client requires a non-blank base URL and default
 * model at construction time and has no fallback of its own. A null value here means either the per-provider static
 * factory already populated a recommended preset (see {@link #openai()}, {@link #claude()}, etc.) or, for
 * deployment-specific providers such as {@link #azureOpenAi()}, {@link #openAiCompatible()}, and {@link #bedrock()},
 * that the caller must supply one explicitly before {@link ProviderBuilder#build()}. {@code options} carries
 * provider-specific settings (keyed in the canonical dotted form, e.g. {@code request.timeout},
 * {@code auth.header.name}) consumed via {@link com.agentforge4j.llm.LlmProviderOptions}.
 *
 * @param provider        non-blank provider id
 * @param apiKeyReference optional credential reference for this layer (never a raw value in a {@code toString})
 * @param baseUrl         optional base URL; null requires the static factory preset or an explicit caller value
 * @param defaultModel    optional default model; null requires the static factory preset or an explicit caller value
 * @param connectTimeout  optional connect timeout; null requires the static factory preset or an explicit caller value
 * @param options         provider-specific option key/values; never {@code null} (empty when none)
 */
public record LlmProviderConfig(
    String provider,
    LlmSecretReference apiKeyReference,
    String baseUrl,
    String defaultModel,
    Duration connectTimeout,
    Map<String, String> options) {

  /**
   * Validates {@code provider} is non-blank and defensively copies {@code options}.
   */
  public LlmProviderConfig {
    Validate.notBlank(provider, "provider cannot be blank");
    options = options == null ? Map.of() : Map.copyOf(options);
  }

  /**
   * Renders without exposing credentials: {@code apiKeyReference} already redacts itself, and only option
   * <em>keys</em> are shown because an option <em>value</em> may carry a provider secret. Replaces the record's
   * generated {@code toString}, which would print option values verbatim.
   *
   * @return a secret-safe string representation
   */
  @Override
  public String toString() {
    return ("LlmProviderConfig[provider=%s, apiKeyReference=%s, baseUrl=%s, defaultModel=%s, "
        + "connectTimeout=%s, optionKeys=%s]").formatted(
        provider, apiKeyReference, baseUrl, defaultModel, connectTimeout, options.keySet());
  }

  /**
   * The shipped OSS provider→tier→model defaults (design source of truth: {@link ShippedModelTierDefaults}), read once
   * so the per-provider presets below stay in lockstep with it instead of carrying their own copy that can drift.
   */
  private static final Map<String, Map<ModelTier, String>> SHIPPED_MODEL_DEFAULTS = ShippedModelTierDefaults.asMap();

  /**
   * Returns the shipped {@link ModelTier#STANDARD} model for {@code provider}, used as this class's preset default
   * model so it never carries its own copy of a model name that can go stale independently of
   * {@link ShippedModelTierDefaults}.
   *
   * @param provider the provider id; must have a shipped mapping
   *
   * @return the shipped standard-tier model for {@code provider}; never {@code null}
   */
  private static String standardModel(String provider) {
    return SHIPPED_MODEL_DEFAULTS.get(provider).get(ModelTier.STANDARD);
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
        standardModel("openai"),
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
        standardModel("claude"),
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
        standardModel("ollama"),
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
        standardModel("vllm"),
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
        standardModel("gemini"),
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
        standardModel("mistral"),
        Duration.ofSeconds(30));
  }

  /**
   * Returns a builder for Azure OpenAI. {@code baseUrl} and {@code defaultModel} are {@code null} by default — both are
   * deployment-specific and must be set by the caller.
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
   * Returns a builder for any OpenAI-compatible endpoint. {@code baseUrl} and {@code defaultModel} are {@code null} by
   * default — both are deployment-specific and must be set by the caller.
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
   * Returns a builder for AWS Bedrock. {@code baseUrl} and {@code defaultModel} are {@code null} by default — both are
   * deployment-specific and must be set by the caller.
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
   * Builder for {@link LlmProviderConfig}. Obtain via {@link LlmProviderConfig#openai()}, {@link #claude()}, etc.
   */
  public static final class ProviderBuilder {

    private final String provider;
    private LlmSecretReference apiKeyReference;
    private String baseUrl;
    private String defaultModel;
    private Duration connectTimeout;
    private final Map<String, String> options = new LinkedHashMap<>();

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
     * No-op kept purely for call-site readability, e.g. {@code openai().defaults().apiKey(...).build()}. The
     * builder's recommended defaults ({@code baseUrl}, {@code defaultModel}, {@code connectTimeout}) are already set
     * by the static factory method that created it (e.g. {@link LlmProviderConfig#openai()}); calling this method
     * does not populate, refresh, or otherwise change anything.
     *
     * @return {@code this} for chaining, unchanged
     */
    public ProviderBuilder defaults() {
      return this;
    }

    /**
     * Sets the API key.
     *
     * @param apiKey the API key; must not be blank
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder apiKey(String apiKey) {
      Validate.notBlank(apiKey, "apiKey cannot be blank");
      this.apiKeyReference = LlmSecretReference.parse(apiKey);
      return this;
    }

    /**
     * Sets the credential as a reference (literal or {@code env:}/{@code sysprop:} indirect).
     *
     * @param apiKeyReference the credential reference; must not be {@code null}
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder apiKeyReference(LlmSecretReference apiKeyReference) {
      this.apiKeyReference = Validate.notNull(apiKeyReference, "apiKeyReference cannot be null");
      return this;
    }

    /**
     * Sets a provider-specific option (canonical dotted key, e.g. {@code request.timeout}).
     *
     * @param key   the option key; must not be blank
     * @param value the option value; must not be blank
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder option(String key, String value) {
      Validate.notBlank(key, "option key cannot be blank");
      Validate.notBlank(value, "option value cannot be blank");
      this.options.put(key, value);
      return this;
    }

    /**
     * Overrides the provider base URL.
     *
     * @param baseUrl override the provider base URL; must not be blank
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder baseUrl(String baseUrl) {
      Validate.notBlank(baseUrl, "baseUrl cannot be blank");
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * Overrides the provider default model.
     *
     * @param defaultModel override the provider default model; must not be blank
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder defaultModel(String defaultModel) {
      Validate.notBlank(defaultModel, "defaultModel cannot be blank");
      this.defaultModel = defaultModel;
      return this;
    }

    /**
     * Overrides the provider connect timeout.
     *
     * @param connectTimeout override the provider connect timeout; must not be null
     *
     * @return {@code this} for chaining
     */
    public ProviderBuilder connectTimeout(Duration connectTimeout) {
      Validate.notNull(connectTimeout, "connectTimeout cannot be null");
      this.connectTimeout = connectTimeout;
      return this;
    }

    /**
     * Builds the {@link LlmProviderConfig}.
     *
     * @return immutable config; never {@code null}
     */
    public LlmProviderConfig build() {
      return new LlmProviderConfig(provider, apiKeyReference, baseUrl, defaultModel, connectTimeout, options);
    }
  }
}
