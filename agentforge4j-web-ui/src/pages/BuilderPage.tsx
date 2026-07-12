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
  return (
    <div className="h-full w-full">
      <p className="px-4 py-2 text-sm text-fg-muted">{BUILDER_COPY.accessibilityNote}</p>
      <WorkflowBuilder capabilities={CAPABILITIES} />
    </div>
  );
}
