// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SchemaContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistries.draft202012();

  private static final Path MODULE_ROOT = Path.of(".").toAbsolutePath().normalize();
  private static final Path REPO_ROOT = MODULE_ROOT.getParent();
  private static final Path SCHEMA_DIR = MODULE_ROOT.resolve("src/main/resources/schema");
  private static final Path SHIPPED_WORKFLOWS_DIR =
      REPO_ROOT.resolve("agentforge4j-workflows/src/main/resources/shipped-workflows");
  private static final Path FIXTURES_DIR = MODULE_ROOT.resolve("src/test/resources/fixtures");

  private static final Map<String, Path> SCHEMAS = Map.of(
      "agent.schema.json", SCHEMA_DIR.resolve("agent.schema.json"),
      "workflow.schema.json", SCHEMA_DIR.resolve("workflow.schema.json"),
      "blueprint.schema.json", SCHEMA_DIR.resolve("blueprint.schema.json"),
      "artifact.schema.json", SCHEMA_DIR.resolve("artifact.schema.json")
  );

  @Test
  void draft_2020_12_schema_documents_are_valid() throws Exception {
    List<String> errors = new ArrayList<>();
    for (Map.Entry<String, Path> entry : SCHEMAS.entrySet()) {
      JsonNode schemaNode = MAPPER.readTree(Files.readString(entry.getValue()));
      List<String> schemaErrors = validate(entry.getValue(), schemaNode);
      for (String schemaError : schemaErrors) {
        errors.add(formatError(entry.getValue(), entry.getKey(), "$", schemaError));
      }
    }
    assertThat(errors).as(String.join(System.lineSeparator(), errors)).isEmpty();
  }

  @Test
  void shipped_and_fixture_resources_validate_against_contract_schemas() throws Exception {
    List<ResourceValidationCase> cases = new ArrayList<>();
    cases.addAll(buildShippedCases());
    cases.addAll(buildFixtureCases());

    List<String> errors = new ArrayList<>();
    for (ResourceValidationCase validationCase : cases) {
      JsonNode instance = MAPPER.readTree(Files.readString(validationCase.resourcePath()));
      JsonNode schemaNode = MAPPER.readTree(Files.readString(validationCase.schemaPath()));
      Schema schema = SCHEMA_REGISTRY.getSchema(
          SchemaLocation.of(validationCase.schemaPath().toUri().toString()), schemaNode);
      List<Error> violations = schema.validate(instance);
      for (Error violation : violations) {
        String path = violationPath(violation);
        errors.add(formatError(
            validationCase.resourcePath(),
            validationCase.schemaName(),
            path,
            violation.getMessage()));
      }
    }

    assertThat(errors).as(String.join(System.lineSeparator(), errors)).isEmpty();
  }

  private static List<String> validate(Path schemaPath, JsonNode schemaNode) {
    try {
      Schema metaSchema = SCHEMA_REGISTRY.getSchema(
          SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
      List<Error> violations = metaSchema.validate(schemaNode);
      return violations.stream().map(Error::getMessage).toList();
    } catch (RuntimeException e) {
      return List.of(e.getMessage());
    }
  }

  private static List<ResourceValidationCase> buildShippedCases() throws IOException {
    List<ResourceValidationCase> cases = new ArrayList<>();
    if (!Files.exists(SHIPPED_WORKFLOWS_DIR)) {
      return cases;
    }

    cases.addAll(casesForPattern(SHIPPED_WORKFLOWS_DIR, "*.agent/agent.json", "agent.schema.json"));
    cases.addAll(casesForPattern(SHIPPED_WORKFLOWS_DIR, "*.workflow/workflow.json", "workflow.schema.json"));
    cases.addAll(casesForPattern(SHIPPED_WORKFLOWS_DIR, "*.blueprint.json", "blueprint.schema.json"));
    cases.addAll(casesForPattern(SHIPPED_WORKFLOWS_DIR, "*.artifact.json", "artifact.schema.json"));
    return cases;
  }

  private static List<ResourceValidationCase> buildFixtureCases() throws IOException {
    List<ResourceValidationCase> cases = new ArrayList<>();
    if (!Files.exists(FIXTURES_DIR)) {
      return cases;
    }

    cases.addAll(casesForPattern(FIXTURES_DIR, "*agent.valid.json", "agent.schema.json"));
    cases.addAll(casesForPattern(FIXTURES_DIR, "*workflow.valid.json", "workflow.schema.json"));
    return cases;
  }

  private static List<ResourceValidationCase> casesForPattern(
      Path root, String glob, String schemaName) throws IOException {
    Path schemaPath = SCHEMAS.get(schemaName);
    try (Stream<Path> stream = Files.walk(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileSystem().getPathMatcher("glob:" + glob)
              .matches(path.getFileName()) || path.toString().replace('\\', '/').matches(globToRegex(glob)))
          .sorted(Comparator.naturalOrder())
          .map(path -> new ResourceValidationCase(path, schemaName, schemaPath))
          .collect(Collectors.toList());
    }
  }

  private static String globToRegex(String glob) {
    String regex = glob.replace(".", "\\.").replace("*", ".*");
    return ".*" + regex;
  }

  private static String violationPath(Error violation) {
    String[] candidateMethods = {"getPath", "getInstanceLocation", "getEvaluationPath"};
    for (String methodName : candidateMethods) {
      try {
        Method method = violation.getClass().getMethod(methodName);
        Object value = method.invoke(violation);
        if (value != null) {
          String text = value.toString();
          if (!text.isBlank()) {
            return text;
          }
        }
      } catch (ReflectiveOperationException ignored) {
        // Try the next known API variant.
      }
    }
    return "$";
  }

  private static String formatError(Path filePath, String schemaName, String errorPath,
      String errorMessage) {
    return "file=%s schema=%s errorPath=%s message=%s".formatted(
        filePath.toString().replace('\\', '/'),
        schemaName,
        errorPath,
        errorMessage);
  }

  private record ResourceValidationCase(Path resourcePath, String schemaName, Path schemaPath) {
  }
}
