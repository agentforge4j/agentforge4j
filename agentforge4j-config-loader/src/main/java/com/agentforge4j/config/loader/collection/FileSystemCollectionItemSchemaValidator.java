// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.collection;

import com.agentforge4j.core.spi.validation.CollectionItemSchemaValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link CollectionItemSchemaValidator} resolving each {@code itemSchemaRef} to a JSON-schema file
 * {@code <ref>.schema.json} inside a configured directory, mirroring how the other external
 * configuration (agents, workflows, integrations) is loaded from directories. Parsed schemas are
 * cached per reference for the validator's lifetime, so edits to a schema file require a restart —
 * the same contract as every other config directory.
 *
 * <p>Fail-closed by contract: an unknown reference, a reference escaping the directory, an
 * unparseable schema file, or unparseable item JSON all yield an invalid result, never a pass.
 */
public final class FileSystemCollectionItemSchemaValidator implements CollectionItemSchemaValidator {

  private static final String FILE_SUFFIX = ".schema.json";

  private final Path schemasDir;
  private final ObjectMapper objectMapper;
  private final Map<String, Schema> schemasByRef = new ConcurrentHashMap<>();

  /**
   * Creates a validator over the given schema directory.
   *
   * @param schemasDir   directory holding {@code <ref>.schema.json} files; must be an existing
   *                     directory
   * @param objectMapper mapper used to parse schema files and item JSON; must not be {@code null}
   */
  public FileSystemCollectionItemSchemaValidator(Path schemasDir, ObjectMapper objectMapper) {
    this.schemasDir = Validate.requireDirectory(schemasDir, "schemasDir must be a valid directory");
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public ValidationResult validate(String itemSchemaRef, String inlineJson) {
    Validate.notBlank(itemSchemaRef, "itemSchemaRef must not be blank");
    Validate.notBlank(inlineJson, "inlineJson must not be blank");
    Schema schema;
    try {
      schema = schemasByRef.computeIfAbsent(itemSchemaRef, this::loadSchema);
    } catch (IllegalArgumentException invalidRef) {
      return ValidationResult.invalid(invalidRef.getMessage());
    }
    JsonNode item;
    try {
      item = objectMapper.readTree(inlineJson);
    } catch (IOException e) {
      return ValidationResult.invalid("item is not parseable JSON: %s".formatted(e.getMessage()));
    }
    List<Error> violations = schema.validate(item);
    if (violations.isEmpty()) {
      return ValidationResult.ok();
    }
    return ValidationResult.invalid(violations.stream()
        .map(violation -> "%s: %s".formatted(violation.getInstanceLocation(), violation.getMessage()))
        .collect(Collectors.joining(", ")));
  }

  private Schema loadSchema(String itemSchemaRef) {
    Path file = Validate.requireWithinBase(schemasDir, itemSchemaRef + FILE_SUFFIX,
        "itemSchemaRef '%s' must resolve inside the schemas directory".formatted(itemSchemaRef));
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException(
          "unknown item schema '%s': no file %s".formatted(itemSchemaRef, file.getFileName()));
    }
    try {
      JsonNode schemaNode = objectMapper.readTree(Files.readString(file));
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
          .getSchema(schemaNode);
    } catch (IOException | RuntimeException e) {
      throw new IllegalArgumentException(
          "item schema '%s' failed to parse: %s".formatted(itemSchemaRef, e.getMessage()), e);
    }
  }
}
