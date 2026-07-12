// SPDX-License-Identifier: Apache-2.0
import { CONTACT_COPY } from '@/copy/contact';

export default function ContactPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Contact</h1>
      <p className="mt-4 text-fg">{CONTACT_COPY.intro}</p>

      <dl className="mt-8 space-y-6">
        {CONTACT_COPY.aliases.map((alias) => (
          <div key={alias.address}>
            <dt>
              <a href={`mailto:${alias.address}`} className="text-lg font-semibold text-brand underline">
                {alias.address}
              </a>
            </dt>
            <dd className="mt-1 text-sm text-fg-muted">{alias.purpose}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
