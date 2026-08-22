# API calls

The Kotlin service exposes its JSON API at `http://localhost:8080`.
Interactive Swagger UI is available at `/docs`, and the machine-readable
OpenAPI 3 specification is available at `/openapi.json`.

The playable Tic-Tac-Toe reference client is served from `/`, and Blackjack is
served from `/blackjack.html`. Their activity panels show the same public calls
documented below while you play.

## Resources

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness and implementation name |
| `GET` | `/v1/templates` | Discover available game templates |
| `GET` | `/v1/templates/{id}` | Read template metadata |
| `POST` | `/v1/games` | Create a game from a template |
| `GET` | `/v1/games` | List game sessions |
| `GET` | `/v1/games/{id}` | Fetch the latest authoritative state |
| `POST` | `/v1/games/{id}/players` | Join a lobby and receive a player ID |
| `POST` | `/v1/games/{id}/commands` | Validate and execute a move atomically |
| `GET` | `/v1/games/{id}/events?after=N` | Incrementally synchronize events |

## Play a game

Create a session:

```bash
curl -sS http://localhost:8080/v1/games \
  -H 'Content-Type: application/json' \
  -d '{"template_id":"tic-tac-toe","name":"Friday game"}'
```

The response contains the `id` and the initial `version` (`0`). Join twice:

```bash
curl -sS http://localhost:8080/v1/games/GAME_ID/players \
  -H 'Content-Type: application/json' -d '{"name":"Ada"}'

curl -sS http://localhost:8080/v1/games/GAME_ID/players \
  -H 'Content-Type: application/json' -d '{"name":"Grace"}'
```

Start after both players join. Use the latest returned version (here, `2`):

```bash
curl -sS http://localhost:8080/v1/games/GAME_ID/commands \
  -H 'Content-Type: application/json' \
  -d '{
    "type":"start_game",
    "actor_id":"PLAYER_ID",
    "payload":{},
    "expected_version":2,
    "idempotency_key":"start-001"
  }'
```

Place a mark using the version returned by the start command:

```bash
curl -sS http://localhost:8080/v1/games/GAME_ID/commands \
  -H 'Content-Type: application/json' \
  -d '{
    "type":"place_piece",
    "actor_id":"PLAYER_ID",
    "payload":{"space_id":"0-0"},
    "expected_version":3,
    "idempotency_key":"turn-001"
  }'
```

Get only events after sequence 3:

```bash
curl -sS 'http://localhost:8080/v1/games/GAME_ID/events?after=3'
```

## Command catalog

Commands all use the same envelope. `actor_id` identifies the player,
`expected_version` prevents a stale client from overwriting a newer move, and
`idempotency_key` makes a retry safe.

| Command | Payload | Resulting event | Intended use |
|---|---|---|---|
| `start_game` | `{}` | `game_started` | Lock the lobby and choose the first turn |
| `hit` | `{}` | `card_dealt` | Deal one Blackjack card to the acting player |
| `stand` | `{}` | `game_finished` | Resolve the Blackjack dealer and outcome |
| `place_piece` | `{"space_id":"0-0"}` | `piece_placed` | Chess placement, Go stones, tokens |
| `move_piece` | `{"piece_id":"pawn-1","to":"b4"}` | `piece_moved` | Grid, graph, or zone movement |
| `draw_card` | `{"deck_id":"draw"}` | `card_drawn` | Move the top card to a private hand |
| `play_card` | `{"deck_id":"draw","card_id":"ace-spades"}` | `card_played` | Move a hand card to discard |
| `shuffle_deck` | `{"deck_id":"draw"}` | `deck_shuffled` | Server-authoritative randomization |
| `roll_dice` | `{"dice_ids":["d20","damage-d8"]}` | `dice_rolled` | Server-authoritative random rolls |
| `set_value` | `{"key":"round","value":2}` | `value_set` | RPG stats, clocks, scores, custom state |
| `end_turn` | `{}` | `turn_changed` | Explicit turn advancement |

Every successful command returns:

```json
{
  "state": { "id": "...", "version": 5, "board": {} },
  "events": [{ "sequence": 5, "type": "piece_moved", "data": {} }],
  "replayed": false
}
```

Retrying the same `idempotency_key` returns the original result with
`replayed: true`. Sending a different command with an old version returns HTTP
`409` and the current version, prompting the client to refresh.

## Blackjack example

Create a game with `"template_id":"blackjack"`, join once, and send
`start_game`. While the game is active, submit either `hit` or `stand` through
the same command endpoint. The server owns shuffling, dealing, ace valuation,
dealer drawing, busts, pushes, and the final outcome.

This demo exposed an important next protocol requirement: the generic snapshot
currently contains the complete deck and every hand. The web client hides the
dealer's hole card visually, but a production card game needs actor-specific
state projections so unauthorized clients never receive hidden cards. That
projection boundary is the next service capability to design.
