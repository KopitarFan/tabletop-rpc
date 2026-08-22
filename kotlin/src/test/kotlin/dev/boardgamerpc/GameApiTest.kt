package dev.boardgamerpc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class GameApiTest(
    @param:Autowired private val mvc: MockMvc,
    @param:Autowired private val mapper: ObjectMapper,
) {
    @Test
    fun `serves the playable reference client`() {
        mvc.get("/").andExpect {
            status { isOk() }
            forwardedUrl("index.html")
        }
        mvc.get("/index.html").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Every move is")) }
        }
        mvc.get("/app.js").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/javascript") }
        }
        mvc.get("/styles.css").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/css") }
        }
        mvc.get("/blackjack.html").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Beat the dealer")) }
        }
        mvc.get("/blackjack.js").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/javascript") }
        }
        mvc.get("/ludo.html").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Roll. Race.")) }
        }
        mvc.get("/ludo.js").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/javascript") }
        }
    }

    @Test
    fun `exposes Swagger and OpenAPI documentation`() {
        mvc.get("/docs").andExpect { status { is3xxRedirection() } }
        val result = mvc.get("/openapi.json").andExpect { status { isOk() } }.andReturn()
        val specification = mapper.readTree(result.response.contentAsString)
        assertThat(specification.at("/info/title").asText()).isEqualTo("TabletopRPC")
        assertThat(specification.at("/paths/~1v1~1games~1{id}~1commands/post/operationId").asText())
            .isEqualTo("executeCommand")
        assertThat(specification.at("/components/schemas/Board/description").asText())
            .contains("Generic container")
        assertThat(specification.at("/paths/~1v1~1games~1{id}~1commands/post/responses/409/content/application~1json/schema/\$ref").asText())
            .endsWith("/ApiError")
    }

    @Test
    fun `plays a complete Tic-Tac-Toe game and safely replays a command`() {
        val game = request("/v1/games", """{"name":"Friday game"}""", 201)
        val id = game["id"].asText()
        val one = request("/v1/games/$id/players", """{"name":"Ada"}""", 201)
        val two = request("/v1/games/$id/players", """{"name":"Grace"}""", 201)
        val playerOne = one.at("/players/0/id").asText()
        val playerTwo = two.at("/players/1/id").asText()
        var result = command(id, "start_game", playerOne, two["version"].asLong(), "{}", "start")
        var version = result.at("/state/version").asLong()
        val moves = listOf(
            playerOne to "0-0", playerTwo to "1-0", playerOne to "0-1",
            playerTwo to "1-1", playerOne to "0-2",
        )
        moves.forEachIndexed { index, (player, space) ->
            result = command(id, "place_piece", player, version,
                """{"space_id":"$space"}""", "move-$index")
            version = result.at("/state/version").asLong()
        }
        assertThat(result.at("/state/status").asText()).isEqualTo("FINISHED")
        assertThat(result.at("/state/board/values/winner").asText()).isEqualTo(playerOne)
        val replay = command(id, "place_piece", playerOne, 0,
            """{"space_id":"0-2"}""", "move-4")
        assertThat(replay["replayed"].asBoolean()).isTrue()
    }

    @Test
    fun `rejects a stale command`() {
        val game = request("/v1/games", """{"name":"Conflict"}""", 201)
        val result = mvc.post("/v1/games/${game["id"].asText()}/commands") {
            contentType = MediaType.APPLICATION_JSON
            content = """{
                "type":"start_game", "expected_version":99,
                "idempotency_key":"stale", "payload":{}
            }"""
        }.andExpect { status { isConflict() } }.andReturn()
        assertThat(mapper.readTree(result.response.contentAsString).at("/detail/actual").asLong()).isZero()
    }

    @Test
    fun `plays Blackjack through the same command endpoint`() {
        val templates = mvc.get("/v1/templates").andExpect { status { isOk() } }.andReturn()
        assertThat(mapper.readTree(templates.response.contentAsString).map { it["id"].asText() })
            .contains("blackjack")

        val game = request("/v1/games", """{"template_id":"blackjack","name":"Ada at the table"}""", 201)
        val id = game["id"].asText()
        val joined = request("/v1/games/$id/players", """{"name":"Ada"}""", 201)
        val player = joined.at("/players/0/id").asText()
        val started = command(id, "start_game", player, joined["version"].asLong(), "{}", "blackjack-start")
        assertThat(started.at("/state/board/decks/shoe/hands/$player").size()).isEqualTo(2)
        assertThat(started.at("/state/board/decks/shoe/hands/dealer").size()).isEqualTo(2)

        if (started.at("/state/status").asText() == "ACTIVE") {
            val stood = command(
                id, "stand", player, started.at("/state/version").asLong(), "{}", "blackjack-stand",
            )
            assertThat(stood.at("/state/status").asText()).isEqualTo("FINISHED")
            assertThat(stood.at("/state/board/values/outcome").asText())
                .isIn("PLAYER_WIN", "DEALER_WIN", "PUSH")
            assertThat(stood.at("/state/board/values/dealer_revealed").asBoolean()).isTrue()
        }
    }

    @Test
    fun `starts Mini Ludo and rolls its server authoritative die`() {
        val game = request("/v1/games", """{"template_id":"mini-ludo","name":"Race"}""", 201)
        val id = game["id"].asText()
        val one = request("/v1/games/$id/players", """{"name":"Ada"}""", 201)
        val two = request("/v1/games/$id/players", """{"name":"Computer"}""", 201)
        val player = one.at("/players/0/id").asText()
        val started = command(id, "start_game", player, two["version"].asLong(), "{}", "ludo-start")
        assertThat(started.at("/state/board/pieces").size()).isEqualTo(4)
        assertThat(started.at("/state/board/dice/ludo-d6/sides").asInt()).isEqualTo(6)

        val rolled = command(
            id, "roll_dice", player, started.at("/state/version").asLong(),
            """{"dice_ids":["ludo-d6"]}""", "ludo-roll",
        )
        assertThat(rolled.at("/state/board/dice/ludo-d6/value").asInt()).isBetween(1, 6)
        val hasMove = rolled.at("/state/board/values/movable_piece_ids").size() > 0
        val turnAdvanced = rolled.at("/state/current_player_id").asText() != player
        assertThat(hasMove || turnAdvanced).isTrue()
    }

    private fun command(
        gameId: String, type: String, actor: String, version: Long, payload: String, key: String,
    ): JsonNode = request(
        "/v1/games/$gameId/commands",
        """{
            "type":"$type", "actor_id":"$actor", "expected_version":$version,
            "idempotency_key":"$key", "payload":$payload
        }""",
        200,
    )

    private fun request(path: String, body: String, expectedStatus: Int): JsonNode {
        val result = mvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isEqualTo(expectedStatus) } }.andReturn()
        return mapper.readTree(result.response.contentAsString)
    }
}
