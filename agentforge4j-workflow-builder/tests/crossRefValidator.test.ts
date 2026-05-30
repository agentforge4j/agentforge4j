import { describe, expect, it } from 'vitest';
import type { AgentRef, WorkflowDefinition } from '../src/api/types';
import { validateCrossReferences } from '../src/validation/crossRefValidator';

const catalog: AgentRef[] = [{ id: 'known-agent', name: 'Known' }];

function baseWorkflow(overrides: Partial<WorkflowDefinition> = {}): WorkflowDefinition {
  return {
    id: 'wf-1',
    name: 'Workflow',
    description: '',
    steps: [],
    artifacts: {},
    ...overrides,
  };
}

describe('validateCrossReferences', () => {
  it('flags missing agentRef when not in agentCatalog', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 's1',
          name: 'Agent step',
          behaviourType: 'AGENT',
          config: { agentRef: 'missing-agent', transition: 'AUTO' },
        },
      ],
    });
    const result = validateCrossReferences(workflow, catalog);
    expect(result.issues.some((issue) => issue.path.includes('agentRef') && issue.severity === 'error')).toBe(
      true,
    );
  });

  it('flags INPUT steps referencing missing artifacts', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 's1',
          name: 'Ask',
          behaviourType: 'INPUT',
          config: { artifactId: 'missing-artifact', transition: 'AUTO' },
        },
      ],
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('artifactId'))).toBe(true);
  });

  it('flags retry steps that target a later step', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 'first',
          name: 'First',
          behaviourType: 'INPUT',
          config: { artifactId: 'a1', transition: 'AUTO' },
        },
        {
          stepId: 'retry',
          name: 'Retry',
          behaviourType: 'RETRY_PREVIOUS',
          config: {
            retryStepId: 'later',
            retryMode: 'SINGLE_STEP',
            maxAttempts: 2,
            fallback: '',
          },
        },
        {
          stepId: 'later',
          name: 'Later',
          behaviourType: 'FAIL',
          config: { reason: 'stop' },
        },
      ],
      artifacts: {
        a1: { id: 'a1', items: [{ id: 'f1', type: 'TEXT', label: 'Field', required: true }] },
      },
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('retryStepId'))).toBe(true);
  });

  it('accepts retry steps that target an earlier step', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 'first',
          name: 'First',
          behaviourType: 'INPUT',
          config: { artifactId: 'a1', transition: 'AUTO' },
        },
        {
          stepId: 'retry',
          name: 'Retry',
          behaviourType: 'RETRY_PREVIOUS',
          config: {
            retryStepId: 'first',
            retryMode: 'SINGLE_STEP',
            maxAttempts: 2,
            fallback: '',
          },
        },
      ],
      artifacts: {
        a1: { id: 'a1', items: [{ id: 'f1', type: 'TEXT', label: 'Field', required: true }] },
      },
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('retryStepId'))).toBe(false);
  });

  it('flags duplicate step ids', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 'dup',
          name: 'One',
          behaviourType: 'FAIL',
          config: { reason: 'a' },
        },
        {
          stepId: 'dup',
          name: 'Two',
          behaviourType: 'FAIL',
          config: { reason: 'b' },
        },
      ],
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('stepId'))).toBe(true);
  });

  it('flags branch targets referencing missing blueprints', () => {
    const workflow = baseWorkflow({
      steps: [
        {
          stepId: 'branch',
          name: 'Branch',
          behaviourType: 'BRANCH',
          config: {
            contextKey: 'flag',
            branches: { yes: 'missing-blueprint' },
            defaultBranch: 'missing-blueprint',
          },
        },
      ],
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('branches.yes'))).toBe(true);
  });

  it('flags blank workflow id', () => {
    const result = validateCrossReferences(baseWorkflow({ id: '' }));
    expect(result.issues.some((issue) => issue.path === 'workflow.id')).toBe(true);
  });

  it('flags FOR_EACH loop config without forEachContextKey', () => {
    const workflow = baseWorkflow({
      blueprintBodies: {
        loop: {
          kind: 'BLUEPRINT',
          blueprintId: 'loop',
          name: 'Loop',
          behaviour: {
            transition: 'AUTO',
            loopConfig: {
              terminationStrategy: 'FOR_EACH',
              maxIterations: 3,
            },
          },
          steps: [
            {
              kind: 'STEP',
              stepId: 'inner',
              name: 'Inner',
              behaviour: { type: 'FAIL', reason: 'stop' },
            },
          ],
        },
      },
    });
    const result = validateCrossReferences(workflow);
    expect(result.issues.some((issue) => issue.path.includes('forEachContextKey'))).toBe(true);
  });
});
