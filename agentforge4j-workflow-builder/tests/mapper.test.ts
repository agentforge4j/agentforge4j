import type { CanvasModel } from '../src/model/canvasModel';
import {
  canvasToWorkflow,
  exportStepJson,
  newStepId,
  workflowDetailToCanvas,
  workflowToCanvas,
} from '../src/model/mapper';
import type { WorkflowDetailDto } from '../src/model/mapper';
import type { EditableStep, WorkflowDefinition } from '../src/api/types';
import { validateWorkflowEditor } from '../src/validation/validateWorkflow';
import { describe, expect, it } from 'vitest';

function ask(id: string, bid: string): CanvasModel['nodes'][number] {
  return {
    id,
    backendStepId: bid,
    kind: 'ASK_USER',
    position: { x: 0, y: 0 },
    data: {
      name: 'Collect',
      question: 'What?',
      artifactItems: [{ id: 'f1', type: 'TEXT', label: 'Field', key: 'f1', required: true }],
    },
  };
}

function ai(id: string, bid: string): CanvasModel['nodes'][number] {
  return {
    id,
    backendStepId: bid,
    kind: 'AI_STEP',
    position: { x: 0, y: 100 },
    data: {
      name: 'Think',
      agentRef: 'my-agent',
      instructions: 'Do work',
      transition: 'AUTO',
      maxRetries: 0,
    },
  };
}

describe('canvasToWorkflow', () => {
  it('maps a linear flow to a valid workflow definition', () => {
    const a = ask('c-a', newStepId('ask-user'));
    const b = ai('c-b', newStepId('ai-step'));
    const model: CanvasModel = {
      workflowId: 'demo-linear',
      workflowName: 'Demo',
      description: 'Test',
      startNodeId: a.id,
      nodes: [a, b],
      edges: [{ id: 'e1', source: a.id, target: b.id, label: null, sourceHandle: null }],
      artifacts: {},
      blueprints: {},
    };
    const wf = canvasToWorkflow(model);
    const validation = validateWorkflowEditor(wf);
    expect(Object.keys(validation.workflow)).toHaveLength(0);
    expect(wf.steps.length).toBeGreaterThan(0);
  });

  it('round-trips a simple workflow through workflowToCanvas', () => {
    const a = ask('c-a', newStepId('ask-user'));
    const b = ai('c-b', newStepId('ai-step'));
    const model: CanvasModel = {
      workflowId: 'rt',
      workflowName: 'RT',
      description: '',
      startNodeId: a.id,
      nodes: [a, b],
      edges: [],
      artifacts: {},
      blueprints: {},
    };
    const wf = canvasToWorkflow(model);
    const back = workflowToCanvas(wf);
    expect(back.nodes).toHaveLength(wf.steps.length);
    expect(back.workflowId).toBe('rt');
  });
});

describe('exportStepJson retryPolicy shape', () => {
  // The runtime RetryPolicy record shrank from five fields to three (allowRetry,
  // allowRetryFromPrevious, maxAttempts); allowAgentSwap/allowPromptOverride were removed as
  // unsupported, decorative promises with no backing runtime operation, and the exporting
  // workflow.schema.json $def now declares additionalProperties: false — an exported step still
  // carrying either removed field fails schema validation for every consumer of the exported
  // bundle. Pins the exact key set for both branches AI_STEP can export (maxRetries 0 → the
  // disabled/"none" policy, maxRetries > 0 → the enabled/"simple" policy) and for AI_DEBATE
  // (SPAR), which always exports the disabled policy.
  const emptyWorkflow: WorkflowDefinition = { id: 'wf', name: 'W', description: '', steps: [], artifacts: {} };

  function agentStep(maxRetries: number): EditableStep {
    return {
      stepId: 'ai-step-1',
      name: 'Think',
      behaviourType: 'AGENT',
      config: { agentRef: 'agent-a', transition: 'AUTO', maxRetries },
      contextMapping: { inputKeys: [], outputKeys: [] },
    };
  }

  function sparStep(): EditableStep {
    return {
      stepId: 'ai-debate-1',
      name: 'Debate',
      behaviourType: 'SPAR',
      config: {
        agentRef: 'agent-a',
        challengerAgentId: 'agent-b',
        maxRounds: 2,
        resolutionPrompt: 'Resolve',
        transition: 'AUTO',
      },
      contextMapping: { inputKeys: [], outputKeys: [] },
    };
  }

  it('exports exactly the three-field disabled policy when maxRetries is 0', () => {
    const json = exportStepJson(emptyWorkflow, agentStep(0));
    const behaviour = json.behaviour as Record<string, unknown>;
    expect(behaviour.retryPolicy).toEqual({
      allowRetry: false,
      allowRetryFromPrevious: false,
      maxAttempts: 0,
    });
  });

  it('exports exactly the three-field enabled policy when maxRetries is greater than 0', () => {
    const json = exportStepJson(emptyWorkflow, agentStep(3));
    const behaviour = json.behaviour as Record<string, unknown>;
    expect(behaviour.retryPolicy).toEqual({
      allowRetry: true,
      allowRetryFromPrevious: false,
      maxAttempts: 3,
    });
  });

  it('SPAR steps export the same three-field disabled policy', () => {
    const json = exportStepJson(emptyWorkflow, sparStep());
    const behaviour = json.behaviour as Record<string, unknown>;
    expect(behaviour.retryPolicy).toEqual({
      allowRetry: false,
      allowRetryFromPrevious: false,
      maxAttempts: 0,
    });
  });
});

describe('workflowDetailToCanvas', () => {
  it('marks unsupported and records reasons when the API returns a blueprint ref', () => {
    const detail: WorkflowDetailDto = {
      id: 'wf-loopish',
      name: 'W',
      description: '',
      steps: [
        {
          kind: 'STEP',
          step: {
            stepId: 's1',
            name: 'One',
            stepPrompt: '',
            behaviour: { type: 'INPUT', artifactId: 'a1', transition: 'AUTO' },
            inputKeys: [],
            outputKeys: [],
          },
        },
        {
          kind: 'BLUEPRINT_REF',
          blueprintRef: { blueprintId: 'bp-1' },
        },
      ],
      artifacts: {},
      blueprints: { 'bp-1': {} },
    };
    const canvas = workflowDetailToCanvas(detail);
    expect(canvas.unsupported).toBe(true);
    expect(canvas.unsupportedReasons?.length).toBeGreaterThanOrEqual(2);
    expect(canvas.unsupportedReasons?.some((r) => r.includes('Blueprint reference'))).toBe(true);
  });
});
