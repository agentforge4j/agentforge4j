// SPDX-License-Identifier: Apache-2.0

/**
 * Step-level properties the framework's workflow schema defines that this builder does not model,
 * carried through an import/edit/export round trip instead of being dropped.
 *
 * The builder projects an imported step onto a per-kind node model, and that projection copies only
 * the fields it knows. Everything else used to vanish: importing a workflow and exporting it again
 * silently removed `modelTier`, `estimatedInputTokens`, `estimatedOutputTokens` and
 * `maxUserPromptRounds` — every step-level property of `StepDefinition` the builder has no editor
 * for. Rather than teach the model one field at a time and lose the next one the framework adds,
 * anything the builder does not own is preserved verbatim.
 *
 * The passthrough is deliberately bounded:
 *
 * - **Top-level step properties only.** It never descends into `behaviour`, `contextMapping`, or
 *   any nested structure the builder does own and rewrites wholesale.
 * - **Builder-owned keys are excluded by name** ({@link BUILDER_OWNED_STEP_KEYS}), so a field the
 *   builder edits can never be shadowed by a stale copy captured at import.
 * - **Values are stored, never interpreted.** An unknown future `modelTier` string survives exactly
 *   as written; the builder neither validates nor rewrites it, because the framework's schema is
 *   what decides whether it is legal, and it already rejects an unknown tier at import.
 */
export type PreservedStepFields = Readonly<Record<string, unknown>>;

/**
 * Step properties the builder models itself and therefore never preserves. Each is either written
 * from the builder's own state on export or derived from it, so capturing an imported copy would
 * let stale data shadow an edit.
 *
 * `kind` is here because the builder always writes `"STEP"`; `stepPrompt` because the builder
 * rewrites it to a prompt-file reference on export rather than carrying its text.
 */
export const BUILDER_OWNED_STEP_KEYS: ReadonlySet<string> = new Set([
  'kind',
  'stepId',
  'name',
  'behaviour',
  'contextMapping',
  'stepPrompt',
]);

/**
 * Step properties the builder never preserves when reading a host's workflow-detail DTO.
 *
 * That DTO is a different shape from the exported document: it flattens the context mapping into
 * `inputKeys`/`outputKeys` and carries envelope fields the exported step must not contain. Emitting
 * any of them back into a runtime document would produce a step the framework's schema rejects
 * outright, because `StepDefinition` sets `additionalProperties: false`. So the DTO path excludes
 * its own shape on top of the shared builder-owned keys.
 */
export const BUILDER_OWNED_DETAIL_STEP_KEYS: ReadonlySet<string> = new Set([
  ...BUILDER_OWNED_STEP_KEYS,
  'inputKeys',
  'outputKeys',
  'blueprintRef',
  'nestedWorkflow',
]);

/**
 * Captures every top-level property of an imported step that the builder does not model.
 *
 * @param stepJson one step, exactly as it appeared in the imported document or DTO
 * @param ownedKeys the keys to exclude — {@link BUILDER_OWNED_STEP_KEYS} for an exported document,
 *        {@link BUILDER_OWNED_DETAIL_STEP_KEYS} for a host's workflow-detail DTO
 * @returns the preserved fields, or `undefined` when the step had none — absent stays absent, so an
 *          import that declared nothing extra does not gain an empty object that would later be
 *          indistinguishable from one that lost its contents
 */
export function collectPreservedStepFields(
  stepJson: Record<string, unknown>,
  ownedKeys: ReadonlySet<string> = BUILDER_OWNED_STEP_KEYS,
): PreservedStepFields | undefined {
  const preserved: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(stepJson)) {
    if (ownedKeys.has(key) || value === undefined) {
      continue;
    }
    preserved[key] = value;
  }
  return Object.keys(preserved).length > 0 ? Object.freeze(preserved) : undefined;
}

/**
 * Merges preserved fields into an exported step body, letting the builder's own fields win.
 *
 * Order is the contract: preserved values are laid down first and the builder-written body
 * overwrites them. A preserved key that the builder has since started modelling therefore cannot
 * resurrect a stale value over an edited one, even if {@link BUILDER_OWNED_STEP_KEYS} and the
 * export path fall out of step.
 *
 * @param body the step body the builder just serialized from its own state
 * @param preserved fields captured at import, or `undefined` when there were none
 * @returns a new object; neither argument is mutated
 */
export function applyPreservedStepFields(
  body: Record<string, unknown>,
  preserved: PreservedStepFields | undefined,
): Record<string, unknown> {
  if (!preserved) {
    return body;
  }
  return { ...preserved, ...body };
}

/**
 * Type guard for a restored draft's preserved-field bag: a plain object whose values may be
 * anything, or absent. Used by the persistence gate, which must type-check what it restores.
 */
export function isPreservedStepFields(value: unknown): value is PreservedStepFields {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
