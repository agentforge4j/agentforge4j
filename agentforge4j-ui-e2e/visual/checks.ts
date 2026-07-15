// SPDX-License-Identifier: Apache-2.0

import type { Page } from '@playwright/test';
import type { VisualManifestEntry } from './manifest';

export type CheckStatus = 'pass' | 'fail' | 'skip';

export interface CheckResult {
  readonly id: string;
  readonly status: CheckStatus;
  readonly detail?: string;
}

/** Raw facts collected from the live DOM in one `page.evaluate` round trip (cheaper and more
 *  consistent than one round trip per check) and then turned into pass/fail `CheckResult`s by the
 *  pure `evaluateDeterministicChecks` function below — kept separate from DOM access so the
 *  pass/fail *logic* is unit-testable without a browser (Task 4: "must still work when no AI
 *  configuration is present" — trivially true here, but also directly testable in isolation). */
export interface DomFacts {
  readonly horizontalOverflowPx: number;
  readonly headings: readonly { readonly text: string; readonly visible: boolean; readonly fontSizePx: number }[];
  readonly failedImages: readonly string[];
  readonly zeroSizedMustBeVisible: readonly string[];
  readonly offscreenMustBeVisible: readonly string[];
  readonly mustBeVisibleMissing: readonly string[];
  readonly mustNotBeVisiblePresent: readonly string[];
  readonly clippedPanels: readonly string[];
  readonly overlappingFixedPairs: readonly string[];
  readonly navCoversHeading: boolean;
  readonly primaryActionsInvisible: readonly string[];
  readonly mainContentBlank: boolean;
}

/** Runs entirely inside the page (no Node-side DOM access) — kept as a single function literal so
 *  it can be passed straight to `page.evaluate`. Deliberately does not touch `mustNotBeVisible`
 *  elements beyond checking presence: a `display:none`/DOM-absent React omission (the real
 *  capability-hiding contract, per `builder-empty`'s manifest note) already reads as "not
 *  present" via `querySelector` returning null — exactly what this needs to catch a control that
 *  is merely `disabled` instead of truly hidden. */
function collectDomFacts(args: readonly [readonly string[], readonly string[]]): DomFacts {
  const [mustBeVisible, mustNotBeVisible] = args;

  // Whether an element is genuinely rendered — accounts for an ANCESTOR's `display:none`/
  // `visibility:hidden`/zero-opacity, not just the element's own computed style (a responsive
  // `hidden md:flex` container hides its children this way; the children's own `display` never
  // reads as `none`, only their rendered box collapses to zero size — checking only the element's
  // own style previously misread every responsively-hidden desktop-only control as "an invisible
  // primary action"). `checkVisibility()` (stable in this suite's Chromium) already implements
  // exactly this walk; a manual own-style/own-rect fallback covers only the same-element case for
  // an older engine, which is an acceptable, honestly-narrower fallback, not a silent gap.
  function isRendered(el: Element): boolean {
    const withCheck = el as Element & { checkVisibility?: (opts?: Record<string, boolean>) => boolean };
    if (typeof withCheck.checkVisibility === 'function') {
      return withCheck.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
    }
    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
  }

  function resolve(selector: string): Element | null {
    // A small, deliberately non-exhaustive shorthand for role queries the manifest uses
    // (`role=heading[level=1][name="X"]`, `role=navigation`) — full ARIA query semantics live in
    // Playwright's own `getByRole`, which this in-page evaluate cannot call; this covers exactly
    // the shapes the manifest actually uses today, and a resolve() that returns null for an
    // unsupported shape fails the check loudly (missing) rather than silently skipping it.
    const roleMatch = selector.match(/^role=([a-z]+)(?:\[level=(\d)])?(?:\[name="([^"]+)"])?$/);
    if (roleMatch) {
      const [, role, level, name] = roleMatch;
      const tag = role === 'heading' && level ? `h${level}` : role === 'navigation' ? 'nav' : `[role="${role}"]`;
      let candidates = Array.from(document.querySelectorAll(tag));
      if (name) {
        candidates = candidates.filter((el) => el.textContent?.trim() === name);
      }
      if (candidates.length === 0) {
        return null;
      }
      // A page can legitimately have more than one match for a landmark role at once (e.g. a
      // desktop nav hidden via `hidden md:flex` alongside a mobile nav, both real `<nav>`
      // elements) — prefer whichever instance is actually rendered for THIS viewport over
      // whichever happens to come first in DOM order, so this check answers "is navigation
      // reachable", not "is the first nav element in the markup visible".
      return candidates.find((el) => isRendered(el)) ?? candidates[0];
    }
    return document.querySelector(selector);
  }

  const viewportWidth = window.innerWidth;

  const headings = Array.from(document.querySelectorAll('h1, h2, h3')).map((el) => {
    const style = window.getComputedStyle(el);
    return {
      text: el.textContent?.trim() ?? '',
      visible: isRendered(el),
      fontSizePx: parseFloat(style.fontSize) || 0,
    };
  });

  const failedImages = Array.from(document.querySelectorAll('img'))
    .filter((img) => img.complete && img.naturalWidth === 0)
    .map((img) => img.src || img.outerHTML.slice(0, 120));

  const zeroSizedMustBeVisible: string[] = [];
  const offscreenMustBeVisible: string[] = [];
  const mustBeVisibleMissing: string[] = [];
  for (const selector of mustBeVisible) {
    const el = resolve(selector);
    if (!el) {
      mustBeVisibleMissing.push(selector);
      continue;
    }
    if (!isRendered(el)) {
      zeroSizedMustBeVisible.push(selector);
      continue;
    }
    // Horizontal only, deliberately: a `fullPage` capture (most manifest entries) legitimately
    // scrolls well past the initial viewport's vertical bounds for anything below the fold — that
    // is not a defect, just where the element sits on a long page. Horizontal overflow off either
    // edge, by contrast, is never expected on a responsive layout regardless of scroll position,
    // so it stays a real signal.
    const rect = el.getBoundingClientRect();
    const offscreen = rect.right <= 0 || rect.left >= viewportWidth;
    if (offscreen) {
      offscreenMustBeVisible.push(selector);
    }
  }

  const mustNotBeVisiblePresent: string[] = [];
  for (const selector of mustNotBeVisible) {
    const el = resolve(selector);
    if (el && isRendered(el)) {
      mustNotBeVisiblePresent.push(selector);
    }
  }

  // Clipped panels: any element whose own CSS hides overflow, but whose content is taller/wider
  // than the box itself — the exact shape of the real #99/#104 palette-clipping bug this check is
  // modelled on. Three deliberate exclusions, all confirmed against real false positives seen in
  // this suite's own dogfooding runs against the actual assembled site, not theoretical: (1) any
  // `react-flow`-classed element (the bare root class, not just its `react-flow__*` descendants) —
  // React Flow's own pannable canvas viewport is INTENTIONALLY larger than its clipping box; that's
  // how an infinite/pannable canvas works, not a bug in app code; (2) single-line ellipsis
  // truncation (`overflow:hidden` + `text-overflow:ellipsis` + `white-space:nowrap` together is the
  // standard, accessible "shorten this text" pattern); (3) the visually-hidden/"sr-only"
  // accessibility technique (an intentionally 1x1px clipped box carrying real text for a screen
  // reader while being invisible on-screen — `workflow-builder__subtitle` is exactly this pattern,
  // confirmed against its own source CSS, not guessed).
  const clippedPanels: string[] = [];
  for (const el of Array.from(document.querySelectorAll<HTMLElement>('[class]'))) {
    if (el.className.toString().includes('react-flow')) {
      continue;
    }
    if (el.clientWidth <= 1 && el.clientHeight <= 1) {
      continue;
    }
    const style = window.getComputedStyle(el);
    const hidesOverflow = style.overflow === 'hidden' || style.overflowY === 'hidden' || style.overflowX === 'hidden';
    if (!hidesOverflow) {
      continue;
    }
    const isEllipsisTruncation = style.textOverflow === 'ellipsis' && style.whiteSpace === 'nowrap';
    if (isEllipsisTruncation) {
      continue;
    }
    if (el.scrollHeight - el.clientHeight > 4 || el.scrollWidth - el.clientWidth > 4) {
      clippedPanels.push(el.className.toString().split(' ')[0] || el.tagName.toLowerCase());
    }
  }

  // Overlapping fixed/sticky elements: two independently-positioned chrome elements (nav, banners,
  // toolbars) whose boxes intersect almost always means one is unintentionally sitting on top of
  // the other's content, the same shape as the 2026-07-12 mobile-nav overlap bug this check is
  // modelled on. Scoped to elements actually rendered right now — an off-screen/hidden modal
  // backdrop (Docusaurus's own `navbar-sidebar__backdrop`, present in the DOM but inert until its
  // sidebar toggle is engaged) still carries `position:fixed` in its stylesheet even while
  // genuinely invisible, and comparing it against real on-screen chrome produced false positives.
  const fixedEls = Array.from(document.querySelectorAll<HTMLElement>('body *')).filter((el) => {
    const position = window.getComputedStyle(el).position;
    return (position === 'fixed' || position === 'sticky') && isRendered(el);
  });
  const overlappingFixedPairs: string[] = [];
  for (let i = 0; i < fixedEls.length; i += 1) {
    for (let j = i + 1; j < fixedEls.length; j += 1) {
      const a = fixedEls[i].getBoundingClientRect();
      const b = fixedEls[j].getBoundingClientRect();
      const intersects = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
      if (intersects && !fixedEls[i].contains(fixedEls[j]) && !fixedEls[j].contains(fixedEls[i])) {
        overlappingFixedPairs.push(
          `${fixedEls[i].tagName.toLowerCase()}.${fixedEls[i].className} × ${fixedEls[j].tagName.toLowerCase()}.${fixedEls[j].className}`,
        );
      }
    }
  }

  // Navigation covering content: the first visible heading must not sit underneath a fixed/sticky
  // nav/header's box.
  const navCandidates = Array.from(document.querySelectorAll('nav, header[role="banner"], header'));
  const nav = navCandidates.find((el) => isRendered(el)) ?? null;
  const firstHeading = document.querySelector('h1');
  let navCoversHeading = false;
  if (nav && firstHeading && isRendered(firstHeading)) {
    const navStyle = window.getComputedStyle(nav);
    if (navStyle.position === 'fixed' || navStyle.position === 'sticky') {
      const navRect = nav.getBoundingClientRect();
      const headingRect = firstHeading.getBoundingClientRect();
      navCoversHeading =
        headingRect.top < navRect.bottom &&
        headingRect.bottom > navRect.top &&
        headingRect.left < navRect.right &&
        headingRect.right > navRect.left;
    }
  }

  // Invisible primary actions: any element that reads as an actionable control (button, submit
  // input, or link) but is NOT rendered due to its OWN styling (zero size, zero opacity) while
  // still occupying layout space some other way — a control a user genuinely cannot see or reach.
  // Deliberately NOT `!isRendered(el)` alone: that would also flag every control inside a
  // legitimately, intentionally hidden responsive container (e.g. the desktop nav on a mobile
  // viewport) as if it were a broken/invisible action, which is exactly the false-positive this
  // check produced before this rewrite. Only an element whose OWN computed style hides it while
  // its immediate parent is otherwise rendered is a genuine "control got squashed to invisible"
  // defect, not an intentional responsive omission.
  const primaryActionsInvisible: string[] = [];
  for (const el of Array.from(document.querySelectorAll<HTMLElement>('button, a[href], input[type="submit"]'))) {
    const parent = el.parentElement;
    if (!parent || !isRendered(parent)) {
      // The control's own container isn't rendered at all (e.g. inside a `hidden md:flex` desktop
      // nav on mobile) — an intentional responsive omission, not a visible-but-broken control.
      continue;
    }
    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    const ownRectZero = rect.width === 0 || rect.height === 0;
    // A zero-sized wrapper is not itself proof of invisibility: React Flow's edge-insert/label
    // buttons (and CSS positioning generally) legitimately use a zero-sized anchor element with a
    // transformed/overflow-visible child that does the actual, real on-screen rendering — confirmed
    // against a real false positive on `wf-edge-insert` buttons in this suite's own dogfooding.
    // Only flag a zero-sized control when NONE of its descendants render any visible area either.
    const hasVisibleDescendant =
      ownRectZero &&
      Array.from(el.querySelectorAll<HTMLElement>('*')).some((child) => {
        const childRect = child.getBoundingClientRect();
        return childRect.width > 0 && childRect.height > 0;
      });
    // A resting `opacity: 0` with a `transition: opacity ...` declared is the standard
    // hover/focus-reveal pattern (confirmed against a real false positive on `.wf-edge-insert`,
    // whose own CSS is exactly `opacity: 0` + `transition: opacity 120ms ease` + a `:hover`/
    // `:focus-visible` rule restoring it) — genuinely reachable via mouse/keyboard, not invisible.
    // A control that's opacity-0 with NO such transition has no discoverable path to becoming
    // visible at all, which is the real defect this check exists to catch.
    const isHoverRevealPattern = parseFloat(style.opacity) === 0 && style.transitionProperty.includes('opacity');
    const invisible =
      style.display !== 'none' &&
      style.visibility !== 'hidden' &&
      !isHoverRevealPattern &&
      (parseFloat(style.opacity) === 0 || (ownRectZero && !hasVisibleDescendant));
    if (invisible) {
      primaryActionsInvisible.push(el.textContent?.trim().slice(0, 60) || el.outerHTML.slice(0, 80));
    }
  }

  // Unexpected blank region: the primary <main> landmark (or body, if none) renders with no
  // visible text and no visible image — a genuinely empty page, not just a sparse one.
  const main = document.querySelector('main') ?? document.body;
  const hasVisibleText = (main.textContent ?? '').trim().length > 0;
  const hasVisibleImage = Array.from(main.querySelectorAll('img, svg')).some((el) => {
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  });
  const mainContentBlank = !hasVisibleText && !hasVisibleImage;

  return {
    horizontalOverflowPx: Math.max(0, document.documentElement.scrollWidth - viewportWidth),
    headings,
    failedImages,
    zeroSizedMustBeVisible,
    offscreenMustBeVisible,
    mustBeVisibleMissing,
    mustNotBeVisiblePresent,
    clippedPanels,
    overlappingFixedPairs,
    navCoversHeading,
    primaryActionsInvisible,
    mainContentBlank,
  };
}

export async function collectDomFactsFromPage(
  page: Page,
  entry: Pick<VisualManifestEntry, 'mustBeVisible' | 'mustNotBeVisible'>,
): Promise<DomFacts> {
  return page.evaluate(collectDomFacts, [entry.mustBeVisible ?? [], entry.mustNotBeVisible ?? []] as const);
}

/** Pure: turns `DomFacts` into pass/fail `CheckResult`s. No page/browser access — directly
 *  unit-testable, and this is the function that actually encodes "what counts as a defect" so
 *  that logic can be verified without launching Chromium. */
export function evaluateDeterministicChecks(facts: DomFacts): CheckResult[] {
  const results: CheckResult[] = [];

  results.push(
    facts.horizontalOverflowPx > 2
      ? { id: 'horizontal-overflow', status: 'fail', detail: `${facts.horizontalOverflowPx}px wider than the viewport` }
      : { id: 'horizontal-overflow', status: 'pass' },
  );

  const missingOrUnreadableHeadings = facts.headings.filter((h) => !h.visible || h.fontSizePx === 0 || h.text.length === 0);
  results.push(
    facts.headings.length === 0
      ? { id: 'headings-present', status: 'fail', detail: 'no h1/h2/h3 found on the page' }
      : { id: 'headings-present', status: 'pass' },
  );
  // Only meaningful once at least one heading exists — 'skip' rather than a vacuous 'pass' so the
  // report can't misread "no headings at all" as "the headings that don't exist are readable".
  results.push(
    facts.headings.length === 0
      ? { id: 'headings-readable', status: 'skip', detail: 'no headings to evaluate' }
      : missingOrUnreadableHeadings.length > 0
        ? {
            id: 'headings-readable',
            status: 'fail',
            detail: `${missingOrUnreadableHeadings.length} heading(s) invisible or zero-size`,
          }
        : { id: 'headings-readable', status: 'pass' },
  );

  results.push(
    facts.failedImages.length > 0
      ? { id: 'images-load', status: 'fail', detail: facts.failedImages.slice(0, 5).join('; ') }
      : { id: 'images-load', status: 'pass' },
  );

  results.push(
    facts.mustBeVisibleMissing.length > 0
      ? { id: 'must-be-visible-present', status: 'fail', detail: facts.mustBeVisibleMissing.join('; ') }
      : { id: 'must-be-visible-present', status: 'pass' },
  );

  results.push(
    facts.zeroSizedMustBeVisible.length > 0
      ? { id: 'must-be-visible-non-zero-size', status: 'fail', detail: facts.zeroSizedMustBeVisible.join('; ') }
      : { id: 'must-be-visible-non-zero-size', status: 'pass' },
  );

  results.push(
    facts.offscreenMustBeVisible.length > 0
      ? { id: 'must-be-visible-in-viewport', status: 'fail', detail: facts.offscreenMustBeVisible.join('; ') }
      : { id: 'must-be-visible-in-viewport', status: 'pass' },
  );

  results.push(
    facts.mustNotBeVisiblePresent.length > 0
      ? { id: 'must-not-be-visible-absent', status: 'fail', detail: facts.mustNotBeVisiblePresent.join('; ') }
      : { id: 'must-not-be-visible-absent', status: 'pass' },
  );

  results.push(
    facts.clippedPanels.length > 0
      ? { id: 'no-clipped-panels', status: 'fail', detail: [...new Set(facts.clippedPanels)].slice(0, 5).join('; ') }
      : { id: 'no-clipped-panels', status: 'pass' },
  );

  results.push(
    facts.overlappingFixedPairs.length > 0
      ? { id: 'no-overlapping-fixed-elements', status: 'fail', detail: facts.overlappingFixedPairs.join('; ') }
      : { id: 'no-overlapping-fixed-elements', status: 'pass' },
  );

  results.push(
    facts.navCoversHeading
      ? { id: 'nav-does-not-cover-content', status: 'fail', detail: 'fixed/sticky nav overlaps the h1 bounding box' }
      : { id: 'nav-does-not-cover-content', status: 'pass' },
  );

  results.push(
    facts.primaryActionsInvisible.length > 0
      ? { id: 'primary-actions-visible', status: 'fail', detail: facts.primaryActionsInvisible.slice(0, 5).join('; ') }
      : { id: 'primary-actions-visible', status: 'pass' },
  );

  results.push(
    facts.mainContentBlank
      ? { id: 'main-content-not-blank', status: 'fail', detail: 'no visible text or image in <main>' }
      : { id: 'main-content-not-blank', status: 'pass' },
  );

  return results;
}

/** A console message or failed network request captured by the caller during the page's
 *  lifetime (ownership of the Playwright listeners belongs to the capture spec, not this pure
 *  module — see the file header comment on `DomFacts`). */
export interface RuntimeSignal {
  readonly kind: 'console-error' | 'request-failed';
  readonly detail: string;
}

/** Pure and browser-free — classifies already-collected console/network signals. Kept separate
 *  from DOM checks so both remain independently unit-testable, and so a caller with zero AI
 *  configuration (or zero browser access, in a unit test) can still exercise this logic. */
export function evaluateRuntimeSignals(signals: readonly RuntimeSignal[]): CheckResult {
  if (signals.length === 0) {
    return { id: 'no-console-or-network-errors', status: 'pass' };
  }
  return {
    id: 'no-console-or-network-errors',
    status: 'fail',
    detail: signals.map((s) => `[${s.kind}] ${s.detail}`).slice(0, 10).join(' | '),
  };
}
