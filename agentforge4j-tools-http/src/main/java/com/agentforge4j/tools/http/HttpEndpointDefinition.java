// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.tools.http;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Code-defined binding of a logical tool capability to a single HTTP endpoint.
 *
 * <p>Immutable; supplied by the consumer to {@link HttpToolProvider}. Per-field invariants are
 * checked here at construction; cross-field rules that need the provider's view (capability
 * uniqueness, argument disposition, method/body legality, secret-header consistency) are enforced
 * by {@link HttpToolProvider}'s constructor so a bad set of definitions fails fast at wiring time.
 *
 * @param capability         {@code <domain>.<verb_object>} logical id, lowercase snake_case; stable
 *                           and unique within the provider, and used as the {@code remoteToolName}
 * @param displayName        human-readable name, or {@code null}
 * @param description        human-readable description, or {@code null}
 * @param mutating           whether invoking this endpoint may mutate remote state; surfaced as the
 *                           realised {@link com.agentforge4j.core.spi.tool.ToolRiskMetadata} signal.
 *                           {@code null} (absent) normalizes to {@code true} — the highest safe
 *                           risk — so a definition that omits the signal is treated as conservative
 * @param method             HTTP method
 * @param urlTemplate        absolute {@code http}/{@code https} URL with {@code {name}}
 *                           placeholders
 * @param inputSchema        JSON Schema for the arguments; non-null (convention
 *                           {@code additionalProperties:false})
 * @param outputSchema       JSON Schema for the result, or {@code null} if unknown
 * @param queryArgs          argument names routed to the query string
 * @param bodyMode           how the request body is formed from the leftover arguments
 * @param staticHeaders      fixed, non-secret headers (Content-Type, Accept, API-version, ...)
 * @param secretHeaders      header name to secret-reference key; resolved at invoke, never inlined
 * @param timeout            endpoint-level timeout ceiling, or {@code null}; restricts, never
 *                           expands
 * @param maxRetries         endpoint-level retry cap, or {@code -1} when unset; restricts, never
 *                           expands
 * @param retryNonIdempotent opt-in to retry {@code POST}/{@code PATCH}; meaningless on other
 *                           methods
 * @param maxResponseBytes   response body read cap, or {@code null} to use the provider default
 */
public record HttpEndpointDefinition(
    String capability,
    String displayName,
    String description,
    Boolean mutating,
    HttpMethod method,
    String urlTemplate,
    JsonNode inputSchema,
    JsonNode outputSchema,
    Set<String> queryArgs,
    BodyMode bodyMode,
    Map<String, String> staticHeaders,
    Map<String, String> secretHeaders,
    Duration timeout,
    int maxRetries,
    boolean retryNonIdempotent,
    Long maxResponseBytes) {

  /**
   * Validates per-field invariants and defensively copies the collections.
   */
  public HttpEndpointDefinition {
    mutating = mutating == null || mutating;
    Validate.notBlank(capability, "HttpEndpointDefinition capability must not be blank");
    Validate.notNull(method, "HttpEndpointDefinition method must not be null");
    Validate.notBlank(urlTemplate, "HttpEndpointDefinition urlTemplate must not be blank");
    Validate.notNull(inputSchema, "HttpEndpointDefinition inputSchema must not be null");
    Validate.notNull(bodyMode, "HttpEndpointDefinition bodyMode must not be null");
    Validate.isTrue(maxRetries >= -1,
        "HttpEndpointDefinition maxRetries must be >= -1 (use -1 for unset)");
    if (maxResponseBytes != null) {
      Validate.isGreaterThanZero(maxResponseBytes,
          "HttpEndpointDefinition maxResponseBytes must be greater than zero");
    }
    queryArgs = queryArgs != null ? Set.copyOf(queryArgs) : Set.of();
    staticHeaders = staticHeaders != null ? Map.copyOf(staticHeaders) : Map.of();
    secretHeaders = secretHeaders != null ? Map.copyOf(secretHeaders) : Map.of();
  }
}
