// SPDX-License-Identifier: Apache-2.0
import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { canonicalUrl, findSeoRoute, type JsonLd } from '@/config/seo';
import { catalogueData } from '@/lib/catalogueData';
import { catalogueWorkflowDescription, catalogueWorkflowTitle } from '@/lib/catalogueSeo';

// `i` (case-insensitive) and an optional trailing slash before `$`, matching React Router's own
// default matching for the literal `/catalogue/` segment. The captured id itself is matched
// case-SENSITIVELY against real workflow data below (`entry.id === id`) — CatalogueDetailPage.tsx
// does the same exact-match lookup, so a wrong-case id renders that page's own NotFoundPage even
// though the *route* matched; this hook must agree with what actually rendered, not diverge from it.
const CATALOGUE_DETAIL_PATH = /^\/catalogue\/([^/]+?)\/?$/i;

function setMetaDescription(content: string): void {
  let tag = document.querySelector('meta[name="description"]');
  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute('name', 'description');
    document.head.appendChild(tag);
  }
  tag.setAttribute('content', content);
}

function setCanonical(href: string): void {
  let link = document.querySelector('link[rel="canonical"]');
  if (!link) {
    link = document.createElement('link');
    link.setAttribute('rel', 'canonical');
    document.head.appendChild(link);
  }
  link.setAttribute('href', href);
}

/** One entry of the route-scoped social-tag table below. `attribute` is the identifying attribute
 * the tag is keyed by — `property` for Open Graph, `name` for Twitter; the two vocabularies really
 * do differ here, and a crawler looking for `property="og:title"` does not accept `name="og:title"`
 * — and `source` names which of the route's three resolved values supplies its content. */
interface RouteScopedSocialTag {
  readonly attribute: 'property' | 'name';
  readonly key: string;
  readonly source: 'title' | 'description' | 'canonical';
}

/**
 * Must stay in exact agreement with `ROUTE_SCOPED_SOCIAL_TAGS` in scripts/build-seo.mjs — the
 * build-time static shell's own copy, and the authority for what a fresh page load already carries.
 * Re-declared rather than imported only because this module cannot import build-seo.mjs at all (it
 * pulls in node:child_process). Exported solely so `tests/usePageSeo.test.tsx` can import both
 * tables and assert they are equal — element for element, in both directions. That assertion is
 * what fails if they ever drift apart, and it needs this table by value: iterating only the build's
 * table (which is what the suite did at first) proves the hook writes everything the shell writes,
 * but says nothing about a tag the hook writes and the shell does not — one that would then ship to
 * JavaScript-executing consumers only, absent from every static shell a crawler receives, with
 * every gate green.
 *
 * That drift is not hypothetical — it is the exact defect this table exists to close. The static
 * shell rewrote all five of these per route while this hook rewrote none, so a visitor who arrived
 * on the home page and then navigated anywhere in the app carried the home page's og:title,
 * og:description, og:url, twitter:title and twitter:description onto every subsequent route, while
 * `document.title`, the description meta and the canonical link all updated correctly around them.
 * Nothing on the build side could see it (every shell was individually correct) and nothing on the
 * client side was looking.
 *
 * The site-CONSTANT social tags (og:type, og:site_name, og:image and its width/height/alt,
 * twitter:card, twitter:image) are deliberately absent: their value does not depend on the route,
 * index.html declares them once, and a route change has nothing to re-derive for them. That they
 * are present and correct on every built shell is proven separately by scripts/verify-seo.mjs.
 */
export const ROUTE_SCOPED_SOCIAL_TAGS: readonly RouteScopedSocialTag[] = [
  { attribute: 'property', key: 'og:title', source: 'title' },
  { attribute: 'property', key: 'og:description', source: 'description' },
  { attribute: 'property', key: 'og:url', source: 'canonical' },
  { attribute: 'name', key: 'twitter:title', source: 'title' },
  { attribute: 'name', key: 'twitter:description', source: 'description' },
];

/** Updates the existing tag in place whenever the shell already declared one (the normal case on a
 * real build — index.html ships all five and build-seo.mjs rewrites them per route), and only
 * creates a node when there genuinely is none. Selecting by the same `attribute`/`key` pair the tag
 * is keyed by is what makes this an update rather than an append: appending on every navigation
 * would leave a growing pile of contradictory og:title tags, and a crawler reading the first one
 * would see the value from whichever route the visitor happened to land on first. */
function setSocialMeta(values: Record<RouteScopedSocialTag['source'], string>): void {
  for (const { attribute, key, source } of ROUTE_SCOPED_SOCIAL_TAGS) {
    let tag = document.querySelector(`meta[${attribute}="${key}"]`);
    if (!tag) {
      tag = document.createElement('meta');
      tag.setAttribute(attribute, key);
      document.head.appendChild(tag);
    }
    tag.setAttribute('content', values[source]);
  }
}

// Must stay byte-identical to build-seo.mjs's exported JSON_LD_SCRIPT_ID — the id the build-time
// static shell stamps on its own JSON-LD script, and the one `setJsonLd` below looks for so a fresh
// load adopts that node instead of creating a duplicate beside it. Re-declared rather than imported
// only because this module cannot import build-seo.mjs at all (it pulls in node:child_process).
// tests/usePageSeo.test.tsx imports that constant and asserts on the id the hook CREATES, which is
// the single assertion in the suite that fails if these two ever drift apart — every other guard on
// this invariant lives on the build side and compares the shell against build-seo.mjs's own copy,
// so all of them stay green while production ships a shell this hook can no longer find.
const JSON_LD_SCRIPT_ID = 'seo-json-ld';

/** Adds, updates, or removes the page's `<script type="application/ld+json">` block to match
 * `jsonLd` exactly — `undefined` removes any block a previous route left behind, so structured
 * data never survives a client-side navigation to a route that doesn't declare its own (the build-
 * time shell, scripts/build-seo.mjs's `injectJsonLd`, only ever covers the *first* request; without
 * this, home's JSON-LD would either linger on every later route or never appear at all when a
 * route lands on `/` via client-side navigation rather than a fresh load).
 *
 * Sets `.textContent` rather than splicing a serialized string into `innerHTML` — this writes the
 * script node's text data directly, never engaging the HTML parser's "look for a literal `</script`"
 * scan the way string-based HTML assembly would, so unlike the build-time `injectJsonLd` (which
 * must escape the opening angle bracket defensively for exactly that reason) there is no
 * script-breakout risk here and no escaping is needed. */
function setJsonLd(jsonLd: JsonLd | undefined): void {
  const existing = document.getElementById(JSON_LD_SCRIPT_ID);
  if (jsonLd === undefined) {
    existing?.remove();
    return;
  }
  let script = existing;
  if (!script || script.tagName !== 'SCRIPT') {
    existing?.remove();
    script = document.createElement('script');
    script.id = JSON_LD_SCRIPT_ID;
    script.setAttribute('type', 'application/ld+json');
    document.head.appendChild(script);
  }
  script.textContent = JSON.stringify(jsonLd);
}

/** The complete SEO state of one route, resolved before anything is written to the document — so
 * every branch below produces the same shape and there is exactly one place that applies it. The
 * three independent apply-sites this replaced are precisely what let the social tags be added on the
 * build side and forgotten here. */
interface ResolvedRouteSeo {
  readonly title: string;
  readonly description: string;
  readonly canonical: string;
  readonly jsonLd?: JsonLd;
}

/** The one write path. `document.title`, the description meta and the canonical link carry the same
 * three values the route-scoped social tags are derived from, so a page's social metadata cannot
 * disagree with its own title/description/canonical — the property the audit found broken. */
function applyRouteSeo({ title, description, canonical, jsonLd }: ResolvedRouteSeo): void {
  document.title = title;
  setMetaDescription(description);
  setCanonical(canonical);
  setSocialMeta({ title, description, canonical });
  setJsonLd(jsonLd);
}

/** Resolves a path to its SEO state without touching the document, so every branch is forced to
 * produce one complete state rather than a partial set of side effects — which is the property that
 * makes `applyRouteSeo` above the single write path. Module-internal: the hook is the only caller,
 * and the suite exercises the mapping through it rather than around it. `null` only if the home
 * entry itself is missing from the committed config, which no real build can produce. */
function resolveRouteSeo(path: string): ResolvedRouteSeo | null {
  const staticEntry = findSeoRoute(path);
  if (staticEntry) {
    return {
      title: staticEntry.title,
      description: staticEntry.description,
      canonical: canonicalUrl(staticEntry.canonicalPath ?? staticEntry.path),
      jsonLd: staticEntry.jsonLd,
    };
  }

  const catalogueMatch = CATALOGUE_DETAIL_PATH.exec(path);
  const workflow = catalogueMatch
    ? catalogueData.workflows.find((entry) => entry.id === catalogueMatch[1])
    : undefined;
  if (workflow) {
    return {
      title: catalogueWorkflowTitle(workflow),
      description: catalogueWorkflowDescription(workflow),
      // Built from the real, matched `workflow.id` — never from the visited `path` — so a
      // trailing-slash or differently-cased "catalogue" segment on the visited URL can never leak
      // into the emitted canonical; the canonical is always the one clean, normalized address.
      canonical: canonicalUrl(`/catalogue/${workflow.id}`),
      // No catalogue workflow declares its own jsonLd today — leaving this undefined is what clears
      // whatever an earlier route (e.g. home) left behind, rather than letting it linger here.
    };
  }

  // Unmatched path (NotFoundPage) or any other route with no SEO entry: fall back to the home
  // entry rather than leaving a stale title/canonical from whatever route preceded it — matches the
  // static 404.html shell, whose head carries the same home title/canonical (copy-404.mjs copies the
  // built index.html shell before any per-route rewriting).
  const home = findSeoRoute('/');
  if (!home) {
    return null;
  }
  return {
    title: home.title,
    description: home.description,
    canonical: canonicalUrl('/'),
    jsonLd: home.jsonLd,
  };
}

/**
 * Keeps `document.title`, `<meta name="description">`, `<link rel="canonical">`, the route-scoped
 * Open Graph / Twitter tags, and any structured-data (`jsonLd`) block in sync with the current
 * client-side route. The build-time static per-route HTML shells (scripts/build-seo.mjs) only cover
 * the *first* request to a given route — any subsequent in-app navigation is client-side only and
 * never re-fetches a shell, so without this the tab title/canonical/social tags/JSON-LD would
 * silently keep whatever the initially-loaded shell said.
 */
export function usePageSeo(): void {
  const location = useLocation();

  useEffect(() => {
    const resolved = resolveRouteSeo(location.pathname);
    if (resolved) {
      applyRouteSeo(resolved);
    }
  }, [location.pathname]);
}
