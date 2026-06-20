// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.llm.api.LlmInvocationException;
import com.agentforge4j.runtime.llm.LlmProviderSelectionStrategy;
import java.util.List;

/**
 * A {@link LlmProviderSelectionStrategy} that selects an agent's first declared
 * {@link ProviderPreference} without consulting the available-provider enumeration.
 *
 * <p>This is sound only because it is paired with {@link FakeLlmClientResolver}, which resolves the
 * single deterministic fake client for <em>every</em> provider id — so whichever preference is
 * chosen resolves to the same fake. It lets the harness drive any agent regardless of which
 * providers it prefers, and frees the testkit from advertising a hardcoded provider catalog: the
 * production {@code FirstAvailableProviderSelectionStrategy} would otherwise reject an agent whose
 * only preference was absent from that catalog.
 */
final class FakeProviderSelectionStrategy implements LlmProviderSelectionStrategy {

  @Override
  public ProviderPreference selectInitialProvider(AgentDefinition agent,
      List<String> availableProviders) {
    return agent.providerPreferences().stream()
        .findFirst()
        .orElseThrow(() -> new LlmInvocationException(
            "Agent '%s' declares no provider preferences".formatted(agent.id())));
  }
}
