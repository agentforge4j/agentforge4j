import { describe, expect, it } from 'vitest';
import { isInsertableEdge } from '../src/model/graphOps';
import { defaultNodeData } from '../src/model/mapper';
import type { CanvasEdge, CanvasModel, CanvasNode } from '../src/model/canvasModel';

/**
 * Stale insert-edge fallback (WorkflowBuilder.onAddStepFromLibrary).
 *
 * When the user clicks the edge-insert "+" (setting `insertOnEdgeId`) and then
 * deletes that edge, `insertOnEdgeId` points at an edge that no longer exists.
 * The splice guard is `insertOnEdgeId && isInsertableEdge(model, insertOnEdgeId)`:
 * a stale id makes `isInsertableEdge` return false, so the callback skips the
 * splice, clears the stale insert state, and falls back to a normal append
 * instead of splicing onto a missing edge.
 *
 * The full behavioural path (click "+" -> delete edge -> add step -> node
 * appended, insert banner gone) is exercised by the Playwright C10 spec
 * (`agentforge4j-ui-e2e/specs/builder/c10-edge-insert.spec.ts`); React Flow's
 * edge affordance does not render with measurable geometry under jsdom, so the
 * splice-vs-append decision is pinned here at the graph-operation boundary that
 * gates it.
 */
function node(id: string, kind: CanvasNode['kind'] = 'AI_STEP'): CanvasNode {
  return { id, backendStepId: id, kind, position: { x: 0, y: 0 }, data: defaultNodeData(kind) } as CanvasNode;
}

function linEdge(source: string, target: string): CanvasEdge {
  return { id: `e-${source}-${target}`, source, target, sourceHandle: null, label: null };
}

function model(nodes: CanvasNode[], edges: CanvasEdge[]): CanvasModel {
  return {
    workflowId: 'wf',
    workflowName: 'Test',
    description: '',
    startNodeId: nodes[0]?.id ?? null,
    nodes,
    edges,
    artifacts: {},
    blueprints: {},
  };
}

describe('stale insert-edge fallback', () => {
  it('treats a no-longer-present edge id as non-insertable, so the add falls back to append', () => {
    const m = model([node('A'), node('B')], [linEdge('A', 'B')]);
    // A live linear edge is insertable...
    expect(isInsertableEdge(m, 'e-A-B')).toBe(true);
    // ...but a stale id (the clicked edge was since deleted) is not, so
    // `activeInsertEdge` resolves to null and onAddStepFromLibrary appends instead.
    expect(isInsertableEdge(m, 'e-A-B-deleted')).toBe(false);
    expect(isInsertableEdge(m, 'nonexistent')).toBe(false);
  });
});
