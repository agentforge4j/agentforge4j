// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { START_SENTINEL } from '../src/model/graphOps';
import { defaultNodeData } from '../src/model/mapper';
import type { CanvasEdge, CanvasModel, CanvasNode } from '../src/model/canvasModel';

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

describe('Runs after selector', () => {
  it('renders the selector and repositions on change', () => {
    const onReposition = vi.fn();
    render(
      <StepConfigPanel
        model={chainModel()}
        selectedId="B"
        mode="advanced"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        onReposition={onReposition}
      />,
    );
    const select = screen.getByTestId('runs-after-select') as HTMLSelectElement;
    expect(select.value).toBe('A');
    fireEvent.change(select, { target: { value: START_SENTINEL } });
    expect(onReposition).toHaveBeenCalledWith('B', START_SENTINEL);
  });

  it('does not render the selector for a DECISION node', () => {
    const model = chainModel();
    model.nodes[1] = node('B', 'DECISION');
    render(
      <StepConfigPanel
        model={model}
        selectedId="B"
        mode="advanced"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        onReposition={() => {}}
      />,
    );
    expect(screen.queryByTestId('runs-after-select')).not.toBeInTheDocument();
  });

  it('hides the selector and delete control in read-only mode', () => {
    render(
      <StepConfigPanel
        model={chainModel()}
        selectedId="B"
        mode="advanced"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        onReposition={() => {}}
        readOnly
      />,
    );
    expect(screen.queryByTestId('runs-after-select')).not.toBeInTheDocument();
    expect(screen.getByTestId('inspector-readonly-banner')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete step/i })).not.toBeInTheDocument();
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-readonly', 'true');
  });
});
