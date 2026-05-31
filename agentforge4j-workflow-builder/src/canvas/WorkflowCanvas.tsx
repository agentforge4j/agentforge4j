// SPDX-License-Identifier: Apache-2.0

import '@xyflow/react/dist/style.css';

import { FlowEdge } from './edges/FlowEdge';
import { EmptyState } from './empty/EmptyState';
import { DecisionNode } from './nodes/DecisionNode';
import { LoopNode } from './nodes/LoopNode';
import { StepNode } from './nodes/StepNode';
import type { CanvasModel, CanvasNode } from '../model/canvasModel';
import type { NodeKind } from '../model/nodeKinds';
import { NODE_KIND_META } from '../model/nodeKinds';
import type { StepTransition } from '../api/types';
import { ACTION_LABELS, BUILDER_COPY } from '../copy/workflow-terminology';
import {
  Background,
  BackgroundVariant,
  Controls,
  type Connection,
  type Edge,
  type EdgeChange,
  MarkerType,
  type Node as FlowNode,
  MiniMap,
  NodeToolbar,
  type Node,
  type NodeChange,
  Position,
  ReactFlow,
  ReactFlowProvider,
  applyEdgeChanges,
  applyNodeChanges,
  useNodesInitialized,
  useReactFlow,
} from '@xyflow/react';
import { useCallback, useEffect, useMemo } from 'react';

const SNAP = 16;
const DOT_GAP = 22;

function nodeTransition(node: CanvasNode): StepTransition | null {
  const data = node.data as { transition?: StepTransition };
  return data.transition ?? null;
}

function nodeNeedsApproval(node: CanvasNode): boolean {
  return nodeTransition(node) === 'HUMAN_APPROVAL';
}

function isStarterCanvas(model: CanvasModel): boolean {
  if (model.nodes.length !== 1 || model.edges.length > 0) {
    return false;
  }
  const only = model.nodes[0];
  return only?.kind === 'ASK_USER';
}

function toFlowNodes(
  model: CanvasModel,
  selectedId: string | null,
  issueCountByBackendStepId: Record<string, number>,
): Node[] {
  return model.nodes.map((n) => {
    const loopBodyLabels =
      n.kind === 'REPEAT'
        ? model.nodes
            .filter((c) => c.parentNode === n.id)
            .map((c) => (c.data.name?.trim() ? c.data.name.trim() : NODE_KIND_META[c.kind].label))
        : undefined;
    const issueCount = n.backendStepId ? (issueCountByBackendStepId[n.backendStepId] ?? 0) : 0;
    return {
      id: n.id,
      type: n.kind === 'DECISION' ? 'decision' : n.kind === 'REPEAT' ? 'loop' : 'step',
      position: n.position,
      parentId: n.parentNode,
      extent: n.parentNode ? ('parent' as const) : undefined,
      data: {
        canvasNode: n,
        selected: selectedId === n.id,
        loopBodyLabels,
        issueCount,
        needsApproval: nodeNeedsApproval(n),
      },
      draggable: true,
    };
  });
}

function toFlowEdges(model: CanvasModel): Edge[] {
  const byId = new Map(model.nodes.map((n) => [n.id, n] as const));
  return model.edges.map((e) => {
    const source = byId.get(e.source);
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle ?? undefined,
      type: 'flow',
      data: { transition: source ? nodeTransition(source) : null },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 16,
        height: 16,
        color: 'var(--builder-color-edge)',
      },
    };
  });
}

const nodeTypes = { step: StepNode, decision: DecisionNode, loop: LoopNode };
const edgeTypes = { flow: FlowEdge };

type InnerProps = {
  model: CanvasModel;
  onModelChange: (next: CanvasModel) => void;
  onSelectNode: (id: string | null) => void;
  selectedId: string | null;
  onAppend: (kind: NodeKind, position: { x: number; y: number }) => void;
  issueCountByBackendStepId: Record<string, number>;
};

function WorkflowCanvasInner({
  model,
  onModelChange,
  onSelectNode,
  selectedId,
  onAppend,
  issueCountByBackendStepId,
}: InnerProps) {
  const { screenToFlowPosition, setCenter } = useReactFlow();
  const nodesInitialized = useNodesInitialized();
  const showStarterHint = isStarterCanvas(model);

  const flowNodes = useMemo(
    () => toFlowNodes(model, selectedId, issueCountByBackendStepId),
    [issueCountByBackendStepId, model, selectedId],
  );
  const flowEdges = useMemo(() => toFlowEdges(model), [model]);

  useEffect(() => {
    if (!selectedId || !nodesInitialized) {
      return;
    }
    const node = flowNodes.find((entry) => entry.id === selectedId);
    if (!node) {
      return;
    }
    const x = node.position.x + 120;
    const y = node.position.y + 60;
    void setCenter(x, y, { zoom: 1.05, duration: 250 });
  }, [flowNodes, nodesInitialized, selectedId, setCenter]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      const nextFlow = applyNodeChanges(changes, flowNodes);
      const ids = new Set(nextFlow.map((flowNode: Node) => flowNode.id));
      const nextNodes = model.nodes
        .filter((n) => ids.has(n.id))
        .map((n) => {
          const fn = nextFlow.find((flowNode: Node) => flowNode.id === n.id)!;
          return {
            ...n,
            position: fn.position,
            parentNode: fn.parentId,
          };
        });
      onModelChange({ ...model, nodes: nextNodes });
    },
    [flowNodes, model, onModelChange],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      const nextFlow = applyEdgeChanges(changes, flowEdges);
      onModelChange({
        ...model,
        edges: nextFlow.map((e: Edge) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle ?? null,
          label: null,
        })),
      });
    },
    [flowEdges, model, onModelChange],
  );

  const onNodesDelete = useCallback(
    (deletedNodes: FlowNode[]) => {
      if (deletedNodes.length === 0) {
        return;
      }
      const deletedIds = new Set(deletedNodes.map((node) => node.id));
      onModelChange({
        ...model,
        nodes: model.nodes.filter((node) => !deletedIds.has(node.id)),
        edges: model.edges.filter((edge) => !deletedIds.has(edge.source) && !deletedIds.has(edge.target)),
      });
      if (selectedId && deletedIds.has(selectedId)) {
        onSelectNode(null);
      }
    },
    [model, onModelChange, onSelectNode, selectedId],
  );

  const onEdgesDelete = useCallback(
    (deletedEdges: Edge[]) => {
      if (deletedEdges.length === 0) {
        return;
      }
      const deletedIds = new Set(deletedEdges.map((edge) => edge.id));
      onModelChange({
        ...model,
        edges: model.edges.filter((edge) => !deletedIds.has(edge.id)),
      });
    },
    [model, onModelChange],
  );

  const deleteSelectedNode = useCallback(() => {
    if (!selectedId) {
      return;
    }
    onModelChange({
      ...model,
      nodes: model.nodes.filter((node) => node.id !== selectedId),
      edges: model.edges.filter((edge) => edge.source !== selectedId && edge.target !== selectedId),
    });
    onSelectNode(null);
  }, [model, onModelChange, onSelectNode, selectedId]);

  const onConnect = useCallback(
    (conn: Connection) => {
      if (!conn.source || !conn.target) {
        return;
      }
      const id = `e-${conn.source}-${conn.target}-${conn.sourceHandle ?? 'src'}`;
      if (
        model.edges.some(
          (e) => e.source === conn.source && e.target === conn.target && (e.sourceHandle ?? '') === (conn.sourceHandle ?? ''),
        )
      ) {
        return;
      }
      onModelChange({
        ...model,
        edges: [
          ...model.edges,
          {
            id,
            source: conn.source,
            target: conn.target,
            sourceHandle: conn.sourceHandle ?? null,
            label: null,
          },
        ],
      });
    },
    [model, onModelChange],
  );

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
  }, []);

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      const kind = e.dataTransfer.getData('application/agentforge-node-kind') as NodeKind;
      if (!kind) {
        return;
      }
      const pos = screenToFlowPosition({ x: e.clientX, y: e.clientY });
      onAppend(kind, pos);
    },
    [onAppend, screenToFlowPosition],
  );

  const startNode = model.nodes.find((n) => n.id === model.startNodeId) ?? model.nodes[0];

  return (
    <div className="wf-canvas" onDragOver={onDragOver} onDrop={onDrop}>
      <ReactFlow
        className="wf-canvas__flow"
        nodes={flowNodes}
        edges={flowEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodesDelete={onNodesDelete}
        onEdgesDelete={onEdgesDelete}
        onConnect={onConnect}
        onNodeClick={(_event, flowNode: Node) => onSelectNode(flowNode.id)}
        onPaneClick={() => onSelectNode(null)}
        snapToGrid
        snapGrid={[SNAP, SNAP]}
        fitView
        panOnDrag
        zoomOnPinch
        panOnScroll={false}
        zoomOnDoubleClick
        proOptions={{ hideAttribution: true }}
      >
        {selectedId ? (
          <NodeToolbar nodeId={selectedId} isVisible position={Position.Top} align="end" offset={10}>
            <button
              type="button"
              className="wf-button wf-button--icon wf-button--secondary"
              aria-label={ACTION_LABELS.deleteNode}
              title={ACTION_LABELS.deleteNode}
              onClick={deleteSelectedNode}
            >
              ×
            </button>
          </NodeToolbar>
        ) : null}
        <Background variant={BackgroundVariant.Dots} gap={DOT_GAP} size={1} color="var(--afb-canvas-dot)" />
        <Controls className="wf-canvas__controls" />
        <MiniMap pannable zoomable className="wf-canvas__minimap" />
      </ReactFlow>
      {showStarterHint && startNode ? (
        <div className="wf-canvas__starter-overlay">
          <div className="wf-canvas__starter-card">
            <EmptyState
              icon={<span className="wf-canvas__starter-icon" aria-hidden>◎</span>}
              title={BUILDER_COPY.startHere}
              description={NODE_KIND_META.ASK_USER.description}
              primaryAction={{
                label: ACTION_LABELS.selectStartStep,
                onClick: () => onSelectNode(startNode.id),
              }}
              secondaryAction={{
                label: BUILDER_COPY.useTemplate,
                onClick: () => {},
              }}
            />
            <p className="wf-canvas__starter-hint">
              <button type="button" className="wf-link-button" onClick={() => {}}>
                {BUILDER_COPY.templateHint}
              </button>
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export type WorkflowCanvasProps = {
  model: CanvasModel;
  onModelChange: (next: CanvasModel) => void;
  onSelectNode: (id: string | null) => void;
  selectedId: string | null;
  onAppend: (kind: NodeKind, position: { x: number; y: number }) => void;
  issueCountByBackendStepId?: Record<string, number>;
};

export function WorkflowCanvas({ issueCountByBackendStepId = {}, ...props }: WorkflowCanvasProps) {
  return (
    <ReactFlowProvider>
      <WorkflowCanvasInner {...props} issueCountByBackendStepId={issueCountByBackendStepId} />
    </ReactFlowProvider>
  );
}
