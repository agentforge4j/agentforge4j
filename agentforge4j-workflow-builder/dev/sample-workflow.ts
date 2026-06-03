// SPDX-License-Identifier: Apache-2.0

import type { WorkflowDefinition } from '../src/api/types';

/**
 * Minimal valid WorkflowDefinition for the dev playground.
 * SAVE_RESULT and REPEAT are canvas-only node kinds — they do not round-trip through
 * workflowToCanvas — so the save/loop branch targets use FAIL (STOP) and RETRY_PREVIOUS
 * (RETRY) behaviour types that map to visible canvas nodes with the same labels.
 */
export function sampleWorkflow(): WorkflowDefinition {
  return {
    id: 'dev-sample',
    name: 'Dev Sample Workflow',
    description: 'Playground sample for canvas redesign',
    steps: [
      {
        stepId: 'ask-user-dev',
        name: 'Collect Input',
        behaviourType: 'INPUT',
        config: { artifactId: 'artifact-ask-user-dev', transition: 'AUTO' },
      },
      {
        stepId: 'ai-step-dev',
        name: 'AI Analysis',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-demo', transition: 'HUMAN_APPROVAL', maxRetries: 0 },
        stepPrompt: 'Analyze the collected input and propose next steps.',
      },
      {
        stepId: 'decision-dev',
        name: 'Route Outcome',
        behaviourType: 'BRANCH',
        config: {
          contextKey: 'outcome',
          branches: { save: 'save-result-dev', loop: 'repeat-dev' },
          defaultBranch: 'repeat-dev',
        },
      },
      {
        stepId: 'save-result-dev',
        name: 'Save Result',
        behaviourType: 'FAIL',
        config: { reason: 'Workflow result saved.' },
      },
      {
        stepId: 'repeat-dev',
        name: 'Loop',
        behaviourType: 'RETRY_PREVIOUS',
        config: {
          retryStepId: 'ai-step-dev',
          retryMode: 'SINGLE_STEP',
          maxAttempts: 3,
          fallback: 'save-result-dev',
        },
      },
    ],
    artifacts: {
      'artifact-ask-user-dev': {
        id: 'artifact-ask-user-dev',
        items: [{ id: 'topic', type: 'TEXT', label: 'Topic', required: true }],
      },
    },
  };
}
