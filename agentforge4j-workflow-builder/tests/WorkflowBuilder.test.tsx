import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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

describe('WorkflowBuilder', () => {
  it('does not render Import when capabilities.import is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('workflow-builder-import')).not.toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-canvas')).toHaveTextContent(
      'Builder canvas — Phase 3',
    );
  });

  it('does not render AI affordance when capabilities.aiAssist is false', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.queryByTestId('workflow-builder-ai')).not.toBeInTheDocument();
  });

  it('renders Import and AI when capabilities allow', () => {
    render(
      <WorkflowBuilder
        capabilities={{ ...allDisabled, import: true, aiAssist: true }}
      />,
    );
    expect(screen.getByTestId('workflow-builder-import')).toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-ai')).toBeInTheDocument();
  });
});
