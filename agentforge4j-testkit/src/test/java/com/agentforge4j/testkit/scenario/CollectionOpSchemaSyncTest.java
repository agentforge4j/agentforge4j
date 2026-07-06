// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Drift guard between the scenario DSL's collection-op vocabulary and its JSON schema: the
 * {@code op} enum in {@code scenario.schema.json} must name exactly the {@link CollectionOp}
 * variants the harness can execute (lower-cased). An op present in the schema but not the sealed
 * set would validate but be unexecutable; a new variant missing from the schema would be
 * unreachable from any scenario file.
 */
class CollectionOpSchemaSyncTest {

  @Test
  void scenarioSchemaOpEnumMatchesCollectionOpVariants() throws Exception {
    List<String> variantNames = Arrays.stream(CollectionOp.class.getPermittedSubclasses())
        .map(variant -> variant.getSimpleName().toLowerCase(Locale.ROOT))
        .toList();

    JsonNode schema;
    try (InputStream in = CollectionOp.class.getResourceAsStream("scenario.schema.json")) {
      assertThat(in).as("scenario.schema.json must ship next to the scenario DSL").isNotNull();
      schema = new ObjectMapper().readTree(in);
    }
    JsonNode opEnum = findCollectionOpEnum(schema);
    assertThat(opEnum.isMissingNode())
        .as("scenario.schema.json must declare the collection op enum")
        .isFalse();

    List<String> schemaOps = new ArrayList<>();
    opEnum.forEach(node -> schemaOps.add(node.asText()));
    assertThat(schemaOps).containsExactlyInAnyOrderElementsOf(variantNames);
  }

  private static JsonNode findCollectionOpEnum(JsonNode schema) {
    // The collection op definition carries an "op" property with an enum; locate it structurally
    // rather than by definition name so a rename keeps the guard intact.
    for (JsonNode def : schema.path("$defs")) {
      JsonNode candidate = def.path("properties").path("op").path("enum");
      if (candidate.isArray()) {
        return candidate;
      }
    }
    return schema.path("$defs").path("collectionOp").path("properties").path("op").path("enum");
  }
}
