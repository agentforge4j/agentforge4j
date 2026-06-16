// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker interface for commands that LLMs can return in structured JSON responses. Each command
 * type is a record implementing this interface, dispatched by the runtime to produce side effects.
 *
 * <p>Workflow configuration controls the execution flow; AI/model output provides commands or
 * content but does not own runtime flow control.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateFileCommand.class, name = "CREATE_FILE"),
    @JsonSubTypes.Type(value = UserPromptCommand.class, name = "USER_PROMPT"),
    @JsonSubTypes.Type(value = SetContextCommand.class, name = "SET_CONTEXT"),
    @JsonSubTypes.Type(value = RunCommandCommand.class, name = "RUN_COMMAND"),
    @JsonSubTypes.Type(value = CompleteCommand.class, name = "COMPLETE"),
    @JsonSubTypes.Type(value = ContinueCommand.class, name = "CONTINUE"),
    @JsonSubTypes.Type(value = GenerateQuestionsCommand.class, name = "GENERATE_QUESTIONS"),
    @JsonSubTypes.Type(value = EscalateCommand.class, name = "ESCALATE"),
    @JsonSubTypes.Type(value = ToolInvocationCommand.class, name = "TOOL_INVOCATION")
})
public sealed interface LlmCommand
    permits CreateFileCommand,
    UserPromptCommand,
    SetContextCommand,
    RunCommandCommand,
    CompleteCommand,
    ContinueCommand,
    GenerateQuestionsCommand,
    EscalateCommand,
    ToolInvocationCommand {

}
