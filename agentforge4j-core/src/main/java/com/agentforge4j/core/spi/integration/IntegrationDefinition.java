package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;
import java.util.List;

/**
 * Immutable, transport-agnostic description of one external integration: a single MCP server or a
 * single HTTP-endpoint set, the capabilities it exposes, and whether it is active. The
 * type-specific connection detail is carried as {@code config} JSON text (house convention),
 * interpreted by the downstream {@link ToolProviderFactory} for the matching {@link #type()}.
 *
 * @param id           stable integration id; non-blank (a loader may derive it from the filename)
 * @param displayName  human-readable name; non-blank
 * @param type         integration kind; non-null
 * @param config       type-specific payload as JSON text; non-blank
 * @param capabilities capabilities this integration exposes; non-empty, defensively copied
 * @param active       whether this integration feeds capability resolution
 */
public record IntegrationDefinition(
    String id,
    String displayName,
    IntegrationType type,
    String config,
    List<IntegrationCapability> capabilities,
    boolean active) {

  /**
   * Validates required fields and defensively copies {@code capabilities}.
   */
  public IntegrationDefinition {
    Validate.notBlank(id, "IntegrationDefinition id must not be blank");
    Validate.notBlank(displayName, "IntegrationDefinition displayName must not be blank");
    Validate.notNull(type, "IntegrationDefinition type must not be null");
    Validate.notBlank(config, "IntegrationDefinition config must not be blank");
    capabilities = List.copyOf(
        Validate.notEmpty(capabilities, "IntegrationDefinition capabilities must not be empty"));
  }
}
