// SPDX-License-Identifier: Apache-2.0

/**
 * Deterministic facts about the Builder functional-testkit fixtures
 * (`agentforge4j-workflow-builder/dev/fixtures.ts`), selected via `?fixture=<name>` on the dev
 * harness. Mirrors the existing `fixtures/builder-fixture.ts` convention: specs assert against
 * these constants rather than importing the `WorkflowDefinition` objects themselves.
 */
export const FUNCTIONAL_FIXTURES = {
  /**
   * `emptyWorkflow()` (zero steps) as `initialWorkflow`. `WorkflowBuilder.tsx` renders a
   * zero-step seed via `createInitialCanvasModel()` rather than an actually-empty canvas — the
   * Builder's own concept of "empty" is a single untouched, unconfigured ASK_USER starter step,
   * matching issue #94's own description ("the default single unconfigured step"). Its canvas
   * node id is a random nanoid (`c-ask-user-<random>`), not deterministic — use
   * `canvasNodes.first()`, never a hardcoded id, against this fixture.
   */
  empty: {
    query: 'fixture=empty',
    nodeCount: 1,
  },
  /** One fully configured INPUT step — the minimal "Looks good" workflow. */
  simpleValid: {
    query: 'fixture=simple-valid',
    nodeCount: 1,
    stepName: 'Collect Topic',
  },
  /** The pre-existing `sampleWorkflow()` dev default — same fixture the c1-c11 suite uses. */
  multiStep: {
    query: 'fixture=multi-step',
    nodeCount: 5,
  },
  /**
   * One node per WorkflowDefinition-representable behaviour type (8 of the 10 NodeKinds).
   * `steps` is unordered w.r.t. the export — `BRANCH`/`RETRY_PREVIOUS` targets get
   * topologically reordered ahead of some earlier canvas nodes, same as the `multiStep`
   * fixture's `decision-dev` case — so assertions must match by `stepId`, not array position.
   */
  allStepTypes: {
    query: 'fixture=all-step-types',
    nodeCount: 8,
    steps: [
      { stepId: 'ask-user', name: 'Ask a Question', behaviourType: 'INPUT' },
      { stepId: 'ai-step', name: 'AI Step', behaviourType: 'AGENT' },
      { stepId: 'ai-debate', name: 'AI with Tools', behaviourType: 'SPAR' },
      { stepId: 'decision', name: 'Decision', behaviourType: 'BRANCH' },
      { stepId: 'load-resource', name: 'Load External Resource', behaviourType: 'RESOURCE' },
      { stepId: 'reuse-workflow', name: 'Reuse Existing Workflow', behaviourType: 'WORKFLOW_BEHAVIOUR' },
      { stepId: 'stop', name: 'Stop with Error', behaviourType: 'FAIL' },
      { stepId: 'retry-previous', name: 'Retry Previous Step', behaviourType: 'RETRY_PREVIOUS' },
    ],
  },
  /**
   * `initialWorkflow` entirely omitted — the one seed state that engages the built-in
   * localStorage draft-recovery adapter (any supplied `initialWorkflow`, fixtures included,
   * suppresses restore). Persistence-journey specs must use this, not `empty`.
   */
  none: {
    query: 'fixture=none',
  },
} as const;

/** The 6 default-visible palette step kinds (COMMON + FLOW) and their add-button slugs/labels. */
export const PALETTE_STEP_KINDS = [
  { slug: 'ask-user', label: 'Ask a Question', group: 'common' },
  { slug: 'ai-step', label: 'AI Step', group: 'common' },
  { slug: 'save-result', label: 'Save Result', group: 'common' },
  { slug: 'decision', label: 'Decision', group: 'flow' },
  { slug: 'repeat', label: 'Loop / Repeat', group: 'flow' },
  { slug: 'reuse-workflow', label: 'Reuse Existing Workflow', group: 'flow' },
] as const;
