package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.CallEndpointCommand;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.integrations.AgentIntegration;
import com.agentforge4j.integrations.IntegrationRegistry;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;

public final class CallEndpointCommandHandler implements CommandHandler<CallEndpointCommand> {

  private static final System.Logger LOG = System.getLogger(
      CallEndpointCommandHandler.class.getName());

  private final EventRecorder eventRecorder;
  private final IntegrationRegistry integrationRegistry;

  public CallEndpointCommandHandler(EventRecorder eventRecorder,
      IntegrationRegistry integrationRegistry) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.integrationRegistry = Validate.notNull(integrationRegistry,
        "integrationRegistry must not be null");
  }

  @Override
  public Class<CallEndpointCommand> getCommandClass() {
    return CallEndpointCommand.class;
  }

  @Override
  public CommandApplicationResult apply(CallEndpointCommand cmd,
      CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG,
        "Applying CallEndpointCommand with integrationId: %s, operation: %s",
        cmd.integrationId(), cmd.operation());

    validateCommand(cmd);

    AgentIntegration integration = resolveIntegration(cmd);
    try {
      String response = integration.execute(cmd.operation(), cmd.payload());
      if (cmd.contextKey() != null && response != null) {
        CommandHandler.ensureContextOutputKeyAllowed(cmd.contextKey(), request.contextMapping(),
            request.agentId());
        request.state().putContextValue(cmd.contextKey(), new StringContextValue(response));
        request.state().putContextKeyWrittenAtUid(cmd.contextKey(), request.currentStepUid());
        recordEvent(request, "integration '%s' operation '%s' stored in context key '%s'"
            .formatted(cmd.integrationId(), cmd.operation(),
                cmd.contextKey()));
      } else {
        recordEvent(request, "integration '%s' operation '%s' completed"
            .formatted(cmd.integrationId(), cmd.operation()));
      }
      return CommandApplicationResult.CONTINUE;
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.ERROR,
          "Command application failed commandType={0}, stepId={1}, message={2}",
          cmd.getClass().getSimpleName(), request.state().getCurrentStepId(), e.getMessage());
      throw e;
    }
  }

  private AgentIntegration resolveIntegration(CallEndpointCommand cmd) {
    return integrationRegistry
        .resolve(cmd.integrationId())
        .orElseThrow(() -> new IllegalStateException(
            "Integration '%s' passed permission checks but could not be resolved"
                .formatted(cmd.integrationId())));
  }

  private void recordEvent(CommandApplicationRequest request, String cmd) {
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.CONTEXT_UPDATED,
        cmd,
        request.agentId());
  }

  private void validateCommand(CallEndpointCommand cmd) {
    Validate.isTrue(integrationRegistry.isEnabled(cmd.integrationId()),
        "Integration '%s' is not configured or not enabled"
            .formatted(cmd.integrationId()));
    Validate.isTrue(
        integrationRegistry.isOperationAllowed(cmd.integrationId(), cmd.operation()),
        "Operation '%s' is not permitted for integration '%s'"
            .formatted(cmd.operation(), cmd.integrationId()));
  }
}
