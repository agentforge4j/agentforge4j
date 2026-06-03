// SPDX-License-Identifier: Apache-2.0

import { StrictMode, useCallback, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowBuilder } from '../src/index';
import { WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import { createGalleryModel } from './sample-gallery-model';
import { sampleWorkflow } from './sample-workflow';

type DevMode = 'demo' | 'gallery';

const capabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

function readInitialMode(): DevMode {
  return new URLSearchParams(window.location.search).has('gallery') ? 'gallery' : 'demo';
}

function DevHarness() {
  const [mode, setMode] = useState<DevMode>(readInitialMode);
  const galleryModel = useMemo(() => createGalleryModel(), []);

  const switchMode = useCallback((next: DevMode) => {
    setMode(next);
    const url = new URL(window.location.href);
    if (next === 'gallery') {
      url.searchParams.set('gallery', '1');
    } else {
      url.searchParams.delete('gallery');
    }
    window.history.replaceState(null, '', url);
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', width: '100%', height: '100%' }}>
      <div
        style={{
          display: 'flex',
          gap: '0.5rem',
          padding: '0.5rem 0.75rem',
          borderBottom: '1px solid var(--afb-chrome-border)',
          background: 'var(--afb-chrome-bg)',
          color: 'var(--afb-chrome-text)',
          fontFamily: 'var(--afb-font-sans)',
          fontSize: '0.8125rem',
        }}
      >
        <span style={{ marginRight: 'auto', fontWeight: 600 }}>Workflow Builder — Dev</span>
        <button type="button" onClick={() => switchMode('demo')} aria-pressed={mode === 'demo'}>
          Builder demo
        </button>
        <button type="button" onClick={() => switchMode('gallery')} aria-pressed={mode === 'gallery'}>
          Node gallery
        </button>
      </div>
      <div style={{ flex: 1, minHeight: 0 }}>
        {mode === 'demo' ? (
          <WorkflowBuilder
            capabilities={capabilities}
            initialWorkflow={sampleWorkflow()}
            agentCatalog={[{ id: 'agent-demo', name: 'Demo Agent' }]}
          />
        ) : (
          <WorkflowCanvas
            model={galleryModel}
            onModelChange={() => {}}
            onSelectNode={() => {}}
            selectedId={null}
            onAppend={() => {}}
          />
        )}
      </div>
    </div>
  );
}

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('Root element #root not found');
}

createRoot(rootEl).render(
  <StrictMode>
    <DevHarness />
  </StrictMode>,
);
