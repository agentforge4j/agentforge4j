// SPDX-License-Identifier: Apache-2.0
//
// Product-name separation vocabulary. The OSS documentation must never leak the
// commercial Platform/Cloud product identity: proprietary artifact names, the hosted product names, the
// commercial vendors, or billing/tenancy concepts the OSS framework deliberately does not carry.
//
// The block list targets PRECISE identifiers grounded against the live Platform/Cloud reactor
// (agentforge4j-platform pom.xml) — never the generic English words "platform"/"cloud", which the OSS
// docs use legitimately. A small allowlist exempts reviewed edges. Shared by the lint and its test.
//
// Deliberate, documented exception to the no-commercial-identifiers rule: this file exists to
// enforce that rule, so the identifiers must appear here and nowhere else. The exact strings below
// are the boundary-enforcement vocabulary itself, not a leak.

// Precise blocked identifiers (each entry is matched case-insensitively as a whole token/phrase).
export const BLOCKED = [
  // Proprietary repos / artifact prefixes (catch every platform-*/cloud-* module in one shot).
  'agentforge4j-platform',
  'agentforge4j-cloud',
  'agentforge4j-billing',
  'agentforge4j-metering',
  'agentforge4j-entitlement',
  'agentforge4j-persistence-jpa',
  'agentforge4j-admin-api',
  // Hosted product names.
  'AgentForge4j Platform',
  'AgentForge4j Cloud',
  // Commercial vendors.
  'Stripe',
  'Clerk',
  // Licence of the proprietary layers (OSS is Apache-2.0).
  'Business Source License',
  'BSL 1.1',
  // Unambiguous monetisation term (the demo domain has no payments; a real occurrence is a leak).
  'paywall',
];

// General structural guard, not a maintained module roster: the private Platform/Cloud reactor
// names its modules `<layer>-<capability>` (platform-engine, cloud-enforcement, billing-stripe,
// entitlement-default, ...). Matching that naming convention catches a bare short-form module name
// without the OSS repo carrying a literal, update-on-every-rename inventory of the private reactor's
// current module list as shipped source. A handful of legitimate generic-English compounds share a
// layer word (platform-agnostic, cloud-native); those are exempted individually below rather than
// narrowing the pattern, so a genuinely new compound just needs one reviewed addition, not a redesign.
const STRUCTURAL_LAYER_PREFIXES = ['platform', 'cloud', 'billing', 'entitlement'];
const STRUCTURAL_GENERIC_COMPOUNDS = new Set([
  'platform-agnostic',
  'platform-independent',
  'platform-specific',
  'cloud-native',
  'cloud-hosted',
  'cloud-agnostic',
  'cloud-provider',
  'cloud-providers',
]);

function structuralLayerPattern() {
  const alternation = STRUCTURAL_LAYER_PREFIXES.join('|');
  return new RegExp(`(?<![\\w-])(${alternation})-[a-z][a-z-]*(?![\\w])`, 'gi');
}

// NOTE: the block list and structural guard both stay to PRECISE product/artifact/vendor
// identifiers or the private layer's own naming convention. Bare concept words — "billing",
// "subscription", "metering", "tenant"/"tenancy", "multi-tenant" — are deliberately NOT blocked: they
// occur legitimately in third-party technical text surfaced by the generated reference (e.g. an LLM
// provider's "multi-tenant host"), so blocking them produces false positives. A genuine commercial
// leak virtually always carries one of the precise identifiers above, or the layer-prefixed
// module-naming shape, alongside it.

// Reviewed exemptions: exact source substrings that legitimately contain a blocked token. Empty today
// (the docs are clean); an entry here documents why a specific occurrence is allowed.
export const ALLOWLIST = [];

/** Build a case-insensitive matcher for a blocked token, bounded so it does not match inside words. */
function tokenPattern(token) {
  const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // Leading: not preceded by a word char or hyphen (so it isn't a sub-token of a larger identifier).
  // Trailing: not followed by a word char — but a following hyphen IS allowed, so an artifact prefix
  // like `agentforge4j-platform` still matches `agentforge4j-platform-engine`, while `Clerk` does not
  // match inside `clerkship`.
  return new RegExp(`(?<![\\w-])${escaped}(?![\\w])`, 'gi');
}

const MATCHERS = BLOCKED.map((token) => ({token, re: tokenPattern(token)}));
const STRUCTURAL_RE = structuralLayerPattern();

/**
 * Scan text for blocked product-name identifiers, ignoring allowlisted occurrences.
 *
 * @param {string} text the document source
 * @returns {{token: string, line: number, excerpt: string}[]} findings (empty when clean)
 */
export function findProductNameLeaks(text) {
  const findings = [];
  const lines = text.split(/\r?\n/);
  lines.forEach((line, i) => {
    if (ALLOWLIST.some((allowed) => line.includes(allowed))) {
      return;
    }
    for (const {token, re} of MATCHERS) {
      re.lastIndex = 0;
      if (re.test(line)) {
        findings.push({token, line: i + 1, excerpt: line.trim()});
      }
    }
    STRUCTURAL_RE.lastIndex = 0;
    let match;
    while ((match = STRUCTURAL_RE.exec(line)) !== null) {
      const candidate = match[0].toLowerCase();
      if (!STRUCTURAL_GENERIC_COMPOUNDS.has(candidate)) {
        findings.push({token: match[0], line: i + 1, excerpt: line.trim()});
      }
    }
  });
  return findings;
}
