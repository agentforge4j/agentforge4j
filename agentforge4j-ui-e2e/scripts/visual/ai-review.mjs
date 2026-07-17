// SPDX-License-Identifier: Apache-2.0
//
// Optional, provider-neutral AI visual review. Disabled by default — never runs
// unless AI_VISUAL_REVIEW_ENABLED=true AND an API key is present. Never invoked by CI (see
// `.github/workflows/visual-freshness.yml`, which reads only the committed attestation file and
// never calls this script) — this is a local-only, explicitly-invoked developer step.
//
// Speaks the OpenAI-compatible chat-completions vision message shape (`image_url` content parts),
// which is mirrored by enough providers/gateways (OpenAI itself, Azure OpenAI, most local
// OpenAI-compatible servers) to count as a reasonable provider-neutral default without inventing a
// bespoke protocol. A genuinely different native API (e.g. Anthropic's or Gemini's own SDK) needs
// only `callVisionModel()` replaced — every other part of this script (selection, budget,
// parsing, output shape) is provider-agnostic.
//
// Configuration (all via environment variables, all optional except the API key):
//   AI_VISUAL_REVIEW_ENABLED       'true' to opt in. Default: disabled.
//   AI_VISUAL_REVIEW_API_KEY       Required to actually call a model. No key => this script exits
//                                  0 having reviewed nothing, and says so — never a hard failure:
//                                  lack of local AI credentials must not block the rest of the
//                                  visual-review flow.
//   AI_VISUAL_REVIEW_BASE_URL      Default: https://api.openai.com/v1
//   AI_VISUAL_REVIEW_MODEL         Default: gpt-4o-mini — today's cheapest OpenAI vision-capable
//                                  model at the time this was written. Model names and pricing
//                                  change; treat this default as a starting point to revisit, not
//                                  a permanent fact, and override it via this variable rather than
//                                  editing the script.
//   AI_VISUAL_REVIEW_MAX_SCREENSHOTS  Default: 20. Hard cap on how many screenshots get reviewed
//                                  in one run, applied after sorting by release importance
//                                  (blocker > important > nice-to-have) so a low budget still
//                                  covers the highest-value screenshots first.
//   AI_VISUAL_REVIEW_MAX_TOKENS    Default: 700 (max_tokens per request — the response is a small
//                                  structured JSON object, not prose).

import { mkdirSync, readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = fileURLToPath(new URL('.', import.meta.url));
const OUTPUT_DIR = resolve(here, '..', '..', 'visual-output');
const RESULTS_DIR = join(OUTPUT_DIR, 'results');
const AI_RESULTS_PATH = join(OUTPUT_DIR, 'ai-review-results.json');

const IMPORTANCE_RANK = { blocker: 0, important: 1, 'nice-to-have': 2 };

function loadCaptureRecords() {
  if (!existsSync(RESULTS_DIR)) {
    return [];
  }
  return readdirSync(RESULTS_DIR)
    .filter((name) => name.endsWith('.json'))
    .map((name) => JSON.parse(readFileSync(join(RESULTS_DIR, name), 'utf8')));
}

/** Only what the model is allowed to judge: clipping, overlap, broken alignment,
 *  unreadable text, poor responsive adaptation, missing content, distorted images, incorrect
 *  spacing, visual inconsistency, controls hidden by overlays, blank/broken rendering — nothing
 *  about backend functionality, architecture, or feature completeness. */
const REVIEW_PROMPT = `You are reviewing a single screenshot of a web page for VISUAL PRESENTATION problems only.

Judge ONLY: clipping, element overlap, broken alignment, unreadable or too-small text, poor
responsive adaptation for the stated viewport, visibly missing content, distorted images, obviously
incorrect spacing, visual inconsistency, controls hidden behind overlays, and blank or broken
rendering.

Do NOT judge backend functionality, application architecture, or whether a feature is complete —
this page may legitimately be a work-in-progress feature; only its VISUAL rendering is in scope.

Respond with ONLY a single JSON object, no other text, matching exactly this shape:
{"status": "pass" | "warning" | "fail", "confidence": <0-1 number>, "issueCategory": "<short category or \\"none\\">", "evidence": "<one concise sentence>", "location": "<approximate screen region, e.g. \\"top-right\\", \\"none\\">", "humanConfirmationRequired": <boolean>}`;

async function callVisionModel({ baseUrl, apiKey, model, maxTokens, imageBase64, context }) {
  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      max_tokens: maxTokens,
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: `${REVIEW_PROMPT}\n\nPage state: "${context.stateName}" at viewport "${context.viewport}".` },
            { type: 'image_url', image_url: { url: `data:image/png;base64,${imageBase64}` } },
          ],
        },
      ],
    }),
  });
  if (!response.ok) {
    throw new Error(`AI vision call failed: HTTP ${response.status} ${await response.text()}`);
  }
  const payload = await response.json();
  return payload.choices?.[0]?.message?.content ?? '';
}

function parseModelResponse(raw) {
  try {
    // Models occasionally wrap JSON in a code fence despite instructions — strip one if present.
    const cleaned = raw.trim().replace(/^```(?:json)?\s*/i, '').replace(/```\s*$/i, '');
    const parsed = JSON.parse(cleaned);
    return {
      status: ['pass', 'warning', 'fail'].includes(parsed.status) ? parsed.status : 'warning',
      confidence: typeof parsed.confidence === 'number' ? parsed.confidence : 0,
      issueCategory: typeof parsed.issueCategory === 'string' ? parsed.issueCategory : 'unknown',
      evidence: typeof parsed.evidence === 'string' ? parsed.evidence : raw.slice(0, 200),
      location: typeof parsed.location === 'string' ? parsed.location : 'unknown',
      humanConfirmationRequired:
        typeof parsed.humanConfirmationRequired === 'boolean' ? parsed.humanConfirmationRequired : true,
      parseError: false,
    };
  } catch {
    // An unparseable response is a review-tooling problem, not evidence of a real visual defect —
    // surface it as a warning that always requires human confirmation, never silently dropped.
    return {
      status: 'warning',
      confidence: 0,
      issueCategory: 'ai-response-unparseable',
      evidence: raw.slice(0, 300),
      location: 'unknown',
      humanConfirmationRequired: true,
      parseError: true,
    };
  }
}

async function main() {
  // Every write path below targets AI_RESULTS_PATH inside OUTPUT_DIR — on a genuinely clean
  // checkout (no prior visual:capture/visual:report run) that directory doesn't exist yet, and
  // writeFileSync would throw ENOENT. Must run before the very first possible write, including the
  // disabled/no-credentials skip path — that path is explicitly documented as "never a hard
  // failure", so it must not crash on a clean checkout for exactly the reason it exists.
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const enabled = process.env.AI_VISUAL_REVIEW_ENABLED === 'true';
  const apiKey = process.env.AI_VISUAL_REVIEW_API_KEY;

  if (!enabled || !apiKey) {
    const reason = !enabled ? 'AI_VISUAL_REVIEW_ENABLED is not "true"' : 'AI_VISUAL_REVIEW_API_KEY is not set';
    console.log(`[ai-review] skipped: ${reason}. This is expected and non-fatal — AI review is opt-in.`);
    writeFileSync(AI_RESULTS_PATH, JSON.stringify({ enabled: false, reason, reviewed: [] }, null, 2));
    return;
  }

  const baseUrl = process.env.AI_VISUAL_REVIEW_BASE_URL ?? 'https://api.openai.com/v1';
  const model = process.env.AI_VISUAL_REVIEW_MODEL ?? 'gpt-4o-mini';
  // `||`, not `??`: an unset var is `undefined` either way, but a var that is SET to an empty
  // string (e.g. a CI variable left blank) must also fall back to the default — `??` only
  // substitutes for `null`/`undefined`, and `Number('')` is `0`, not `NaN`, so `??` alone would
  // silently turn a blank env var into a zero budget instead of the documented default.
  const maxScreenshots = Number(process.env.AI_VISUAL_REVIEW_MAX_SCREENSHOTS || 20);
  const maxTokens = Number(process.env.AI_VISUAL_REVIEW_MAX_TOKENS || 700);

  const records = loadCaptureRecords()
    .filter((record) => record.aiReviewEnabled)
    .sort((a, b) => IMPORTANCE_RANK[a.releaseImportance] - IMPORTANCE_RANK[b.releaseImportance]);

  if (records.length === 0) {
    console.log('[ai-review] no AI-review-enabled captures found in visual-output/results — run visual:capture first.');
    writeFileSync(AI_RESULTS_PATH, JSON.stringify({ enabled: true, model, reviewed: [] }, null, 2));
    return;
  }

  const selected = records.slice(0, maxScreenshots);
  const skipped = records.length - selected.length;
  if (skipped > 0) {
    console.log(`[ai-review] budget cap: reviewing ${selected.length}/${records.length} screenshots (lowest release-importance ${skipped} skipped).`);
  }

  // Each screenshot's vision-model call is an independent network round trip with no shared
  // state — awaiting them one at a time serializes the whole budget's wall-clock time for no
  // reason. `REVIEW_CONCURRENCY` independent workers pull from a shared cursor instead.
  async function reviewOne(record) {
    const screenshotPath = join(OUTPUT_DIR, ...record.screenshotPath.split('/'));
    if (!existsSync(screenshotPath)) {
      return {
        entryId: record.entryId,
        viewport: record.viewport,
        status: 'warning',
        confidence: 0,
        issueCategory: 'screenshot-missing',
        evidence: `expected screenshot not found at ${record.screenshotPath}`,
        location: 'unknown',
        humanConfirmationRequired: true,
      };
    }
    const imageBase64 = readFileSync(screenshotPath).toString('base64');
    try {
      const raw = await callVisionModel({ baseUrl, apiKey, model, maxTokens, imageBase64, context: record });
      const parsed = parseModelResponse(raw);
      console.log(`[ai-review] ${record.entryId} @ ${record.viewport}: ${parsed.status} (${parsed.issueCategory})`);
      return { entryId: record.entryId, viewport: record.viewport, ...parsed };
    } catch (error) {
      console.error(`[ai-review] ${record.entryId} @ ${record.viewport}: call failed — ${error.message}`);
      return {
        entryId: record.entryId,
        viewport: record.viewport,
        status: 'warning',
        confidence: 0,
        issueCategory: 'ai-call-failed',
        evidence: error.message,
        location: 'unknown',
        humanConfirmationRequired: true,
      };
    }
  }

  const REVIEW_CONCURRENCY = 4;
  const results = new Array(selected.length);
  let nextIndex = 0;
  async function worker() {
    while (nextIndex < selected.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await reviewOne(selected[index]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(REVIEW_CONCURRENCY, selected.length) }, worker));
  const reviewed = results;

  writeFileSync(
    AI_RESULTS_PATH,
    JSON.stringify({ enabled: true, baseUrl, model, reviewedCount: reviewed.length, skippedForBudget: skipped, reviewed }, null, 2),
  );
  console.log(`[ai-review] wrote ${AI_RESULTS_PATH}`);
}

main().catch((error) => {
  console.error('[ai-review] fatal error:', error);
  process.exit(1);
});
