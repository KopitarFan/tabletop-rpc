package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Generic tabletop state shared by board, card, dice, and role-playing games.
 *
 * Object maps are keyed by the object's own stable ID for efficient client
 * lookup. [values] is the deliberate extension point for template-specific
 * state that does not warrant a universal engine model.
 */
@Schema(description = "Generic container for common tabletop objects and custom values")
data class Board(
    @field:Schema(description = "Space ID to board location")
    val spaces: Map<String, Space> = emptyMap(),
    @field:Schema(description = "Piece ID to placed or off-board object")
    val pieces: Map<String, Piece> = emptyMap(),
    @field:Schema(description = "Deck ID to card container")
    val decks: Map<String, Deck> = emptyMap(),
    @field:Schema(description = "Die ID to server-owned die")
    val dice: Map<String, Die> = emptyMap(),
    @field:Schema(description = "Template-specific scores, clocks, flags, and RPG state")
    val values: Map<String, Any?> = emptyMap(),
)
