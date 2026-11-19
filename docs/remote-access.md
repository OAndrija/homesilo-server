# Reaching HomeSilo from outside

Out of the box, HomeSilo is only reachable on your home network. There are a
few ways to use it from anywhere. Which one fits depends mostly on who needs
to get in.

| | Just you (and family on your devices) | Anyone you send an invite link |
|---|---|---|
| Open ports at home | none | Caddy: 80 and 443 |
| HTTPS | Tailscale | Cloudflare or Caddy |
| Works behind CGNAT | yes | Cloudflare yes, Caddy no |

If you're not sure, start with Tailscale. It's the most private and the
hardest to get wrong, and you can switch later without losing anything.

## Tailscale: private, for your own devices

[Tailscale](https://tailscale.com) puts your devices on a private network.
Nothing is exposed to the internet, and nothing needs a domain.

1. Install Tailscale on the HomeSilo machine and sign in.
2. Install it on your phone and laptop, signed in to the same account.
3. On the HomeSilo machine, give it an HTTPS address on your tailnet:

   ```sh
   sudo tailscale serve --bg 8080
   ```

   It prints the address, something like `https://homesilo.tail1234.ts.net`.

That's it. The catch: everyone who uses HomeSilo needs Tailscale on their
device and access to your tailnet. That's fine for a household, awkward for
sending a link to your aunt. Invite links still work, but only for people
already on your tailnet.

Tailscale passes connections on from the HomeSilo machine itself, so the app
sees them all coming from one address. Sign-in limits then count everyone
together, which is reasonable when everyone on your tailnet is someone you know.

## Cloudflare Tunnel: public, with no open ports

The HomeSilo machine opens an outgoing connection to Cloudflare, and Cloudflare
serves your domain over HTTPS. Your router stays closed, your home address stays
hidden, and it works even if your provider uses CGNAT.

You'll need a domain that uses Cloudflare for DNS. Any registrar works;
[Cloudflare's own](https://www.cloudflare.com/products/registrar/) sells at cost.

1. In the Cloudflare dashboard, go to **Zero Trust → Networks → Tunnels** and
   create a tunnel of type *Cloudflared*. Copy the token it shows you.
2. Add a public hostname: your subdomain (say `drive.example.com`), type
   `HTTP`, URL `client:8080`.
3. Put the token in `.env`:

   ```sh
   TUNNEL_TOKEN=eyJh...
   ```

4. Download the overlay and start HomeSilo with it:

   ```sh
   curl -fsSLO https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/compose.cloudflare.yml
   docker compose -f docker-compose.yml -f compose.cloudflare.yml up -d
   ```

   Use the same two `-f` options with every `docker compose` command from now
   on, or put `COMPOSE_FILE=docker-compose.yml:compose.cloudflare.yml` in `.env`
   so plain `docker compose` picks both up.

The overlay also tells HomeSilo to read each visitor's real address from
Cloudflare, so sign-in limits apply per person rather than to everyone at once.

Worth doing in the Cloudflare dashboard for your domain:

- **SSL/TLS → Edge Certificates:** turn on *Always Use HTTPS* and *HSTS*, and set
  the minimum TLS version to 1.2.
- **Security → WAF:** a custom rule that blocks countries you'll never connect
  from, and a rate limiting rule on paths starting with `/api/v1/auth/`.
- **Caching:** leave it on the default. Don't add a "Cache Everything" rule:
  HomeSilo's files are private and must not be cached at Cloudflare.

What to know before choosing it:

- Cloudflare decrypts the traffic at its edge, like every CDN. Your files pass
  through Cloudflare's servers in readable form. Most people are fine with
  that; if you aren't, use Caddy.
- Cloudflare's free plan allows 100 MB per request. HomeSilo sends large files
  in 32 MB pieces, so this doesn't limit what you can upload.
- Cloudflare's terms don't allow using the free plan mostly for serving video
  or other large files. A family's documents and photos are fine; streaming a
  film collection to many people isn't what it's for.

If you only use HomeSilo through the tunnel, set `HOMESILO_BIND=127.0.0.1` in
`.env` so port 8080 isn't open on your home network either. Create the admin
account first, while you can still reach it locally.

## Caddy: your own domain, your own certificate

[Caddy](https://caddyserver.com) runs next to HomeSilo, gets a certificate from
Let's Encrypt, and renews it by itself. Encryption ends on your machine, and
there's no per-request limit. You need a public IP address you can forward ports
to; if your provider uses CGNAT, this won't work.

1. Point your domain's DNS (an `A` record, plus `AAAA` for IPv6) at your home's
   public address. If that address changes, use a dynamic DNS service.
2. Forward ports 80 and 443 on your router to the HomeSilo machine.
3. Set the domain in `.env`:

   ```sh
   HOMESILO_DOMAIN=drive.example.com
   ```

4. Download the overlay and the Caddyfile, and start:

   ```sh
   curl -fsSLO https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/compose.caddy.yml
   curl -fsSLO https://raw.githubusercontent.com/OAndrija/homesilo-server/main/deploy/Caddyfile
   docker compose -f docker-compose.yml -f compose.caddy.yml up -d
   ```

The first start takes a few seconds longer while Caddy gets the certificate.
`docker compose logs caddy` shows how it went.

Your sign-in page is now on the open internet, which is what HomeSilo's sign-in
limits, invite-only sign-up and setup code are for. Keep it updated.

## Something else in front

Already running Traefik, Nginx Proxy Manager or another reverse proxy? Point it
at port 8080 and tell HomeSilo about it in `.env`:

```sh
# The proxy's address, or a range that contains it
TRUSTED_PROXIES=192.168.1.10
# The header it puts the visitor's address in
REAL_IP_HEADER=X-Forwarded-For
```

Without these, HomeSilo sees every visitor as the proxy, and one person's failed
sign-ins would lock everyone out. Make sure the proxy also sends
`X-Forwarded-Proto`, so HomeSilo knows the connection was HTTPS and marks its
cookies accordingly. Most proxies do this by default.
