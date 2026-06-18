// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An opaque reference to an LLM provider credential, carried through neutral provider configuration so a raw credential
 * value never has to travel through the wiring layer.
 *
 * <p>A reference is either:
 * <ul>
 *   <li><b>literal</b> — a direct credential value supplied programmatically (or already resolved by
 *       an embedding framework such as Spring); or</li>
 *   <li><b>indirect</b> — a {@code scheme:key} pointer (for example {@code env:OPENAI_API_KEY}) that
 *       an {@link LlmSecretResolver} turns into an {@link LlmSecret} at the point of use.</li>
 * </ul>
 *
 * <p>Deliberately a final class, not a record: a literal reference holds a credential value, so its
 * {@code toString()} is fully redacted; an indirect reference renders only {@code scheme:key} (a key
 * name is not a secret value).
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public final class LlmSecretReference {

  private static final Pattern REFERENCE = Pattern.compile("^\\$\\{(env|sysprop):(.+)}$");

  @Getter
  private final boolean literal;
  private final String value;
  private final String scheme;
  private final String key;

  /**
   * Creates a literal reference wrapping a direct credential value.
   *
   * @param value the credential value; must not be blank
   *
   * @return a literal reference
   */
  public static LlmSecretReference literal(String value) {
    return new LlmSecretReference(true,
        Validate.notBlank(value, "LlmSecretReference literal value must not be blank"), null, null);
  }

  /**
   * Creates an indirect reference pointing at a credential held in an external source.
   *
   * @param scheme the source scheme, for example {@code env} or {@code sysprop}; must not be blank
   * @param key    the lookup key within that source; must not be blank
   *
   * @return an indirect reference
   */
  public static LlmSecretReference reference(String scheme, String key) {
    return new LlmSecretReference(false, null,
        Validate.notBlank(scheme, "LlmSecretReference scheme must not be blank"),
        Validate.notBlank(key, "LlmSecretReference key must not be blank"));
  }

  /**
   * Parses a raw configuration value into a reference. A value of the form {@code ${env:NAME}} or
   * {@code ${sysprop:name}} becomes an indirect reference; any other value is treated as a literal.
   *
   * @param raw the raw value; must not be blank
   *
   * @return the parsed reference
   */
  public static LlmSecretReference parse(String raw) {
    Validate.notBlank(raw, "LlmSecretReference raw value must not be blank");
    Matcher matcher = REFERENCE.matcher(raw.trim());
    if (matcher.matches()) {
      return reference(matcher.group(1), matcher.group(2));
    }
    return literal(raw);
  }

  /**
   * Returns the literal credential value. Only valid for a literal reference.
   *
   * @return the literal value
   *
   * @throws IllegalStateException if this is an indirect reference
   */
  public String literalValue() {
    if (!literal) {
      throw new IllegalStateException("LlmSecretReference is not a literal reference");
    }
    return value;
  }

  /**
   * Returns the source scheme of an indirect reference.
   *
   * @return the scheme, for example {@code env}
   *
   * @throws IllegalStateException if this is a literal reference
   */
  public String scheme() {
    if (literal) {
      throw new IllegalStateException("LlmSecretReference is a literal reference and has no scheme");
    }
    return scheme;
  }

  /**
   * Returns the lookup key of an indirect reference.
   *
   * @return the key
   *
   * @throws IllegalStateException if this is a literal reference
   */
  public String key() {
    if (literal) {
      throw new IllegalStateException("LlmSecretReference is a literal reference and has no key");
    }
    return key;
  }

  @Override
  public String toString() {
    if (literal) {
      return "LlmSecretReference[REDACTED]";
    }
    return "LlmSecretReference[%s:%s]".formatted(scheme, key);
  }
}
