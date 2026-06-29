// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';
import { BUILDER_FIXTURE } from '../../fixtures/builder-fixture';

/**
 * Part A — the edge-insert "+" splits an ordinary linear edge, dropping a new
 * step between its two endpoints. Behavioural proof = the new step lands between
 * the endpoints in serialized order. (The overlay-edge exclusion is kept at unit
 * level — no loadable workflow produces overlay edges, so a browser-level
 * construction would need a flaky React Flow handle-drag.)
 */
test.describe('C10 — edge-insert', () => {
  test('"+" affordances render on the linear continuation edges', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?caps=export');

    // The sample's four continuation edges are all linear → four insert points.
    await expect(builder.insertButtons).toHaveCount(4);
    await expect(page.getByTestId('edge-insert-e-0-1')).toBeVisible();
  });

  test('clicking "+" splices a new step between the two endpoints in serialized order', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?caps=export');

    const baseline = await builder.exportStepIds();
    const nodeCountBefore = await builder.canvasNodes.count();
    expect(baseline.indexOf('ask-user-dev')).toBeLessThan(baseline.indexOf('ai-step-dev'));

    // The demo stacks nodes vertically (same x), so this edge's "+" overlaps the
    // target node's top handle. Dispatch the click straight to the button — the
    // real onClick (insert mode) still fires; only the layout overlap is bypassed.
    await page.getByTestId('edge-insert-e-0-1').dispatchEvent('click');
    await expect(page.getByTestId(TID.insertBanner)).toBeVisible();
    await builder.addStep(BUILDER_FIXTURE.addableKindSlug);

    await expect(builder.canvasNodes).toHaveCount(nodeCountBefore + 1);

    const after = await builder.exportStepIds();
    const added = after.filter((id) => !baseline.includes(id));
    expect(added).toHaveLength(1);
    const newId = added[0];
    // The spliced step sits strictly between the edge's source and target.
    expect(after.indexOf('ask-user-dev')).toBeLessThan(after.indexOf(newId));
    expect(after.indexOf(newId)).toBeLessThan(after.indexOf('ai-step-dev'));
  });
});
