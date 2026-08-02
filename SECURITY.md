# Security Policy

We take the security of AgentForge4j seriously. Thank you for helping keep the project and its
users safe.

## Supported versions

> Only the **latest minor of each track** receives patches. When 0.(N+1).0 ships on a track,
> 0.N.x of that track is end-of-life. No LTS, no backports. Breaking changes may occur in any
> 0.x minor and are always listed in the release notes. Builder versions <0.5.0 are unsupported
> pre-launch test builds.

AgentForge4j publishes three independently versioned release tracks — the framework
(`framework-v*`), the shipped workflow catalog (`catalog-v*`), and the workflow builder
(`builder-v*`) — see [CONTRIBUTING.md](CONTRIBUTING.md#releases). Security fixes for an
already-released version land on that version's `release/<track>-0.N.x` maintenance branch and
ship as a patch release; fixes for unreleased tracks are made directly against `main`.

Once 1.0.0 ships on a track, a major-version bump gives the previous major's last minor a
6-month critical/security-fix-only window before it reaches end-of-life — never more than one
legacy major supported at a time.

## Secure defaults

AgentForge4j ships fail-closed defaults for tool execution:

- **Tool policy.** The default `ToolPolicy` (`SecureDefaultToolPolicy`) allows in-process tools
  registered by the embedding application's own code, and **denies** remote-network tools (the
  HTTP tool provider, MCP over streamable HTTP) and local-process tools (MCP over stdio). To allow
  them, supply a custom `ToolPolicy` or the explicit `ToolPolicy.allowAll()` opt-in. The allow
  decision is made solely on the framework-trusted `ToolSourceKind`; provider-declared
  `ToolRiskMetadata` is untrusted advisory metadata and is not consulted by this default policy. A
  tool provider supplied directly to the bootstrap is trusted embedder code, so the `ToolSourceKind`
  it declares is taken at face value — gate untrusted providers with a custom policy.
- **Outbound egress (SSRF).** Outbound tool HTTP is screened by `HttpEgressGuard`: only `http`/
  `https` schemes are admitted, and only hosts that resolve entirely to public addresses — private,
  loopback, link-local, carrier-grade-NAT (`100.64.0.0/10`), cloud-metadata (`169.254.169.254`),
  their IPv4-mapped IPv6 equivalents, and IPv6 unique-local targets are refused. Redirects are never
  followed on either tool path, so a 30x to a blocked host cannot bypass the guard. The two paths
  differ in **when** the host is validated:
  - **HTTP tool provider:** the guard resolves and re-checks the mapped URL immediately before each
    invocation (resolve-and-recheck). It does not pin the resolved IP for the connection, so a host
    that re-resolves to a blocked address between the check and the connect is a known residual
    TOCTOU gap.
  - **MCP over streamable HTTP:** the configured server URL is validated once when the transport is
    built; the MCP SDK then owns the socket and re-resolves the host at connect time, so the
    connect-time address is not re-validated by the guard — a wider residual TOCTOU window than the
    HTTP tool path (redirects are still disabled).

  IP-pinning is a future hardening step. The development-only
  `agentforge4j.tools.egress.allow-private-networks` property lifts the private-network blocks; the
  scheme allowlist is never lifted.

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues, discussions, or
pull requests.**

Instead, report privately by either:

- **Email:** [security@agentforge4j.org](mailto:security@agentforge4j.org), or
- **GitHub private vulnerability reporting:** the repository's **Security → Report a vulnerability**
  page, if enabled.

Please include enough detail for us to reproduce and assess the issue:

- A description of the vulnerability and its potential impact.
- Steps to reproduce, or a proof of concept.
- Affected component(s)/module(s) and, where relevant, the commit or version.
- Any suggested remediation.

## What to expect

- We aim to acknowledge a report within a few business days.
- We will investigate, keep you informed of progress, and coordinate a fix and disclosure
  timeline with you.
- Please give us a reasonable opportunity to address the issue before any public disclosure.

We appreciate responsible disclosure and will credit reporters who wish to be acknowledged.
