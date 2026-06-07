package com.agentforge4j.llm.bedrock;

import com.agentforge4j.llm.api.LlmInvocationException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Maps AWS SDK service exceptions to the framework's {@link LlmInvocationException}, preserving the
 * HTTP status and original cause. Shared by all Bedrock transports.
 */
final class BedrockServiceExceptions {

  private BedrockServiceExceptions() {
  }

  /**
   * Maps an {@link AwsServiceException} to an {@link LlmInvocationException}.
   *
   * @param e the AWS service exception
   *
   * @return the mapped invocation exception
   */
  static LlmInvocationException map(AwsServiceException e) {
    String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "";
    String msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    int status = e.statusCode();
    String summary = "bedrock HTTP error: %s - %s %s".formatted(status, code, msg).strip();
    LlmInvocationException ex = new LlmInvocationException(summary, status);
    ex.initCause(e);
    return ex;
  }
}
