// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validated, provider-specific configuration transport for the LLM SPI. Carries the provider-specific settings (beyond
 * the common provider id / default model / connect timeout) that a provider factory needs, exposed through typed,
 * validating accessors.
 *
 * <p>The backing key/value store is never exposed raw: callers read through {@link #string},
 * {@link #integer}, {@link #bool}, {@link #duration}, and {@link #secret}, each of which parses and validates. A
 * type-parse failure throws {@link LlmProviderConfigurationException} naming the provider and offending key — never the
 * value, in case it is a secret.
 *
 * <p>This type defines only the mechanism. The option-key vocabulary belongs to each provider module
 * (for example {@code openai-compatible} documents {@code auth.header.name}, {@code auth.header.prefix},
 * {@code responses.path}). Unknown keys are not an error here (forward-compatibility); required-key validation is
 * provider-owned.
 */
public interface LlmProviderOptions {

  /**
   * Returns the value for {@code key} exactly as configured (not trimmed — significant whitespace such as an auth
   * header prefix {@code "Bearer "} is preserved), or empty when the key is absent or blank.
   *
   * @param key the option key
   *
   * @return the value, if present and non-blank
   */
  Optional<String> string(String key);

  /**
   * Returns the integer value for {@code key}.
   *
   * @param key the option key
   *
   * @return the value, if present
   *
   * @throws LlmProviderConfigurationException if the value is not a valid integer
   */
  Optional<Integer> integer(String key);

  /**
   * Returns the boolean value for {@code key} (strictly {@code "true"} or {@code "false"}, case insensitive).
   *
   * @param key the option key
   *
   * @return the value, if present
   *
   * @throws LlmProviderConfigurationException if the value is not a strict boolean
   */
  Optional<Boolean> bool(String key);

  /**
   * Returns the {@link Duration} value for {@code key}, accepting either ISO-8601 (for example {@code PT30S}) or a
   * compact shorthand — an amount plus an optional {@code ns}/{@code us}/{@code ms}/{@code s}/{@code m}/{@code h}/
   * {@code d} unit suffix, defaulting to milliseconds (for example {@code 15s}, {@code 2m}, {@code 500ms}).
   *
   * @param key the option key
   *
   * @return the value, if present
   *
   * @throws LlmProviderConfigurationException if the value is not a valid ISO-8601 or shorthand duration
   */
  Optional<Duration> duration(String key);

  /**
   * Returns the decimal value for {@code key}.
   *
   * @param key the option key
   *
   * @return the value, if present
   *
   * @throws LlmProviderConfigurationException if the value is not a valid decimal number
   */
  Optional<Double> decimal(String key);

  /**
   * Returns the value for {@code key} as a credential reference (see {@link LlmSecretReference#parse}).
   *
   * @param key the option key
   *
   * @return the reference, if present
   */
  Optional<LlmSecretReference> secret(String key);

  /**
   * @return the set of option keys present
   */
  Set<String> keys();

  /**
   * @return the owning provider id, used in error messages
   */
  String providerName();

  /**
   * Returns the required string value for {@code key}.
   *
   * @param key the option key
   *
   * @return the value
   *
   * @throws LlmProviderConfigurationException if the key is absent or blank
   */
  default String requireString(String key) {
    return string(key).orElseThrow(() -> missing(key));
  }

  /**
   * Returns the required integer value for {@code key}.
   *
   * @param key the option key
   *
   * @return the value
   *
   * @throws LlmProviderConfigurationException if the key is absent, or not a valid integer
   */
  default int requireInteger(String key) {
    return integer(key).orElseThrow(() -> missing(key));
  }

  /**
   * Returns the required {@link Duration} value for {@code key}.
   *
   * @param key the option key
   *
   * @return the value
   *
   * @throws LlmProviderConfigurationException if the key is absent, or not a valid ISO-8601 or shorthand duration
   */
  default Duration requireDuration(String key) {
    return duration(key).orElseThrow(() -> missing(key));
  }

  private LlmProviderConfigurationException missing(String key) {
    return new LlmProviderConfigurationException(
        "Provider '%s' requires option '%s' but it is not configured".formatted(providerName(), key));
  }

  /**
   * @return an empty options instance
   */
  static LlmProviderOptions empty() {
    return MapLlmProviderOptions.EMPTY;
  }

  /**
   * Creates options backed by the given key/value map.
   *
   * @param providerName the owning provider id, used only for error messages; must not be {@code null}
   * @param values       the option key/value pairs; must not be {@code null}
   *
   * @return validated options
   */
  static LlmProviderOptions of(String providerName, Map<String, String> values) {
    return new MapLlmProviderOptions(providerName, values);
  }
}
