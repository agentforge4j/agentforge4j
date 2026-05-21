package com.agentforge4j.runtime.llm;

import com.agentforge4j.llm.api.TokenUsageReport;

/**
 * Shared constants for {@link AgentInvocationResult} test doubles.
 */
public final class AgentInvocationResultTestFixtures {

  public static final String TEST_MODEL = "test-model";

  public static final TokenUsageReport TEST_TOKEN_USAGE =
      new TokenUsageReport(100, 50, null, null);

  private AgentInvocationResultTestFixtures() {
  }
}
