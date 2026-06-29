// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

/**
 * Part A — the "Runs after" selector repositions a step along the linear chain.
 * Behavioural proof = the serialized step order changes; structural exclusion =
 * the selector is absent for DECISION / RETRY / branch-owned nodes (which the
 * unit suite proves return `hidden` from `getRunsAfterState`).
 */
test.describe('C9 — runs-after reposition', () => {
  test('repositioning a step to Start changes the serialized order', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?caps=export');
    await builder.enterAdvancedMode();

    const baseline = await builder.exportStepIds();
    expect(baseline.indexOf('ask-user-dev')).toBeLessThan(baseline.indexOf('ai-step-dev'));

    await builder.selectNodeById('n-1'); // AI step
    const select = page.getByTestId(TID.runsAfterSelect);
    await expect(select).toBeEnabled();
    await select.selectOption('__start__');

    const after = await builder.exportStepIds();
    // The repositioned step now precedes its former predecessor...
    expect(after.indexOf('ai-step-dev')).toBeLessThan(after.indexOf('ask-user-dev'));
    // ...and only the order changed — the set of steps is identical.
    expect([...after].sort()).toEqual([...baseline].sort());
  });

  test('the selector is absent for decision, retry and branch-owned nodes', async ({ page }) => {
    const builder = new BuilderPage(page);
    await builder.goto('?caps=export');
    await builder.enterAdvancedMode();

    // n-2 = DECISION, n-4 = RETRY, n-3 = branch-owned (decision case target).
    for (const id of ['n-2', 'n-4', 'n-3']) {
      await builder.selectNodeById(id);
      await expect(page.getByTestId(TID.runsAfterSelect)).toHaveCount(0);
    }
  });
});
