// SPDX-License-Identifier: Apache-2.0

import type { CanvasModel } from '../model/canvasModel';
import { useCallback, useState } from 'react';

export type BuilderMode = 'guided' | 'advanced';

const BUILDER_MODE_KEY = 'agentforge_builder_mode';

function isUntouchedStarter(model: CanvasModel): boolean {
  if (model.nodes.length !== 1) {
    return false;
  }
  const starter = model.nodes[0];
  if (starter.kind !== 'ASK_USER') {
    return false;
  }
  return (
    !model.workflowId.trim() &&
    !model.workflowName.trim() &&
    !model.description.trim() &&
    !starter.data.name.trim() &&
    !starter.data.question.trim()
  );
}

export function readStoredBuilderMode(): BuilderMode | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const value = window.localStorage.getItem(BUILDER_MODE_KEY);
  if (value === 'guided' || value === 'advanced') {
    return value;
  }
  return null;
}

export function isUntouchedStarterCanvas(model: CanvasModel): boolean {
  return isUntouchedStarter(model);
}

export function useBuilderMode(model: CanvasModel, isNewRoute: boolean) {
  const [mode, setMode] = useState<BuilderMode>(() => {
    const stored = readStoredBuilderMode();
    if (stored) {
      return stored;
    }
    if (isNewRoute && isUntouchedStarter(model)) {
      return 'guided';
    }
    return 'advanced';
  });

  const setAndPersistMode = useCallback((nextMode: BuilderMode) => {
    setMode(nextMode);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(BUILDER_MODE_KEY, nextMode);
    }
  }, []);

  return { mode, setMode: setAndPersistMode };
}
