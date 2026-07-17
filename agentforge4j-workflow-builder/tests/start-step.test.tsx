// @vitest-environment jsdom
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { StartStepChooser } from '../src/guided/StartStepChooser';
import type { BuilderCapabilities, WorkflowDefinition } from '../src/api/types';
import { ACTION_LABELS, NODE_LABELS } from '../src/copy/workflow-terminology';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

function twoNodeModel(startNodeId: string): CanvasModel {
  const a = {
    id: 'c-a',
    backendStepId: 'a',
    kind: 'ASK_USER',
    position: { x: 0, y: 0 },
    data: { ...defaultNodeData('ASK_USER'), name: 'First' },
  } as CanvasNode;
  const b = {
    id: 'c-b',
    backendStepId: 'b',
    kind: 'AI_STEP',
    position: { x: 260, y: 0 },
    data: { ...defaultNodeData('AI_STEP'), name: 'Second' },
  } as CanvasNode;
  return {
    workflowId: 'wf',
    workflowName: 'Test',
    description: '',
    startNodeId,
    nodes: [a, b],
    edges: [{ id: 'e-a-b', source: 'c-a', target: 'c-b', sourceHandle: null, label: null }],
    artifacts: {},
    blueprints: {},
  };
}

describe('Start-step marker (canvas)', () => {
  it('renders exactly one Start badge, on the node matching startNodeId', () => {
    render(
      <WorkflowCanvas
        model={twoNodeModel('c-b')}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    const badge = screen.getByTestId('node-start-badge');
    expect(badge.closest('.wf-node')).toHaveTextContent('Second');
  });

  it('moves the badge when startNodeId changes', () => {
    const { rerender } = render(
      <WorkflowCanvas
        model={twoNodeModel('c-a')}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(screen.getByTestId('node-start-badge').closest('.wf-node')).toHaveTextContent('First');

    rerender(
      <WorkflowCanvas
        model={twoNodeModel('c-b')}
        onModelChange={() => {}}
        onSelectNode={() => {}}
        selectedId={null}
        onAppend={() => {}}
      />,
    );
    expect(screen.getByTestId('node-start-badge').closest('.wf-node')).toHaveTextContent('Second');
  });
});

describe('StartStepChooser with a null startNodeId', () => {
  it('renders a disabled placeholder option so the controlled select is never in an unmatched state', () => {
    const model = { ...twoNodeModel('c-a'), startNodeId: null };
    render(<StartStepChooser model={model} onSelectStart={() => {}} />);

    const select = screen.getByTestId('guided-start-step-select') as HTMLSelectElement;
    expect(select.value).toBe('');
    expect(select.options[0]?.disabled).toBe(true);
    expect(select.options[0]?.text).toBe(ACTION_LABELS.startStepPlaceholder);
  });
});

describe('Start-step marker and chooser in Guided mode (default)', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('shows the Start marker on the sole seeded step in Guided mode', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.getByRole('button', { name: 'Guided' })).toHaveClass('wf-button--primary');
    expect(screen.getByTestId('node-start-badge')).toBeInTheDocument();
  });

  it('does not show a start-step chooser with only one node', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('guided-start-step-select')).not.toBeInTheDocument();
  });

  it('shows a real chooser once a second node exists, and reassigns start on selection', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);

    // Add a second step from the palette (the same interaction add-step.test.tsx exercises).
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);

    const select = (await screen.findByTestId('guided-start-step-select')) as HTMLSelectElement;
    expect(screen.getByTestId('node-start-badge').closest('.wf-node')).toHaveTextContent(NODE_LABELS.ASK_USER);

    const aiStepOption = Array.from(select.options).find((o) => o.text === NODE_LABELS.AI_STEP)!;
    await user.selectOptions(select, aiStepOption.value);

    expect(screen.getByTestId('node-start-badge').closest('.wf-node')).toHaveTextContent(NODE_LABELS.AI_STEP);
  });

  it('hides the chooser again after deleting back down to one node', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} />);

    // Adding a step from the palette also selects it, opening the inspector.
    await user.click(screen.getAllByRole('button', { name: NODE_LABELS.AI_STEP })[0]!);
    expect(await screen.findByTestId('guided-start-step-select')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: ACTION_LABELS.deleteStep }));
    expect(screen.queryByTestId('guided-start-step-select')).not.toBeInTheDocument();
  });
});

/** Two plain linear steps: enough nodes for the chooser to render in Guided edit mode. */
function twoStepWorkflow(): WorkflowDefinition {
  return {
    id: 'wf-two',
    name: 'Two steps',
    description: '',
    steps: [
      { stepId: 'ask1', name: 'First', behaviourType: 'INPUT', config: { artifactId: 'a1', transition: 'AUTO' } },
      { stepId: 'ask2', name: 'Second', behaviourType: 'INPUT', config: { artifactId: 'a1', transition: 'AUTO' } },
    ],
    artifacts: { a1: { id: 'a1', items: [] } },
  };
}

describe('Start-step chooser scoping (Guided edit mode only)', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('does not render in Advanced mode, only once Guided is active', async () => {
    const user = userEvent.setup();
    render(<WorkflowBuilder capabilities={allDisabled} initialWorkflow={twoStepWorkflow()} />);

    // A seeded document defaults to Advanced mode: no chooser, despite two eligible nodes.
    expect(screen.queryByTestId('guided-start-step-select')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('workflow-builder-mode-guided'));
    expect(await screen.findByTestId('guided-start-step-select')).toBeInTheDocument();

    await user.click(screen.getByTestId('workflow-builder-mode-advanced'));
    expect(screen.queryByTestId('guided-start-step-select')).not.toBeInTheDocument();
  });

  it('never renders in read-only mode, even when the stored builder mode is Guided', async () => {
    const user = userEvent.setup();
    // Persist Guided mode the way a user would: by selecting it in an edit session.
    const { unmount } = render(
      <WorkflowBuilder capabilities={allDisabled} initialWorkflow={twoStepWorkflow()} />,
    );
    await user.click(screen.getByTestId('workflow-builder-mode-guided'));
    expect(await screen.findByTestId('guided-start-step-select')).toBeInTheDocument();
    unmount();

    render(
      <WorkflowBuilder capabilities={allDisabled} mode="readOnly" initialWorkflow={twoStepWorkflow()} />,
    );
    expect(screen.queryByTestId('guided-start-step-select')).not.toBeInTheDocument();
  });
});
