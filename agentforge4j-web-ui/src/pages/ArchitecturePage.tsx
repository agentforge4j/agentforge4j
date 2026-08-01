// SPDX-License-Identifier: Apache-2.0
import { ARCHITECTURE_COPY } from '@/copy/architecture';

export default function ArchitecturePage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Architecture</h1>
      <p className="mt-4 max-w-2xl text-fg">{ARCHITECTURE_COPY.intro}</p>

      <section className="mt-12">
        <h2 className="text-lg font-semibold text-fg">{ARCHITECTURE_COPY.moduleOverview.heading}</h2>
        <p className="mt-2 max-w-2xl text-sm text-fg-muted">{ARCHITECTURE_COPY.moduleOverview.body}</p>
        <img
          src="/diagrams/readme-oss-module-overview.svg"
          alt="Module dependency overview: Spring Boot Starter depends on Bootstrap, which depends on Runtime; Runtime depends on Config Loader and LLM API; Config Loader and Tools/MCP depend on Core; Provider implementations depend on the LLM API, not the reverse."
          className="diagram-frame mt-6 max-w-full rounded-lg border p-4"
        />
      </section>

      <section className="mt-12">
        <h2 className="text-lg font-semibold text-fg">{ARCHITECTURE_COPY.runtimeOverview.heading}</h2>
        <p className="mt-2 max-w-2xl text-sm text-fg-muted">{ARCHITECTURE_COPY.runtimeOverview.body}</p>
        <img
          src="/diagrams/readme-runtime-overview.svg"
          alt="Runtime execution flow: resolve next step, run agent or plain behaviour, optionally invoke a governed tool call, pass a transition gate, persist state and emit events, then loop until a terminal state is reached."
          className="diagram-frame mt-6 max-w-full rounded-lg border p-4"
        />
      </section>

      <p className="mt-10 text-sm text-fg-muted">
        <a href={ARCHITECTURE_COPY.detailLink.href} className="text-brand underline">
          {ARCHITECTURE_COPY.detailLink.label}
        </a>{' '}
        — the full module-by-module dependency graph, for a deeper look.
      </p>
    </div>
  );
}
