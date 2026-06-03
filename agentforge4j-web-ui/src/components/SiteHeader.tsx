// SPDX-License-Identifier: Apache-2.0
export default function SiteHeader() {
  return (
    <header className="border-b border-border bg-bg-elevated">
      <div className="px-6 h-20 flex items-center">
        <a href="/" aria-label="AgentForge4j" className="flex items-center">
          <img src="/brand/logo-horizontal.svg" alt="AgentForge4j" className="h-16 w-auto block" />
        </a>
      </div>
    </header>
  );
}
