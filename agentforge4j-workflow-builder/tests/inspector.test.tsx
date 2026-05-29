// @vitest-environment jsdom
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

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
      <StepConfigPanel model={model} selectedId={id} mode="guided" onClose={onClose} onUpdateNodeData={() => {}} />,
    );
    fireEvent.click(screen.getAllByRole('button', { name: /Configure this step/i })[0]!);
    expect(onClose).toHaveBeenCalled();
  });
});
