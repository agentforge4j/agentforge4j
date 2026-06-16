// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

/**
 * Signal that the current step or loop iteration is complete.
 *
 * <p>Used as the termination signal when {@code LoopTerminationStrategy.AGENT_SIGNAL}
 * is in effect. The optional {@code summary} is recorded on the event log.
 */
public record CompleteCommand(String summary) implements LlmCommand {

}
