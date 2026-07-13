// SPDX-License-Identifier: Apache-2.0
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import BuilderPage from '@/pages/BuilderPage';

function renderPage() {
  return render(<BuilderPage />);
}

describe('BuilderPage', () => {
  test('mounts the workflow builder root', () => {
    renderPage();
    expect(screen.getByTestId('workflow-builder')).toBeInTheDocument();
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
