package com.agentforge4j.llm.api;

/**
 * Thrown when a declared {@link ModelTier} cannot be resolved to a concrete model string for the
 * selected provider — that is, no mapping exists after shipped defaults and any operator or tenant
 * overrides have been applied. Signals a configuration gap rather than a transient failure; the
 * runtime never silently downgrades to a provider default in this case.
 */
public final class ModelTierResolutionException extends RuntimeException {

  /**
   * Creates a new exception with the given message.
   *
   * @param message a description of the unresolved provider/tier combination
   */
  public ModelTierResolutionException(String message) {
    super(message);
  }
}
