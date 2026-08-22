package dev.boardgamerpc.service

import com.fasterxml.jackson.databind.JsonNode
import dev.boardgamerpc.model.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Collections
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative, thread-safe command processor for board-game sessions.
 *
 * Clients never mutate [GameState] directly. They submit a [Command] containing
 * the last version they observed and a unique idempotency key. Each game is
 * locked while a command is validated, applied, recorded as events, and copied
 * into a response snapshot. This prevents two concurrent clients from silently
 * overwriting one another and makes network retries safe.
 *
 * The beta stores games in memory. Persistence can be introduced behind this
 * service without changing the HTTP command envelope.
 *
 * @param random source of server-authoritative randomness; injectable for tests.
 */
@Service
class GameEngine(
    private val random: Random = Random(),
) {
    private val games = ConcurrentHashMap<UUID, MutableGame>()

    /** Returns metadata for every rules template that can create a game. */
    fun templates(): List<TemplateSummary> = TEMPLATES

    /**
     * Finds a registered template.
     * @throws ApiException with HTTP 404 semantics when [id] is unknown.
     */
    fun template(id: String): TemplateSummary =
        TEMPLATES.firstOrNull { it.id == id }
            ?: fail(HttpStatus.NOT_FOUND, "Template not found")

    /**
     * Creates a new lobby from [templateId].
     * @return an isolated authoritative snapshot at version zero.
     */
    fun create(templateId: String, name: String): GameState {
        template(templateId)
        if (name.isBlank()) fail(HttpStatus.UNPROCESSABLE_ENTITY, "name is required")
        return MutableGame(templateId, name).also { games[it.id] = it }.snapshot()
    }

    /** Returns snapshots of all sessions currently held by this process. */
    fun list(): List<GameState> = games.values.map { game ->
        synchronized(game) { game.snapshot() }
    }

    /** Returns the latest authoritative snapshot for [id]. */
    fun get(id: UUID): GameState = requireGame(id).let { game ->
        synchronized(game) { game.snapshot() }
    }

    /**
     * Adds a player to an open lobby and assigns the next zero-based seat.
     * Joining is serialized with commands so capacity cannot be overbooked.
     */
    fun join(id: UUID, name: String): GameState {
        val game = requireGame(id)
        return synchronized(game) {
            if (game.status != GameStatus.LOBBY) {
                fail(HttpStatus.CONFLICT, "Players can only join while the game is in the lobby")
            }
            if (game.players.size >= template(game.templateId).maxPlayers) fail(HttpStatus.CONFLICT, "Game is full")
            if (name.isBlank()) fail(HttpStatus.UNPROCESSABLE_ENTITY, "name is required")
            val player = Player(name = name, seat = game.players.size)
            game.players += player
            game.record("player_joined", player.id, mapOf("name" to name))
            game.snapshot()
        }
    }

    /**
     * Returns events whose sequence is strictly greater than [after].
     * Clients can persist their last sequence and use this method for polling.
     */
    fun events(id: UUID, after: Long): List<GameEvent> {
        val game = requireGame(id)
        return synchronized(game) { game.events.filter { it.sequence > after }.toList() }
    }

    /**
     * Applies [command] atomically to game [id].
     *
     * A previously seen idempotency key returns its original result before the
     * version check. A new command must match the current version exactly.
     *
     * @return the resulting snapshot and only the events produced by this command.
     * @throws ApiException when the game, version, actor, turn, or payload is invalid.
     */
    fun execute(id: UUID, command: Command): CommandResult {
        val game = requireGame(id)
        return synchronized(game) {
            game.replays[command.idempotencyKey]?.let { return@synchronized it.copy(replayed = true) }
            if (command.expectedVersion != game.version) {
                fail(
                    HttpStatus.CONFLICT,
                    mapOf(
                        "message" to "Version conflict",
                        "expected" to command.expectedVersion,
                        "actual" to game.version,
                    ),
                )
            }
            val before = game.events.size
            when (command.type) {
                "start_game" -> start(game, command)
                "place_piece" -> place(game, command)
                "hit" -> hit(game, command)
                "stand" -> stand(game, command)
                "move_piece" -> move(game, command)
                "draw_card" -> draw(game, command)
                "play_card" -> play(game, command)
                "shuffle_deck" -> shuffle(game, command)
                "roll_dice" -> roll(game, command)
                "set_value" -> setValue(game, command)
                "end_turn" -> advance(game, command.actorId)
                else -> fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown command type: ${command.type}")
            }
            CommandResult(
                state = game.snapshot(),
                events = game.events.subList(before, game.events.size).toList(),
            ).also { game.replays[command.idempotencyKey] = it }
        }
    }

    private fun start(game: MutableGame, command: Command) {
        if (game.status != GameStatus.LOBBY) fail(HttpStatus.CONFLICT, "Game is not in the lobby")
        when (game.templateId) {
            TIC_TAC_TOE.id -> startTicTacToe(game, command)
            BLACKJACK.id -> startBlackjack(game, command)
            else -> fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported template: ${game.templateId}")
        }
    }

    private fun startTicTacToe(game: MutableGame, command: Command) {
        if (game.players.size != 2) fail(HttpStatus.CONFLICT, "Tic-Tac-Toe requires exactly two lobby players")
        game.status = GameStatus.ACTIVE
        game.currentPlayerId = game.players.first().id
        game.record("game_started", command.actorId)
    }

    private fun startBlackjack(game: MutableGame, command: Command) {
        if (game.players.size != 1) fail(HttpStatus.CONFLICT, "Blackjack requires exactly one player")
        if (command.actorId != game.players.first().id) fail(HttpStatus.FORBIDDEN, "Actor is not a player in this game")
        val deck = Deck(id = BLACKJACK_DECK, drawPile = standardDeck().toMutableList())
        Collections.shuffle(deck.drawPile, random)
        game.decks[BLACKJACK_DECK] = deck
        game.status = GameStatus.ACTIVE
        game.currentPlayerId = game.players.first().id
        repeat(2) {
            deal(deck, game.players.first().id.toString())
            deal(deck, DEALER_HAND)
        }
        updateBlackjackValues(game, revealDealer = false)
        game.record("game_started", command.actorId, mapOf("template_id" to BLACKJACK.id))
        if (blackjackTotal(deck.hands.getValue(game.players.first().id.toString())) == 21) resolveBlackjack(game, command.actorId)
    }

    private fun place(game: MutableGame, command: Command) {
        if (game.templateId != TIC_TAC_TOE.id) fail(HttpStatus.UNPROCESSABLE_ENTITY, "place_piece is not supported by this template")
        val actor = requireTurn(game, command.actorId)
        val spaceId = text(command.payload, "space_id")
        if (spaceId !in game.spaces) fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown space: $spaceId")
        if (game.pieces.values.any { it.location == spaceId }) fail(HttpStatus.CONFLICT, "Space is occupied")

        val piece = Piece(
            id = "mark-${game.version + 1}",
            kind = if (actor.seat == 0) "X" else "O",
            ownerId = actor.id,
            location = spaceId,
        )
        game.pieces[piece.id] = piece
        game.record("piece_placed", actor.id, mapOf("piece" to piece))

        val winner = winner(game)
        if (winner != null || game.pieces.size == 9) {
            game.status = GameStatus.FINISHED
            game.values["winner"] = winner?.toString()
            game.values["draw"] = winner == null
            game.record(
                "game_finished",
                actor.id,
                mapOf("winner" to winner?.toString(), "draw" to (winner == null)),
            )
        } else {
            advance(game, actor.id)
        }
    }

    private fun hit(game: MutableGame, command: Command) {
        if (game.templateId != BLACKJACK.id) fail(HttpStatus.UNPROCESSABLE_ENTITY, "hit is only supported by Blackjack")
        val actor = requireTurn(game, command.actorId)
        val deck = game.decks.getValue(BLACKJACK_DECK)
        val card = deal(deck, actor.id.toString())
        game.record("card_dealt", actor.id, mapOf("recipient" to "player", "card" to card))
        updateBlackjackValues(game, revealDealer = false)
        val total = blackjackTotal(deck.hands.getValue(actor.id.toString()))
        if (total >= 21) resolveBlackjack(game, actor.id)
    }

    private fun stand(game: MutableGame, command: Command) {
        if (game.templateId != BLACKJACK.id) fail(HttpStatus.UNPROCESSABLE_ENTITY, "stand is only supported by Blackjack")
        val actor = requireTurn(game, command.actorId)
        resolveBlackjack(game, actor.id)
    }

    private fun resolveBlackjack(game: MutableGame, actorId: UUID?) {
        val deck = game.decks.getValue(BLACKJACK_DECK)
        val playerHand = deck.hands.getValue(game.players.first().id.toString())
        val dealerHand = deck.hands.getValue(DEALER_HAND)
        if (blackjackTotal(playerHand) <= 21) {
            while (blackjackTotal(dealerHand) < 17) {
                val card = deal(deck, DEALER_HAND)
                game.record("card_dealt", null, mapOf("recipient" to "dealer", "card" to card))
            }
        }
        val playerTotal = blackjackTotal(playerHand)
        val dealerTotal = blackjackTotal(dealerHand)
        val outcome = when {
            playerTotal > 21 -> "DEALER_WIN"
            dealerTotal > 21 -> "PLAYER_WIN"
            playerTotal > dealerTotal -> "PLAYER_WIN"
            dealerTotal > playerTotal -> "DEALER_WIN"
            else -> "PUSH"
        }
        game.status = GameStatus.FINISHED
        game.currentPlayerId = null
        game.values["outcome"] = outcome
        updateBlackjackValues(game, revealDealer = true)
        game.record("game_finished", actorId, mapOf("outcome" to outcome, "player_total" to playerTotal, "dealer_total" to dealerTotal))
    }

    private fun deal(deck: Deck, hand: String): Card {
        val card = deck.drawPile.removeLastOrNull() ?: fail(HttpStatus.CONFLICT, "Deck is empty")
        deck.hands.getOrPut(hand) { mutableListOf() } += card
        return card
    }

    private fun updateBlackjackValues(game: MutableGame, revealDealer: Boolean) {
        val deck = game.decks.getValue(BLACKJACK_DECK)
        val playerHand = deck.hands.getValue(game.players.first().id.toString())
        val dealerHand = deck.hands.getValue(DEALER_HAND)
        game.values["player_total"] = blackjackTotal(playerHand)
        game.values["dealer_total"] = if (revealDealer) blackjackTotal(dealerHand) else blackjackTotal(dealerHand.take(1))
        game.values["dealer_revealed"] = revealDealer
    }

    private fun blackjackTotal(cards: List<Card>): Int {
        var total = cards.sumOf { card ->
            when (card.rank) {
                "A" -> 11
                "K", "Q", "J" -> 10
                else -> card.rank?.toIntOrNull() ?: 0
            }
        }
        var aces = cards.count { it.rank == "A" }
        while (total > 21 && aces-- > 0) total -= 10
        return total
    }

    private fun move(game: MutableGame, command: Command) {
        requireTurn(game, command.actorId)
        val pieceId = text(command.payload, "piece_id")
        val destination = text(command.payload, "to")
        val piece = game.pieces[pieceId]
            ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown piece or destination")
        if (destination !in game.spaces) fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown piece or destination")
        if (piece.ownerId != null && piece.ownerId != command.actorId) {
            fail(HttpStatus.FORBIDDEN, "Actor does not own this piece")
        }
        game.pieces[pieceId] = piece.copy(location = destination)
        game.record(
            "piece_moved",
            command.actorId,
            mapOf("piece_id" to pieceId, "from" to piece.location, "to" to destination),
        )
    }

    private fun draw(game: MutableGame, command: Command) {
        requireTurn(game, command.actorId)
        val deck = game.decks[text(command.payload, "deck_id")]
            ?: fail(HttpStatus.CONFLICT, "Deck does not exist or is empty")
        if (deck.drawPile.isEmpty()) fail(HttpStatus.CONFLICT, "Deck does not exist or is empty")
        val card = deck.drawPile.removeLast()
        deck.hands.getOrPut(command.actorId.toString()) { mutableListOf() } += card
        game.record("card_drawn", command.actorId, mapOf("deck_id" to deck.id, "card_id" to card.id))
    }

    private fun play(game: MutableGame, command: Command) {
        requireTurn(game, command.actorId)
        val deck = game.decks[text(command.payload, "deck_id")]
            ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Card is not in the actor's hand")
        val cardId = text(command.payload, "card_id")
        val hand = deck.hands[command.actorId.toString()]
            ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Card is not in the actor's hand")
        val card = hand.firstOrNull { it.id == cardId }
            ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Card is not in the actor's hand")
        hand.remove(card)
        deck.discardPile += card
        game.record("card_played", command.actorId, mapOf("deck_id" to deck.id, "card" to card))
    }

    private fun shuffle(game: MutableGame, command: Command) {
        val deck = game.decks[text(command.payload, "deck_id")]
            ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown deck")
        Collections.shuffle(deck.drawPile, random)
        game.record("deck_shuffled", command.actorId, mapOf("deck_id" to deck.id))
    }

    private fun roll(game: MutableGame, command: Command) {
        requireTurn(game, command.actorId)
        val values = mutableMapOf<String, Int>()
        command.payload.path("dice_ids").forEach { node ->
            val id = node.asText()
            val die = game.dice[id] ?: fail(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown die: $id")
            val value = random.nextInt(die.sides) + 1
            game.dice[id] = die.copy(value = value)
            values[id] = value
        }
        game.record("dice_rolled", command.actorId, mapOf("values" to values))
    }

    private fun setValue(game: MutableGame, command: Command) {
        requireTurn(game, command.actorId)
        val key = text(command.payload, "key")
        game.values[key] = command.payload.get("value")
        game.record("value_set", command.actorId, mapOf("key" to key, "value" to game.values[key]))
    }

    private fun requireTurn(game: MutableGame, actorId: UUID?): Player {
        val player = game.players.firstOrNull { it.id == actorId }
            ?: fail(HttpStatus.FORBIDDEN, "Actor is not a player in this game")
        if (game.status != GameStatus.ACTIVE) fail(HttpStatus.CONFLICT, "Game is not active")
        if (game.currentPlayerId != actorId) fail(HttpStatus.CONFLICT, "It is not this player's turn")
        return player
    }

    private fun advance(game: MutableGame, actorId: UUID?) {
        requireTurn(game, actorId)
        val index = game.players.indexOfFirst { it.id == actorId }
        game.currentPlayerId = game.players[(index + 1) % game.players.size].id
        game.record("turn_changed", actorId, mapOf("current_player_id" to game.currentPlayerId.toString()))
    }

    private fun winner(game: MutableGame): UUID? {
        val occupied = game.pieces.values.associate { it.location to it.ownerId }
        return WINNING_LINES.firstNotNullOfOrNull { line ->
            occupied[line[0]]?.takeIf { owner ->
                owner == occupied[line[1]] && owner == occupied[line[2]]
            }
        }
    }

    private fun text(payload: JsonNode, key: String): String {
        val value = payload.get(key)
        if (value == null || !value.isTextual || value.asText().isBlank()) {
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "payload.$key is required")
        }
        return value.asText()
    }

    private fun requireGame(id: UUID): MutableGame =
        games[id] ?: fail(HttpStatus.NOT_FOUND, "Game not found")

    private fun fail(status: HttpStatus, detail: Any): Nothing = throw ApiException(status, detail)

    /** Internal mutable aggregate. It is never exposed outside a synchronized block. */
    private class MutableGame(
        val templateId: String,
        val name: String,
    ) {
        val id: UUID = UUID.randomUUID()
        val createdAt: Instant = Instant.now()
        var updatedAt: Instant = createdAt
        var status: GameStatus = GameStatus.LOBBY
        var version: Long = 0
        var currentPlayerId: UUID? = null
        val players = mutableListOf<Player>()
        val spaces = linkedMapOf<String, Space>()
        val pieces = linkedMapOf<String, Piece>()
        val decks = linkedMapOf<String, Deck>()
        val dice = linkedMapOf<String, Die>()
        val values = linkedMapOf<String, Any?>()
        val events = mutableListOf<GameEvent>()
        val replays = mutableMapOf<String, CommandResult>()

        init {
            if (templateId == TIC_TAC_TOE.id) {
                values["winner"] = null
                values["draw"] = false
                for (row in 0..2) {
                    for (column in 0..2) {
                        val key = "$row-$column"
                        spaces[key] = Space(key, "Row ${row + 1}, Column ${column + 1}")
                    }
                }
            } else if (templateId == BLACKJACK.id) {
                values["outcome"] = null
                values["dealer_revealed"] = false
            }
        }

        /** Advances the shared version/event sequence and appends one immutable fact. */
        fun record(type: String, actorId: UUID?, data: Map<String, Any?> = emptyMap()) {
            version += 1
            updatedAt = Instant.now()
            events += GameEvent(
                gameId = id,
                sequence = version,
                type = type,
                actorId = actorId,
                data = data,
                occurredAt = updatedAt,
            )
        }

        /** Deep-copies mutable collections into a client-safe value snapshot. */
        fun snapshot(): GameState = GameState(
            id = id,
            templateId = templateId,
            name = name,
            status = status,
            version = version,
            players = players.toList(),
            currentPlayerId = currentPlayerId,
            board = Board(
                spaces = spaces.toMap(),
                pieces = pieces.toMap(),
                decks = decks.mapValues { (_, deck) ->
                    deck.copy(
                        drawPile = deck.drawPile.toMutableList(),
                        discardPile = deck.discardPile.toMutableList(),
                        hands = deck.hands.mapValues { it.value.toMutableList() }.toMutableMap(),
                    )
                },
                dice = dice.toMap(),
                values = values.toMap(),
            ),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        private val TIC_TAC_TOE = TemplateSummary(
            id = "tic-tac-toe",
            name = "Tic-Tac-Toe",
            minPlayers = 2,
            maxPlayers = 2,
            description = "Two players place X and O on a 3x3 board.",
        )

        private val BLACKJACK = TemplateSummary(
            id = "blackjack",
            name = "Blackjack",
            minPlayers = 1,
            maxPlayers = 1,
            description = "A single player competes against a server-authoritative dealer.",
        )

        private val TEMPLATES = listOf(TIC_TAC_TOE, BLACKJACK)
        private const val BLACKJACK_DECK = "shoe"
        private const val DEALER_HAND = "dealer"

        private fun standardDeck(): List<Card> = listOf("clubs", "diamonds", "hearts", "spades").flatMap { suit ->
            listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K").map { rank ->
                Card(
                    id = "${rank.lowercase()}-$suit",
                    name = "$rank of ${suit.replaceFirstChar(Char::uppercase)}",
                    suit = suit,
                    rank = rank,
                )
            }
        }

        private val WINNING_LINES = listOf(
            listOf("0-0", "0-1", "0-2"),
            listOf("1-0", "1-1", "1-2"),
            listOf("2-0", "2-1", "2-2"),
            listOf("0-0", "1-0", "2-0"),
            listOf("0-1", "1-1", "2-1"),
            listOf("0-2", "1-2", "2-2"),
            listOf("0-0", "1-1", "2-2"),
            listOf("0-2", "1-1", "2-0"),
        )
    }
}
