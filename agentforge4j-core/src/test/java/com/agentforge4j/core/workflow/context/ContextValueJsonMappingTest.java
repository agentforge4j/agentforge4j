package com.agentforge4j.core.workflow.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson polymorphism for {@link ContextValue} (no network).
 */
class ContextValueJsonMappingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void round_trip_string() throws Exception {
    ContextValue v = new StringContextValue("hello");
    String json = mapper.writeValueAsString(v);
    assertThat(json).contains("\"type\":\"STRING\"");

    ContextValue read = mapper.readValue(json, ContextValue.class);
    assertThat(read).isEqualTo(v);
  }

  @Test
  void round_trip_number_boolean_json_and_list() throws Exception {
    assertThat(mapper.readValue(mapper.writeValueAsString(new NumberContextValue(42)), ContextValue.class))
        .isEqualTo(new NumberContextValue(42));
    assertThat(mapper.readValue(mapper.writeValueAsString(new BooleanContextValue(true)), ContextValue.class))
        .isEqualTo(new BooleanContextValue(true));
    assertThat(mapper.readValue(mapper.writeValueAsString(new JsonContextValue("{\"a\":1}")), ContextValue.class))
        .isEqualTo(new JsonContextValue("{\"a\":1}"));

    var list = new ContextValueList(List.of(new StringContextValue("a"), new BooleanContextValue(false)));
    assertThat(mapper.readValue(mapper.writeValueAsString(list), ContextValue.class)).isEqualTo(list);
  }
}
