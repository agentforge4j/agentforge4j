// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.workflow.step.behaviour.StepBehaviour;
import com.agentforge4j.schema.ClasspathSchemaProvider;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guard against model/schema drift: every behaviour type Jackson can deserialize must be
 * expressible in {@code workflow.schema.json}, and vice versa. A type present on one side only is
 * either a schema-accepted config the runtime cannot execute or a runtime capability no schema-valid
 * config can reach — both are release defects caught here rather than at a user's first workflow.
 */
class BehaviourSchemaReconciliationTest {

  @Test
  void jacksonBehaviourSubtypesMatchWorkflowSchemaEnum() throws Exception {
    JsonSubTypes subTypes = StepBehaviour.class.getAnnotation(JsonSubTypes.class);
    assertThat(subTypes).as("StepBehaviour must declare @JsonSubTypes").isNotNull();
    Set<String> jacksonNames = Arrays.stream(subTypes.value())
        .map(JsonSubTypes.Type::name)
        .collect(Collectors.toSet());

    JsonNode schema = new ObjectMapper()
        .readTree(new ClasspathSchemaProvider().workflowSchema());
    JsonNode behaviourDef = schema.path("$defs").path("StepBehaviour");
    assertThat(behaviourDef.isMissingNode())
        .as("workflow.schema.json must define $defs.StepBehaviour")
        .isFalse();

    List<String> enumNames = new ArrayList<>();
    behaviourDef.path("properties").path("type").path("enum")
        .forEach(node -> enumNames.add(node.asText()));

    assertThat(enumNames)
        .as("workflow.schema.json behaviour enum must match StepBehaviour @JsonSubTypes names")
        .containsExactlyInAnyOrderElementsOf(jacksonNames);

    // Every enum entry must also have a oneOf variant, so the discriminator and the payload
    // definitions cannot drift apart within the schema itself.
    assertThat(behaviourDef.path("oneOf").size())
        .as("each behaviour enum entry needs a oneOf payload definition")
        .isEqualTo(enumNames.size());
  }
}
