package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/**
 * Immutable authoritative snapshot returned to clients.
 *
 * [version] advances with every event. A client must send this value as a
 * command's `expected_version`; if another mutation wins first, the engine
 * rejects the stale command rather than merging ambiguous state.
 */
@Schema(description = "The latest authoritative snapshot of one game")
data class GameState(
    @field:Schema(description = "Stable game session UUID")
    val id: UUID,
    @field:JsonProperty("template_id")
    @field:Schema(example = "tic-tac-toe")
    val templateId: String,
    @field:Schema(example = "Friday game")
    val name: String,
    @field:Schema(description = "Current session lifecycle phase")
    val status: GameStatus,
    @field:Schema(description = "Monotonic concurrency and event sequence")
    val version: Long,
    @field:Schema(description = "Players ordered by seat")
    val players: List<Player>,
    @field:JsonProperty("current_player_id")
    @field:Schema(description = "Player allowed to act, or null before start/after finish")
    val currentPlayerId: UUID?,
    @field:Schema(description = "Complete generic tabletop state")
    val board: Board,
    @field:JsonProperty("created_at")
    val createdAt: Instant,
    @field:JsonProperty("updated_at")
    val updatedAt: Instant,
)
