// SPDX-License-Identifier: Apache-2.0
import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { canonicalUrl, findSeoRoute, NOT_FOUND_SEO, type JsonLd } from '@/config/seo';
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

/** `null` REMOVES the canonical link rather than pointing it anywhere. That is the not-found case,
 * and removal is the only honest answer there: a canonical naming the home page asks a crawler to
 * fold a nonexistent URL into a real one, and a self-referential one asserts the arbitrary address
 * is a real page. Removal (not merely "don't set it") matters because a client-side navigation into
 * a missing route inherits whatever the previous route left in the document. */
function setCanonical(href: string | null): void {
  const existing = document.querySelector('link[rel="canonical"]');
  if (href === null) {
    existing?.remove();
    return;
  }
  let link = existing;
  if (!link) {
    link = document.createElement('link');
    link.setAttribute('rel', 'canonical');
    document.head.appendChild(link);
  }
  link.setAttribute('href', href);
}

/** Sets, updates, or removes `<meta name="robots">`. `null` removes it, which is what a navigation
 * OUT of a missing route back into a real one must do — a `noindex` left behind by the previous
 * route would quietly suppress a page that is perfectly indexable. */
function setRobots(content: string | null): void {
  const existing = document.querySelector('meta[name="robots"]');
  if (content === null) {
    existing?.remove();
    return;
  }
  let tag = existing;
  if (!tag) {
    tag = document.createElement('meta');
    tag.setAttribute('name', 'robots');
    document.head.appendChild(tag);
  }
  tag.setAttribute('content', content);
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
 * pulls in node:child_process). `tests/usePageSeo.test.tsx` imports that constant and asserts the
 * two tables are equal, which is the single assertion that fails if they ever drift apart.
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
const ROUTE_SCOPED_SOCIAL_TAGS: readonly RouteScopedSocialTag[] = [
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
function setSocialMeta(values: Record<RouteScopedSocialTag['source'], string | null>): void {
  for (const { attribute, key, source } of ROUTE_SCOPED_SOCIAL_TAGS) {
    const existing = document.querySelector(`meta[${attribute}="${key}"]`);
    // A `null` source REMOVES its tag, exactly as `routeScopedSocialReplacements` does on the build
    // side. That is the not-found case: `og:url` states which URL the content belongs to, and the
    // address does not exist. Both surfaces must agree, or a direct load and a client-side
    // navigation to the same missing route would disagree about whether the tag is there at all.
    if (values[source] === null) {
      existing?.remove();
      continue;
    }
    let tag = existing;
    if (!tag) {
      tag = document.createElement('meta');
      tag.setAttribute(attribute, key);
      document.head.appendChild(tag);
    }
    tag.setAttribute('content', values[source] as string);
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
export interface ResolvedRouteSeo {
  readonly title: string;
  readonly description: string;
  /** `null` on a not-found address: no canonical link, and no `og:url` either — see `setCanonical`
   * and `setSocialMeta`. */
  readonly canonical: string | null;
  /** The `robots` directive this route needs, or `null` to clear one a previous route left. */
  readonly robots: string | null;
  readonly jsonLd?: JsonLd;
}

/** The one write path. `document.title`, the description meta and the canonical link carry the same
 * three values the route-scoped social tags are derived from, so a page's social metadata cannot
 * disagree with its own title/description/canonical — the property the audit found broken. */
function applyRouteSeo({ title, description, canonical, robots, jsonLd }: ResolvedRouteSeo): void {
  document.title = title;
  setMetaDescription(description);
  setCanonical(canonical);
  setRobots(robots);
  setSocialMeta({ title, description, canonical });
  setJsonLd(jsonLd);
}

/** Resolves a path to its SEO state without touching the document — pure, so the mapping itself is
 * testable independently of the DOM writes, and so every branch is forced to produce one complete
 * state rather than a partial set of side effects. `null` only if the home entry itself is missing
 * from the committed config, which no real build can produce. */
export function resolveRouteSeo(path: string): ResolvedRouteSeo | null {
  const staticEntry = findSeoRoute(path);
  if (staticEntry) {
    return {
      title: staticEntry.title,
      description: staticEntry.description,
      canonical: canonicalUrl(staticEntry.canonicalPath ?? staticEntry.path),
      // A real, indexable page: clears a `noindex` a preceding not-found route may have left behind.
      robots: null,
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
      // Same reason as the static-route branch: a real page must not inherit a noindex.
      robots: null,
      // No catalogue workflow declares its own jsonLd today — leaving this undefined is what clears
      // whatever an earlier route (e.g. home) left behind, rather than letting it linger here.
    };
  }

  // Unmatched path (NotFoundPage) or any other route with no SEO entry: its own not-found
  // metadata, never the home page's. This branch used to copy the home entry wholesale — title,
  // description, canonical AND the home page's WebSite+Organization+SoftwareSourceCode structured
  // data — so a mistyped address rendered as a second, indexable-looking home page whose only
  // protection was the HTTP 404 status (and `/404.html`, served at 200, had not even that).
  //
  // Matches what build-seo.mjs's `injectNotFoundHead` writes into the static dist/404.html head,
  // down to the absent canonical and the absent `og:url`, so a direct load and a client-side
  // navigation into a missing route converge rather than disagreeing about what a 404 is.
  return {
    title: NOT_FOUND_SEO.title,
    description: NOT_FOUND_SEO.description,
    canonical: null,
    robots: NOT_FOUND_SEO.robots,
    // Never the home page's structured data: those nodes assert this URL is the site's WebSite
    // entity and its Organization's home, which is untrue of an address that does not exist.
    jsonLd: undefined,
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
