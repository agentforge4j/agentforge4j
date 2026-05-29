// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../../copy/workflow-terminology';
import { NodeChrome } from './NodeChrome';
import type { CanvasNode } from '../../model/canvasModel';
import { NODE_KIND_META } from '../../model/nodeKinds';
import { Handle, Position } from '@xyflow/react';

export type DecisionNodeProps = {
  data: {
    canvasNode: CanvasNode;
    selected?: boolean;
    issueCount?: number;
  };
};

export function DecisionNode({ data }: DecisionNodeProps) {
  const { canvasNode: node, selected, issueCount = 0 } = data;
  if (node.kind !== 'DECISION') {
    return null;
  }
  const d = node.data;
  const meta = NODE_KIND_META.DECISION;
  const title = d.name?.trim() || meta.label;
  const handleClass = ['wf-handle', 'wf-handle--decision', selected ? 'wf-handle--selected' : ''].filter(Boolean).join(' ');

  return (
    <div className="wf-flow-node">
      <Handle type="target" position={Position.Top} className={handleClass} />
      <NodeChrome
        kind="DECISION"
        variant="decision"
        title={title}
        subtitle={d.contextKey?.trim() || meta.description}
        selected={selected}
        issueCount={issueCount}
      >
        <div className="wf-node__branches">
          {d.cases.map((c) => (
            <div key={c.value} className="wf-node__branch">
              <span className="wf-node__branch-label">{c.label || c.value}</span>
              <Handle type="source" position={Position.Right} id={c.value} className={handleClass} />
            </div>
          ))}
          <div className="wf-node__branch">
            <span className="wf-node__branch-label">{ACTION_LABELS.otherwiseBranch}</span>
            <Handle type="source" position={Position.Right} id="default" className={handleClass} />
          </div>
        </div>
      </NodeChrome>
    </div>
  );
}
