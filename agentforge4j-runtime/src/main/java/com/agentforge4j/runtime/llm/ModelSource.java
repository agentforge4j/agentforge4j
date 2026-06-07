package com.agentforge4j.runtime.llm;

/**
 * How the concrete model for an LLM call was determined, recorded as audit metadata on
 * {@link AgentInvocationResult} and the {@code LLM_CALL_COMPLETED} event.
 */
public enum ModelSource {

  /**
   * A raw model pin on the selected {@code ProviderPreference} was used verbatim. Pins always win
   * over tiers.
   */
  PIN,

  /**
   * A declared capability tier was resolved to a concrete model via the configured
   * {@code ModelTierResolver}.
   */
  TIER,

  /**
   * Neither a pin nor a tier applied; the request carried no model and the provider's own default
   * was used.
   */
  PROVIDER_DEFAULT
}
