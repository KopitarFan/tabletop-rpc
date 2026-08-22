package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/**
 * Immutable fact emitted after a successful state transition.
 * [sequence] is both the event offset and resulting game version.
 */
@Schema(description = "An immutable, ordered fact produced by the engine")
data class GameEvent(
    val id: UUID = UUID.randomUUID(),
    @field:JsonProperty("game_id")
    val gameId: UUID,
    @field:Schema(description = "Monotonic per-game event offset", minimum = "1")
    val sequence: Long,
    @field:Schema(example = "piece_placed")
    val type: String,
    @field:JsonProperty("actor_id")
    val actorId: UUID?,
    @field:Schema(description = "Event-specific immutable facts")
    val data: Map<String, Any?> = emptyMap(),
    @field:JsonProperty("occurred_at")
    val occurredAt: Instant = Instant.now(),
)
