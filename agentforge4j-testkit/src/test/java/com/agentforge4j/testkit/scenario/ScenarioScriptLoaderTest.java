// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.llm.fake.FakeScriptParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioScriptLoaderTest {

  private static final String VALID_SCRIPT = """
      {
        "schemaVersion": 1,
        "responses": [
          {
            "workflowId": "wf",
            "stepId": "s",
            "agentId": "a",
            "ordinal": 0,
            "responseText": "[{\\"type\\":\\"COMPLETE\\"}]"
          }
        ]
      }
      """;

  @Test
  void parsesValidScriptFromJson() {
    FakeScript script = new ScenarioScriptLoader().fromJson(VALID_SCRIPT);

    assertThat(script.schemaVersion()).isEqualTo(1);
    assertThat(script.responses()).hasSize(1);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> new ScenarioScriptLoader().fromJson("{ not json"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankJson() {
    assertThatThrownBy(() -> new ScenarioScriptLoader().fromJson("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesScriptFromPath(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("script.json");
    Files.writeString(file, VALID_SCRIPT, StandardCharsets.UTF_8);

    FakeScript script = new ScenarioScriptLoader().fromPath(file);

    assertThat(script.responses()).hasSize(1);
  }

  @Test
  void fromPathRejectsNull() {
    assertThatThrownBy(() -> new ScenarioScriptLoader().fromPath(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromPathWrapsMissingFile(@TempDir Path dir) {
    Path missing = dir.resolve("absent.json");

    assertThatThrownBy(() -> new ScenarioScriptLoader().fromPath(missing))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void parsesScriptFromClasspath() {
    FakeScript script =
        new ScenarioScriptLoader().fromClasspath("/scenarios/classpath-script.json");

    assertThat(script.responses()).hasSize(1);
  }

  @Test
  void fromClasspathRejectsMissingResource() {
    assertThatThrownBy(() -> new ScenarioScriptLoader().fromClasspath("/scenarios/absent.json"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromClasspathRejectsBlankResource() {
    assertThatThrownBy(() -> new ScenarioScriptLoader().fromClasspath("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void honoursInjectedParser() {
    FakeScript script = new ScenarioScriptLoader(new FakeScriptParser()).fromJson(VALID_SCRIPT);

    assertThat(script.schemaVersion()).isEqualTo(1);
  }
}
