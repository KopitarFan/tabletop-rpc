package dev.boardgamerpc.web

import dev.boardgamerpc.model.*
import dev.boardgamerpc.service.GameEngine
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * HTTP boundary for the MeepleRPC protocol.
 *
 * The controller deliberately delegates all rule enforcement and concurrency
 * control to [GameEngine]. It is responsible only for transport mapping and
 * discoverable OpenAPI metadata.
 */
@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class GameController(
    private val engine: GameEngine,
) {
    @GetMapping("/health")
    @Operation(operationId = "getHealth", summary = "Check service health", tags = ["system"])
    /** Reports whether the process can serve requests. */
    fun health(): Map<String, String> = mapOf("status" to "ok", "implementation" to "kotlin")

    @GetMapping("/v1/templates")
    @Operation(operationId = "listTemplates", summary = "List game templates", tags = ["templates"])
    /** Lists rules templates from which clients may create sessions. */
    fun templates(): List<TemplateSummary> = engine.templates()

    @GetMapping("/v1/templates/{id}")
    @Operation(operationId = "getTemplate", summary = "Get a game template", tags = ["templates"])
    @ApiResponse(
        responseCode = "404",
        description = "Template not found",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    /** Returns metadata for one rules template. */
    fun template(@PathVariable @Parameter(description = "Stable template identifier") id: String): TemplateSummary =
        engine.template(id)

    @PostMapping("/v1/games")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createGame", summary = "Create a game lobby", tags = ["games"])
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Game created"),
        ApiResponse(
            responseCode = "404",
            description = "Template not found",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    /** Creates a lobby and returns its initial authoritative snapshot. */
    fun create(@RequestBody request: CreateGameRequest): GameState =
        engine.create(request.templateId, request.name)

    @GetMapping("/v1/games")
    @Operation(operationId = "listGames", summary = "List game sessions", tags = ["games"])
    /** Lists the current snapshot of every in-memory game session. */
    fun games(): List<GameState> = engine.list()

    @GetMapping("/v1/games/{id}")
    @Operation(operationId = "getGame", summary = "Get authoritative game state", tags = ["games"])
    @ApiResponse(
        responseCode = "404",
        description = "Game not found",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    /** Fetches the newest snapshot, typically after a client version conflict. */
    fun game(@PathVariable @Parameter(description = "Game session UUID") id: UUID): GameState = engine.get(id)

    @PostMapping("/v1/games/{id}/players")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "joinGame", summary = "Join an open lobby", tags = ["players"])
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Player joined"),
        ApiResponse(responseCode = "404", description = "Game not found",
            content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Game started or lobby full",
            content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    /** Joins an available seat while the session remains in its lobby. */
    fun join(
        @PathVariable @Parameter(description = "Game session UUID") id: UUID,
        @RequestBody request: JoinGameRequest,
    ): GameState =
        engine.join(id, request.name)

    @PostMapping("/v1/games/{id}/commands")
    @Operation(
        operationId = "executeCommand",
        summary = "Execute an atomic game command",
        description = "Validates turn ownership and expected_version, applies the command once, " +
            "and returns the updated snapshot and produced events.",
        tags = ["commands"],
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Command applied or safely replayed"),
        ApiResponse(responseCode = "404", description = "Game not found",
            content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Rule or version conflict",
            content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "422", description = "Invalid command payload",
            content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    /** Validates and executes one versioned, idempotent unit of player intent. */
    fun command(
        @PathVariable @Parameter(description = "Game session UUID") id: UUID,
        @RequestBody command: Command,
    ): CommandResult =
        engine.execute(id, command)

    @GetMapping("/v1/games/{id}/events")
    @Operation(operationId = "listGameEvents", summary = "List events after a sequence", tags = ["events"])
    @ApiResponse(
        responseCode = "404",
        description = "Game not found",
        content = [Content(schema = Schema(implementation = ApiError::class))],
    )
    fun events(
        @PathVariable @Parameter(description = "Game session UUID") id: UUID,
        @Parameter(description = "Return events with a higher sequence")
        @RequestParam(defaultValue = "0") after: Long,
    ): List<GameEvent> = engine.events(id, after)
}
