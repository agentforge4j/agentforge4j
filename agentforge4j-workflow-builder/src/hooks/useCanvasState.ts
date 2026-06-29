// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel, CanvasNode, NodeData } from '../model/canvasModel';
import { defaultNodeData, newStepId } from '../model/mapper';
import type { NodeKind } from '../model/nodeKinds';
import { useCallback, useState } from 'react';

export function createInitialCanvasModel(): CanvasModel {
  const backendStepId = newStepId('ask-user');
  const id = `c-${backendStepId}`;
  return {
    workflowId: '',
    workflowName: '',
    description: '',
    startNodeId: id,
    nodes: [
      {
        id,
        backendStepId,
        kind: 'ASK_USER',
        position: { x: 80, y: 120 },
        data: defaultNodeData('ASK_USER'),
      } as CanvasNode,
    ],
    edges: [],
    artifacts: {},
    blueprints: {},
  };
}

/** Dev-time guard message when a mutation is rejected in read-only mode. */
function warnReadOnlyBlocked(operation: string): void {
  if (typeof console !== 'undefined' && typeof console.warn === 'function') {
    console.warn(`[workflow-builder] Ignored "${operation}" while in read-only mode.`);
  }
}

export function useCanvasState(initialModel: CanvasModel, readOnly = false) {
  const [model, setModel] = useState<CanvasModel>(initialModel);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);

  const markDirty = useCallback(() => {
    setIsDirty(true);
  }, []);

  const markClean = useCallback(() => {
    setIsDirty(false);
  }, []);

  // Import-path setter (file-import → fresh model, clears dirty). This is a user
  // mutation, so it is correctly blocked in read-only mode; initial/host load happens
  // via the mount-time initial model, not through here.
  const setModelFromLoad = useCallback(
    (next: CanvasModel | ((prev: CanvasModel) => CanvasModel)) => {
      if (readOnly) {
        warnReadOnlyBlocked('load model');
        return;
      }
      setModel((current) => (typeof next === 'function' ? (next as (prev: CanvasModel) => CanvasModel)(current) : next));
      setIsDirty(false);
    },
    [readOnly],
  );

  const setModelDirty = useCallback(
    (next: CanvasModel | ((prev: CanvasModel) => CanvasModel)) => {
      if (readOnly) {
        warnReadOnlyBlocked('edit graph');
        return;
      }
      setModel((current) => (typeof next === 'function' ? (next as (prev: CanvasModel) => CanvasModel)(current) : next));
      setIsDirty(true);
    },
    [readOnly],
  );

  const updateNodeData = useCallback(
    (id: string, partial: Partial<NodeData>) => {
      setModelDirty((m) => ({
        ...m,
        nodes: m.nodes.map((n) => {
          if (n.id !== id) {
            return n;
          }
          return { ...n, data: { ...n.data, ...partial } } as CanvasNode;
        }),
      }));
    },
    [setModelDirty],
  );

  const appendNode = useCallback(
    (kind: NodeKind, position: { x: number; y: number }) => {
      if (readOnly) {
        warnReadOnlyBlocked('append node');
        return;
      }
      const prefix: Record<NodeKind, string> = {
        ASK_USER: 'ask-user',
        AI_STEP: 'ai-step',
        AI_DEBATE: 'ai-debate',
        DECISION: 'decision',
        REPEAT: 'repeat',
        SAVE_RESULT: 'save-result',
        REUSE_WORKFLOW: 'reuse-wf',
        LOAD_RESOURCE: 'load-res',
        STOP: 'stop',
        RETRY: 'retry',
      };
      const backendStepId = newStepId(prefix[kind]);
      const id = `c-${backendStepId}`;
      const node = {
        id,
        backendStepId,
        kind,
        position,
        data: defaultNodeData(kind),
      } as CanvasNode;
      setModelDirty((m) => ({
        ...m,
        nodes: [...m.nodes, node],
        startNodeId: m.startNodeId ?? id,
      }));
      setSelectedId(id);
    },
    [readOnly, setModelDirty],
  );

  return {
    model,
    setModel: setModelDirty,
    setModelFromLoad,
    selectedId,
    setSelectedId,
    isDirty,
    markDirty,
    markClean,
    updateNodeData,
    appendNode,
  };
}
