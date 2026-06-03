// @vitest-environment jsdom
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities } from '../src/api/types';
import { GUIDED_STAGE_LABELS } from '../src/copy/workflow-terminology';

const ossCapabilities: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

const BUILDER_MODE_KEY = 'agentforge_builder_mode';

describe('OSS no-AI / no-auth path', () => {
  beforeEach(() => {
    window.localStorage.removeItem(BUILDER_MODE_KEY);
  });

  it('renders no AI affordance, never calls fetch, and guided stage actions are client-side', async () => {
    const user = userEvent.setup();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    const { container } = render(<WorkflowBuilder capabilities={ossCapabilities} />);

    expect(screen.queryByTestId('workflow-builder-ai')).not.toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();

    const guidedStepper = container.querySelector('.wf-guided-stepper');
    expect(guidedStepper).toBeTruthy();

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(fetchSpy).not.toHaveBeenCalled();

    const inspector = container.querySelector('.wf-inspector');
    expect(inspector).toBeTruthy();

    const nameInput = within(inspector as HTMLElement).getAllByRole('textbox')[0]!;
    await user.type(nameInput, 'User input');
    expect(fetchSpy).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.addAiStep }));
    expect(fetchSpy).not.toHaveBeenCalled();

    const canvas = screen.getByTestId('workflow-builder-canvas');
    expect(within(canvas).getAllByText(/AI Step/i).length).toBeGreaterThan(0);

    fetchSpy.mockRestore();
  });
});
