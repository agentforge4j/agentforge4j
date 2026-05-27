import { useCallback, useEffect, useMemo, useState } from 'react';
import { exportWorkflowBundle, importWorkflowFromFilePicker } from '../io/browser/download';
import { useBuilderState } from '../state/useBuilderState';
import { validateWorkflow as defaultValidateWorkflow } from '../validation/validateWorkflow';
import type { WorkflowBuilderProps } from './types';
import './workflow-builder.css';

type ActionKey = 'import' | 'export' | 'save' | 'run' | 'publish';

type PendingState = Partial<Record<ActionKey, boolean>>;
type ErrorState = Partial<Record<ActionKey, string | null>>;

export function WorkflowBuilder({
  capabilities,
  adapters,
  actions,
  theme,
  initialWorkflow,
}: WorkflowBuilderProps) {
  const { state, dispatch, dirty } = useBuilderState(initialWorkflow);
  const [pending, setPending] = useState<PendingState>({});
  const [errors, setErrors] = useState<ErrorState>({});

  const resolvedAdapters = useMemo(
    () => ({
      validateWorkflow: adapters?.validateWorkflow ?? defaultValidateWorkflow,
      importBundle: adapters?.importBundle ?? importWorkflowFromFilePicker,
      exportBundle: adapters?.exportBundle ?? exportWorkflowBundle,
    }),
    [adapters],
  );

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

  const runAction = useCallback(
    async (key: ActionKey, fn: () => Promise<void>) => {
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
    },
    [],
  );

  const handleImport = () =>
    runAction('import', async () => {
      const imported = await resolvedAdapters.importBundle();
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

  const rootClass = ['workflow-builder', theme?.className].filter(Boolean).join(' ');
  const activeError = Object.values(errors).find((value) => value) ?? null;

  return (
    <div className={rootClass} data-testid="workflow-builder">
      <header className="workflow-builder__header">
        <h2 className="workflow-builder__title">Workflow Builder</h2>
        <p className="workflow-builder__subtitle">
          {dirty ? 'Unsaved changes' : 'Up to date'}
          {state.validation.valid ? '' : ' · Validation issues'}
        </p>
      </header>

      <div className="workflow-builder__canvas" data-testid="workflow-builder-canvas">
        Builder canvas — Phase 3
      </div>

      {capabilities.aiAssist ? (
        <div className="workflow-builder__ai" data-testid="workflow-builder-ai">
          AI assist — Phase 3
        </div>
      ) : null}

      <div className="workflow-builder__actions">
        {capabilities.import ? (
          <button
            type="button"
            className="workflow-builder__button"
            data-testid="workflow-builder-import"
            disabled={pending.import}
            onClick={() => void handleImport()}
          >
            {pending.import ? 'Importing…' : 'Import'}
          </button>
        ) : null}

        {capabilities.export ? (
          <button
            type="button"
            className="workflow-builder__button"
            data-testid="workflow-builder-export"
            disabled={pending.export}
            onClick={() => void handleExport()}
          >
            {pending.export ? 'Exporting…' : 'Export'}
          </button>
        ) : null}

        {capabilities.save ? (
          <button
            type="button"
            className="workflow-builder__button workflow-builder__button--primary"
            data-testid="workflow-builder-save"
            disabled={pending.save}
            onClick={() => void handleSave()}
          >
            {pending.save ? 'Saving…' : 'Save'}
          </button>
        ) : null}

        {capabilities.run ? (
          <button
            type="button"
            className="workflow-builder__button"
            data-testid="workflow-builder-run"
            disabled={pending.run}
            onClick={() => void handleRun()}
          >
            {pending.run ? 'Running…' : 'Run'}
          </button>
        ) : null}

        {capabilities.publish ? (
          <button
            type="button"
            className="workflow-builder__button workflow-builder__button--primary"
            data-testid="workflow-builder-publish"
            disabled={pending.publish}
            onClick={() => void handlePublish()}
          >
            {pending.publish ? 'Publishing…' : 'Publish'}
          </button>
        ) : null}
      </div>

      {activeError ? (
        <p className="workflow-builder__status workflow-builder__status--error" role="alert">
          {activeError}
        </p>
      ) : (
        <p className="workflow-builder__status">Phase 1 skeleton — actions invoke host adapters.</p>
      )}
    </div>
  );
}
