# API calls

The Kotlin service exposes its JSON API at `http://localhost:8080`.
Interactive Swagger UI is available at `/docs`, and the machine-readable
OpenAPI 3 specification is available at `/openapi.json`.

The six playable reference clients are served from `/`, `/blackjack.html`,
`/ludo.html`, `/checkers.html`, `/holdem.html`, and `/color-clash.html`. They show
the same public calls documented below while you play.

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
| `poker_action` | `{"action":"check"}` | `poker_action_taken` | Check, bet, call, or fold in heads-up Hold'em |
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

This first card demo exposed the need for actor-specific state. Hold'em and
Color Clash now exercise that projection mechanism, while Blackjack remains a
single-human game with a server-controlled dealer.

## Mini Ludo example

Create a game with `"template_id":"mini-ludo"`, join two players, and send
`start_game`. Each turn begins with `roll_dice` and
`{"dice_ids":["ludo-d6"]}`. If `board.values.movable_piece_ids` is non-empty,
choose one and submit `move_piece` with its `piece_id`. The server calculates
the destination, enforces six-to-enter and exact-home rules, resolves captures,
awards extra turns, and detects the winner.

Ludo exposed a second design pressure: a command name such as `move_piece` has
different payload and validation rules depending on the template. Clients need
machine-readable, template-specific command schemas and current legal-action
hints instead of relying on a global prose command catalog.

## Checkers, Hold'em, and Color Clash

- Checkers returns `board.values.legal_actions`, including forced captures and
  chained jumps. The client renders those actions instead of duplicating rules.
- Heads-up Hold'em progresses through preflop, flop, turn, river, and showdown
  with fixed ten-chip bets and complete seven-card hand evaluation.
- Color Clash is an original shedding game. `play_card` accepts
  `chosen_color` for wild cards; skip, reverse, and draw-two cards alter the
  normal turn sequence.

Private card games can request a player-specific projection:

```http
GET /v1/games/{gameId}?viewer_id={playerId}
```

Before a Hold'em showdown, the response includes only that player's hole cards
and public community cards. Color Clash similarly hides the opponent's hand
and draw pile while publishing hand counts. `viewer_id` selects a projection;
it is not authentication. A production deployment must bind viewer identity to
authenticated player credentials before treating this as a security boundary.
