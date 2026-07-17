// SPDX-License-Identifier: Apache-2.0

/**
 * Deterministic facts about the Day 4 functional-testkit fixtures
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
  /** One node per WorkflowDefinition-representable behaviour type (8 of the 10 NodeKinds). */
  allStepTypes: {
    query: 'fixture=all-step-types',
    nodeCount: 8,
    stepNames: [
      'Ask a Question',
      'AI Step',
      'AI with Tools',
      'Decision',
      'Load External Resource',
      'Reuse Existing Workflow',
      'Stop with Error',
      'Retry Previous Step',
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
