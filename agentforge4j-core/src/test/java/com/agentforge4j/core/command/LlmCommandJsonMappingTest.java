// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.BooleanContextValue;
import com.agentforge4j.core.workflow.context.ContextValueList;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.context.NumberContextValue;
import com.agentforge4j.core.workflow.context.StringContextValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises Jackson polymorphic mapping for {@link LlmCommand} and nested DTOs (no HTTP).
 */
class LlmCommandJsonMappingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserialize_user_prompt() throws Exception {
    String json = """
        {"type":"USER_PROMPT","message":"Hello","responseRequired":true}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(UserPromptCommand.class, c -> {
      assertThat(c.message()).isEqualTo("Hello");
      assertThat(c.responseRequired()).isTrue();
    });
  }

  @Test
  void deserialize_user_prompt_response_required_defaults_to_false_when_absent() throws Exception {
    String json = """
        {"type":"USER_PROMPT","message":"Hello"}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(UserPromptCommand.class, c -> assertThat(c.responseRequired()).isFalse());
  }

  @Test
  void deserialize_create_file_accepts_file_path_alias() throws Exception {
    String json = """
        {"type":"CREATE_FILE","filePath":"/tmp/a.txt","content":"body"}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(CreateFileCommand.class, c -> {
      assertThat(c.path()).isEqualTo("/tmp/a.txt");
      assertThat(c.content()).isEqualTo("body");
    });
  }

  @Test
  void deserialize_complete_with_and_without_summary() throws Exception {
    assertThat(mapper.readValue("{\"type\":\"COMPLETE\"}", LlmCommand.class))
        .isInstanceOfSatisfying(CompleteCommand.class, c -> assertThat(c.summary()).isNull());

    assertThat(mapper.readValue("{\"type\":\"COMPLETE\",\"summary\":\"ok\"}", LlmCommand.class))
        .isInstanceOfSatisfying(CompleteCommand.class, c -> assertThat(c.summary()).isEqualTo("ok"));
  }

  @Test
  void deserialize_continue() throws Exception {
    LlmCommand cmd = mapper.readValue("{\"type\":\"CONTINUE\"}", LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(ContinueCommand.class, c -> {
      assertThat(c.wantsAnotherRound()).isNull();
      assertThat(c.reason()).isNull();
      assertThat(c.unresolvedConcerns()).isNull();
    });
  }

  @Test
  void deserialize_continue_with_spar_continuation_fields() throws Exception {
    String json = """
        {"type":"CONTINUE","wantsAnotherRound":true,"reason":"Evidence is missing for the latency claim.",
        "unresolvedConcerns":["SLO vs measured p99","Retry policy unclear"]}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(ContinueCommand.class, c -> {
      assertThat(c.wantsAnotherRound()).isTrue();
      assertThat(c.reason()).contains("latency");
      assertThat(c.unresolvedConcerns()).containsExactly("SLO vs measured p99", "Retry policy unclear");
    });
  }

  @Test
  void deserialize_run_command() throws Exception {
    LlmCommand cmd = mapper.readValue(
        "{\"type\":\"RUN_COMMAND\",\"command\":\"echo hi\"}",
        LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(RunCommandCommand.class, c -> assertThat(c.command()).isEqualTo("echo hi"));
  }

  @Test
  void deserialize_escalate() throws Exception {
    LlmCommand cmd = mapper.readValue(
        "{\"type\":\"ESCALATE\",\"reason\":\"blocked\"}",
        LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(EscalateCommand.class, c -> assertThat(c.reason()).isEqualTo("blocked"));
  }

  @Test
  void deserialize_set_context_with_string_value() throws Exception {
    String json = """
        {"type":"SET_CONTEXT","key":"k","value":{"type":"STRING","value":"v"}}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(SetContextCommand.class, c -> {
      assertThat(c.key()).isEqualTo("k");
      assertThat(c.value()).isNotNull();
    });
  }

  @Test
  void deserialize_set_context_with_numeric_boolean_json_and_list_values() throws Exception {
    LlmCommand numeric = mapper.readValue(
        """
            {"type":"SET_CONTEXT","key":"n","value":{"type":"NUMBER","value":7}}
            """,
        LlmCommand.class);
    assertThat(numeric).isInstanceOfSatisfying(SetContextCommand.class, c -> {
      assertThat(c.value()).isInstanceOf(NumberContextValue.class);
      assertThat(((NumberContextValue) c.value()).value().intValue()).isEqualTo(7);
    });

    assertThat(mapper.readValue(
        """
            {"type":"SET_CONTEXT","key":"b","value":{"type":"BOOLEAN","value":false}}
            """,
        LlmCommand.class))
        .isInstanceOfSatisfying(SetContextCommand.class, c -> assertThat(c.value()).isEqualTo(new BooleanContextValue(false)));

    assertThat(mapper.readValue(
        """
            {"type":"SET_CONTEXT","key":"j","value":{"type":"JSON","json":"[]"}}
            """,
        LlmCommand.class))
        .isInstanceOfSatisfying(SetContextCommand.class, c -> assertThat(c.value()).isEqualTo(new JsonContextValue("[]")));

    assertThat(mapper.readValue(
        """
            {"type":"SET_CONTEXT","key":"l","value":{"type":"LIST","values":[
              {"type":"STRING","value":"a"}
            ]}}
            """,
        LlmCommand.class))
        .isInstanceOfSatisfying(SetContextCommand.class, c -> assertThat(c.value())
            .isEqualTo(new ContextValueList(List.of(new StringContextValue("a")))));
  }

  @Test
  void deserialize_generate_questions() throws Exception {
    String json = """
        {"type":"GENERATE_QUESTIONS","questions":[
          {"type":"TEXT","id":"q1","label":"Name","required":true,"hint":"?"}
        ]}
        """;
    LlmCommand cmd = mapper.readValue(json, LlmCommand.class);
    assertThat(cmd).isInstanceOfSatisfying(GenerateQuestionsCommand.class, c -> {
      assertThat(c.questions()).hasSize(1);
      assertThat(c.questions().get(0)).isInstanceOfSatisfying(TextArtifactItem.class, q -> {
        assertThat(q.id()).isEqualTo("q1");
        assertThat(q.label()).isEqualTo("Name");
      });
    });
  }

  @Test
  void deserialize_command_array() throws Exception {
    String json = """
        [{"type":"USER_PROMPT","message":"m","responseRequired":false},{"type":"COMPLETE"}]
        """;
    List<LlmCommand> cmds = mapper.readValue(json, new TypeReference<>() {
    });
    assertThat(cmds).hasSize(2);
    assertThat(cmds.get(0)).isInstanceOf(UserPromptCommand.class);
    assertThat(cmds.get(1)).isInstanceOf(CompleteCommand.class);
  }

  @Test
  void unknown_type_id_fails_during_deserialization() {
    assertThatThrownBy(() -> mapper.readValue("{\"type\":\"NOT_REAL\"}", LlmCommand.class))
        .isInstanceOf(InvalidTypeIdException.class);
  }

  @Test
  void blank_message_fails_compact_constructor_after_json_binding() {
    assertThatThrownBy(() -> mapper.readValue(
        "{\"type\":\"USER_PROMPT\",\"message\":\"  \",\"responseRequired\":false}",
        LlmCommand.class))
        .isInstanceOf(JsonMappingException.class)
        .satisfies(ex -> {
          boolean found = false;
          for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof IllegalArgumentException && t.getMessage() != null
                && t.getMessage().contains("UserPromptCommand")) {
              found = true;
              break;
            }
          }
          assertThat(found).as("expected UserPromptCommand validation in cause chain").isTrue();
        });
  }

  @Test
  void round_trip_polymorphic_command() throws Exception {
    LlmCommand original = new UserPromptCommand("text", true);
    String json = mapper.writeValueAsString(original);
    LlmCommand read = mapper.readValue(json, LlmCommand.class);
    assertThat(read).isEqualTo(original);
  }
}
