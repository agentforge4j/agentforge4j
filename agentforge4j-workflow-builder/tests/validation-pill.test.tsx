// @vitest-environment jsdom
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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

    // Open the step inspector first — this is the real-world case this fix addresses: the
    // popover only actually occluded when an inspector panel was already open.
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    const inspector = await screen.findByRole('dialog', { name: NODE_LABELS.AI_STEP });
    expect(inspector).toBeInTheDocument();

    // Open the validation popover while the inspector is still open.
    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    expect(pillButton).toBeTruthy();
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // jsdom has no real layout/paint engine, so `document.elementFromPoint` (what an in-browser
    // check used to prove the original bug) is not available as a test oracle here.
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

  it('re-applies the host theme (variables + className) on the portaled popover, which lives outside the themed root', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <WorkflowBuilder
        capabilities={allDisabled}
        theme={{ className: 'host-dark', variables: { '--afb-chrome-bg': 'rgb(1, 2, 3)' } }}
      />,
    );

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // The popover is a direct child of document.body — outside the builder root where the
    // theme's inline variables and class are applied — so both must be re-applied here or a
    // themed host gets a default-palette popover floating over its UI.
    expect(popover.parentElement).toBe(document.body);
    expect(popover).toHaveClass('host-dark');
    expect(popover.style.getPropertyValue('--afb-chrome-bg')).toBe('rgb(1, 2, 3)');
  });

  it('moves focus into the portaled dialog on open and restores it to the toggle button on close', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    // Portaling out of the anchor's DOM subtree drops the tab-order adjacency the popover had
    // before this fix (it was the button's own next sibling) — focus must be moved explicitly.
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });
    expect(popover).toHaveFocus();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
    expect(pillButton).toHaveFocus();
  });

  it('does not steal focus from the page on initial mount, before the popover has ever been opened', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);

    expect(document.body).toHaveFocus();
  });

  it('does not steal focus back to the toggle button when an outside click already moved it elsewhere', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // Establish the user's own focus target deterministically first, then fire the exact
    // "outside click" mousedown the dismiss listener reacts to — this isolates the specific
    // defect (a later focus-restore effect overriding an already-established focus target) from
    // the browser's own click-focuses-input default action, whose timing relative to React's
    // effect flush is not something a test should assert through indirectly.
    const nameInput = screen.getByRole('textbox', { name: ACTION_LABELS.workflowNameLabel });
    nameInput.focus();
    expect(nameInput).toHaveFocus();
    fireEvent.mouseDown(nameInput);

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
    });
    expect(nameInput).toHaveFocus();
    expect(pillButton).not.toHaveFocus();
  });

  it('closes only the popover on Escape when a step inspector is open underneath it, leaving the inspector open', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    const inspector = await screen.findByRole('dialog', { name: NODE_LABELS.AI_STEP });

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
    expect(inspector).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
  });

  it('moves focus into the step inspector when "Fix" is activated, instead of leaving it on document.body', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    // Add an AI step (left with a blank agentRef) so validation carries a step-level issue tagged
    // with that step's id — the workflow-level id/name issues alone only ever render disabled Fix
    // buttons (no stepId to jump to).
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    const aiInspector = await screen.findByRole('dialog', { name: NODE_LABELS.AI_STEP });
    await user.click(within(aiInspector).getByRole('button', { name: ACTION_LABELS.configureStepClose }));
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: NODE_LABELS.AI_STEP })).not.toBeInTheDocument();
    });

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    const popover = await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });
    const fixButton = within(popover)
      .getAllByRole('button', { name: ACTION_LABELS.fixIssue })
      .find((button) => !button.hasAttribute('disabled'));
    expect(fixButton).toBeTruthy();

    await user.click(fixButton!);

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
    });
    const inspector = await screen.findByRole('dialog', { name: NODE_LABELS.AI_STEP });
    // The popover's close effect must not steal focus back to the pill toggle: it follows the
    // step Fix just opened instead.
    expect(inspector).toHaveFocus();
    expect(pillButton).not.toHaveFocus();
  });

  it('does not steal focus back to the toggle button on Escape when focus already moved elsewhere while the popover stayed open', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);

    const pillButton = container.querySelector('.wf-validation-pill') as HTMLButtonElement;
    await user.click(pillButton);
    await screen.findByRole('dialog', { name: ACTION_LABELS.clientValidation });

    // Simulate a keyboard user Tab-ing out of the still-open popover to another control (only a
    // pointer/outside click closes it early — Tab alone does not), then pressing Escape out of
    // habit while focus sits elsewhere.
    const nameInput = screen.getByRole('textbox', { name: ACTION_LABELS.workflowNameLabel });
    nameInput.focus();
    expect(nameInput).toHaveFocus();

    await user.keyboard('{Escape}');

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: ACTION_LABELS.clientValidation })).not.toBeInTheDocument();
    });
    expect(nameInput).toHaveFocus();
    expect(pillButton).not.toHaveFocus();
  });
});
