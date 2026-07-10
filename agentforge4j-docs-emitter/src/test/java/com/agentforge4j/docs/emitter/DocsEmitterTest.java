// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.docs.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  /**
   * Drift guard: the emitted provider set must equal the reactor's registered provider modules
   * (llm-* minus the api/fake modules, which are not providers). The emitter pom's dependency list
   * is otherwise a hardcoded classpath — a provider module added to the reactor without a matching
   * emitter dependency would silently vanish from the "generated from source" provider page with no
   * failure signal. Mirrors the reverse drift guard already used for the Javadoc module table
   * (scripts/javadoc.test.mjs), reading the same kind of root-of-truth (a pom's &lt;module&gt; list).
   */
  @Test
  void providerSetMatchesTheReactorsProviderModules() throws Exception {
    final Path rootPom = Path.of("..", "pom.xml");
    assertTrue(Files.exists(rootPom), "expected the OSS reactor root pom at " + rootPom.toAbsolutePath());
    final String pom = Files.readString(rootPom);
    final Pattern modulePattern = Pattern.compile("<module>(agentforge4j-llm-[\\w-]+)</module>");
    final Matcher matcher = modulePattern.matcher(pom);
    final Set<String> expectedProviders = new LinkedHashSet<>();
    while (matcher.find()) {
      final String artifactId = matcher.group(1);
      if ("agentforge4j-llm-api".equals(artifactId) || "agentforge4j-llm-fake".equals(artifactId)) {
        continue; // not providers: the API module and the deterministic test-only provider
      }
      expectedProviders.add(artifactId.substring("agentforge4j-llm-".length()));
    }
    assertTrue(expectedProviders.size() >= 8, "expected to find the reactor's provider modules in " + rootPom);

    final Set<String> emittedProviders = new LinkedHashSet<>();
    providers.forEach(provider -> emittedProviders.add(provider.get("name").asText()));

    assertEquals(expectedProviders, emittedProviders,
        "emitted providers.json must match the reactor's llm-* provider modules exactly — "
            + "a provider module was added to (or removed from) the reactor without updating "
            + "the emitter pom's dependency list");
  }

  /**
   * Drift guard: the emitter pom hardcodes {@code agentforge4j.version} for all ten OSS
   * dependencies with no CI/README override — a release-time reactor version bump that is not also
   * applied here would resolve stale (or unresolvable) artifacts with no failure signal until the
   * build itself breaks confusingly downstream. A failing test that forces a manual bump is the
   * right scope for a value that only ever changes at release time.
   */
  @Test
  void emitterVersionMatchesTheReactorVersion() throws Exception {
    final Path rootPom = Path.of("..", "pom.xml");
    assertTrue(Files.exists(rootPom), "expected the OSS reactor root pom at " + rootPom.toAbsolutePath());
    final Matcher rootVersion = Pattern.compile("<version>([^<]+)</version>").matcher(Files.readString(rootPom));
    assertTrue(rootVersion.find(), "expected to find a <version> in " + rootPom);
    final String reactorVersion = rootVersion.group(1);

    final Path ownPom = Path.of("pom.xml");
    assertTrue(Files.exists(ownPom), "expected the emitter's own pom at " + ownPom.toAbsolutePath());
    final Matcher emitterVersion = Pattern.compile("<agentforge4j\\.version>([^<]+)</agentforge4j\\.version>")
        .matcher(Files.readString(ownPom));
    assertTrue(emitterVersion.find(), "expected to find <agentforge4j.version> in " + ownPom);

    assertEquals(reactorVersion, emitterVersion.group(1),
        "the emitter pom's agentforge4j.version (%s) has drifted from the reactor's own version (%s) — "
            .formatted(emitterVersion.group(1), reactorVersion)
            + "bump agentforge4j-docs-emitter/pom.xml's agentforge4j.version to match a release");
  }

  /**
   * {@link DocsEmitter#main} must never leave a mix of old/new/missing descriptors on a failure
   * partway through — see the atomicity note on {@code emitAll}. Forces the second write (the
   * {@code contract-sets.json} target) to fail by pre-occupying it with a directory, which a file
   * move can never replace, then asserts neither the first nor the third descriptor survived.
   */
  @Test
  void emitAllLeavesNoPartialOutputOnFailure(@TempDir Path partialDir) throws Exception {
    Files.createDirectory(partialDir.resolve("contract-sets.json"));

    assertThrows(Exception.class, () -> DocsEmitter.main(new String[] {partialDir.toString()}));

    assertTrue(Files.notExists(partialDir.resolve("providers.json")),
        "a failed emit must not leave providers.json behind");
    assertTrue(Files.notExists(partialDir.resolve("bootstrap-config.json")),
        "a failed emit must not leave bootstrap-config.json behind");
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
