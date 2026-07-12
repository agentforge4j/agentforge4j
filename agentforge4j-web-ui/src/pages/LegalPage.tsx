// SPDX-License-Identifier: Apache-2.0
import { LEGAL_COPY } from '@/copy/legal';

const SECTIONS = [LEGAL_COPY.license, LEGAL_COPY.privacy, LEGAL_COPY.accessibility, LEGAL_COPY.trademark];

export default function LegalPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Legal</h1>

      <div className="mt-8 space-y-10">
        {SECTIONS.map((section) => (
          <section key={section.heading}>
            <h2 className="text-lg font-semibold text-fg">{section.heading}</h2>
            <p className="mt-2 max-w-2xl text-sm text-fg">{section.body}</p>
            {'link' in section && (
              <a href={section.link.href} target="_blank" rel="noreferrer" className="mt-2 inline-block text-sm text-brand underline">
                {section.link.label}
              </a>
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
