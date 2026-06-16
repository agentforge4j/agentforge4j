// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.agent;

import com.agentforge4j.util.Validate;

/**
 * Represents a preference for an LLM provider and optional model.
 * Instances are immutable and validated at construction time.
 */
public record ProviderPreference(
    String provider,
    String model
) {

  /**
   * Creates a ProviderPreference with validation.
   *
   * @param provider the name of the LLM provider
   * @param model the preferred model name; null indicates the provider's default model
   */
  public ProviderPreference {
    Validate.notBlank(provider, "ProviderPreference provider must not be blank");
  }
}
