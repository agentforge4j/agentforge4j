// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.testkit.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.PolicyDecision;
import com.agentforge4j.core.spi.tool.ToolPolicy;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.testkit.scenario.ScenarioScriptLoader;
import com.agentforge4j.testkit.tool.ScriptedToolProvider;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link WorkflowTestHarness.Builder} configuration and validation: each setter
 * rejects {@code null}, and {@code build()} enforces the {@code shippedCatalog}/{@code workflowsDir}
 * either-or / mutual-exclusion invariants.
 */
class WorkflowTestHarnessBuilderTest {

  private static final FakeScript SCRIPT =
      new ScenarioScriptLoader().fromJson("{ \"schemaVersion\": 1, \"responses\": [] }");

  @Test
  void buildRequiresEitherShippedCatalogOrWorkflowsDir() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().script(SCRIPT).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shippedCatalog");
  }

  @Test
  void buildRejectsBothShippedCatalogAndWorkflowsDir() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder()
        .script(SCRIPT)
        .shippedCatalog(true)
        .workflowsDir(Path.of("workflows"))
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutually exclusive");
  }

  @Test
  void buildsWithShippedCatalog() {
    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .shippedCatalog(true)
        .script(SCRIPT)
        .build();

    assertThat(harness).isNotNull();
  }

  @Test
  void buildsWithAllOptionalSettings() {
    ToolPolicy allowAll = (cmd, descriptor, ctx) -> new PolicyDecision.Allow();

    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .workflowsDir(Path.of("workflows"))
        .agentsDir(Path.of("agents"))
        .script(SCRIPT)
        .clock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        .fileSinkDir(Path.of("out"))
        .toolProviders(List.of(ScriptedToolProvider.succeeding("p", "c", "out")))
        .toolPolicy(allowAll)
        .build();

    assertThat(harness).isNotNull();
  }

  @Test
  void workflowsDirRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().workflowsDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void agentsDirRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().agentsDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void scriptRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().script(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void clockRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().clock(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fileSinkDirRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().fileSinkDir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toolProvidersRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().toolProviders(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toolPolicyRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().toolPolicy(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void collectionAuthorizerRejectsNull() {
    assertThatThrownBy(() -> WorkflowTestHarness.builder().collectionAuthorizer(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void buildsWithCollectionAuthorizer() {
    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .shippedCatalog(true)
        .script(SCRIPT)
        .collectionAuthorizer(FakeCollectionAuthorizer.allowAll())
        .build();

    assertThat(harness).isNotNull();
  }
}
