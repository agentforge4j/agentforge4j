// SPDX-License-Identifier: Apache-2.0

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowBuilder } from '../src/index';
import { sampleWorkflow } from './sample-workflow';

const capabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

createRoot(rootEl).render(
  <StrictMode>
    <WorkflowBuilder
      capabilities={capabilities}
      initialWorkflow={sampleWorkflow()}
      agentCatalog={[{ id: 'agent-demo', name: 'Demo Agent' }]}
    />
  </StrictMode>,
);
