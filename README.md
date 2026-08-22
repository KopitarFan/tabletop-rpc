# TabletopRPC

**One protocol. Every tabletop.**

[Project website](https://kopitarfan.github.io/tabletop-rpc/) ·
[Live demo](https://tabletoprpc.miguelrodriguez.net/) ·
[Swagger API](https://tabletoprpc.miguelrodriguez.net/docs)

A game-agnostic **Kotlin/Spring Boot** web service for building online
board-game clients.

It includes a polished reference client you can play in the browser. Create a
Tic-Tac-Toe game, share its invite URL with another player, or play against an
unbeatable computer opponent. Human clicks and computer moves both submit the
same public API commands; the UI never edits board state locally.

The beta provides reusable objects for boards, spaces, pieces, cards, decks,
dice, players, turns, and custom RPG-style values. Clients submit commands
instead of trusting local state. The engine validates each move, updates the
authoritative state, and returns an ordered event stream. A complete,
server-validated Tic-Tac-Toe template demonstrates the protocol end to end.

## Why this is more than CRUD

- Optimistic concurrency rejects moves made against stale board state.
- Idempotency makes client retries safe on unreliable networks.
- Ordered events let polling, replay, and future real-time transports share a
  single synchronization model.
- Server-side randomization is ready for cards and dice.
- Immutable Kotlin data classes keep the protocol readable, typed, and easy to
  extend without burying the game rules in transport code.

## Run it

The service requires Java 21 and listens on port 8080. The included Maven
wrapper downloads the matching Maven version automatically.

```bash
# Local development
make run

# Or Docker
docker compose up --build
```

Open [http://localhost:8080/docs](http://localhost:8080/docs) for the fully
annotated Swagger UI, or consume `/openapi.json` to generate a client SDK. Then
follow the [playable API walkthrough](docs/API.md). See
[the architecture](docs/ARCHITECTURE.md) for the design rationale and extension
path.

Open [http://localhost:8080](http://localhost:8080) to play the reference
client. Invite links work anywhere the service URL is reachable, so a deployed
instance can host games between different devices.

The public demo is available at
[tabletoprpc.miguelrodriguez.net](https://tabletoprpc.miguelrodriguez.net/),
with live
[Swagger documentation](https://tabletoprpc.miguelrodriguez.net/docs).

## Documentation

- Swagger UI: `/docs`
- OpenAPI 3.1 JSON: `/openapi.json`
- [API calls and command payloads](docs/API.md)
- [Architecture and correctness properties](docs/ARCHITECTURE.md)
- [Adding models, commands, and templates](docs/EXTENDING.md)
- [Contribution workflow and code conventions](CONTRIBUTING.md)

Public Kotlin types and functions use KDoc, Kotlin's Javadoc-compatible
documentation format. Public HTTP shapes also carry Swagger annotations so the
source, IDE tooltips, generated OpenAPI contract, and interactive docs describe
the same behavior.

## Test it

```bash
make test
```

The integration suite plays a complete game, verifies win detection, exercises
command replay, rejects stale versions, and validates the generated OpenAPI
document. GitHub Actions runs it on every push and pull request.

## Repository layout

```text
kotlin/     Spring Boot service, Kotlin data models, JUnit integration suite
            and zero-build browser reference client
docs/       Protocol walkthrough and architecture notes
deploy/     Production Compose and Caddy configuration for the public demo
compose.yaml
```

## Beta scope and roadmap

This release is intentionally an in-memory single-node beta. It is ideal for
client prototyping, rules experimentation, and demonstrating the protocol—not
yet for untrusted public games.

The reference client stores a player's seat identity in browser session
storage. Player authentication and private seat tokens remain production
roadmap items; do not expose this beta to adversarial traffic.

Next production milestones are PostgreSQL event persistence, authenticated
players and game hosts, per-player state redaction for hidden cards, pluggable
rule modules, WebSocket/SSE subscriptions, and lobby discovery.

Contributions are welcome under the [MIT license](LICENSE).
