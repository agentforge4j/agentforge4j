// SPDX-License-Identifier: Apache-2.0
//
// Per-workflow title/description formatting for /catalogue/:id, shared by the client-side
// title/meta sync (usePageSeo) and the build-time static-shell generator
// (scripts/build-seo.mjs). The build script is plain Node ESM and cannot import this typed
// module directly (no bundler step ahead of it, unlike Vite for the client bundle), so it
// re-implements the same two small formatting rules against the same generated
// catalogue-data.json — kept deliberately tiny so the two stay easy to eyeball in sync; see the
// matching comment there.

const MAX_DESCRIPTION_LENGTH = 157;

/** Below this, a truncated description has stopped being a useful search snippet — so a sentence
 * boundary is only preferred over a longer word-boundary cut when it still leaves this much. Not a
 * threshold to optimise against: it exists so "end at a sentence" cannot silently collapse a
 * description to its four-word opening clause. */
const MIN_USEFUL_DESCRIPTION_LENGTH = 80;

/** A sentence end: `.`, `!` or `?` followed by whitespace or the end of the text. `[^\s]` before it
 * so an ellipsis or a decimal point inside a version number cannot be mistaken for one. */
const SENTENCE_END_PATTERN = /[^\s][.!?](?=\s|$)/g;

/** Clause punctuation left dangling by a word-boundary cut — `token range, agent turns, tool` reads
 * as a truncated list; `token range, agent turns, tool,…` reads as a mistake. */
const TRAILING_CLAUSE_PUNCTUATION = /[\s,;:—–-]+$/;

/**
 * Shortens `raw` to at most `MAX_DESCRIPTION_LENGTH` characters WITHOUT ever cutting a word in half.
 *
 * The previous rule sliced at a fixed offset and appended an ellipsis, which is what published
 * `…and a verification starter). Sin…` and `…tool invoc…` as the site's own meta descriptions — a
 * search result whose last word is a fragment reads as broken, and there is no length at which a
 * blind slice is safe.
 *
 * In order:
 *  1. a description that already fits is used unchanged;
 *  2. otherwise the longest run of COMPLETE SENTENCES that fits, provided it is still a useful
 *     snippet length — this needs no ellipsis at all, because nothing was cut off mid-thought;
 *  3. otherwise the longest run of complete WORDS that fits, with dangling clause punctuation
 *     removed and an ellipsis appended to signal that more follows.
 *
 * Every branch returns text that ends on a real word boundary of `raw`, which is what
 * `describesCompleteWords` (below) checks mechanically rather than by eye.
 */
export function truncateDescription(raw: string): string {
  if (raw.length <= MAX_DESCRIPTION_LENGTH) {
    return raw;
  }

  let lastSentenceEnd = -1;
  for (const match of raw.matchAll(SENTENCE_END_PATTERN)) {
    const end = match.index + match[0].length;
    if (end > MAX_DESCRIPTION_LENGTH) {
      break;
    }
    lastSentenceEnd = end;
  }
  if (lastSentenceEnd >= MIN_USEFUL_DESCRIPTION_LENGTH) {
    return raw.slice(0, lastSentenceEnd);
  }

  // One character of the budget is reserved for the ellipsis itself.
  const window = raw.slice(0, MAX_DESCRIPTION_LENGTH - 1);
  const lastSpace = window.lastIndexOf(' ');
  const words = (lastSpace === -1 ? window : window.slice(0, lastSpace)).replace(TRAILING_CLAUSE_PUNCTUATION, '');
  return `${words}…`;
}

/**
 * Whether `description` really is `raw` cut at a word boundary — the mechanical form of "no word was
 * chopped in half", checked against the source text rather than by inspecting the result's last
 * characters.
 *
 * Exact, not heuristic: strip any trailing ellipsis and dangling clause punctuation the truncation
 * itself removed, and what remains must be a prefix of `raw` that either IS all of `raw` or stops
 * exactly where `raw` has whitespace (or clause punctuation followed by whitespace). A cut in the
 * middle of `invocations` fails that test; a cut after `invocations,` passes it.
 */
export function describesCompleteWords(raw: string, description: string): boolean {
  const trimmed = raw.trim();
  if (description === trimmed) {
    return true;
  }
  const body = description.replace(/…$/, '');
  if (body.length === 0 || !trimmed.startsWith(body)) {
    return false;
  }
  // The one case where no word-boundary cut exists to make: the source's first word is itself
  // longer than everything that fits. Cutting inside it is then unavoidable, not a defect — and
  // accepting it here cannot mask a real mid-word cut, because any source with whitespace before
  // the cut point still has to land on it.
  const firstBoundary = trimmed.search(/\s/);
  if (firstBoundary === -1 || firstBoundary >= body.length) {
    return true;
  }
  // What `raw` has immediately after the emitted text, skipping only the clause punctuation the
  // truncation is allowed to have dropped.
  const remainder = trimmed.slice(body.length).replace(/^[,;:—–-]+/, '');
  return remainder.length === 0 || /^\s/.test(remainder);
}

export interface CatalogueSeoWorkflow {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
}

export function catalogueWorkflowTitle(workflow: CatalogueSeoWorkflow): string {
  return `${workflow.name} — AgentForge4j Catalogue`;
}

export function catalogueWorkflowDescription(workflow: CatalogueSeoWorkflow): string {
  const raw = workflow.description?.trim();
  if (!raw) {
    return `${workflow.name} — a shipped, ready-to-run AgentForge4j workflow from the workflow catalogue.`;
  }
  return truncateDescription(raw);
}
