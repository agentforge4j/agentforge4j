import { useMemo, useReducer, type Dispatch } from 'react';
import type { WorkflowDefinition } from '../api/types';
import {
  builderReducer,
  createInitialState,
  isDirty,
  type BuilderAction,
  type BuilderState,
} from './reducer';

export interface UseBuilderStateResult {
  state: BuilderState;
  dispatch: Dispatch<BuilderAction>;
  dirty: boolean;
}

export function useBuilderState(
  initialWorkflow?: WorkflowDefinition,
): UseBuilderStateResult {
  const [state, dispatch] = useReducer(
    builderReducer,
    initialWorkflow,
    (workflow) => createInitialState(workflow),
  );

  const dirty = useMemo(() => isDirty(state), [state]);

  return { state, dispatch, dirty };
}
