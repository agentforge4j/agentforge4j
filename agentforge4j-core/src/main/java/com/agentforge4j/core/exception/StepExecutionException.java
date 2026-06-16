// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.exception;

/**
 * Raised when a step fails during execution. The exception message includes the step id and run id
 * when available to aid debugging.
 */
public class StepExecutionException extends RuntimeException {

  public StepExecutionException(String message) {
    super(message);
  }

  public StepExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
