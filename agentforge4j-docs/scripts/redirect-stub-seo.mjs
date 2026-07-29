// SPDX-License-Identifier: Apache-2.0
//
// SEO for the composed artifact's client-redirect stubs — the pages
// `@docusaurus/plugin-client-redirects` writes for `/docs/` and `/docs/latest/`, and the ones
// `assemble-site.mjs`'s own `writeRedirectStubs` writes for an archived version's old addresses.
//
// Raw, the plugin's stubs are the thinnest possible HTML: a meta refresh, a relative canonical, a
// script that sets `window.location.href`, and nothing else — no `<title>`, no description, no
// robots directive, not even a `<body>`. Published on a static host they answer 200, which makes
// each one an indexable, title-less, near-empty page sitting at the site's most linked-to
// documentation address. `/docs/` in particular was the ONLY route into the documentation tree, so
// it was also the page most likely to be crawled first.
//
// This pass adds the metadata that makes such a page describe itself honestly, and changes nothing
// else:
//
//   - a real `<title>` and description, so it is not a blank result in an index or a share preview,
//     and NAMING THE DESTINATION IT ACTUALLY HAS — an archived version's stub says so, rather than
//     claiming to forward to the current documentation (see `redirectStubCopy`);
//   - `robots: noindex, follow` — the page has no content of its own to index, and `follow` keeps
//     the destination link crawlable so the redirect still passes signal through.
//
// What it deliberately does NOT touch: the canonical. This is the same policy javadoc-seo.mjs
// already applies to the redirect shells `javadoc (17)`'s IndexRedirectWriter emits — on a
// suppressed surface such a stub "gains exactly the robots tag, with its plugin canonical and body
// untouched" (asserted in assemble-site.test.mjs). A site with two redirect-shell policies has no
// policy, and the earlier version of this module absolutised the canonical on the stated grounds
// that "every other canonical this site publishes is absolute" — which was not true: the Javadoc
// redirect shells publish relative ones, on purpose. One rule now covers both surfaces.
//
// It also does not pretend to be an HTTP redirect. GitHub Pages (see .github/workflows/deploy.yml)
// serves static files with no redirect configuration of any kind, so a 301 from `/docs/` is not
// implementable on this host. The marketing site's own links are pointed straight at the
// destination instead (agentforge4j-web-ui/scripts/build-docs-entry.mjs), leaving these stubs to
// serve only the inbound links that already exist.

const REFRESH_PATTERN = /<meta\s+http-equiv="refresh"\s+content="0;\s*url=([^"]+)"\s*\/?>/i;
const TITLE_PATTERN = /<title>[\s\S]*?<\/title>/i;
// Attribute-tolerant on purpose. A `name="…"`-first pattern does not match the
// `<meta data-rh="true" name="robots" …>` shape a React-Helmet-rendered head carries — the very
// shape verify-noindex.mjs documents as what the real generated tag looks like — so a stricter
// pattern silently appended a SECOND robots/description tag instead of replacing the first. The
// `<title>` guard never had that hole (a title has no attributes), which is exactly why the defect
// was invisible: the post-condition below only counted titles.
const DESCRIPTION_PATTERN = /<meta[^>]*\sname="description"[^>]*>/i;
const ROBOTS_PATTERN = /<meta[^>]*\sname="robots"[^>]*>/i;

/** The archive mount every archived version's frozen artifact is published under. A stub pointing
 * here forwards to a superseded version, not to the current documentation. */
const ARCHIVE_PREFIX = '/docs/archive/';

/**
 * Whether `value` is an absolute HTTP(S) URL, decided by actually parsing it rather than by how it
 * begins.
 *
 * `value.startsWith('http')` — what this replaced — classifies `httpfoo/bar` as absolute (it is a
 * relative path), and classifies nothing else correctly either: it would let `javascript:...` or
 * `data:...` through as "relative". Parsing answers the real question, and the caller below rejects
 * any absolute URL that is not HTTP(S) rather than guessing what to do with it.
 *
 * Still load-bearing now that nothing is concatenated onto a site origin: `redirectStubCopy` below
 * decides which copy a stub gets by looking at the destination PATH, and that reading is only
 * meaningful for a value that really is a site-relative path.
 */
export function classifyRedirectTarget(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    // Not an absolute URL of any kind. Only a site-root-relative path is meaningful here: both
    // producers (the redirects plugin and assemble-site's own archive stubs) emit `/docs/...`, and
    // a `../`-style value cannot be classified against the archive mount at all.
    return value.startsWith('/') ? 'site-relative' : 'unusable';
  }
  return url.protocol === 'http:' || url.protocol === 'https:' ? 'absolute-http' : 'unusable';
}

/** The destination a client-redirect stub points at, or `null` when `html` is not one. Recognized
 * by the meta refresh itself rather than by filename: which paths the plugin generates a stub for
 * is derived from the version lifecycle (docusaurus.config.ts's `redirectConfig`) and changes at a
 * release cut, so a hardcoded list of stub paths would silently stop covering them. */
export function redirectStubTarget(html) {
  const match = REFRESH_PATTERN.exec(html);
  return match ? match[1] : null;
}

/**
 * The title and description for a stub forwarding to `target`, chosen from the destination itself.
 *
 * Two producers write stubs into `/docs/` of the composed artifact and they go to materially
 * different places: the redirects plugin's `/docs/` and `/docs/latest/` forward to the CURRENT
 * documentation, while `writeRedirectStubs` forwards every page route of an archived version to
 * that version's frozen copy under `/docs/archive/<v>/`. One shared string told the second group's
 * visitors they were being sent to the current documentation, which is the opposite of what
 * happens — and it said it on every page of every archived version, in the browser tab and in any
 * share preview.
 *
 * @param {string} target the stub's redirect destination, as written in its meta refresh
 * @returns {{title: string, description: string}}
 */
export function redirectStubCopy(target) {
  if (target.startsWith(ARCHIVE_PREFIX)) {
    return {
      title: 'Archived documentation — AgentForge4j',
      description:
        'This version of the AgentForge4j documentation has been archived. This address forwards to ' +
        'its archived copy; follow the link for the current version.',
    };
  }
  return {
    title: 'Documentation — AgentForge4j',
    description:
      'This address forwards to the current AgentForge4j documentation. Follow the link to the ' +
      'current version.',
  };
}

/**
 * Rewrites one client-redirect stub's `<head>`. The meta refresh, the canonical, the redirect
 * script and the body are all untouched — this only adds the metadata the stub never had.
 *
 * Idempotent by refusal, matching javadoc-seo.mjs's own style: a stub that already carries a robots
 * directive is returned unchanged rather than accumulating a second one, so re-running this over an
 * already-processed artifact cannot emit contradictory directives. Callers that need to tell
 * "nothing to do" from "nothing recognised" must count recognised stubs themselves — see
 * `applyRedirectStubSeo`.
 *
 * Every tag it writes REPLACES an existing one of the same kind when there is one, and is only
 * appended when there is not. That distinction is load-bearing rather than tidy: the plugin's stubs
 * carry no `<title>`, while `redirectHtml`'s archive stubs DO — so an unconditional append gave
 * every archived version's stub two `<title>` elements.
 *
 * @param {string} html
 * @param {{robots: string}} options the copy is not a caller choice — it follows the destination,
 *        see `redirectStubCopy`.
 */
export function injectRedirectStubSeo(html, { robots }) {
  const target = redirectStubTarget(html);
  if (target === null) {
    throw new Error('redirect-stub-seo: not a client-redirect stub — no meta refresh found');
  }
  if (ROBOTS_PATTERN.test(html)) {
    return html;
  }
  if (!/<\/head>/i.test(html)) {
    throw new Error('redirect-stub-seo: expected a </head> closing tag — template drift?');
  }
  if (classifyRedirectTarget(target) === 'unusable') {
    throw new Error(
      `redirect-stub-seo: refusing a redirect target that is neither a site-relative path nor an ` +
        `absolute HTTP(S) URL: ${JSON.stringify(target)}`,
    );
  }

  const { title, description } = redirectStubCopy(target);
  let result = html;

  // Replace-or-append, per tag. See this function's own doc comment for the archive stub that made
  // the difference between the two a real defect rather than a stylistic choice.
  result = replaceOrAppend(result, TITLE_PATTERN, `<title>${escapeHtmlText(title)}</title>`);
  result = replaceOrAppend(
    result,
    DESCRIPTION_PATTERN,
    `<meta name="description" content="${escapeHtmlAttribute(description)}">`,
  );
  // The robots tag is unconditionally an append: the early return above already established that
  // this page carries none.
  result = result.replace(/<\/head>/i, () => `<meta name="robots" content="${escapeHtmlAttribute(robots)}">\n</head>`);

  assertExactlyOneOfEach(result, target);
  return result;
}

/**
 * Post-condition, checked on the real output rather than trusted from the edits above. A page with
 * two titles is exactly the defect this function once shipped; the same replace-or-append logic can
 * produce a duplicate description or robots tag the moment a pattern stops matching the shape a
 * producer emits, so all three are counted, not just the one that failed first.
 */
function assertExactlyOneOfEach(html, target) {
  for (const [what, pattern] of [
    ['<title>', /<title>/gi],
    ['name="description"', /<meta[^>]*\sname="description"[^>]*>/gi],
    ['name="robots"', /<meta[^>]*\sname="robots"[^>]*>/gi],
  ]) {
    const count = (html.match(pattern) ?? []).length;
    if (count !== 1) {
      throw new Error(
        `redirect-stub-seo: produced ${count} ${what} element(s) for ${target} — expected exactly one`,
      );
    }
  }
}

/** Replaces the first match of `pattern` with `replacement`, or inserts `replacement` before
 * `</head>` when the document has no such tag yet. Function replacers throughout: a `$&`/`` $` ``/
 * `$'` sequence in a value would otherwise be expanded as a substitution pattern — the same guard
 * build-seo.mjs and javadoc-seo.mjs already document. */
function replaceOrAppend(html, pattern, replacement) {
  if (pattern.test(html)) {
    return html.replace(pattern, () => replacement);
  }
  return html.replace(/<\/head>/i, () => `${replacement}\n</head>`);
}

// Function replacers throughout above (never a bare replacement string): a value containing `$&`,
// "$`" or `$'` would otherwise be read as a substitution pattern and silently corrupt the output —
// the same guard build-seo.mjs and javadoc-seo.mjs already document.
//
// These are applied only to this module's OWN copy, never to a value read out of the page. An
// earlier version escaped the redirect target into a canonical it built, which double-escaped an
// already-entity-escaped destination (`/docs/a&amp;b/` became `/docs/a&amp;amp;b/`); nothing read
// out of the stub is re-emitted now, so that class of bug is gone rather than fixed.
function escapeHtmlAttribute(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escapeHtmlText(value) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
