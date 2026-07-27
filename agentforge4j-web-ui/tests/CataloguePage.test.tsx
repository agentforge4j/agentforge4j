// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import CataloguePage from '@/pages/CataloguePage';
import { catalogueData } from '@/lib/catalogueData';

describe('CataloguePage (real generated data)', () => {
  test('renders one card per shipped workflow, sourced from catalogue-data.json', () => {
    render(
      <MemoryRouter>
        <CataloguePage />
      </MemoryRouter>,
    );

    expect(screen.getAllByRole('listitem')).toHaveLength(catalogueData.workflows.length);
    for (const workflow of catalogueData.workflows) {
      const link = screen.getByRole('link', { name: workflow.name });
      expect(link).toHaveAttribute('href', `/catalogue/${workflow.id}`);
    }
  });

  test('every rendered card carries the honest "Shipped" badge', () => {
    render(
      <MemoryRouter>
        <CataloguePage />
      </MemoryRouter>,
    );

    expect(screen.getAllByText('Shipped')).toHaveLength(catalogueData.workflows.length);
  });
});
