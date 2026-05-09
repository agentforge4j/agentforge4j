package com.agentforge4j.integrations;

import java.util.Map;

/**
 * Integration invoked from agent steps. Implementations are obtained from an
 * {@link IntegrationRegistry} using {@link #integrationId()}.
 */
public interface AgentIntegration {

  /**
   * Stable key referenced from workflow or agent configuration for lookup.
   */
  String integrationId();

  /**
   * Runs one integration operation with the supplied arguments.
   *
   * @param operation logical operation name understood by the integration
   * @param payload   arguments for the integration; never {@code null}, may be empty
   * @return a result string defined by the integration for logging or downstream context (not
   * interpreted by the core runtime)
   */
  String execute(String operation, Map<String, Object> payload);
}
