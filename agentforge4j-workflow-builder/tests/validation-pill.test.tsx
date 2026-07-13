// @vitest-environment jsdom
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { ACTION_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import type { BuilderCapabilities } from '../src/api/types';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

describe('ValidationPill popover occlusion fix', () => {
  // useBuilderMode persists to localStorage; avoid leaking mode state across tests in this file.
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('portals the popover to document.body, outside the toolbar/inspector stacking contexts, while the inspector is open', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    // Open the step inspector first — this is the real-world case the audit's corrected finding
    // identified: the popover only actually occluded when an inspector panel was already open.
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    const inspector = await screen.findByRole('dialog', { name: NODE_LABELS.AI_STEP });
    expect(inspector).toBeInTheDocument();

    // Open the validation popover while the inspector is still open.
    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    expect(pillButton).toBeTruthy();
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // jsdom has no real layout/paint engine, so `document.elementFromPoint` (what the live
    // Playwright grounding used to prove the original bug) is not available as a test oracle here.
    // The structural, actually-verifiable proxy for "will paint above the inspector" is: the
    // popover is a portal directly under document.body — never a descendant of the toolbar (whose
    // own non-auto z-index establishes a stacking context a mere z-index bump could never escape)
    // or of the inspector panel itself.
    expect(popover.parentElement).toBe(document.body);
    expect(inspector.contains(popover)).toBe(false);
    expect(container.contains(popover)).toBe(false);
    expect(popover).toHaveClass('wf-validation-pill__popover--portal');

    // Positioned via viewport-relative fixed coordinates computed from the pill's own screen
    // position, replacing the `position: absolute; right: 0` that only worked while the popover
    // was still a DOM descendant of the anchor.
    expect(popover.style.position).toBe('fixed');
  });

  it('still closes on an outside click even though the popover now lives outside the anchor subtree', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    await user.click(document.body);

    expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
  });

  it('does not close when clicking inside the portaled popover itself', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // A click on the popover's own header (not a specific "Fix" button) must not be treated as an
    // outside click just because the popover is no longer a DOM descendant of the anchor. Scoped
    // via `within(popover)` since the same "Workflow Problems" text also appears as a section
    // heading inside the issue list when there are issues to show.
    await user.click(within(popover).getByRole('heading', { name: ACTION_LABELS.clientValidation, level: 2 }));

    expect(popover).toBeInTheDocument();
  });
});
