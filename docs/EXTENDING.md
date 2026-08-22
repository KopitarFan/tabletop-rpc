# Extending the engine

## Add a model

Create one data class in `kotlin/src/main/kotlin/dev/boardgamerpc/model`. Document
its semantics with KDoc and describe every non-obvious field with Swagger's
`@Schema`. Prefer generic tabletop concepts over objects meaningful to only one
game; game-specific state belongs in `attributes` or `Board.values`.

## Add a command

1. Add the command name and example payload to `Command` and `docs/API.md`.
2. Dispatch it from `GameEngine.execute`.
3. Validate the actor, lifecycle, turn, object references, and payload before
   changing state.
4. Apply the transition and call `record` for every observable fact.
5. Return no internal mutable collections through a snapshot.
6. Add integration tests for success, validation, version conflicts, and retry.

## Add a game template

A production template abstraction should own initial state, player limits,
command validation, and win/finish detection. Tic-Tac-Toe is currently kept
inside `GameEngine` to make the beta easy to follow. When adding the second
template, extract those responsibilities behind a `GameTemplate` interface and
register implementations by stable template ID.

Avoid branching on template IDs throughout the engine. The engine should
continue to own concurrency, idempotency, event sequencing, and storage while
templates own rules.

## Preserve compatibility

Existing clients depend on snake_case JSON names even though Kotlin properties
use camelCase. Treat renaming or removing fields, commands, and events as a
breaking change. Additive optional fields and new command types are normally
backward-compatible.

