// SPDX-License-Identifier: Apache-2.0
//
// Checks only the public entry surface of /builder and /catalogue/:id — mounts without
// crashing, no console errors. Deep interaction with the embedded workflow-builder editor
// (drag-drop, validation, undo/redo, persistence, mobile gating, ...) is a separate, already
// in-progress workstream (GitHub issues #94-#103) and is deliberately out of scope here.

import { expect, test } from '@playwright/test';
import { REAL_CATALOGUE_WORKFLOW_ID } from '../../support/web-ui/routes';

test.describe('Builder public entry surface', () => {
  test('/builder mounts the workflow builder with no console/page errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(String(error)));
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await page.goto('/builder');
    await expect(page.getByTestId('workflow-builder')).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('/builder is reachable from the primary nav (no dangling /visualizer link exists)', async ({
    page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Visualizer' })).toHaveCount(0);
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Builder' }).click();
    await expect(page).toHaveURL(/\/builder$/);
    await expect(page.getByTestId('workflow-builder')).toBeVisible();
  });
});

test.describe('Catalogue public entry surface', () => {
  test('/catalogue lists at least the real shipped workflow', async ({ page }) => {
    await page.goto('/catalogue');
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow catalogue' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Workflow Execution Estimator' })).toBeVisible();
  });

  test(`/catalogue/${REAL_CATALOGUE_WORKFLOW_ID} renders the detail page with no console/page errors`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(String(error)));
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });

    await page.goto(`/catalogue/${REAL_CATALOGUE_WORKFLOW_ID}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Workflow Execution Estimator' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Open the Builder' })).toHaveAttribute('href', '/builder');
    expect(errors).toEqual([]);
  });

  test('an unknown catalogue id renders the branded 404, not a fabricated entry', async ({ page }) => {
    await page.goto('/catalogue/this-workflow-does-not-exist');
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
  });
});
