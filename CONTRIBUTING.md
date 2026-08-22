# Contributing

Thanks for helping make the Board Game RPC Engine useful to more game and
client authors.

## Development setup

You need Java 21. The Maven wrapper supplies the correct Maven version.

```bash
make test
make run
```

Before opening a pull request, run `make test` and inspect Swagger UI at
`http://localhost:8080/docs`. Changes to JSON fields, endpoint paths, command
payloads, event types, or error shapes are protocol changes and should include
updated integration tests and `docs/API.md` examples.

## Code conventions

- Use Kotlin data classes for immutable wire/domain values and keep one public
  model per file.
- Put state transitions in `GameEngine`, not controllers.
- Return copied snapshots; never expose the internal mutable aggregate.
- Every mutation must emit an event and advance the same version sequence.
- Preserve idempotency checks before optimistic version checks so retries of a
  completed command continue to work.
- Add KDoc to public types and functions, and explain non-obvious invariants on
  private implementation code.
- Add `@Schema`, `@Operation`, and documented error responses when changing the
  public contract.

## Pull requests

Keep changes focused and describe the client-visible behavior. New commands
should include successful, invalid-payload, wrong-turn, stale-version, and
idempotent-retry coverage where applicable.

