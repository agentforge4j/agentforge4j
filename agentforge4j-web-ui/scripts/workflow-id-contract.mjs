// SPDX-License-Identifier: Apache-2.0
//
// The one workflow-id contract: lowercase ASCII letters, digits, and single hyphens; no
// leading/trailing hyphen, no duplicate hyphen. Chosen to match every currently shipped id
// ("agent-creator", "workflow-execution-estimator") unchanged, and to be safe, as-is, as a
// catalogue route segment, a filesystem directory segment, a canonical URL segment, and a sitemap
// URL segment — the same raw value in every one of those contexts.
//
// Shared by build-catalogue-data.mjs (the enforcement point: every id is validated against this
// before catalogue-data.json is written, so no downstream consumer ever sees a bad one in real
// production usage) and build-seo.mjs (a defense-in-depth re-check, since its own unit tests
// exercise it directly against fixture catalogue data that bypasses the real catalog build). One
// definition in one place, so the two can never independently drift apart.
export const WORKFLOW_ID_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
