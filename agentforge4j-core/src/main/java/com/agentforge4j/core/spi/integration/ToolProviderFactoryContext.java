// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.spi.integration;

import com.agentforge4j.util.Validate;
import com.agentforge4j.util.net.OutboundEgressGuard;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Framework-supplied collaborators threaded into every {@link IntegrationToolProviderFactory#create} call by the
 * aggregating {@link ToolProviderFactory}. Carrying them here — rather than on each contributor's constructor — keeps
 * contributors {@link java.util.ServiceLoader}-instantiable through their no-arg constructor while still handing them
 * the single bootstrap-owned instances they need.
 *
 * <p>Exposes the shared Jackson {@link ObjectMapper} that parses the integration {@code config}
 * payload, the {@link SecretResolver} that turns secret-reference keys into live values at invoke time, and the
 * {@link OutboundEgressGuard} that contributors building remote-network providers use to classify SSRF targets. Carrying
 * collaborators here keeps the {@link IntegrationToolProviderFactory} SPI stable: a contributor that needs a new shared
 * collaborator reads it from this context rather than forcing an SPI signature change.
 *
 * @param objectMapper   the single shared Jackson mapper; never {@code null}
 * @param secretResolver the secret-reference resolver for contributors that read secrets (for example HTTP
 *                       {@code secretHeaders}); never {@code null}
 * @param egressGuard    the outbound-egress guard (a neutral classifier) for contributors that build remote-network
 *                       providers; never {@code null}
 */
public record ToolProviderFactoryContext(ObjectMapper objectMapper, SecretResolver secretResolver,
                                         OutboundEgressGuard egressGuard) {

  /**
   * Validates that {@code objectMapper}, {@code secretResolver}, and {@code egressGuard} are non-null.
   */
  public ToolProviderFactoryContext {
    Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(secretResolver, "secretResolver must not be null");
    Validate.notNull(egressGuard, "egressGuard must not be null");
  }
}
