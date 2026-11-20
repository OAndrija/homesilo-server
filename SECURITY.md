# Security

HomeSilo holds people's private files, so I take reports about its security
seriously and would much rather hear about a problem than not.

## Reporting a problem

Please don't open a public issue. Use
[Report a vulnerability](https://github.com/OAndrija/homesilo-server/security/advisories/new)
on this repository's Security tab instead; only the maintainers can see it.
That's also the right place for problems in the
[web client](https://github.com/OAndrija/homesilo-client).

It helps to include:

- what an attacker could do, and what they need first (an account, an admin
  account, access to the same network, nothing at all)
- the steps to reproduce it, against a fresh install if you can
- the version, from **Users** in the admin area or the image tag

You'll get a reply within a week. Once there's a fix, it goes out as a release
with a GitHub security advisory, and you're credited there unless you'd rather
not be.

## Supported versions

Fixes go into the latest release. If you're on `HOMESILO_VERSION=1` (the
default), `docker compose pull && docker compose up -d` picks them up.

## What's in scope

Anything that lets someone read, change or delete files or accounts they
shouldn't, get around sign-in or its limits, run code in the browser through
HomeSilo, or take over a server they don't own.

Not in scope:

- Problems that need an admin account to begin with. An admin can already do
  everything on their server.
- Missing hardening on a setup the docs advise against, such as exposing port
  8080 directly to the internet without HTTPS.
- Reports from automated scanners with no way of exploiting them.

## How a release is checked

Every image is scanned with Trivy before it's published and ships with an SBOM
and a signed build provenance. CodeQL runs on every change, and Dependabot keeps
the dependencies and base images current. You can check that an image was built
from this repository with:

```sh
gh attestation verify oci://ghcr.io/oandrija/homesilo-server:1 --owner OAndrija
```
