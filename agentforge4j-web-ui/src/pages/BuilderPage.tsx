// SPDX-License-Identifier: Apache-2.0
import '@agentforge4j/workflow-builder-react/styles/tokens.css';
import { WorkflowBuilder } from '@agentforge4j/workflow-builder-react';
import type { BuilderCapabilities } from '@agentforge4j/workflow-builder-react';
import { BUILDER_COPY } from '@/copy/builder';

const CAPABILITIES: BuilderCapabilities = {
  import: true,
  export: true,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

export default function BuilderPage() {
  // Flex column so the builder receives all remaining height as a definite size (its own root
  // is `height: 100%`) — the app shell guarantees <main> a definite viewport-bound height on
  // this route. min-h-0 on both levels keeps the flex chain from re-expanding to content size.
  return (
    <div className="flex h-full min-h-0 w-full flex-col">
      {/* Visually hidden, not omitted: the canvas has no other document heading at all until a
          step is selected (the inspector panel provides its own), so a screen reader / other
          assistive tech landing on this route with nothing selected had no page-level heading to
          announce. sr-only provides that structure without any visible/layout change — same
          "keep the DOM contract, hide the pixels" technique already used elsewhere in this UI
          (see visual/checks.ts's clipped-panel check, which explicitly excludes exactly this
          pattern from being flagged as a clipping defect). */}
      <h1 className="sr-only">{BUILDER_COPY.heading}</h1>
      <p className="shrink-0 px-4 py-2 text-sm text-fg-muted">{BUILDER_COPY.accessibilityNote}</p>
      {/* overflow-y-auto: on viewports shorter than the builder's own CSS min-height (24rem)
          the builder cannot fit the remaining space — this pane then scrolls internally
          instead of the builder spilling past the viewport-pinned shell and clipping its
          bottom controls below the fold. No effect when the builder fits. */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        <WorkflowBuilder capabilities={CAPABILITIES} />
      </div>
    </div>
  );
}
