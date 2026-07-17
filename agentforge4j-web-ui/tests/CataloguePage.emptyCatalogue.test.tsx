// SPDX-License-Identifier: Apache-2.0
//
// Covers the zero-workflow catalogue case (plan Testing approach: "empty-catalog list state
// renders the honest empty message rather than a fabricated entry"). Mocks the generated-JSON
// import so this doesn't depend on the real repo ever having zero shipped workflows.

import { describe, expect, test, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('@/generated/catalogue-data.json', () => ({
  default: {
    workflows: [],
    catalogVersion: '0.0.0-test',
    minimumAgentForge4jVersion: '0.0.0',
    maximumAgentForge4jVersion: null,
    workflowSchemaVersion: 1,
  },
}));

describe('CataloguePage (empty catalogue)', () => {
  test('renders an honest empty state, never a fabricated placeholder entry', async () => {
    const { default: CataloguePage } = await import('@/pages/CataloguePage');
    render(
      <MemoryRouter>
        <CataloguePage />
      </MemoryRouter>,
    );

    expect(screen.getByText('No workflows are published yet.')).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });
});
