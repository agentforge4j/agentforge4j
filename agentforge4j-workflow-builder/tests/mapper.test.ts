import type { CanvasModel } from '../src/model/canvasModel';
import {
  canvasToWorkflow,
  newStepId,
  workflowDetailToCanvas,
  workflowToCanvas,
} from '../src/model/mapper';
import type { WorkflowDetailDto } from '../src/model/mapper';
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
