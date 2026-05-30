// SPDX-License-Identifier: Apache-2.0
import '@agentforge4j/workflow-builder-react/styles/tokens.css';
import { WorkflowBuilder } from '@agentforge4j/workflow-builder-react';
import type { BuilderCapabilities } from '@agentforge4j/workflow-builder-react';

const CAPABILITIES: BuilderCapabilities = {
  import:   true,
  export:   true,
  save:     false,
  run:      false,
  publish:  false,
  aiAssist: false,
};

export default function BuilderPage() {
  return (
    <div className="h-full w-full">
      <WorkflowBuilder capabilities={CAPABILITIES} />
    </div>
  );
}
