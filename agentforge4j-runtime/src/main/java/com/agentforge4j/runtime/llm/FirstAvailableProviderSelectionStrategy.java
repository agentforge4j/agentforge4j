// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.llm.api.LlmInvocationException;
import java.util.List;
import java.util.Locale;

/**
 * Selects the first provider preference in agent order that is available in the resolver.
 */
public final class FirstAvailableProviderSelectionStrategy implements LlmProviderSelectionStrategy {

  @Override
  public ProviderPreference selectInitialProvider(
      AgentDefinition agent,
      List<String> availableProviders) {
    return agent.providerPreferences().stream()
        .filter(p -> availableProviders.contains(p.provider().toLowerCase(Locale.ROOT)))
        .findFirst()
        .orElseThrow(() -> new LlmInvocationException(
            "Agent '%s' has no available provider preferences".formatted(agent.id())));
  }
}
