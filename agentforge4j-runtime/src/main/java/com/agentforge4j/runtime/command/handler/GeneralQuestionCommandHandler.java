// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.command.handler;

import com.agentforge4j.core.command.GenerateQuestionsCommand;
import com.agentforge4j.core.workflow.artifact.ArtifactDefinition;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.runtime.command.CommandApplicationRequest;
import com.agentforge4j.runtime.command.CommandApplicationResult;
import com.agentforge4j.runtime.command.CommandHandler;
import com.agentforge4j.runtime.event.EventRecorder;
import com.agentforge4j.util.Validate;
import java.time.Clock;
import java.util.UUID;

/**
 * Handles {@link GenerateQuestionsCommand} by materialising a generated {@link com.agentforge4j.core.workflow.artifact.ArtifactDefinition}
 * and pausing for structured answers.
 */
public final class GeneralQuestionCommandHandler implements
    CommandHandler<GenerateQuestionsCommand> {

  private static final System.Logger LOG = System.getLogger(
      GeneralQuestionCommandHandler.class.getName());
  private static final String GENERATED_ID_PREFIX = "generated.";

  private final EventRecorder eventRecorder;
  private final Clock clock;

  /**
   * Creates a handler.
   *
   * @param eventRecorder event sink for generated question flows
   * @param clock         wall clock for pause timestamps
   */
  public GeneralQuestionCommandHandler(EventRecorder eventRecorder, Clock clock) {
    this.eventRecorder = Validate.notNull(eventRecorder, "eventRecorder must not be null");
    this.clock = Validate.notNull(clock, "clock must not be null");
  }

  @Override
  public Class<GenerateQuestionsCommand> getCommandClass() {
    return GenerateQuestionsCommand.class;
  }

  /** {@inheritDoc} */
  @Override
  public CommandApplicationResult apply(GenerateQuestionsCommand cmd,
      CommandApplicationRequest request) {
    LOG.log(System.Logger.Level.DEBUG,
        "Applying generate question with %s".formatted(cmd.questions()));

    String generatedId = GENERATED_ID_PREFIX + UUID.randomUUID();
    request.state().setPendingArtifact(new ArtifactDefinition(generatedId, cmd.questions()));
    request.state().setStatus(WorkflowStatus.AWAITING_INPUT);
    request.state().setLastUpdatedAt(clock.instant());
    eventRecorder.record(request.state().getRunId(), request.state().getCurrentStepId(),
        WorkflowEventType.AWAITING_INPUT,
        "awaiting input for generated artifact %s".formatted(generatedId), request.agentId());
    return CommandApplicationResult.AWAITING_INPUT;
  }
}
