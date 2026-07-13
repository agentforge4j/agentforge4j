// SPDX-License-Identifier: Apache-2.0

import '@xyflow/react/dist/style.css';

import { EdgeDefs } from './edges/EdgeDefs';
import { edgeVisual, FlowEdge } from './edges/FlowEdge';
import { EmptyState } from './empty/EmptyState';
import { DecisionNode } from './nodes/DecisionNode';
import { LoopNode } from './nodes/LoopNode';
import { StepNode } from './nodes/StepNode';
import type { CanvasModel, CanvasNode } from '../model/canvasModel';
import { isInsertableEdge, pruneReferences } from '../model/graphOps';
import type { NodeKind } from '../model/nodeKinds';
import { NODE_KIND_META } from '../model/nodeKinds';
import type { StepTransition } from '../api/types';
import { ACTION_LABELS, BUILDER_COPY } from '../copy/workflow-terminology';
import type { HistorySetOptions } from '../state/useHistoryState';
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
  type Node,
  type NodeChange,
  ReactFlow,
  ReactFlowProvider,
  applyEdgeChanges,
  applyNodeChanges,
  reconnectEdge,
  useNodesInitialized,
  useNodesState,
  useReactFlow,
} from '@xyflow/react';
import { useCallback, useEffect, useMemo, useRef } from 'react';

/** Coalesce key for a node-drag gesture: every intermediate tick of one drag
 * folds into a single undo step, sealed on release. */
const NODE_DRAG_COALESCE_KEY = 'node-drag';

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

/**
 * Pure decision logic behind the canvas Delete/Backspace confirmation gate
 * (`<ReactFlow onBeforeDelete>`), extracted so it is directly unit-testable
 * without driving React Flow's own internal node-selection state — a real
 * browser is required to exercise that end-to-end (React Flow's click-to-select
 * wiring depends on measured node dimensions, unavailable under jsdom; see the
 * other canvas tests in this suite for the same constraint).
 *
 * Read-only always refuses. Edge-only deletions (no nodes in the batch) are
 * approved immediately — undo already covers reverting those, and an edge
 * disappearing is not the "step vanished with no warning" complaint the
 * confirmation exists for. A node deletion with no `confirmNodeDeletion`
 * handler wired is approved immediately (no gate configured).
 */
export function resolveNodeDeletionGate(
  nodeIds: string[],
  options: { readOnly: boolean; confirmNodeDeletion?: (ids: string[]) => Promise<boolean> },
): Promise<boolean> {
  if (options.readOnly) {
    return Promise.resolve(false);
  }
  if (nodeIds.length === 0 || !options.confirmNodeDeletion) {
    return Promise.resolve(true);
  }
  return options.confirmNodeDeletion(nodeIds);
}

/** Merge domain-derived nodes onto existing RF nodes, keeping measured dimensions. */
export function mergeModelIntoFlowNodes(existing: Node[], derived: Node[]): Node[] {
  const existingById = new Map(existing.map((node) => [node.id, node] as const));
  return derived.map((derivedNode) => {
    const prior = existingById.get(derivedNode.id);
    if (!prior) {
      return derivedNode;
    }
    return {
      ...derivedNode,
      measured: prior.measured,
      width: prior.width,
      height: prior.height,
    };
  });
}

function nodeChangeUpdatesModel(change: NodeChange): boolean {
  if (change.type === 'dimensions') {
    return false;
  }
  if (change.type === 'select') {
    return false;
  }
  return true;
}

function toFlowNodes(
  model: CanvasModel,
  selectedId: string | null,
  issueCountByBackendStepId: Record<string, number>,
  readOnly: boolean,
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
      draggable: !readOnly,
    };
  });
}

function toFlowEdges(
  model: CanvasModel,
  options: { readOnly: boolean; onInsert?: (edgeId: string) => void },
): Edge[] {
  const byId = new Map(model.nodes.map((n) => [n.id, n] as const));
  return model.edges.map((e) => {
    const source = byId.get(e.source);
    const transition = source ? nodeTransition(source) : null;
    const { markerColor } = edgeVisual(transition);
    // Scope-safe: only offer the edge-insert "+" on edges that can actually be
    // split without corrupting loop/branch scope (see isInsertableEdge).
    const insertable = !options.readOnly && isInsertableEdge(model, e.id);
    return {
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle ?? undefined,
      type: 'flow',
      data: { transition, insertable, onInsert: insertable ? options.onInsert : undefined },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 12,
        height: 12,
        color: markerColor,
      },
    };
  });
}

const nodeTypes = { step: StepNode, decision: DecisionNode, loop: LoopNode };
const edgeTypes = { flow: FlowEdge };

type InnerProps = {
  model: CanvasModel;
  onModelChange: (next: CanvasModel, options?: HistorySetOptions) => void;
  onSelectNode: (id: string | null) => void;
  selectedId: string | null;
  onAppend: (kind: NodeKind, position: { x: number; y: number }) => void;
  issueCountByBackendStepId: Record<string, number>;
  readOnly: boolean;
  onInsertOnEdge?: (edgeId: string) => void;
  /** Suppresses the "Select start step" starter-hint overlay even on a fresh starter canvas.
   * Used when the guided-mode stepper panel is already showing the same call to action, so the
   * two overlays (each sized to their own content) don't compete for the same canvas space —
   * most visible on a short/narrow viewport, where together they don't fit. Defaults to false;
   * outside guided mode the starter hint's behavior is unchanged. */
  hideStarterHint?: boolean;
  /**
   * Gate invoked before a Delete/Backspace-triggered removal of one or more
   * selected nodes is applied; resolving `false` cancels the whole deletion
   * (nodes and any co-selected edges). Edge-only deletions never go through
   * this gate — only node deletion is destructive enough to warrant
   * confirmation. Omit to apply node deletions immediately (no confirmation).
   */
  confirmNodeDeletion?: (nodeIds: string[]) => Promise<boolean>;
};

function WorkflowCanvasInner({
  model,
  onModelChange,
  onSelectNode,
  selectedId,
  onAppend,
  issueCountByBackendStepId,
  readOnly,
  onInsertOnEdge,
  hideStarterHint = false,
  confirmNodeDeletion,
}: InnerProps) {
  const { screenToFlowPosition, setCenter } = useReactFlow();
  const nodesInitialized = useNodesInitialized();
  const showStarterHint = isStarterCanvas(model) && !hideStarterHint;

  const derivedFlowNodes = useMemo(
    () => toFlowNodes(model, selectedId, issueCountByBackendStepId, readOnly),
    [issueCountByBackendStepId, model, readOnly, selectedId],
  );
  const [flowNodes, setFlowNodes, onFlowNodesChange] = useNodesState(derivedFlowNodes);
  const flowNodesRef = useRef(flowNodes);
  flowNodesRef.current = flowNodes;

  useEffect(() => {
    setFlowNodes((current) => mergeModelIntoFlowNodes(current, derivedFlowNodes));
  }, [derivedFlowNodes, setFlowNodes]);

  const flowEdges = useMemo(
    () => toFlowEdges(model, { readOnly, onInsert: onInsertOnEdge }),
    [model, onInsertOnEdge, readOnly],
  );

  useEffect(() => {
    if (!selectedId || !nodesInitialized) {
      return;
    }
    const node = flowNodes.find((entry) => entry.id === selectedId);
    if (!node) {
      return;
    }
    const INSPECTOR_WIDTH = 380;
    const CENTER_ZOOM = 1.05;
    const isDesktop =
      typeof window !== 'undefined' &&
      window.matchMedia('(min-width: 48rem)').matches;
    const offsetX = isDesktop ? INSPECTOR_WIDTH / 2 / CENTER_ZOOM : 0;
    const x = node.position.x + 120 + offsetX;
    const y = node.position.y + 60;
    void setCenter(x, y, { zoom: CENTER_ZOOM, duration: 250 });
  }, [flowNodes, nodesInitialized, selectedId, setCenter]);

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      onFlowNodesChange(changes);

      // Read-only: selection/measurement still apply to the flow view above,
      // but never write structural changes back to the model.
      if (readOnly || !changes.some(nodeChangeUpdatesModel)) {
        return;
      }

      // Only a 'position' change actually needs remapping onto the model here;
      // 'remove' changes are applied by onNodesDelete instead (a plain lookup
      // miss below would otherwise leave the deleted node's stale entry
      // untouched and still emit a no-op model update — polluting undo with a
      // spurious extra step for every delete).
      const positionChanges = changes.filter((change) => change.type === 'position');
      if (positionChanges.length === 0) {
        return;
      }

      const nextFlow = applyNodeChanges(changes, flowNodesRef.current);
      const byId = new Map(nextFlow.map((fn: Node) => [fn.id, fn] as const));
      const nextNodes = model.nodes.map((n) => {
        const fn = byId.get(n.id);
        return fn ? { ...n, position: fn.position, parentNode: fn.parentId } : n;
      });
      // A drag gesture fires many intermediate 'position' changes (dragging: true)
      // followed by one final change (dragging: false) on release; coalesce the
      // whole gesture into a single undo step, sealed at release.
      const dragEnded = positionChanges.some((change) => change.dragging === false);
      onModelChange({ ...model, nodes: nextNodes }, { coalesceKey: NODE_DRAG_COALESCE_KEY, commit: dragEnded });
    },
    [model, onFlowNodesChange, onModelChange, readOnly],
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      if (readOnly) {
        return;
      }
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
    [flowEdges, model, onModelChange, readOnly],
  );

  const onNodesDelete = useCallback(
    (deletedNodes: FlowNode[]) => {
      if (readOnly || deletedNodes.length === 0) {
        return;
      }
      const deletedIds = new Set(deletedNodes.map((node) => node.id));
      const nodes = model.nodes.filter((node) => !deletedIds.has(node.id));
      const startNodeId =
        model.startNodeId && deletedIds.has(model.startNodeId) ? (nodes[0]?.id ?? null) : model.startNodeId;
      // Scrub node-data references to every deleted node so serialization never
      // receives a dangling id (Part: delete-cleanup).
      let next: CanvasModel = {
        ...model,
        nodes,
        edges: model.edges.filter((edge) => !deletedIds.has(edge.source) && !deletedIds.has(edge.target)),
        startNodeId,
      };
      for (const deletedId of deletedIds) {
        next = pruneReferences(next, deletedId);
      }
      onModelChange(next);
      if (selectedId && deletedIds.has(selectedId)) {
        onSelectNode(null);
      }
    },
    [model, onModelChange, onSelectNode, readOnly, selectedId],
  );

  const onEdgesDelete = useCallback(
    (deletedEdges: Edge[]) => {
      if (readOnly || deletedEdges.length === 0) {
        return;
      }
      const deletedIds = new Set(deletedEdges.map((edge) => edge.id));
      onModelChange({
        ...model,
        edges: model.edges.filter((edge) => !deletedIds.has(edge.id)),
      });
    },
    [model, onModelChange, readOnly],
  );

  const onConnect = useCallback(
    (conn: Connection) => {
      if (readOnly || !conn.source || !conn.target) {
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
    [model, onModelChange, readOnly],
  );

  // Dragging an existing edge's endpoint to a different node/handle
  // ("rerouting"). `edgesReconnectable` alone only enables the drag handle —
  // without this handler the reconnect is never written back to the model and
  // the edge snaps back to its original endpoints on the next render.
  const onReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) => {
      if (readOnly) {
        return;
      }
      const nextFlowEdges = reconnectEdge(oldEdge, newConnection, flowEdges);
      onModelChange({
        ...model,
        edges: nextFlowEdges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          sourceHandle: e.sourceHandle ?? null,
          label: null,
        })),
      });
    },
    [flowEdges, model, onModelChange, readOnly],
  );

  // Gate the Delete/Backspace-triggered removal behind confirmation for node
  // deletions (the destructive case the usability audit flagged); edge-only
  // deletions (no nodes in the batch) proceed immediately — undo already
  // covers reverting those, and they are not the "step vanished with no
  // warning" complaint. Resolving `false` cancels the whole batch before any
  // change ever reaches onNodesChange/onNodesDelete/onEdgesDelete.
  const onBeforeDelete = useCallback(
    ({ nodes: toDeleteNodes }: { nodes: FlowNode[]; edges: Edge[] }): Promise<boolean> =>
      resolveNodeDeletionGate(
        toDeleteNodes.map((node) => node.id),
        { readOnly, confirmNodeDeletion },
      ),
    [confirmNodeDeletion, readOnly],
  );

  const onDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.dataTransfer.dropEffect = readOnly ? 'none' : 'copy';
    },
    [readOnly],
  );

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      if (readOnly) {
        return;
      }
      const kind = e.dataTransfer.getData('application/agentforge-node-kind') as NodeKind;
      if (!kind) {
        return;
      }
      const pos = screenToFlowPosition({ x: e.clientX, y: e.clientY });
      onAppend(kind, pos);
    },
    [onAppend, readOnly, screenToFlowPosition],
  );

  const startNode = model.nodes.find((n) => n.id === model.startNodeId) ?? model.nodes[0];

  return (
    <div className="wf-canvas" onDragOver={onDragOver} onDrop={onDrop}>
      <EdgeDefs />
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
        onBeforeDelete={onBeforeDelete}
        onConnect={onConnect}
        onReconnect={onReconnect}
        onNodeClick={(_event, flowNode: Node) => onSelectNode(flowNode.id)}
        onPaneClick={() => onSelectNode(null)}
        nodesDraggable={!readOnly}
        nodesConnectable={!readOnly}
        edgesReconnectable={!readOnly}
        elementsSelectable
        deleteKeyCode={readOnly ? null : undefined}
        snapToGrid
        snapGrid={[SNAP, SNAP]}
        fitView
        panOnDrag
        zoomOnPinch
        panOnScroll={false}
        zoomOnDoubleClick
        proOptions={{ hideAttribution: true }}
      >
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
            />
            <p className="wf-canvas__starter-hint">
              <strong>{BUILDER_COPY.useTemplate}</strong>
              {BUILDER_COPY.templateHint}
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export type WorkflowCanvasProps = {
  model: CanvasModel;
  onModelChange: (next: CanvasModel, options?: HistorySetOptions) => void;
  onSelectNode: (id: string | null) => void;
  selectedId: string | null;
  onAppend: (kind: NodeKind, position: { x: number; y: number }) => void;
  issueCountByBackendStepId?: Record<string, number>;
  readOnly?: boolean;
  onInsertOnEdge?: (edgeId: string) => void;
  hideStarterHint?: boolean;
  confirmNodeDeletion?: (nodeIds: string[]) => Promise<boolean>;
};

export function WorkflowCanvas({
  issueCountByBackendStepId = {},
  readOnly = false,
  ...props
}: WorkflowCanvasProps) {
  return (
    <ReactFlowProvider>
      <WorkflowCanvasInner
        {...props}
        issueCountByBackendStepId={issueCountByBackendStepId}
        readOnly={readOnly}
      />
    </ReactFlowProvider>
  );
}
