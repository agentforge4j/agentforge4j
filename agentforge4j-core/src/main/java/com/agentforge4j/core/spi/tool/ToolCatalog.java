package com.agentforge4j.core.spi.tool;

import java.util.List;

/**
 * Read-only aggregate view of available tools. Cannot invoke and holds no provider references;
 * consumed by {@code AgentInvoker} to advertise capabilities to the LLM.
 */
public interface ToolCatalog {

  /**
   * Lists the descriptors advertised for the given scope.
   *
   * @param scope workflow/run scope
   *
   * @return descriptors advertised for {@code scope}
   */
  List<ToolDescriptor> available(ToolScope scope);
}
