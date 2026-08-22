package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/** Public catalog entry for a server-supported rules template. */
@Schema(description = "Discoverable metadata for a supported rules template")
data class TemplateSummary(
    @field:Schema(example = "tic-tac-toe")
    val id: String,
    @field:Schema(example = "Tic-Tac-Toe")
    val name: String,
    @field:JsonProperty("min_players")
    @field:Schema(description = "Minimum players required to start", minimum = "1")
    val minPlayers: Int,
    @field:JsonProperty("max_players")
    @field:Schema(description = "Maximum lobby capacity", minimum = "1")
    val maxPlayers: Int,
    @field:Schema(description = "Human-readable overview of the rules")
    val description: String,
)
