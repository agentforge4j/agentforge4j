// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { BuilderPage, TID } from '../../support/builder-page';

test.describe('C6 — responsive', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('narrow container replaces the editor with the desktop-required notice, with no horizontal overflow', async ({
    page,
  }) => {
    const builder = new BuilderPage(page);
    await builder.goto();

    // The narrow-container gate: below the 767px container breakpoint the WHOLE editor is
    // replaced by the desktop-required notice — the pre-gate mobile affordances (palette
    // trigger, mode toggle, canvas) must be genuinely absent from the DOM, not merely
    // covered (same DOM-absence contract the package's own narrow-container-gate.test.tsx
    // and the visual manifest's builder-narrow-gate entry certify).
    await expect(page.getByTestId(TID.narrowNotice)).toBeVisible();
    await expect(page.getByTestId(TID.canvas)).toHaveCount(0);
    await expect(page.getByTestId(TID.mobileTrigger)).toHaveCount(0);
    await expect(page.getByTestId(TID.modeGuided)).toHaveCount(0);

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
