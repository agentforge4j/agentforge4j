// SPDX-License-Identifier: Apache-2.0
import { SECURITY_COPY } from '@/copy/security';

export default function SecurityPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Security</h1>
      <p className="mt-4 max-w-2xl text-fg">{SECURITY_COPY.intro}</p>

      <h2 className="mt-10 text-lg font-semibold text-fg">Reporting a vulnerability</h2>
      <p className="mt-2 max-w-2xl text-sm text-fg">{SECURITY_COPY.reportingIntro}</p>
      <ul className="mt-3 space-y-2">
        {SECURITY_COPY.reportingChannels.map((channel) => (
          <li key={channel.href}>
            <a href={channel.href} target="_blank" rel="noreferrer" className="text-brand underline">
              {channel.label}
            </a>
          </li>
        ))}
      </ul>

      <h3 className="mt-6 text-sm font-semibold text-fg">Please include</h3>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-fg-muted">
        {SECURITY_COPY.reportContents.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>

      <h2 className="mt-10 text-lg font-semibold text-fg">What to expect</h2>
      <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-fg-muted">
        {SECURITY_COPY.expectations.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>

      <h2 className="mt-10 text-lg font-semibold text-fg">Secure defaults</h2>
      <div className="mt-3 space-y-4">
        {SECURITY_COPY.secureDefaults.map((item) => (
          <div key={item.heading}>
            <h3 className="text-sm font-semibold text-fg">{item.heading}</h3>
            <p className="mt-1 text-sm text-fg-muted">{item.body}</p>
          </div>
        ))}
      </div>

      <p className="mt-10 text-sm text-fg-muted">
        <a href={SECURITY_COPY.fullPolicyLink.href} target="_blank" rel="noreferrer" className="text-brand underline">
          {SECURITY_COPY.fullPolicyLink.label}
        </a>
      </p>
    </div>
  );
}
