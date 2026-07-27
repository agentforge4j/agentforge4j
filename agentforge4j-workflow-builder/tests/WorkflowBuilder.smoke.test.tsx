// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities } from '../src/api/types';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('WorkflowBuilder smoke', () => {
  // useBuilderMode persists to localStorage and reads it back as the initial mode, so a stale
  // value from an earlier test in this file would otherwise leak into the next one.
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('renders canvas instead of Phase 1 placeholder', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');
    expect(canvas.querySelector('.react-flow')).toBeTruthy();
    expect(canvas).not.toHaveTextContent('Phase 3');
  });

  it('does not render Import when capabilities.import is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('workflow-builder-import')).not.toBeInTheDocument();
  });

  it('does not render AI affordance when capabilities.aiAssist is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('workflow-builder-ai')).not.toBeInTheDocument();
  });

  it('renders Import and AI when capabilities allow', () => {
    render(
      <WorkflowBuilder capabilities={{ ...allDisabled, import: true, aiAssist: true }} />,
    );
    expect(screen.getByTestId('workflow-builder-import')).toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-ai')).toBeInTheDocument();
  });

  it('suppresses the canvas start-here hint in guided mode (default, fresh starter canvas)', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.getByRole('button', { name: 'Guided' })).toHaveClass('wf-button--primary');
    expect(screen.queryByText(/Start here/i)).not.toBeInTheDocument();
  });

  it('shows the canvas start-here hint again after switching to advanced mode', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);
    await user.click(screen.getByTestId('workflow-builder-mode-advanced'));
    expect(screen.getAllByText(/Start here/i).length).toBeGreaterThan(0);
  });
});
