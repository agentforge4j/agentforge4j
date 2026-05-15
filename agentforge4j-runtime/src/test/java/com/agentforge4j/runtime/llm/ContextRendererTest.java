package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
class ContextRendererTest {

  private final ContextRenderer renderer = new ContextRenderer(new ObjectMapper());

  @Test
  void simple_key_renders_as_json_field() {
    Map<String, ContextValue> context = Map.of("name", new StringContextValue("world"));

    String json = renderer.render(context, new ContextMapping(List.of("name"), List.of()));

    assertThat(json).contains("\"name\":\"world\"");
  }

  @Test
  void missing_filtered_key_omitted_from_output() {
    Map<String, ContextValue> context = Map.of("name", new StringContextValue("world"));

    String json = renderer.render(context, new ContextMapping(List.of("other"), List.of()));

    assertThat(json).isEqualTo("{}");
  }

  @Test
  void list_context_value_renders_as_json_array() {
    Map<String, ContextValue> context = Map.of(
        "items",
        new ContextValueList(List.of(new StringContextValue("a"), new StringContextValue("b"))));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\"items\":[\"a\",\"b\"]");
  }

  @Test
  void nested_json_context_value_renders_parsed() {
    Map<String, ContextValue> context = Map.of(
        "payload", new JsonContextValue("{\"nested\":true}"));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\"nested\":true");
  }

  @Test
  void number_and_boolean_context_values_render() {
    Map<String, ContextValue> context = Map.of(
        "count", new NumberContextValue(42),
        "ok", new BooleanContextValue(true));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\"count\":42");
    assertThat(json).contains("\"ok\":true");
  }

  @Test
  void special_characters_are_not_escaped_beyond_json_encoding() {
    Map<String, ContextValue> context = Map.of(
        "msg", new StringContextValue("line1\n\"quoted\""));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\\n");
    assertThat(json).contains("\\\"quoted\\\"");
  }

  @Test
  void invalid_json_context_value_renders_as_text_node() {
    Map<String, ContextValue> context = Map.of(
        "payload", new JsonContextValue("not-json{"));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\"payload\":\"not-json{\"");
  }
}
