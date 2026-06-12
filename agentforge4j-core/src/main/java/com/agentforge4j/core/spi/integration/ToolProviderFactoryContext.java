package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Framework-supplied collaborators threaded into every {@link IntegrationToolProviderFactory#create} call by the
 * aggregating {@link ToolProviderFactory}. Carrying them here — rather than on each contributor's constructor — keeps
 * contributors {@link java.util.ServiceLoader}-instantiable through their no-arg constructor while still handing them
 * the single bootstrap-owned instances they need.
 *
 * <p>Exposes the shared Jackson {@link ObjectMapper} that parses the integration {@code config}
 * payload and the {@link SecretResolver} that turns secret-reference keys into live values at invoke time. Carrying
 * collaborators here keeps the {@link IntegrationToolProviderFactory} SPI stable: a contributor that needs a new shared
 * collaborator reads it from this context rather than forcing an SPI signature change.
 *
 * @param objectMapper   the single shared Jackson mapper; never {@code null}
 * @param secretResolver the secret-reference resolver for contributors that read secrets (for example HTTP
 *                       {@code secretHeaders}); never {@code null}
 */
public record ToolProviderFactoryContext(ObjectMapper objectMapper, SecretResolver secretResolver) {

  /**
   * Validates that {@code objectMapper} and {@code secretResolver} are non-null.
   */
  public ToolProviderFactoryContext {
    Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(secretResolver, "secretResolver must not be null");
  }
}
