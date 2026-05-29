// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { GuidedStepper } from '../src/guided/GuidedStepper';

describe('GuidedStepper', () => {
  it('invokes stage action for the active incomplete stage', async () => {
    const user = userEvent.setup();
    const onStageAction = vi.fn();
    render(
      <GuidedStepper
        stages={[
          { label: 'Add input', complete: false, actionLabel: 'Configure input' },
          { label: 'Add AI step', complete: false, actionLabel: 'Add AI step' },
        ]}
        activeIndex={0}
        onStageAction={onStageAction}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'Configure input' }));
    expect(onStageAction).toHaveBeenCalledWith(0);
  });
});
