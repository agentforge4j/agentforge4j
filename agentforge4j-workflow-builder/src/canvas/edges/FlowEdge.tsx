// SPDX-License-Identifier: Apache-2.0

import { ACTION_LABELS, EDGE_LABELS } from '../../copy/workflow-terminology';
import type { StepTransition } from '../../api/types';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';
import { Eye, Plus, UserCheck } from 'lucide-react';

type FlowEdgeData = {
  transition?: StepTransition | null;
  insertable?: boolean;
  onInsert?: (edgeId: string) => void;
};

export type EdgeVisualVariant = 'default' | 'approval' | 'review';

export type EdgeVisual = {
  variant: EdgeVisualVariant;
  stroke: string;
  strokeDasharray: string | undefined;
  label: string | null;
  markerColor: string;
};

export function edgeVisual(transition: StepTransition | null | undefined): EdgeVisual {
  if (transition === 'HUMAN_APPROVAL') {
    return {
      variant: 'approval',
      stroke: 'var(--afb-human)',
      strokeDasharray: '7 5',
      label: EDGE_LABELS.approvalGate,
      markerColor: 'var(--afb-human)',
    };
  }
  if (transition === 'HUMAN_REVIEW') {
    return {
      variant: 'review',
      stroke: 'var(--afb-chrome-muted)',
      strokeDasharray: '2 5',
      label: EDGE_LABELS.reviewGate,
      markerColor: 'var(--afb-chrome-muted)',
    };
  }
  return {
    variant: 'default',
    stroke: 'url(#afb-edge-gradient)',
    strokeDasharray: undefined,
    label: null,
    markerColor: 'var(--afb-blue-500)',
  };
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
  const edgeData = data as FlowEdgeData | undefined;
  const transition = edgeData?.transition ?? null;
  const onInsert = edgeData?.onInsert;
  const insertable = Boolean(edgeData?.insertable && onInsert);
  const visual = edgeVisual(transition);
  const [path, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        style={{
          strokeWidth: selected ? 3 : 2.4,
          stroke: visual.stroke,
          strokeDasharray: visual.strokeDasharray,
        }}
        markerEnd={markerEnd}
      />
      {visual.variant === 'approval' && visual.label ? (
        <EdgeLabelRenderer>
          <div
            className="wf-edge-label wf-edge-pill wf-edge-pill--approval nodrag nopan"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            }}
          >
            <UserCheck className="wf-edge-pill__icon" aria-hidden size={12} strokeWidth={2.25} />
            <span>{visual.label}</span>
          </div>
        </EdgeLabelRenderer>
      ) : null}
      {visual.variant === 'review' ? (
        <EdgeLabelRenderer>
          <div
            className="wf-edge-label wf-edge-pill wf-edge-pill--review nodrag nopan"
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            }}
          >
            <Eye className="wf-edge-pill__icon" aria-hidden size={12} strokeWidth={2.25} />
            {visual.label ? <span>{visual.label}</span> : null}
          </div>
        </EdgeLabelRenderer>
      ) : null}
      {insertable ? (
        <EdgeLabelRenderer>
          <button
            type="button"
            className="wf-edge-insert nodrag nopan"
            data-testid={`edge-insert-${id}`}
            aria-label={ACTION_LABELS.insertStepHere}
            title={ACTION_LABELS.insertStepHere}
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
            }}
            onClick={(event) => {
              event.stopPropagation();
              onInsert?.(id);
            }}
          >
            <Plus aria-hidden size={12} strokeWidth={2.5} />
          </button>
        </EdgeLabelRenderer>
      ) : null}
    </>
  );
}
