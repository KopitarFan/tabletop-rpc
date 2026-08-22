package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * Atomic player intent submitted against a known game version.
 *
 * Payload shape depends on [type]. See `docs/API.md` for the command catalog.
 * The [idempotencyKey] identifies the logical operation, not an HTTP attempt:
 * every retry of the same operation must reuse it.
 */
@Schema(description = "An atomic player intent submitted against a known game version")
data class Command(
    @field:Schema(
        example = "place_piece",
        allowableValues = ["start_game", "place_piece", "move_piece", "draw_card", "play_card",
            "shuffle_deck", "roll_dice", "set_value", "end_turn"],
    )
    val type: String,
    @field:JsonProperty("actor_id")
    @field:Schema(description = "Player performing the action; optional for system commands")
    val actorId: UUID? = null,
    @field:Schema(
        description = "Command-specific JSON object",
        example = "{\"space_id\":\"0-0\"}",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val payload: JsonNode,
    @field:JsonProperty("expected_version")
    @field:Schema(description = "Latest version observed by the client", minimum = "0")
    val expectedVersion: Long,
    @field:JsonProperty("idempotency_key")
    @field:Schema(example = "turn-001", description = "Client-generated key that makes retries safe")
    val idempotencyKey: String,
)
