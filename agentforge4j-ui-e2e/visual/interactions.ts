// SPDX-License-Identifier: Apache-2.0

import type { Page } from '@playwright/test';

/** A named interaction referenced by `VisualManifestEntry.interaction`. Runs after the page has
 *  settled (fonts/images/network idle) and before animations are disabled/checks run. Interactions
 *  are deliberately best-effort against the LIVE `/builder` embed (capabilities import/export
 *  only, no fixture data) rather than the dev harness's fixture-backed page object in
 *  `support/builder-page.ts` — that object targets a different target (port 5173) per
 *  `playwright.config.ts`'s own project-isolation comment. Reuses the same real, already-proven
 *  `data-testid`/React-Flow locators that page object uses. */
export type Interaction = (page: Page) => Promise<void>;

const RF_NODE = '.react-flow__node';

/** The desktop palette collapses to an icon rail below its own width breakpoint; a collapsed kind
 *  renders twice (hidden panel + collapsed rail), making the per-kind test id ambiguous. Hovering
 *  expands it. Safe no-op if already expanded or if the palette isn't in the DOM at all (e.g. a
 *  narrow viewport where the palette is behind the mobile trigger instead). */
async function expandDesktopPaletteIfPresent(page: Page): Promise<void> {
  const palette = page.locator('.wf-palette');
  if ((await palette.count()) === 0) {
    return;
  }
  const collapsed = page.locator('.wf-palette__collapsed-list');
  if ((await collapsed.count()) > 0) {
    await palette.hover();
  }
}

/** Adds one step of the given NodeKind (lowercase-hyphenated slug, matching
 *  `workflow-builder-palette-add-<slug>`). Best-effort: if the button never becomes visible (e.g.
 *  the mobile palette trigger path, which this interaction set doesn't drive), it gives up
 *  quietly rather than failing the whole capture run — a missing step is still a real, reportable
 *  visual state (an incomplete interaction), not a crash. */
async function addStep(page: Page, kindSlug: string): Promise<void> {
  await expandDesktopPaletteIfPresent(page);
  const button = page.getByTestId(`workflow-builder-palette-add-${kindSlug}`);
  try {
    await button.first().waitFor({ state: 'visible', timeout: 5_000 });
    await button.first().click();
  } catch {
    // Non-fatal — see doc comment above.
  }
}

export const INTERACTIONS: Record<string, Interaction> = {
  openMobileNav: async (page) => {
    await page.getByRole('button', { name: /menu/i }).click();
  },

  scrollToFooter: async (page) => {
    await page.locator('footer').scrollIntoViewIfNeeded();
  },

  addSampleSteps: async (page) => {
    await addStep(page, 'agent');
    await addStep(page, 'decision');
  },

  addStepAndSelectNode: async (page) => {
    await addStep(page, 'agent');
    const nodes = page.locator(RF_NODE);
    if ((await nodes.count()) > 0) {
      try {
        // Bounded, non-fatal: on a narrow viewport the guided-mode stepper panel
        // (`.workflow-builder__guided`) can sit on top of the canvas and intercept every pointer
        // event there, making the node genuinely unclickable — a real, reportable visual/
        // interaction defect, not a test-infra problem. Letting this hang for Playwright's full
        // default 30s timeout would still fail only this one capture, but a bounded attempt keeps
        // the run fast and leaves time for the screenshot + deterministic checks to still run and
        // record the state (inspector-panel absent) as evidence, rather than the test erroring out
        // before a screenshot is ever taken.
        await nodes.last().click({ timeout: 5_000 });
      } catch {
        // Non-fatal — see comment above. The subsequent `mustBeVisible` check for the inspector
        // panel will correctly fail and record this as a real deterministic-check finding.
      }
    }
  },

  addUnconfiguredDecisionStep: async (page) => {
    await addStep(page, 'decision');
  },

  addSampleStepsAndExport: async (page) => {
    await addStep(page, 'agent');
    const exportButton = page.getByTestId('workflow-builder-export');
    if ((await exportButton.count()) > 0) {
      await exportButton.click();
    }
  },
};
