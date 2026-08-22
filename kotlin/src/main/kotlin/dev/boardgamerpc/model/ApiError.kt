package dev.boardgamerpc.model

import io.swagger.v3.oas.annotations.media.Schema

/** Stable error envelope used for lookup, validation, and rule failures. */
@Schema(description = "Error response returned when a request cannot be applied")
data class ApiError(
    @field:Schema(description = "String explanation or structured conflict detail", example = "Game not found")
    val detail: Any,
)
