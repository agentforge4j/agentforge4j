package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.util.Validate;
import java.util.List;

public interface CommandHandler<C extends LlmCommand> {

  Class<C> getCommandClass();
  /**
   * Applies the given command to the workflow state, returning an outcome that
   * indicates whether the command was successfully applied and whether the
   * execution engine should advance to the next sibling step, pause the run, or
   * stop looping.
   */
  CommandApplicationResult apply(C llmCommand, CommandApplicationRequest request);

  static void ensureContextOutputKeyAllowed(String key,
      ContextMapping contextMapping,
      String agentId) {
    List<String> allowed = contextMapping.outputKeys();
    Validate.isTrue(allowed.isEmpty() || allowed.contains(key),
        "Agent '%s' attempted to write context key '%s' but it is not in outputKeys %s"
            .formatted(agentId, key, allowed));
  }
}
