// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.examples.wlbranch;

import com.agentforge4j.util.Validate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Resolves whether this example runs against the deterministic fake LLM or a real provider, and (for the
 * real path) the provider name and API key. Each value is taken from, highest precedence first: a JVM
 * system property, an environment variable, then the bundled {@code example.properties}. This mirrors the
 * framework's own {@code programmatic > system-property > environment-variable} ordering.
 *
 * <p>The fake/real toggle: an explicitly set {@code agentforge4j.example.fake-llm} (system property,
 * the {@code AGENTFORGE4J_EXAMPLE_FAKE_LLM} environment variable, or the properties file) wins; otherwise
 * the fake is used if and only if no non-blank API key is configured. An explicit toggle must be
 * {@code true} or {@code false} (case-insensitive, surrounding whitespace ignored); any other value is
 * rejected with an {@link IllegalArgumentException} rather than silently treated as {@code false}. An
 * explicit {@code false} with no non-blank API key configured also fails fast with an
 * {@link IllegalArgumentException}, rather than deferring to an unclear failure once the real provider is
 * assembled.
 *
 * <p>This is a per-example copy by design — the examples deliberately share no helper module, so each one
 * is a complete, copy-paste-ready template. It reads its own keys directly rather than through the
 * framework's internal configuration reader.
 */
final class ExampleLlmConfig {

  private static final String RESOURCE = "/example.properties";
  private static final String PROVIDER_PROP = "agentforge4j.example.llm.provider";
  private static final String PROVIDER_ENV = "AGENTFORGE4J_EXAMPLE_LLM_PROVIDER";
  private static final String API_KEY_PROP = "agentforge4j.example.llm.api-key";
  private static final String API_KEY_ENV = "AGENTFORGE4J_EXAMPLE_LLM_API_KEY";
  private static final String FAKE_LLM_PROP = "agentforge4j.example.fake-llm";
  private static final String FAKE_LLM_ENV = "AGENTFORGE4J_EXAMPLE_FAKE_LLM";

  private final boolean fakeLlm;
  private final String provider;
  private final String apiKey;

  private ExampleLlmConfig(boolean fakeLlm, String provider, String apiKey) {
    this.fakeLlm = fakeLlm;
    this.provider = provider;
    this.apiKey = apiKey;
  }

  /**
   * Loads the configuration, applying the precedence and toggle described on the type.
   *
   * @return the resolved configuration; never {@code null}
   * @throws IllegalArgumentException if the explicit toggle is neither {@code true} nor {@code false},
   *         or real mode resolves with no non-blank API key configured
   */
  static ExampleLlmConfig load() {
    Properties properties = loadProperties();
    String provider = resolveOrEmpty(PROVIDER_PROP, PROVIDER_ENV, properties);
    String apiKey = resolveOrEmpty(API_KEY_PROP, API_KEY_ENV, properties);
    String explicitFake = resolve(FAKE_LLM_PROP, FAKE_LLM_ENV, properties);
    boolean fakeLlm = explicitFake != null ? parseFakeToggle(explicitFake) : isBlank(apiKey);
    if (!fakeLlm) {
      Validate.notBlank(apiKey,
          "%s (or %s) must be set when %s is explicitly \"false\"."
              .formatted(API_KEY_PROP, API_KEY_ENV, FAKE_LLM_PROP));
    }
    return new ExampleLlmConfig(fakeLlm, provider, apiKey);
  }

  boolean fakeLlm() {
    return fakeLlm;
  }

  String provider() {
    return provider;
  }

  String apiKey() {
    return apiKey;
  }

  private static String resolveOrEmpty(String propKey, String envKey, Properties properties) {
    String value = resolve(propKey, envKey, properties);
    return value == null ? "" : value;
  }

  private static String resolve(String propKey, String envKey, Properties properties) {
    String fromProperty = System.getProperty(propKey);
    if (!isBlank(fromProperty)) {
      return fromProperty;
    }
    String fromEnv = System.getenv(envKey);
    if (!isBlank(fromEnv)) {
      return fromEnv;
    }
    String fromFile = properties.getProperty(propKey);
    if (!isBlank(fromFile)) {
      return fromFile;
    }
    return null;
  }

  private static Properties loadProperties() {
    Properties properties = new Properties();
    try (InputStream in = ExampleLlmConfig.class.getResourceAsStream(RESOURCE)) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load %s".formatted(RESOURCE), exception);
    }
    return properties;
  }

  private static boolean parseFakeToggle(String value) {
    String normalised = value.trim();
    boolean isTrueValue = normalised.equalsIgnoreCase("true");
    boolean isFalseValue = normalised.equalsIgnoreCase("false");
    Validate.isTrue(isTrueValue || isFalseValue,
        "Invalid value \"%s\" for %s (or %s): expected \"true\" or \"false\" (case-insensitive)."
            .formatted(value, FAKE_LLM_PROP, FAKE_LLM_ENV));
    return isTrueValue;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
