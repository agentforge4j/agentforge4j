// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.support;

import com.agentforge4j.core.spi.tool.HealthStatus;
import com.agentforge4j.core.spi.tool.ToolDescriptor;
import com.agentforge4j.core.spi.tool.ToolExecutionOptions;
import com.agentforge4j.core.spi.tool.ToolInvocationContext;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.core.spi.tool.ToolResult;
import com.agentforge4j.core.spi.tool.ToolRiskMetadata;
import com.agentforge4j.core.spi.tool.ToolSource;
import com.agentforge4j.util.Validate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A test {@link ToolProvider} that fails the first invocation and succeeds on every invocation
 * after it. With the default {@link ToolExecutionOptions} (no in-cycle retries) the first execute
 * cycle fails and suspends the run in {@code AWAITING_TOOL_DECISION}; a {@code toolRetry} resume
 * re-invokes the provider, which now succeeds — proving the failed pending invocation is genuinely
 * replayed and the workflow advances on the retried result rather than continuing past the failure.
 */
public final class FailOnceThenSucceedToolProvider implements ToolProvider {

  private static final String OBJECT_SCHEMA = "{\"type\":\"object\"}";

  private final String providerId;
  private final String capability;
  private final String successOutput;
  private final String firstFailureMessage;
  private final AtomicInteger invocations = new AtomicInteger();

  /**
   * Creates the provider.
   *
   * @param providerId          stable provider id; must not be blank
   * @param capability          the capability id; must not be blank
   * @param successOutput       output returned from the second invocation onward; must not be blank
   * @param firstFailureMessage failure message returned from the first invocation; must not be blank
   */
  public FailOnceThenSucceedToolProvider(String providerId, String capability, String successOutput,
      String firstFailureMessage) {
    this.providerId = Validate.notBlank(providerId, "providerId must not be blank");
    this.capability = Validate.notBlank(capability, "capability must not be blank");
    this.successOutput = Validate.notBlank(successOutput, "successOutput must not be blank");
    this.firstFailureMessage =
        Validate.notBlank(firstFailureMessage, "firstFailureMessage must not be blank");
  }

  @Override
  public String providerId() {
    return providerId;
  }

  @Override
  public List<ToolDescriptor> listTools() {
    return List.of(new ToolDescriptor(capability, capability, "Fail-once test tool", OBJECT_SCHEMA,
        null, new ToolSource(providerId, capability), ToolRiskMetadata.conservative()));
  }

  @Override
  public ToolResult invoke(ToolDescriptor descriptor, String arguments, ToolInvocationContext ctx,
      ToolExecutionOptions options) {
    if (invocations.incrementAndGet() == 1) {
      return ToolResult.failure(firstFailureMessage, 1L);
    }
    return ToolResult.success(successOutput, 1L);
  }

  /**
   * Returns how many times {@link #invoke} has been called — exactly two once the run has retried
   * once and completed.
   *
   * @return the invocation count
   */
  public int invocationCount() {
    return invocations.get();
  }

  @Override
  public HealthStatus health() {
    return new HealthStatus(HealthStatus.State.UP, null);
  }
}
