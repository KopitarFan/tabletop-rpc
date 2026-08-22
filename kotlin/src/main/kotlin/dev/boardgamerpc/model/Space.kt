package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * A named board location.
 *
 * Spaces work for grids, zones, rooms, or arbitrary graphs. Rules may use
 * [neighbors] to constrain movement without assuming rectangular coordinates.
 */
@Schema(description = "A board location, optionally connected as part of a graph")
data class Space(
    @field:Schema(example = "0-0")
    val id: String,
    @field:Schema(example = "Row 1, Column 1")
    val name: String,
    @field:Schema(description = "IDs of directly connected spaces")
    val neighbors: List<String> = emptyList(),
    @field:Schema(description = "Template-specific terrain, cost, or zone metadata")
    val attributes: Map<String, Any?> = emptyMap(),
)
