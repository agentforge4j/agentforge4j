// SPDX-License-Identifier: Apache-2.0

import { NODE_LABELS } from '../src/copy/workflow-terminology';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';
import type { NodeKind } from '../src/model/nodeKinds';

const COL = 260;
const ROW = 240;

function stepNode(
  id: string,
  kind: NodeKind,
  x: number,
  y: number,
  overrides: Record<string, unknown> = {},
): CanvasNode {
  return {
    id,
    backendStepId: id.slice(2),
    kind,
    position: { x, y },
    data: { ...defaultNodeData(kind), ...overrides },
  } as CanvasNode;
}

export function createGalleryModel(): CanvasModel {
  const repeatId = 'c-repeat';
  const repeatChildId = 'c-repeat-child';
  const decisionId = 'c-decision';

  const nodes: CanvasNode[] = [
    stepNode('c-ask-user', 'ASK_USER', 0, 0, { name: 'User Input', question: 'What is your goal?' }),
    stepNode('c-ai-step', 'AI_STEP', COL, 0, {
      name: 'Summarize',
      agentRef: 'agent-demo',
      instructions: 'Summarize the user input.',
    }),
    stepNode('c-ai-debate', 'AI_DEBATE', COL * 2, 0, {
      name: 'Tool debate',
      primaryAgentRef: 'agent-demo',
    }),
    stepNode('c-save-result', 'SAVE_RESULT', COL * 3, 0, {
      name: 'Persist output',
      resultName: 'finalResult',
    }),
    {
      id: decisionId,
      backendStepId: 'decision',
      kind: 'DECISION',
      position: { x: 0, y: ROW },
      data: {
        name: 'Route by score',
        contextKey: 'score',
        cases: [
          { label: 'High', value: 'high', targetNodeId: '' },
          { label: 'Low', value: 'low', targetNodeId: '' },
        ],
        defaultTargetNodeId: '',
      },
    },
    {
      id: repeatId,
      backendStepId: 'repeat',
      kind: 'REPEAT',
      position: { x: COL, y: ROW },
      data: {
        name: 'Retry loop',
        strategy: 'FIXED_COUNT',
        maxIterations: 3,
        maxIterationsAction: 'AWAIT_USER',
        bodyNodeIds: [repeatChildId],
      },
    },
    {
      id: repeatChildId,
      backendStepId: 'repeat-child',
      kind: 'AI_STEP',
      parentNode: repeatId,
      position: { x: 48, y: 140 },
      data: {
        name: 'Body step',
        agentRef: 'agent-demo',
        instructions: 'Process one iteration.',
        transition: 'AUTO',
        maxRetries: 0,
      },
    },
    stepNode('c-reuse-wf', 'REUSE_WORKFLOW', COL * 2, ROW, {
      name: 'Nested flow',
      workflowRef: 'sub-workflow',
    }),
    stepNode('c-load-resource', 'LOAD_RESOURCE', COL * 3, ROW, {
      name: 'Load schema',
      resourcePath: '/schema/workflow.json',
      resultName: 'schemaDoc',
    }),
    stepNode('c-stop', 'STOP', 0, ROW * 2, { name: 'Fatal error', reason: 'Unrecoverable failure' }),
    stepNode('c-retry', 'RETRY', COL, ROW * 2, { name: 'Retry prior step' }),
  ];

  return {
    workflowId: 'node-gallery',
    workflowName: 'Node Gallery',
    description: 'All node kinds for Phase B visual review',
    startNodeId: 'c-ask-user',
    nodes,
    edges: [],
    artifacts: {},
    blueprints: {},
  };
}

export const GALLERY_KIND_LABELS = Object.values(NODE_LABELS);
