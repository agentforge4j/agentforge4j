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
  narrowNotice: 'workflow-builder-narrow-notice',
  // 0.4.0 — read-only mode + step-connection UX seams.
  importButton: 'workflow-builder-import',
  exportButton: 'workflow-builder-export',
  saveButton: 'workflow-builder-save',
  runButton: 'workflow-builder-run',
  publishButton: 'workflow-builder-publish',
  readonlyBadge: 'workflow-builder-readonly-badge',
  inspectorReadonlyBanner: 'inspector-readonly-banner',
  runsAfterSelect: 'runs-after-select',
  insertBanner: 'insert-mode-banner',
} as const;

/** The serialized draft captured by the dev harness's `exportBundle` seam. */
export type CapturedExport = {
  count: number;
  draft: { steps: { stepId: string }[] } & Record<string, unknown>;
};

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

  /**
   * Navigate to the dev harness. `search` opts into harness query params
   * (e.g. `?mode=readOnly&caps=export`); omitted/empty preserves the default
   * mount the original specs rely on.
   */
  async goto(search = ''): Promise<void> {
    await this.page.goto(search ? `/${search}` : '/');
    await expect(this.page.getByTestId(TID.root)).toBeVisible();
  }

  get canvasNodes(): Locator {
    return this.page.locator(RF.node);
  }

  get canvasEdges(): Locator {
    return this.page.locator('.react-flow__edge');
  }

  /** Every rendered edge-insert "+" affordance (one per insertable linear edge). */
  get insertButtons(): Locator {
    return this.page.locator('[data-testid^="edge-insert-"]');
  }

  get minimapNodes(): Locator {
    return this.page.locator(RF.minimapNode);
  }

  get inspector(): Locator {
    return this.page.getByTestId(TID.inspectorPanel);
  }

  /** A canvas node by its React Flow `data-id` (the canvas node id, e.g. `n-1`). */
  nodeByDataId(id: string): Locator {
    return this.page.locator(`${RF.node}[data-id="${id}"]`);
  }

  /** The `transform` style of a node — its position projection in the flow plane. */
  async nodeTransform(id: string): Promise<string> {
    return this.nodeByDataId(id).evaluate((el) => (el as HTMLElement).style.transform);
  }

  /**
   * Click Export and return the draft the harness captured (requires
   * `?caps=export`). Waits for the capture count to advance so the read never
   * races the click.
   */
  async exportDraft(): Promise<CapturedExport['draft']> {
    const before = await this.exportCount();
    await this.page.getByTestId(TID.exportButton).click();
    await expect
      .poll(async () => this.exportCount())
      .toBeGreaterThan(before);
    const captured = await this.page.evaluate(
      () => (window as unknown as { __afbExport?: CapturedExport }).__afbExport ?? null,
    );
    if (!captured) {
      throw new Error('exportDraft: no __afbExport capture present');
    }
    return captured.draft;
  }

  async exportCount(): Promise<number> {
    return this.page.evaluate(
      () => (window as unknown as { __afbExport?: CapturedExport }).__afbExport?.count ?? 0,
    );
  }

  /** Ordered serialized step ids — the behavioral proof of linear ordering. */
  async exportStepIds(): Promise<string[]> {
    const draft = await this.exportDraft();
    return draft.steps.map((step) => step.stepId);
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

  /**
   * Select a node by its React Flow `data-id` (the canvas node id, e.g. `n-2`).
   * Deterministic — avoids the substring ambiguity of name matching (a decision
   * node renders its case labels, so e.g. "Loop" matches both the loop step and a
   * "loop" branch label). Closes any open inspector first, since its backdrop
   * intercepts canvas clicks.
   */
  async selectNodeById(id: string): Promise<void> {
    await this.closeInspector();
    await this.nodeByDataId(id).click();
    await expect(this.inspector).toBeVisible();
  }

  /** Dismiss the inspector (and its click-blocking backdrop) if it is open. */
  async closeInspector(): Promise<void> {
    if ((await this.inspector.count()) > 0) {
      await this.page.keyboard.press('Escape');
      await expect(this.inspector).toHaveCount(0);
    }
  }

  /**
   * Attempt a pointer drag of a node by `(dx, dy)`. In read-only mode React Flow
   * is non-draggable, so this is expected to be a no-op the spec then asserts.
   */
  async dragNodeBy(id: string, dx: number, dy: number): Promise<void> {
    const box = await this.nodeByDataId(id).boundingBox();
    if (!box) {
      throw new Error(`dragNodeBy: node ${id} has no bounding box`);
    }
    const cx = box.x + box.width / 2;
    const cy = box.y + box.height / 2;
    await this.page.mouse.move(cx, cy);
    await this.page.mouse.down();
    await this.page.mouse.move(cx + dx, cy + dy, { steps: 8 });
    await this.page.mouse.up();
  }
}
