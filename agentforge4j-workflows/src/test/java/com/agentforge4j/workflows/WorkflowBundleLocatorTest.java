package com.agentforge4j.workflows;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowBundleLocatorTest {

  @Test
  void shippedWorkflowIds_loadsIdsFromClasspathIndex() {
    assertThat(WorkflowBundleLocator.shippedWorkflowIds())
        .contains("agent-creator", "workflow-generator", "recruitment");
  }

  @Test
  void locateWorkflowJson_returnsExistingResourceForKnownWorkflow() {
    assertThat(WorkflowBundleLocator.locateWorkflowJson("agent-creator"))
        .isNotNull();
  }

  @Test
  void retrieveWorkflowBundleFiles_returnsIndexedEntriesForKnownWorkflow() {
    List<String> bundleFiles = WorkflowBundleLocator.retrieveWorkflowBundleFiles("agent-creator");

    assertThat(bundleFiles)
        .isNotEmpty()
        .anyMatch(path -> path.endsWith("/agent-creator.workflow/agent-requirements.artifact.json"))
        .anyMatch(path -> path.endsWith("/agent-creator.workflow/agents/agent-creator-agent.agent/agent.json"));
  }

  @Test
  void retrieveWorkflowBundleFiles_returnsEmptyListWhenNoIndexExists() {
    assertThat(WorkflowBundleLocator.retrieveWorkflowBundleFiles("unknown-workflow"))
        .isEmpty();
  }

  @Test
  void openBundleResource_readsExistingClasspathResource() throws IOException {
    try (var stream = WorkflowBundleLocator.openBundleResource(
        "/shipped-workflows/agent-creator.workflow/workflow.json")) {
      String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(json).contains("\"id\": \"agent-creator\"");
    }
  }

  @Test
  void openBundleResource_throwsForMissingResource() {
    assertThatThrownBy(() -> WorkflowBundleLocator.openBundleResource(
        "/shipped-workflows/agent-creator.workflow/does-not-exist.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped workflow bundle resource");
  }
}
