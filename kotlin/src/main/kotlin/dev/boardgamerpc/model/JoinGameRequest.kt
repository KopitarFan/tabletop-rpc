package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/** Request body for claiming the next available seat in a lobby. */
@Schema(description = "Adds a named player to an open lobby")
data class JoinGameRequest(
    @field:Schema(example = "Ada", minLength = 1, maxLength = 60, requiredMode = Schema.RequiredMode.REQUIRED)
    val name: String,
)
