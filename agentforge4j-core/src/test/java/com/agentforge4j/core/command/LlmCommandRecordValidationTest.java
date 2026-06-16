// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.core.command;

import com.agentforge4j.core.workflow.artifact.ArtifactItem;
import com.agentforge4j.core.workflow.artifact.TextArtifactItem;
import com.agentforge4j.core.workflow.context.StringContextValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers canonical constructor validation on command records (no Jackson).
 */
class LlmCommandRecordValidationTest {

  @Test
  void create_file_rejects_blank_path() {
    assertThatThrownBy(() -> new CreateFileCommand("  ", "c"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  @Test
  void create_file_rejects_null_content() {
    assertThatThrownBy(() -> new CreateFileCommand("/p", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content");
  }

  @Test
  void user_prompt_rejects_blank_message() {
    assertThatThrownBy(() -> new UserPromptCommand("\t", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  void run_command_rejects_blank_command_string() {
    assertThatThrownBy(() -> new RunCommandCommand(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command");
  }

  @Test
  void escalate_rejects_blank_reason() {
    assertThatThrownBy(() -> new EscalateCommand(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void set_context_rejects_blank_key() {
    assertThatThrownBy(() -> new SetContextCommand(" ", new StringContextValue("v")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
  }

  @Test
  void set_context_rejects_null_value() {
    assertThatThrownBy(() -> new SetContextCommand("k", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value");
  }

  @Test
  void generate_questions_rejects_empty_list() {
    assertThatThrownBy(() -> new GenerateQuestionsCommand(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("questions");
  }

  @Test
  void generate_questions_defensively_copies_question_list() {
    var q = new TextArtifactItem("id", "label", false, null);
    java.util.ArrayList<ArtifactItem> list = new java.util.ArrayList<>(List.of(q));
    var cmd = new GenerateQuestionsCommand(list);
    list.clear();
    assertThat(cmd.questions()).containsExactly(q);
  }
}
