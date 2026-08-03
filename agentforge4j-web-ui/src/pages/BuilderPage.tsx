// SPDX-License-Identifier: Apache-2.0
import '@agentforge4j/workflow-builder-react/styles/tokens.css';
import { WorkflowBuilder } from '@agentforge4j/workflow-builder-react';
import type { BuilderCapabilities, BuilderTheme } from '@agentforge4j/workflow-builder-react';
import { BUILDER_COPY } from '@/copy/builder';

const CAPABILITIES: BuilderCapabilities = {
  import: true,
  export: true,
  save: false,
  run: false,
  publish: false,
  aiAssist: false,
};

/**
 * Maps this site's semantic theme tokens (src/styles/tokens.css) onto the builder package's
 * documented consumer-facing token contract — the `--afb-*` custom properties listed in
 * @agentforge4j/workflow-builder-react's README "Design tokens" section. Those are the variables
 * the builder's canvas, node cards, palette, inspector, toolbar, and header selectors consume
 * directly; BuilderTheme.variables is applied as inline custom properties on the builder's root
 * element (`.workflow-builder`), so an inline `--afb-*` value overrides the package's stylesheet
 * default for the whole subtree. Without these, the builder keeps its own fixed internal palette
 * (dark navy canvas/chrome with light node cards) regardless of this site's light/dark/system
 * selection. Values here are `var(...)` REFERENCES, not resolved colors, so the mapping tracks a
 * theme change live: `data-theme` flips the referenced token's value and the builder repaints
 * along with the rest of the page, with no extra re-render wiring needed here.
 *
 * The `--color-*` entries below feed a second, older variable family (`--builder-color-*` in the
 * package's tokens.css) that only the `.wf-*` control styles (buttons, panels, narrow-viewport
 * notice) read — kept alongside the `--afb-*` mappings so those controls pick precise values
 * (e.g. accent-fg from `--color-brand-ink`) instead of their generic fallbacks.
 *
 * `--color-border`, `--color-danger`, `--color-success`, and `--color-warning` are deliberately
 * NOT listed below even though the builder's contract accepts them: this site already declares a
 * token of that exact same name on `:root`, which `.workflow-builder` (a descendant element)
 * inherits for free. Re-declaring `'--color-border': 'var(--color-border)'` inline on that same
 * element would be a self-reference — CSS resolves a custom property cycle to invalid rather than
 * "the inherited value", which would silently DISABLE the free inheritance and fall the builder
 * back to its own internal default instead. Only list a name here when this site's source token
 * has a genuinely different name (avoiding the cycle) or needs a different value than its
 * same-named site token would supply. The `--afb-*` names cannot collide this way — this site
 * declares no `--afb-*` tokens of its own.
 *
 * Cosmetics with no site-token counterpart (canvas glow, selection ring, per-kind accent colors,
 * the human-gate amber) are intentionally left at the package's own defaults: they are the
 * builder's identity colors and read correctly against both mapped canvas values.
 */
const BUILDER_THEME: BuilderTheme = {
  variables: {
    // Documented --afb-* contract (README "Design tokens"): canvas, node, and chrome surfaces.
    '--afb-canvas-bg': 'var(--color-bg)',
    '--afb-canvas-dot': 'var(--color-border)',
    '--afb-node-surface': 'var(--color-bg-elevated)',
    '--afb-node-surface-2': 'var(--color-bg)',
    '--afb-node-border': 'var(--color-border)',
    '--afb-node-text': 'var(--color-fg)',
    '--afb-node-muted': 'var(--color-fg-muted)',
    '--afb-node-shadow': 'var(--shadow-md)',
    '--afb-chrome-bg': 'var(--color-bg-elevated)',
    '--afb-chrome-border': 'var(--color-border)',
    '--afb-chrome-text': 'var(--color-fg)',
    '--afb-chrome-muted': 'var(--color-fg-muted)',
    '--afb-accent': 'var(--color-brand)',
    '--afb-ok': 'var(--color-success)',
    '--afb-warn': 'var(--color-warning)',
    '--afb-err': 'var(--color-danger)',
    // Legacy .wf-* control aliases (see comment above).
    '--color-surface': 'var(--color-bg-elevated)',
    '--color-text': 'var(--color-fg)',
    '--color-text-muted': 'var(--color-fg-muted)',
    '--color-accent': 'var(--color-brand)',
    '--color-accent-fg': 'var(--color-brand-ink)',
    '--color-card': 'var(--color-bg-elevated)',
    '--color-canvas': 'var(--color-bg)',
  },
};

export default function BuilderPage() {
  // Flex column so the builder receives all remaining height as a definite size (its own root
  // is `height: 100%`) — the app shell guarantees <main> a definite viewport-bound height on
  // this route. min-h-0 on both levels keeps the flex chain from re-expanding to content size.
  return (
    <div className="flex h-full min-h-0 w-full flex-col">
      {/* Visually hidden, not omitted: the canvas has no other document heading at all until a
          step is selected (the inspector panel provides its own), so a screen reader / other
          assistive tech landing on this route with nothing selected had no page-level heading to
          announce. sr-only provides that structure without any visible/layout change — same
          "keep the DOM contract, hide the pixels" technique already used elsewhere in this UI
          (see visual/checks.ts's clipped-panel check, which explicitly excludes exactly this
          pattern from being flagged as a clipping defect). */}
      <h1 className="sr-only">{BUILDER_COPY.heading}</h1>
      <p className="shrink-0 px-4 py-2 text-sm text-fg-muted">{BUILDER_COPY.accessibilityNote}</p>
      {/* overflow-y-auto: on viewports shorter than the builder's own CSS min-height (24rem)
          the builder cannot fit the remaining space — this pane then scrolls internally
          instead of the builder spilling past the viewport-pinned shell and clipping its
          bottom controls below the fold. No effect when the builder fits. */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        <WorkflowBuilder capabilities={CAPABILITIES} theme={BUILDER_THEME} />
      </div>
    </div>
  );
}
