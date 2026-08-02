// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createInitialCanvasModel, useCanvasState } from '../src/hooks/useCanvasState';
import { repositionAfter, START_SENTINEL } from '../src/model/graphOps';
import { defaultNodeData } from '../src/model/mapper';
import type { AskUserNodeData, CanvasEdge, CanvasModel, CanvasNode } from '../src/model/canvasModel';

function node(id: string, kind: CanvasNode['kind'] = 'AI_STEP'): CanvasNode {
  return { id, backendStepId: id, kind, position: { x: 0, y: 0 }, data: defaultNodeData(kind) } as CanvasNode;
}

function linEdge(source: string, target: string): CanvasEdge {
  return { id: `e-${source}-${target}`, source, target, sourceHandle: null, label: null };
}

function chainModel(): CanvasModel {
  return {
    workflowId: 'wf',
    workflowName: 'Test',
    description: '',
    startNodeId: 'A',
    nodes: [node('A'), node('B'), node('C')],
    edges: [linEdge('A', 'B'), linEdge('B', 'C')],
    artifacts: {},
    blueprints: {},
  };
}

describe('useCanvasState undo/redo', () => {
  it('undoes and redoes adding a step', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel()));
    const before = result.current.model.nodes.length;

    act(() => result.current.appendNode('AI_STEP', { x: 200, y: 200 }));
    expect(result.current.model.nodes).toHaveLength(before + 1);
    expect(result.current.canUndo).toBe(true);
    expect(result.current.canRedo).toBe(false);

    act(() => result.current.undo());
    expect(result.current.model.nodes).toHaveLength(before);
    expect(result.current.canRedo).toBe(true);

    act(() => result.current.redo());
    expect(result.current.model.nodes).toHaveLength(before + 1);
  });

  it('undoes deleting a step, restoring the exact node', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel()));
    const id = result.current.model.nodes[0]!.id;

    act(() =>
      result.current.setModel((m) => ({
        ...m,
        nodes: m.nodes.filter((n) => n.id !== id),
        edges: m.edges.filter((e) => e.source !== id && e.target !== id),
      })),
    );
    expect(result.current.model.nodes).toHaveLength(0);

    act(() => result.current.undo());
    expect(result.current.model.nodes).toHaveLength(1);
    expect(result.current.model.nodes[0]!.id).toBe(id);
  });

  it('coalesces a config-field editing session into one undo step', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel()));
    const id = result.current.model.nodes[0]!.id;

    act(() => result.current.updateNodeData(id, { name: 'A' }));
    act(() => result.current.updateNodeData(id, { name: 'Ab' }));
    act(() => result.current.updateNodeData(id, { name: 'Abc' }));

    expect((result.current.model.nodes[0]!.data as AskUserNodeData).name).toBe('Abc');
    expect(result.current.canUndo).toBe(true);

    act(() => result.current.undo());
    expect((result.current.model.nodes[0]!.data as AskUserNodeData).name).toBe('');
    expect(result.current.canUndo).toBe(false);
  });

  it('editing a different node afterward starts a new, separately undoable entry', () => {
    const model = chainModel();
    const { result } = renderHook(() => useCanvasState(model));

    act(() => result.current.updateNodeData('A', { name: 'first' }));
    act(() => result.current.updateNodeData('B', { name: 'second' }));

    act(() => result.current.undo());
    expect((result.current.model.nodes.find((n) => n.id === 'B')!.data as { name: string }).name).toBe('');
    expect((result.current.model.nodes.find((n) => n.id === 'A')!.data as { name: string }).name).toBe('first');

    act(() => result.current.undo());
    expect((result.current.model.nodes.find((n) => n.id === 'A')!.data as { name: string }).name).toBe('');
  });

  it('undoes a connection change (create then delete an edge)', () => {
    const model = chainModel();
    const { result } = renderHook(() => useCanvasState(model));

    act(() =>
      result.current.setModel((m) => ({
        ...m,
        edges: [...m.edges, { id: 'e-A-C', source: 'A', target: 'C', sourceHandle: 'branch', label: null }],
      })),
    );
    expect(result.current.model.edges).toHaveLength(3);

    act(() =>
      result.current.setModel((m) => ({
        ...m,
        edges: m.edges.filter((e) => e.id !== 'e-A-C'),
      })),
    );
    expect(result.current.model.edges).toHaveLength(2);

    act(() => result.current.undo());
    expect(result.current.model.edges).toHaveLength(3);
    act(() => result.current.undo());
    expect(result.current.model.edges).toHaveLength(2);
  });

  it('undoes a reposition (ordering change) in the chain', () => {
    const model = chainModel();
    const { result } = renderHook(() => useCanvasState(model));

    act(() => result.current.setModel((m) => repositionAfter(m, 'C', 'A')));
    // C now runs right after A: A -> C -> B
    expect(result.current.model.edges.find((e) => e.source === 'A')?.target).toBe('C');

    act(() => result.current.undo());
    expect(result.current.model.edges.find((e) => e.source === 'A')?.target).toBe('B');
  });

  it('undoes a workflow metadata edit, coalescing a typing session', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel()));

    act(() => result.current.setModel((m) => ({ ...m, workflowName: 'M' }), { coalesceKey: 'meta:workflowName' }));
    act(() => result.current.setModel((m) => ({ ...m, workflowName: 'My' }), { coalesceKey: 'meta:workflowName' }));
    act(() => result.current.setModel((m) => ({ ...m, workflowName: 'My Flow' }), { coalesceKey: 'meta:workflowName' }));

    expect(result.current.model.workflowName).toBe('My Flow');
    act(() => result.current.undo());
    expect(result.current.model.workflowName).toBe('');
  });

  it('undoes a start-step change (repositioning a node to the Start sentinel)', () => {
    const model = chainModel();
    const { result } = renderHook(() => useCanvasState(model));
    expect(result.current.model.startNodeId).toBe('A');

    act(() => result.current.setModel((m) => repositionAfter(m, 'C', START_SENTINEL)));
    expect(result.current.model.startNodeId).toBe('C');

    act(() => result.current.undo());
    expect(result.current.model.startNodeId).toBe('A');
  });

  it('loading a new model resets history instead of becoming an undoable step', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel()));
    act(() => result.current.appendNode('AI_STEP', { x: 0, y: 0 }));
    expect(result.current.canUndo).toBe(true);

    act(() => result.current.setModelFromLoad(chainModel()));
    expect(result.current.model.nodes.map((n) => n.id)).toEqual(['A', 'B', 'C']);
    expect(result.current.canUndo).toBe(false);
    expect(result.current.canRedo).toBe(false);
  });

  it('read-only mode blocks undo/redo as defense-in-depth', () => {
    const { result } = renderHook(() => useCanvasState(createInitialCanvasModel(), true));
    act(() => result.current.undo());
    act(() => result.current.redo());
    expect(result.current.canUndo).toBe(false);
    expect(result.current.canRedo).toBe(false);
  });
});
