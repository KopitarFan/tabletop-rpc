package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/** Lifecycle phases understood by the generic session API. */
@Schema(description = "Lifecycle of a game session")
enum class GameStatus {
    /** Players may join, but gameplay commands are not yet accepted. */
    LOBBY,
    /** Rules and turn ownership are actively enforced. */
    ACTIVE,
    /** The game reached a terminal win or draw state. */
    FINISHED,
}
