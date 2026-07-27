// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import CatalogueDetailPage from '@/pages/CatalogueDetailPage';
import { catalogueData } from '@/lib/catalogueData';

function renderDetailAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/catalogue/${id}`]}>
      <Routes>
        <Route path="/catalogue/:id" element={<CatalogueDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CatalogueDetailPage (real generated data)', () => {
  const [estimator] = catalogueData.workflows;

  test('renders the real shipped workflow name and description', () => {
    renderDetailAt(estimator.id);
    expect(screen.getByRole('heading', { level: 1, name: estimator.name })).toBeInTheDocument();
    if (estimator.description) {
      expect(screen.getByText(estimator.description)).toBeInTheDocument();
    }
  });

  test('omits author/contact/version dl entries when the source workflow.json carries none', () => {
    renderDetailAt(estimator.id);
    // The real Estimator workflow.json has none of these (the schema does not even define the
    // fields) — no "Unknown"/"N/A" filler should render either.
    expect(screen.queryByText('Author')).not.toBeInTheDocument();
    expect(screen.queryByText('Contact')).not.toBeInTheDocument();
    expect(screen.queryByText('Version')).not.toBeInTheDocument();
    expect(screen.queryByText(/unknown/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^N\/A$/i)).not.toBeInTheDocument();
  });

  test('renders the static step-graph region with a descriptive accessible name', () => {
    renderDetailAt(estimator.id);
    const graph = screen.getByRole('img', { name: `Step graph for the ${estimator.name} workflow` });
    expect(graph.querySelector('svg')).not.toBeNull();
  });

  test('renders an "Open the Builder" link pointing at /builder, with a note that it opens an empty canvas', () => {
    renderDetailAt(estimator.id);
    expect(screen.getByRole('link', { name: 'Open the Builder' })).toHaveAttribute('href', '/builder');
    expect(screen.getByText(/isn't loaded automatically yet/)).toBeInTheDocument();
  });

  test('renders no docs/usage link anywhere on the page', () => {
    renderDetailAt(estimator.id);
    const links = screen.getAllByRole('link');
    for (const link of links) {
      expect(link.getAttribute('href')).not.toMatch(/\/docs/);
      expect(link.textContent ?? '').not.toMatch(/docs|usage/i);
    }
  });

  test('an unmatched id renders NotFoundPage instead of a fabricated entry', () => {
    renderDetailAt('this-workflow-does-not-exist');
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
  });
});
