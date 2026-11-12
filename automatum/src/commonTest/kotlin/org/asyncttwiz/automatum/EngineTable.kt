package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TurnState
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.InitialShuffle
import org.asyncttwiz.protocol.InitialStarter
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayPlayer
import org.asyncttwiz.protocol.PlayRoot
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.RandomV1
import org.asyncttwiz.protocol.StarterChoice
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import kotlin.random.Random

/**
 * A two-player game on engine state (Alice and Bob) with invented cube cards by name, as a game in progress: its engine
 * state built directly ([state]: full information, as a game on engine state holds it), the play it started as ([base]:
 * its containers, players and card data) and the board the table draws of it ([board], [EngineProjection]).
 */
internal class EngineTable {
    val alice = "alice"
    val bob = "bob"
    private val objects = LinkedHashMap<ObjectId, GameObject>()
    private val zones = mutableMapOf<Pair<String, Zone>, MutableList<ObjectId>>()
    private val submitted = mutableMapOf<String, SubmittedDefinition>()
    private val digests = mutableMapOf<String, String>()
    /** The engine definition of each card data digest. */
    val definitions = mutableMapOf<String, arcanum.api.CardDefinition>()
    private var clock = 0L
    var turn = 3
    var active = alice
    var step: TurnStep = TurnStep.PrecombatMain
    var priority: String? = alice
    private val random = Random(5)

    /** Puts cards named [names] into [player]'s [zone] (a library top first); returns their card ids. */
    fun put(player: String, zone: Zone, vararg names: String, tapped: Boolean = false): List<String> = names.map { name ->
        val definition = Cards.definition(name)
        val data = AtwTokens.submitted(definition, token = false)
        submitted[data.digest] = data
        definitions[data.digest] = definition
        val id = ObjectId.Card(CardId("$player.${objects.size}"), 0)
        digests[id.card.value] = data.digest
        objects[id] = GameObject(id = id, owner = PlayerId(player), controller = PlayerId(player).takeIf { zone == Zone.Battlefield },
            zone = zone, definition = definition, tapped = tapped, timestamp = ++clock,
            controlSince = ControlSince(PlayerId(player), 0).takeIf { zone == Zone.Battlefield })
        zones.getOrPut(player to zone) { mutableListOf() } += id
        id.card.value
    }

    /** The engine object of the card [cardId] where it is. */
    fun obj(cardId: String): ObjectId.Card = objects.keys.filterIsInstance<ObjectId.Card>().single { it.card.value == cardId }

    /** The game's engine state now (a fresh copy each time). */
    fun state(): GameState = GameState(
        players = listOf(alice, bob).map { p ->
            PlayerState(PlayerId(p), life = 20, library = zones[p to Zone.Library].orEmpty(), hand = zones[p to Zone.Hand].orEmpty(),
                graveyard = zones[p to Zone.Graveyard].orEmpty())
        }.toMutableList(),
        objects = LinkedHashMap(objects),
        battlefield = objects.values.filter { it.zone == Zone.Battlefield }.map { it.id }.toMutableList(),
        exile = objects.values.filter { it.zone == Zone.Exile }.map { it.id }.toMutableList(),
        command = objects.values.filter { it.zone == Zone.Command }.map { it.id }.toMutableList(),
        turn = TurnState(turn, PlayerId(active), step, turnStarted = mapOf(PlayerId(active) to turn,
            PlayerId(if (active == alice) bob else alice) to turn - 1)),
        priority = PriorityState(priority?.let(::PlayerId)),
        timestampCounter = clock,
    )

    /** The play as it started: each player's containers and the Stack, the cards' data. */
    fun base(): PlayState {
        fun id(key: String) = "00000000-0000-4000-8000-" + key.hashCode().toUInt().toString(16).padStart(12, '0')
        val containers = listOf(alice, bob).flatMap { p ->
            PlayZone.entries.filter { it != PlayZone.STACK }.map { zone -> PlayContainer(id("$p/$zone"), zone, p) }
        } + PlayContainer(id("stack"), PlayZone.STACK)
        return PlayState(gameId = "game", id = "play", preparationRound = "round", startRequestId = "start",
            root = PlayRoot("root", "branch"),
            board = BoardState(listOf(PlayPlayer(alice), PlayPlayer(bob)), objects.values.map { o ->
                val digest = digests.getValue(o.card.value)
                org.asyncttwiz.protocol.PlayCard(o.card.value, digest, o.owner.value, containers.first { it.zone == PlayZone.LIBRARY &&
                    it.accountId == o.owner.value }.id, submitted.getValue(digest).definition.faces.first().faceId)
            }, containers,
                org.asyncttwiz.protocol.TurnState(alice)),
            definitions = submitted.values.sortedBy { it.digest },
            shuffles = listOf(alice, bob).map { InitialShuffle(it, SEED, emptyList()) },
            starter = InitialStarter(StarterChoice(alice), alice))
    }

    /** The board the table draws of [state] for [viewer]. */
    fun board(state: GameState = state(), viewer: String = alice): BoardState =
        EngineProjection.project(base(), GameModel(state.deepCopy(), TableState()), viewer, "node").board

    /** Fresh random seeds. */
    fun seed(): String = RandomV1.encodeSeed(random.nextBytes(32))

    /** The shared flow of [state] as the table works it out. */
    fun flow(state: GameState = state()): SharedFlow = SharedFlow.of(state, board(state), "game", ::seed)

    /** What [viewer] may do with each card of [state] ([CardPlays]). */
    fun plays(state: GameState = state(), viewer: String = alice): CardPlays {
        val view = Engine.view(state)
        return CardPlays.of(state, viewer, view, Host.visible(state, PlayerId(viewer), view))
    }

    /** A cast source on [state]. */
    fun source(state: GameState = state()): CastFlow.Source = CastFlow.Source.engine(state, board(state), "game")

    companion object {
        val SEED: String = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    }
}
