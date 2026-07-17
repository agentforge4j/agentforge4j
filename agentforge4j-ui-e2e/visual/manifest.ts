// SPDX-License-Identifier: Apache-2.0

import { SITE_ROUTES, REAL_CATALOGUE_WORKFLOW_ID, VIEWPORTS } from '../support/web-ui/routes';
import { DELIVERY_TOLERANT_INTERACTIONS } from './interactions';

/** Which product this entry belongs to — kept distinct so a Day 2 report can separate ".org"
 *  release-blocker findings from Workflow Builder findings that belong to the dedicated
 *  builder-usability remediation workstream (issues #94-#103), per this workstream's scope
 *  boundary: visual review, not builder remediation. */
export type ProductSurface = 'org' | 'builder';

/** How this state is reached: a direct `.org` route, or a named setup routine run against a
 *  freshly-loaded page (used for Builder states that require UI interaction to reach — there is
 *  no URL that encodes "a populated canvas" or "the inspector open"). */
export type EntryTarget =
  | { readonly kind: 'route'; readonly path: string }
  | { readonly kind: 'builder-route'; readonly path: '/builder' }
  /** The workflow-builder package's own dev harness (port 5173, see playwright.config.ts) rather
   *  than the public site — used ONLY for states the public `/builder` route cannot reach today
   *  (see `builder-readonly-mode` below for why). Not part of the assembled `.org` site's own
   *  coverage; kept in the same manifest so the report can still track it as Builder-surface
   *  evidence, honestly labelled. */
  | { readonly kind: 'builder-harness'; readonly search: string };

export type ReleaseImportance = 'blocker' | 'important' | 'nice-to-have';

/**
 * A deliberate, documented classification of a specific deterministic check's failure on this
 * entry as NOT release-blocking — either because it's confirmed to be expected/by-design
 * (`requiresHumanConfirmation: false`, the default), or because it's a genuine, tracked finding
 * that is deliberately scoped OUT of this release gate rather than fixed here (e.g. Builder-surface
 * findings, which belong to the separate builder-usability remediation workstream, not the `.org`
 * release). Never silent: every entry here carries a `reason` that shows up in the generated
 * report, and only a check listed here is exempted — an unlisted, genuinely new failure on the
 * same entry still fails the strict release check. `requiresHumanConfirmation: true` marks a
 * judgment call that hasn't been independently confirmed either way (a warning), distinct from a
 * fully accepted/explained non-issue — both are equally non-blocking, but the report shows them
 * differently so neither reads as more settled than it actually is.
 */
export interface AcceptedFinding {
  readonly checkId: string;
  readonly reason: string;
  readonly requiresHumanConfirmation?: boolean;
  /** GitHub issue number, if this finding has been filed — purely informational (the report links
   *  it), not a separate exemption path. Exemption always requires a `checkId` match here; a
   *  filed issue number alone, with no matching `acceptedFindings` entry, exempts nothing (see the
   *  `VisualManifestEntry.knownIssues` doc comment for why that distinction matters). */
  readonly issue?: number;
  /** Restricts the exemption to specific viewport names on this entry (default: all of the
   *  entry's viewports). Use this when a defect is confirmed on one viewport only — e.g. a mobile-
   *  only rendering bug — so the SAME check failing on a DIFFERENT viewport of the same entry,
   *  for a genuinely different reason, still blocks. Never widen a viewport-specific defect to
   *  "all viewports" just for convenience; that's exactly the entry-level-blanket-exemption
   *  mistake this whole mechanism replaced. */
  readonly viewports?: readonly string[];
}

/** Every viewport this entry must be captured at, by name (see `support/web-ui/routes.ts`
 *  `VIEWPORTS`). Grouped into two named sets below so entries reference a group instead of
 *  hand-listing sizes, per the "avoid a huge matrix" guidance: `CORE_VIEWPORTS` for routine
 *  pages, `FULL_VIEWPORTS` for release-blocker / overflow-prone surfaces. */
export const CORE_VIEWPORTS = ['mobile', 'tablet-portrait', 'laptop'] as const;
export const FULL_VIEWPORTS = VIEWPORTS.map((v) => v.name);

export interface VisualManifestEntry {
  /** Stable id — becomes the screenshot filename stem and the report row key. Never reused once
   *  a report has referenced it; rename means a new id, not an edit in place. */
  readonly id: string;
  readonly surface: ProductSurface;
  readonly target: EntryTarget;
  /** Human label for the report ("Home", "Builder — populated workflow"). */
  readonly stateName: string;
  readonly viewports: readonly string[];
  /** Name of a registered interaction (see `interactions.ts`) run after the page settles and
   *  before the screenshot/checks. Omit for "the page as it loads, no interaction". */
  readonly interaction?: string;
  /** Playwright locator strings (CSS or `role=...[name=...]` shorthand handled by
   *  `checks.ts`'s `resolveLocator`) that MUST be visible once the state is reached — a
   *  deterministic-check failure if absent. */
  readonly mustBeVisible?: readonly string[];
  /** Locator strings that must NOT be visible in this state (e.g. Platform-only controls that
   *  must stay hidden, not just disabled, on the OSS `/builder` embed). */
  readonly mustNotBeVisible?: readonly string[];
  /** Locator strings masked (painted over) before capture because their content is genuinely
   *  dynamic (timestamps, generated ids) — never used to hide a real rendering problem. */
  readonly maskSelectors?: readonly string[];
  readonly aiReviewEnabled: boolean;
  readonly releaseImportance: ReleaseImportance;
  /** GitHub issue numbers this state is already known to INTERSECT — purely informational context
   *  the report displays (e.g. "this Builder state is part of the #99/#104 palette-clipping
   *  family"), NOT an exemption by itself. Deliberately does not exempt anything on its own:
   *  entry-level "any issue number present ⇒ every failing check here is non-blocking" was a real
   *  correctness bug (a state tagged for a known clipping issue could develop an unrelated blank
   *  page or console crash and still pass) — exemption ALWAYS requires a specific `acceptedFindings`
   *  entry naming the exact `checkId`, same as any other non-blocking classification. Use
   *  `AcceptedFinding.issue` to link a specific exemption back to one of these numbers once you
   *  have real evidence of which check actually fires for it; don't pre-emptively guess a mapping
   *  for a failure that isn't currently reproducing. */
  readonly knownIssues?: readonly number[];
  /** Per-check non-blocking classifications — see `AcceptedFinding`'s own doc comment for the full
   *  rationale. The ONLY thing that exempts a failing check from blocking the strict release
   *  check; a check id not listed here still blocks, `knownIssues` above notwithstanding. */
  readonly acceptedFindings?: readonly AcceptedFinding[];
  /** Minimum number of `.react-flow__node` elements this state's own setup interaction is
   *  supposed to leave on the canvas — the deterministic proof that e.g. "representative populated
   *  workflow" or "validation state" actually got there, rather than a setup failure silently
   *  leaving an empty canvas that still passes every other check (this is the real bug a prior
   *  version of `visual/interactions.ts`'s `addStep` had: every setup-interaction failure was
   *  silently swallowed, and NOTHING checked node count, so an empty canvas was indistinguishable
   *  from a correctly populated one). Omit for entries with no setup interaction (or none that adds
   *  nodes) — `checks.ts` skips this check entirely rather than asserting a meaningless "0". */
  readonly minNodeCount?: number;
  /** Capture the full scrollable page, not just the viewport. Off by default for Builder states
   *  (the canvas is its own scroll region; a full-page capture would just be mostly blank). */
  readonly fullPage: boolean;
  /** Free-text context surfaced verbatim in the report — used here to record real, load-bearing
   *  scope facts (e.g. "not reachable on the public site today") rather than silently guessing. */
  readonly notes?: string;
}

const orgRoute = (path: string): EntryTarget => ({ kind: 'route', path });

/** One manifest entry per Day 1 `SITE_ROUTES` row, at the routine coverage level. Extra states
 *  for the same route (nav open, footer, etc.) are separate entries below — this loop only
 *  covers "the page as it loads". Single source of truth: importing `SITE_ROUTES` instead of
 *  re-listing the 11 routes here is what keeps this manifest and the Day 1 functional testkit
 *  from drifting apart (Day 2 brief, Task 2: "avoid duplicating route lists across scripts"). */
const ROUTE_ENTRIES: VisualManifestEntry[] = SITE_ROUTES.map((route) => {
  const id = `org-${route.path === '/' ? 'home' : route.path.replace(/^\//, '').replace(/\//g, '-')}`;
  const overflowProne = route.path === '/architecture'; // embeds two SVG diagrams — see Task 2 brief
  // `/docs` on the ASSEMBLED site (this suite's actual target) is the real, separately-built
  // Docusaurus site, not agentforge4j-web-ui's own placeholder DocsPage.tsx that Day 1's
  // `SITE_ROUTES` heading label describes — its real page title differs and isn't this manifest's
  // concern (Docusaurus already runs its own pa11y-ci WCAG 2.1 AA gate, confirmed passing 28/28
  // pages in this workstream's own real build). Assert only that SOME h1 exists there, not its
  // exact wording.
  const isDocsHandoff = route.path === '/docs';
  // Computed once and reused by both `viewports` and `releaseImportance` below: every
  // release-blocking route gets full viewport coverage, not just the routes hand-picked here — a
  // future blocker route silently getting only CORE_VIEWPORTS (this file's own documented rule for
  // FULL_VIEWPORTS is "release-blocker / overflow-prone surfaces", just above) was a real gap
  // `/use` had prior to this fix.
  const releaseImportance: ReleaseImportance = route.path === '/' || route.path === '/use' ? 'blocker' : 'important';
  return {
    id,
    surface: 'org',
    target: orgRoute(route.path),
    stateName: route.heading,
    viewports: overflowProne || releaseImportance === 'blocker' ? FULL_VIEWPORTS : CORE_VIEWPORTS,
    // `role=navigation` deliberately NOT required here: on a narrow viewport the site's real,
    // Day-1-tested mobile pattern collapses navigation entirely behind a hamburger button until
    // clicked (SiteHeader.tsx conditionally renders the mobile `<nav>` only while open) — the
    // dedicated `org-home-nav-mobile-open`/`org-home-nav-mobile-closed` entries below cover that
    // interactive contract instead of asserting it unconditionally on every route/viewport.
    mustBeVisible: [isDocsHandoff ? 'role=heading[level=1]' : `role=heading[level=1][name="${route.heading}"]`],
    aiReviewEnabled: true,
    releaseImportance,
    fullPage: true,
    // Docusaurus's own fixed top navbar and its collapsible sidebar viewport geometrically
    // intersect at rest — plausibly correct, by-design fixed-header stacking (Docusaurus's real
    // pa11y-ci gate independently passes 28/28 pages at WCAG 2.1 AA in this workstream's own real
    // build), but not independently confirmed by this suite as either genuinely fine or a real
    // defect — a warning, not an accepted-and-explained non-issue like the Builder dev-harness
    // finding above.
    acceptedFindings: isDocsHandoff
      ? [
          {
            checkId: 'no-overlapping-fixed-elements',
            reason: "Docusaurus's own internal navbar/sidebar chrome, not .org site code — likely "
              + 'by-design fixed-header layering (backed by Docusaurus\'s own real, passing pa11y-ci '
              + 'WCAG 2.1 AA gate), but not independently confirmed by this check as correct.',
            requiresHumanConfirmation: true,
          },
        ]
      : undefined,
  };
});

export const VISUAL_MANIFEST: readonly VisualManifestEntry[] = [
  ...ROUTE_ENTRIES,

  // --- Additional ".org" visual states beyond "page as it loads" -------------------------------
  {
    id: 'org-home-nav-mobile-open',
    surface: 'org',
    target: orgRoute('/'),
    stateName: 'Home — mobile nav open',
    viewports: ['mobile'],
    interaction: 'openMobileNav',
    mustBeVisible: ['role=navigation'],
    aiReviewEnabled: true,
    releaseImportance: 'blocker',
    fullPage: true,
    notes: 'Regression target for the 2026-07-12 mobile-nav overlap fix (SiteHeader.tsx).',
  },
  {
    id: 'org-home-nav-mobile-closed',
    surface: 'org',
    target: orgRoute('/'),
    stateName: 'Home — mobile nav closed (default)',
    viewports: ['mobile'],
    // Not `role=navigation`: SiteHeader.tsx conditionally renders the mobile `<nav>` only while
    // `menuOpen` is true — on initial/closed load there is genuinely no nav landmark in the DOM at
    // all, by design (confirmed against the real component source, not assumed). The always-present,
    // always-reachable affordance in the closed state is the hamburger toggle itself.
    mustBeVisible: ['[aria-label="Open menu"]'],
    mustNotBeVisible: ['#primary-nav-mobile'],
    aiReviewEnabled: false,
    releaseImportance: 'important',
    fullPage: true,
  },
  {
    id: 'org-footer',
    surface: 'org',
    target: orgRoute('/'),
    stateName: 'Footer',
    viewports: CORE_VIEWPORTS,
    interaction: 'scrollToFooter',
    mustBeVisible: ['footer'],
    aiReviewEnabled: true,
    releaseImportance: 'important',
    fullPage: false,
  },
  {
    id: 'org-catalogue-detail',
    surface: 'org',
    target: orgRoute(`/catalogue/${REAL_CATALOGUE_WORKFLOW_ID}`),
    stateName: `Catalogue detail — ${REAL_CATALOGUE_WORKFLOW_ID}`,
    viewports: FULL_VIEWPORTS,
    mustBeVisible: ['role=heading[level=1]', 'role=img'],
    aiReviewEnabled: true,
    releaseImportance: 'blocker',
    fullPage: true,
    notes: 'Real shipped catalogue id (support/web-ui/routes.ts REAL_CATALOGUE_WORKFLOW_ID) — a '
      + 'generated static SVG graph, the highest clip/overflow risk on the whole site (see PR #88 '
      + 'merge-blocker fix #5, wf-svg max-width rule).',
  },

  // --- Workflow Builder public entry surface (`/builder` on the assembled site) -----------------
  {
    id: 'builder-empty',
    surface: 'builder',
    target: { kind: 'builder-route', path: '/builder' },
    stateName: 'Builder — empty workflow (default load)',
    viewports: FULL_VIEWPORTS,
    mustBeVisible: ['[data-testid="workflow-builder"]', '[data-testid="workflow-builder-canvas"]'],
    mustNotBeVisible: [
      '[data-testid="workflow-builder-save"]',
      '[data-testid="workflow-builder-run"]',
      '[data-testid="workflow-builder-publish"]',
      '[data-testid="workflow-builder-ai"]',
    ],
    aiReviewEnabled: true,
    releaseImportance: 'blocker',
    fullPage: false,
    notes: 'capabilities = {import:true, export:true, save:false, run:false, publish:false, '
      + 'aiAssist:false} (BuilderPage.tsx) — the mustNotBeVisible list is the actual 0.1.0 launch '
      + 'contract (DOM omission, not disabled buttons).',
  },
  {
    id: 'builder-populated',
    surface: 'builder',
    target: { kind: 'builder-route', path: '/builder' },
    stateName: 'Builder — representative populated workflow',
    viewports: CORE_VIEWPORTS,
    interaction: 'addSampleSteps',
    mustBeVisible: ['[data-testid="workflow-builder-canvas"]'],
    minNodeCount: 2,
    aiReviewEnabled: true,
    releaseImportance: 'blocker',
    fullPage: false,
    knownIssues: [99, 104],
    notes: 'Adds an AI_STEP and a DECISION step via the palette. Known clipping risk in the '
      + 'step-library panel tracked as #99/#104 (fixed in unmerged PR #108, not yet live).',
    // Confirmed real, new (2026-07-16 dogfooding, via direct DOM inspection — not guessed), NOT
    // yet filed as an issue — a separate Builder-remediation triage item, deliberately out of THIS
    // release gate's scope. Scoped to mobile only, and to (at least) two distinct confirmed
    // causes: (1) `.wf-palette__mobile-sheet` opens upward from its trigger with
    // `max-height: min(70vh, 28rem)` and `overflow: hidden` on itself — when the trigger sits low
    // enough on a short viewport, the sheet, and every button in its first ("Common") group
    // including AI_STEP, renders above y=0 with no scroll path back into view (its own
    // `getBoundingClientRect()` measured `top: -88` against an 844px-tall viewport); (2) even a
    // button that IS within the viewport's y-bounds (DECISION, lower in the sheet) can still be
    // unclickable because other page chrome — the accessibility-note paragraph above the canvas,
    // the fixed site header — visually overlaps and intercepts the click. `addStep` cannot deliver
    // the clicks it needs to reach 2 nodes for either reason, so `canvas-node-count` correctly
    // fails here — not silently.
    acceptedFindings: [
      {
        checkId: 'canvas-node-count',
        viewports: ['mobile'],
        reason: 'The mobile palette sheet (.wf-palette__mobile-sheet) can render above the top of '
          + 'the viewport with no reachable scroll path, and separately other page chrome (the '
          + "accessibility note, the site header) can overlap and intercept a button that IS "
          + 'within the viewport — both confirmed via direct inspection, not assumed. A real '
          + "Builder defect, pending triage, out of this .org release gate's scope.",
      },
    ],
  },
  {
    id: 'builder-inspector-selected',
    surface: 'builder',
    target: { kind: 'builder-route', path: '/builder' },
    stateName: 'Builder — selected-step inspector open',
    viewports: CORE_VIEWPORTS,
    interaction: 'addStepAndSelectNode',
    mustBeVisible: ['[data-testid="workflow-builder-inspector-panel"]'],
    minNodeCount: 1,
    aiReviewEnabled: true,
    releaseImportance: 'blocker',
    fullPage: false,
    // Two DISTINCT real, mobile-only Builder defect FAMILIES can each independently prevent this
    // state, and each is scoped to exactly the check it actually causes — never widened to "the
    // whole entry is fine on mobile":
    //  1. `canvas-node-count` — the same mobile-palette-unclickable defects as `builder-populated`
    //     above (off-screen sheet AND page-chrome overlap; this state's own setup also adds an
    //     AI_STEP first).
    //  2. `must-be-visible-present` — confirmed via Playwright trace: on a narrow viewport, the
    //     guided-mode stepper panel (.workflow-builder__guided) intercepts pointer events over the
    //     canvas, making a node genuinely unclickable even once one exists — the inspector never
    //     opens. None of these are yet filed as GitHub issues; all are pending Builder-remediation
    //     triage items, deliberately out of THIS release gate's scope.
    acceptedFindings: [
      {
        checkId: 'canvas-node-count',
        viewports: ['mobile'],
        reason: 'Same mobile palette-unclickable defects as builder-populated — see that entry '
          + 'for the confirmed technical detail.',
      },
      {
        checkId: 'must-be-visible-present',
        viewports: ['mobile'],
        reason: 'Confirmed via Playwright trace: on a narrow viewport, the guided-mode stepper '
          + 'panel (.workflow-builder__guided) intercepts pointer events over the canvas, making a '
          + 'node genuinely unclickable — the inspector never opens. Real Builder defect, tracked '
          + 'as a pending triage item (not yet filed as a GitHub issue), out of this .org release '
          + "gate's scope pending owner decision on which remediation family it joins.",
      },
    ],
  },
  {
    id: 'builder-validation',
    surface: 'builder',
    target: { kind: 'builder-route', path: '/builder' },
    stateName: 'Builder — validation state (incomplete DECISION step)',
    viewports: CORE_VIEWPORTS,
    interaction: 'addUnconfiguredDecisionStep',
    minNodeCount: 1,
    aiReviewEnabled: true,
    releaseImportance: 'important',
    fullPage: false,
    knownIssues: [95, 101],
    notes: 'A DECISION step with no configured branches is expected to surface the validation '
      + 'pill/panel. Known reachability bug tracked as #95/#101 (popover renders behind an open '
      + 'inspector; fixed in unmerged PR #106, not yet live) may reproduce here.',
    acceptedFindings: [
      {
        checkId: 'canvas-node-count',
        viewports: ['mobile'],
        reason: 'Same mobile palette-unclickable defects as builder-populated — see that entry '
          + 'for the confirmed technical detail. The DECISION button sits in the Flow group, a '
          + "different position than AI_STEP, but is confirmed equally unreachable here.",
      },
    ],
  },
  {
    id: 'builder-export-clicked',
    surface: 'builder',
    target: { kind: 'builder-route', path: '/builder' },
    stateName: 'Builder — after Export click',
    viewports: ['laptop'],
    interaction: 'addSampleStepsAndExport',
    minNodeCount: 1,
    aiReviewEnabled: false,
    releaseImportance: 'nice-to-have',
    fullPage: false,
    notes: 'Export confirmation banner (naming the produced filename) ships in unmerged PR #109 — '
      + 'expect no visible change on the currently-live embed; this state exists to catch a '
      + 'misleading UI, not to certify the feature. Laptop-only viewport, so the mobile '
      + 'palette-sheet-off-screen defect (see builder-populated) does not apply here.',
  },
  {
    id: 'builder-readonly-mode',
    surface: 'builder',
    // Deliberately the package's own dev harness, NOT the public site: BuilderPage.tsx never
    // passes a `mode` prop to <WorkflowBuilder>, so it always defaults to 'editable' — read-only
    // mode is genuinely unreachable on the live agentforge4j.org `/builder` route today. Captured
    // here anyway (package-level, not site-level evidence) so the gap itself is visible in the
    // report rather than silently absent from coverage.
    target: { kind: 'builder-harness', search: '?mode=readOnly' },
    stateName: 'Builder package — read-only mode (dev harness only; NOT reachable on the public site)',
    viewports: ['laptop'],
    mustBeVisible: ['[data-testid="workflow-builder-readonly-badge"]'],
    aiReviewEnabled: false,
    releaseImportance: 'nice-to-have',
    fullPage: false,
    notes: 'Real coverage gap, not a Day 2 omission: no UI path on agentforge4j.org sets '
      + "mode='readOnly'. Flag for the .org workstream if read-only is ever meant to be reachable "
      + 'publicly (e.g. a future /visualizer, itself explicitly not built yet).',
    acceptedFindings: [
      {
        checkId: 'headings-present',
        reason: 'The workflow-builder package\'s own dev harness genuinely renders no h1/h2/h3 on '
          + 'this route — a dev-tool-only page, not reachable on the public site (see this entry\'s '
          + 'own notes above). No user-facing surface is affected; no action needed.',
      },
    ],
  },
] as const;

/** Fails loudly (not silently) if a manifest entry references a viewport name that doesn't exist
 *  in `VIEWPORTS`, or two entries share an id — both would otherwise surface as a confusing
 *  capture-time crash far from the actual mistake. Called once by the capture spec against the
 *  real manifest; the optional `entries` param lets the manifest's own unit test exercise the
 *  failure paths against a synthetic, deliberately-broken list without mutating the real one. */
export function validateManifest(entries: readonly VisualManifestEntry[] = VISUAL_MANIFEST): void {
  const knownViewports = new Set(VIEWPORTS.map((v) => v.name));
  const seenIds = new Set<string>();
  for (const entry of entries) {
    if (seenIds.has(entry.id)) {
      throw new Error(`visual manifest: duplicate id "${entry.id}"`);
    }
    seenIds.add(entry.id);
    for (const viewport of entry.viewports) {
      if (!knownViewports.has(viewport)) {
        throw new Error(`visual manifest: entry "${entry.id}" references unknown viewport "${viewport}"`);
      }
    }
    if (entry.viewports.length === 0) {
      throw new Error(`visual manifest: entry "${entry.id}" has no viewports`);
    }
    // An entry whose interaction can silently add nothing (a known, tolerated mobile delivery
    // failure — see interactions.ts's DELIVERY_TOLERANT_INTERACTIONS doc comment) MUST set
    // minNodeCount, or that outcome passes every check silently instead of being caught by
    // canvas-node-count — this was previously true only by convention, never enforced.
    if (
      entry.interaction &&
      DELIVERY_TOLERANT_INTERACTIONS.has(entry.interaction) &&
      entry.minNodeCount === undefined
    ) {
      throw new Error(
        `visual manifest: entry "${entry.id}" uses interaction "${entry.interaction}", which can ` +
          'silently add nothing on a known, tolerated delivery failure — minNodeCount must be set ' +
          'so that outcome is caught by canvas-node-count instead of passing silently.',
      );
    }
  }
}
