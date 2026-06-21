// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.config.loader.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Verifies the shipped workflow locator resolves bundle resources from the classpath after moving
 * into {@code config-loader} and switching to {@link ClassLoader}-based lookup. Backed by the
 * synthetic {@code shipped-workflows/} fixture on the test classpath.
 */
class WorkflowBundleLocatorTest {

  @Test
  void shippedWorkflowIds_listsTheClasspathFixture() {
    assertThat(WorkflowBundleLocator.shippedWorkflowIds()).contains("loader-fixture");
  }

  @Test
  void locateWorkflowJson_returnsUrlForShippedFixture() {
    assertThat(WorkflowBundleLocator.locateWorkflowJson("loader-fixture")).isNotNull();
  }

  @Test
  void locateWorkflowJson_throwsForUnknownWorkflow() {
    assertThatThrownBy(() -> WorkflowBundleLocator.locateWorkflowJson("does-not-exist"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped workflow resource");
  }

  @Test
  void retrieveWorkflowBundleFiles_returnsIndexedEntries() {
    assertThat(WorkflowBundleLocator.retrieveWorkflowBundleFiles("loader-fixture"))
        .contains("agents/loader-fixture-agent.agent");
  }

  @Test
  void retrieveWorkflowBundleFiles_emptyWhenNoBundleIndex() {
    assertThat(WorkflowBundleLocator.retrieveWorkflowBundleFiles("does-not-exist")).isEmpty();
  }

  @Test
  void workflowPath_buildsClasspathPrefixWithoutLeadingSlash() {
    assertThat(WorkflowBundleLocator.workflowPath("loader-fixture"))
        .isEqualTo("shipped-workflows/loader-fixture.workflow/");
  }

  @Test
  void workflowPath_rejectsPathTraversalInId() {
    assertThatThrownBy(() -> WorkflowBundleLocator.workflowPath("../evil"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path traversal");
  }

  @Test
  void workflowPath_rejectsPathSeparatorInId() {
    assertThatThrownBy(() -> WorkflowBundleLocator.workflowPath("evil/sub"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path separators");
  }

  @Test
  void locateWorkflowJson_rejectsTraversalId() {
    // The guard fires through every id-to-path entry point, not only workflowPath.
    assertThatThrownBy(() -> WorkflowBundleLocator.locateWorkflowJson("../../escape"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path traversal");
  }
}
