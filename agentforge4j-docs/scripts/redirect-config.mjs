// SPDX-License-Identifier: Apache-2.0
//
// Docs redirect toggle — a pure function consumed by docusaurus.config.ts so the
// pre-/post-first-release routing is computed from the support window, not hardcoded.
//
//   Pre-first-release (no stable version exists): the docs root and the moving `/latest` alias both
//     resolve to `/next/` — byte-identical to the Phase-1 wiring, so this is inert until the first cut.
//   Post-first-release: the root and the durable, bookmarkable `/latest` alias both resolve to the
//     newest stable version, and follow each release automatically.
//
// The toggle flips automatically the first time `versions.json` is non-empty; no code change is needed
// at release time.

/**
 * The version-path segment that is the current effective docs entry: `next` pre-first-release, or the
 * newest supported stable version once one exists. This is the single source of truth behind the
 * `/latest` redirect target — anything that needs to link into "the current docs" (the redirects
 * plugin, the navbar, the footer) derives its target from this, so there is exactly one place that
 * knows how to answer "where do I point today".
 *
 * @param {{latest: string|null}} window the result of supportWindow(...)
 * @returns {string} `next`, or the newest supported stable version
 */
export function docsEntryPath(window) {
  return (window && window.latest) || 'next';
}

/**
 * Compute the client-redirects for the current support window.
 *
 * @param {{latest: string|null}} window the result of supportWindow(...)
 * @returns {{from: string, to: string}[]} redirect entries for @docusaurus/plugin-client-redirects
 */
export function redirectConfig(window) {
  const latest = window && window.latest;
  if (!latest) {
    // Pre-first-release: preserve the exact Phase-1 behaviour.
    return [
      {from: '/', to: '/next/'},
      {from: '/latest', to: '/next/'},
    ];
  }
  // Post-first-release: the root and the durable `/latest` alias both resolve directly to the
  // newest stable version. The redirects plugin validates every `to` against the site's real
  // routes, so `/latest` cannot itself be a redirect target (a redirect is not a route) — the
  // intended outcome (`/` -> `/latest` -> newest stable) is expressed as two direct redirects
  // with the same reachable result. Proven by the scratch-cut's post-release build.
  return [
    {from: '/', to: `/${latest}/`},
    {from: '/latest', to: `/${latest}/`},
  ];
}
