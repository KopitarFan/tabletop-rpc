package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Server-authoritative card container.
 *
 * The last item in [drawPile] is the top card. Hand keys are player UUIDs
 * encoded as strings. Templates with hidden information can return a
 * player-specific projection that omits other hands and the draw pile.
 */
@Schema(description = "Server-ordered card piles and player hands")
data class Deck(
    @field:Schema(example = "draw")
    val id: String,
    @field:JsonProperty("draw_pile")
    @field:Schema(description = "Ordered cards; the final item is drawn first")
    val drawPile: MutableList<Card> = mutableListOf(),
    @field:JsonProperty("discard_pile")
    @field:Schema(description = "Cards that have been played or discarded")
    val discardPile: MutableList<Card> = mutableListOf(),
    @field:Schema(description = "Player UUID to cards currently held")
    val hands: MutableMap<String, MutableList<Card>> = mutableMapOf(),
)
