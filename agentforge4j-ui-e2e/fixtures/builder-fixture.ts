// SPDX-License-Identifier: Apache-2.0

/**
 * Deterministic facts about the workflow-builder dev harness sample workflow
 * (`agentforge4j-workflow-builder/dev/sample-workflow.ts`). The harness fixture already uses
 * fixed step ids, so the builder specs assert against these constants rather than injecting
 * their own corpus (Phase 0, I8 / Phase 1, D3).
 */
export const BUILDER_FIXTURE = {
  /** One canvas node per sample step (all five map to visible canvas nodes). */
  initialNodeCount: 5,
  nodeNames: {
    askUser: 'Collect Input',
    aiStep: 'AI Analysis',
    decision: 'Route Outcome',
    save: 'Save Result',
    loop: 'Loop',
  },
  /** A common-kind palette slug always reachable from the expanded palette. */
  addableKindSlug: 'ai-step',
} as const;
