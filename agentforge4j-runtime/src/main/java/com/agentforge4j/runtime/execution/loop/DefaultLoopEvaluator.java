package com.agentforge4j.runtime.execution.loop;

import com.agentforge4j.core.command.CompleteCommand;
import com.agentforge4j.core.command.LlmCommand;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.runtime.execution.ExecutionContext;
import com.agentforge4j.runtime.llm.AgentInvocationResult;
import com.agentforge4j.runtime.llm.AgentInvoker;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link LoopEvaluator} that invokes the evaluator agent and inspects the parsed commands
 * for a {@link CompleteCommand}.
 *
 * <p>The evaluator agent is handed the full shared context (empty
 * {@link ContextMapping}) so it can reason over the current iteration's outputs, and it signals
 * termination by emitting a {@code COMPLETE} command — any other output keeps the loop going.
 */
@RequiredArgsConstructor
public final class DefaultLoopEvaluator implements LoopEvaluator {

  private final AgentInvoker agentInvoker;

  @Override
  public boolean shouldTerminate(String evaluatorAgentId,
      int iteration,
      ExecutionContext executionContext) {
    AgentInvocationResult result = agentInvoker.invoke(
        evaluatorAgentId,
        ContextMapping.none(),
        executionContext.getState(),
        null);
    for (LlmCommand command : result.commands()) {
      if (command instanceof CompleteCommand) {
        return true;
      }
    }
    return false;
  }
}
