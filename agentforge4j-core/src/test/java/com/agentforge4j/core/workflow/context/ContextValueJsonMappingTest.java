// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.workflow.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson polymorphism and provenance round-trip for {@link ContextValue} (no network).
 */
class ContextValueJsonMappingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void round_trip_string_preserves_type_and_provenance() throws Exception {
    ContextValue v = new StringContextValue("hello", ContextProvenance.SYSTEM_GENERATED);
    String json = mapper.writeValueAsString(v);
    assertThat(json).contains("\"type\":\"STRING\"");
    assertThat(json).contains("\"provenance\":\"SYSTEM_GENERATED\"");

    ContextValue read = mapper.readValue(json, ContextValue.class);
    assertThat(read).isEqualTo(v);
    assertThat(read.provenance()).isEqualTo(ContextProvenance.SYSTEM_GENERATED);
  }

  @Test
  void round_trip_number_boolean_json_and_list() throws Exception {
    NumberContextValue number = new NumberContextValue(42, ContextProvenance.SYSTEM_GENERATED);
    assertThat(mapper.readValue(mapper.writeValueAsString(number), ContextValue.class))
        .isEqualTo(number);
    BooleanContextValue bool = new BooleanContextValue(true, ContextProvenance.LLM_GENERATED);
    assertThat(mapper.readValue(mapper.writeValueAsString(bool), ContextValue.class))
        .isEqualTo(bool);
    JsonContextValue jsonValue = new JsonContextValue("{\"a\":1}", ContextProvenance.EXTERNAL_TOOL);
    assertThat(mapper.readValue(mapper.writeValueAsString(jsonValue), ContextValue.class))
        .isEqualTo(jsonValue);

    ContextValueList list = new ContextValueList(
        List.of(new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
            new BooleanContextValue(false, ContextProvenance.SYSTEM_GENERATED)),
        ContextProvenance.USER_SUPPLIED);
    assertThat(mapper.readValue(mapper.writeValueAsString(list), ContextValue.class)).isEqualTo(list);
  }

  @Test
  void absent_provenance_in_json_defaults_to_user_supplied() throws Exception {
    // Inbound LLM SET_CONTEXT JSON carries no provenance; the deserialization seam defaults it
    // fail-safe (the write path then re-stamps). Without the seam default this would throw.
    String legacy = "{\"type\":\"STRING\",\"value\":\"x\"}";
    ContextValue read = mapper.readValue(legacy, ContextValue.class);
    assertThat(read).isInstanceOf(StringContextValue.class);
    assertThat(read.provenance()).isEqualTo(ContextProvenance.USER_SUPPLIED);
  }

  @Test
  void with_provenance_returns_restamped_copy() {
    ContextValue original = new StringContextValue("x", ContextProvenance.USER_SUPPLIED);
    ContextValue restamped = original.withProvenance(ContextProvenance.LLM_GENERATED);
    assertThat(restamped.provenance()).isEqualTo(ContextProvenance.LLM_GENERATED);
    assertThat(((StringContextValue) restamped).value()).isEqualTo("x");
    assertThat(original.provenance()).isEqualTo(ContextProvenance.USER_SUPPLIED);
  }
}
