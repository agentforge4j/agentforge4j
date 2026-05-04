package com.agentforge4j.core.command;

/**
 * Signal that the current step should proceed to the next iteration or phase.
 */
public record ContinueCommand() implements LlmCommand {

}
