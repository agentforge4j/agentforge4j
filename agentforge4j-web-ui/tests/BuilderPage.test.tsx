// SPDX-License-Identifier: Apache-2.0
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import BuilderPage from '@/pages/BuilderPage';
import { BUILDER_COPY } from '@/copy/builder';

function renderPage() {
  return render(<BuilderPage />);
}

describe('BuilderPage', () => {
  test('mounts the workflow builder root', () => {
    renderPage();
    expect(screen.getByTestId('workflow-builder')).toBeInTheDocument();
  });

  test('provides a visually-hidden page heading — the canvas has no other document structure until a step is selected', () => {
    renderPage();
    const heading = screen.getByRole('heading', { level: 1, name: BUILDER_COPY.heading });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveClass('sr-only');
  });

  test('does not render a read-only badge (editable mode)', () => {
    renderPage();
    expect(screen.queryByTestId('workflow-builder-readonly-badge')).not.toBeInTheDocument();
  });

  test('renders the import and export controls', () => {
    renderPage();
    expect(screen.getByTestId('workflow-builder-import')).toBeInTheDocument();
    expect(screen.getByTestId('workflow-builder-export')).toBeInTheDocument();
  });

  test('omits the save, run, publish, and AI-assist controls', () => {
    renderPage();
    expect(screen.queryByTestId('workflow-builder-save')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-run')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-publish')).not.toBeInTheDocument();
    expect(screen.queryByTestId('workflow-builder-ai')).not.toBeInTheDocument();
  });

  test('hands the shell-provided height down a definite flex chain to the builder', () => {
    // The builder sizes itself with height:100%, so the page must pass <main>'s definite
    // height through: a full-height flex column whose non-builder content cannot grow and
    // whose builder pane is the flex-1/min-h-0 remainder. overflow-y-auto is the short-
    // viewport fail-safe: when the builder's own CSS min-height (24rem) exceeds the
    // remaining space, the pane scrolls instead of spilling past the viewport-pinned shell.
    const { container } = renderPage();
    const root = container.firstElementChild as HTMLElement;
    expect(root).toHaveClass('flex', 'flex-col', 'h-full');
    expect(screen.getByText(BUILDER_COPY.accessibilityNote)).toHaveClass('shrink-0');
    const builderPane = screen.getByTestId('workflow-builder').parentElement as HTMLElement;
    expect(builderPane).toHaveClass('flex-1', 'min-h-0', 'overflow-y-auto');
  });

  test('structural guard: does not pass a custom adapters prop to WorkflowBuilder', () => {
    // Regression guard against reintroducing a site-side adapters override, which would
    // bypass the package's built-in import/export trust-model enforcement (size cap, step
    // cap, zip path-traversal guard, prototype-pollution strip, schema validation) that the
    // default adapters provide automatically.
    const here = dirname(fileURLToPath(import.meta.url));
    const source = readFileSync(resolve(here, '../src/pages/BuilderPage.tsx'), 'utf8');
    expect(source).not.toMatch(/\badapters\s*[:=]/);
    expect(source).not.toMatch(/BuilderAdapters/);
  });
});
