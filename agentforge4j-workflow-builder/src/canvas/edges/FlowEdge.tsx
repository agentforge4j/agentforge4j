// SPDX-License-Identifier: Apache-2.0

import { EDGE_LABELS } from '../../copy/workflow-terminology';
import type { StepTransition } from '../../api/types';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';

type FlowEdgeData = {
  transition?: StepTransition | null;
};

function edgeStroke(transition: StepTransition | null | undefined): string {
  if (transition === 'HUMAN_APPROVAL') {
    return 'var(--builder-color-edge-approval)';
  }
  if (transition === 'HUMAN_REVIEW') {
    return 'var(--builder-color-edge-review)';
  }
  return 'var(--builder-color-edge)';
}

function edgeDash(transition: StepTransition | null | undefined): string | undefined {
  if (transition === 'HUMAN_REVIEW') {
    return '6 4';
  }
  return undefined;
}

export function FlowEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  selected,
  markerEnd,
  data,
}: EdgeProps) {
  const transition = (data as FlowEdgeData | undefined)?.transition ?? null;
  const [path, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });
  const stroke = edgeStroke(transition);
  const label =
    transition === 'HUMAN_APPROVAL'
      ? EDGE_LABELS.approvalGate
      : transition === 'HUMAN_REVIEW'
        ? EDGE_LABELS.reviewGate
        : null;

  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        style={{
          strokeWidth: selected ? 2.75 : 2,
          stroke,
          strokeDasharray: edgeDash(transition),
        }}
        markerEnd={markerEnd}
      />
      {label ? (
        <EdgeLabelRenderer>
          <div
            className="wf-edge-label nodrag nopan"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            }}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
}
