# Public deployment

The public beta is available at:

- Reference client: <https://tabletoprpc.miguelrodriguez.net/>
- Swagger UI: <https://tabletoprpc.miguelrodriguez.net/docs>
- OpenAPI JSON: <https://tabletoprpc.miguelrodriguez.net/openapi.json>
- Project website: <https://kopitarfan.github.io/tabletop-rpc/>

## Topology

Cloudflare provides authoritative DNS, edge TLS, and HTTP proxying. Requests
are forwarded over HTTPS using Full (strict) validation to Caddy on a Vultr
server. Caddy terminates the origin connection and proxies to the application
container on `127.0.0.1:8080`.

The loopback binding deliberately keeps Spring Boot off the server's public
network interfaces. The production Compose and Caddy configurations live in
[`deploy/`](../deploy/).

## Deploying an update

Copy the repository to `/opt/tabletoprpc` on the host and rebuild the service:

```bash
cd /opt/tabletoprpc
sudo docker compose -f deploy/compose.yaml up -d --build
curl --fail http://127.0.0.1:8080/health
```

Then verify all public surfaces:

```bash
curl --fail https://tabletoprpc.miguelrodriguez.net/health
curl --fail https://tabletoprpc.miguelrodriguez.net/openapi.json
```

## Beta durability warning

Game sessions are currently stored in memory. Rebuilding or restarting the
container clears active games and invite links. This is intentional for the
beta and keeps the reference implementation understandable. Persistent event
storage is the next major production milestone.

## TLS and DNS

The `tabletoprpc` DNS record is proxied by Cloudflare. Cloudflare SSL/TLS mode
must remain **Full (strict)** because Caddy maintains a publicly trusted origin
certificate. Mail and portfolio records in the same zone are independent of
the game service and should not be changed during an application deployment.
