// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.tool;

import com.agentforge4j.util.Validate;

/**
 * Logical, transport-agnostic description of a tool the runtime can invoke.
 *
 * @param capability   logical id, for example {@code "github.create_pull_request"}
 *                     ({@code <domain>.<verb_object>}, lowercase snake_case)
 * @param displayName  human-readable name, or {@code null}
 * @param description  human-readable description, or {@code null}
 * @param inputSchema  JSON Schema for the arguments as JSON text, or {@code null}
 * @param outputSchema JSON Schema for the result as JSON text, or {@code null} if unknown
 * @param source       physical source that fulfils this capability
 * @param riskMetadata realised risk signal for this tool; non-null (use
 *                     {@link ToolRiskMetadata#conservative()} when no trustworthy signal exists)
 */
public record ToolDescriptor(
    String capability,
    String displayName,
    String description,
    String inputSchema,
    String outputSchema,
    ToolSource source,
    ToolRiskMetadata riskMetadata) {

  /**
   * Validates that {@code capability} is non-blank and {@code source} and {@code riskMetadata} are
   * non-null.
   */
  public ToolDescriptor {
    Validate.notBlank(capability, "ToolDescriptor capability must not be blank");
    Validate.notNull(source, "ToolDescriptor source must not be null");
    Validate.notNull(riskMetadata, "ToolDescriptor riskMetadata must not be null");
  }
}
