// @vitest-environment jsdom
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { NODE_LABELS, NODE_STATUS_LABELS } from '../src/copy/workflow-terminology';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';

function createNodeTestModel(): CanvasModel {
  const repeatId = 'c-repeat';

  const askUser = {
    id: 'c-ask-user',
    backendStepId: 'ask-user',
    kind: 'ASK_USER',
    position: { x: 0, y: 0 },
    data: { ...defaultNodeData('ASK_USER'), name: 'Collect input' },
  } as CanvasNode;

  const aiStep = {
    id: 'c-ai-step',
    backendStepId: 'ai-step',
    kind: 'AI_STEP',
    position: { x: 260, y: 0 },
    data: {
      ...defaultNodeData('AI_STEP'),
      name: 'Generate summary',
      agentRef: 'agent-1',
    },
  } as CanvasNode;

  const decision: CanvasNode = {
    id: 'c-decision',
    backendStepId: 'decision',
    kind: 'DECISION',
    position: { x: 0, y: 220 },
    data: {
      name: 'Branch',
      contextKey: 'score',
      cases: [
        { label: 'Yes', value: 'yes', targetNodeId: '' },
        { label: 'No', value: 'no', targetNodeId: '' },
      ],
      defaultTargetNodeId: '',
    },
  };

  const repeat: CanvasNode = {
    id: repeatId,
    backendStepId: 'repeat',
    kind: 'REPEAT',
    position: { x: 260, y: 220 },
    data: {
      name: 'Iterate',
      strategy: 'FIXED_COUNT',
      maxIterations: 2,
      maxIterationsAction: 'AWAIT_USER',
      bodyNodeIds: ['c-repeat-child'],
    },
  };

  const repeatChild = {
    id: 'c-repeat-child',
    backendStepId: 'repeat-child',
    kind: 'AI_STEP',
    parentNode: repeatId,
    position: { x: 40, y: 120 },
    data: {
      ...defaultNodeData('AI_STEP'),
      name: 'Loop body step',
      agentRef: 'agent-1',
    },
  } as CanvasNode;

  const saveResult = {
    id: 'c-save-result',
    backendStepId: 'save-result',
    kind: 'SAVE_RESULT',
    position: { x: 520, y: 0 },
    data: { ...defaultNodeData('SAVE_RESULT'), name: 'Save output', resultName: 'result' },
  } as CanvasNode;

  return {
    workflowId: 'node-test',
    workflowName: 'Node test',
    description: '',
    startNodeId: 'c-ask-user',
    nodes: [askUser, aiStep, decision, repeat, repeatChild, saveResult],
    edges: [],
    artifacts: {},
    blueprints: {},
  };
}

describe('WorkflowCanvas node cards', () => {
  it('renders kind labels and lucide icons for representative node kinds', () => {
    const model = createNodeTestModel();
    const { container } = render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );

    expect(screen.getByText(NODE_LABELS.ASK_USER)).toBeTruthy();
    expect(screen.getAllByText(NODE_LABELS.AI_STEP).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(NODE_LABELS.DECISION)).toBeTruthy();
    expect(screen.getByText(NODE_LABELS.REPEAT)).toBeTruthy();
    expect(screen.getByText(NODE_LABELS.SAVE_RESULT)).toBeTruthy();
    expect(container.querySelectorAll('.wf-node__icon svg').length).toBeGreaterThanOrEqual(5);
  });

  it('shows Ready when there are no issues and Needs attention when issueCount is set', () => {
    const model = createNodeTestModel();
    const { rerender } = render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
        issueCountByBackendStepId={{}}
      />,
    );

    expect(screen.getAllByText(NODE_STATUS_LABELS.valid).length).toBeGreaterThan(0);

    rerender(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
        issueCountByBackendStepId={{ 'ask-user': 2 }}
      />,
    );

    expect(screen.getByText(NODE_STATUS_LABELS.hasIssues)).toBeTruthy();
  });
});
