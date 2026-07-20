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
  if (raw.length <= MAX_DESCRIPTION_LENGTH) {
    return raw;
  }
  return `${raw.slice(0, MAX_DESCRIPTION_LENGTH - 1).trimEnd()}…`;
}
