// SPDX-License-Identifier: Apache-2.0

import type { WorkflowDefinition } from '../src/index';
import { sampleWorkflow } from './sample-workflow';

/**
 * Named, deterministic `WorkflowDefinition` fixtures for the Builder functional testkit
 * (`agentforge4j-ui-e2e/specs/builder-functional`), selected via `?fixture=<name>` on the dev
 * harness. Dev-harness-only test infrastructure, excluded from the published package — same
 * category as the existing `?mode=`/`?caps=` query params and the `__afbExport` capture seam.
 * An absent or unrecognized name falls back to `sampleWorkflow()`, the pre-existing default the
 * c1-c11 suite already depends on, so this is purely additive.
 *
 * Every fixture is a plain object literal built only from the public `WorkflowDefinition` shape
 * (re-exported from `src/index.ts`, the package's public entry point) — none reaches into
 * `CanvasModel`/`NodeData`/the mapper internals, or any other module the published package does
 * not itself export.
 */

/** Deliberately not imported from `api/types.ts`'s `emptyWorkflow()` helper — that module path
 *  isn't re-exported from `src/index.ts`, so this fixture stays visibly built from nothing but
 *  the public `WorkflowDefinition` shape rather than an internal helper. */
function emptyWorkflow(): WorkflowDefinition {
  return { id: '', name: '', description: '', steps: [], artifacts: {} };
}

function simpleValidWorkflow(): WorkflowDefinition {
  return {
    id: 'fx-simple-valid',
    name: 'Simple Valid Workflow',
    description: 'One fully configured step; nothing left to fix.',
    steps: [
      {
        stepId: 'collect-topic',
        name: 'Collect Topic',
        behaviourType: 'INPUT',
        config: { artifactId: 'artifact-collect-topic', transition: 'AUTO' },
      },
    ],
    artifacts: {
      'artifact-collect-topic': {
        id: 'artifact-collect-topic',
        items: [{ id: 'topic', type: 'TEXT', label: 'Topic', required: true }],
      },
    },
  };
}

/**
 * One step per `WorkflowDefinition`-representable behaviour type (8 of the 10 palette
 * `NodeKind`s — `SAVE_RESULT` and `REPEAT` are canvas-only constructs with no `behaviourType`
 * of their own, per `dev/sample-workflow.ts`'s existing comment, and so cannot be preloaded this
 * way; they are covered instead by the step-type-coverage journey adding them live via the
 * palette). Steps are a flat sequential array — `workflowToCanvas` lays them out in a straight
 * line regardless of branch/retry semantics — so branch/retry references only need to resolve
 * against step ids present in this same list to round-trip through export.
 */
function allStepTypesWorkflow(): WorkflowDefinition {
  return {
    id: 'fx-all-step-types',
    name: 'All Step Types Workflow',
    description: 'One step per WorkflowDefinition-representable behaviour type.',
    steps: [
      {
        stepId: 'ask-user',
        name: 'Ask a Question',
        behaviourType: 'INPUT',
        config: { artifactId: 'artifact-ask-user', transition: 'AUTO' },
      },
      {
        stepId: 'ai-step',
        name: 'AI Step',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-demo', transition: 'AUTO', maxRetries: 0 },
        stepPrompt: 'Analyze the collected input.',
      },
      {
        stepId: 'ai-debate',
        name: 'AI with Tools',
        behaviourType: 'SPAR',
        config: {
          agentRef: 'agent-demo',
          challengerAgentId: 'agent-demo',
          maxRounds: 2,
          resolutionPrompt: 'Pick the stronger answer.',
          transition: 'AUTO',
        },
      },
      {
        stepId: 'decision',
        name: 'Decision',
        behaviourType: 'BRANCH',
        config: {
          contextKey: 'outcome',
          branches: { approve: 'load-resource', reject: 'stop' },
          defaultBranch: 'stop',
        },
      },
      {
        stepId: 'load-resource',
        name: 'Load External Resource',
        behaviourType: 'RESOURCE',
        config: { resourcePath: '/schema/example.json', contextKey: 'resourceResult', transition: 'AUTO' },
      },
      {
        stepId: 'reuse-workflow',
        name: 'Reuse Existing Workflow',
        behaviourType: 'WORKFLOW_BEHAVIOUR',
        config: { workflowRef: 'dev-sample', transition: 'AUTO' },
      },
      {
        stepId: 'stop',
        name: 'Stop with Error',
        behaviourType: 'FAIL',
        config: { reason: 'Terminal step for the all-step-types fixture.' },
      },
      {
        stepId: 'retry-previous',
        name: 'Retry Previous Step',
        behaviourType: 'RETRY_PREVIOUS',
        config: { retryStepId: 'ai-step', retryMode: 'SINGLE_STEP', maxAttempts: 3, fallback: 'stop' },
      },
    ],
    artifacts: {
      'artifact-ask-user': {
        id: 'artifact-ask-user',
        items: [{ id: 'topic', type: 'TEXT', label: 'Topic', required: true }],
      },
    },
  };
}

export const FIXTURES: Record<string, () => WorkflowDefinition> = {
  empty: emptyWorkflow,
  'simple-valid': simpleValidWorkflow,
  'multi-step': sampleWorkflow,
  'all-step-types': allStepTypesWorkflow,
};

/**
 * Resolve `?fixture=<name>`.
 *
 * - Absent/unrecognized → `sampleWorkflow()` (the pre-existing default the c1-c11 suite depends on).
 * - A name in {@link FIXTURES} → that fixture.
 * - `'none'` → `undefined` — omits `initialWorkflow` entirely, the one state that engages the
 *   built-in localStorage draft-recovery adapter (`WorkflowBuilder.tsx`'s
 *   `allowRestore: !readOnly && !initialWorkflow` gate suppresses restore whenever ANY
 *   `initialWorkflow` is supplied, fixtures included). Persistence-journey specs need this.
 */
export function readFixture(params: URLSearchParams): WorkflowDefinition | undefined {
  const name = params.get('fixture');
  if (name === 'none') {
    return undefined;
  }
  const factory = (name ? FIXTURES[name] : undefined) ?? sampleWorkflow;
  return factory();
}
