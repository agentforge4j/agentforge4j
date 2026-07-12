// SPDX-License-Identifier: Apache-2.0
//
// Attribution-term separation vocabulary (design §4, group 2). Committed/published
// AgentForge4j material must never credit an assistant, model, or coding tool with having
// done, designed, written, reviewed, or implemented the work — this is a house rule
// covering commit messages, PR text, code comments, docs, and any other committed content.
//
// This is deliberately NOT a token blocklist like product-name.mjs's BLOCKED list: a tool
// name alone (e.g. "Claude", "ChatGPT") is legitimate when it names an actual supported LLM
// provider (the llm-claude/llm-openai modules, a provider matrix). What's prohibited is
// phrasing that puts one of those names in the AUTHOR role for the work itself. Every
// pattern below therefore requires proximity between a tool/model/assistant name and an
// authorship verb (wrote/implemented/reviewed/generated/authored/designed/created/built),
// or a well-known attribution-trailer shape (`Co-Authored-By: <tool>`, a claude.ai/code
// session link) — never the bare name on its own.
//
// Shared by the lint driver (lint-attribution-terms.mjs) and its test
// (attribution-terms.test.mjs), and consumed cross-module by agentforge4j-web-ui's own gate
// driver via a relative import — no npm workspace exists in this repo to publish this as a
// shared package, so a relative import between top-level repo siblings is the smallest
// mechanism that avoids a second, drifting copy of this list.

const TOOL_NAMES = 'claude(?:\\s*code)?|chatgpt|gpt-\\d\\w*|copilot|gemini|codex|cursor|anthropic|openai';
const AUTHORSHIP_VERBS = 'wrote|writes|written|implement(?:ed|s)?|generat(?:ed|es)|authored|design(?:ed)?|creat(?:ed|es)|built|review(?:ed|s)?|co-authored';

export const ATTRIBUTION_BLOCKED = [
  {
    id: 'co-authored-by-tool',
    pattern: new RegExp(`co-authored-by:\\s*(?:${TOOL_NAMES})`, 'i'),
    description: 'commit-trailer-style tool authorship credit',
  },
  {
    id: 'claude-session-link',
    pattern: /claude\.ai\/code/i,
    description: 'Claude Code session link',
  },
  {
    id: 'verb-then-tool',
    pattern: new RegExp(`\\b(?:${AUTHORSHIP_VERBS})\\s+by\\s+(?:${TOOL_NAMES}|an?\\s+(?:ai|llm|assistant|language model))\\b`, 'i'),
    description: 'authorship credited to an assistant/model/tool',
  },
  {
    id: 'tool-then-verb',
    pattern: new RegExp(`\\b(?:${TOOL_NAMES})\\s+(?:${AUTHORSHIP_VERBS})\\b`, 'i'),
    description: 'assistant/tool named as the one who did the work',
  },
  {
    id: 'ai-generated-claim',
    pattern: /\b(?:ai|llm)[-\s](?:generated|written|authored)\b/i,
    description: 'generic AI/LLM-authorship claim',
  },
];

// Reviewed exemptions: exact source substrings that legitimately contain a blocked pattern's
// surface text without being an attribution claim. Empty today; an entry here documents why
// a specific occurrence is allowed (mirrors product-name.mjs's ALLOWLIST convention).
export const ALLOWLIST = [];

/**
 * Scan text for blocked attribution phrasing, ignoring allowlisted occurrences.
 *
 * @param {string} text the document source
 * @returns {{id: string, line: number, excerpt: string, description: string}[]} findings (empty when clean)
 */
export function findAttributionLeaks(text) {
  const findings = [];
  const lines = text.split(/\r?\n/);
  lines.forEach((line, i) => {
    if (ALLOWLIST.some((allowed) => line.includes(allowed))) {
      return;
    }
    for (const {id, pattern, description} of ATTRIBUTION_BLOCKED) {
      if (pattern.test(line)) {
        findings.push({id, line: i + 1, excerpt: line.trim(), description});
      }
    }
  });
  return findings;
}
