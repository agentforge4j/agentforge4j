package com.agentforge4j.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ToolArgumentValidatorTest {

  private final ToolArgumentValidator validator = new ToolArgumentValidator(new ObjectMapper());

  private static final String SCHEMA =
      "{\"type\":\"object\",\"required\":[\"title\"],\"properties\":{\"title\":{\"type\":\"string\"}}}";

  @Test
  void acceptsArgumentsWithRequiredFields() {
    assertThat(validator.validate("{\"title\":\"x\"}", SCHEMA).ok()).isTrue();
  }

  @Test
  void rejectsMissingRequiredField() {
    ToolArgumentValidator.Result result = validator.validate("{\"body\":\"x\"}", SCHEMA);

    assertThat(result.ok()).isFalse();
    assertThat(result.message()).contains("title");
  }

  @Test
  void rejectsInvalidJsonArguments() {
    assertThat(validator.validate("not json", SCHEMA).ok()).isFalse();
  }

  @Test
  void acceptsWhenNoSchema() {
    assertThat(validator.validate("{\"anything\":1}", null).ok()).isTrue();
  }

  @Test
  void treatsBlankArgumentsAsEmptyObject() {
    assertThat(validator.validate(null, SCHEMA).ok()).isFalse();
    assertThat(validator.validate(null, null).ok()).isTrue();
  }
}
