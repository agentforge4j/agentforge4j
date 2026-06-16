// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

/**
 * Raised when the LLM output cannot be parsed as a JSON array of
 * {@link com.agentforge4j.core.command.LlmCommand}.
 */
public class LlmCommandParseException extends RuntimeException {

  public LlmCommandParseException(String message) {
    super(message);
  }

  public LlmCommandParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
