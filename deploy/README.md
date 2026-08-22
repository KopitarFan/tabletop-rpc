# Public demo deployment

The public demo runs as a single Docker container on the existing Vultr host.
Caddy terminates HTTPS and proxies `tabletoprpc.miguelrodriguez.net` to the
container's loopback-only port. Cloudflare provides DNS and may proxy the
public hostname.

## Deploy the service

Copy the repository to `/opt/tabletoprpc`, then run:

```bash
cd /opt/tabletoprpc
sudo docker compose -f deploy/compose.yaml up -d --build
curl --fail http://127.0.0.1:8080/health
```

Install `Caddyfile.tabletoprpc` in the server Caddyfile only after the DNS
record exists, validate with `sudo caddy validate --config /etc/caddy/Caddyfile`,
and reload Caddy.

## DNS

Create a proxied `A` record named `tabletoprpc` pointing to the Vultr IPv4
address. Do not publish port 8080: it is bound only to localhost.

