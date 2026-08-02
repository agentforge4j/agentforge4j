// SPDX-License-Identifier: Apache-2.0
import { describe, expect, test } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ApiPage from '@/pages/ApiPage';

function renderApiPage() {
  return render(
    <MemoryRouter>
      <ApiPage />
    </MemoryRouter>,
  );
}

describe('ApiPage', () => {
  test('links the latest stable API reference to /javadoc/latest/', () => {
    renderApiPage();
    expect(screen.getByRole('link', { name: 'Latest stable API reference' })).toHaveAttribute(
      'href',
      '/javadoc/latest/',
    );
  });

  test('links the next (development) API reference to /javadoc/next/', () => {
    renderApiPage();
    expect(screen.getByRole('link', { name: 'Next (development) API reference' })).toHaveAttribute(
      'href',
      '/javadoc/next/',
    );
  });

  test('does not hard-code a version string anywhere in the page copy', () => {
    renderApiPage();
    // Design §7/§16's site-wide rule: no hard-coded version anywhere in website copy.
    expect(document.body.textContent).not.toMatch(/\bv?\d+\.\d+\.\d+\b/);
  });
});
