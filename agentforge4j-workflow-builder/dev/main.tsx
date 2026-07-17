// SPDX-License-Identifier: Apache-2.0

import { StrictMode, useCallback, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowBuilder } from '../src/index';
import { WorkflowCanvas } from '../src/canvas/WorkflowCanvas';
import type {
  BuilderAdapters,
  BuilderCapabilities,
  WorkflowBuilderMode,
  WorkflowDefinition,
} from '../src/api/types';
import { createGalleryModel } from './sample-gallery-model';
import { readFixture } from './fixtures';

type DevMode = 'demo' | 'gallery';

/**
 * Dev-harness-only observation seam for the Playwright e2e suite. The shipped
 * `WorkflowBuilder` is uncontrolled (no `onChange`); the only host-observable
 * serialization path is `adapters.exportBundle(draft)`. The harness wires that
 * adapter to record the serialized {@link WorkflowDefinition} on `window` so a
 * spec can read the real draft after an interaction. This exists ONLY in the
 * dev harness (excluded from the published package).
 */
type ExportCaptureWindow = Window & {
  __afbExport?: { count: number; draft: WorkflowDefinition };
};

const NO_CAPABILITIES: BuilderCapabilities = {
  import: false,
  export: false,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

const CAPABILITY_KEYS: (keyof BuilderCapabilities)[] = [
  'import',
  'export',
  'save',
  'run',
  'publish',
  'aiAssist',
];

function readInitialDevMode(params: URLSearchParams): DevMode {
  return params.has('gallery') ? 'gallery' : 'demo';
}

/**
 * Build capabilities from a `?caps=save,export` query param. Absent/empty →
 * all-false (the default the existing specs depend on). Unknown tokens ignored.
 */
function readCapabilities(params: URLSearchParams): BuilderCapabilities {
  const enabled = new Set(
    (params.get('caps') ?? '')
      .split(',')
      .map((token) => token.trim())
      .filter(Boolean),
  );
  const capabilities: BuilderCapabilities = { ...NO_CAPABILITIES };
  for (const key of CAPABILITY_KEYS) {
    capabilities[key] = enabled.has(key);
  }
  return capabilities;
}

/** Editing posture from `?mode=readOnly`; anything else (incl. absent) → editable. */
function readBuilderMode(params: URLSearchParams): WorkflowBuilderMode {
  return params.get('mode') === 'readOnly' ? 'readOnly' : 'editable';
}

function DevHarness() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const [mode, setMode] = useState<DevMode>(() => readInitialDevMode(params));
  const galleryModel = useMemo(() => createGalleryModel(), []);

  const builderCapabilities = useMemo(() => readCapabilities(params), [params]);
  const builderMode = useMemo(() => readBuilderMode(params), [params]);
  // `?fixture=<name>` selects a named WorkflowDefinition (dev/fixtures.ts); absent/unrecognized
  // falls back to sampleWorkflow(), preserving the pre-existing default the c1-c11 suite depends on.
  const initialWorkflow = useMemo(() => readFixture(params), [params]);

  // Dev-harness-only: capture the serialized draft for e2e observation.
  const builderAdapters = useMemo<BuilderAdapters>(
    () => ({
      exportBundle: async (draft) => {
        const target = window as ExportCaptureWindow;
        target.__afbExport = { count: (target.__afbExport?.count ?? 0) + 1, draft };
      },
    }),
    [],
  );

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
            capabilities={builderCapabilities}
            adapters={builderAdapters}
            initialWorkflow={initialWorkflow}
            agentCatalog={[{ id: 'agent-demo', name: 'Demo Agent' }]}
            mode={builderMode}
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
