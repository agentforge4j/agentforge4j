// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import type { BuilderCapabilities, WorkflowDefinition } from '../src/api/types';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

/**
 * A workflow whose DECISION has no resolvable default/case targets — the same
 * incomplete-routing shape produced by a fresh decision OR by deleting a branch
 * target. The strict schema serializer (`serializeStepExecutable`) throws on it;
 * Layer 2 must convert that throw into a surfaced validation error instead of
 * tearing down the editor.
 */
function incompleteDecisionWorkflow(): WorkflowDefinition {
  return {
    id: 'wf-incomplete',
    name: 'Incomplete',
    description: '',
    steps: [
      {
        stepId: 'ask',
        name: 'Ask',
        behaviourType: 'INPUT',
        config: { artifactId: 'a1', transition: 'AUTO' },
      },
      {
        stepId: 'decide',
        name: 'Decide',
        behaviourType: 'BRANCH',
        config: { contextKey: 'k', branches: {}, defaultBranch: '' },
      },
    ],
    artifacts: { a1: { id: 'a1', items: [] } },
  };
}

describe('crash-proof draft-sync (Layer 2)', () => {
  it('does not tear down the editor when serialization of an incomplete draft throws, and surfaces a validation error', async () => {
    // Swallow the one expected dev-warn from the serialize guard.
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});

    render(<WorkflowBuilder capabilities={allDisabled} initialWorkflow={incompleteDecisionWorkflow()} />);

    // The builder stays mounted (no uncaught throw from the validation effect)...
    expect(screen.getByTestId('workflow-builder')).toBeInTheDocument();
    // ...and the incompleteness is surfaced via the existing validation flag.
    expect(await screen.findByText(/Validation issues/)).toBeInTheDocument();
    expect(ACTION_LABELS.validationIssues).toBe('Validation issues');

    warn.mockRestore();
  });
});
