// SPDX-License-Identifier: Apache-2.0
package com.agentforge4j.runtime;

import com.agentforge4j.config.loader.repository.InMemoryWorkflowRepository;
import com.agentforge4j.core.runtime.CloseRequest;
import com.agentforge4j.core.runtime.CloseResult;
import com.agentforge4j.core.runtime.CollectionGateRuntime;
import com.agentforge4j.core.runtime.CollectionSubmission;
import com.agentforge4j.core.runtime.CollectionView;
import com.agentforge4j.core.runtime.SubmissionResult;
import com.agentforge4j.core.runtime.WorkflowRuntime;
import com.agentforge4j.core.spi.validation.CollectionItemSchemaValidator;
import com.agentforge4j.core.spi.validation.ValidationResult;
import com.agentforge4j.core.workflow.WorkflowDefinition;
import com.agentforge4j.core.workflow.WorkflowLifecycle;
import com.agentforge4j.core.workflow.WorkflowSource;
import com.agentforge4j.core.workflow.collection.AuthorizationMode;
import com.agentforge4j.core.workflow.collection.CloseReason;
import com.agentforge4j.core.workflow.collection.CollectionAction;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizationException;
import com.agentforge4j.core.workflow.collection.CollectionAuthorizer;
import com.agentforge4j.core.workflow.collection.CollectionPayload;
import com.agentforge4j.core.workflow.collection.CollectionPhase;
import com.agentforge4j.core.workflow.collection.CollectionState;
import com.agentforge4j.core.workflow.collection.CollectionSubmissionContext;
import com.agentforge4j.core.workflow.collection.CollectionSubmissionValidator;
import com.agentforge4j.core.workflow.collection.Decision;
import com.agentforge4j.core.workflow.collection.DuplicatePolicy;
import com.agentforge4j.core.workflow.collection.FileRef;
import com.agentforge4j.core.workflow.collection.ReopenPolicy;
import com.agentforge4j.core.workflow.collection.ReplacementPolicy;
import com.agentforge4j.core.workflow.collection.WithdrawalPolicy;
import com.agentforge4j.core.workflow.context.ContextMapping;
import com.agentforge4j.core.workflow.context.JsonContextValue;
import com.agentforge4j.core.workflow.event.WorkflowEvent;
import com.agentforge4j.core.workflow.event.WorkflowEventType;
import com.agentforge4j.core.workflow.repository.WorkflowRepository;
import com.agentforge4j.core.workflow.repository.WorkflowStateRepository;
import com.agentforge4j.core.workflow.requirement.RequirementScope;
import com.agentforge4j.core.workflow.requirement.ResolutionMode;
import com.agentforge4j.core.workflow.requirement.WorkflowRequirement;
import com.agentforge4j.core.workflow.state.WorkflowState;
import com.agentforge4j.core.workflow.state.WorkflowStatus;
import com.agentforge4j.core.workflow.step.StepDefinition;
import com.agentforge4j.core.workflow.step.StepTransition;
import com.agentforge4j.core.workflow.step.behaviour.CollectionBehaviour;
import com.agentforge4j.core.workflow.step.behaviour.FailBehaviour;
import com.agentforge4j.runtime.llm.AgentInvoker;
import com.agentforge4j.runtime.repository.InMemoryWorkflowEventLog;
import com.agentforge4j.runtime.repository.InMemoryWorkflowStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CollectionGateRuntimeTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC);
  private static final String STEP = "cv-intake";
  private static final String OUTPUT_KEY = "collectedCvs";
  private static final String ACTOR = "recruiter-1";

  private InMemoryWorkflowStateRepository stateRepo;
  private InMemoryWorkflowEventLog eventLog;

  // ---- happy path: open, submit, close (reopen NONE) advances and publishes -------------------

  @Test
  void opensGateOnStartAndAwaitsCollection() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    String runId = runtime.start("wf");

    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_COLLECTION);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_OPENED);
  }

  @Test
  void submitAcceptsItemAndEmitsEvent() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
    assertThat(result.submissionId()).isNotBlank();
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(1);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_SUBMITTED);
  }

  @Test
  void clientTokenMakesSubmitIdempotent() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    SubmissionResult first = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);
    SubmissionResult retry = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);

    assertThat(retry.status()).isEqualTo(SubmissionResult.Status.IDEMPOTENT);
    assertThat(retry.submissionId()).isEqualTo(first.submissionId());
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(1);
  }

  @Test
  void maxItemsRejectsAndEmitsRejectedEvent() {
    WorkflowRuntime runtime = runtime(behaviour(0, 1, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    SubmissionResult rejected = gate.submitItem(runId, STEP, submission("b", null, null), ACTOR);

    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("MAX_ITEMS: have 1, cap 1");
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_REJECTED);
  }

  @Test
  void maxItemsPerActorRejectsTheCappedActorButNotAnother() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, 1, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    SubmissionResult rejected = gate.submitItem(runId, STEP, submission("b", null, null), ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("PER_ACTOR_CAP: actor has 1, cap 1");

    SubmissionResult accepted =
        gate.submitItem(runId, STEP, submission("c", null, null), "recruiter-2");
    assertThat(accepted.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
  }

  @Test
  void oversizePayloadIsRejectedButExactCapIsAccepted() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 9, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    // {"v":"a"} is exactly 9 bytes -- at the cap, not over it, so it is accepted.
    SubmissionResult accepted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    assertThat(accepted.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    // {"v":"bb"} is 10 bytes -- one over the 9-byte cap.
    SubmissionResult rejected = gate.submitItem(runId, STEP, submission("bb", null, null), ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("OVERSIZE: payload is 10 bytes, cap 9");
  }

  @Test
  void tooManyFilesIsRejectedButExactCapIsAccepted() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, 2, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    FileRef file = new FileRef("run/a.txt", "a.txt", "text/plain", 10);

    SubmissionResult accepted = gate.submitItem(runId, STEP,
        new CollectionSubmission(new CollectionPayload(null, List.of(file, file)), null, null), ACTOR);
    assertThat(accepted.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    SubmissionResult rejected = gate.submitItem(runId, STEP,
        new CollectionSubmission(new CollectionPayload(null, List.of(file, file, file)), null, null),
        ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("MAX_FILES_PER_ITEM: item has 3 files, cap 2");
  }

  // ---- nested collection gate (declaring-workflow scope) -------------------------------------

  @Test
  void nestedCollectionGateHonorsARequirementDeclaredOnItsDeclaringWorkflowNotTheRoot() {
    // The gate's STEP_ACTION requirement is declared on the NESTED workflow that actually contains
    // the step, not on the root -- authorize() must resolve against the declaring workflow (like
    // RequirementCheckpoint does elsewhere), not always the run's root workflow.
    CollectionBehaviour cfg = behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED);
    StepDefinition step = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(cfg)
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    WorkflowRequirement submitReq = new WorkflowRequirement("req-submit", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "submit", false, "\"recruiter\"", ResolutionMode.DEFERRED);
    WorkflowDefinition nested = new WorkflowDefinition("nested-wf", "nested-wf", null, null, null,
        "1.0.0", null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(step), List.of(submitReq));
    WorkflowDefinition root = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(nested));

    eventLog = new InMemoryWorkflowEventLog();
    stateRepo = new InMemoryWorkflowStateRepository();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", root)))
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .collectionAuthorizer((actorId, stepId, action, descriptor, context) -> Decision.allow())
        .build();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
  }

  @Test
  void nestedCollectionGateSubmissionValidatorSeesTheDeclaringWorkflowIdNotTheRoot() {
    // CollectionSubmissionContext.workflowId must be scoped identically to authorize()'s
    // ResolutionContext -- both resolve against the workflow that actually declares the step, not
    // always the run's root workflow.
    CollectionBehaviour cfg = behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN);
    StepDefinition step = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(cfg)
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    WorkflowDefinition nested = new WorkflowDefinition("nested-wf", "nested-wf", null, null, null,
        "1.0.0", null, WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(step), List.of());
    WorkflowDefinition root = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(nested));

    AtomicReference<String> seenWorkflowId = new AtomicReference<>();
    CollectionSubmissionValidator submissionValidator = context -> {
      seenWorkflowId.set(context.workflowId());
      return Decision.allow();
    };

    eventLog = new InMemoryWorkflowEventLog();
    stateRepo = new InMemoryWorkflowStateRepository();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", root)))
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .collectionSubmissionValidator(submissionValidator)
        .build();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(seenWorkflowId.get()).isEqualTo("nested-wf");
  }

  @Test
  void closeBelowMinIsRejectedAndRunStaysAwaiting() {
    WorkflowRuntime runtime = runtime(behaviour(1, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult result = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));

    assertThat(result.closed()).isFalse();
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_COLLECTION);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_CLOSE_REJECTED);
  }

  @Test
  void closeAdvancesRunAndPublishesMaterializedCollection() {
    WorkflowRuntime runtime = runtime(behaviour(1, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    CloseResult result = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));

    assertThat(result.closed()).isTrue();
    assertThat(result.advanced()).isTrue();
    assertThat(result.finalCount()).isEqualTo(1);
    WorkflowState state = runtime.getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getContextValue(OUTPUT_KEY)).isPresent();
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_CLOSED);
  }

  @Test
  void naturalEmptyCollectionCloseAdvancesRunAndPublishesAnEmptyArray() {
    // minItems=0, zero submissions, a plain non-override MANUAL close -- the natural empty-close
    // path, distinct from the override-bypass tests elsewhere in this file.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult result = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));

    assertThat(result.closed()).isTrue();
    assertThat(result.advanced()).isTrue();
    assertThat(result.finalCount()).isEqualTo(0);
    WorkflowState state = runtime.getState(runId);
    assertThat(state.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(state.getContextValue(OUTPUT_KEY)).isPresent();
    JsonContextValue published = (JsonContextValue) state.getContextValue(OUTPUT_KEY).orElseThrow();
    assertThat(published.json()).isEqualTo("[]");
  }

  @Test
  void openModeDeniesOverrideRegardlessOfActor() {
    // request.override()=true bypasses the minItems check -- OPEN mode, which otherwise authorizes
    // any non-blank actor for CLOSE, must not grant that bypass to an arbitrary caller.
    WorkflowRuntime runtime = runtime(behaviour(2, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, true, null)))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
  }

  @Test
  void enforcedModeAllowsOverrideWhenExplicitlyAuthorized() {
    // minItems=2 with zero submissions is already minUnmet -- no submitItem authorization needed to
    // exercise the override path.
    CollectionBehaviour cfg = behaviour(2, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED);
    WorkflowRuntime runtime = runtime(cfg, overrideCloseRequirements(), ALLOW_CLOSE_AND_OVERRIDE);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult result = gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, true, null));

    assertThat(result.closed()).isTrue();
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  @Test
  void enforcedModeDeniesOverrideFromAnActorAuthorizedForCloseButNotOverride() {
    CollectionBehaviour cfg = behaviour(2, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED);
    WorkflowRuntime runtime = runtime(cfg, overrideCloseRequirements(), ALLOW_CLOSE_ONLY);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, true, null)))
        .isInstanceOf(CollectionAuthorizationException.class);
  }

  @Test
  void overrideFlagSetButNotNeededDoesNotRequireOverrideAuthorization() {
    // minItems=0 is always met -- a defensively-set override=true must not demand OVERRIDE
    // authorization when it is not actually substituting for an unmet minimum, so an actor
    // authorized only for CLOSE (not OVERRIDE) still succeeds.
    CollectionBehaviour cfg = behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED);
    WorkflowRuntime runtime = runtime(cfg, overrideCloseRequirements(), ALLOW_CLOSE_ONLY);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult result = gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, true, null));

    assertThat(result.closed()).isTrue();
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
  }

  // ---- stale-step / retry hardening ----------------------------------------------------------

  private static final String SECOND_STEP = "reference-checks";

  @Test
  void mutatingAStaleCollectionStepIsRejectedWhileTheRunAwaitsALaterGate() {
    WorkflowRuntime runtime = runtimeTwoCollectionSteps();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult firstClose = gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    assertThat(firstClose.closed()).isTrue();
    assertThat(runtime.getState(runId).getCurrentStepId()).isEqualTo(SECOND_STEP);
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_COLLECTION);

    // The run has moved on to SECOND_STEP; STEP is a closed, non-current gate and must reject every
    // mutating verb, not only the one this finding names as the clearest corruption path (reopen).
    // The run's status is genuinely AWAITING_COLLECTION (just on a different step), so this is a
    // step-target mismatch, not a status mismatch -- requireSuspendedStep's IllegalStateException,
    // matching the same "state conflict, not a bad argument" convention every other suspension verb
    // uses for a stale-step target.
    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> gate.reopenCollection(runId, STEP, ACTOR))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, null)))
        .isInstanceOf(IllegalStateException.class);

    // The actual current gate is unaffected and still governs normally.
    CloseResult secondClose = gate.closeCollection(runId, SECOND_STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    assertThat(secondClose.closed()).isTrue();
  }

  @Test
  void misusingAnUnrelatedSuspensionVerbOnARunAwaitingCollectionNamesTheCorrectVerb() {
    // AWAITING_COLLECTION is registered in isSuspensionStatus/verbFor, so a mismatched verb from a
    // different suspension-status family (submitReview, here) gets the same helpful "use X instead"
    // cross-guidance every other suspension status already provides, instead of a generic error.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> runtime.submitReview(runId, STEP, "note", ACTOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("collections()");
  }

  @Test
  void getCollectionRemainsExemptFromTheCurrentStepCheck() {
    WorkflowRuntime runtime = runtimeTwoCollectionSteps();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));

    // Reading a prior, non-current gate stays supported for audit -- only mutation is rejected.
    CollectionView view = gate.getCollection(runId, STEP, ACTOR);
    assertThat(view.phase()).isEqualTo(CollectionPhase.CLOSED);
  }

  @Test
  void retryTargetingAClosedCollectionStepIsRejectedUpFrontPreservingTheOriginalFailure() {
    StepDefinition collectionStep = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN))
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    StepDefinition terminalFail = StepDefinition.builder()
        .withStepId("fail")
        .withName("fail")
        .withBehaviour(new FailBehaviour("expected"))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(collectionStep, terminalFail), List.of());
    stateRepo = new InMemoryWorkflowStateRepository();
    eventLog = new InMemoryWorkflowEventLog();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow)))
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .build();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    // Closing STEP advances into the fail step, which fails the run -- the ordinary precondition
    // for a top-level retry() back to STEP.
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    WorkflowState beforeRetry = runtime.getState(runId);
    assertThat(beforeRetry.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    String originalFailedStepId = beforeRetry.getRunFailure().failedStepId();
    String originalSupportId = beforeRetry.getRunFailure().supportId();

    // A retry back to STEP is rejected before any mutation -- not silently accepted and left to fail
    // again deep inside the handler -- so the original failed attempt (its step id, support id, and
    // FAILED status) survives the rejected retry untouched instead of being discarded and replaced.
    assertThatThrownBy(() -> runtime.retry(runId, STEP, "user"))
        .isInstanceOf(IllegalArgumentException.class);

    WorkflowState after = runtime.getState(runId);
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(after.getRunFailure().failedStepId()).isEqualTo(originalFailedStepId);
    assertThat(after.getRunFailure().supportId()).isEqualTo(originalSupportId);
  }

  @Test
  void reEnteringAClosedCollectionGateFailsTheStepEvenOutsideTheRetryApi() {
    // Defense-in-depth: retry() rejects a closed-collection retry target up front (previous test),
    // but a RETRY_PREVIOUS step behaviour reaches the same handler through its own
    // clearEntriesFromUid call, not this runtime's retry() method. This proves the handler itself
    // still refuses to silently re-pause on a stale, permanently-closed gate regardless of caller.
    StepDefinition collectionStep = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN))
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(collectionStep), List.of());
    stateRepo = new InMemoryWorkflowStateRepository();
    eventLog = new InMemoryWorkflowEventLog();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow)))
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .build();

    WorkflowState seeded = new WorkflowState("seeded-run", "wf", null, CLOCK.instant());
    seeded.putCollectionState(new CollectionState(STEP, CollectionPhase.CLOSED, CLOCK.instant(),
        List.of(), CLOCK.instant(), ACTOR, CloseReason.MANUAL, Set.of(), Set.of(), 1L));
    seeded.setCurrentStepId(STEP);
    seeded.setStatus(WorkflowStatus.PAUSED);
    stateRepo.save(seeded);

    runtime.continueRun("seeded-run", "user");

    WorkflowState after = runtime.getState("seeded-run");
    assertThat(after.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    assertThat(after.getRunFailure().failedStepId()).isEqualTo(STEP);
  }

  private WorkflowRuntime runtimeTwoCollectionSteps() {
    stateRepo = new InMemoryWorkflowStateRepository();
    eventLog = new InMemoryWorkflowEventLog();
    StepDefinition first = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN))
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    StepDefinition second = StepDefinition.builder()
        .withStepId(SECOND_STEP)
        .withName("Reference checks")
        .withBehaviour(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN))
        .withContextMapping(new ContextMapping(List.of(), List.of("referenceChecks")))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(first, second), List.of());
    return new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow)))
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .build();
  }

  private static final CollectionAuthorizer ALLOW_CLOSE_AND_DEADLINE_CLOSE =
      (actorId, stepId, action, descriptor, context) ->
          action == CollectionAction.CLOSE || action == CollectionAction.DEADLINE_CLOSE
              ? Decision.allow()
              : Decision.deny("not permitted");

  private static final CollectionAuthorizer ALLOW_CLOSE_ONLY =
      (actorId, stepId, action, descriptor, context) ->
          action == CollectionAction.CLOSE ? Decision.allow() : Decision.deny("not permitted");

  private static final CollectionAuthorizer ALLOW_CLOSE_AND_OVERRIDE =
      (actorId, stepId, action, descriptor, context) ->
          action == CollectionAction.CLOSE || action == CollectionAction.OVERRIDE
              ? Decision.allow()
              : Decision.deny("not permitted");

  @Test
  void deadlineCloseEmitsDeadlineSpecificRequestedEventInsteadOfTheGenericOne() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, true, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg, deadlineCloseRequirements(),
        ALLOW_CLOSE_AND_DEADLINE_CLOSE);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult result = gate.closeCollection(runId, STEP,
        new CloseRequest("scheduler", CloseReason.DEADLINE, false, null));

    assertThat(result.closed()).isTrue();
    assertThat(eventTypes(runId))
        .contains(WorkflowEventType.COLLECTION_DEADLINE_CLOSE_REQUESTED)
        .doesNotContain(WorkflowEventType.COLLECTION_CLOSE_REQUESTED);
  }

  @Test
  void openModeDeniesDeadlineCloseRegardlessOfActor() {
    // CloseReason.DEADLINE carries real security semantics (it can satisfy
    // externalDeadlineClosable even when manualClose is false), so bare OPEN mode -- which
    // otherwise authorizes any non-blank actor for CLOSE -- must not grant it to an arbitrary caller.
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, true, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest("any-actor", CloseReason.DEADLINE, false, null)))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
  }

  @Test
  void enforcedModeDeniesDeadlineCloseFromAnActorAuthorizedForCloseButNotDeadlineClose() {
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, true, true, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg, deadlineCloseRequirements(), ALLOW_CLOSE_ONLY);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest("not-the-scheduler", CloseReason.DEADLINE, false, null)))
        .isInstanceOf(CollectionAuthorizationException.class);
  }

  @Test
  void manualCloseDisabledDoesNotOpenABackdoorViaDeadlineReason() {
    // manualClose=false, externalDeadlineClosable=true: an actor authorized for the generic CLOSE
    // action but not for DEADLINE_CLOSE must not be able to use CloseReason.DEADLINE to route
    // around the manual-close restriction; the same actor with DEADLINE_CLOSE authorization
    // succeeds under the deadline path.
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.NONE, WithdrawalPolicy.NONE, false, true, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    WorkflowRuntime closeOnlyRuntime = runtime(cfg, deadlineCloseRequirements(), ALLOW_CLOSE_ONLY);
    CollectionGateRuntime closeOnlyGate = (CollectionGateRuntime) closeOnlyRuntime;
    String closeOnlyRunId = closeOnlyRuntime.start("wf");

    // manualClose=false rejects MANUAL as a normal (non-throwing) closability check.
    CloseResult manualResult = closeOnlyGate.closeCollection(closeOnlyRunId, STEP,
        new CloseRequest("intruder", CloseReason.MANUAL, false, null));
    assertThat(manualResult.closed()).isFalse();
    // The same actor cannot route around it via DEADLINE without DEADLINE_CLOSE authorization --
    // that is rejected at the authorization step instead, before checkClosable even runs.
    assertThatThrownBy(() -> closeOnlyGate.closeCollection(closeOnlyRunId, STEP,
        new CloseRequest("intruder", CloseReason.DEADLINE, false, null)))
        .isInstanceOf(CollectionAuthorizationException.class);

    WorkflowRuntime allowRuntime = runtime(cfg, deadlineCloseRequirements(),
        ALLOW_CLOSE_AND_DEADLINE_CLOSE);
    CollectionGateRuntime allowGate = (CollectionGateRuntime) allowRuntime;
    String allowRunId = allowRuntime.start("wf");

    CloseResult result = allowGate.closeCollection(allowRunId, STEP,
        new CloseRequest("scheduler", CloseReason.DEADLINE, false, null));
    assertThat(result.closed()).isTrue();
  }

  private static List<WorkflowRequirement> deadlineCloseRequirements() {
    return List.of(
        new WorkflowRequirement("req-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, STEP, "close", false, null, ResolutionMode.DEFERRED),
        new WorkflowRequirement("req-deadline-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, STEP, "deadline_close", false, null,
            ResolutionMode.DEFERRED));
  }

  private static List<WorkflowRequirement> overrideCloseRequirements() {
    return List.of(
        new WorkflowRequirement("req-close", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, STEP, "close", false, null, ResolutionMode.DEFERRED),
        new WorkflowRequirement("req-override", "rbac_step_action_allowed",
            RequirementScope.STEP_ACTION, STEP, "override", false, null, ResolutionMode.DEFERRED));
  }

  // ---- replace / withdraw -------------------------------------------------------------------

  @Test
  void replayOfTokenWhoseItemWasSinceReplacedReturnsIdempotentWithNullSubmissionId() {
    // A clientToken stays in seenClientTokens forever once seen, but a replace can overwrite the
    // item's own clientToken. A later replay of the original token then finds no live item still
    // carrying it: SubmissionResult.Status.IDEMPOTENT with submissionId=null, version=0.
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);

    gate.replaceItem(runId, STEP, submitted.submissionId(), submission("b", null, null), ACTOR);
    SubmissionResult replay = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);

    assertThat(replay.status()).isEqualTo(SubmissionResult.Status.IDEMPOTENT);
    assertThat(replay.submissionId()).isNull();
    assertThat(replay.version()).isZero();
    // The replace is not undone and no second item is created.
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(1);
  }

  @Test
  void replayOfTokenWhoseItemWasSinceWithdrawnReturnsIdempotentWithNullSubmissionId() {
    // Unlike a replace, a withdraw does not overwrite the item's clientToken -- the withdrawn item
    // keeps carrying it. A later replay of that token must still resolve to submissionId=null,
    // version=0, the same as the replaced case above, not to the withdrawn item's own (dead) id.
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);

    gate.withdrawItem(runId, STEP, submitted.submissionId(), ACTOR);
    SubmissionResult replay = gate.submitItem(runId, STEP, submission("a", "tok-1", null), ACTOR);

    assertThat(replay.status()).isEqualTo(SubmissionResult.Status.IDEMPOTENT);
    assertThat(replay.submissionId()).isNull();
    assertThat(replay.version()).isZero();
    // The withdrawal is not undone and no second item is created.
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isZero();
  }

  @Test
  void replayOfAReplaceCallWhoseTargetWasSinceWithdrawnReturnsIdempotentWithNullSubmissionId() {
    // A retried replaceItem call itself (not a submitItem replay) against a target withdrawn since
    // the original replace succeeded: replace()'s own liveness check must not run before
    // idempotentReturn has a chance to recognize the replay via the token's earlier
    // seenClientTokens entry, or the retry throws instead of returning the documented no-op.
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    SubmissionResult replaced = gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", "tok-1", null), ACTOR);
    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    gate.withdrawItem(runId, STEP, submitted.submissionId(), ACTOR);

    SubmissionResult replay = gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", "tok-1", null), ACTOR);

    assertThat(replay.status()).isEqualTo(SubmissionResult.Status.IDEMPOTENT);
    assertThat(replay.submissionId()).isNull();
    assertThat(replay.version()).isZero();
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isZero();
  }

  @Test
  void freshReplaceAttemptAgainstAWithdrawnTargetStillThrows() {
    // Not a replay -- a brand-new clientToken against an already-withdrawn target is a genuine
    // error, not an idempotent no-op; the liveness check must still fire for this case.
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    gate.withdrawItem(runId, STEP, submitted.submissionId(), ACTOR);

    assertThatThrownBy(() -> gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), ACTOR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No live item");
  }

  @Test
  void ownerReplaceBumpsVersion() {
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    SubmissionResult replaced = gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), ACTOR);

    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
    assertThat(replaced.version()).isEqualTo(2);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_REPLACED);
  }

  @Test
  void ownerWithdrawRemovesFromLiveView() {
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    gate.withdrawItem(runId, STEP, submitted.submissionId(), ACTOR);

    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isZero();
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_WITHDRAWN);
  }

  // ---- reopen (two-phase) -------------------------------------------------------------------

  @Test
  void reopenHoldsRunUntilContinueRun() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.ALLOWED, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    CloseResult closed = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    assertThat(closed.advanced()).isFalse();
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.AWAITING_COLLECTION);

    gate.reopenCollection(runId, STEP, ACTOR);
    gate.submitItem(runId, STEP, submission("b", null, null), ACTOR);
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));

    runtime.continueRun(runId, ACTOR);
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_REOPENED);
  }

  @Test
  void reopenInvalidatesTheOriginalCloseTokenInsteadOfNoOppingAReplayOnTheReopenedGate() {
    // A close token is only idempotent within the closed cycle it was accepted in. A reopen starts a
    // fresh cycle, so a late-arriving replay of the original close call must be evaluated as a real
    // close attempt against the now-open gate, not short-circuited as a lying no-op that reports
    // closed=true while the gate is actually still open and accepting submissions.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.ALLOWED, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    CloseResult firstClose = gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct"));
    assertThat(firstClose.closed()).isTrue();

    gate.reopenCollection(runId, STEP, ACTOR);
    assertThat(gate.getCollection(runId, STEP, ACTOR).phase()).isEqualTo(CollectionPhase.OPEN);

    CloseResult replay = gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct"));

    assertThat(replay.closed()).isTrue();
    assertThat(gate.getCollection(runId, STEP, ACTOR).phase()).isEqualTo(CollectionPhase.CLOSED);
  }

  @Test
  void idempotentCloseTokenIsNoOp() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.ALLOWED, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct"));

    CloseResult again = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct"));
    assertThat(again.closed()).isTrue();
  }

  @Test
  void closeTokenReplayIsRejectedNotNoOpOnceANoneReopenPolicyCloseHasAdvancedTheRun() {
    // Documented on CloseRequest.closeToken: a NONE-policy close always advances the run past the
    // gate, so a repeated call with the same token lands outside AWAITING_COLLECTION and is rejected
    // as an invalid-status call -- it is not idempotent the way the ALLOWED-policy case above is.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    CloseResult first = gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct"));
    assertThat(first.advanced()).isTrue();
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

    assertThatThrownBy(() -> gate.closeCollection(runId, STEP,
        new CloseRequest(ACTOR, CloseReason.MANUAL, false, "ct")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- authorization ------------------------------------------------------------------------

  @Test
  void openModeDeniesReplacingAnotherActorsItem() {
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.NONE));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThatThrownBy(() -> gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), "other-actor"))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
  }

  @Test
  void ownerPolicyDeniesCrossActorReplaceAndWithdrawWithAnAuditedDenial() {
    // OWNER_REPLACE/OWNER_WITHDRAW is the one denial path that used to throw before authorize() ever
    // ran, leaving no COLLECTION_AUTHORIZATION_DENIED audit trail for a cross-actor tampering attempt
    // against an owner-only gate. Both verbs must now be audited identically to every other denial.
    WorkflowRuntime runtime = runtime(behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThatThrownBy(() -> gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), "other-actor"))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);

    assertThatThrownBy(() -> gate.withdrawItem(runId, STEP, submitted.submissionId(), "other-actor"))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).filteredOn(type -> type == WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED)
        .hasSize(2);
  }

  @Test
  void openModeAllowsCloseFromAnyNonBlankActorNotJustASubmitter() {
    // Like REOPEN, CLOSE is not in OPEN mode's DEADLINE_CLOSE/OVERRIDE/REPLACE_ANY/WITHDRAW_ANY
    // carve-out list -- OPEN mode's default "any non-blank actor" grant applies to it too, even for
    // an actor who never submitted anything to the gate.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    CloseResult result = gate.closeCollection(runId, STEP,
        new CloseRequest("an-uninvolved-actor", CloseReason.MANUAL, false, null));

    assertThat(result.closed()).isTrue();
  }

  @Test
  void openModeAllowsReopenFromAnyNonBlankActor() {
    // Unlike OVERRIDE and DEADLINE_CLOSE (which OPEN mode structurally denies regardless of actor),
    // REOPEN is not in that carve-out list -- OPEN mode's default "any non-blank actor" grant applies,
    // so a cross-actor reopen must succeed.
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.ALLOWED, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    assertThat(gate.getCollection(runId, STEP, ACTOR).phase()).isEqualTo(CollectionPhase.CLOSED);

    gate.reopenCollection(runId, STEP, "any-other-actor");

    assertThat(gate.getCollection(runId, STEP, ACTOR).phase()).isEqualTo(CollectionPhase.OPEN);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_REOPENED);
  }

  @Test
  void blankActorIsDenied() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void enforcedModeWithDefaultAuthorizerDeniesSubmit() {
    WorkflowRuntime runtime = runtimeEnforced(null);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class);
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
  }

  @Test
  void enforcedModeWithGrantingAuthorizerAllowsSubmit() {
    CollectionAuthorizer allowSubmit = (actorId, stepId, action, descriptor, context) ->
        action == CollectionAction.SUBMIT ? Decision.allow() : Decision.deny("nope");
    WorkflowRuntime runtime = runtimeEnforced(allowSubmit);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
  }

  @Test
  void authorizerReturningNullDecisionIsDeniedAsNoDecision() throws Exception {
    CollectionAuthorizer returnsNull = (actorId, stepId, action, descriptor, context) -> null;
    WorkflowRuntime runtime = runtimeEnforced(returnsNull);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class);
    JsonNode payload = payloadOf(runId, WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
    assertThat(payload.get("reason").asText()).isEqualTo("authorizer returned no decision");
  }

  @Test
  void authorizerThatThrowsFailsClosed() throws Exception {
    CollectionAuthorizer throwing = (actorId, stepId, action, descriptor, context) -> {
      throw new IllegalStateException("boom");
    };
    WorkflowRuntime runtime = runtimeEnforced(throwing);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class)
        .hasMessageNotContaining("boom");
    JsonNode payload = payloadOf(runId, WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
    assertThat(payload.get("reason").asText()).isEqualTo("authorizer error").doesNotContain("boom");
  }

  @Test
  void authorizerReturningAMalformedDenyDecisionFailsClosedAsAuthorizationExceptionNotIllegalArgument() {
    // A misbehaving CollectionAuthorizer bypassing Decision.deny(...) and constructing
    // new Decision(false, null) directly must not leak IllegalArgumentException from Decision's
    // own compact constructor to the caller of the gate operation -- it is caught the same as any
    // other authorizer exception and converted to the documented CollectionAuthorizationException.
    CollectionAuthorizer malformed = (actorId, stepId, action, descriptor, context) ->
        new Decision(false, null);
    WorkflowRuntime runtime = runtimeEnforced(malformed);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class)
        .isNotInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dedupeKeyPolicyRejectsDuplicate() {
    WorkflowRuntime runtime = runtime(behaviourDuplicate(DuplicatePolicy.REJECT_BY_DEDUPE_KEY));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("a", null, "dk-1"), ACTOR);

    SubmissionResult rejected = gate.submitItem(runId, STEP, submission("b", null, "dk-1"), ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).isEqualTo("DUPLICATE: dedupeKey 'dk-1' collides with a live item");
  }

  // ---- replace dedupe-key policy -------------------------------------------------------------

  @Test
  void replaceRejectsDedupeKeyCollisionWithAnotherLiveItem() {
    WorkflowRuntime runtime = runtime(behaviourFullDuplicate(0, null, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.NONE,
        DuplicatePolicy.REJECT_BY_DEDUPE_KEY));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult itemA = gate.submitItem(runId, STEP, submission("a", null, "dk-a"), ACTOR);
    gate.submitItem(runId, STEP, submission("b", null, "dk-b"), ACTOR);

    SubmissionResult replaced = gate.replaceItem(runId, STEP, itemA.submissionId(),
        submission("a2", null, "dk-b"), ACTOR);

    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(replaced.reason()).isEqualTo("DUPLICATE: dedupeKey 'dk-b' collides with a live item");
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_REJECTED);
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(2);
  }

  @Test
  void replaceAllowsReusingItsOwnPriorDedupeKey() {
    WorkflowRuntime runtime = runtime(behaviourFullDuplicate(0, null, ReopenPolicy.NONE,
        AuthorizationMode.OPEN, ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.NONE,
        DuplicatePolicy.REJECT_BY_DEDUPE_KEY));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult itemA = gate.submitItem(runId, STEP, submission("a", null, "dk-a"), ACTOR);

    SubmissionResult replaced = gate.replaceItem(runId, STEP, itemA.submissionId(),
        submission("a2", null, "dk-a"), ACTOR);

    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
  }

  // ---- audit payload JSON escaping -----------------------------------------------------------

  @Test
  void submittedEventPayloadEscapesActorIdWithSpecialCharacters() throws Exception {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    String actorId = "actor\"with\\quotes\nand\nnewlines";

    gate.submitItem(runId, STEP, submission("a", null, null), actorId);

    JsonNode payload = payloadOf(runId, WorkflowEventType.COLLECTION_ITEM_SUBMITTED);
    assertThat(payload.get("actorId").asText()).isEqualTo(actorId);
  }

  @Test
  void denialEventPayloadEscapesAuthorizerReason() throws Exception {
    String maliciousReason = "denied: \"bad\" actor\nwith backslash \\ and a trailing quote\"";
    CollectionAuthorizer denyWithSpecialChars = (actorId, stepId, action, descriptor, context) ->
        Decision.deny(maliciousReason);
    WorkflowRuntime runtime = runtimeEnforced(denyWithSpecialChars);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    assertThatThrownBy(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class);

    JsonNode payload = payloadOf(runId, WorkflowEventType.COLLECTION_AUTHORIZATION_DENIED);
    assertThat(payload.get("reason").asText()).isEqualTo(maliciousReason);
  }

  // ---- concurrency ----------------------------------------------------------------------------

  @Test
  void concurrentSubmitsDoNotLoseUpdates() throws Exception {
    int threadCount = 20;
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<SubmissionResult>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threadCount; i++) {
        int index = i;
        futures.add(pool.submit(() -> {
          ready.countDown();
          go.await();
          return gate.submitItem(runId, STEP, submission("item-" + index, null, null), ACTOR);
        }));
      }
      ready.await();
      go.countDown();
      Set<String> submissionIds = new HashSet<>();
      for (Future<SubmissionResult> future : futures) {
        SubmissionResult result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
        submissionIds.add(result.submissionId());
      }
      assertThat(submissionIds).hasSize(threadCount);
      assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(threadCount);
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void concurrentSubmitsDoNotLoseUpdatesOnCopyReturningRepository() throws Exception {
    // Repositories backed by real persistence hand out defensive copies rather than the live
    // instance. The gate verbs must load the state under the per-run lock: a copy loaded before
    // lock acquisition is a stale snapshot whose save would discard concurrent, already-persisted
    // submissions.
    int threadCount = 20;
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN),
        List.of(), null, new CopyReturningWorkflowStateRepository());
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<SubmissionResult>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threadCount; i++) {
        int index = i;
        futures.add(pool.submit(() -> {
          ready.countDown();
          go.await();
          return gate.submitItem(runId, STEP, submission("item-" + index, null, null), ACTOR);
        }));
      }
      ready.await();
      go.countDown();
      Set<String> submissionIds = new HashSet<>();
      for (Future<SubmissionResult> future : futures) {
        SubmissionResult result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
        submissionIds.add(result.submissionId());
      }
      assertThat(submissionIds).hasSize(threadCount);
      assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(threadCount);
    } finally {
      pool.shutdown();
    }
  }

  // ---- client-token duplicate policy ----------------------------------------------------------

  @Test
  void rejectByClientTokenPolicyRejectsRepeatedTokenAsHardDuplicate() throws Exception {
    WorkflowRuntime runtime = runtime(behaviourDuplicate(DuplicatePolicy.REJECT_BY_CLIENT_TOKEN));
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult first = gate.submitItem(runId, STEP, submission("a", "token-1", null), ACTOR);
    assertThat(first.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    SubmissionResult repeat = gate.submitItem(runId, STEP, submission("b", "token-1", null), ACTOR);
    assertThat(repeat.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(repeat.reason()).isEqualTo("DUPLICATE: clientToken 'token-1' was already seen at this gate");
    assertThat(payloadOf(runId, WorkflowEventType.COLLECTION_ITEM_REJECTED).get("reason").asText())
        .isEqualTo("DUPLICATE: clientToken 'token-1' was already seen at this gate");

    SubmissionResult fresh = gate.submitItem(runId, STEP, submission("c", "token-2", null), ACTOR);
    assertThat(fresh.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(2);
  }

  // ---- item schema enforcement ----------------------------------------------------------------

  @Test
  void declaredItemSchemaRejectsFailClosedWhenNoValidatorConfigured() {
    WorkflowRuntime runtime = runtime(behaviourWithItemSchema("cv-item"));
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(result.reason()).startsWith("ITEM_SCHEMA_INVALID");
    assertThat(eventTypes(runId)).contains(WorkflowEventType.COLLECTION_ITEM_REJECTED);
  }

  @Test
  void configuredItemSchemaValidatorAcceptsConformingAndRejectsViolatingItems() {
    CollectionItemSchemaValidator schemaValidator = (ref, inlineJson) ->
        inlineJson.contains("good")
            ? ValidationResult.ok()
            : ValidationResult.invalid("value must be 'good'");
    WorkflowRuntime runtime = runtime(behaviourWithItemSchema("cv-item"), List.of(), null,
        new InMemoryWorkflowStateRepository(), schemaValidator, null);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult accepted = gate.submitItem(runId, STEP, submission("good", null, null), ACTOR);
    assertThat(accepted.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    SubmissionResult rejected = gate.submitItem(runId, STEP, submission("bad", null, null), ACTOR);
    assertThat(rejected.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(rejected.reason()).contains("value must be 'good'");
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(1);
  }

  @Test
  void itemSchemaValidatorThatThrowsFailsClosedWithoutLeakingTheExceptionMessage() throws Exception {
    CollectionItemSchemaValidator throwing = (ref, inlineJson) -> {
      throw new IllegalStateException("boom");
    };
    WorkflowRuntime runtime = runtime(behaviourWithItemSchema("cv-item"), List.of(), null,
        new InMemoryWorkflowStateRepository(), throwing, null);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(result.reason()).isEqualTo("ITEM_SCHEMA_INVALID: item-schema validator error")
        .doesNotContain("boom");
    assertThat(payloadOf(runId, WorkflowEventType.COLLECTION_ITEM_REJECTED).get("reason").asText())
        .doesNotContain("boom");
  }

  // ---- submission validator SPI ----------------------------------------------------------------

  @Test
  void submissionValidatorReceivesFullContextOnSubmitAndReplace() {
    List<CollectionSubmissionContext> seen = new ArrayList<>();
    CollectionSubmissionValidator recording = context -> {
      seen.add(context);
      return Decision.allow();
    };
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN),
        List.of(), null, new InMemoryWorkflowStateRepository(), null, recording);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult submitted =
        gate.submitItem(runId, STEP, submission("a", "tok-1", "key-1"), ACTOR);
    gate.replaceItem(runId, STEP, submitted.submissionId(), submission("b", null, null), ACTOR);

    assertThat(seen).hasSize(2);
    CollectionSubmissionContext submitContext = seen.get(0);
    assertThat(submitContext.runId()).isEqualTo(runId);
    assertThat(submitContext.workflowId()).isEqualTo("wf");
    assertThat(submitContext.stepId()).isEqualTo(STEP);
    assertThat(submitContext.actorId()).isEqualTo(ACTOR);
    assertThat(submitContext.clientToken()).isEqualTo("tok-1");
    assertThat(submitContext.dedupeKey()).isEqualTo("key-1");
    assertThat(submitContext.replacesSubmissionId()).isNull();
    assertThat(submitContext.payload().inlineJson()).contains("a");
    assertThat(seen.get(1).replacesSubmissionId()).isEqualTo(submitted.submissionId());
  }

  @Test
  void submissionValidatorDenialRejectsWithAuditEvent() throws Exception {
    CollectionSubmissionValidator denying =
        context -> Decision.deny("submissions are closed for this deployment");
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN),
        List.of(), null, new InMemoryWorkflowStateRepository(), null, denying);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(result.reason()).contains("submissions are closed for this deployment");
    assertThat(payloadOf(runId, WorkflowEventType.COLLECTION_ITEM_REJECTED).get("reason").asText())
        .contains("SUBMISSION_VALIDATOR_REJECTED");
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isZero();
  }

  @Test
  void submissionValidatorThatThrowsFailsClosedWithoutLeakingTheExceptionMessage() throws Exception {
    CollectionSubmissionValidator throwing = context -> {
      throw new IllegalStateException("boom");
    };
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN),
        List.of(), null, new InMemoryWorkflowStateRepository(), null, throwing);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(result.reason()).isEqualTo("SUBMISSION_VALIDATOR_REJECTED: submission validator error")
        .doesNotContain("boom");
    assertThat(payloadOf(runId, WorkflowEventType.COLLECTION_ITEM_REJECTED).get("reason").asText())
        .doesNotContain("boom");
  }

  @Test
  void defaultSubmissionValidatorRejectsUnsafeFilePaths() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    CollectionSubmission unsafe = new CollectionSubmission(
        new CollectionPayload("{\"v\":\"a\"}",
            List.of(new FileRef("../escape.pdf", "cv.pdf", "application/pdf", 10))),
        null, null);

    SubmissionResult result = gate.submitItem(runId, STEP, unsafe, ACTOR);

    assertThat(result.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(result.reason()).contains("FILE_PATH_UNSAFE");
  }

  // ---- runtime-scoped accessor ----------------------------------------------------------------

  @Test
  void collectionsAccessorExposesGateOperationsWithoutCasting() {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    String runId = runtime.start("wf");

    CollectionGateRuntime gate = runtime.collections();

    SubmissionResult result = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isEqualTo(1);
  }

  @Test
  void closeVsSubmitRaceProducesConsistentFinalCount() throws Exception {
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN));
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      Future<SubmissionResult> submitFuture = pool.submit(() -> {
        go.await();
        try {
          return gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
        } catch (IllegalArgumentException racedAfterClose) {
          // The gate had already closed (and, with ReopenPolicy.NONE, advanced) before this submit
          // acquired the run's collection lock -- a legitimate race outcome, not a defect.
          return null;
        }
      });
      Future<CloseResult> closeFuture = pool.submit(() -> {
        go.await();
        return gate.closeCollection(runId, STEP,
            new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
      });
      go.countDown();
      SubmissionResult submitResult = submitFuture.get(10, TimeUnit.SECONDS);
      CloseResult closeResult = closeFuture.get(10, TimeUnit.SECONDS);

      assertThat(closeResult.closed()).isTrue();
      assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
      // Whichever operation the lock ordered first, the close's reported finalCount must match what
      // was actually materialized -- no lost update, no double count.
      CollectionView view = gate.getCollection(runId, STEP, ACTOR);
      assertThat(closeResult.finalCount()).isEqualTo(view.liveCount());
      if (submitResult != null && submitResult.status() == SubmissionResult.Status.ACCEPTED) {
        assertThat(view.liveCount()).isEqualTo(1);
      } else {
        assertThat(view.liveCount()).isEqualTo(0);
      }
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void cancelVsSubmitRaceOnCopyReturningRepositoryDoesNotLoseSubmission() throws Exception {
    // Repositories backed by real persistence hand out defensive copies. cancel() must reload state
    // under the run's collection lock after a concurrent submit has fully committed, not act on a
    // stale pre-lock snapshot -- otherwise its save silently discards the already-persisted item.
    // The barrier forces exactly that interleaving: cancel's first (pre-lock) read is captured, then
    // held, while a submit runs to completion, before cancel is allowed to proceed.
    FindByIdBarrierRepository repo =
        new FindByIdBarrierRepository(new CopyReturningWorkflowStateRepository());
    WorkflowRuntime runtime = runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN),
        List.of(), null, repo);
    CollectionGateRuntime gate = runtime.collections();
    String runId = runtime.start("wf");

    repo.armFor("cancel-race-thread");
    Thread cancelThread = new Thread(() -> runtime.cancel(runId, ACTOR), "cancel-race-thread");
    cancelThread.start();
    assertThat(repo.awaitEntered(10, TimeUnit.SECONDS)).isTrue();

    SubmissionResult submitResult = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);
    assertThat(submitResult.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    repo.release();
    cancelThread.join(TimeUnit.SECONDS.toMillis(10));
    assertThat(cancelThread.isAlive()).isFalse();

    WorkflowState finalState = runtime.getState(runId);
    assertThat(finalState.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
    int liveCount = finalState.getCollectionState(STEP).orElseThrow().items().size();
    assertThat(liveCount).isEqualTo(1);
  }

  @Test
  void getStateWaitsForAnInFlightCollectionMutationInsteadOfRacingIt() throws Exception {
    // getState() now shares the same per-run collection lock as the mutating verbs: while a
    // submit is mid-flight holding the lock (blocked here inside the authorizer, which authorize()
    // calls from within the locked section), a concurrent getState() call must wait for it instead
    // of racing it on the same shared, non-thread-safe WorkflowState instance.
    CountDownLatch authorizerEntered = new CountDownLatch(1);
    CountDownLatch releaseAuthorizer = new CountDownLatch(1);
    CollectionAuthorizer blockingAuthorizer = (actorId, stepId, action, descriptor, context) -> {
      authorizerEntered.countDown();
      try {
        releaseAuthorizer.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return Decision.allow();
    };
    WorkflowRuntime runtime = runtimeEnforced(blockingAuthorizer);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<SubmissionResult> submitFuture =
          pool.submit(() -> gate.submitItem(runId, STEP, submission("a", null, null), ACTOR));
      assertThat(authorizerEntered.await(10, TimeUnit.SECONDS)).isTrue();

      Future<WorkflowState> stateFuture = pool.submit(() -> runtime.getState(runId));
      Thread.sleep(200);
      assertThat(stateFuture.isDone()).isFalse();

      releaseAuthorizer.countDown();
      SubmissionResult submitResult = submitFuture.get(10, TimeUnit.SECONDS);
      WorkflowState state = stateFuture.get(10, TimeUnit.SECONDS);

      assertThat(submitResult.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);
      assertThat(state.getCollectionState(STEP).orElseThrow().items()).hasSize(1);
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void retryNowWaitsForAnInFlightCollectionMutationInsteadOfRacingIt() throws Exception {
    // retry() previously mutated WorkflowState (including, via drive(), the same
    // collectionStateByStepId map the CollectionGateRuntime verbs touch) without holding the run's
    // collection lock. A retry() targeting a FAILED run's non-collection step and a concurrent
    // getCollection() call on that same run -- both legitimately callable on a FAILED run,
    // regardless of the collection gate's own status -- could therefore race on that shared,
    // non-thread-safe map. retry() must now block a concurrent getCollection() the same way any
    // other locked verb does.
    StepDefinition collectionStep = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.OPEN))
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    StepDefinition terminalFail = StepDefinition.builder()
        .withStepId("fail")
        .withName("fail")
        .withBehaviour(new FailBehaviour("expected"))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(),
        List.of(collectionStep, terminalFail), List.of());
    GetWorkflowBarrierRepository workflowRepo =
        new GetWorkflowBarrierRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow)));
    stateRepo = new InMemoryWorkflowStateRepository();
    eventLog = new InMemoryWorkflowEventLog();
    WorkflowRuntime runtime = new WorkflowRuntimeBuilder()
        .workflowRepository(workflowRepo)
        .workflowStateRepository(stateRepo)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class))
        .build();
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.closeCollection(runId, STEP, new CloseRequest(ACTOR, CloseReason.MANUAL, false, null));
    assertThat(runtime.getState(runId).getStatus()).isEqualTo(WorkflowStatus.FAILED);

    workflowRepo.armFor("retry-race-thread");
    Thread retryThread = new Thread(() -> runtime.retry(runId, "fail", "user"), "retry-race-thread");
    retryThread.start();
    assertThat(workflowRepo.awaitEntered(10, TimeUnit.SECONDS)).isTrue();

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<CollectionView> viewFuture = pool.submit(() -> gate.getCollection(runId, STEP, ACTOR));
      Thread.sleep(200);
      assertThat(viewFuture.isDone()).isFalse();

      workflowRepo.release();
      CollectionView view = viewFuture.get(10, TimeUnit.SECONDS);
      retryThread.join(TimeUnit.SECONDS.toMillis(10));

      assertThat(retryThread.isAlive()).isFalse();
      assertThat(view.phase()).isEqualTo(CollectionPhase.CLOSED);
    } finally {
      pool.shutdown();
    }
  }

  // ---- authorized cross-actor replacement ownership ------------------------------------------

  @Test
  void authorizedCrossActorReplacementTransfersVersionOwnership() {
    WorkflowRequirement submitReq = new WorkflowRequirement("req-submit", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "submit", false, "\"recruiter\"", ResolutionMode.DEFERRED);
    WorkflowRequirement replaceAnyReq = new WorkflowRequirement("req-replace-any",
        "rbac_step_action_allowed", RequirementScope.STEP_ACTION, STEP, "replace_any", false,
        "\"admin\"", ResolutionMode.DEFERRED);
    WorkflowRequirement withdrawOwnReq = new WorkflowRequirement("req-withdraw-own",
        "rbac_step_action_allowed", RequirementScope.STEP_ACTION, STEP, "withdraw_own", false,
        "\"owner\"", ResolutionMode.DEFERRED);
    WorkflowRequirement viewReq = new WorkflowRequirement("req-view", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "view", false, "\"viewer\"", ResolutionMode.DEFERRED);
    CollectionAuthorizer allowAll = (actorId, stepId, action, descriptor, context) -> Decision.allow();
    CollectionBehaviour cfg = behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW);
    WorkflowRuntime runtime =
        runtime(cfg, List.of(submitReq, replaceAnyReq, withdrawOwnReq, viewReq), allowAll);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    SubmissionResult replaced = gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), "admin-actor");
    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.ACCEPTED);

    // Ownership of the slot follows the latest version (documented CollectionItem/CollectionGateService
    // contract), not the original submitter: the original actor can no longer OWNER_WITHDRAW it -- this
    // is an authorization denial (audited as COLLECTION_AUTHORIZATION_DENIED), not a structural error.
    assertThatThrownBy(() -> gate.withdrawItem(runId, STEP, submitted.submissionId(), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class);
    // ...but the replacing actor, now the recorded owner, can.
    gate.withdrawItem(runId, STEP, submitted.submissionId(), "admin-actor");
    assertThat(gate.getCollection(runId, STEP, ACTOR).liveCount()).isZero();
  }

  @Test
  void authorizedCrossActorReplacementCannotBypassTheReplacingActorsPerActorCap() {
    // admin-actor is already at maxItemsPerActor via its own submission; replacing ACTOR's item
    // would transfer that item's ownership to admin-actor too, pushing it over the cap the same
    // way a third fresh submission would -- the cap must be enforced on the transfer, not just on
    // direct submission.
    WorkflowRequirement submitReq = new WorkflowRequirement("req-submit", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "submit", false, "\"any\"", ResolutionMode.DEFERRED);
    WorkflowRequirement replaceAnyReq = new WorkflowRequirement("req-replace-any",
        "rbac_step_action_allowed", RequirementScope.STEP_ACTION, STEP, "replace_any", false,
        "\"admin\"", ResolutionMode.DEFERRED);
    CollectionAuthorizer allowAll = (actorId, stepId, action, descriptor, context) -> Decision.allow();
    CollectionBehaviour cfg = new CollectionBehaviour(null, 0, null, 1, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE,
        AuthorizationMode.ENFORCED, StepTransition.AUTO);
    WorkflowRuntime runtime = runtime(cfg, List.of(submitReq, replaceAnyReq), allowAll);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    gate.submitItem(runId, STEP, submission("x", null, null), "admin-actor");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    SubmissionResult replaced = gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), "admin-actor");

    assertThat(replaced.status()).isEqualTo(SubmissionResult.Status.REJECTED);
    assertThat(replaced.reason()).isEqualTo("PER_ACTOR_CAP: actor has 1, cap 1");
  }

  @Test
  void authorizedReplacePolicyRequiresAnOwnActionForTheSubmittersOwnReplace() {
    // Under AUTHORIZED_REPLACE, the submitter's own replace resolves to REPLACE_OWN, not
    // REPLACE_ANY (see ReplacementPolicy.AUTHORIZED_REPLACE): declaring only a replace_any
    // requirement does not authorize the owner's own replace, since it is a distinct action.
    WorkflowRequirement submitReq = new WorkflowRequirement("req-submit", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "submit", false, "\"recruiter\"", ResolutionMode.DEFERRED);
    WorkflowRequirement replaceAnyReq = new WorkflowRequirement("req-replace-any",
        "rbac_step_action_allowed", RequirementScope.STEP_ACTION, STEP, "replace_any", false,
        "\"admin\"", ResolutionMode.DEFERRED);
    CollectionAuthorizer allowAll = (actorId, stepId, action, descriptor, context) -> Decision.allow();
    CollectionBehaviour cfg = behaviourFull(0, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED,
        ReplacementPolicy.AUTHORIZED_REPLACE, WithdrawalPolicy.NONE);
    WorkflowRuntime runtime = runtime(cfg, List.of(submitReq, replaceAnyReq), allowAll);
    CollectionGateRuntime gate = (CollectionGateRuntime) runtime;
    String runId = runtime.start("wf");
    SubmissionResult submitted = gate.submitItem(runId, STEP, submission("a", null, null), ACTOR);

    assertThatThrownBy(() -> gate.replaceItem(runId, STEP, submitted.submissionId(),
        submission("b", null, null), ACTOR))
        .isInstanceOf(CollectionAuthorizationException.class)
        .hasMessageContaining("replace_own");
  }

  // ---- helpers ------------------------------------------------------------------------------

  private JsonNode payloadOf(String runId, WorkflowEventType eventType) throws Exception {
    WorkflowEvent event = eventLog.getEvents(runId).stream()
        .filter(candidate -> candidate.eventType() == eventType)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No event of type " + eventType));
    return new ObjectMapper().readTree(event.payload());
  }

  private static CollectionSubmission submission(String value, String clientToken, String dedupeKey) {
    return new CollectionSubmission(
        new CollectionPayload("{\"v\":\"%s\"}".formatted(value), List.of()), clientToken, dedupeKey);
  }

  private static CollectionBehaviour behaviour(int minItems, Integer maxItems, ReopenPolicy reopen,
      AuthorizationMode mode) {
    return behaviourFull(minItems, maxItems, reopen, mode, ReplacementPolicy.OWNER_REPLACE,
        WithdrawalPolicy.OWNER_WITHDRAW);
  }

  private static CollectionBehaviour behaviourFull(int minItems, Integer maxItems, ReopenPolicy reopen,
      AuthorizationMode mode, ReplacementPolicy replacement, WithdrawalPolicy withdrawal) {
    return new CollectionBehaviour(null, minItems, maxItems, null, 0, null, DuplicatePolicy.ALLOW,
        replacement, withdrawal, true, false, reopen, mode, StepTransition.AUTO);
  }

  private static CollectionBehaviour behaviourDuplicate(DuplicatePolicy policy) {
    return new CollectionBehaviour(null, 0, null, null, 0, null, policy, ReplacementPolicy.NONE,
        WithdrawalPolicy.NONE, true, false, ReopenPolicy.NONE, AuthorizationMode.OPEN,
        StepTransition.AUTO);
  }

  private static CollectionBehaviour behaviourWithItemSchema(String itemSchemaRef) {
    return new CollectionBehaviour(itemSchemaRef, 0, null, null, 0, null, DuplicatePolicy.ALLOW,
        ReplacementPolicy.OWNER_REPLACE, WithdrawalPolicy.OWNER_WITHDRAW, true, false,
        ReopenPolicy.NONE, AuthorizationMode.OPEN, StepTransition.AUTO);
  }

  private static CollectionBehaviour behaviourFullDuplicate(int minItems, Integer maxItems,
      ReopenPolicy reopen, AuthorizationMode mode, ReplacementPolicy replacement,
      WithdrawalPolicy withdrawal, DuplicatePolicy duplicatePolicy) {
    return new CollectionBehaviour(null, minItems, maxItems, null, 0, null, duplicatePolicy, replacement,
        withdrawal, true, false, reopen, mode, StepTransition.AUTO);
  }

  private WorkflowRuntime runtime(CollectionBehaviour cfg) {
    return runtime(cfg, List.of(), null);
  }

  private WorkflowRuntime runtimeEnforced(CollectionAuthorizer authorizer) {
    WorkflowRequirement submitReq = new WorkflowRequirement("req-submit", "rbac_step_action_allowed",
        RequirementScope.STEP_ACTION, STEP, "submit", false, "\"recruiter\"", ResolutionMode.DEFERRED);
    return runtime(behaviour(0, null, ReopenPolicy.NONE, AuthorizationMode.ENFORCED),
        List.of(submitReq), authorizer);
  }

  private WorkflowRuntime runtime(CollectionBehaviour cfg, List<WorkflowRequirement> requirements,
      CollectionAuthorizer authorizer) {
    stateRepo = new InMemoryWorkflowStateRepository();
    return runtime(cfg, requirements, authorizer, stateRepo);
  }

  private WorkflowRuntime runtime(CollectionBehaviour cfg, List<WorkflowRequirement> requirements,
      CollectionAuthorizer authorizer, WorkflowStateRepository repository) {
    return runtime(cfg, requirements, authorizer, repository, null, null);
  }

  private WorkflowRuntime runtime(CollectionBehaviour cfg, List<WorkflowRequirement> requirements,
      CollectionAuthorizer authorizer, WorkflowStateRepository repository,
      CollectionItemSchemaValidator itemSchemaValidator,
      CollectionSubmissionValidator submissionValidator) {
    eventLog = new InMemoryWorkflowEventLog();
    StepDefinition step = StepDefinition.builder()
        .withStepId(STEP)
        .withName("CV intake")
        .withBehaviour(cfg)
        .withContextMapping(new ContextMapping(List.of(), List.of(OUTPUT_KEY)))
        .build();
    WorkflowDefinition workflow = new WorkflowDefinition("wf", "wf", null, null, null, "1.0.0", null,
        WorkflowSource.CUSTOM, WorkflowLifecycle.ACTIVE, Map.of(), Map.of(), List.of(step),
        requirements);
    WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder()
        .workflowRepository(new InMemoryWorkflowRepository(Map.of("wf", workflow)))
        .workflowStateRepository(repository)
        .workflowEventLog(eventLog)
        .clock(CLOCK)
        .agentInvoker(mock(AgentInvoker.class));
    if (authorizer != null) {
      builder.collectionAuthorizer(authorizer);
    }
    if (itemSchemaValidator != null) {
      builder.collectionItemSchemaValidator(itemSchemaValidator);
    }
    if (submissionValidator != null) {
      builder.collectionSubmissionValidator(submissionValidator);
    }
    return builder.build();
  }

  /**
   * Stores and returns defensive copies, the way repositories backed by real persistence behave —
   * the runtime never sees the stored instance, so mutations survive only through an explicit save
   * of the freshly loaded copy.
   */
  private static final class CopyReturningWorkflowStateRepository implements WorkflowStateRepository {

    private final ConcurrentMap<String, WorkflowState> statesByRunId = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowState state) {
      statesByRunId.put(state.getRunId(), state.snapshot());
    }

    @Override
    public Optional<WorkflowState> findById(String runId) {
      return Optional.ofNullable(statesByRunId.get(runId)).map(WorkflowState::snapshot);
    }

    @Override
    public List<WorkflowState> findAll() {
      return statesByRunId.values().stream().map(WorkflowState::snapshot).toList();
    }
  }

  /**
   * Wraps a delegate repository so the first {@link #findById(String)} call from a designated
   * thread is captured, then held, until explicitly released -- reproducing the interleaving where
   * a caller reads state, another actor's operation fully commits, and only then does the first
   * caller's stale read resume and act on it.
   */
  private static final class FindByIdBarrierRepository implements WorkflowStateRepository {

    private final WorkflowStateRepository delegate;
    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile String armedThreadName;

    FindByIdBarrierRepository(WorkflowStateRepository delegate) {
      this.delegate = delegate;
    }

    void armFor(String threadName) {
      this.armedThreadName = threadName;
    }

    boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
      return entered.await(timeout, unit);
    }

    void release() {
      release.countDown();
    }

    @Override
    public void save(WorkflowState state) {
      delegate.save(state);
    }

    @Override
    public Optional<WorkflowState> findById(String runId) {
      Optional<WorkflowState> snapshot = delegate.findById(runId);
      if (Thread.currentThread().getName().equals(armedThreadName) && triggered.compareAndSet(false, true)) {
        entered.countDown();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return snapshot;
    }

    @Override
    public List<WorkflowState> findAll() {
      return delegate.findAll();
    }
  }

  /**
   * Blocks a named thread's first {@link #get} call, mirroring {@link FindByIdBarrierRepository}
   * but for {@link WorkflowRepository} -- used to hold a locked verb (e.g. {@code retry}) inside
   * its collection-lock-protected section (past acquisition, mid-{@code workflowRepository.get}
   * call) so a concurrent lock-protected call can be proven to wait for it.
   */
  private static final class GetWorkflowBarrierRepository implements WorkflowRepository {

    private final WorkflowRepository delegate;
    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile String armedThreadName;

    GetWorkflowBarrierRepository(WorkflowRepository delegate) {
      this.delegate = delegate;
    }

    void armFor(String threadName) {
      this.armedThreadName = threadName;
    }

    boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
      return entered.await(timeout, unit);
    }

    void release() {
      release.countDown();
    }

    @Override
    public WorkflowDefinition get(String id) {
      WorkflowDefinition workflow = delegate.get(id);
      if (Thread.currentThread().getName().equals(armedThreadName) && triggered.compareAndSet(false, true)) {
        entered.countDown();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return workflow;
    }

    @Override
    public Map<String, WorkflowDefinition> findAll() {
      return delegate.findAll();
    }
  }

  private List<WorkflowEventType> eventTypes(String runId) {
    return eventLog.getEvents(runId).stream().map(WorkflowEvent::eventType).toList();
  }
}
