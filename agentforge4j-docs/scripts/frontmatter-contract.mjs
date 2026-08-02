// SPDX-License-Identifier: Apache-2.0
//
// Single source of truth for the kind-conditional frontmatter contract.
// Consumed by scripts/lint-frontmatter.mjs (and reused by later CI gates). The
// contract keeps the Diátaxis spine unchanged while admitting the structural/meta
// pages (routers, gallery, release notes, contributing) that fit no Diátaxis quadrant.

/** Allowed `kind` values; `kind` defaults to `doc` when omitted. */
export const KINDS = ['doc', 'router', 'gallery', 'release-notes', 'contributing'];

/** Allowed `audience` values (the three landing paths). */
export const AUDIENCES = ['evaluating', 'integrating', 'authoring'];

/** Allowed `diataxis` values (the Diátaxis spine). */
export const DIATAXIS = ['tutorial', 'how-to', 'concept', 'reference'];

/**
 * Validate a single page's parsed frontmatter against the §9 contract.
 *
 * @param {Record<string, unknown>} fm parsed YAML frontmatter
 * @returns {string[]} a list of human-readable violations (empty == valid)
 */
export function validateFrontmatter(fm) {
  const errors = [];

  // Base requirements for every page.
  if (typeof fm.title !== 'string' || fm.title.trim() === '') {
    errors.push('missing or empty `title`');
  }
  if (typeof fm.description !== 'string' || fm.description.trim() === '') {
    errors.push('missing or empty `description`');
  }

  const kind = fm.kind === undefined ? 'doc' : fm.kind;
  if (!KINDS.includes(kind)) {
    errors.push(`invalid \`kind\`: ${JSON.stringify(fm.kind)} (allowed: ${KINDS.join(', ')})`);
    return errors; // cannot apply kind-conditional rules with an unknown kind
  }

  const hasAudience = fm.audience !== undefined;
  const hasDiataxis = fm.diataxis !== undefined;

  // Any present enum value must be in-enum, regardless of kind.
  if (hasAudience && !AUDIENCES.includes(fm.audience)) {
    errors.push(`invalid \`audience\`: ${JSON.stringify(fm.audience)} (allowed: ${AUDIENCES.join(', ')})`);
  }
  if (hasDiataxis && !DIATAXIS.includes(fm.diataxis)) {
    errors.push(`invalid \`diataxis\`: ${JSON.stringify(fm.diataxis)} (allowed: ${DIATAXIS.join(', ')})`);
  }

  switch (kind) {
    case 'doc':
      // Content pages classify on both axes.
      if (!hasAudience) {
        errors.push('`kind: doc` requires `audience`');
      }
      if (!hasDiataxis) {
        errors.push('`kind: doc` requires `diataxis`');
      }
      break;
    case 'router':
      // Audience-specific landing pages: audience required, no Diátaxis quadrant.
      if (!hasAudience) {
        errors.push('`kind: router` requires `audience`');
      }
      if (hasDiataxis) {
        errors.push('`kind: router` must not carry `diataxis`');
      }
      break;
    case 'gallery':
    case 'release-notes':
    case 'contributing':
      // Structural/meta pages: neither axis applies.
      if (hasAudience) {
        errors.push(`\`kind: ${kind}\` must not carry \`audience\``);
      }
      if (hasDiataxis) {
        errors.push(`\`kind: ${kind}\` must not carry \`diataxis\``);
      }
      break;
    default:
      break;
  }

  return errors;
}
