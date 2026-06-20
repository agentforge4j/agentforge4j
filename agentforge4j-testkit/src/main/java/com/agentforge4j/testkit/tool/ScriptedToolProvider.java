// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.tool;

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

/**
 * A deterministic, in-process {@link ToolProvider} for tests: it advertises a single capability and
 * returns a fixed {@link ToolResult} on every invocation, with no network transport. Use it to drive
 * the runtime tool-governance path (resolve → validate → policy → invoke and the approval / decision
 * suspend-resume branches) without standing up an MCP server.
 */
public final class ScriptedToolProvider implements ToolProvider {

  private static final String OBJECT_SCHEMA = "{\"type\":\"object\"}";

  private final String providerId;
  private final String capability;
  private final ToolResult result;
  private final String inputSchema;

  /**
   * Creates a provider serving one capability with a fixed result and an open object input schema.
   *
   * @param providerId stable provider id; must not be blank
   * @param capability the capability id matched against a {@code TOOL_INVOCATION}; must not be blank
   * @param result     the result returned on every invocation; must not be {@code null}
   */
  public ScriptedToolProvider(String providerId, String capability, ToolResult result) {
    this(providerId, capability, result, OBJECT_SCHEMA);
  }

  /**
   * Creates a provider serving one capability with a fixed result and an explicit input schema (used
   * to drive the argument-validation branch of the governance chokepoint).
   *
   * @param providerId  stable provider id; must not be blank
   * @param capability  the capability id; must not be blank
   * @param result      the result returned on every invocation; must not be {@code null}
   * @param inputSchema the JSON-schema text advertised for the capability; must not be blank
   */
  public ScriptedToolProvider(String providerId, String capability, ToolResult result,
      String inputSchema) {
    this.providerId = Validate.notBlank(providerId, "providerId must not be blank");
    this.capability = Validate.notBlank(capability, "capability must not be blank");
    this.result = Validate.notNull(result, "result must not be null");
    this.inputSchema = Validate.notBlank(inputSchema, "inputSchema must not be blank");
  }

  /**
   * Creates a provider whose capability succeeds with the given output.
   *
   * @param providerId stable provider id
   * @param capability the capability id
   * @param output     the success output payload
   *
   * @return a succeeding provider
   */
  public static ScriptedToolProvider succeeding(String providerId, String capability, String output) {
    return new ScriptedToolProvider(providerId, capability, ToolResult.success(output, 1L));
  }

  /**
   * Creates a succeeding provider that advertises {@code requiredProperty} as a required top-level
   * input field, so a {@code TOOL_INVOCATION} whose arguments omit it fails argument validation and
   * drives the run to {@code AWAITING_TOOL_DECISION} before the provider is ever invoked.
   *
   * @param providerId       stable provider id
   * @param capability       the capability id
   * @param output           the success output payload (returned only if validation passed)
   * @param requiredProperty the input property the capability requires
   *
   * @return a succeeding provider with a required-field input schema
   */
  public static ScriptedToolProvider requiring(String providerId, String capability, String output,
      String requiredProperty) {
    String schema = "{\"type\":\"object\",\"required\":[\"%s\"]}"
        .formatted(Validate.notBlank(requiredProperty, "requiredProperty must not be blank"));
    return new ScriptedToolProvider(providerId, capability, ToolResult.success(output, 1L), schema);
  }

  /**
   * Creates a provider whose capability fails with the given message (drives the decision-on-failure
   * path to {@code AWAITING_TOOL_DECISION}).
   *
   * @param providerId   stable provider id
   * @param capability   the capability id
   * @param errorMessage the failure message
   *
   * @return a failing provider
   */
  public static ScriptedToolProvider failing(String providerId, String capability,
      String errorMessage) {
    return new ScriptedToolProvider(providerId, capability, ToolResult.failure(errorMessage, 1L));
  }

  @Override
  public String providerId() {
    return providerId;
  }

  @Override
  public List<ToolDescriptor> listTools() {
    return List.of(new ToolDescriptor(capability, capability, "Scripted test tool", inputSchema,
        null, new ToolSource(providerId, capability), ToolRiskMetadata.conservative()));
  }

  @Override
  public ToolResult invoke(ToolDescriptor descriptor, String arguments, ToolInvocationContext ctx,
      ToolExecutionOptions options) {
    return result;
  }

  @Override
  public HealthStatus health() {
    return new HealthStatus(HealthStatus.State.UP, null);
  }
}
