// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime.llm;

import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.ContextProvenance;
import com.agentforge4j.core.workflow.context.ContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextRendererTest {

  private static final String ENVELOPE = ContextRenderer.UNTRUSTED_USER_INPUT_KEY;

  private final ObjectMapper mapper = new ObjectMapper();
  private final ContextRenderer renderer = new ContextRenderer(mapper);

  private JsonNode render(Map<String, ContextValue> context, ContextMapping mapping) throws Exception {
    return mapper.readTree(renderer.render(context, mapping));
  }

  @Test
  void trusted_entry_renders_at_root_with_unchanged_path() throws Exception {
    Map<String, ContextValue> context = Map.of(
        "design", new StringContextValue("approved", ContextProvenance.SYSTEM_GENERATED));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.get("design").asText()).isEqualTo("approved");
    assertThat(out.get(ENVELOPE).isObject()).isTrue();
    assertThat(out.get(ENVELOPE).has("design")).isFalse();
  }

  @Test
  void untrusted_entries_isolated_under_envelope() throws Exception {
    Map<String, ContextValue> context = Map.of(
        "user.response.s1", new StringContextValue("hi", ContextProvenance.USER_SUPPLIED),
        "tool.search", new StringContextValue("result", ContextProvenance.EXTERNAL_TOOL));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.has("user.response.s1")).isFalse();
    assertThat(out.has("tool.search")).isFalse();
    assertThat(out.get(ENVELOPE).get("user.response.s1").asText()).isEqualTo("hi");
    assertThat(out.get(ENVELOPE).get("tool.search").asText()).isEqualTo("result");
  }

  @Test
  void mixed_trusted_at_root_untrusted_nested() throws Exception {
    Map<String, ContextValue> context = Map.of(
        "trusted", new StringContextValue("t", ContextProvenance.LLM_GENERATED),
        "untrusted", new StringContextValue("u", ContextProvenance.USER_SUPPLIED));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.get("trusted").asText()).isEqualTo("t");
    assertThat(out.get(ENVELOPE).get("untrusted").asText()).isEqualTo("u");
    assertThat(out.get(ENVELOPE).has("trusted")).isFalse();
  }

  @Test
  void empty_render_still_emits_envelope() {
    Map<String, ContextValue> context = Map.of(
        "name", new StringContextValue("world", ContextProvenance.USER_SUPPLIED));

    String json = renderer.render(context, new ContextMapping(List.of("other"), List.of()));

    assertThat(json).isEqualTo("{\"%s\":{}}".formatted(ENVELOPE));
  }

  @Test
  void reserved_key_collision_with_trusted_key_fails_fast() {
    Map<String, ContextValue> context = Map.of(
        ENVELOPE, new StringContextValue("oops", ContextProvenance.SYSTEM_GENERATED));

    assertThatThrownBy(() -> renderer.render(context, ContextMapping.none()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ENVELOPE)
        .hasMessageContaining("reserved");
  }

  @Test
  void untrusted_key_named_like_envelope_nests_without_collision() throws Exception {
    Map<String, ContextValue> context = Map.of(
        ENVELOPE, new StringContextValue("data", ContextProvenance.USER_SUPPLIED));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.get(ENVELOPE).get(ENVELOPE).asText()).isEqualTo("data");
  }

  @Test
  void value_types_render_under_correct_partition() throws Exception {
    Map<String, ContextValue> context = Map.of(
        "count", new NumberContextValue(42, ContextProvenance.SYSTEM_GENERATED),
        "ok", new BooleanContextValue(true, ContextProvenance.SYSTEM_GENERATED),
        "payload", new JsonContextValue("{\"nested\":true}", ContextProvenance.SYSTEM_GENERATED),
        "items", new ContextValueList(
            List.of(new StringContextValue("a", ContextProvenance.USER_SUPPLIED),
                new StringContextValue("b", ContextProvenance.USER_SUPPLIED)),
            ContextProvenance.USER_SUPPLIED));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.get("count").asInt()).isEqualTo(42);
    assertThat(out.get("ok").asBoolean()).isTrue();
    assertThat(out.get("payload").get("nested").asBoolean()).isTrue();
    // The list is untrusted -> nested, rendered as a JSON array.
    assertThat(out.get(ENVELOPE).get("items").isArray()).isTrue();
    assertThat(out.get(ENVELOPE).get("items").get(0).asText()).isEqualTo("a");
  }

  @Test
  void list_partition_follows_container_provenance_regardless_of_element_provenance() throws Exception {
    // Untrusted container, trusted-looking elements -> nested under the envelope.
    ContextValueList untrustedContainer = new ContextValueList(
        List.of(new StringContextValue("x", ContextProvenance.SYSTEM_GENERATED)),
        ContextProvenance.USER_SUPPLIED);
    // Trusted container, untrusted elements -> stays at the root.
    ContextValueList trustedContainer = new ContextValueList(
        List.of(new StringContextValue("y", ContextProvenance.USER_SUPPLIED)),
        ContextProvenance.SYSTEM_GENERATED);

    JsonNode out = render(Map.of("u", untrustedContainer, "t", trustedContainer), ContextMapping.none());

    assertThat(out.has("u")).isFalse();
    assertThat(out.get(ENVELOPE).get("u").isArray()).isTrue();
    assertThat(out.get("t").isArray()).isTrue();
    assertThat(out.get(ENVELOPE).has("t")).isFalse();
  }

  @Test
  void special_characters_are_not_escaped_beyond_json_encoding() {
    Map<String, ContextValue> context = Map.of(
        "msg", new StringContextValue("line1\n\"quoted\"", ContextProvenance.USER_SUPPLIED));

    String json = renderer.render(context, ContextMapping.none());

    assertThat(json).contains("\\n");
    assertThat(json).contains("\\\"quoted\\\"");
  }

  @Test
  void invalid_json_context_value_renders_as_text_node() throws Exception {
    Map<String, ContextValue> context = Map.of(
        "payload", new JsonContextValue("not-json{", ContextProvenance.SYSTEM_GENERATED));

    JsonNode out = render(context, ContextMapping.none());

    assertThat(out.get("payload").asText()).isEqualTo("not-json{");
  }
}
