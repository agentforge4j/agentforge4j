// @vitest-environment jsdom
import { StepPalette } from '../src/palette/StepPalette';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

describe('Phase E responsive shell', () => {
  it('palette renders mobile sheet trigger when its container is narrow', () => {
    // Narrowness is a container-derived prop (same measurement as the builder's
    // narrow-container gate), not a viewport media query — the two axes can disagree.
    render(<StepPalette mode="guided" onAddStep={() => {}} containerNarrow />);
    expect(document.querySelector('.wf-palette--mobile')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Add step/i })).toBeInTheDocument();
  });

  it('palette renders the desktop rail when its container is wide, regardless of the viewport', () => {
    render(<StepPalette mode="guided" onAddStep={() => {}} />);
    expect(document.querySelector('.wf-palette--mobile')).toBeNull();
    expect(document.querySelector('.wf-palette')).toBeTruthy();
  });

  it('inspector panel is present for selection (full-width styling is CSS at max-width 47.9375rem)', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    render(
      <StepConfigPanel model={model} selectedId={id} mode="guided" onClose={() => {}} onDelete={() => {}} onUpdateNodeData={() => {}} />,
    );
    const inspector = document.querySelector('.wf-inspector');
    expect(inspector).toBeTruthy();
    expect(inspector).toHaveClass('wf-inspector--open');
  });
});
