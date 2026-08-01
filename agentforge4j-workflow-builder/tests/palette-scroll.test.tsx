// SPDX-License-Identifier: Apache-2.0
// @vitest-environment jsdom
//
// Regression coverage for issue #99 (step-library panel clips 3 of 6 step types with no
// scroll affordance on desktop/tablet). Two things are worth locking in independently:
//
// 1. The DOM/behavioural shape: rendering StepPalette in "everything expanded" mode still
//    produces all 6 step types in the document (the reachability the issue cared about).
//    jsdom has no real layout engine, so this can't observe scrolling directly, but it
//    proves the items exist in the DOM rather than being conditionally omitted.
//
// 2. The CSS mechanism that makes `.wf-palette__body` a genuine scrolling flex child rather
//    than silently clipping. Vitest does not process CSS imports by default (`test.css` is
//    off), so `getComputedStyle` in this environment can't see cascade rules from the
//    stylesheet either. Instead this reads the actual CSS source and asserts the specific
//    declarations required for the pattern to work:
//      - `.wf-palette` (the element with a real, definite height — position: absolute;
//        top: 0; bottom: 0;) must be a column flex container, or none of its flex-item
//        descendants can grow to fill it.
//      - `.wf-palette__panel` sits directly between `.wf-palette` and `.wf-palette__body`.
//        If it is not itself a flex container, `flex: 1` on `.wf-palette__body` is inert
//        (flex properties only apply to a direct child of a flex/grid container) and the
//        panel shrinks to its content size instead of filling `.wf-palette`'s available
//        height — which is exactly what let the panel silently overflow and get
//        hard-clipped by `.wf-palette`'s own `overflow: hidden` in issue #99.
//      - `.wf-palette__body` must be allowed to grow (`flex: 1`) and to shrink below its
//        own content size (`min-height: 0` — the standard escape hatch from a flex item's
//        default `min-height: auto`, which otherwise refuses to shrink below content and
//        defeats `overflow: auto`), and must actually declare `overflow: auto`.
//
// This is a real, browser-behaviour-independent CSS/flexbox spec fact, so reading the
// stylesheet source is a faithful way to verify it without a real browser.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StepPalette } from '../src/palette/StepPalette';

const here = dirname(fileURLToPath(import.meta.url));
const cssSource = readFileSync(resolve(here, '../src/api/workflow-builder.css'), 'utf8');

/** Extracts the declaration body of the first `selector { ... }` block in the stylesheet. */
function ruleBody(selector: string): string {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = new RegExp(`${escaped}\\s*\\{([^}]*)\\}`).exec(cssSource);
  expect(match, `expected to find a "${selector} { ... }" rule in workflow-builder.css`).toBeTruthy();
  return match![1]!;
}

describe('StepPalette scroll CSS contract (issue #99)', () => {
  it('.wf-palette is a column flex container with a real height', () => {
    const body = ruleBody('.wf-palette');
    expect(body).toMatch(/display:\s*flex/);
    expect(body).toMatch(/flex-direction:\s*column/);
    expect(body).toMatch(/position:\s*absolute/);
    expect(body).toMatch(/top:\s*0/);
    expect(body).toMatch(/bottom:\s*0/);
  });

  it('.wf-palette__panel is a flex container that grows to fill .wf-palette (not content-sized)', () => {
    const body = ruleBody('.wf-palette__panel');
    expect(body).toMatch(/display:\s*flex/);
    expect(body).toMatch(/flex-direction:\s*column/);
    expect(body).toMatch(/flex:\s*1/);
    expect(body).toMatch(/min-height:\s*0/);
  });

  it('.wf-palette__body can grow, shrink below content size, and scrolls its own overflow', () => {
    const body = ruleBody('.wf-palette__body');
    expect(body).toMatch(/flex:\s*1/);
    expect(body).toMatch(/min-height:\s*0/);
    expect(body).toMatch(/overflow:\s*auto/);
  });

  it('renders all 6 step types (3 common + 3 flow) so a broken scroll container would make them unreachable rather than merely invisible', () => {
    const { container } = render(
      <StepPalette mode="advanced" onAddStep={() => {}} defaultCollapsed={false} />,
    );

    const items = container.querySelectorAll('.wf-palette__item');
    expect(items.length).toBeGreaterThanOrEqual(6);
  });
});
