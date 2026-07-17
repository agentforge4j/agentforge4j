// @vitest-environment jsdom
import { StepConfigPanel } from '../src/inspector/StepConfigPanel';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { ACTION_LABELS, GUIDED_STAGE_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import { createInitialCanvasModel } from '../src/hooks/useCanvasState';
import { defaultNodeData } from '../src/model/mapper';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import type { BuilderCapabilities } from '../src/api/types';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

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
        onDelete={() => {}}
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
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={onClose}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getAllByRole('button', { name: /Configure this step/i })[0]!);
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onDelete when Delete step is clicked', () => {
    const model = createInitialCanvasModel();
    const id = model.nodes[0]!.id;
    const onDelete = vi.fn();
    render(
      <StepConfigPanel
        model={model}
        selectedId={id}
        mode="guided"
        onClose={() => {}}
        onDelete={onDelete}
        onUpdateNodeData={() => {}}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    expect(onDelete).toHaveBeenCalledWith(id);
  });
});

describe('WorkflowBuilder inspector delete', () => {
  it('removes the selected step and clears selection', async () => {
    const user = userEvent.setup();
    const { container } = render(<WorkflowBuilder capabilities={allDisabled} />);
    const canvas = screen.getByTestId('workflow-builder-canvas');

    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });
    await user.click(screen.getAllByRole('button', { name: ACTION_LABELS.configureStepClose })[0]!);

    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    expect(screen.getByRole('dialog', { name: NODE_LABELS.ASK_USER })).toBeInTheDocument();
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(canvas.querySelectorAll('.react-flow__node')).toHaveLength(1);
    expect(container.querySelector('.wf-inspector--open')).toBeNull();
  });
});

describe('StepConfigPanel focusField ("Add approval" guided stage discoverability)', () => {
  function modelWithAiStep(): { model: CanvasModel; aiNode: CanvasNode } {
    const model = createInitialCanvasModel();
    const aiNode = {
      id: 'c-ai-1',
      backendStepId: 'ai-1',
      kind: 'AI_STEP',
      position: { x: 0, y: 0 },
      data: defaultNodeData('AI_STEP'),
    } as CanvasNode;
    return { model: { ...model, nodes: [...model.nodes, aiNode] }, aiNode };
  }

  it('forces the collapsed Behavior section open and focuses the Approval field when focusField is "transition"', () => {
    const { model, aiNode } = modelWithAiStep();
    const onFocusFieldHandled = vi.fn();

    render(
      <StepConfigPanel
        model={model}
        selectedId={aiNode.id}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        focusField="transition"
        onFocusFieldHandled={onFocusFieldHandled}
      />,
    );

    expect(screen.getByTestId('workflow-builder-inspector-behaviour-section')).toHaveAttribute('open');
    const approvalSelect = screen.getByRole('combobox', { name: ACTION_LABELS.approvalField });
    expect(approvalSelect).toHaveFocus();
    // Revealed for the user to choose — not silently set on their behalf.
    expect(approvalSelect).toHaveValue('AUTO');
    expect(onFocusFieldHandled).toHaveBeenCalledTimes(1);
  });

  it('focuses the panel container itself when focusField is "panel" (no specific field target)', () => {
    const { model, aiNode } = modelWithAiStep();
    const onFocusFieldHandled = vi.fn();

    render(
      <StepConfigPanel
        model={model}
        selectedId={aiNode.id}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        focusField="panel"
        onFocusFieldHandled={onFocusFieldHandled}
      />,
    );

    expect(screen.getByTestId('workflow-builder-inspector-panel')).toHaveFocus();
    // No specific field is revealed for a plain panel-focus request — Behavior stays collapsed.
    expect(screen.getByTestId('workflow-builder-inspector-behaviour-section')).not.toHaveAttribute('open');
    expect(onFocusFieldHandled).toHaveBeenCalledTimes(1);
  });

  it('clears a pending focusField request without acting when no node is selected', () => {
    const onFocusFieldHandled = vi.fn();

    render(
      <StepConfigPanel
        model={createInitialCanvasModel()}
        selectedId={null}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
        focusField="transition"
        onFocusFieldHandled={onFocusFieldHandled}
      />,
    );

    expect(screen.queryByTestId('workflow-builder-inspector-panel')).not.toBeInTheDocument();
    expect(onFocusFieldHandled).toHaveBeenCalledTimes(1);
  });

  it('leaves the Behavior section collapsed in guided mode when no field focus is requested (baseline)', () => {
    const { model, aiNode } = modelWithAiStep();

    render(
      <StepConfigPanel
        model={model}
        selectedId={aiNode.id}
        mode="guided"
        onClose={() => {}}
        onDelete={() => {}}
        onUpdateNodeData={() => {}}
      />,
    );

    expect(screen.getByTestId('workflow-builder-inspector-behaviour-section')).not.toHaveAttribute('open');
  });

  it('reopens and refocuses on a repeat focusField request after the user has natively collapsed the section', () => {
    const { model, aiNode } = modelWithAiStep();

    function Harness(): ReactNode {
      const [focusField, setFocusField] = useState<'transition' | null>(null);
      return (
        <>
          <button type="button" onClick={() => setFocusField('transition')}>
            trigger
          </button>
          <StepConfigPanel
            model={model}
            selectedId={aiNode.id}
            mode="guided"
            onClose={() => {}}
            onDelete={() => {}}
            onUpdateNodeData={() => {}}
            focusField={focusField}
            onFocusFieldHandled={() => setFocusField(null)}
          />
        </>
      );
    }

    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'trigger' }));
    const section = screen.getByTestId('workflow-builder-inspector-behaviour-section');
    expect(section).toHaveAttribute('open');

    // Simulate the user natively collapsing the section via the <summary> — this updates the DOM
    // `open` attribute directly, without React ever seeing the change (mirrors what a real
    // <summary> click does; jsdom's own toggle behavior is not what's under test here, only the
    // resulting DOM/React divergence).
    (section as HTMLDetailsElement).open = false;
    expect(section).not.toHaveAttribute('open');

    // A second "Require approval" request must still force the section open and refocus the
    // field — not silently no-op because React's last-rendered `open` value was already `true`.
    fireEvent.click(screen.getByRole('button', { name: 'trigger' }));
    expect(section).toHaveAttribute('open');
    expect(screen.getByRole('combobox', { name: ACTION_LABELS.approvalField })).toHaveFocus();
  });
});

// The "Add approval" guided action's editable-candidate filter (skip nodes inside a REPEAT loop
// body, since StepConfigPanel renders their whole fieldset disabled there) is unit-tested
// directly against the shared `isInsideLoopBody` predicate in `graphOps.test.ts` — a loop-body
// node can only be produced via a live canvas drag-reparent (React Flow's own drag/intersection
// handling), which has no real layout engine to drive in jsdom, and importing a workflow with a
// loop body is a separate, pre-existing "unsupported" path that never reconstructs it as a live,
// editable node at all.

describe('Guided "Add approval" stage discoverability, end to end', () => {
  // useBuilderMode persists to localStorage as the initial mode; avoid leaking state across tests.
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('reveals and focuses the transition field via the guided action, without silently choosing a value', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);

    // Stage 0: configure the starter Ask User step so hasConfiguredInput is satisfied. Scoped to
    // the open dialog — the toolbar's own workflow-name input is also a `textbox` earlier in the
    // full-tree DOM order, so an unscoped index [0] would grab the wrong field here.
    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.configureInput }));
    const askUserDialog = await screen.findByRole('dialog', { name: NODE_LABELS.ASK_USER });
    fireEvent.change(within(askUserDialog).getAllByRole('textbox')[0]!, { target: { value: 'Collect topic' } });
    await user.click(within(askUserDialog).getByRole('button', { name: ACTION_LABELS.configureStepClose }));

    // Stage 1: add an AI step.
    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.addAiStep }));
    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });
    await user.click(screen.getAllByRole('button', { name: ACTION_LABELS.configureStepClose })[0]!);

    // The "Add approval" stage is now the active stage; trigger its guided action.
    await user.click(screen.getByRole('button', { name: GUIDED_STAGE_LABELS.requireApproval }));

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: NODE_LABELS.AI_STEP })).toBeInTheDocument();
    });
    expect(screen.getByTestId('workflow-builder-inspector-behaviour-section')).toHaveAttribute('open');

    const approvalSelect = screen.getByRole('combobox', { name: ACTION_LABELS.approvalField });
    expect(approvalSelect).toHaveFocus();
    // Genuinely revealed for the user to choose themselves — not auto-completed for them: the
    // field and check were always real, only discoverability was broken.
    expect(approvalSelect).toHaveValue('AUTO');
  });
});
