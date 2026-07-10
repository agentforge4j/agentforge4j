// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.docs.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reflection-contract regression tests. Runs the emitter against the installed OSS artifacts and
 * asserts the structure of its output. Assertions are structural, not count-based, so they survive a
 * legitimate addition of a provider/behaviour/event but catch a broken contract (a removed
 * {@code @JsonSubTypes}, a leaked {@code fake} provider, a wrongly classified builder method).
 */
class DocsEmitterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir
  static Path outputDir;

  private static JsonNode providers;
  private static JsonNode contracts;
  private static JsonNode bootstrap;

  @BeforeAll
  static void emit() throws Exception {
    DocsEmitter.main(new String[] {outputDir.toString()});
    providers = MAPPER.readTree(outputDir.resolve("providers.json").toFile());
    contracts = MAPPER.readTree(outputDir.resolve("contract-sets.json").toFile());
    bootstrap = MAPPER.readTree(outputDir.resolve("bootstrap-config.json").toFile());
  }

  @Test
  void emitsAllThreeDescriptors() {
    assertTrue(Files.exists(outputDir.resolve("providers.json")));
    assertTrue(Files.exists(outputDir.resolve("contract-sets.json")));
    assertTrue(Files.exists(outputDir.resolve("bootstrap-config.json")));
  }

  @Test
  void providerMatrixExcludesFakeAndCarriesTiers() {
    assertTrue(providers.size() > 0, "expected at least one provider");
    final List<String> modelTiers = new ArrayList<>();
    contracts.get("modelTiers").forEach(node -> modelTiers.add(node.asText()));
    final List<String> names = new ArrayList<>();
    for (final JsonNode provider : providers) {
      names.add(provider.get("name").asText());
      assertTrue(provider.has("requiresApiKey"));
      final JsonNode tiers = provider.get("tiers");
      for (final String tier : modelTiers) {
        assertTrue(tiers.has(tier), "every provider must carry every emitted model tier, missing " + tier);
      }
    }
    assertFalse(names.contains("fake"), "the fake provider must not appear in the user-facing matrix");
    assertTrue(names.contains("openai"), "expected the openai provider to be present");
  }

  @Test
  void contractSetsCarryDiscriminatorsAndTheShippedBehaviours() {
    final List<String> behaviours = jsonTypes(contracts.get("stepBehaviours"));
    // VALIDATE and ASSIGN_CONTEXT are the behaviours added since the original design; their presence
    // proves the table is generated from live source rather than a stale hardcoded list.
    assertTrue(behaviours.contains("VALIDATE"), "stepBehaviours must include VALIDATE");
    assertTrue(behaviours.contains("ASSIGN_CONTEXT"), "stepBehaviours must include ASSIGN_CONTEXT");
    assertTrue(behaviours.contains("AGENT"));

    assertTrue(jsonTypes(contracts.get("llmCommands")).contains("CREATE_FILE"));

    assertTrue(contracts.get("workflowEventTypes").size() > 0);
    assertTrue(contracts.get("workflowStatuses").size() > 0);

    // Containment, not an exact list: a legitimate new tier (e.g. PREMIUM) must not break this test.
    final List<String> tiers = new ArrayList<>();
    contracts.get("modelTiers").forEach(node -> tiers.add(node.asText()));
    assertTrue(tiers.containsAll(List.of("LITE", "STANDARD", "POWERFUL")),
        "modelTiers must include the baseline LITE/STANDARD/POWERFUL tiers");
  }

  @Test
  void bootstrapConfigKeepsValueSettersAndDropsSpiWiring() {
    final List<String> methods = new ArrayList<>();
    for (final JsonNode method : bootstrap) {
      methods.add(method.get("method").asText());
      assertTrue(method.has("paramType"));
    }
    assertTrue(methods.contains("withAgentsDir"), "a value-config setter must be present");
    assertTrue(methods.contains("withMaxNestingDepth"));
    assertFalse(methods.contains("withClock"), "an SPI-wiring setter must be excluded");
    assertFalse(methods.contains("withObjectMapper"), "an SPI-wiring setter must be excluded");
  }

  private static List<String> jsonTypes(JsonNode array) {
    final List<String> result = new ArrayList<>();
    array.forEach(node -> result.add(node.get("jsonType").asText()));
    return result;
  }
}
