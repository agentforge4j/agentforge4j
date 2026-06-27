// SPDX-License-Identifier: Apache-2.0

import { expect, type Locator, type Page } from '@playwright/test';

/** React Flow built-in DOM hooks (do not testid React Flow internals — Phase 0 / design §2). */
export const RF = {
  node: '.react-flow__node',
  minimapNode: '.react-flow__minimap-node',
} as const;

/** Stable test ids the specs target. New seams extend the existing live `workflow-builder-*`
 *  namespace (no parallel `afb-*` namespace). */
export const TID = {
  root: 'workflow-builder',
  canvas: 'workflow-builder-canvas',
  aiAssist: 'workflow-builder-ai',
  modeGuided: 'workflow-builder-mode-guided',
  modeAdvanced: 'workflow-builder-mode-advanced',
  inspectorPanel: 'workflow-builder-inspector-panel',
  behaviourSection: 'workflow-builder-inspector-behaviour-section',
  addField: 'workflow-builder-inspector-add-field',
  removeField: 'workflow-builder-inspector-remove-field',
  addCase: 'workflow-builder-inspector-add-case',
  removeCase: 'workflow-builder-inspector-remove-case',
  mobileTrigger: 'workflow-builder-palette-mobile-trigger',
} as const;

/** Maps a NodeKind to the per-kind palette add-button test id. */
export function paletteAddTestId(kindSlug: string): string {
  return `workflow-builder-palette-add-${kindSlug}`;
}

/** Page object for the workflow-builder dev harness. */
export class BuilderPage {
  readonly page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  async goto(): Promise<void> {
    await this.page.goto('/');
    await expect(this.page.getByTestId(TID.root)).toBeVisible();
  }

  get canvasNodes(): Locator {
    return this.page.locator(RF.node);
  }

  get minimapNodes(): Locator {
    return this.page.locator(RF.minimapNode);
  }

  get inspector(): Locator {
    return this.page.getByTestId(TID.inspectorPanel);
  }

  /**
   * Switch to advanced mode: removes the guided stepper and opens the inspector Behaviour
   * section (`behaviorOpen = mode === 'advanced'`). Idempotent.
   */
  async enterAdvancedMode(): Promise<void> {
    await this.page.getByTestId(TID.modeAdvanced).click();
  }

  /**
   * Expand the desktop palette. In the collapsed state a kind renders twice (hidden panel +
   * collapsed rail), so the per-kind test id is ambiguous; expanding unmounts the rail and
   * leaves a single match. Safe no-op when already expanded.
   */
  async expandPalette(): Promise<void> {
    await this.page.locator('.wf-palette').hover();
    await expect(this.page.locator('.wf-palette__collapsed-list')).toHaveCount(0);
  }

  async addStep(kindSlug: string): Promise<void> {
    await this.expandPalette();
    const button = this.page.getByTestId(paletteAddTestId(kindSlug));
    await expect(button).toBeVisible();
    await button.click();
  }

  async selectNode(name: string): Promise<void> {
    await this.canvasNodes.filter({ hasText: name }).first().click();
  }
}
