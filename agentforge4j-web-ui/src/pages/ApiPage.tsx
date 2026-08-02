// SPDX-License-Identifier: Apache-2.0
import PagePlaceholder from '@/components/PagePlaceholder';
import { API_COPY } from '@/copy/api';

/**
 * Links directly into the real /javadoc/{next,latest} routes composed by the Assembler
 * track (design §9/§13) — two fixed links, no version-detection logic. A per-version
 * picker duplicating agentforge4j-docs/scripts/redirect-config.mjs is deliberately out
 * of scope here.
 */
export default function ApiPage() {
  return (
    <PagePlaceholder title={API_COPY.heading}>
      <p className="mt-4 text-fg-muted">{API_COPY.intro}</p>
      <ul className="mt-6 space-y-3">
        <li>
          <a href="/javadoc/latest/" className="text-brand underline">
            {API_COPY.latestLabel}
          </a>
        </li>
        <li>
          <a href="/javadoc/next/" className="text-brand underline">
            {API_COPY.nextLabel}
          </a>
        </li>
      </ul>
    </PagePlaceholder>
  );
}
