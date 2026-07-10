// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.SetContextCommand;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.UntrustedInputEnvelope;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

/**
 * Handles {@link SetContextCommand} by writing a context value when the key passes output-key policy.
 */
public final class SetContextCommandHandler implements CommandHandler<SetContextCommand> {

  private static final System.Logger LOG = System.getLogger(
      SetContextCommandHandler.class.getName());

  private final EventRecorder eventRecorder;

  /**
   * Creates a handler.
   *
   * @param eventRecorder event sink for context updates
   */
  public SetContextCommandHandler(EventRecorder eventRecorder) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
  }

  @Override
  public Class<SetContextCommand> getCommandClass() {
    return SetContextCommand.class;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@link CommandHandler#ensureContextOutputKeyAllowed(String, com.agentforge4j.core.workflow.context.ContextMapping, String)} rejects the key
   */
  @Override
  public CommandApplicationResult apply(SetContextCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "SetContext command key={0}", cmd.key());
    CommandHandler.ensureContextOutputKeyAllowed(cmd.key(), request.contextMapping(), request.agentId());
    // Reject the reserved render-envelope key at the write path so an injection-influenced SET_CONTEXT
    // fails fast and attributably here, rather than poisoning every later render (the renderer keeps its
    // own collision guard as a backstop).
    Validate.isTrue(!UntrustedInputEnvelope.KEY.equals(cmd.key()),
        "Context key '%s' is reserved for the untrusted-input render envelope and cannot be written"
            .formatted(UntrustedInputEnvelope.KEY));
    // Reject the reserved '__' runtime namespace (ledger merges, compact siblings, and other
    // governance state) so a declared output key can never land there, matching the same guard
    // AssignContextBehaviour applies at construction time.
    Validate.isTrue(!cmd.key().startsWith("__"),
        "Context key '%s' targets the reserved '__' runtime namespace and cannot be written"
            .formatted(cmd.key()));
    // Re-stamp the LLM-supplied value's provenance authoritatively: the value content comes from LLM
    // command JSON, but provenance must never be honoured from that JSON (an LLM could otherwise emit
    // "provenance":"SYSTEM_GENERATED" to launder its content into the trusted partition).
    request.state().putContextValue(cmd.key(), cmd.value().withProvenance(ContextProvenance.LLM_GENERATED));
    request.state().putContextKeyWrittenAtUid(cmd.key(), request.currentStepUid());
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED, "set context key: %s".formatted(cmd.key()),
        request.agentId());
    return CommandApplicationResult.CONTINUE;
  }
}
