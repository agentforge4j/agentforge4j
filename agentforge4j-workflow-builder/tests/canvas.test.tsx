// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';

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
