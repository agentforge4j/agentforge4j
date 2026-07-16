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
      <p className="shrink-0 px-4 py-2 text-sm text-fg-muted">{BUILDER_COPY.accessibilityNote}</p>
      <div className="min-h-0 flex-1">
        <WorkflowBuilder capabilities={CAPABILITIES} />
      </div>
    </div>
  );
}
