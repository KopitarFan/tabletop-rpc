package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * A token, pawn, miniature, marker, or other object placed on a board.
 * Ownership and location are nullable to support neutral and off-board pieces.
 */
@Schema(description = "A movable or placeable object on the board")
data class Piece(
    @field:Schema(example = "mark-3")
    val id: String,
    @field:Schema(example = "X")
    val kind: String,
    @field:JsonProperty("owner_id")
    @field:Schema(description = "Owning player, or null for a neutral piece")
    val ownerId: UUID? = null,
    @field:Schema(example = "0-0")
    val location: String? = null,
    @field:Schema(description = "Template-specific piece state such as health or orientation")
    val attributes: Map<String, Any?> = emptyMap(),
)
