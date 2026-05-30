// SPDX-License-Identifier: Apache-2.0
export default function SiteHeader() {
  return (
    <header className="border-b border-border bg-bg-elevated">
      <div className="mx-auto px-4 h-14 flex items-center" style={{ maxWidth: 'var(--max-content-width)' }}>
        <a href="/" className="flex items-center gap-2">
          <img
            src="/brand/logo-light.png"
            alt="AgentForge4j"
            className="h-8 w-auto"
          />
        </a>
      </div>
    </header>
  );
}
