// SPDX-License-Identifier: Apache-2.0
import { GITHUB_URL } from '@/config/nav';

export const SECURITY_COPY = {
  intro: 'We take the security of AgentForge4j seriously.',
  reportingIntro:
    'Please do not report security vulnerabilities through public GitHub issues, discussions, ' +
    'or pull requests. Instead, report privately by either:',
  reportingChannels: [
    { label: 'Email: security@agentforge4j.org', href: 'mailto:security@agentforge4j.org' },
    {
      label: "GitHub private vulnerability reporting, via the repository's Security tab, if enabled",
      href: `${GITHUB_URL}/security`,
    },
  ],
  reportContents: [
    'A description of the vulnerability and its potential impact.',
    'Steps to reproduce, or a proof of concept.',
    'Affected component(s)/module(s) and, where relevant, the commit or version.',
    'Any suggested remediation.',
  ],
  expectations: [
    'We aim to acknowledge a report within a few business days.',
    'We investigate, keep you informed of progress, and coordinate a fix and disclosure ' +
      'timeline with you.',
    'Please give us a reasonable opportunity to address the issue before any public disclosure.',
  ],
  secureDefaults: [
    {
      heading: 'Tool policy',
      body:
        'The default tool policy allows only in-process tools registered by the embedding ' +
        'application and denies remote-network and local-process tools unless explicitly opted in.',
    },
    {
      heading: 'Outbound egress',
      body:
        'Outbound tool HTTP is screened against private, loopback, link-local, and ' +
        'cloud-metadata address ranges; redirects are never followed.',
    },
  ],
  fullPolicyLink: { label: 'Full security policy', href: `${GITHUB_URL}/blob/main/SECURITY.md` },
} as const;
