package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A card value usable in draw piles, discard piles, and player hands.
 * [suit] and [rank] are optional because many tabletop cards do not use them.
 */
@Schema(description = "A card with common fields plus extensible attributes")
data class Card(
    @field:Schema(example = "ace-spades")
    val id: String,
    @field:Schema(example = "Ace of Spades")
    val name: String,
    @field:Schema(example = "spades")
    val suit: String? = null,
    @field:Schema(example = "ace")
    val rank: String? = null,
    @field:Schema(description = "Template-specific rules text, cost, tags, or effects")
    val attributes: Map<String, Any?> = emptyMap(),
)
