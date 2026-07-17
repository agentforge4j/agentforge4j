// SPDX-License-Identifier: Apache-2.0
import { RELEASES_COPY } from '@/copy/releases';

export default function ReleasesPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Releases</h1>
      <p className="mt-4 max-w-2xl text-fg">{RELEASES_COPY.intro}</p>

      <h2 className="mt-10 text-lg font-semibold text-fg">Release tracks</h2>
      <p className="mt-2 text-sm text-fg-muted">{RELEASES_COPY.tracksIntro}</p>
      <table className="mt-4 text-sm text-fg">
        <tbody>
          {RELEASES_COPY.tracks.map((track) => (
            <tr key={track.name} className="border-b border-border">
              <td className="py-2 pr-8 font-medium">{track.name}</td>
              <td className="py-2 text-fg-muted">{track.publishesTo}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="mt-10 flex flex-wrap gap-6">
        {RELEASES_COPY.links.map((link) => (
          <a key={link.href} href={link.href} target="_blank" rel="noreferrer" className="text-sm text-brand underline">
            {link.label}
          </a>
        ))}
      </div>
    </div>
  );
}
