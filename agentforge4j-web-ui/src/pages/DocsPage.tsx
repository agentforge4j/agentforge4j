// SPDX-License-Identifier: Apache-2.0
import PagePlaceholder from '@/components/PagePlaceholder';

/**
 * Thin handoff into the composed Docusaurus artifact mounted at /docs/** (design
 * §10/P20) — not a page in its own right. Full nav parity with the docs site is
 * out of scope for this track (assembler track wires the actual mount).
 */
export default function DocsPage() {
  return (
    <PagePlaceholder title="Documentation">
      <p className="mt-4 text-fg-muted">
        Continue to the <a href="/docs/" className="text-brand underline">documentation</a>.
      </p>
    </PagePlaceholder>
  );
}
