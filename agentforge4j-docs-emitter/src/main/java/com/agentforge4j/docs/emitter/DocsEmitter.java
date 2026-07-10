// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.docs.emitter;

import com.agentforge4j.llm.LlmClientFactory;
import com.agentforge4j.llm.ShippedModelTierDefaults;
import com.agentforge4j.llm.api.ModelTier;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Reflects over the installed AgentForge4j OSS artifacts and emits JSON descriptors consumed by the
 * documentation generators. Run via {@code mvn exec:java -Dexec.args="<output-dir>"} against a local
 * Maven repository that already holds the OSS artifacts (run {@code ./mvnw install -Dmaven.test.skip=true}
 * on the OSS reactor first).
 *
 * <p>Emits three files into the output directory:
 * <ul>
 *   <li>{@code providers.json} — the user-facing provider matrix (excludes the test {@code fake} provider).</li>
 *   <li>{@code contract-sets.json} — the stable contract vocabularies (commands, behaviours, events, statuses, tiers).</li>
 *   <li>{@code bootstrap-config.json} — the value-config subset of {@code AgentForge4jBootstrap.Builder}.</li>
 * </ul>
 *
 * <p>Spring configuration metadata and the curated env/system-property key fixture are JSON already and
 * are consumed directly by the documentation generators, so they are not emitted here.
 */
public final class DocsEmitter {

  private static final Logger LOGGER = System.getLogger(DocsEmitter.class.getName());

  /** The deterministic test-only provider, excluded from the user-facing matrix. */
  private static final String FAKE_PROVIDER = "fake";

  private static final String LLM_COMMAND = "com.agentforge4j.core.command.LlmCommand";
  private static final String STEP_BEHAVIOUR =
      "com.agentforge4j.core.workflow.step.behaviour.StepBehaviour";
  private static final String WORKFLOW_EVENT_TYPE =
      "com.agentforge4j.core.workflow.event.WorkflowEventType";
  private static final String WORKFLOW_STATUS =
      "com.agentforge4j.core.workflow.state.WorkflowStatus";
  private static final String BUILDER = "com.agentforge4j.bootstrap.AgentForge4jBootstrap$Builder";

  /** Parameter types that mark a builder method as value configuration rather than SPI wiring. */
  private static final Set<Class<?>> VALUE_CONFIG_TYPES = Set.of(
      boolean.class, int.class, long.class, double.class,
      String.class, Path.class, Duration.class);

  private final ObjectMapper mapper = new ObjectMapper();

  private DocsEmitter() {
  }

  /**
   * Emits the three descriptor files into the directory named by the first argument.
   *
   * @param args {@code args[0]} is the output directory; created if absent
   * @throws Exception if reflection or file output fails — the build must fail loudly, never emit partial output
   */
  public static void main(String[] args) throws Exception {
    Validate.isTrue(args.length >= 1, "usage: DocsEmitter <output-dir>");
    final Path outputDir = Path.of(args[0]);
    Files.createDirectories(outputDir);
    new DocsEmitter().emitAll(outputDir);
  }

  private void emitAll(Path outputDir) throws Exception {
    write(outputDir.resolve("providers.json"), emitProviders());
    write(outputDir.resolve("contract-sets.json"), emitContractSets());
    write(outputDir.resolve("bootstrap-config.json"), emitBootstrapConfig());
    LOGGER.log(Level.INFO, "DocsEmitter wrote providers.json, contract-sets.json, bootstrap-config.json to %s"
        .formatted(outputDir.toAbsolutePath()));
  }

  /** Provider matrix from the {@link LlmClientFactory} ServiceLoader registrations plus tier defaults. */
  private List<Map<String, Object>> emitProviders() {
    final Map<String, Map<ModelTier, String>> tiers = ShippedModelTierDefaults.asMap();
    final List<Map<String, Object>> providers = new ArrayList<>();
    for (final LlmClientFactory factory : ServiceLoader.load(LlmClientFactory.class)) {
      final String name = factory.getProviderName();
      if (FAKE_PROVIDER.equals(name)) {
        continue;
      }
      final Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", name);
      entry.put("requiresApiKey", factory.requiresApiKey());
      final Map<String, String> tierMap = new LinkedHashMap<>();
      final Map<ModelTier, String> byTier = tiers.get(name);
      for (final ModelTier tier : ModelTier.values()) {
        tierMap.put(tier.name(), byTier == null ? null : byTier.get(tier));
      }
      entry.put("tiers", tierMap);
      providers.add(entry);
    }
    providers.sort(Comparator.comparing(entry -> (String) entry.get("name")));
    return providers;
  }

  /** The stable contract vocabularies, each generated from its live source declaration. */
  private Map<String, Object> emitContractSets() throws ClassNotFoundException {
    final Map<String, Object> sets = new LinkedHashMap<>();
    sets.put("llmCommands", sealedDiscriminators(LLM_COMMAND));
    sets.put("stepBehaviours", sealedDiscriminators(STEP_BEHAVIOUR));
    sets.put("workflowEventTypes", enumNames(WORKFLOW_EVENT_TYPE));
    sets.put("workflowStatuses", enumNames(WORKFLOW_STATUS));
    sets.put("modelTiers", enumNames(ModelTier.class.getName()));
    return sets;
  }

  /**
   * Maps a sealed interface's permitted subclasses to their Jackson JSON {@code type} discriminators,
   * read from the {@code @JsonSubTypes} declaration on the interface. Fails loudly if the annotation is
   * absent or a permit has no declared name.
   */
  private List<Map<String, String>> sealedDiscriminators(String className) throws ClassNotFoundException {
    final Class<?> sealed = Class.forName(className);
    Validate.isTrue(sealed.isSealed(), "%s is expected to be a sealed type".formatted(className));
    final JsonSubTypes subTypes = sealed.getAnnotation(JsonSubTypes.class);
    Validate.notNull(subTypes, "%s must carry @JsonSubTypes to derive JSON type discriminators"
        .formatted(className));
    final Map<String, String> nameByClass = new LinkedHashMap<>();
    for (final JsonSubTypes.Type type : subTypes.value()) {
      Validate.notBlank(type.name(),
          "@JsonSubTypes entry for %s has no name".formatted(type.value().getName()));
      nameByClass.put(type.value().getName(), type.name());
    }

    final List<Map<String, String>> result = new ArrayList<>();
    for (final Class<?> permit : sealed.getPermittedSubclasses()) {
      final String jsonType = nameByClass.get(permit.getName());
      Validate.notNull(jsonType,
          "sealed permit %s has no @JsonSubTypes name on %s".formatted(permit.getName(), className));
      final Map<String, String> entry = new LinkedHashMap<>();
      entry.put("javaName", permit.getSimpleName());
      entry.put("jsonType", jsonType);
      result.add(entry);
    }
    result.sort(Comparator.comparing(entry -> entry.get("jsonType")));
    return result;
  }

  /** The constant names of an enum, in declaration order. */
  private List<String> enumNames(String className) throws ClassNotFoundException {
    final Class<?> enumClass = Class.forName(className);
    Validate.isTrue(enumClass.isEnum(), "%s is expected to be an enum".formatted(className));
    final List<String> names = new ArrayList<>();
    for (final Object constant : enumClass.getEnumConstants()) {
      names.add(((Enum<?>) constant).name());
    }
    return names;
  }

  /**
   * The value-configuration subset of {@code AgentForge4jBootstrap.Builder}: {@code withX} methods whose
   * single parameter is a value type (primitive, {@link String}, {@link Path}, {@link Duration}) or the
   * {@code LlmProviderConfig} carrier. SPI-wiring setters (whose parameter is a framework interface) are
   * excluded. Names and parameter types only — Javadoc descriptions are not retained in bytecode.
   */
  private List<Map<String, String>> emitBootstrapConfig() throws ClassNotFoundException {
    final Class<?> builder = Class.forName(BUILDER);
    final List<Map<String, String>> result = new ArrayList<>();
    for (final Method method : builder.getMethods()) {
      if (!method.getName().startsWith("with")
          || method.getReturnType() != builder
          || method.getParameterCount() != 1) {
        continue;
      }
      final Class<?> paramType = method.getParameterTypes()[0];
      if (!isValueConfig(paramType)) {
        continue;
      }
      final Map<String, String> entry = new LinkedHashMap<>();
      entry.put("method", method.getName());
      entry.put("paramType", paramType.getSimpleName());
      result.add(entry);
    }
    result.sort(Comparator.comparing(entry -> entry.get("method")));
    return result;
  }

  private boolean isValueConfig(Class<?> paramType) {
    return VALUE_CONFIG_TYPES.contains(paramType) || "LlmProviderConfig".equals(paramType.getSimpleName());
  }

  private void write(Path target, Object value) throws IOException {
    // Output is already deterministic: lists are sorted and maps use insertion order.
    mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
  }
}
