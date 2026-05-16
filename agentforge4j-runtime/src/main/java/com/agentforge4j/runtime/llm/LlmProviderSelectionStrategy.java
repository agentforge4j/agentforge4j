package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.agent.AgentDefinition;
import com.agentforge4j.core.agent.ProviderPreference;
import com.agentforge4j.llm.LlmClientResolver;
import java.util.List;

/**
 * Selects which {@link ProviderPreference} to use for an initial LLM invocation.
 */
public interface LlmProviderSelectionStrategy {

  /**
   * Chooses the provider preference to use for the first LLM call for the given agent.
   */
  ProviderPreference selectInitialProvider(
      AgentDefinition agent,
      List<String> availableProviders);
}
