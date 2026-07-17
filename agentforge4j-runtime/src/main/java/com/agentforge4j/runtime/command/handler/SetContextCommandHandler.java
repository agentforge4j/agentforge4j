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
   * Prefix of the reserved runtime context namespace. Keys under this namespace back internal
   * bookkeeping (retry-attempt counters, token totals, and similar runtime-owned state) and are
   * written only through direct {@code WorkflowState.putContextValue} calls from runtime code —
   * never through an LLM-emitted command. Rejected unconditionally here regardless of
   * {@link com.agentforge4j.core.workflow.context.ContextMapping#outputKeys()}, since the
   * reserved-namespace guard is absolute, not policy-configurable.
   */
  private static final String RESERVED_NAMESPACE_PREFIX = "__";

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@link CommandHandler#ensureContextOutputKeyAllowed(String, com.agentforge4j.core.workflow.context.ContextMapping, String)} rejects the key,
   *                                   or {@code cmd.key()} names a reserved key
   */
  @Override
  public CommandApplicationResult apply(SetContextCommand cmd, CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG, "SetContext command key={0}", cmd.key());
    // Reject the reserved runtime namespace before the allow-list check: an LLM-emitted SET_CONTEXT
    // must never be able to write __-prefixed keys (retry-attempt counters, token totals, ...) no
    // matter what outputKeys declares, since those keys back runtime-owned governance state.
    Validate.isTrue(!cmd.key().startsWith(RESERVED_NAMESPACE_PREFIX),
        "Context key '%s' is reserved for internal runtime state and cannot be written by a command"
            .formatted(cmd.key()));
    CommandHandler.ensureContextOutputKeyAllowed(cmd.key(), request.contextMapping(), request.agentId());
    // Reject the reserved render-envelope key at the write path so an injection-influenced SET_CONTEXT
    // fails fast and attributably here, rather than poisoning every later render (the renderer keeps its
    // own collision guard as a backstop).
    Validate.isTrue(!UntrustedInputEnvelope.KEY.equals(cmd.key()),
        "Context key '%s' is reserved for the untrusted-input render envelope and cannot be written"
            .formatted(UntrustedInputEnvelope.KEY));
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
