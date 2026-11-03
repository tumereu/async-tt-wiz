package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.engine.Engine
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState

/**
 * I-D56 step C: a game on engine state as the server starts it, for the app's engine path: Alice's and Bob's decks of
 * invented Mountains and Storm Sparks (parsed from their oracle text, as on a device), the play's own base board
 * ([base], [TableStatesFixture]'s containers) and the server's first event ([start]: the game started, simultaneous
 * mulligans) with its [snapshot].
 */
internal class EngineGameFixture(startingPlayer: String = TableStatesFixture.a) {
    private val f = TableStatesFixture
    private class Invented(face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private val spark = Invented(DefinitionFace("spark", "Storm Spark", typeLine = "Instant", manaCost = "{R}",
        rulesText = "Storm Spark deals 3 damage to any target."),
        OracleCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target."))
    private val mountain = Invented(DefinitionFace("mountain", "Mountain", typeLine = "Basic Land — Mountain"),
        OracleCard("Mountain", null, "Basic Land — Mountain"))

    val a = f.a
    val b = f.b
    val game: GameSummary = f.game
    private val decks = listOf(a, b).associateWith { p -> List(30) { i -> f.id("$p/card/$i") to if (i % 3 == 2) spark else mountain } }

    /** The play as it started: the fixture's containers holding the decks' cards in the libraries. */
    val base: PlayState = f.initial.let { p ->
        val cards = decks.flatMap { (player, deck) -> deck.map { (id, card) ->
            PlayCard(id, card.submitted.digest, player, f.id("${if (player == a) "a" else "b"}/LIBRARY"), card.submitted.definition.faces.single().faceId)
        } }
        p.copy(definitions = listOf(spark.submitted, mountain.submitted).sortedBy { it.digest },
            board = p.board.copy(cards = cards, stackEffects = emptyList(), combat = null, engineRecords = emptyList(),
                containers = p.board.containers.map { c ->
                    if (c.zone == PlayZone.LIBRARY && !c.temp) c.copy(cards = cards.filter { it.containerId == c.id }.map { it.id }, layers = emptyList())
                    else c.copy(cards = emptyList(), layers = emptyList())
                }))
    }

    /** As the server starts it: every player's Skip when I have no choice off in the shared state (I-D56 §13). */
    val model: GameModel = GameModel(Engine.newGame(listOf(a, b).map { p ->
        Seat(PlayerId(p), decks.getValue(p).map { (id, card) -> DeckCard(CardId(id), card.definition) })
    }).also { s -> s.players.toList().forEach { p -> s.apply(arcanum.engine.change.Changes.skipWhenNoChoice(s, p.id, false)) } },
        TableState(secret = LineRandom.secret(ByteArray(32) { 3 })))
    val codec = EngineJson(GameDefinitions.of(model.engine))

    /** The server's first event: the game started (Alice first), simultaneous mulligans. */
    val start: GameEvent = EventBuilder(model, f.id("start"), f.id("root"), "server").run {
        perform(Input.StartGame(StartOptions(startingPlayer = PlayerId(startingPlayer), simultaneousMulligans = true, freeFirstMulligan = true)))
        answerRandoms()
        build()
    }

    /** The snapshot a device loads at the start (both local heads at the start event). */
    fun snapshot(settings: org.asyncttwiz.protocol.game.PlaySettings? = null): EngineSnapshot =
        EngineSnapshot(codec.encodeDefinitions(), f.id("root"), start.id, codec.encodeModel(model),
            localHeads = mapOf(a to start.id, b to start.id), settings = settings)

    /** The revision the server sends for [event] (accepted), with the local heads after it. */
    fun revision(event: GameEvent, n: Int, heads: Map<String, String>): GameRevision = GameRevision(game.id, "$n", "${n + 1}",
        DeviceRandom.uuid(), event.author, GameScope(game.id, playId = base.id), game, engineEvent = codec.encodeEvent(event),
        engine = EngineUpdate(localHeads = heads))
}
