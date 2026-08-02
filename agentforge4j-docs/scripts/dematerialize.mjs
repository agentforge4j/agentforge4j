// SPDX-License-Identifier: Apache-2.0
//
// Release-staging de-materialiser.
//
// A versioned docs snapshot must contain *materialised* content — plain Markdown/MDX with no live
// `file=`/`vocab:`/`javadoc:` directives — because `docusaurus docs:version` copies the current
// `docs/` verbatim and Docusaurus re-runs the remark plugins on every version. Left live, an
// archived snapshot would re-read the *current* example source, validate against the *current*
// vocabulary sets, and link every Javadoc reference at the moving `/javadoc/next/` — the opposite of
// immutable history. This module resolves those three roles into static content, pinned to the
// snapshot's own version, before the snapshot is cut.
//
// It works on the raw MDX text via an AST-with-positions + offset-splice: the document is parsed to
// mdast (remark-parse + remark-mdx + remark-frontmatter) purely to locate the directive nodes and
// their source offsets; every replacement is spliced back into the original text. So all non-directive
// MDX (JSX, imports/exports, frontmatter, prose) is preserved byte-for-byte, and `vocab:`/`javadoc:`
// text that appears *inside* a fenced code block (documentation about the roles) is correctly left
// alone — it is a `code` node's literal value, not an `inlineCode` node.
//
// The resolution itself reuses the roles' own logic (resolveInclude, loadSets, resolveJavadocUrl), so
// the staged snapshot can never disagree with what a live build would have rendered for that version.

import {unified} from 'unified';
import remarkParse from 'remark-parse';
import remarkMdx from 'remark-mdx';
import remarkFrontmatter from 'remark-frontmatter';
import {visit} from 'unist-util-visit';

import {
  resolveInclude,
  includeAllowBases,
  parseMeta,
  DEFAULT_REPO_ROOT,
} from '../src/remark/include.mjs';
import {
  loadSets,
  DEFAULT_VOCAB_DIR,
  PATTERN as VOCAB_PATTERN,
} from '../src/remark/vocab.mjs';
import {
  resolveJavadocUrl,
  PATTERN as JAVADOC_PATTERN,
} from '../src/remark/javadoc.mjs';

const parser = unified()
  .use(remarkParse)
  .use(remarkMdx)
  .use(remarkFrontmatter, ['yaml']);

/** The longest fence (>= 3 backticks) that safely encloses a body containing backtick runs. */
function fenceFor(value) {
  const runs = value.match(/`+/g) || [];
  const longest = runs.reduce((max, run) => Math.max(max, run.length), 0);
  return '`'.repeat(Math.max(3, longest + 1));
}

/** Serialise a resolved include into a plain fenced code block (language + kept meta + body). */
function renderFence(lang, meta, value) {
  const fence = fenceFor(value);
  const info = [lang, meta].filter(Boolean).join(' ');
  return `${fence}${info}\n${value}\n${fence}`;
}

/** Serialise a resolved Javadoc reference into a static Markdown link with an inline-code label. */
function renderJavadocLink(fqcn, resolved) {
  return `[\`${resolved.simpleName}\`](${resolved.url} "${fqcn}")`;
}

// The current-version Javadoc route, hardcoded in authored/generated pages (surface links and labels
// like `/javadoc/next/`). These are not `javadoc:` directives, so they must be pinned to the snapshot
// version too — otherwise a frozen page would link the moving `next` surface. Pinned everywhere EXCEPT
// inside fenced code blocks (which are literal `code` nodes, protected via their AST spans).
const JAVADOC_ROUTE = /\/javadoc\/next\//g;

/**
 * De-materialise a single MDX document: resolve every `file=`/`vocab:`/`javadoc:` directive into
 * static content pinned to `version`, returning the rewritten source.
 *
 * @param {string} source the raw .mdx text
 * @param {{version: string, repoRoot?: string, vocabDir?: string, allowBases?: string[],
 *          vocabSets?: object, docLabel?: string}} options resolution context; `version` is required
 *          (the snapshot's version — Javadoc links are pinned to it)
 * @returns {string} the de-materialised MDX
 */
export function dematerialize(source, options) {
  if (!options || typeof options.version !== 'string' || options.version === '') {
    throw new Error('dematerialize: a target `version` is required');
  }
  const {version} = options;
  const repoRoot = options.repoRoot || DEFAULT_REPO_ROOT;
  const allowBases = options.allowBases || includeAllowBases(repoRoot);
  const vocabSets = options.vocabSets || loadSets(options.vocabDir || DEFAULT_VOCAB_DIR);
  const docLabel = options.docLabel || 'unknown';

  const tree = parser.parse(source);
  const edits = [];
  const codeSpans = []; // fenced/indented code-node spans; route-pinning skips these.

  visit(tree, (node) => {
    if (!node.position || node.position.start.offset === undefined) {
      return;
    }
    const {start, end} = node.position;

    if (node.type === 'code') {
      codeSpans.push([start.offset, end.offset]);
      const resolved = resolveInclude(node.meta, {repoRoot, allowBases, docLabel});
      if (resolved !== null) {
        edits.push({
          start: start.offset,
          end: end.offset,
          text: renderFence(node.lang, resolved.meta, resolved.value),
        });
      }
      return;
    }

    if (node.type === 'inlineCode') {
      const vocabMatch = VOCAB_PATTERN.exec(node.value);
      if (vocabMatch !== null) {
        const [, setName, value] = vocabMatch;
        const set = vocabSets[setName];
        if (set === undefined) {
          throw new Error(
            `dematerialize (${docLabel}): unknown vocabulary set '${setName}' in \`${node.value}\``,
          );
        }
        if (!set.has(value)) {
          throw new Error(
            `dematerialize (${docLabel}): '${value}' is not a member of the '${setName}' vocabulary ` +
              `(referenced as \`${node.value}\`)`,
          );
        }
        edits.push({start: start.offset, end: end.offset, text: `\`${value}\``});
        return;
      }

      const javadocMatch = JAVADOC_PATTERN.exec(node.value);
      if (javadocMatch !== null) {
        const fqcn = javadocMatch[1];
        let resolved;
        try {
          resolved = resolveJavadocUrl(fqcn, version);
        } catch (err) {
          throw new Error(`dematerialize (${docLabel}): ${err.message}`);
        }
        edits.push({
          start: start.offset,
          end: end.offset,
          text: renderJavadocLink(fqcn, resolved),
        });
      }
    }
  });

  // Pin hardcoded `/javadoc/next/` routes (surface links/labels) to the snapshot version, skipping any
  // occurrence inside a fenced code block (protected via the collected code spans).
  const inCodeSpan = (offset) => codeSpans.some(([s, e]) => offset >= s && offset < e);
  for (const match of source.matchAll(JAVADOC_ROUTE)) {
    if (!inCodeSpan(match.index)) {
      edits.push({
        start: match.index,
        end: match.index + match[0].length,
        text: `/javadoc/${version}/`,
      });
    }
  }

  // Splice replacements back into the original text in reverse offset order so earlier offsets stay
  // valid. Non-directive text is preserved byte-for-byte.
  edits.sort((a, b) => b.start - a.start);
  let out = source;
  for (const edit of edits) {
    out = out.slice(0, edit.start) + edit.text + out.slice(edit.end);
  }
  return out;
}

/**
 * Find every LIVE directive in an MDX document: `file=` includes (a code node's info string),
 * `vocab:`/`javadoc:` inline-code tags, and unpinned `/javadoc/next/` routes outside fenced code.
 *
 * This is the assertion-side twin of {@link dematerialize}: it walks the same AST, so text that
 * merely *documents* a directive inside a fenced code block (a `code` node's literal value — e.g. the
 * contributor guide's authoring examples) is correctly NOT reported, exactly as `dematerialize`
 * correctly does not rewrite it. The scratch-cut uses this to prove a snapshot is fully materialised
 * without false-failing on documentation about the directives themselves.
 *
 * @param {string} source the raw .mdx text
 * @returns {{type: 'include'|'vocab'|'javadoc'|'javadoc-route', line: number, excerpt: string}[]}
 *          live directives found (empty when the document is fully materialised)
 */
export function findLiveDirectives(source) {
  const tree = parser.parse(source);
  const findings = [];
  const codeSpans = [];

  visit(tree, (node) => {
    if (!node.position || node.position.start.offset === undefined) {
      return;
    }
    if (node.type === 'code') {
      codeSpans.push([node.position.start.offset, node.position.end.offset]);
      if (parseMeta(node.meta).file) {
        findings.push({
          type: 'include',
          line: node.position.start.line,
          excerpt: String(node.meta).trim(),
        });
      }
      return;
    }
    if (node.type === 'inlineCode') {
      if (VOCAB_PATTERN.test(node.value)) {
        findings.push({type: 'vocab', line: node.position.start.line, excerpt: node.value});
      } else if (JAVADOC_PATTERN.test(node.value)) {
        findings.push({type: 'javadoc', line: node.position.start.line, excerpt: node.value});
      }
    }
  });

  // Unpinned current-version Javadoc routes, outside fenced code — mirrors dematerialize's pinning.
  const inCodeSpan = (offset) => codeSpans.some(([s, e]) => offset >= s && offset < e);
  for (const match of source.matchAll(JAVADOC_ROUTE)) {
    if (!inCodeSpan(match.index)) {
      const line = source.slice(0, match.index).split('\n').length;
      findings.push({type: 'javadoc-route', line, excerpt: match[0]});
    }
  }

  return findings.sort((a, b) => a.line - b.line);
}
