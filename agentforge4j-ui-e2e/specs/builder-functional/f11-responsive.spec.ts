// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage } from '../../support/builder-page';
import { FUNCTIONAL_FIXTURES } from '../../fixtures/builder-functional-fixtures';
import { PALETTE_STEP_KINDS } from '../../fixtures/builder-functional-fixtures';
import { FUNCTIONAL_VIEWPORTS } from '../../support/viewports';

/**
 * Targeted responsive journeys (Task 6: "do not duplicate every test at every viewport"). The
 * mobile-width overflow/no-editor assertion already exists in `specs/builder/c6-responsive.spec.ts`
 * against the dev-server project — these tests cover the angle that suite doesn't: that below the
 * breakpoint the previously-broken controls (#97/#98/#103) are simply unreachable (the editor is
 * gone, not merely resized), at a narrower width than that suite checks, and that the palette
 * genuinely reaches all 6 step types at tablet width (#99), which no existing suite checks off
 * desktop. Tagged `@mobile-only`/`@tablet-only`/`@responsive` — see playwright.builder-functional.config.ts.
 */
test.describe('F11 — responsive coverage', () => {
  test('@mobile-only: below the breakpoint, the editor is replaced by the notice — the obstructed mobile controls from #97/#98/#103 are simply unreachable', async ({
    page,
  }) => {
    await page.setViewportSize(FUNCTIONAL_VIEWPORTS.mobileNarrow);
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);

    await expect(page.getByTestId('workflow-builder-narrow-notice')).toBeVisible();
    await expect(page.getByTestId('workflow-builder-canvas')).toHaveCount(0);
    await expect(page.getByTestId('workflow-builder-palette-mobile-trigger')).toHaveCount(0);
    await expect(builder.nameField).toHaveCount(0);

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('@mobile-only: the notice is reachable and informative (no dead-end)', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    const notice = page.getByTestId('workflow-builder-narrow-notice');
    await expect(notice).toBeVisible();
    await expect(notice).toContainText(/desktop|tablet/i);
    await expect(builder.canvasNodes).toHaveCount(0);
  });

  test('@tablet-only: all 6 step types are reachable via the palette, none clipped (#99)', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}`);
    await builder.expandPalette();
    for (const kind of PALETTE_STEP_KINDS) {
      await expect(page.getByTestId(`workflow-builder-palette-add-${kind.slug}`)).toBeVisible();
    }
  });

  test('@tablet-only: a critical creation journey still works at tablet width', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.empty.query}&caps=export`);
    await builder.setWorkflowName('Tablet Workflow');
    await builder.addStep('ask-user');
    await builder.addStep('ai-step');
    await expect(builder.canvasNodes).toHaveCount(3); // 1 starter + 2 added
    const draft = await builder.exportDraft();
    expect(draft.steps).toHaveLength(3);
  });

  test('@responsive: no horizontal page overflow at tablet or desktop width', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto(`?${FUNCTIONAL_FIXTURES.multiStep.query}`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
