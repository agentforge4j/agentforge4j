// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS } from '../../copy/workflow-terminology';
import { NodeChrome } from './NodeChrome';
import type { CanvasNode } from '../../model/canvasModel';
import { NODE_KIND_META } from '../../model/nodeKinds';
import { Handle, Position } from '@xyflow/react';

export type LoopNodeProps = {
  data: {
    canvasNode: CanvasNode;
    selected?: boolean;
    issueCount?: number;
    loopBodyLabels?: string[];
  };
};

export function LoopNode({ data }: LoopNodeProps) {
  const { canvasNode: node, selected, issueCount = 0, loopBodyLabels } = data;
  if (node.kind !== 'REPEAT') {
    return null;
  }
  const d = node.data;
  const meta = NODE_KIND_META.REPEAT;
  const title = d.name?.trim() || meta.label;
  const handleClass = ['wf-handle', 'wf-handle--loop', selected ? 'wf-handle--selected' : ''].filter(Boolean).join(' ');

  return (
    <div className="wf-flow-node">
      <Handle type="target" position={Position.Top} className={handleClass} />
      <NodeChrome
        kind="REPEAT"
        variant="loop"
        title={title}
        subtitle={`${d.strategy} · up to ${d.maxIterations} iteration${d.maxIterations === 1 ? '' : 's'}`}
        selected={selected}
        issueCount={issueCount}
        className="wf-node--loop-wide"
      >
        <div className="wf-node__loop-body">
          <p className="wf-node__loop-body-title">{ACTION_LABELS.loopBodyTitle}</p>
          {loopBodyLabels && loopBodyLabels.length > 0 ? (
            <ul className="wf-node__loop-body-list">
              {loopBodyLabels.map((label, i) => (
                <li key={`${label}-${i}`} className="wf-node__loop-body-item">
                  <span className="wf-node__loop-body-bullet" aria-hidden />
                  <span>{label}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="wf-node__loop-body-empty">{ACTION_LABELS.loopBodyEmpty}</p>
          )}
        </div>
      </NodeChrome>
      <Handle type="source" position={Position.Bottom} className={handleClass} />
    </div>
  );
}
