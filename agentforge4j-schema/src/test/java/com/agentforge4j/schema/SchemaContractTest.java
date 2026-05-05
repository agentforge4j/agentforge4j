package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
  private static final Path CORE_MAIN_JAVA = REPO_ROOT.resolve("agentforge4j-core").resolve("src")
      .resolve("main").resolve("java");

  private JsonNode agentSchema;
  private JsonNode workflowSchema;
  private JsonNode blueprintSchema;
  private JsonNode artifactSchema;

  @BeforeEach
  void setUp() throws IOException {
    SchemaProvider provider = new ClasspathSchemaProvider();
    agentSchema = MAPPER.readTree(provider.agentSchema());
    workflowSchema = MAPPER.readTree(provider.workflowSchema());
    blueprintSchema = MAPPER.readTree(provider.blueprintSchema());
    artifactSchema = MAPPER.readTree(provider.artifactSchema());
  }

  @Test
  void valid_agent_fixture_matches_contract_checks() throws IOException {
    JsonNode fixture = readFixture("fixtures/agent.valid.json");
    List<String> errors = validateAgentFixture(fixture);

    assertThat(errors).isEmpty();
  }

  @Test
  void invalid_agent_fixture_fails_contract_checks() throws IOException {
    JsonNode fixture = readFixture("fixtures/agent.invalid.json");
    List<String> errors = validateAgentFixture(fixture);

    assertThat(errors)
        .anyMatch(error -> error.contains("locality"))
        .anyMatch(error -> error.contains("providerPreferences"));
  }

  @Test
  void valid_workflow_fixture_matches_contract_checks() throws IOException {
    JsonNode fixture = readFixture("fixtures/workflow.valid.json");
    List<String> errors = validateWorkflowFixture(fixture);

    assertThat(errors).isEmpty();
  }

  @Test
  void invalid_workflow_fixture_fails_contract_checks() throws IOException {
    JsonNode fixture = readFixture("fixtures/workflow.invalid.json");
    List<String> errors = validateWorkflowFixture(fixture);

    assertThat(errors)
        .anyMatch(error -> error.contains("kind"))
        .anyMatch(error -> error.contains("steps"));
  }

  @Test
  void schema_enums_match_java_enums_for_critical_contracts() {
    assertThat(schemaEnum(agentSchema, "properties", "locality", "enum"))
        .containsExactlyInAnyOrderElementsOf(
            enumValuesFromSource("com/agentforge4j/core/agent/AgentLocality.java"));

    assertThat(schemaEnum(workflowSchema, "$defs", "StepTransition", "enum"))
        .containsExactlyInAnyOrderElementsOf(
            enumValuesFromSource("com/agentforge4j/core/workflow/step/StepTransition.java"));

    assertThat(schemaEnum(workflowSchema, "$defs", "RetryPreviousBehaviour", "properties", "retryMode", "enum"))
        .containsExactlyInAnyOrderElementsOf(
            enumValuesFromSource("com/agentforge4j/core/workflow/step/behaviour/RetryMode.java"));

    assertThat(schemaEnum(blueprintSchema, "$defs", "LoopConfig", "properties", "terminationStrategy", "enum"))
        .containsExactlyInAnyOrderElementsOf(
            enumValuesFromSource("com/agentforge4j/core/workflow/step/loop/LoopTerminationStrategy.java"));

    assertThat(schemaEnum(blueprintSchema, "$defs", "LoopConfig", "properties", "maxIterationsAction", "enum"))
        .containsExactlyInAnyOrderElementsOf(
            enumValuesFromSource("com/agentforge4j/core/workflow/step/loop/MaxIterationsAction.java"));
  }

  @Test
  void schema_supported_commands_match_llm_command_subtypes() {
    Set<String> schemaCommands = schemaEnum(agentSchema, "properties", "supportedCommands", "items", "enum");
    Set<String> javaCommands = commandNamesFromLlmCommandSource();

    assertThat(schemaCommands).containsExactlyInAnyOrderElementsOf(javaCommands);
  }

  @Test
  void top_level_required_fields_agent_schema_align_with_java_required_components() {
    Set<String> schemaRequired = schemaRequired(agentSchema);
    Set<String> javaRequired = requiredFieldsFromRecordValidation(
        "com/agentforge4j/core/agent/AgentDefinition.java");

    assertThat(schemaRequired)
        .as("Agent schema required fields drift from AgentDefinition constructor contract")
        .containsExactlyInAnyOrderElementsOf(javaRequired);
  }

  @Test
  void top_level_required_fields_workflow_schema_align_with_java_required_components() {
    Set<String> schemaRequired = new LinkedHashSet<>(schemaRequired(workflowSchema));
    schemaRequired.remove("kind");
    Set<String> javaRequired = requiredFieldsFromRecordValidation(
        "com/agentforge4j/core/workflow/WorkflowDefinition.java");

    assertThat(schemaRequired)
        .as("Workflow schema required fields drift from WorkflowDefinition constructor contract")
        .containsExactlyInAnyOrderElementsOf(javaRequired);
  }

  @Test
  void top_level_required_fields_blueprint_schema_align_with_java_required_components() {
    Set<String> schemaRequired = new LinkedHashSet<>(schemaRequired(blueprintSchema));
    schemaRequired.remove("kind");
    Set<String> javaRequired = requiredFieldsFromRecordValidation(
        "com/agentforge4j/core/workflow/step/blueprint/BlueprintDefinition.java");

    assertThat(schemaRequired)
        .as("Blueprint schema required fields drift from BlueprintDefinition constructor contract")
        .containsExactlyInAnyOrderElementsOf(javaRequired);
  }

  @Test
  void top_level_required_fields_artifact_schema_align_with_java_required_components() {
    Set<String> schemaRequired = schemaRequired(artifactSchema);
    Set<String> javaRequired = requiredFieldsFromRecordValidation(
        "com/agentforge4j/core/workflow/artifact/ArtifactDefinition.java");

    assertThat(schemaRequired)
        .as("Artifact schema required fields drift from ArtifactDefinition constructor contract")
        .containsExactlyInAnyOrderElementsOf(javaRequired);
  }

  @Test
  void step_definition_required_fields_align_with_java_constructor_contract() {
    Set<String> schemaRequired = new LinkedHashSet<>(
        schemaRequired(nodeAt(workflowSchema, "$defs", "StepDefinition")));
    schemaRequired.remove("kind");
    Set<String> javaRequired = requiredFieldsFromRecordValidation(
        "com/agentforge4j/core/workflow/step/StepDefinition.java");

    assertThat(schemaRequired)
        .as("Workflow schema StepDefinition.required drift from StepDefinition constructor contract")
        .containsExactlyInAnyOrderElementsOf(javaRequired);
  }

  @Test
  void step_definition_schema_properties_cover_represented_java_components() {
    Set<String> schemaProperties = objectFieldNames(nodeAt(workflowSchema, "$defs", "StepDefinition", "properties"));
    Set<String> javaComponents = recordComponentNamesFromSource(
        "com/agentforge4j/core/workflow/step/StepDefinition.java");

    // Explicit mapping rule: stepPrompt exists in Java model but is currently not represented in schema.
    Set<String> expectedRepresentedComponents = new LinkedHashSet<>(javaComponents);
    expectedRepresentedComponents.remove("stepPrompt");

    assertThat(schemaProperties)
        .as("StepDefinition schema properties drift from represented Java components")
        .containsAll(expectedRepresentedComponents);
  }

  @Test
  void workflow_step_behaviour_discriminator_values_match_java_subtypes() {
    Set<String> schemaBehaviourTypes =
        schemaEnum(workflowSchema, "$defs", "StepBehaviour", "properties", "type", "enum");
    Set<String> javaBehaviourTypes = jsonSubtypeNamesFromSource(
        "com/agentforge4j/core/workflow/step/behaviour/StepBehaviour.java");

    assertThat(schemaBehaviourTypes)
        .as("StepBehaviour discriminator values drift between schema and Java")
        .containsExactlyInAnyOrderElementsOf(javaBehaviourTypes);
  }

  @Test
  void artifact_item_discriminator_values_match_java_subtypes() {
    Set<String> javaItemTypes = jsonSubtypeNamesFromSource(
        "com/agentforge4j/core/workflow/artifact/ArtifactItem.java");
    Set<String> schemaItemTypes = constValuesFromDefs(nodeAt(artifactSchema, "$defs"));

    assertThat(schemaItemTypes)
        .as("Artifact item type discriminator values drift between schema and Java")
        .containsAll(javaItemTypes);
  }

  private static JsonNode readFixture(String classpathPath) throws IOException {
    try (InputStream stream = SchemaContractTest.class.getClassLoader().getResourceAsStream(classpathPath)) {
      assertThat(stream).as("fixture must exist: %s", classpathPath).isNotNull();
      String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return MAPPER.readTree(json);
    }
  }

  private List<String> validateAgentFixture(JsonNode fixture) {
    List<String> errors = new ArrayList<>();
    assertRequiredFieldsPresent(agentSchema, fixture, errors);
    assertEnumValue(agentSchema, fixture, errors, "locality", "properties", "locality", "enum");
    assertEnumValues(agentSchema, fixture, errors, "supportedCommands", "properties", "supportedCommands", "items", "enum");
    assertMinItems(agentSchema, fixture, errors, "providerPreferences", "properties", "providerPreferences", "minItems");
    assertProviderEnum(agentSchema, fixture, errors);
    return errors;
  }

  private List<String> validateWorkflowFixture(JsonNode fixture) {
    List<String> errors = new ArrayList<>();
    assertRequiredFieldsPresent(workflowSchema, fixture, errors);
    assertConstValue(fixture, errors, "kind", workflowSchema.get("properties").get("kind").get("const").asText());
    assertMinItems(workflowSchema, fixture, errors, "steps", "properties", "steps", "minItems");
    return errors;
  }

  private static void assertRequiredFieldsPresent(JsonNode schema, JsonNode instance, List<String> errors) {
    for (String required : schemaRequired(schema)) {
      if (!instance.has(required) || instance.get(required).isNull()) {
        errors.add("Missing required field: " + required);
      }
    }
  }

  private static void assertEnumValue(
      JsonNode schema,
      JsonNode instance,
      List<String> errors,
      String fieldName,
      String... enumPath) {
    if (!instance.has(fieldName)) {
      return;
    }
    Set<String> allowed = schemaEnum(schema, enumPath);
    String actual = instance.get(fieldName).asText();
    if (!allowed.contains(actual)) {
      errors.add("Field '%s' must be one of %s but was '%s'".formatted(fieldName, allowed, actual));
    }
  }

  private static void assertEnumValues(
      JsonNode schema,
      JsonNode instance,
      List<String> errors,
      String fieldName,
      String... enumPath) {
    if (!instance.has(fieldName) || !instance.get(fieldName).isArray()) {
      return;
    }
    Set<String> allowed = schemaEnum(schema, enumPath);
    for (JsonNode item : instance.get(fieldName)) {
      String actual = item.asText();
      if (!allowed.contains(actual)) {
        errors.add("Array '%s' contains unsupported value '%s'".formatted(fieldName, actual));
      }
    }
  }

  private static void assertProviderEnum(JsonNode schema, JsonNode instance, List<String> errors) {
    if (!instance.has("providerPreferences") || !instance.get("providerPreferences").isArray()) {
      return;
    }
    JsonNode providerEnum = schema.get("$defs").get("ProviderPreference")
        .get("properties").get("provider").get("enum");
    Set<String> allowedProviders = arrayValues(providerEnum);
    for (JsonNode pref : instance.get("providerPreferences")) {
      if (!pref.has("provider")) {
        errors.add("providerPreferences item missing 'provider'");
        continue;
      }
      String provider = pref.get("provider").asText();
      if (!allowedProviders.contains(provider)) {
        errors.add("Unsupported provider '%s'. Allowed: %s".formatted(provider, allowedProviders));
      }
    }
  }

  private static void assertConstValue(JsonNode instance, List<String> errors, String fieldName, String expected) {
    if (!instance.has(fieldName)) {
      return;
    }
    String actual = instance.get(fieldName).asText();
    if (!expected.equals(actual)) {
      errors.add("Field '%s' must be '%s' but was '%s'".formatted(fieldName, expected, actual));
    }
  }

  private static void assertMinItems(
      JsonNode schema,
      JsonNode instance,
      List<String> errors,
      String fieldName,
      String... minItemsPath) {
    if (!instance.has(fieldName) || !instance.get(fieldName).isArray()) {
      return;
    }
    int minItems = nodeAt(schema, minItemsPath).asInt();
    int actual = instance.get(fieldName).size();
    if (actual < minItems) {
      errors.add("Array '%s' must have at least %d items but had %d".formatted(fieldName, minItems, actual));
    }
  }

  private static Set<String> schemaRequired(JsonNode schema) {
    return arrayValues(schema.get("required"));
  }

  private static Set<String> schemaEnum(JsonNode root, String... path) {
    return arrayValues(nodeAt(root, path));
  }

  private static JsonNode nodeAt(JsonNode root, String... path) {
    JsonNode node = root;
    for (String pathElement : path) {
      node = node.get(pathElement);
      assertThat(node).as("Missing schema path segment '%s'", pathElement).isNotNull();
    }
    return node;
  }

  private static Set<String> arrayValues(JsonNode arrayNode) {
    assertThat(arrayNode).isNotNull();
    assertThat(arrayNode.isArray()).isTrue();
    Set<String> values = new LinkedHashSet<>();
    for (JsonNode item : arrayNode) {
      values.add(item.asText());
    }
    return values;
  }

  private static Set<String> enumValuesFromSource(String relativePath) {
    String source = readCoreSource(relativePath);
    Matcher matcher = Pattern.compile("(?m)^\\s*([A-Z][A-Z0-9_]*)\\s*(?:,)?\\s*$").matcher(source);
    Set<String> values = new LinkedHashSet<>();
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }

  private static Set<String> recordComponentNamesFromSource(String relativePath) {
    String source = readCoreSource(relativePath);
    Matcher recordMatcher = Pattern.compile(
            "record\\s+\\w+\\s*\\((.*?)\\)\\s*(?:implements\\s+[^{]+)?\\{",
            Pattern.DOTALL)
        .matcher(source);
    assertThat(recordMatcher.find()).isTrue();
    String componentsText = recordMatcher.group(1).replace("\r", " ").replace("\n", " ");
    Matcher componentMatcher =
        Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:,|$)").matcher(componentsText);
    Set<String> components = new LinkedHashSet<>();
    while (componentMatcher.find()) {
      components.add(componentMatcher.group(1));
    }
    return components;
  }

  private static Set<String> requiredFieldsFromRecordValidation(String relativePath) {
    String source = readCoreSource(relativePath);
    Matcher matcher = Pattern.compile("Validate\\.(?:notBlank|notNull|notEmpty)\\((\\w+),").matcher(source);
    Set<String> required = new LinkedHashSet<>();
    while (matcher.find()) {
      required.add(matcher.group(1));
    }
    return required;
  }

  private static Set<String> commandNamesFromLlmCommandSource() {
    String source = readCoreSource("com/agentforge4j/core/command/LlmCommand.java");
    Matcher matcher = Pattern.compile("name\\s*=\\s*\"([A-Z_]+)\"").matcher(source);
    Set<String> commands = new LinkedHashSet<>();
    while (matcher.find()) {
      commands.add(matcher.group(1));
    }
    return commands;
  }

  private static Set<String> jsonSubtypeNamesFromSource(String relativePath) {
    String source = readCoreSource(relativePath);
    Matcher matcher = Pattern.compile("name\\s*=\\s*\"([A-Z_]+)\"").matcher(source);
    Set<String> names = new LinkedHashSet<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  private static Set<String> constValuesFromDefs(JsonNode defsNode) {
    assertThat(defsNode).isNotNull();
    Set<String> values = new LinkedHashSet<>();
    defsNode.fields().forEachRemaining(entry -> {
      JsonNode properties = entry.getValue().get("properties");
      if (properties == null || !properties.isObject()) {
        return;
      }
      JsonNode type = properties.get("type");
      if (type != null && type.has("const")) {
        values.add(type.get("const").asText());
      }
    });
    return values;
  }

  private static Set<String> objectFieldNames(JsonNode objectNode) {
    assertThat(objectNode).isNotNull();
    if (!objectNode.isObject()) {
      return Collections.emptySet();
    }
    Set<String> fields = new LinkedHashSet<>();
    objectNode.fieldNames().forEachRemaining(fields::add);
    return fields;
  }

  private static String readCoreSource(String relativePath) {
    Path path = CORE_MAIN_JAVA.resolve(relativePath);
    assertThat(Files.exists(path)).as("Expected source file to exist: %s", path).isTrue();
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read source file: " + path, e);
    }
  }
}
