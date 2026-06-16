// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.tool;

import com.agentforge4j.util.Validate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

/**
 * Lightweight validator of tool arguments against a JSON Schema. It confirms the arguments are
 * valid JSON and that the {@code required} top-level properties named in the schema are present and
 * non-null. Full JSON Schema validation is intentionally out of scope (no new dependency); when the
 * schema is absent or unparseable, arguments are accepted.
 */
final class ToolArgumentValidator {

  private final ObjectMapper objectMapper;

  ToolArgumentValidator(ObjectMapper objectMapper) {
    this.objectMapper = Validate.notNull(objectMapper, "objectMapper must not be null");
  }

  Result validate(String argumentsJson, String inputSchemaJson) {
    JsonNode arguments;
    if (StringUtils.isBlank(argumentsJson)) {
      arguments = objectMapper.createObjectNode();
    } else {
      try {
        arguments = objectMapper.readTree(argumentsJson);
      } catch (RuntimeException | java.io.IOException e) {
        return Result.invalid("arguments are not valid JSON");
      }
    }

    if (StringUtils.isBlank(inputSchemaJson)) {
      return Result.valid();
    }
    JsonNode schema;
    try {
      schema = objectMapper.readTree(inputSchemaJson);
    } catch (RuntimeException | java.io.IOException e) {
      return Result.valid();
    }

    JsonNode required = schema.get("required");
    if (required == null || !required.isArray() || required.isEmpty()) {
      return Result.valid();
    }
    if (!arguments.isObject()) {
      return Result.invalid("arguments must be a JSON object");
    }
    for (JsonNode requiredName : required) {
      String name = requiredName.asText();
      if (!arguments.has(name) || arguments.get(name).isNull()) {
        return Result.invalid("missing required argument '%s'".formatted(name));
      }
    }
    return Result.valid();
  }

  /**
   * Outcome of validation.
   *
   * @param ok      whether the arguments are acceptable
   * @param message failure detail when not acceptable; {@code null} when acceptable
   */
  record Result(boolean ok, String message) {

    static Result valid() {
      return new Result(true, null);
    }

    static Result invalid(String message) {
      return new Result(false, message);
    }
  }
}
