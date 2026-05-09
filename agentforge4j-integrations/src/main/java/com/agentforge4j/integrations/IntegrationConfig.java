package com.agentforge4j.integrations;

import java.util.List;

/**
 * Per-integration settings consulted by {@link IntegrationRegistry} for enablement and operation
 * allow lists.
 */
public interface IntegrationConfig {

  boolean enabled();

  /**
   * Validates this configuration; implementors throw when state is invalid for use.
   */
  void validate();

  /**
   * Logical operation names permitted for this integration when it is enabled; may be empty.
   *
   * @return allowed operation names, or an empty list when none are permitted
   */
  List<String> allowedOperations();
}
