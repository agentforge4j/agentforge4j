// @vitest-environment jsdom
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { ACTION_LABELS, GUIDED_STAGE_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import type { BuilderCapabilities } from '../src/api/types';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('StepConfigPanel', () => {
  it('updates Ask user name in the model', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onUpdate = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={onUpdate}
      />,
    );
    const input = screen.getAllByRole('textbox')[0]!;
    fireEvent.change(input, { target: { value: 'Welcome step' } });
    expect(onUpdate).toHaveBeenCalledWith(id, expect.objectContaining({ name: 'Welcome step' }));
  });

  it('closes inspector on dismiss', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onClose = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={onClose}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getAllByRole('button', { name: /Configure this step/i })[0]!);
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onDelete when Delete step is clicked', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onDelete = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={() => {}}
        onDelete={onDelete}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    expect(onDelete).toHaveBeenCalledWith(id);
  });
});

describe('WorkflowBuilder inspector delete', () => {
  it('removes the selected step and clears selection', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });
    await user.click(screen.getAllByRole('button', { name: ACTION_LABELS.configureStepClose })[0]!);

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(screen.getByRole('dialog', { name: NODE_LABELS.ASK_USER })).toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    expect(container.querySelector('.wf-inspector--open')).toBeNull();
  });
});
