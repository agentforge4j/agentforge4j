// SPDX-License-Identifier: Apache-2.0
import { COMMUNITY_COPY } from '@/copy/community';

export default function CommunityPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Community &amp; contributing</h1>
      <p className="mt-4 max-w-2xl text-fg">{COMMUNITY_COPY.intro}</p>
      <p className="mt-4 max-w-2xl text-sm text-fg-muted">{COMMUNITY_COPY.status}</p>

      <h2 className="mt-10 text-lg font-semibold text-fg">Ways to contribute</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-fg">
        {COMMUNITY_COPY.ways.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>

      <h2 className="mt-10 text-lg font-semibold text-fg">Development setup</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-fg">
        {COMMUNITY_COPY.devSetup.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>

      <h2 className="mt-10 text-lg font-semibold text-fg">Pull request workflow</h2>
      <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm text-fg">
        {COMMUNITY_COPY.prWorkflow.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ol>

      <h2 className="mt-10 text-lg font-semibold text-fg">Links</h2>
      <ul className="mt-3 space-y-2">
        {COMMUNITY_COPY.links.map((link) => (
          <li key={link.href}>
            <a href={link.href} target="_blank" rel="noreferrer" className="text-brand underline">
              {link.label}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
