package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * A participant in a game session.
 *
 * @property id opaque server-generated identity used by commands and ownership.
 * @property name human-readable display name supplied when joining.
 * @property seat zero-based, stable position used for turn order and player roles.
 * @property attributes game-specific public player metadata such as color or score.
 */
@Schema(description = "A participant and their stable seat in a game")
data class Player(
    @field:Schema(description = "Server-assigned player identifier")
    val id: UUID = UUID.randomUUID(),
    @field:Schema(example = "Ada")
    val name: String,
    @field:Schema(description = "Zero-based turn-order seat", example = "0")
    val seat: Int,
    @field:Schema(description = "Template-specific public player metadata")
    val attributes: Map<String, Any?> = emptyMap(),
)
