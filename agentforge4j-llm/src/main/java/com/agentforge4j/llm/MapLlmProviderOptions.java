// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Map-backed {@link LlmProviderOptions}. The backing map is defensively copied and never exposed; typed accessors parse
 * on read and raise {@link LlmProviderConfigurationException} (naming the provider and key, never the value) on a type
 * mismatch.
 */
final class MapLlmProviderOptions implements LlmProviderOptions {

  static final MapLlmProviderOptions EMPTY = new MapLlmProviderOptions("", Map.of());

  private final String providerName;
  private final Map<String, String> values;

  MapLlmProviderOptions(String providerName, Map<String, String> values) {
    this.providerName = Validate.notNull(providerName, "providerName must not be null");
    this.values = Map.copyOf(Validate.notNull(values, "values must not be null"));
  }

  @Override
  public Optional<String> string(String key) {
    // Preserve the value exactly (not trimmed): some options carry significant whitespace, e.g. an
    // auth header prefix "Bearer ". Only blank values are treated as absent.
    return Optional.ofNullable(values.get(key)).filter(value -> !value.isBlank());
  }

  @Override
  public Optional<Integer> integer(String key) {
    return trimmed(key).map(value -> TypedValueParser.parseInt(value, cause -> invalid(key, "an integer", cause)));
  }

  @Override
  public Optional<Boolean> bool(String key) {
    return trimmed(key)
        .map(value -> TypedValueParser.parseBool(value, cause -> invalid(key, "a boolean (true/false)", cause)));
  }

  @Override
  public Optional<Duration> duration(String key) {
    return trimmed(key).map(value -> TypedValueParser.parseDuration(value,
        cause -> invalid(key, "an ISO-8601 duration (e.g. PT30S) or shorthand (e.g. 15s, 2m, 500ms)", cause)));
  }

  @Override
  public Optional<Double> decimal(String key) {
    return trimmed(key)
        .map(value -> TypedValueParser.parseDecimal(value, cause -> invalid(key, "a decimal number", cause)));
  }

  @Override
  public Optional<LlmSecretReference> secret(String key) {
    return trimmed(key).map(LlmSecretReference::parse);
  }

  private Optional<String> trimmed(String key) {
    return string(key).map(String::trim);
  }

  @Override
  public Set<String> keys() {
    return values.keySet();
  }

  @Override
  public String providerName() {
    return providerName;
  }

  private LlmProviderConfigurationException invalid(String key, String expected, Throwable cause) {
    String message = "Provider '%s' option '%s' is not %s".formatted(providerName, key, expected);
    return cause == null
        ? new LlmProviderConfigurationException(message)
        : new LlmProviderConfigurationException(message, cause);
  }
}
