# IMAYX Host v2

Standalone Android hosting app. WhatsRaX is not included.

## What is actually implemented

1. ZIP website import into the app sandbox.
2. Safe ZIP extraction with path-traversal protection.
3. A lightweight local static HTTP server on `127.0.0.1:3000`.
4. Static MIME handling for HTML/CSS/JS/JSON/SVG/images/fonts/PDF.
5. SPA fallback to `index.html`.
6. Foreground hosting service.
7. Real Cloudflare `cloudflared` process controller.
8. ARM64 `cloudflared` download from the official Cloudflare GitHub release API.
9. Quick Tunnel mode.
10. Named Tunnel token mode.
11. Persistent project/token/port settings.

## Connect imayx.in

For a temporary public URL:
- Install cloudflared in the app.
- Keep Quick Tunnel ON.
- Start Host.
- cloudflared creates a `https://*.trycloudflare.com` URL.

For `https://imayx.in`:
1. In Cloudflare Zero Trust create a Tunnel.
2. Add a Public Hostname for `imayx.in`.
3. Set the service to `http://127.0.0.1:3000`.
4. Copy the tunnel token.
5. In IMAYX Host turn Quick Tunnel OFF.
6. Paste the token.
7. Import the website ZIP and set its project name.
8. Start Host.

The app starts:
`127.0.0.1:3000 -> cloudflared -> Cloudflare -> imayx.in`

## Important

Cloudflare DNS/hostname configuration is account-side configuration and is not automatically changed by the app.

Node.js is intentionally a separate runtime module. A normal Linux Node binary must NOT be assumed to execute on Android. The next runtime module should be an Android-compatible ARM64 Node build published as a GitHub Release artifact.
# ayxhost
