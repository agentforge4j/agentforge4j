// SPDX-License-Identifier: Apache-2.0

import { NodeChrome } from './NodeChrome';
import type { CanvasNode } from '../../model/canvasModel';
import { NODE_KIND_META } from '../../model/nodeKinds';
import { Handle, Position } from '@xyflow/react';

export type StepNodeProps = {
  data: {
    canvasNode: CanvasNode;
    selected?: boolean;
    issueCount?: number;
    needsApproval?: boolean;
    isStart?: boolean;
  };
};

function subtitle(node: CanvasNode): string {
  switch (node.kind) {
    case 'ASK_USER': {
      const q = node.data.question?.trim() ?? '';
      return q.length > 48 ? `${q.slice(0, 48)}…` : q || NODE_KIND_META.ASK_USER.description;
    }
    case 'AI_STEP':
      return node.data.agentRef?.trim() || NODE_KIND_META.AI_STEP.description;
    case 'AI_DEBATE':
      return node.data.primaryAgentRef?.trim() || NODE_KIND_META.AI_DEBATE.description;
    case 'DECISION':
      return node.data.contextKey?.trim() || NODE_KIND_META.DECISION.description;
    case 'REPEAT':
      return `${node.data.strategy} · max ${node.data.maxIterations}`;
    case 'REUSE_WORKFLOW':
      return node.data.workflowRef?.trim() || NODE_KIND_META.REUSE_WORKFLOW.description;
    case 'LOAD_RESOURCE':
      return node.data.resourcePath?.trim() || NODE_KIND_META.LOAD_RESOURCE.description;
    case 'STOP':
      return node.data.reason?.trim() || NODE_KIND_META.STOP.description;
    case 'RETRY':
      return NODE_KIND_META.RETRY.description;
    case 'SAVE_RESULT':
      return node.data.resultName?.trim() || NODE_KIND_META.SAVE_RESULT.description;
    default:
      return '';
  }
}

export function StepNode({ data }: StepNodeProps) {
  const { canvasNode: node, selected, issueCount = 0, needsApproval = false, isStart = false } = data;
  const meta = NODE_KIND_META[node.kind];
  const title = node.data.name?.trim() || meta.label;

  return (
    <div className="wf-flow-node">
      <Handle type="target" position={Position.Top} className={['wf-handle', selected ? 'wf-handle--selected' : ''].filter(Boolean).join(' ')} />
      <NodeChrome
        kind={node.kind}
        variant="step"
        title={title}
        subtitle={subtitle(node)}
        selected={selected}
        issueCount={issueCount}
        needsApproval={needsApproval}
        isStart={isStart}
      />
      <Handle type="source" position={Position.Bottom} className={['wf-handle', selected ? 'wf-handle--selected' : ''].filter(Boolean).join(' ')} />
    </div>
  );
}
