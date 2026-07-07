// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Loads the synthetic shipped-workflow fixture on the test classpath through the classpath loader,
 * proving the classloader-based locator and bundle loading still work after the locators moved into
 * {@code config-loader}. Conformance of the real shipped catalog (agent-creator) is
 * verified in the workflow catalog module, not here.
 */
class ClasspathWorkflowLoaderTest {

  @Test
  void loadWorkflows_loadsSyntheticShippedFixtureWithBundledAgent() {
    ClasspathWorkflowLoader loader = new ClasspathWorkflowLoader(new ObjectMapper());

    WorkflowDirectoryLoad loaded = loader.loadWorkflows();

    assertThat(loaded.workflows()).containsKey("loader-fixture");
    WorkflowDefinition fixture = loaded.workflows().get("loader-fixture");
    assertThat(fixture).isNotNull();
    assertThat(fixture.steps()).hasSize(1);
    assertThat(loaded.bundledAgents()).containsKey("loader-fixture-agent");
  }
}
