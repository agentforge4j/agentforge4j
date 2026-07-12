// SPDX-License-Identifier: Apache-2.0
//
// The real shipped Estimator workflow.json has no author/contact/version field (the schema
// doesn't even define them), so CatalogueDetailPage.test.tsx can only prove the "omit when
// absent" half of the no-fabrication rule. This file mocks the generated data to also prove the
// "render when present" half, and that null fields among present ones still don't fabricate
// filler copy.

import { describe, expect, test, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('@/generated/catalogue-data.json', () => ({
  default: {
    workflows: [
      {
        id: 'fixture-workflow',
        name: 'Fixture Workflow',
        description: 'A fixture workflow for optional-field rendering.',
        author: 'Jane Author',
        contact: 'team@example.org',
        version: '1.2.3',
        source: null,
        schemaVersion: 1,
        steps: [
          {
            kind: 'STEP',
            stepId: 'only-step',
            name: 'Only Step',
            behaviour: { type: 'FAIL', reason: 'fixture' },
          },
        ],
      },
    ],
    catalogVersion: '0.0.0-test',
    minimumAgentForge4jVersion: '0.0.0',
    maximumAgentForge4jVersion: null,
    workflowSchemaVersion: 1,
  },
}));

describe('CatalogueDetailPage (author/contact/version present)', () => {
  test('renders each present optional field under its label, with no fabricated filler', async () => {
    const { default: CatalogueDetailPage } = await import('@/pages/CatalogueDetailPage');
    render(
      <MemoryRouter initialEntries={['/catalogue/fixture-workflow']}>
        <Routes>
          <Route path="/catalogue/:id" element={<CatalogueDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Author')).toBeInTheDocument();
    expect(screen.getByText('Jane Author')).toBeInTheDocument();
    expect(screen.getByText('Contact')).toBeInTheDocument();
    expect(screen.getByText('team@example.org')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('1.2.3')).toBeInTheDocument();
    expect(screen.queryByText(/unknown/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/^N\/A$/i)).not.toBeInTheDocument();
  });
});
