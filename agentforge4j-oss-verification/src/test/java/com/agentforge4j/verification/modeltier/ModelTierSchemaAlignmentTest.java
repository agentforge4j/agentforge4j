// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.modeltier;

import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties the shipped JSON schemas back to {@link ModelTier}. The tier enums in
 * {@code agent.schema.json} and {@code workflow.schema.json} are hand-maintained JSON with no
 * compile-time link to the enum, so the two drift silently and in a user-visible direction: a tier
 * the runtime resolves happily is rejected when the same name appears in a bundle.
 *
 * <p>The check belongs to the verification module because that is this repository's boundary for
 * contracts spanning modules that do not depend on one another, and it can be enforced there
 * without any production module taking on a dependency it does not otherwise need. The schema
 * module deliberately depends on neither core nor the LLM API — the schemas describe the tier
 * vocabulary rather than derive it — so handing it the enum purely to test against would undo
 * that separation. Other modules do have both on their classpath ({@code agentforge4j-runtime}
 * and {@code agentforge4j-bootstrap} each declare the schema module and the LLM API), but they
 * own neither side of this contract; the verification module owns cross-module contracts and
 * already hosts the tier-resolution suite.
 */
class ModelTierSchemaAlignmentTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ClasspathSchemaProvider SCHEMAS = new ClasspathSchemaProvider();

  @Test
  void agentSchemaOffersEveryDeclaredTier() throws Exception {
    assertThat(tierEnum(SCHEMAS.agentSchema(), "/properties/modelTier/enum"))
        .containsExactlyInAnyOrderElementsOf(declaredTierNames());
  }

  @Test
  void workflowStepSchemaOffersEveryDeclaredTier() throws Exception {
    assertThat(tierEnum(SCHEMAS.workflowSchema(),
        "/$defs/StepDefinition/properties/modelTier/enum"))
        .containsExactlyInAnyOrderElementsOf(declaredTierNames());
  }

  private static List<String> declaredTierNames() {
    return Arrays.stream(ModelTier.values()).map(Enum::name).toList();
  }

  /**
   * Reads a {@code modelTier} enum and returns its tier names, dropping the {@code null} member —
   * that encodes "no tier declared", which is not a tier.
   */
  private static List<String> tierEnum(String schemaJson, String pointer) throws Exception {
    JsonNode enumNode = MAPPER.readTree(schemaJson).at(pointer);
    assertThat(enumNode.isArray()).as("no modelTier enum at %s", pointer).isTrue();

    List<String> names = new ArrayList<>();
    enumNode.forEach(node -> {
      if (!node.isNull()) {
        names.add(node.asText());
      }
    });
    return names;
  }
}
