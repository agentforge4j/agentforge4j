// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.tools.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HttpEndpointDefinition.Builder}: required-field enforcement (deferred to
 * {@link HttpEndpointDefinition.Builder#build()}, which delegates to the canonical constructor)
 * and the defaults applied to fields a caller never sets.
 */
class HttpEndpointDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void buildsWithOnlyRequiredFieldsSet() {
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build();

    assertThat(definition.capability()).isEqualTo("items.get");
    assertThat(definition.displayName()).isNull();
    assertThat(definition.description()).isNull();
    // mutating: never set normalizes to true, same as the canonical constructor's null handling.
    assertThat(definition.mutating()).isTrue();
    assertThat(definition.outputSchema()).isNull();
    assertThat(definition.queryArgs()).isEmpty();
    assertThat(definition.staticHeaders()).isEmpty();
    assertThat(definition.secretHeaders()).isEmpty();
    assertThat(definition.timeout()).isNull();
    // maxRetries: never set stays at the same -1 "unset" sentinel the canonical constructor uses;
    // builder callers never need to know or write it themselves.
    assertThat(definition.maxRetries()).isEqualTo(-1);
    assertThat(definition.retryNonIdempotent()).isFalse();
    assertThat(definition.maxResponseBytes()).isNull();
  }

  @Test
  void buildsWithEveryFieldSet() {
    JsonNode inputSchema = objectSchema("id");
    JsonNode outputSchema = objectSchema("result");
    HttpEndpointDefinition definition = HttpEndpointDefinition.builder()
        .withCapability("orders.create")
        .withDisplayName("Create order")
        .withDescription("Creates an order")
        .withMutating(true)
        .withMethod(HttpMethod.POST)
        .withUrlTemplate("https://example.com/orders/{id}")
        .withInputSchema(inputSchema)
        .withOutputSchema(outputSchema)
        .withQueryArgs(Set.of("id"))
        .withBodyMode(BodyMode.JSON)
        .withStaticHeaders(Map.of("Accept", "application/json"))
        .withSecretHeaders(Map.of("Authorization", "auth.token"))
        .withTimeout(Duration.ofSeconds(2))
        .withMaxRetries(3)
        .withRetryNonIdempotent(true)
        .withMaxResponseBytes(2048L)
        .build();

    assertThat(definition.capability()).isEqualTo("orders.create");
    assertThat(definition.displayName()).isEqualTo("Create order");
    assertThat(definition.description()).isEqualTo("Creates an order");
    assertThat(definition.mutating()).isTrue();
    assertThat(definition.method()).isEqualTo(HttpMethod.POST);
    assertThat(definition.urlTemplate()).isEqualTo("https://example.com/orders/{id}");
    assertThat(definition.inputSchema()).isEqualTo(inputSchema);
    assertThat(definition.outputSchema()).isEqualTo(outputSchema);
    assertThat(definition.queryArgs()).containsExactly("id");
    assertThat(definition.bodyMode()).isEqualTo(BodyMode.JSON);
    assertThat(definition.staticHeaders()).containsEntry("Accept", "application/json");
    assertThat(definition.secretHeaders()).containsEntry("Authorization", "auth.token");
    assertThat(definition.timeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(definition.maxRetries()).isEqualTo(3);
    assertThat(definition.retryNonIdempotent()).isTrue();
    assertThat(definition.maxResponseBytes()).isEqualTo(2048L);
  }

  @Test
  void rejectsMissingCapability() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition capability must not be blank");
  }

  @Test
  void rejectsMissingMethod() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition method must not be null");
  }

  @Test
  void rejectsMissingUrlTemplate() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withMethod(HttpMethod.GET)
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition urlTemplate must not be blank");
  }

  @Test
  void rejectsMissingInputSchema() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withBodyMode(BodyMode.NONE)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition inputSchema must not be null");
  }

  @Test
  void rejectsMissingBodyMode() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition bodyMode must not be null");
  }

  @Test
  void rejectsMaxRetriesBelowNegativeOne() {
    assertThatThrownBy(() -> HttpEndpointDefinition.builder()
        .withCapability("items.get")
        .withMethod(HttpMethod.GET)
        .withUrlTemplate("https://example.com/items")
        .withInputSchema(objectSchema())
        .withBodyMode(BodyMode.NONE)
        .withMaxRetries(-2)
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("HttpEndpointDefinition maxRetries must be >= -1");
  }

  private static JsonNode objectSchema(String... properties) {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode props = schema.putObject("properties");
    for (String property : properties) {
      props.putObject(property).put("type", "string");
    }
    return schema;
  }
}
