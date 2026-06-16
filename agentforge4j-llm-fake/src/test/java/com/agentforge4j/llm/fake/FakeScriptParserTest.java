// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.llm.fake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeScriptParserTest {

  private final FakeScriptParser parser = new FakeScriptParser();

  @Test
  void parsesValidScript_withExplicitAndOmittedTokenUsage() {
    String json = """
        {
          "schemaVersion": 1,
          "responses": [
            {
              "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 0,
              "responseText": "first",
              "tokenUsage": { "inputTokens": 10, "outputTokens": 5,
                              "cachedInputTokens": null, "cacheWriteTokens": null }
            },
            {
              "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 2,
              "responseText": "third"
            }
          ]
        }
        """;

    FakeScript script = parser.parse(json);

    assertThat(script.schemaVersion()).isEqualTo(1);
    assertThat(script.responses()).hasSize(2);
    assertThat(script.responses().get(new FakeScriptKey("wf", "s1", "a1", 0)))
        .isEqualTo(new FakeResponse("first", new FakeTokenUsage(10, 5, null, null)));
    // ordinal gap (1 missing) is allowed at parse time
    assertThat(script.responses().get(new FakeScriptKey("wf", "s1", "a1", 2)))
        .isEqualTo(new FakeResponse("third", null));
  }

  @Test
  void rejectsMissingRequiredField_viaSchema() {
    String json = """
        {
          "schemaVersion": 1,
          "responses": [
            { "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 0 }
          ]
        }
        """;

    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schema validation");
  }

  @Test
  void rejectsDuplicateKey() {
    String json = """
        {
          "schemaVersion": 1,
          "responses": [
            { "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 0, "responseText": "x" },
            { "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 0, "responseText": "y" }
          ]
        }
        """;

    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate fake script key");
  }

  @Test
  void rejectsUnsupportedSchemaVersion() {
    String json = """
        {
          "schemaVersion": 2,
          "responses": [
            { "workflowId": "wf", "stepId": "s1", "agentId": "a1", "ordinal": 0, "responseText": "x" }
          ]
        }
        """;

    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> parser.parse("{ not json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Malformed");
  }
}
