// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.core.spi.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemCollectionItemSchemaValidatorTest {

  private static final String CV_SCHEMA = """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "required": ["name", "years"],
        "additionalProperties": false,
        "properties": {
          "name": { "type": "string", "minLength": 1 },
          "years": { "type": "integer", "minimum": 0 }
        }
      }
      """;

  @TempDir
  Path schemasDir;

  private FileSystemCollectionItemSchemaValidator validator;

  @BeforeEach
  void setUp() throws IOException {
    Files.writeString(schemasDir.resolve("cv-item.schema.json"), CV_SCHEMA);
    validator = new FileSystemCollectionItemSchemaValidator(schemasDir, new ObjectMapper());
  }

  @Test
  void acceptsItemConformingToReferencedSchema() {
    ValidationResult result =
        validator.validate("cv-item", "{\"name\":\"Ada\",\"years\":12}");

    assertThat(result.valid()).isTrue();
  }

  @Test
  void rejectsItemViolatingReferencedSchema() {
    ValidationResult result = validator.validate("cv-item", "{\"name\":\"\",\"years\":-1}");

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("name").contains("years");
  }

  @Test
  void rejectsItemMissingRequiredField() {
    ValidationResult result = validator.validate("cv-item", "{\"name\":\"Ada\"}");

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("years");
  }

  @Test
  void rejectsUnparseableItemJson() {
    ValidationResult result = validator.validate("cv-item", "{not json");

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("not parseable");
  }

  @Test
  void rejectsUnknownSchemaReferenceFailClosed() {
    ValidationResult result = validator.validate("no-such-schema", "{\"name\":\"Ada\",\"years\":1}");

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("unknown item schema");
  }

  @Test
  void rejectsReferenceEscapingTheSchemasDirectory() {
    ValidationResult result = validator.validate("../outside", "{\"name\":\"Ada\",\"years\":1}");

    assertThat(result.valid()).isFalse();
  }

  @Test
  void rejectsUnparseableSchemaFileFailClosed() throws IOException {
    Files.writeString(schemasDir.resolve("broken.schema.json"), "{not a schema");

    ValidationResult result = validator.validate("broken", "{\"name\":\"Ada\",\"years\":1}");

    assertThat(result.valid()).isFalse();
    assertThat(result.message()).contains("failed to parse");
  }
}
