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

// Must stay byte-identical to build-seo.mjs's exported ROBOTS_META_ID — the id the build-time
// static dist/404.html stamps on its own robots meta, and the one `setRobots` below looks for.
// Re-declared rather than imported for the same reason as JSON_LD_SCRIPT_ID: this module cannot
// import build-seo.mjs at all (it pulls in node:child_process). tests/usePageSeo.test.tsx imports
// that constant and asserts on the id the hook CREATES, which is the single assertion in the suite
// that fails if the two ever drift apart.
const ROBOTS_META_ID = 'seo-robots';

/** Sets, updates, or removes THIS SITE'S OWN `<meta name="robots" id={ROBOTS_META_ID}>`. `null`
 * removes it, which is what a navigation OUT of a missing route back into a real one must do — a
 * `noindex` left behind by the previous route would quietly suppress a page that is perfectly
 * indexable.
 *
 * Keyed on the shared id rather than on `meta[name="robots"]`, so removal only ever reaches a node
 * this codebase wrote. Robots is the one tag here whose normal case is DELETION on the majority of
 * routes, which makes a name-keyed removal actively destructive: any robots directive the document
 * arrived with — a host embedding this SPA, a future index.html line, an injected
 * `max-image-preview:large` — would be present in the served HTML and then silently disappear the
 * moment the bundle hydrated. `setJsonLd` below is keyed on its own id for exactly this reason;
 * this is the same rule, applied to the tag that needed it more. Adoption comes free with it: the
 * static 404 shell stamps the same id, so a direct load updates that node rather than appending a
 * second, contradictory one beside it. */
function setRobots(content: string | null): void {
  const existing = document.getElementById(ROBOTS_META_ID);
  if (content === null) {
    existing?.remove();
    return;
  }
  let tag = existing;
  // Same defensive branch as `setJsonLd`: an element carrying our id that is not a `<meta>` at all
  // cannot be updated into one, so it is replaced rather than written through.
  if (!tag || tag.tagName !== 'META') {
    existing?.remove();
    tag = document.createElement('meta');
    tag.id = ROBOTS_META_ID;
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
interface ResolvedRouteSeo {
  readonly title: string;
  readonly description: string;
  /** `null` on a not-found address: no canonical link, and no `og:url` either — see `setCanonical`
   * and `setSocialMeta`. */
  readonly canonical: string | null;
  /** The `robots` directive this route needs, or `null` to clear one a previous route left. */
  readonly robots: string | null;
  readonly jsonLd?: JsonLd;
}

/** The one write path, covering every tag this hook owns: the title, the description meta, the
 * canonical link, this site's own `robots` directive, the route-scoped social tags and the
 * structured-data block. `document.title`, the description meta and the canonical link carry the
 * same three values the route-scoped social tags are derived from, so a page's social metadata
 * cannot disagree with its own title/description/canonical — the property the audit found broken. */
function applyRouteSeo({ title, description, canonical, robots, jsonLd }: ResolvedRouteSeo): void {
  document.title = title;
  setMetaDescription(description);
  setCanonical(canonical);
  setRobots(robots);
  setSocialMeta({ title, description, canonical });
  setJsonLd(jsonLd);
}

/** Resolves a path to its SEO state without touching the document, so every branch is forced to
 * produce one complete state rather than a partial set of side effects — which is the property that
 * makes `applyRouteSeo` above the single write path. Module-internal: the hook is the only caller,
 * and the suite exercises the mapping through it rather than around it.
 *
 * Total by construction: an address that matches no static route and no real catalogue workflow is
 * not an absence of an answer, it is the not-found answer (the last branch below), so there is no
 * path here that resolves to "nothing" and no caller-side guard to write. */
function resolveRouteSeo(path: string): ResolvedRouteSeo {
  const staticEntry = findSeoRoute(path);
  if (staticEntry?.redirectTo !== undefined) {
    // A redirect stub, not a page — the same state build-seo.mjs's injectRedirectStub writes into
    // this address's static shell, so a fresh load and the instant before App.tsx's <Navigate>
    // fires describe the address identically instead of momentarily contradicting each other. The
    // page branch below would otherwise emit a SELF-canonical on a redirecting address, and clear
    // the very `noindex` the stub is serving. Both are transient, but they are transient by luck of
    // render ordering rather than by construction, and verify-client-nav-seo.mjs deliberately
    // excludes redirect routes from its convergence corpus, so nothing else would catch it.
    return {
      title: staticEntry.title,
      description: staticEntry.description,
      canonical: canonicalUrl(staticEntry.redirectTo),
      robots: 'noindex, follow',
      jsonLd: staticEntry.jsonLd,
    };
  }
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
 * Keeps `document.title`, `<meta name="description">`, `<link rel="canonical">`, this site's own
 * `<meta name="robots">` (present on a not-found address, absent on every real page — see
 * `setRobots` for why it is removed by id rather than by name), the route-scoped Open Graph /
 * Twitter tags, and any structured-data (`jsonLd`) block in sync with the current client-side
 * route. The build-time static per-route HTML shells (scripts/build-seo.mjs) only cover the *first*
 * request to a given route — any subsequent in-app navigation is client-side only and never
 * re-fetches a shell, so without this the tab title/canonical/robots/social tags/JSON-LD would
 * silently keep whatever the initially-loaded shell said.
 */
export function usePageSeo(): void {
  const location = useLocation();

  useEffect(() => {
    applyRouteSeo(resolveRouteSeo(location.pathname));
  }, [location.pathname]);
}
