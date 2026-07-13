// @vitest-environment jsdom
import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { WorkflowBuilder } from '../src/api/WorkflowBuilder';
import { ACTION_LABELS } from '../src/copy/workflow-terminology';
import { NARROW_CONTAINER_BREAKPOINT_PX } from '../src/hooks/useNarrowContainerGate';
import type { BuilderCapabilities } from '../src/api/types';

const allDisabled: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

// tests/setup.ts installs a global no-op ResizeObserver (observe/unobserve/disconnect do
// nothing) so unrelated tests never see a callback fire. This suite needs to actually
// drive the callback with a synthetic contentRect, so it substitutes a controllable
// mock for the duration of the file — same pattern responsive-shell.test.tsx uses to
// locally override the global matchMedia default.
class ControllableResizeObserver {
  static instances: ControllableResizeObserver[] = [];
  private readonly callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    ControllableResizeObserver.instances.push(this);
  }

  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}

  fire(width: number): void {
    const entry = { contentRect: { width } } as ResizeObserverEntry;
    act(() => {
      this.callback([entry], this as unknown as ResizeObserver);
    });
  }
}

let originalResizeObserver: typeof ResizeObserver;

describe('narrow container gate', () => {
  beforeEach(() => {
    window.localStorage.clear();
    ControllableResizeObserver.instances = [];
    originalResizeObserver = globalThis.ResizeObserver;
    globalThis.ResizeObserver = ControllableResizeObserver as unknown as typeof ResizeObserver;
  });

  afterEach(() => {
    globalThis.ResizeObserver = originalResizeObserver;
  });

  it('renders the editor (not the notice) before any resize is observed', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
  });

  it('replaces the editor with the narrow-viewport notice below the breakpoint, mounting nothing else', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = ControllableResizeObserver.instances[ControllableResizeObserver.instances.length - 1]!;

    observer.fire(NARROW_CONTAINER_BREAKPOINT_PX - 1);

    expect(screen.getByTestId('workflow-builder-narrow-notice')).toBeInTheDocument();
    expect(screen.getByText(ACTION_LABELS.narrowViewportTitle)).toBeInTheDocument();
    expect(screen.getByText(ACTION_LABELS.narrowViewportBody)).toBeInTheDocument();

    // The editor itself must be genuinely unmounted underneath the notice, not merely
    // covered by it — this is what makes issues #97/#98/#103 unreachable rather than
    // papered over.
    expect(screen.queryByTestId('workflow-builder-canvas')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-mode-guided')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-mode-advanced')).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(ACTION_LABELS.workflowNamePlaceholder)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guided' })).not.toBeInTheDocument();
  });

  it('renders the editor normally at and above the breakpoint', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = ControllableResizeObserver.instances[ControllableResizeObserver.instances.length - 1]!;

    observer.fire(NARROW_CONTAINER_BREAKPOINT_PX);

    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
  });

  it('re-shows the editor after growing back above the breakpoint', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = ControllableResizeObserver.instances[ControllableResizeObserver.instances.length - 1]!;

    observer.fire(320);
    expect(screen.getByTestId('workflow-builder-narrow-notice')).toBeInTheDocument();

    observer.fire(1024);
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
  });

  it('ignores a zero-width measurement (not yet laid out) rather than treating it as narrow', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = ControllableResizeObserver.instances[ControllableResizeObserver.instances.length - 1]!;

    observer.fire(0);

    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
  });
});
