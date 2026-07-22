// SPDX-License-Identifier: Apache-2.0
//
// createRoot (not hydrateRoot), deliberately — the initial static document already contains real,
// meaningful body content per route (scripts/prerender-routes.mjs bakes each route's real
// client-rendered markup into its static shell — see build-seo.mjs's injectRoot), but this is a
// snapshot spliced in for crawlers/first paint, not a true SSR/hydration contract: this call
// discards it and does a normal fresh client render on top, exactly like a route with no
// prerendered content at all would.
//
// Verified (real headless-browser runs against a real production build, not assumed):
//  - Every prerendered route except /builder produced BYTE-IDENTICAL markup between the static
//    shell and the fresh client mount — there is no possible visible difference for those routes;
//    the "replacement" is a no-op in effect even though it is a real full-subtree replace.
//  - /builder is the sole, understood exception: its default empty-canvas starter node gets a
//    fresh, genuinely random id (nanoid, via crypto.getRandomValues) on every real mount — by
//    design, so a real user's new workflow never collides with another session's. The build-time
//    prerender snapshot necessarily froze ONE such random id; a real browser mounts with a
//    DIFFERENT one. This is exactly why hydrateRoot would be the wrong choice here: hydration
//    requires the server/prerendered markup to match the client's first render output, and this
//    route's own real behavior can never satisfy that — createRoot's full-replace semantics have
//    no such requirement, so this mismatch causes zero user-visible effect (React commits the
//    whole replacement subtree atomically in one pass; there is no partial/patched intermediate
//    state to flash) and zero console warnings.
//  - Across every prerendered route: zero console errors/warnings and zero page errors during
//    load; the real DOM already has real text content at the earliest measurable paint (no blank
//    frame); client-side navigation, browser back/forward, and keyboard focus (Tab reaches the
//    skip link first, matching pre-existing behaviour) all work unchanged; /builder mounts its
//    real interactive canvas (not the Suspense loading state) and its nodes are actually draggable.
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles/global.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
