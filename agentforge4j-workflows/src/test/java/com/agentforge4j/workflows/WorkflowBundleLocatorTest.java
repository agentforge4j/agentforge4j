package com.agentforge4j.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowBundleLocatorTest {

  @Test
  void shippedWorkflowIds_loadsIdsFromClasspathIndex() {
    assertThat(WorkflowBundleLocator.shippedWorkflowIds())
        .contains("agent-creator", "workflow-generator", "recruitment");
  }

  @Test
  void shippedWorkflowIds_returnsImmutableCopy() {
    List<String> shippedWorkflowIds = WorkflowBundleLocator.shippedWorkflowIds();

    assertThatThrownBy(() -> shippedWorkflowIds.add("should-fail"))
        .isInstanceOf(UnsupportedOperationException.class);
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
        .anyMatch(path -> path.endsWith("agent-requirements.artifact.json"))
        .anyMatch(path -> path.endsWith("agents/agent-creator-agent.agent"));
  }

  @Test
  void retrieveWorkflowBundleFiles_returnsEmptyListWhenNoIndexExists() {
    assertThat(WorkflowBundleLocator.retrieveWorkflowBundleFiles("unknown-workflow"))
        .isEmpty();
  }

  @Test
  void openBundleResource_readsExistingClasspathResource() throws IOException {
    try (var stream = WorkflowBundleLocator.openBundleResource(
        "/shipped-workflows/agent-creator.workflow/workflow.json", "agent-creator.workflow")) {
      String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(json).contains("\"id\": \"agent-creator\"");
    }
  }

  @Test
  void openBundleResource_throwsForMissingResource() {
    assertThatThrownBy(() -> WorkflowBundleLocator.openBundleResource(
        "/shipped-workflows/agent-creator.workflow/does-not-exist.json", "agent-creator"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped workflow bundle resource");
  }

  @Test
  void locateWorkflowJson_throwsForUnknownWorkflow() {
    assertThatThrownBy(() -> WorkflowBundleLocator.locateWorkflowJson("no-such-workflow"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing shipped workflow resource");
  }
}
