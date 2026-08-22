package dev.boardgamerpc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/** Request body for creating a named lobby from registered rules. */
@Schema(description = "Creates a lobby from a registered game template")
data class CreateGameRequest(
    @field:JsonProperty("template_id")
    @field:Schema(example = "tic-tac-toe", defaultValue = "tic-tac-toe")
    val templateId: String = "tic-tac-toe",
    @field:Schema(
        example = "Friday game",
        minLength = 1,
        maxLength = 100,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val name: String,
)
