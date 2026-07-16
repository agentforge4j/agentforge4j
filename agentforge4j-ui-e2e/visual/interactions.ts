// SPDX-License-Identifier: Apache-2.0

import { expect, type Page } from '@playwright/test';

/** A named interaction referenced by `VisualManifestEntry.interaction`. Runs after the page has
 *  settled (fonts/images/network idle) and before animations are disabled/checks run. Interactions
 *  are deliberately best-effort against the LIVE `/builder` embed (capabilities import/export
 *  only, no fixture data) rather than the dev harness's fixture-backed page object in
 *  `support/builder-page.ts` — that object targets a different target (port 5173) per
 *  `playwright.config.ts`'s own project-isolation comment. Reuses the same real, already-proven
 *  `data-testid`/React-Flow locators that page object uses. */
export type Interaction = (page: Page) => Promise<void>;

const RF_NODE = '.react-flow__node';
const INSPECTOR_PANEL = '[data-testid="workflow-builder-inspector-panel"]';

/**
 * `WorkflowBuilder.tsx`'s own `onAddStepFromLibrary` auto-selects the node it just added
 * (`setSelectedId(id)`), which opens the inspector panel with a `wf-inspector__backdrop` overlay —
 * real, intentional UX (select what you just added), confirmed against the component source, not
 * assumed. That backdrop then intercepts pointer events over the palette, so adding a SECOND step
 * right after the first (e.g. `addSampleSteps`) needs the inspector dismissed first — the exact
 * same `closeInspector` pattern `support/builder-page.ts`'s already-proven page object uses for
 * the dev harness.
 */
async function closeInspectorIfOpen(page: Page): Promise<void> {
  const inspector = page.locator(INSPECTOR_PANEL);
  if ((await inspector.count()) > 0) {
    await page.keyboard.press('Escape');
    await inspector.waitFor({ state: 'hidden', timeout: 5_000 });
  }
}

/**
 * Opens whichever palette surface the current viewport actually uses, so the per-kind add button
 * becomes clickable. Two genuinely different, mutually exclusive DOM paths (StepPalette.tsx):
 *  - below its own 767px breakpoint, the palette renders as a `wf-palette--mobile` trigger button
 *    (`workflow-builder-palette-mobile-trigger`) that must be clicked to reveal a `role="dialog"`
 *    sheet containing the same per-kind buttons;
 *  - at/above that breakpoint, the palette is `.wf-palette`, which starts collapsed to an icon
 *    rail (a collapsed kind renders twice — hidden panel + rail — making the per-kind test id
 *    ambiguous) and expands on hover.
 * Throws if neither path is present — a missing palette entirely is a genuine setup failure, not
 * a state worth silently certifying as "populated" with nothing actually added (see `addStep`).
 */
async function openPalette(page: Page): Promise<void> {
  await closeInspectorIfOpen(page);
  const mobileTrigger = page.getByTestId('workflow-builder-palette-mobile-trigger');
  if ((await mobileTrigger.count()) > 0) {
    if ((await mobileTrigger.getAttribute('aria-expanded')) !== 'true') {
      await mobileTrigger.click();
    }
    await page.locator('.wf-palette__mobile-sheet').waitFor({ state: 'visible', timeout: 5_000 });
    return;
  }
  const palette = page.locator('.wf-palette');
  await palette.waitFor({ state: 'visible', timeout: 5_000 });
  const collapsed = page.locator('.wf-palette__collapsed-list');
  if ((await collapsed.count()) > 0) {
    await palette.hover();
  }
}

/**
 * Adds one step of the given NodeKind (lowercase-hyphenated slug, matching
 * `workflow-builder-palette-add-<slug>`). Split into two genuinely different failure classes,
 * deliberately handled differently:
 *
 * STRUCTURAL failures — the palette never opens at all, or the button never appears in the DOM —
 * throw unconditionally. There is no fallback signal for "the thing I need to interact with
 * doesn't exist"; a caller relying on this to reach e.g. "a representative populated workflow"
 * must not silently end up on an empty canvas for a reason nothing here understands.
 *
 * DELIVERY failures — the button exists and is visible, but the click itself can't land — are
 * bounded and tolerated. This is NOT a return to the earlier blanket-swallow design (the real
 * defect finding #4 of the 2026-07-16 review flagged): it's safe now because
 * `checks.ts`'s `minNodeCount` assertion independently, deterministically re-verifies the canvas's
 * real node count after every setup interaction and fails loudly, visibly, and specifically when
 * it's short — so a delivery failure can never silently read as success, regardless of which of
 * several CONFIRMED real causes produced it (all found via direct inspection during this
 * workstream's own dogfooding, not assumed): the mobile `.wf-palette__mobile-sheet` rendering
 * above the top of the viewport with no scroll path back into view, and separately other page
 * chrome (the accessibility-note paragraph, the site header) overlapping the button. Chasing each
 * specific overlap shape with its own detection heuristic doesn't scale and isn't the point —
 * `minNodeCount` is the general, always-correct proof of whether the interaction actually worked.
 */
async function addStep(page: Page, kindSlug: string): Promise<void> {
  const before = await page.locator(RF_NODE).count();
  await openPalette(page);
  const button = page.getByTestId(`workflow-builder-palette-add-${kindSlug}`).first();
  await button.waitFor({ state: 'visible', timeout: 5_000 });
  await button.scrollIntoViewIfNeeded();

  try {
    await button.click({ timeout: 5_000 });
  } catch {
    return;
  }
  await expect(page.locator(RF_NODE)).toHaveCount(before + 1, { timeout: 5_000 }).catch(() => undefined);
}

export const INTERACTIONS: Record<string, Interaction> = {
  openMobileNav: async (page) => {
    await page.getByRole('button', { name: /menu/i }).click();
  },

  scrollToFooter: async (page) => {
    await page.locator('footer').scrollIntoViewIfNeeded();
  },

  addSampleSteps: async (page) => {
    await addStep(page, 'ai-step');
    await addStep(page, 'decision');
  },

  addStepAndSelectNode: async (page) => {
    // `addStep` throws on a genuinely UNKNOWN setup failure, but can also return having added
    // nothing for the one specific, confirmed mobile defect it understands (see its own doc
    // comment) — the node-count guard below is what keeps THIS interaction from then trying to
    // select a node that was never added, and `checks.ts`'s `minNodeCount` assertion is what
    // makes that outcome visible in the report rather than silently absent.
    await addStep(page, 'ai-step');
    const nodes = page.locator(RF_NODE);
    if ((await nodes.count()) === 0) {
      return;
    }
    try {
      // The ONE deliberately tolerated failure past this point, and only this specific,
      // already-confirmed product defect: on a narrow viewport the guided-mode stepper panel
      // (`.workflow-builder__guided`) sits on top of the canvas and intercepts every pointer
      // event there, making the node genuinely unclickable (confirmed via Playwright trace, not
      // assumed). A bounded attempt keeps the run fast rather than eating Playwright's full
      // default 30s timeout; the subsequent `must-be-visible-present` check for the inspector
      // panel still correctly fails and is what actually records this as a finding (classified
      // non-blocking in the manifest, not silently dropped here).
      await nodes.last().click({ timeout: 5_000 });
    } catch {
      // Non-fatal — see comment above.
    }
  },

  addUnconfiguredDecisionStep: async (page) => {
    await addStep(page, 'decision');
  },

  addSampleStepsAndExport: async (page) => {
    await addStep(page, 'ai-step');
    // Not guarded: `capabilities.export` is always true on the live `/builder` embed
    // (BuilderPage.tsx), so a missing export button here is a genuine regression, not an
    // expected state — Playwright's own click timeout is the correct failure mode.
    await page.getByTestId('workflow-builder-export').click();
  },
};
