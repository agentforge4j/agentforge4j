// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import { mergeModelIntoFlowNodes, WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';

describe('mergeModelIntoFlowNodes', () => {
  // jsdom has no ResizeObserver — actual visibility:visible is verified in a real browser.
  it('preserves measured width/height when merging domain-derived nodes', () => {
    const existing: Node[] = [
      {
        id: 'n-1',
        type: 'step',
        position: { x: 0, y: 0 },
        data: {},
        measured: { width: 228, height: 96 },
        width: 228,
        height: 96,
      },
    ];
    const derived: Node[] = [
      {
        id: 'n-1',
        type: 'step',
        position: { x: 40, y: 80 },
        data: { selected: true },
      },
    ];
    const merged = mergeModelIntoFlowNodes(existing, derived);
    expect(merged).toHaveLength(1);
    expect(merged[0]?.position).toEqual({ x: 40, y: 80 });
    expect(merged[0]?.measured).toEqual({ width: 228, height: 96 });
    expect(merged[0]?.width).toBe(228);
    expect(merged[0]?.height).toBe(96);
  });

  it('adds new nodes without measured dimensions', () => {
    const merged = mergeModelIntoFlowNodes([], [{ id: 'n-new', type: 'step', position: { x: 0, y: 0 }, data: {} }]);
    expect(merged[0]?.measured).toBeUndefined();
  });
});

describe('WorkflowCanvas', () => {
  it('renders react-flow root', () => {
    const model = createInitialCanvasModel();
    const { container } = render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(container.querySelector('.react-flow')).toBeTruthy();
  });

  it('shows start-here hint on a fresh starter canvas', () => {
    const model = createInitialCanvasModel();
    render(
      <WorkflowCanvas
        model={model}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(screen.getAllByText(/Start here/i).length).toBeGreaterThan(0);
  });
});
