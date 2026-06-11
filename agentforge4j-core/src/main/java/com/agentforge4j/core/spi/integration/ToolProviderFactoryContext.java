package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Framework-supplied collaborators threaded into every
 * {@link IntegrationToolProviderFactory#create} call by the aggregating {@link ToolProviderFactory}.
 * Carrying them here — rather than on each contributor's constructor — keeps contributors
 * {@link java.util.ServiceLoader}-instantiable through their no-arg constructor while still handing
 * them the single bootstrap-owned instances they need.
 *
 * <p>Currently exposes the shared Jackson {@link ObjectMapper} that parses the integration
 * {@code config} payload. It is the stable seam for future shared collaborators (for example a
 * secret resolver) so that adding one grows this context only and does not change the
 * {@link IntegrationToolProviderFactory} SPI.
 *
 * @param objectMapper the single shared Jackson mapper; never {@code null}
 */
public record ToolProviderFactoryContext(ObjectMapper objectMapper) {

  /**
   * Validates that {@code objectMapper} is non-null.
   */
  public ToolProviderFactoryContext {
    Validate.notNull(objectMapper, "objectMapper must not be null");
  }
}
