// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable neutral view over a provider's {@code agentforge4j.llm.<providerId>.*} configuration subtree, handed to a
 * {@link LlmClientConfigurationAdapter}. Keys are the canonical property names in kebab-case (for example
 * {@code api-key}, {@code deployment-name}); values are their raw string forms.
 *
 * <p>Per-key values are resolved through a {@link RawConfigurationSource}, so the host configuration decides how a key
 * is looked up (including how alternate key spellings resolve to a canonical key) while this class — and the provider
 * adapters that consume it — stay independent of any particular configuration mechanism. A parse failure throws
 * {@link LlmProviderConfigurationException} naming the full property key ({@code agentforge4j.llm.<providerId>.<key>})
 * — never the value, in case it is a secret.
 *
 * <p>{@link #get(String)} returns a key's value whenever the key is present, even if blank — presence semantics;
 * {@link #require(String)} and the typed accessors treat a blank value as absent.
 *
 * <p>The enumeration views ({@link #isEmpty()}, {@link #keys()}, {@link #asMap()}) reflect the keys discovered from
 * configuration sources that can be enumerated by canonical key. They are best-effort for sources whose canonical keys
 * cannot be reconstructed; per-key accessors remain authoritative for every source. Adapters therefore gate activation
 * on explicit keys (an {@code api-key}, an {@code enabled} flag) rather than on {@link #isEmpty()}.
 */
public final class RawProviderConfiguration {

  private final String providerId;
  private final RawConfigurationSource source;
  private final Set<String> presentKeys;

  /**
   * @param providerId  the provider id whose subtree this represents; must not be blank
   * @param source      the per-key value lookup for this provider's subtree; must not be {@code null}
   * @param presentKeys the keys discovered from enumerable property sources, used for the enumeration views; must not be
   *                    {@code null} (copied immutably)
   */
  public RawProviderConfiguration(String providerId, RawConfigurationSource source, Set<String> presentKeys) {
    this.providerId = Validate.notBlank(providerId, "providerId must not be blank");
    this.source = Validate.notNull(source, "source must not be null");
    Validate.notNull(presentKeys, "presentKeys must not be null");
    this.presentKeys = Set.copyOf(presentKeys);
  }

  /**
   * @return the provider id this subtree belongs to
   */
  public String providerId() {
    return providerId;
  }

  /**
   * @return {@code true} when no properties are discovered under this provider's subtree (best-effort; see the class
   *     documentation)
   */
  public boolean isEmpty() {
    return presentKeys.isEmpty();
  }

  /**
   * @return the discovered property keys (immutable; best-effort, see the class documentation)
   */
  public Set<String> keys() {
    return presentKeys;
  }

  /**
   * Returns a key's value whenever the key is present, including a blank value (presence semantics).
   *
   * @param key the property key (kebab-case, relative to the provider subtree)
   *
   * @return the value if the key is present, otherwise empty
   */
  public Optional<String> get(String key) {
    String value = source.find(key);
    if (value != null) {
      return Optional.of(value);
    }
    // A key present but blank in an enumerable source still reports present, preserving the
    // presence semantics where a blank value still activates a provider.
    return presentKeys.contains(key) ? Optional.of("") : Optional.empty();
  }

  /**
   * Returns the required, non-blank value for {@code key}.
   *
   * @param key the property key
   *
   * @return the non-blank value
   *
   * @throws LlmProviderConfigurationException if the key is absent or blank
   */
  public String require(String key) {
    return nonBlank(key).orElseThrow(() -> new LlmProviderConfigurationException(
        "Provider '%s' requires property '%s' but it is not configured".formatted(providerId, fullKey(key))));
  }

  /**
   * @param key the property key
   *
   * @return the integer value when present and non-blank
   *
   * @throws LlmProviderConfigurationException if present but not a valid integer
   */
  public Optional<Integer> getInt(String key) {
    return nonBlank(key).map(value -> TypedValueParser.parseInt(value.trim(), cause -> intError(key)));
  }

  private LlmProviderConfigurationException intError(String key) {
    return new LlmProviderConfigurationException(
        "Provider '%s' property '%s' must be an integer".formatted(providerId, fullKey(key)));
  }

  /**
   * @param key the property key
   *
   * @return the decimal value when present and non-blank
   *
   * @throws LlmProviderConfigurationException if present but not a valid decimal number
   */
  public Optional<Double> getDouble(String key) {
    return nonBlank(key).map(value -> TypedValueParser.parseDecimal(value.trim(), cause -> decimalError(key)));
  }

  private LlmProviderConfigurationException decimalError(String key) {
    return new LlmProviderConfigurationException(
        "Provider '%s' property '%s' must be a decimal number".formatted(providerId, fullKey(key)));
  }

  /**
   * Parses a duration in either ISO-8601 form (for example {@code PT15S}) or the compact shorthand (an amount plus an
   * optional {@code ns}/{@code us}/{@code ms}/{@code s}/{@code m}/{@code h}/{@code d} unit suffix, defaulting to
   * milliseconds — for example {@code 15s}, {@code 2m}, {@code 500ms}, {@code 5000}).
   *
   * @param key the property key
   *
   * @return the duration value when present and non-blank
   *
   * @throws LlmProviderConfigurationException if present but not a valid ISO-8601 or shorthand duration
   */
  public Optional<Duration> getDuration(String key) {
    return nonBlank(key).map(value -> TypedValueParser.parseDuration(value.trim(), cause -> durationError(key)));
  }

  private LlmProviderConfigurationException durationError(String key) {
    return new LlmProviderConfigurationException(
        "Provider '%s' property '%s' must be a duration (ISO-8601 such as PT15S, or shorthand such as 15s)"
            .formatted(providerId, fullKey(key)));
  }

  /**
   * @param key the property key
   *
   * @return the boolean value when present and non-blank (strictly {@code true}/{@code false}, case-insensitive)
   *
   * @throws LlmProviderConfigurationException if present but not a strict boolean
   */
  public Optional<Boolean> getBoolean(String key) {
    return nonBlank(key).map(value -> TypedValueParser.parseBool(value.trim(), cause -> booleanError(key)));
  }

  private LlmProviderConfigurationException booleanError(String key) {
    return new LlmProviderConfigurationException(
        "Provider '%s' property '%s' must be true or false".formatted(providerId, fullKey(key)));
  }

  /**
   * Returns whether {@code key} is set to the literal {@code "true"} (case-insensitive), tolerating any other
   * value — absent, blank, {@code "false"}, or malformed ({@code "yes"}, {@code "1"}, ...) — as {@code false}
   * rather than throwing. Intended for {@code enabled}-style activation gates, where a malformed value must not
   * fail startup; for every other use prefer {@link #getBoolean(String)}, which fails loudly on a malformed
   * value.
   *
   * @param key the property key
   *
   * @return {@code true} only when the value is the literal {@code "true"} (case-insensitive)
   */
  public boolean isTrue(String key) {
    return get(key).map(String::trim).map(value -> value.equalsIgnoreCase("true")).orElse(false);
  }

  /**
   * @return an immutable map of the discovered keys to their resolved values (best-effort; see the class documentation)
   */
  public Map<String, String> asMap() {
    Map<String, String> resolved = new LinkedHashMap<>();
    for (String key : presentKeys) {
      get(key).ifPresent(value -> resolved.put(key, value));
    }
    return Map.copyOf(resolved);
  }

  private Optional<String> nonBlank(String key) {
    return get(key).filter(value -> !value.isBlank());
  }

  private String fullKey(String key) {
    return "agentforge4j.llm.%s.%s".formatted(providerId, key);
  }
}
