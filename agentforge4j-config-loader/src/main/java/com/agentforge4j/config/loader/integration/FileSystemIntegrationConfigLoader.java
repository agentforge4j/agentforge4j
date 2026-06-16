package com.agentforge4j.config.loader.integration;

import com.agentforge4j.core.spi.integration.IntegrationConfigLoader;
import com.agentforge4j.core.spi.integration.IntegrationDefinition;
import com.agentforge4j.core.spi.integration.IntegrationType;
import com.agentforge4j.schema.SchemaProvider;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads {@link IntegrationDefinition}s from top-level {@code *.json} files in a single integrations
 * directory. Each file is one integration in the {@code integration.schema.json} envelope; the
 * type-specific {@code config} payload is retained verbatim as JSON text and never interpreted here
 * — parsing it is the {@code ToolProviderFactory}'s job.
 *
 * <p>Selection is non-recursive: subdirectories and non-{@code .json} entries are ignored. Loading
 * is all-or-nothing — a malformed or schema-invalid file fails the whole load with one exception
 * naming every offending file, and a duplicate integration id across files fails naming the id and
 * both files. The loader does not filter on {@code active}; inactive definitions load normally and
 * are excluded later by capability resolution.
 */
public final class FileSystemIntegrationConfigLoader implements IntegrationConfigLoader {

  private static final System.Logger LOG =
      System.getLogger(FileSystemIntegrationConfigLoader.class.getName());

  private static final String INTEGRATION_FILE_SUFFIX = ".json";

  private final ObjectMapper objectMapper;
  private final Schema integrationSchema;
  private final Path integrationsRoot;

  /**
   * Creates a loader over a single integrations directory.
   *
   * @param objectMapper     JSON mapper used to parse definition files; must not be {@code null}
   * @param schemaProvider   source of {@code integration.schema.json}; must not be {@code null}
   * @param integrationsRoot directory holding the integration definition files; must be an existing
   *                         directory
   *
   * @throws IllegalArgumentException if {@code integrationsRoot} is missing or not a directory
   */
  public FileSystemIntegrationConfigLoader(ObjectMapper objectMapper,
      SchemaProvider schemaProvider, Path integrationsRoot) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    Validate.notNull(schemaProvider, "schemaProvider must not be null");
    this.integrationsRoot = Validate.requireDirectory(integrationsRoot,
        "Integrations directory does not exist: %s".formatted(integrationsRoot));
    this.integrationSchema = parseIntegrationSchema(objectMapper, schemaProvider);
  }

  @Override
  public List<IntegrationDefinition> load() {
    List<Path> files = listIntegrationFiles();
    LOG.log(System.Logger.Level.INFO, "Loading {0} integration definition file(s) from {1}",
        files.size(), integrationsRoot);
    Map<Path, JsonNode> validated = readAndValidate(files);
    Map<String, Path> fileById = new LinkedHashMap<>();
    List<IntegrationDefinition> definitions = new ArrayList<>();
    for (Map.Entry<Path, JsonNode> entry : validated.entrySet()) {
      IntegrationDefinition definition = toDefinition(entry.getValue(), entry.getKey());
      Path previousFile = fileById.putIfAbsent(definition.id(), entry.getKey());
      Validate.isTrue(previousFile == null,
          "Duplicate integration id '%s' in %s and %s"
              .formatted(definition.id(), previousFile, entry.getKey()));
      definitions.add(definition);
    }
    return List.copyOf(definitions);
  }

  private List<Path> listIntegrationFiles() {
    try (Stream<Path> entries = Files.list(integrationsRoot)) {
      return entries
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(INTEGRATION_FILE_SUFFIX))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read integrations directory: %s".formatted(integrationsRoot), e);
    }
  }

  /**
   * Parses and schema-validates every file, collecting all failures so one exception names each
   * offending file with its reason instead of stopping at the first.
   */
  private Map<Path, JsonNode> readAndValidate(List<Path> files) {
    Map<Path, JsonNode> validated = new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();
    for (Path file : files) {
      JsonNode node = readAndValidate(file, errors);
      if (node != null) {
        validated.put(file, node);
      }
    }
    Validate.isTrue(errors.isEmpty(), () -> new IllegalArgumentException(
        "Invalid integration definition file(s): %s"
            .formatted(String.join("; ", errors))));
    return validated;
  }

  private JsonNode readAndValidate(Path file, List<String> errors) {
    JsonNode node;
    try {
      node = objectMapper.readTree(Files.readString(file));
    } catch (JsonProcessingException e) {
      errors.add("%s: malformed JSON (%s)".formatted(file, e.getOriginalMessage()));
      return null;
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to read integration definition file: %s".formatted(file), e);
    }
    List<Error> violations = integrationSchema.validate(node);
    if (!violations.isEmpty()) {
      // 2.0.x no longer bakes the instance location into getMessage(); reconstruct
      // "{instanceLocation}: {message}" explicitly so operators still see which field failed.
      errors.add("%s: %s".formatted(file, violations.stream()
          .map(violation -> "%s: %s".formatted(violation.getInstanceLocation(), violation.getMessage()))
          .collect(Collectors.joining(", "))));
      return null;
    }
    return node;
  }

  /**
   * Maps a schema-validated envelope to the core definition. The in-file {@code id} wins; when
   * absent, the filename without {@code .json} is the id. The {@code config} payload is
   * re-serialized verbatim as JSON text without interpretation.
   */
  private IntegrationDefinition toDefinition(JsonNode node, Path file) {
    String id = Validate.notBlank(resolveId(node, file),
        "Integration id must not be blank (file %s)".formatted(file));
    String displayName = Validate.notBlank(textOrNull(node, "displayName"),
        "Integration displayName must not be blank (file %s)".formatted(file));
    IntegrationType type = parseType(textOrNull(node, "type"), file);
    boolean active = !node.has("active") || node.get("active").asBoolean();
    String config = writeConfig(node.get("config"), file);
    return new IntegrationDefinition(id, displayName, type, config, active);
  }

  private static String resolveId(JsonNode node, Path file) {
    String inFileId = textOrNull(node, "id");
    if (inFileId != null) {
      return inFileId;
    }
    String fileName = file.getFileName().toString();
    return fileName.substring(0, fileName.length() - INTEGRATION_FILE_SUFFIX.length());
  }

  private static IntegrationType parseType(String raw, Path file) {
    Validate.notBlank(raw, "Integration type must not be blank (file %s)".formatted(file));
    try {
      return IntegrationType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown integration type '%s' (file %s)".formatted(raw, file), e);
    }
  }

  private String writeConfig(JsonNode configNode, Path file) {
    Validate.notNull(configNode, "Integration config must be present (file %s)".formatted(file));
    try {
      return objectMapper.writeValueAsString(configNode);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "Failed to serialize integration config (file %s)".formatted(file), e);
    }
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return (value == null || value.isNull()) ? null : value.asText();
  }

  private static Schema parseIntegrationSchema(ObjectMapper objectMapper,
      SchemaProvider schemaProvider) {
    try {
      JsonNode schemaNode = objectMapper.readTree(schemaProvider.integrationSchema());
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(schemaNode);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse integration.schema.json", e);
    }
  }
}
