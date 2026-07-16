// SPDX-License-Identifier: Apache-2.0

import { expect, test } from '@playwright/test';
import { evaluateDeterministicChecks, evaluateRuntimeSignals, type DomFacts } from '../../visual/checks';

function baseFacts(overrides: Partial<DomFacts> = {}): DomFacts {
  return {
    horizontalOverflowPx: 0,
    headings: [{ text: 'Home', visible: true, fontSizePx: 32 }],
    failedImages: [],
    zeroSizedMustBeVisible: [],
    offscreenMustBeVisible: [],
    mustBeVisibleMissing: [],
    mustNotBeVisiblePresent: [],
    clippedPanels: [],
    overlappingFixedPairs: [],
    navCoversHeading: false,
    primaryActionsInvisible: [],
    mainContentBlank: false,
    canvasNodeCount: 0,
    ...overrides,
  };
}

test.describe('evaluateDeterministicChecks — this suite requires no browser, no page fixture', () => {
  test('a fully clean page passes every check', () => {
    const results = evaluateDeterministicChecks(baseFacts());
    expect(results.every((r) => r.status === 'pass')).toBe(true);
  });

  test('horizontal overflow beyond the 2px tolerance fails', () => {
    const results = evaluateDeterministicChecks(baseFacts({ horizontalOverflowPx: 40 }));
    const check = results.find((r) => r.id === 'horizontal-overflow');
    expect(check?.status).toBe('fail');
    expect(check?.detail).toContain('40px');
  });

  test('a tiny 1px rounding overflow does not fail (tolerance)', () => {
    const results = evaluateDeterministicChecks(baseFacts({ horizontalOverflowPx: 1 }));
    expect(results.find((r) => r.id === 'horizontal-overflow')?.status).toBe('pass');
  });

  test('no headings at all fails headings-present', () => {
    const results = evaluateDeterministicChecks(baseFacts({ headings: [] }));
    expect(results.find((r) => r.id === 'headings-present')?.status).toBe('fail');
  });

  test('an invisible heading fails headings-readable, not headings-present', () => {
    const results = evaluateDeterministicChecks(
      baseFacts({ headings: [{ text: 'Home', visible: false, fontSizePx: 32 }] }),
    );
    expect(results.find((r) => r.id === 'headings-present')?.status).toBe('pass');
    expect(results.find((r) => r.id === 'headings-readable')?.status).toBe('fail');
  });

  test('a failed image fails images-load with the image reference in the detail', () => {
    const results = evaluateDeterministicChecks(baseFacts({ failedImages: ['/brand/logo.svg'] }));
    const check = results.find((r) => r.id === 'images-load');
    expect(check?.status).toBe('fail');
    expect(check?.detail).toContain('/brand/logo.svg');
  });

  test('a missing mustBeVisible element fails must-be-visible-present', () => {
    const results = evaluateDeterministicChecks(baseFacts({ mustBeVisibleMissing: ['role=navigation'] }));
    expect(results.find((r) => r.id === 'must-be-visible-present')?.status).toBe('fail');
  });

  test('a zero-sized mustBeVisible element fails must-be-visible-non-zero-size', () => {
    const results = evaluateDeterministicChecks(baseFacts({ zeroSizedMustBeVisible: ['footer'] }));
    expect(results.find((r) => r.id === 'must-be-visible-non-zero-size')?.status).toBe('fail');
  });

  test('an offscreen mustBeVisible element fails must-be-visible-in-viewport', () => {
    const results = evaluateDeterministicChecks(baseFacts({ offscreenMustBeVisible: ['[data-testid="cta"]'] }));
    expect(results.find((r) => r.id === 'must-be-visible-in-viewport')?.status).toBe('fail');
  });

  test('a present mustNotBeVisible element fails must-not-be-visible-absent', () => {
    const results = evaluateDeterministicChecks(
      baseFacts({ mustNotBeVisiblePresent: ['[data-testid="workflow-builder-save"]'] }),
    );
    expect(results.find((r) => r.id === 'must-not-be-visible-absent')?.status).toBe('fail');
  });

  test('a clipped panel fails no-clipped-panels and dedupes repeats in the detail', () => {
    const results = evaluateDeterministicChecks(
      baseFacts({ clippedPanels: ['wf-palette__body', 'wf-palette__body', 'wf-palette__body'] }),
    );
    const check = results.find((r) => r.id === 'no-clipped-panels');
    expect(check?.status).toBe('fail');
    expect(check?.detail).toBe('wf-palette__body');
  });

  test('overlapping fixed elements fails no-overlapping-fixed-elements', () => {
    const results = evaluateDeterministicChecks(baseFacts({ overlappingFixedPairs: ['header.nav × div.cta'] }));
    expect(results.find((r) => r.id === 'no-overlapping-fixed-elements')?.status).toBe('fail');
  });

  test('nav covering the heading fails nav-does-not-cover-content', () => {
    const results = evaluateDeterministicChecks(baseFacts({ navCoversHeading: true }));
    expect(results.find((r) => r.id === 'nav-does-not-cover-content')?.status).toBe('fail');
  });

  test('an invisible primary action fails primary-actions-visible', () => {
    const results = evaluateDeterministicChecks(baseFacts({ primaryActionsInvisible: ['Get started'] }));
    expect(results.find((r) => r.id === 'primary-actions-visible')?.status).toBe('fail');
  });

  test('a blank main content region fails main-content-not-blank', () => {
    const results = evaluateDeterministicChecks(baseFacts({ mainContentBlank: true }));
    expect(results.find((r) => r.id === 'main-content-not-blank')?.status).toBe('fail');
  });

  test('canvas-node-count is omitted entirely when minNodeCount is not set (non-Builder entries)', () => {
    const results = evaluateDeterministicChecks(baseFacts({ canvasNodeCount: 0 }));
    expect(results.find((r) => r.id === 'canvas-node-count')).toBeUndefined();
  });

  test('canvas-node-count fails when fewer nodes exist than the setup interaction claims — the real bug this check closes', () => {
    // The exact shape of the confirmed defect: a setup interaction silently failed to add
    // anything, leaving an empty canvas that every OTHER check still happily passes.
    const results = evaluateDeterministicChecks(baseFacts({ canvasNodeCount: 0 }), 2);
    const check = results.find((r) => r.id === 'canvas-node-count');
    expect(check?.status).toBe('fail');
    expect(check?.detail).toContain('expected at least 2');
    expect(check?.detail).toContain('found 0');
  });

  test('canvas-node-count passes when at least minNodeCount nodes exist', () => {
    const results = evaluateDeterministicChecks(baseFacts({ canvasNodeCount: 2 }), 2);
    expect(results.find((r) => r.id === 'canvas-node-count')?.status).toBe('pass');
  });

  test('canvas-node-count passes when MORE than minNodeCount nodes exist', () => {
    const results = evaluateDeterministicChecks(baseFacts({ canvasNodeCount: 5 }), 2);
    expect(results.find((r) => r.id === 'canvas-node-count')?.status).toBe('pass');
  });
});

test.describe('evaluateRuntimeSignals', () => {
  test('no signals passes', () => {
    expect(evaluateRuntimeSignals([]).status).toBe('pass');
  });

  test('a console error fails and includes its detail', () => {
    const result = evaluateRuntimeSignals([{ kind: 'console-error', detail: 'Uncaught TypeError: x is undefined' }]);
    expect(result.status).toBe('fail');
    expect(result.detail).toContain('Uncaught TypeError');
  });

  test('a failed request fails and includes its detail', () => {
    const result = evaluateRuntimeSignals([{ kind: 'request-failed', detail: 'GET /brand/logo.svg — net::ERR_ABORTED' }]);
    expect(result.status).toBe('fail');
    expect(result.detail).toContain('ERR_ABORTED');
  });

  test('caps the rendered detail at 10 signals even when more are present', () => {
    const signals = Array.from({ length: 15 }, (_, i) => ({ kind: 'console-error' as const, detail: `error ${i}` }));
    const result = evaluateRuntimeSignals(signals);
    expect(result.detail?.split(' | ')).toHaveLength(10);
  });
});
