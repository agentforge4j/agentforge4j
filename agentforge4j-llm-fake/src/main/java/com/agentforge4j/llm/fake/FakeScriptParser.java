// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses fake-provider script JSON into an immutable {@link FakeScript}. Validates against the module-local
 * {@code fake-llm-script.schema.json} (networknt, JSON Schema draft 2020-12, the same dialect and pinned validator the
 * rest of the project uses), rejects duplicate keys, and rejects an unsupported schema version. Ordinal gaps are
 * allowed — a missing ordinal fails closed at the call that requests it, intentionally.
 */
public final class FakeScriptParser {

  /**
   * The only schema version this parser accepts.
   */
  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  private static final String SCHEMA_RESOURCE = "/schema/fake-llm-script.schema.json";

  private final ObjectMapper objectMapper;
  private final Schema schema;

  /**
   * Creates a parser with a private {@link ObjectMapper}.
   */
  public FakeScriptParser() {
    this(new ObjectMapper());
  }

  /**
   * Creates a parser using the given mapper for both script and schema parsing.
   *
   * @param objectMapper JSON mapper; must not be {@code null}
   */
  public FakeScriptParser(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
    this.schema = loadSchema(objectMapper);
  }

  /**
   * Parses and validates script JSON into an immutable {@link FakeScript}.
   *
   * @param json the script JSON text; must not be blank
   *
   * @return the parsed, immutable script
   *
   * @throws IllegalArgumentException if the JSON is malformed, fails schema validation, declares an unsupported schema
   *                                  version, or contains duplicate keys
   */
  public FakeScript parse(String json) {
    Validate.notBlank(json, "script json must not be blank");
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Malformed fake script JSON: " + exception.getOriginalMessage(), exception);
    }

    List<Error> violations = schema.validate(root);
    Validate.isTrue(violations.isEmpty(), () -> new IllegalArgumentException(
        "Fake script failed schema validation: " + violations.stream()
            .map(violation -> "%s: %s".formatted(violation.getInstanceLocation(), violation.getMessage()))
            .collect(Collectors.joining(", "))));

    int schemaVersion = root.get("schemaVersion").asInt();
    Validate.isTrue(schemaVersion == SUPPORTED_SCHEMA_VERSION, () -> new IllegalArgumentException(
        "Unsupported fake script schemaVersion %d (supported: %d)".formatted(schemaVersion, SUPPORTED_SCHEMA_VERSION)));

    Map<FakeScriptKey, FakeResponse> responses = new LinkedHashMap<>();
    for (JsonNode entry : root.get("responses")) {
      FakeScriptKey key = new FakeScriptKey(entry.get("workflowId").asText(), entry.get("stepId").asText(),
          entry.get("agentId").asText(), entry.get("ordinal").asInt());
      FakeResponse response = new FakeResponse(entry.get("responseText").asText(),
          parseTokenUsage(entry.get("tokenUsage")));
      FakeResponse previous = responses.putIfAbsent(key, response);
      Validate.isTrue(previous == null, "Duplicate fake script key: " + key);
    }
    return new FakeScript(schemaVersion, responses);
  }

  private static FakeTokenUsage parseTokenUsage(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    return new FakeTokenUsage(intOrNull(node, "inputTokens"), intOrNull(node, "outputTokens"),
        intOrNull(node, "cachedInputTokens"), intOrNull(node, "cacheWriteTokens"));
  }

  private static Integer intOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return (value == null || value.isNull()) ? null : value.intValue();
  }

  private static Schema loadSchema(ObjectMapper objectMapper) {
    try (InputStream stream = FakeScriptParser.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      Validate.notNull(stream, () -> new IllegalStateException("Missing classpath resource: " + SCHEMA_RESOURCE));
      JsonNode schemaNode = objectMapper.readTree(stream.readAllBytes());
      return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schemaNode);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read " + SCHEMA_RESOURCE, exception);
    }
  }
}
