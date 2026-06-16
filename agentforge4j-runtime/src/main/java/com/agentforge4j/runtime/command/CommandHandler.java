// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command;

import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Strategy for applying one concrete {@link LlmCommand} type to
 * {@link com.agentforge4j.core.workflow.state.WorkflowState} and related context.
 *
 * <p>Implementations record side effects through {@link com.agentforge4j.runtime.event.EventRecorder}
 * where appropriate and return a {@link CommandApplicationResult} that tells the engine whether to
 * keep executing, pause, or finish a loop iteration.
 *
 * @param <C> concrete {@link LlmCommand} subtype this handler accepts
 */
public interface CommandHandler<C extends LlmCommand> {

  /**
   * Command type this handler applies; used as the map key in {@link CommandApplier}.
   *
   * @return non-null command class token
   */
  Class<C> getCommandClass();

  /**
   * Applies state changes for {@code llmCommand} using {@code request}.
   *
   * @param llmCommand parsed command instance
   * @param request    mutable state bundle for the application pass
   * @return control-flow signal after this command
   */
  CommandApplicationResult apply(C llmCommand, CommandApplicationRequest request);

  /**
   * Ensures {@code key} appears in {@link ContextMapping#outputKeys()} when that list is non-empty.
   *
   * @param key             candidate context output key
   * @param contextMapping  active mapping declaring {@code outputKeys}
   * @param agentId         agent id used in error messages
   * @throws IllegalArgumentException if the list is non-empty and excludes {@code key}
   */
  static void ensureContextOutputKeyAllowed(String key,
      ContextMapping contextMapping,
      String agentId) {
    List<String> allowed = contextMapping.outputKeys();
    Validate.isTrue(allowed.isEmpty() || allowed.contains(key),
        "Agent '%s' attempted to write context key '%s' but it is not in outputKeys %s"
            .formatted(agentId, key, allowed));
  }
}
