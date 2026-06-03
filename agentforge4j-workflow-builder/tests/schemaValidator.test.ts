import { describe, expect, it } from 'vitest';
import type { WorkflowDefinition } from '../src/api/types';
import { validateAgainstSchema } from '../src/validation/schemaValidator';

function minimalWorkflow(overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition {
  return {
    id: 'demo-flow',
    name: 'Demo',
    description: '',
    steps: [
      {
        stepId: 'ask-user-1',
        name: 'Ask',
        behaviourType: 'INPUT',
        config: { artifactId: 'artifact-ask-user-1', transition: 'AUTO' },
      },
      {
        stepId: 'ai-step-1',
        name: 'Think',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-a', transition: 'AUTO', maxRetries: 0 },
      },
    ],
    artifacts: {
      'artifact-ask-user-1': {
        id: 'artifact-ask-user-1',
        items: [{ id: 'value', type: 'TEXT', label: 'Value', required: true }],
      },
    },
    ...overrides,
  };
}

describe('validateAgainstSchema', () => {
  it('passes for a valid minimal workflow definition', () => {
    const result = validateAgainstSchema(minimalWorkflow());
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });

  it('fails when workflow id is missing in the runtime document', () => {
    const result = validateAgainstSchema(minimalWorkflow({ id: '' }));
    expect(result.valid).toBe(false);
    expect(result.errors.some((error) => error.path.includes('id'))).toBe(true);
  });

  it('fails when a step has an unrecognised executable kind', () => {
    const broken = minimalWorkflow();
    broken.steps = [
      {
        stepId: 'bad-step',
        name: 'Bad',
        behaviourType: 'AGENT',
        config: { agentRef: 'agent-a', transition: 'AUTO' },
      },
    ];
    const result = validateAgainstSchema({
      ...broken,
      steps: broken.steps.map((step) => ({
        ...step,
        behaviourType: 'INVALID' as 'AGENT',
      })),
    });
    expect(result.valid).toBe(false);
  });
});
