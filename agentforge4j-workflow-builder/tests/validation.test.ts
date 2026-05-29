import { describe, expect, it } from 'vitest';
import { emptyWorkflow } from '../src/api/types';
import { validateWorkflow } from '../src/validation/validateWorkflow';

describe('validateWorkflow', () => {
  it('flags missing workflow id and name', () => {
    const result = validateWorkflow(emptyWorkflow());
    expect(result.valid).toBe(false);
    expect(result.issues.some((i) => i.path.includes('workflow.id'))).toBe(true);
    expect(result.issues.some((i) => i.path.includes('workflow.name'))).toBe(true);
  });

  it('accepts a minimal valid workflow', () => {
    const result = validateWorkflow({
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
    });
    expect(result.valid).toBe(true);
  });
});
