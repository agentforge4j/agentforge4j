// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel, CanvasNode, NodeData } from '../model/canvasModel';
import { defaultNodeData, newStepId } from '../model/mapper';
import type { NodeKind } from '../model/nodeKinds';
import type { HistorySetOptions } from '../state/useHistoryState';
import { useHistoryState } from '../state/useHistoryState';
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
  const history = useHistoryState<CanvasModel>(initialModel);
  const model = history.present;
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
  // via the mount-time initial model, not through here. A load replaces the working
  // document wholesale, so it resets undo/redo history rather than becoming an
  // undoable step back into a different workflow's shape.
  const setModelFromLoad = useCallback(
    (next: CanvasModel | ((prev: CanvasModel) => CanvasModel)) => {
      if (readOnly) {
        warnReadOnlyBlocked('load model');
        return;
      }
      history.reset(typeof next === 'function' ? (next as (prev: CanvasModel) => CanvasModel)(history.present) : next);
      setIsDirty(false);
    },
    [history, readOnly],
  );

  const setModelDirty = useCallback(
    (next: CanvasModel | ((prev: CanvasModel) => CanvasModel), options?: HistorySetOptions) => {
      if (readOnly) {
        warnReadOnlyBlocked('edit graph');
        return;
      }
      history.set(next, options);
      setIsDirty(true);
    },
    [history, readOnly],
  );

  const updateNodeData = useCallback(
    (id: string, partial: Partial<NodeData>) => {
      setModelDirty(
        (m) => ({
          ...m,
          nodes: m.nodes.map((n) => {
            if (n.id !== id) {
              return n;
            }
            return { ...n, data: { ...n.data, ...partial } } as CanvasNode;
          }),
        }),
        // Coalesce a node's rapid-fire field edits (typing, selects made in quick
        // succession) into one undo step per "editing session" on that node;
        // switching to a different node — or any structural action, which always
        // omits a coalesceKey — starts a new one.
        { coalesceKey: `node:${id}` },
      );
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

  const undo = useCallback(() => {
    if (readOnly) {
      warnReadOnlyBlocked('undo');
      return;
    }
    history.undo();
  }, [history, readOnly]);

  const redo = useCallback(() => {
    if (readOnly) {
      warnReadOnlyBlocked('redo');
      return;
    }
    history.redo();
  }, [history, readOnly]);

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
    undo,
    redo,
    canUndo: history.canUndo,
    canRedo: history.canRedo,
    commitHistory: history.commit,
  };
}
