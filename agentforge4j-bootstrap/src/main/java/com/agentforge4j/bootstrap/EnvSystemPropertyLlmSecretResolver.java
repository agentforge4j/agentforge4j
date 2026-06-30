// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import com.agentforge4j.llm.LlmProviderConfigurationException;
import com.agentforge4j.llm.LlmSecret;
import com.agentforge4j.llm.LlmSecretReference;
import com.agentforge4j.llm.LlmSecretResolver;
import com.agentforge4j.util.Validate;

/**
 * Default {@link LlmSecretResolver} for the embeddable bootstrap path. A literal reference passes its value through; an
 * indirect reference is resolved against the process environment ({@code env:KEY} → {@link System#getenv(String)}) or
 * system properties ({@code sysprop:KEY} → {@link System#getProperty(String)}).
 *
 * <p>An explicit reference names the exact variable, so it is unaffected by the
 * {@code AGENTFORGE4J_*} auto-discovery key normalization. Resolution is fail-fast and secret-safe: an unknown scheme
 * or unresolved reference throws {@link LlmProviderConfigurationException} naming only the {@code scheme:key}, never a
 * value.
 *
 * <p>Public so framework layers (for example the Spring starter) can reuse it as the default
 * resolver: an already-resolved property value is wrapped as a literal reference and passed through.
 */
public final class EnvSystemPropertyLlmSecretResolver implements LlmSecretResolver {

  /**
   * Creates the default environment / system-property resolver.
   */
  public EnvSystemPropertyLlmSecretResolver() {
  }

  @Override
  public LlmSecret resolve(LlmSecretReference reference) {
    Validate.notNull(reference, "reference must not be null");
    if (reference.isLiteral()) {
      return new LlmSecret(reference.literalValue());
    }
    String scheme = reference.scheme();
    String key = reference.key();
    String value = switch (scheme) {
      case "env" -> System.getenv(key);
      case "sysprop" -> System.getProperty(key);
      default -> throw new LlmProviderConfigurationException(
          "Unknown secret reference scheme '%s' for key '%s'".formatted(scheme, key));
    };
    Validate.notBlank(value, () -> new LlmProviderConfigurationException(
        "Credential reference '%s:%s' could not be resolved".formatted(scheme, key)));
    return new LlmSecret(value);
  }
}
