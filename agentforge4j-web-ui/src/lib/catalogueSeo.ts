// SPDX-License-Identifier: Apache-2.0
//
// Per-workflow title/description formatting for /catalogue/:id, shared by the client-side
// title/meta sync (usePageSeo) and the build-time static-shell generator
// (scripts/build-seo.mjs). The build script is plain Node ESM and cannot import this typed
// module directly (no bundler step ahead of it, unlike Vite for the client bundle), so it
// re-implements the same rules against the same generated catalogue-data.json.
//
// The duplication is NOT kept in sync by eye — the truncation rule below is too big for that to
// be honest. tests/usePageSeo.test.tsx is what binds the two copies: it drives BOTH
// implementations of every duplicated unit (MAX_DESCRIPTION_LENGTH, truncateDescription,
// describesCompleteWords, catalogueWorkflowDescription) over the real shipped catalogue data AND
// over a shared corpus of hard cases, and requires identical results. A fix applied here and
// forgotten there fails that test instead of shipping two different meta descriptions for the
// same page.

/** The published meta-description budget. Exported so tests can bind it to build-seo.mjs's copy
 * rather than re-typing the number — the same reason JSON_LD_SCRIPT_ID is exported there. */
export const MAX_DESCRIPTION_LENGTH = 157;

/** Below this, a truncated description has stopped being a useful search snippet — so a sentence
 * boundary is only preferred over a longer word-boundary cut when it still leaves this much. Not a
 * threshold to optimise against: it exists so "end at a sentence" cannot silently collapse a
 * description to its four-word opening clause.
 *
 * Exported for the same reason as MAX_DESCRIPTION_LENGTH, plus one of its own: a test whose fixture
 * places a sentence end BELOW this floor proves nothing about the sentence rule, since the floor
 * rejects that cut whatever the rule decides. Fixtures bind to this number so a reworded fixture
 * fails loudly instead of quietly ceasing to test anything. */
export const MIN_USEFUL_DESCRIPTION_LENGTH = 80;

/** A CANDIDATE sentence end: `.`, `!` or `?` closing a run of non-space characters and followed by
 * whitespace or the end of the text. The captured group is the word it closes, which
 * `endsSentence` below needs to tell a real sentence end from an abbreviation. A decimal point
 * inside a version number (`1.0 release`) never matches at all — it is followed by a digit. */
const SENTENCE_END_PATTERN = /(\S+)[.!?](?=\s|$)/g;

/** Clause punctuation left dangling by a word-boundary cut — `token range, agent turns, tool` reads
 * as a truncated list; `token range, agent turns, tool,…` reads as a mistake. A run of two or more
 * dots goes with it: the source's own `stops mid thought...` followed by this rule's ellipsis would
 * otherwise publish `thought...…`. A single trailing dot is kept — it belongs to an abbreviation
 * (`e.g.`), and removing it would mangle the word rather than tidy the cut. */
const TRAILING_CLAUSE_PUNCTUATION = /(\.{2,}|[\s,;:—–-])+$/;

/** The mirror of the above, for `describesCompleteWords`: exactly what the truncation is allowed to
 * have dropped between the emitted text and the source's next word boundary. */
const DROPPED_CLAUSE_PUNCTUATION = /^(\.{2,}|[,;:—–-])+/;

/** The last whitespace in a window, wherever the final word starts — a newline, tab or
 * non-breaking space is as much a word boundary as a plain space, and `describesCompleteWords`
 * accepts all of them, so the cut has to look for all of them too. `-1` when the window holds no
 * whitespace at all (a single token longer than the whole budget). */
const LAST_WORD_BOUNDARY_PATTERN = /\s\S*$/;

/**
 * Whether a candidate match really ends a sentence, rather than an abbreviation that happens to
 * end in a full stop. Without this, `…, e.g.` and `…stops mid thought...` are taken for complete
 * sentences and published as the whole description, discarding most of the budget and reading as
 * broken — the same class of defect as a mid-word cut, arrived at from the other side.
 *
 * Two exact tests, no word list:
 *  - the word the terminator closes must not itself contain a full stop (`e.g`, `i.e`, `U.S`,
 *    and the `thought..` left by a trailing `...` all do; a real sentence's last word does not);
 *  - what follows must be the end of the text, or whitespace and then an upper-case letter —
 *    a new sentence. `etc. and`, `vs. the` and `No. 5` continue the one already running.
 *
 * Deliberately conservative: a real sentence followed by a quote, bracket or digit is rejected
 * too. The cost of a false negative is a few characters and an ellipsis; the cost of a false
 * positive is a published description that stops mid-thought.
 */
function endsSentence(raw: string, word: string, end: number): boolean {
  if (word.includes('.')) {
    return false;
  }
  const rest = raw.slice(end);
  return rest.trim().length === 0 || /^\s+\p{Lu}/u.test(rest);
}

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
 *     snippet length — this needs no ellipsis at all, because nothing was cut off mid-thought.
 *     "Sentence" is `endsSentence`'s exact test, not "anything before a full stop", so an
 *     abbreviation cannot pass for one;
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
    if (endsSentence(raw, match[1], end)) {
      lastSentenceEnd = end;
    }
  }
  if (lastSentenceEnd >= MIN_USEFUL_DESCRIPTION_LENGTH) {
    return raw.slice(0, lastSentenceEnd);
  }

  // One character of the budget is reserved for the ellipsis itself.
  const window = raw.slice(0, MAX_DESCRIPTION_LENGTH - 1);
  const boundary = window.search(LAST_WORD_BOUNDARY_PATTERN);
  const words = (boundary === -1 ? window : window.slice(0, boundary)).replace(TRAILING_CLAUSE_PUNCTUATION, '');
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
 *
 * The enforcement point is build-time (scripts/build-seo.mjs refuses to publish a description that
 * fails this), so this copy has no production caller of its own — it exists so the rule the build
 * gate applies is stated once per implementation and cannot drift. tests/usePageSeo.test.tsx
 * executes THIS copy and requires it to agree with build-seo.mjs's on every case; deleting it
 * without deleting that binding fails the suite rather than quietly leaving the gate unmirrored.
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
  const remainder = trimmed.slice(body.length).replace(DROPPED_CLAUSE_PUNCTUATION, '');
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
