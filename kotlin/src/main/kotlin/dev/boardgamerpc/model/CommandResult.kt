package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Successful command response.
 *
 * [events] contains only facts emitted by this command, while [state] is the
 * full snapshot after all those events have been applied.
 */
@Schema(description = "Updated state plus exactly the events produced by a command")
data class CommandResult(
    val state: GameState,
    val events: List<GameEvent>,
    @field:Schema(description = "True when returning a cached retry result")
    val replayed: Boolean = false,
)
