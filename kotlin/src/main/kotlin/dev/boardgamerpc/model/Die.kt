package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/** A die whose random value is generated authoritatively by the server. */
@Schema(description = "A server-rolled die and its most recent value")
data class Die(
    @field:Schema(example = "d20")
    val id: String,
    @field:Schema(minimum = "2", maximum = "1000", example = "20")
    val sides: Int = 6,
    @field:Schema(description = "Most recent roll, or null before the first roll", nullable = true)
    val value: Int? = null,
)
