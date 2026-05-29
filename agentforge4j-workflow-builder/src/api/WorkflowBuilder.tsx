// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { WorkflowCanvas } from '../canvas/WorkflowCanvas';
import { ACTION_LABELS, GUIDED_STAGE_LABELS } from '../copy/workflow-terminology';
import { GuidedStepper } from '../guided/GuidedStepper';
import { createInitialCanvasModel, useCanvasState } from '../hooks/useCanvasState';
import { useBuilderMode } from '../hooks/useBuilderMode';
import type { DraftValidationIssue } from '../hooks/useWorkflowDraft';
import { useWorkflowDraft } from '../hooks/useWorkflowDraft';
import { StepConfigPanel } from '../inspector/StepConfigPanel';
import type { CanvasModel, CanvasNode } from '../model/canvasModel';
import {
  canvasToWorkflow,
  defaultNodeData,
  newStepId,
  workflowToCanvas,
} from '../model/mapper';
import type { NodeKind } from '../model/nodeKinds';
import { StepPalette } from '../palette/StepPalette';
import { useBuilderState } from '../state/useBuilderState';
import { ValidationPill } from '../validation-ui/ValidationPill';
import type { EditorValidation } from '../validation/validateWorkflow';
import { validateWorkflow as defaultValidateWorkflow } from '../validation/validateWorkflow';
import { exportWorkflowBundle } from '../io/browser/download';
import { importWorkflowFromFilePicker } from '../io/browser/upload';
import type { WorkflowBuilderProps } from './types';
import { emptyWorkflow } from './types';
import '../styles/tokens.css';
import './workflow-builder.css';

type ActionKey = 'import' | 'export' | 'save' | 'run' | 'publish';

type PendingState = Partial<Record<ActionKey, boolean>>;
type ErrorState = Partial<Record<ActionKey, string | null>>;

function flattenClientIssues(model: CanvasModel, validation: EditorValidation): DraftValidationIssue[] {
  const safe = (value: string | undefined) => value ?? '';
  return [
    ...Object.entries(validation.workflow).map(([field, message]) => ({
      code: `workflow.${field}`,
      message: safe(message),
      stepId: undefined,
    })),
    ...Object.entries(validation.steps).flatMap(([idx, errs]) =>
      Object.entries(errs).map(([field, message]) => ({
        code: `step.${field}`,
        message: safe(message),
        stepId: model.nodes[Number(idx)]?.backendStepId,
      })),
    ),
    ...Object.entries(validation.artifacts).flatMap(([artifactId, errs]) =>
      Object.entries(errs).map(([field, message]) => ({
        code: `artifact.${artifactId}.${field}`,
        message: safe(message),
        stepId: undefined,
      })),
    ),
    ...validation.global.map((message) => ({ code: 'global', message: safe(message), stepId: undefined })),
  ];
}

function hasApprovalTransition(node: CanvasNode): boolean {
  const data = node.data as Partial<{ transition: string }>;
  return data.transition === 'HUMAN_APPROVAL';
}

function hasConfiguredInput(node: CanvasNode): boolean {
  if (node.kind !== 'ASK_USER') {
    return false;
  }
  return Boolean(node.data.name?.trim() || node.data.question?.trim());
}

function nextLibraryStepPosition(
  model: CanvasModel,
  selectedId: string | null,
): { position: { x: number; y: number }; afterNodeId: string | null } {
  const GAP_X = 280;
  const GAP_Y = 140;
  const byId = new Map(model.nodes.map((node) => [node.id, node] as const));
  const outgoing = new Map<string, string[]>();
  const incomingCount = new Map<string, number>();

  for (const node of model.nodes) {
    outgoing.set(node.id, []);
    incomingCount.set(node.id, 0);
  }
  for (const edge of model.edges) {
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge.target]);
    incomingCount.set(edge.target, (incomingCount.get(edge.target) ?? 0) + 1);
  }

  const selectedNode = selectedId ? (byId.get(selectedId) ?? null) : null;
  if (selectedNode) {
    return {
      position: { x: selectedNode.position.x + GAP_X, y: selectedNode.position.y },
      afterNodeId: selectedNode.id,
    };
  }

  const start = model.startNodeId && byId.has(model.startNodeId) ? model.startNodeId : (model.nodes[0]?.id ?? null);
  if (!start) {
    return { position: { x: 80, y: 120 }, afterNodeId: null };
  }

  let cursor = start;
  const visited = new Set<string>([cursor]);
  while (true) {
    const next = (outgoing.get(cursor) ?? []).find((target) => (incomingCount.get(target) ?? 0) <= 1);
    if (!next || visited.has(next)) {
      break;
    }
    visited.add(next);
    cursor = next;
  }

  const anchor = byId.get(cursor);
  if (!anchor) {
    return { position: { x: 80, y: 120 }, afterNodeId: null };
  }
  return {
    position: {
      x: anchor.position.x + GAP_X,
      y: anchor.position.y + (anchor.kind === 'DECISION' ? GAP_Y : 0),
    },
    afterNodeId: anchor.id,
  };
}

export function WorkflowBuilder({
  capabilities,
  adapters,
  actions,
  theme,
  initialWorkflow,
  agentCatalog = [],
}: WorkflowBuilderProps) {
  const seed = initialWorkflow ?? emptyWorkflow();
  const { state, dispatch, dirty } = useBuilderState(seed);
  const [pending, setPending] = useState<PendingState>({});
  const [errors, setErrors] = useState<ErrorState>({});
  const skipDraftSync = useRef(false);

  const initialCanvas = useMemo(
    () => (seed.steps.length > 0 ? workflowToCanvas(seed) : createInitialCanvasModel()),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- canvas seed is mount-only
    [],
  );

  const {
    model,
    setModel,
    setModelFromLoad,
    selectedId,
    setSelectedId,
    markClean,
    updateNodeData,
    appendNode,
  } = useCanvasState(initialCanvas);

  const { mode, setMode } = useBuilderMode(model, !initialWorkflow?.id);
  const { buildFromCanvas } = useWorkflowDraft();

  const resolvedAdapters = useMemo(
    () => ({
      validateWorkflow: adapters?.validateWorkflow ?? defaultValidateWorkflow,
      importBundle: adapters?.importBundle ?? importWorkflowFromFilePicker,
      exportBundle: adapters?.exportBundle ?? exportWorkflowBundle,
    }),
    [adapters],
  );

  useEffect(() => {
    if (skipDraftSync.current) {
      skipDraftSync.current = false;
      return;
    }
    const draft = canvasToWorkflow(model);
    dispatch({ type: 'SET_DRAFT', draft });
  }, [model, dispatch]);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve(resolvedAdapters.validateWorkflow(state.draft)).then((result) => {
      if (!cancelled) {
        dispatch({ type: 'SET_VALIDATION', validation: result });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [state.draft, resolvedAdapters, dispatch]);

  const clientValidation = useMemo(() => buildFromCanvas(model).validation, [buildFromCanvas, model]);
  const clientIssues = useMemo(() => flattenClientIssues(model, clientValidation), [clientValidation, model]);

  const issueCountByBackendStepId = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const issue of clientIssues) {
      if (!issue.stepId) {
        continue;
      }
      counts[issue.stepId] = (counts[issue.stepId] ?? 0) + 1;
    }
    return counts;
  }, [clientIssues]);

  const runAction = useCallback(async (key: ActionKey, fn: () => Promise<void>) => {
    setPending((prev) => ({ ...prev, [key]: true }));
    setErrors((prev) => ({ ...prev, [key]: null }));
    try {
      await fn();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Action failed';
      setErrors((prev) => ({ ...prev, [key]: message }));
    } finally {
      setPending((prev) => ({ ...prev, [key]: false }));
    }
  }, []);

  const handleImport = () =>
    runAction('import', async () => {
      const imported = await resolvedAdapters.importBundle();
      skipDraftSync.current = true;
      setModelFromLoad(workflowToCanvas(imported));
      dispatch({ type: 'SET_DRAFT', draft: imported });
      dispatch({ type: 'SET_BASELINE', baseline: imported });
      dispatch({
        type: 'SET_IMPORT_META',
        importMeta: { source: 'file', importedAt: new Date().toISOString() },
      });
    });

  const handleExport = () =>
    runAction('export', async () => {
      await resolvedAdapters.exportBundle(state.draft, 'json');
    });

  const handleSave = () =>
    runAction('save', async () => {
      if (!actions?.save) {
        throw new Error('Save action is not configured');
      }
      await actions.save(state.draft);
      dispatch({ type: 'SET_BASELINE', baseline: state.draft });
      markClean();
    });

  const handleRun = () =>
    runAction('run', async () => {
      if (!actions?.run) {
        throw new Error('Run action is not configured');
      }
      await actions.run(state.draft);
    });

  const handlePublish = () =>
    runAction('publish', async () => {
      if (!actions?.publish) {
        throw new Error('Publish action is not configured');
      }
      await actions.publish(state.draft);
    });

  const onAddStepFromLibrary = useCallback(
    (kind: NodeKind, options?: { patch?: Record<string, unknown> }) => {
      const { position, afterNodeId } = nextLibraryStepPosition(model, selectedId);
      const prefix: Record<NodeKind, string> = {
        ASK_USER: 'ask-user',
        AI_STEP: 'ai-step',
        AI_DEBATE: 'ai-debate',
        DECISION: 'decision',
        REPEAT: 'repeat',
        SAVE_RESULT: 'save-result',
        REUSE_WORKFLOW: 'reuse-wf',
        LOAD_RESOURCE: 'load-res',
        STOP: 'stop',
        RETRY: 'retry',
      };
      const backendStepId = newStepId(prefix[kind]);
      const id = `c-${backendStepId}`;
      const node = {
        id,
        backendStepId,
        kind,
        position,
        data: { ...defaultNodeData(kind), ...options?.patch },
      } as CanvasNode;
      setModel((m) => {
        const edge =
          afterNodeId && !m.edges.some((e) => e.source === afterNodeId && e.target === id)
            ? [
                {
                  id: `e-${afterNodeId}-${id}-auto`,
                  source: afterNodeId,
                  target: id,
                  sourceHandle: null,
                  label: null,
                },
              ]
            : [];
        return {
          ...m,
          nodes: [...m.nodes, node],
          edges: [...m.edges, ...edge],
          startNodeId: m.startNodeId ?? id,
        };
      });
      setSelectedId(id);
    },
    [model, selectedId, setModel, setSelectedId],
  );

  const focusIssue = useCallback(
    (stepId?: string) => {
      if (!stepId) {
        return;
      }
      const node = model.nodes.find((n) => n.backendStepId === stepId);
      if (node) {
        setSelectedId(node.id);
      }
    },
    [model.nodes, setSelectedId],
  );

  const askUserNode = model.nodes.find((n) => n.kind === 'ASK_USER');
  const guidedStages = useMemo(
    () => [
      {
        label: GUIDED_STAGE_LABELS.addInput,
        complete: model.nodes.some((node) => node.kind === 'ASK_USER' && hasConfiguredInput(node)),
        actionLabel: GUIDED_STAGE_LABELS.configureInput,
      },
      {
        label: GUIDED_STAGE_LABELS.addAiStep,
        complete: model.nodes.some((node) => node.kind === 'AI_STEP' || node.kind === 'AI_DEBATE'),
        actionLabel: GUIDED_STAGE_LABELS.addAiStep,
      },
      {
        label: GUIDED_STAGE_LABELS.addApproval,
        complete: model.nodes.some((node) => hasApprovalTransition(node)),
        actionLabel: GUIDED_STAGE_LABELS.requireApproval,
      },
      {
        label: GUIDED_STAGE_LABELS.generateResult,
        complete: model.nodes.some((node) => node.kind === 'SAVE_RESULT' || node.kind === 'STOP'),
        actionLabel: GUIDED_STAGE_LABELS.addSaveStep,
      },
    ],
    [model.nodes],
  );

  const activeGuidedIndex = guidedStages.findIndex((stage) => !stage.complete);

  const onGuidedStageAction = useCallback(
    (index: number) => {
      switch (index) {
        case 0:
          if (askUserNode) {
            setSelectedId(askUserNode.id);
          }
          break;
        case 1:
          onAddStepFromLibrary('AI_STEP');
          break;
        case 2: {
          const aiNode = model.nodes.find((n) => n.kind === 'AI_STEP');
          if (aiNode) {
            updateNodeData(aiNode.id, { transition: 'HUMAN_APPROVAL' } as Partial<CanvasNode['data']>);
            setSelectedId(aiNode.id);
          } else {
            onAddStepFromLibrary('AI_STEP', { patch: { transition: 'HUMAN_APPROVAL' } });
          }
          break;
        }
        case 3:
          onAddStepFromLibrary('SAVE_RESULT');
          break;
        default:
          break;
      }
    },
    [askUserNode, model.nodes, onAddStepFromLibrary, setSelectedId, updateNodeData],
  );

  const rootStyle = theme?.variables
    ? ({ style: theme.variables as React.CSSProperties } as const)
    : undefined;

  const rootClass = ['workflow-builder', theme?.className].filter(Boolean).join(' ');
  const activeError = Object.values(errors).find((value) => value) ?? null;
  const subtitle = [
    dirty ? ACTION_LABELS.unsavedChanges : ACTION_LABELS.upToDate,
    state.validation.valid ? null : ACTION_LABELS.validationIssues,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className={rootClass} data-testid="workflow-builder" {...rootStyle}>
      <header className="workflow-builder__header">
        <input
          className="workflow-builder__name-input"
          value={model.workflowName}
          placeholder={ACTION_LABELS.workflowNamePlaceholder}
          aria-label={ACTION_LABELS.workflowNameLabel}
          onChange={(e) => setModel((m) => ({ ...m, workflowName: e.target.value }))}
        />
        <input
          className="workflow-builder__id-input"
          value={model.workflowId}
          placeholder={ACTION_LABELS.workflowIdPlaceholder}
          aria-label={ACTION_LABELS.workflowIdLabel}
          onChange={(e) => setModel((m) => ({ ...m, workflowId: e.target.value }))}
        />
        <div className="workflow-builder__mode-toggle" role="group" aria-label="Builder mode">
          <button
            type="button"
            className={['wf-button', mode === 'guided' ? 'wf-button--primary' : 'wf-button--ghost'].join(' ')}
            onClick={() => setMode('guided')}
          >
            {ACTION_LABELS.guidedMode}
          </button>
          <button
            type="button"
            className={['wf-button', mode === 'advanced' ? 'wf-button--primary' : 'wf-button--ghost'].join(' ')}
            onClick={() => setMode('advanced')}
          >
            {ACTION_LABELS.advancedMode}
          </button>
        </div>
        <ValidationPill model={model} clientIssues={clientIssues} onFix={focusIssue} />
        {capabilities.export ? (
          <button
            type="button"
            className="wf-button wf-button--ghost"
            disabled={pending.export}
            data-testid="workflow-builder-export"
            onClick={() => void handleExport()}
          >
            {pending.export ? ACTION_LABELS.exporting : ACTION_LABELS.export}
          </button>
        ) : null}
        {capabilities.save ? (
          <button
            type="button"
            className="wf-button wf-button--primary"
            disabled={pending.save}
            data-testid="workflow-builder-save"
            onClick={() => void handleSave()}
          >
            {pending.save ? ACTION_LABELS.saving : ACTION_LABELS.save}
          </button>
        ) : null}
        {capabilities.run ? (
          <button
            type="button"
            className="wf-button wf-button--secondary"
            disabled={pending.run}
            data-testid="workflow-builder-run"
            onClick={() => void handleRun()}
          >
            {pending.run ? ACTION_LABELS.running : ACTION_LABELS.run}
          </button>
        ) : null}
        {capabilities.publish ? (
          <button
            type="button"
            className="wf-button wf-button--primary"
            disabled={pending.publish}
            data-testid="workflow-builder-publish"
            onClick={() => void handlePublish()}
          >
            {pending.publish ? ACTION_LABELS.publishing : ACTION_LABELS.publish}
          </button>
        ) : null}
        {capabilities.aiAssist ? (
          <button
            type="button"
            className="wf-button wf-button--secondary"
            data-testid="workflow-builder-ai"
            aria-label={ACTION_LABELS.aiAssist}
          >
            {ACTION_LABELS.aiAssist}
          </button>
        ) : null}
        <p className="workflow-builder__subtitle">{subtitle}</p>
      </header>

      {model.unsupported ? (
        <div className="workflow-builder__banner workflow-builder__banner--warning" role="status">
          <p className="workflow-builder__banner-title">{ACTION_LABELS.unsupportedBannerTitle}</p>
          {model.unsupportedReasons?.length ? (
            <ul className="workflow-builder__banner-list">
              {model.unsupportedReasons.map((reason, index) => (
                <li key={index}>{reason}</li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      <div className="workflow-builder__workspace">
        {mode === 'guided' ? (
          <div className="workflow-builder__guided">
            <GuidedStepper
              stages={guidedStages}
              activeIndex={activeGuidedIndex === -1 ? guidedStages.length - 1 : activeGuidedIndex}
              onStageAction={onGuidedStageAction}
            />
          </div>
        ) : null}

        <StepPalette
          mode={mode}
          onAddStep={(kind) => onAddStepFromLibrary(kind)}
          defaultCollapsed={mode !== 'advanced'}
        />

        <div className="workflow-builder__canvas" data-testid="workflow-builder-canvas">
          <WorkflowCanvas
            model={model}
            onModelChange={setModel}
            onSelectNode={setSelectedId}
            selectedId={selectedId}
            onAppend={appendNode}
            issueCountByBackendStepId={issueCountByBackendStepId}
          />
        </div>

        <StepConfigPanel
          model={model}
          selectedId={selectedId}
          mode={mode}
          onClose={() => setSelectedId(null)}
          onUpdateNodeData={updateNodeData}
          agentCatalog={agentCatalog}
        />
      </div>

      <div className="workflow-builder__actions">
        {capabilities.import ? (
          <button
            type="button"
            className="workflow-builder__button"
            data-testid="workflow-builder-import"
            disabled={pending.import}
            onClick={() => void handleImport()}
          >
            {pending.import ? ACTION_LABELS.importing : ACTION_LABELS.import}
          </button>
        ) : null}
      </div>

      {activeError ? (
        <p className="workflow-builder__status workflow-builder__status--error" role="alert">
          {activeError}
        </p>
      ) : null}
    </div>
  );
}
