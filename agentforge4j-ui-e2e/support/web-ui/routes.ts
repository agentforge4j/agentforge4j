// SPDX-License-Identifier: Apache-2.0

/** One public route on the agentforge4j.org site and the <h1> a healthy load renders. Kept as a
 *  single source so the routes/navigation/responsive specs can't drift out of sync with each
 *  other. Excludes `/builder` and `/catalogue/:id` — see builder-catalogue-entry.spec.ts, which
 *  checks those two separately (a heavy third-party embed and a route needing a real workflow id,
 *  respectively — both stop at "the public entry surface loads", per the site's own scope
 *  boundary against the separate Workflow Builder usability workstream, issues #94-#103). */
export interface SiteRoute {
  readonly path: string;
  readonly heading: string;
}

export const SITE_ROUTES: readonly SiteRoute[] = [
  { path: '/', heading: 'AgentForge4j' },
  // /docs is deliberately absent: it is NOT an agentforge4j-web-ui SPA route (see
  // agentforge4j-web-ui/src/config/nav.ts's `external` flag) — the Assembler track composes the
  // real Docusaurus build at that exact path on the deployed artifact, and the SPA must not
  // intercept it client-side. The functional specs here (routes/navigation/responsive) exercise
  // the plain SPA build only, so /docs has no place in this list; the assembled-site visual
  // capture of the real composed page lives as its own standalone entry in visual/manifest.ts.
  { path: '/api', heading: 'API reference' },
  { path: '/use', heading: 'Get started' },
  { path: '/catalogue', heading: 'Workflow catalogue' },
  { path: '/architecture', heading: 'Architecture' },
  { path: '/releases', heading: 'Releases' },
  { path: '/community', heading: 'Community & contributing' },
  { path: '/contributing', heading: 'Community & contributing' },
  { path: '/security', heading: 'Security' },
  { path: '/legal', heading: 'Legal' },
  { path: '/contact', heading: 'Contact' },
] as const;

/** A real, currently-shipped catalogue workflow id — used by the catalogue-detail entry check
 *  instead of a fabricated id, so the test fails honestly if the catalogue pipeline ever ships
 *  empty. */
export const REAL_CATALOGUE_WORKFLOW_ID = 'workflow-execution-estimator';

export interface NamedViewport {
  readonly name: string;
  readonly width: number;
  readonly height: number;
}

/** Representative sizes (mobile portrait/landscape, tablet portrait, tablet landscape at the
 *  `lg:` breakpoint boundary, laptop, and a large desktop) — not an exhaustive device matrix,
 *  but wide enough to catch orientation- and breakpoint-specific overflow that three
 *  portrait/laptop sizes alone couldn't. */
export const VIEWPORTS: readonly NamedViewport[] = [
  { name: 'mobile', width: 390, height: 844 },
  { name: 'mobile-landscape', width: 844, height: 390 },
  { name: 'tablet-portrait', width: 768, height: 1024 },
  { name: 'tablet-landscape', width: 1024, height: 768 },
  { name: 'laptop', width: 1440, height: 900 },
  { name: 'large-desktop', width: 1920, height: 1080 },
] as const;
