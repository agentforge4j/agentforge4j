// @vitest-environment jsdom
import { StepPalette } from '../src/palette/StepPalette';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

function mockMobileViewport() {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('max-width: 767px'),
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe('Phase E responsive shell', () => {
  beforeEach(() => {
    mockMobileViewport();
  });

  it('palette renders mobile sheet trigger below md breakpoint', () => {
    render(<StepPalette mode="guided" onAddStep={() => {}} />);
    expect(document.querySelector('.wf-palette--mobile')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Add step/i })).toBeInTheDocument();
  });

  it('inspector panel is present for selection (full-width styling is CSS at max-width 47.9375rem)', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    render(
      <StepConfigPanel model={model} selectedId={id} mode="guided" onClose={() => {}} onUpdateNodeData={() => {}} />,
    );
    const inspector = document.querySelector('.wf-inspector');
    expect(inspector).toBeTruthy();
    expect(inspector).toHaveClass('wf-inspector--open');
  });
});
