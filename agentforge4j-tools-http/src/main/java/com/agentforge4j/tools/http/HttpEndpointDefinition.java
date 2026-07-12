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
 *                           Never {@code null} on a constructed instance: {@code null} is accepted
 *                           only as the wire/positional-constructor spelling of "absent" (an
 *                           HTTP_TOOL integration JSON config that omits the field, or a direct
 *                           positional-constructor caller passing {@code null}) and is normalized to
 *                           {@code true} — the highest safe risk — before the object exists, so a
 *                           definition that omits the signal is treated as conservative. Java callers
 *                           should prefer {@link Builder#withMutating(boolean)}, which takes a
 *                           non-null primitive and cannot express "absent" at all
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
 * @param maxRetries         endpoint-level retry cap, or {@code null} when unset; restricts, never
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
    Integer maxRetries,
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
    Validate.isTrue(maxRetries == null || maxRetries >= 0,
        "HttpEndpointDefinition maxRetries must be null (unset) or >= 0");
    if (maxResponseBytes != null) {
      Validate.isGreaterThanZero(maxResponseBytes,
          "HttpEndpointDefinition maxResponseBytes must be greater than zero");
    }
    queryArgs = queryArgs != null ? Set.copyOf(queryArgs) : Set.of();
    staticHeaders = staticHeaders != null ? Map.copyOf(staticHeaders) : Map.of();
    secretHeaders = secretHeaders != null ? Map.copyOf(secretHeaders) : Map.of();
  }

  /**
   * Returns a new {@link Builder} for assembling an {@link HttpEndpointDefinition} without 16
   * positional constructor arguments. The required fields ({@code capability}, {@code method},
   * {@code urlTemplate}, {@code inputSchema}, {@code bodyMode}) are validated when
   * {@link Builder#build()} delegates to the canonical constructor; every other field may be left
   * unset and takes the same default the canonical constructor already applies to a {@code null}
   * argument.
   *
   * @return new builder; never {@code null}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for {@link HttpEndpointDefinition}. Each {@code with*} method sets exactly one
   * field, so adjacent same-typed fields (the several {@code String} and {@code Map} components)
   * can no longer transpose silently at a call site the way they can with the 16-argument
   * positional constructor. Validation is deferred to {@link #build()}, which delegates to the
   * canonical constructor.
   */
  public static final class Builder {

    private String capability;
    private String displayName;
    private String description;
    private boolean mutating = true;
    private HttpMethod method;
    private String urlTemplate;
    private JsonNode inputSchema;
    private JsonNode outputSchema;
    private Set<String> queryArgs;
    private BodyMode bodyMode;
    private Map<String, String> staticHeaders;
    private Map<String, String> secretHeaders;
    private Duration timeout;
    private Integer maxRetries;
    private boolean retryNonIdempotent;
    private Long maxResponseBytes;

    private Builder() {
      // obtain via HttpEndpointDefinition.builder()
    }

    /**
     * Sets the logical capability id.
     *
     * @param capability {@code <domain>.<verb_object>} id, lowercase snake_case; required
     *
     * @return this builder
     */
    public Builder withCapability(String capability) {
      this.capability = capability;
      return this;
    }

    /**
     * Sets the human-readable display name.
     *
     * @param displayName display name, or {@code null}
     *
     * @return this builder
     */
    public Builder withDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    /**
     * Sets the human-readable description.
     *
     * @param description description, or {@code null}
     *
     * @return this builder
     */
    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets whether invoking this endpoint may mutate remote state. Never calling this method leaves
     * {@code mutating} at its default, {@code true} — the highest safe risk — matching the wire
     * default an HTTP_TOOL integration config applies when it omits the field.
     *
     * @param mutating mutating signal; required, no "unset" value exists at this API surface
     *
     * @return this builder
     */
    public Builder withMutating(boolean mutating) {
      this.mutating = mutating;
      return this;
    }

    /**
     * Sets the HTTP method.
     *
     * @param method HTTP method; required
     *
     * @return this builder
     */
    public Builder withMethod(HttpMethod method) {
      this.method = method;
      return this;
    }

    /**
     * Sets the absolute URL template.
     *
     * @param urlTemplate absolute {@code http}/{@code https} URL with {@code {name}} placeholders;
     *                    required
     *
     * @return this builder
     */
    public Builder withUrlTemplate(String urlTemplate) {
      this.urlTemplate = urlTemplate;
      return this;
    }

    /**
     * Sets the JSON Schema for the arguments.
     *
     * @param inputSchema input JSON Schema; required
     *
     * @return this builder
     */
    public Builder withInputSchema(JsonNode inputSchema) {
      this.inputSchema = inputSchema;
      return this;
    }

    /**
     * Sets the JSON Schema for the result.
     *
     * @param outputSchema output JSON Schema, or {@code null} if unknown
     *
     * @return this builder
     */
    public Builder withOutputSchema(JsonNode outputSchema) {
      this.outputSchema = outputSchema;
      return this;
    }

    /**
     * Sets the argument names routed to the query string.
     *
     * @param queryArgs query argument names; {@code null} (or never calling this method) becomes
     *                  empty
     *
     * @return this builder
     */
    public Builder withQueryArgs(Set<String> queryArgs) {
      this.queryArgs = queryArgs;
      return this;
    }

    /**
     * Sets how the request body is formed from the leftover arguments.
     *
     * @param bodyMode body mode; required
     *
     * @return this builder
     */
    public Builder withBodyMode(BodyMode bodyMode) {
      this.bodyMode = bodyMode;
      return this;
    }

    /**
     * Sets the fixed, non-secret headers.
     *
     * @param staticHeaders static headers; {@code null} (or never calling this method) becomes
     *                      empty
     *
     * @return this builder
     */
    public Builder withStaticHeaders(Map<String, String> staticHeaders) {
      this.staticHeaders = staticHeaders;
      return this;
    }

    /**
     * Sets the header name to secret-reference key mapping.
     *
     * @param secretHeaders secret headers; {@code null} (or never calling this method) becomes
     *                      empty
     *
     * @return this builder
     */
    public Builder withSecretHeaders(Map<String, String> secretHeaders) {
      this.secretHeaders = secretHeaders;
      return this;
    }

    /**
     * Sets the endpoint-level timeout ceiling.
     *
     * @param timeout timeout ceiling, or {@code null}; restricts, never expands
     *
     * @return this builder
     */
    public Builder withTimeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Sets the endpoint-level retry cap. Never calling this method leaves {@code maxRetries}
     * {@code null} (unset).
     *
     * @param maxRetries retry cap, or {@code null} for unset; must be {@code >= 0} when set,
     *                   restricts, never expands
     *
     * @return this builder
     */
    public Builder withMaxRetries(Integer maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * Sets whether to opt in to retrying {@code POST}/{@code PATCH}.
     *
     * @param retryNonIdempotent opt-in flag; meaningless on other methods
     *
     * @return this builder
     */
    public Builder withRetryNonIdempotent(boolean retryNonIdempotent) {
      this.retryNonIdempotent = retryNonIdempotent;
      return this;
    }

    /**
     * Sets the response body read cap.
     *
     * @param maxResponseBytes read cap, or {@code null} to use the provider default
     *
     * @return this builder
     */
    public Builder withMaxResponseBytes(Long maxResponseBytes) {
      this.maxResponseBytes = maxResponseBytes;
      return this;
    }

    /**
     * Builds the validated {@link HttpEndpointDefinition}.
     *
     * @return immutable endpoint definition; never {@code null}
     * @throws IllegalArgumentException if a required field was not set, or a set field is invalid
     */
    public HttpEndpointDefinition build() {
      return new HttpEndpointDefinition(capability, displayName, description, mutating, method,
          urlTemplate, inputSchema, outputSchema, queryArgs, bodyMode, staticHeaders,
          secretHeaders, timeout, maxRetries, retryNonIdempotent, maxResponseBytes);
    }
  }
}
