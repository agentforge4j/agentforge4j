// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ValidationPanel } from '../src/validation-ui/ValidationPanel';
import type { DraftValidationIssue } from '../src/hooks/useWorkflowDraft';
import type { CanvasModel, CanvasNode } from '../src/model/canvasModel';
import { defaultNodeData } from '../src/model/mapper';

function node(id: string): CanvasNode {
  return { id, backendStepId: id, kind: 'AI_STEP', position: { x: 0, y: 0 }, data: defaultNodeData('AI_STEP') } as CanvasNode;
}

function model(nodes: CanvasNode[]): CanvasModel {
  return {
    workflowId: 'wf',
    workflowName: 'Test',
    description: '',
    startNodeId: nodes[0]?.id ?? null,
    nodes,
    edges: [],
    artifacts: {},
    blueprints: {},
  };
}

describe('ValidationPanel severity rendering', () => {
  it('renders the unreachable graph issue with the warning glyph, not the error glyph', () => {
    const issues: DraftValidationIssue[] = [
      { code: 'graph.warning.unreachable', message: '"Step" is not connected to the start.', stepId: 'step-1' },
    ];
    render(<ValidationPanel model={model([node('step-1')])} clientIssues={issues} onFix={() => {}} />);
    expect(screen.getByText('⚠')).toBeInTheDocument();
    expect(screen.queryByText('!')).not.toBeInTheDocument();
  });

  it('renders an ordinary client issue with the error glyph', () => {
    const issues: DraftValidationIssue[] = [
      { code: 'step.name', message: 'Step name is required.', stepId: 'step-1' },
    ];
    render(<ValidationPanel model={model([node('step-1')])} clientIssues={issues} onFix={() => {}} />);
    expect(screen.getByText('!')).toBeInTheDocument();
    expect(screen.queryByText('⚠')).not.toBeInTheDocument();
  });
});
