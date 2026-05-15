package com.agentforge4j.llm;

import com.agentforge4j.util.Validate;

/**
 * Shared validation for {@link LlmExecutionRequest} across HTTP and non-HTTP clients.
 */
public final class LlmExecutionRequestValidator {

  private LlmExecutionRequestValidator() {}

  public static void validate(LlmExecutionRequest request, String clientProviderName) {
    Validate.notNull(request, "Request must not be null");
    Validate.notBlank(request.providerName(), "Request providerName must be specified");
    Validate.isTrue(
        clientProviderName.equalsIgnoreCase(request.providerName()),
        "Request providerName '%s' does not match client providerName '%s'".formatted(
            request.providerName(),
            clientProviderName
        )
    );
    Validate.notBlank(request.userInput(), "Request user input must be provided");
    Validate.notBlank(request.systemPrompt(), "Request system prompt must be provided");
  }
}
