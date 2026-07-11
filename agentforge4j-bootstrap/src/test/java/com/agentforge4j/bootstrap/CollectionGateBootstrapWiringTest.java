// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.CollectionGateRuntime;
import com.agentforge4j.core.runtime.CollectionSubmission;
import com.agentforge4j.core.runtime.SubmissionResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.CollectionPayload;
import com.agentforge4j.core.workflow.collection.CollectionSubmissionValidator;
import com.agentforge4j.core.workflow.collection.Decision;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that {@code AgentForge4jBootstrap.Builder.withCollectionItemSchemasDir(Path)} and
 * {@code AgentForge4jBootstrap.Builder.withCollectionSubmissionValidator(CollectionSubmissionValidator)}
 * actually wire through to a working runtime end to end, through the public bootstrap surface —
 * not merely in isolation against {@code FileSystemCollectionItemSchemaValidator} directly.
 */
class CollectionGateBootstrapWiringTest {

  private static final String STEP = "cv-intake";
  private static final String ACTOR = "recruiter-1";

  private static final String CV_SCHEMA = """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "required": ["name", "years"],
        "additionalProperties": false,
        "properties": {
          "name": { "type": "string", "minLength": 1 },
          "years": { "type": "integer", "minimum": 0 }
        }
      }
      """;

  @Test
  void withCollectionItemSchemasDirEnforcesTheDeclaredSchemaEndToEnd(@TempDir Path schemasDir)
      throws IOException {
    Files.writeString(schemasDir.resolve("cv-item.schema.json"), CV_SCHEMA);
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow("cv-item"))))
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withCollectionItemSchemasDir(schemasDir)
        .build();
    CollectionGateRuntime gate = af.runtime().collections();
    String runId = af.start("wf");

    SubmissionResult accepted = gate.submitItem(runId, STEP,
        inlineSubmission("{\"name\":\"Ada\",\"years\":12}"), ACTOR);
    assertThat(accepted.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    SubmissionResult rejected = gate.submitItem(runId, STEP,
        inlineSubmission("{\"name\":\"\",\"years\":-1}"), ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).startsWith("ITEM_SCHEMA_INVALID");
  }

  @Test
  void withCollectionSubmissionValidatorEnforcesApplicationPolicyEndToEnd() {
    CollectionSubmissionValidator rejectsBlockedActors = context ->
        "blocked-actor".equals(context.actorId())
            ? Decision.deny("actor is blocked")
            : Decision.allow();
    AgentForge4j af = AgentForge4jBootstrap.defaults()
        .withWorkflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow(null))))
        .withLoadShippedAgents(false)
        .withLoadShippedWorkflows(false)
        .withCollectionSubmissionValidator(rejectsBlockedActors)
        .build();
    CollectionGateRuntime gate = af.runtime().collections();
    String runId = af.start("wf");

    SubmissionResult allowed = gate.submitItem(runId, STEP, inlineSubmission("{\"v\":\"a\"}"), ACTOR);
    assertThat(allowed.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    SubmissionResult denied = gate.submitItem(runId, STEP, inlineSubmission("{\"v\":\"b\"}"),
        "blocked-actor");
    assertThat(denied.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(denied.reason()).contains("actor is blocked");
  }

  private static CollectionSubmission inlineSubmission(String inlineJson) {
    return new CollectionSubmission(new CollectionPayload(inlineJson, List.of()), null, null);
  }

  private static WorkflowDefinition workflow(String itemSchemaRef) {
    CollectionBehaviour behaviour = new CollectionBehaviour(itemSchemaRef, 0, null, null, 0,
        DuplicatePolicy.ALLOW, ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW,
        true, false, ReopenPolicy.NONE, AuthorizationMode.OPEN, StepTransition.AUTO);
    StepDefinition step = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(behaviour)
        .build();
    return new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step), List.of());
  }
}
