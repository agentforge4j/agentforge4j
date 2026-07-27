// SPDX-License-Identifier: Apache-2.0
import { RELEASES_COPY } from '@/copy/releases';

export default function ReleasesPage() {
  return (
    <div className="mx-auto px-6 py-16" style={{ maxWidth: 'var(--max-content-width)' }}>
      <h1 className="text-3xl font-semibold text-fg">Releases</h1>
      <p className="mt-4 max-w-2xl text-fg">{RELEASES_COPY.intro}</p>

      <h2 className="mt-10 text-lg font-semibold text-fg">Release tracks</h2>
      <p className="mt-2 text-sm text-fg-muted">{RELEASES_COPY.tracksIntro}</p>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[640px] text-left text-sm text-fg">
          <caption className="sr-only">Current released version of each AgentForge4j track</caption>
          <thead>
            <tr className="border-b border-border">
              {RELEASES_COPY.tableHeadings.map((heading) => (
                <th key={heading} scope="col" className="py-2 pr-8 font-semibold">
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {RELEASES_COPY.tracks.map((track) => (
              <tr key={track.name} className="border-b border-border align-top">
                <th scope="row" className="py-3 pr-8 font-medium">
                  {track.name}
                </th>
                <td className="py-3 pr-8 text-fg-muted">{track.version}</td>
                <td className="py-3 pr-8 text-fg-muted">
                  <a href={track.mavenHref} target="_blank" rel="noreferrer" className="text-brand underline">
                    {track.publishesTo}
                  </a>
                  <div className="mt-1 text-xs">{track.coordinates}</div>
                </td>
                <td className="py-3 text-fg-muted">
                  {track.releaseHref ? (
                    <a href={track.releaseHref} target="_blank" rel="noreferrer" className="text-brand underline">
                      {track.releaseLabel}
                    </a>
                  ) : (
                    '—'
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

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
