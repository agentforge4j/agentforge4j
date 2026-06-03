// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
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

describe('WorkflowBuilder capability contract', () => {
  it('calls save action with current draft when Save is clicked', async () => {
    const user = userEvent.setup();
    const save = vi.fn().mockResolvedValue(undefined);
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, save: true }}
        actions={{ save }}
      />,
    );
    await user.click(screen.getByTestId('workflow-builder-save'));
    expect(save).toHaveBeenCalledTimes(1);
    expect(save.mock.calls[0]?.[0]).toMatchObject({ steps: expect.any(Array) });
  });

  it('does not render Save when capabilities.save is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} actions={{ save: vi.fn() }} />);
    expect(screen.queryByTestId('workflow-builder-save')).not.toBeInTheDocument();
  });

  it('does not render Run when capabilities.run is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} actions={{ run: vi.fn() }} />);
    expect(screen.queryByTestId('workflow-builder-run')).not.toBeInTheDocument();
  });

  it('does not fetch when aiAssist is false', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('workflow-builder-ai')).not.toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('renders AI affordance when aiAssist is true', () => {
    render(<WorkflowBuilder capabilities={{ ...allDisabled, aiAssist: true }} />);
    expect(screen.getByTestId('workflow-builder-ai')).toBeInTheDocument();
  });
});
