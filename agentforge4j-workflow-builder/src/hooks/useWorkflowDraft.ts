// SPDX-License-Identifier: Apache-2.0

import { canvasToWorkflow } from '../model/mapper';
import type { CanvasModel } from '../model/canvasModel';
import type { EditorValidation } from '../validation/validateWorkflow';
import { validateWorkflowEditor } from '../validation/validateWorkflow';
import { useCallback, useState } from 'react';

export type DraftValidationIssue = {
  code: string;
  message: string;
  /** Matched backend step id when parseable from the error text. */
  stepId?: string;
};

function parseStepIdFromMessage(message: string): string | undefined {
  const m = message.match(/stepId[=:'\s]+([a-z0-9-]+)/i) ?? message.match(/step\s+['"]?([a-z0-9-]+)['"]?/i);
  return m?.[1];
}

function validationToIssues(model: CanvasModel, validation: EditorValidation): DraftValidationIssue[] {
  const issues: DraftValidationIssue[] = [];

  for (const message of Object.values(validation.workflow)) {
    if (message) {
      issues.push({ code: 'workflow', message, stepId: parseStepIdFromMessage(message) });
    }
  }

  for (const [idx, stepErrors] of Object.entries(validation.steps)) {
    for (const message of Object.values(stepErrors)) {
      if (message) {
        issues.push({
          code: 'field',
          message: `Step ${idx}: ${message}`,
          stepId: model.nodes[Number(idx)]?.backendStepId,
        });
      }
    }
  }

  for (const message of validation.global) {
    issues.push({ code: 'global', message, stepId: undefined });
  }

  return issues;
}

export function useWorkflowDraft() {
  const [issues, setIssues] = useState<DraftValidationIssue[]>([]);

  const buildFromCanvas = useCallback((model: CanvasModel) => {
    const workflow = canvasToWorkflow(model);
    const validation = validateWorkflowEditor(workflow);
    return { workflow, validation };
  }, []);

  const validateOnly = useCallback(
    (model: CanvasModel) => {
      const { workflow, validation } = buildFromCanvas(model);
      const nextIssues = validationToIssues(model, validation);
      setIssues(nextIssues);
      return { workflow, validation, valid: nextIssues.length === 0 };
    },
    [buildFromCanvas],
  );

  return {
    issues,
    setIssues,
    buildFromCanvas,
    validateOnly,
  };
}
