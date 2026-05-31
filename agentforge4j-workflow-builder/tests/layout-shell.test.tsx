// @vitest-environment jsdom
import { StepPalette } from '../src/palette/StepPalette';
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { ValidationPill } from '../src/validation-ui/ValidationPill';
import { ACTION_LABELS, PALETTE_GROUP_LABELS } from '../src/copy/workflow-terminology';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

describe('Phase D layout shell', () => {
  it('palette starts collapsed and expands on toggle', async () => {
    const user = userEvent.setup();
    const { container } = render(<StepPalette mode="advanced" onAddStep={() => {}} defaultCollapsed />);
    const palette = container.querySelector('.wf-palette');
    expect(palette).toHaveClass('wf-palette--collapsed');
    const expandButtons = screen.getAllByRole('button', { name: /Expand step library/i });
    await user.click(expandButtons[0]!);
    expect(palette).toHaveClass('wf-palette--expanded');
    expect(screen.getByText(PALETTE_GROUP_LABELS.flow)).toBeInTheDocument();
  });

  it('inspector opens when a node is selected', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    render(
      <StepConfigPanel model={model} selectedId={id} mode="guided" onClose={() => {}} onUpdateNodeData={() => {}} />,
    );
    expect(document.querySelector('.wf-inspector--open')).toBeTruthy();
  });

  it('inspector is absent when selection is cleared', () => {
    const model = createInitialCanvasModel();
    render(
      <StepConfigPanel model={model} selectedId={null} mode="guided" onClose={() => {}} onUpdateNodeData={() => {}} />,
    );
    expect(document.querySelector('.wf-inspector--open')).toBeNull();
  });

  it('inspector closes on dismiss', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onClose = vi.fn();
    render(
      <StepConfigPanel model={model} selectedId={id} mode="guided" onClose={onClose} onUpdateNodeData={() => {}} />,
    );
    fireEvent.click(screen.getAllByRole('button', { name: /Configure this step/i })[0]!);
    expect(onClose).toHaveBeenCalled();
  });

  it('validation pill shows issue count and opens popover', async () => {
    const user = userEvent.setup();
    const model = createInitialCanvasModel();
    render(
      <ValidationPill
        model={model}
        clientIssues={[
          { code: 'step.name', message: 'Name required', stepId: model.nodes[0]!.backendStepId },
          { code: 'step.agent', message: 'Agent required', stepId: model.nodes[0]!.backendStepId },
        ]}
        onFix={() => {}}
      />,
    );
    expect(screen.getByRole('button', { name: ACTION_LABELS.thingsToFix(2) })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: ACTION_LABELS.thingsToFix(2) }));
    expect(document.querySelector('.wf-validation-pill__popover')).toBeTruthy();
  });
});
