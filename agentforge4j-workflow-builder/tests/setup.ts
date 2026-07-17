import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeAll } from 'vitest';

afterEach(async () => {
  cleanup();
  // Draft-recovery persistence (and useBuilderMode before it) touch localStorage on every
  // mount; without this, a draft saved by one test's WorkflowBuilder instance silently
  // restores into the next test's instance, accumulating canvas state across tests in the
  // same file. `cleanup()`'s unmount synchronously schedules the persistence hook's
  // unmount-flush write, but the write itself (`adapter.save` via the write queue) runs on
  // a later microtask — draining the macrotask queue first (`setTimeout`) guarantees that
  // write has actually landed in localStorage before it is cleared here.
  await new Promise((resolve) => setTimeout(resolve, 0));
  window.localStorage.clear();
});

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });

  globalThis.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;

  // jsdom does not implement scrollIntoView at all (not even as a no-op) — needed by the guided
  // checklist's "reveal and focus" field behavior (StepConfigPanel).
  if (!window.HTMLElement.prototype.scrollIntoView) {
    window.HTMLElement.prototype.scrollIntoView = () => {};
  }
});
