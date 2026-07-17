// @vitest-environment jsdom
import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  readonly targets: Element[] = [];

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    ControllableResizeObserver.instances.push(this);
  }

  observe(target: Element): void {
    this.targets.push(target);
  }
  unobserve(): void {}
  disconnect(): void {}

  fire(width: number): void {
    const entry = { contentRect: { width } } as ResizeObserverEntry;
    act(() => {
      this.callback([entry], this as unknown as ResizeObserver);
    });
  }
}

/**
 * The gate's own observer is the one watching the builder ROOT element. React Flow also
 * creates ResizeObservers internally (they land on this mock too since it replaces the
 * global for the whole file), and the creation order between them is an implementation
 * detail of effect timing — select by observed target, never by instance order.
 */
function gateObserver(): ControllableResizeObserver {
  const root = screen.getByTestId('workflow-builder');
  const found = ControllableResizeObserver.instances.find((instance) => instance.targets.includes(root));
  expect(found, 'expected a ResizeObserver observing the builder root').toBeTruthy();
  return found!;
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
    const observer = gateObserver();

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
    const observer = gateObserver();

    observer.fire(NARROW_CONTAINER_BREAKPOINT_PX);

    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
  });

  it('re-shows the editor after growing back above the breakpoint', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = gateObserver();

    observer.fire(320);
    expect(screen.getByTestId('workflow-builder-narrow-notice')).toBeInTheDocument();

    observer.fire(1024);
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
  });

  it('gates synchronously from the first layout measurement — a narrow container shows the notice before paint, no editor flash', () => {
    // jsdom's getBoundingClientRect returns 0 by default (the "not yet measured" path the
    // other tests exercise); give the root a real narrow width for this one.
    const rectSpy = vi
      .spyOn(window.HTMLElement.prototype, 'getBoundingClientRect')
      .mockReturnValue({ width: 320, height: 600, top: 0, left: 0, bottom: 600, right: 320, x: 0, y: 0, toJSON: () => ({}) } as DOMRect);
    try {
      render(<WorkflowBuilder capabilities={allDisabled} />);

      // The notice is there from the first paint, with no ResizeObserver callback fired.
      expect(screen.getByTestId('workflow-builder-narrow-notice')).toBeInTheDocument();
      expect(screen.queryByTestId('workflow-builder-canvas')).not.toBeInTheDocument();
    } finally {
      rectSpy.mockRestore();
    }
  });

  it('ignores a zero-width measurement (not yet laid out) rather than treating it as narrow', () => {
    render(<WorkflowBuilder capabilities={allDisabled} />);
    const observer = gateObserver();

    observer.fire(0);

    expect(screen.getByTestId('workflow-builder-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-narrow-notice')).not.toBeInTheDocument();
  });
});
