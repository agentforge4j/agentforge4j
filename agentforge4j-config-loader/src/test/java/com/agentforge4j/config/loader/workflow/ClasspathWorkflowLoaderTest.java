package com.agentforge4j.config.loader.workflow;

import com.agentforge4j.config.loader.WorkflowDirectoryLoad;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathWorkflowLoaderTest {

  @Test
  void loadWorkflows_exposesBundledAgentsOutsideWorkflowDefinition() {
    ObjectMapper objectMapper = new ObjectMapper();
    ClasspathWorkflowLoader loader = new ClasspathWorkflowLoader(objectMapper);

    WorkflowDirectoryLoad loaded = loader.loadWorkflows();

    assertThat(loaded.workflows()).containsKeys("agent-creator", "workflow-generator");

    WorkflowDefinition agentCreator = loaded.workflows().get("agent-creator");
    assertThat(agentCreator).isNotNull();
    assertThat(loaded.bundledAgents()).containsKey("agent-creator-agent");
    assertThat(loaded.bundledAgents().get("agent-creator-agent").supportedCommands())
        .containsExactly("CREATE_FILE", "USER_PROMPT", "COMPLETE");

    WorkflowDefinition workflowGenerator = loaded.workflows().get("workflow-generator");
    assertThat(workflowGenerator).isNotNull();
    assertThat(loaded.bundledAgents()).containsKeys(
        "workflow-conversation-agent", "workflow-designer-agent");

    // Artifacts and blueprints come from bundle files listed in each workflow's index, not from
    // embedded fields in workflow.json.
    assertThat(agentCreator.artifacts()).containsKey("agent-requirements");
    assertThat(agentCreator.blueprints()).isEmpty();

    WorkflowDefinition recruitment = loaded.workflows().get("recruitment");
    assertThat(recruitment).isNotNull();
    assertThat(recruitment.artifacts()).containsKeys(
        "initial-role-prompt", "cv-upload", "assessment-submission");
    assertThat(recruitment.blueprints()).containsKeys(
        "intake-loop", "cv-collection-loop", "assessment-per-candidate");

    WorkflowDefinition applicationDelivery = loaded.workflows().get("application-delivery");
    assertThat(applicationDelivery).isNotNull();
    assertThat(applicationDelivery.artifacts()).containsKey("app-idea");
    assertThat(applicationDelivery.blueprints()).containsKeys(
        "po-refinement-loop", "epic-loop-blueprint");

    WorkflowDefinition epicImplementation = loaded.workflows().get("epic-implementation");
    assertThat(epicImplementation).isNotNull();
    assertThat(epicImplementation.blueprints()).containsKey("mark-epic-failed-blueprint");

    WorkflowDefinition costEstimator = loaded.workflows().get("workflow-cost-estimator");
    assertThat(costEstimator).isNotNull();
    assertThat(costEstimator.artifacts()).containsKey("estimation-input");
  }
}
