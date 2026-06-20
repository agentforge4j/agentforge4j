// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.verification.negative;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentforge4j.core.spi.tool.CapabilityResolutionException;
import com.agentforge4j.core.spi.tool.ToolProvider;
import com.agentforge4j.llm.fake.FakeScript;
import com.agentforge4j.testkit.harness.WorkflowTestHarness;
import com.agentforge4j.testkit.tool.ScriptedToolProvider;
import com.agentforge4j.verification.support.Fixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tier 8 — tool-resolution fail-fast. Two providers exposing the same capability id must be rejected
 * fail-fast when the runtime is assembled, before any agent or tool invocation occurs.
 */
class DuplicateCapabilityTest {

  @Test
  void duplicateCapabilityAcrossProvidersFailsFastAtAssembly() {
    ToolProvider first = ScriptedToolProvider.succeeding("provider-a", "test.echo", "{\"ok\":true}");
    ToolProvider second = ScriptedToolProvider.succeeding("provider-b", "test.echo", "{\"ok\":true}");

    WorkflowTestHarness harness = WorkflowTestHarness.builder()
        .workflowsDir(Fixtures.dir("/fixtures/negative/workflows"))
        .agentsDir(Fixtures.dir("/fixtures/negative/agents"))
        .script(new FakeScript(1, Map.of()))
        .toolProviders(List.of(first, second))
        .build();

    // Assembly (bootstrap.build()) happens inside run(); the collision throws before the run starts.
    assertThatThrownBy(() -> harness.run("negative-run"))
        .isInstanceOf(CapabilityResolutionException.class)
        .hasMessageContaining("test.echo")
        .hasMessageContaining("ambiguous");
  }
}
