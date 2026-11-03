package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.cards.Cards
import org.asyncttwiz.protocol.BoardPosition
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.BattlefieldPlacement
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.InitialShuffle
import org.asyncttwiz.protocol.InitialStarter
import org.asyncttwiz.protocol.LibraryLayers
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayPlacement
import org.asyncttwiz.protocol.PlayPlayer
import org.asyncttwiz.protocol.PlayRoot
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.RandomV1
import org.asyncttwiz.protocol.StarterChoice
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState

/** Builds an ATW two-player game (Alice and Bob) with invented cube cards by name, as a game in progress. */
internal class AtwTable(private val name: String = "table") {
    private fun id(key: String) = CommandIds.uuidV5(NAMESPACE, "$name/$key")
    val alice = id("alice")
    val bob = id("bob")
    val players = listOf(alice, bob)
    private val cards = mutableListOf<PlayCard>()
    private val zones = mutableMapOf<Pair<String, PlayZone>, MutableList<String>>()
    private val submitted = mutableMapOf<String, SubmittedDefinition>()
    /** The engine definition of each ATW definition digest (what I0-b's parsing gives a game). */
    val definitions = mutableMapOf<String, CardDefinition>()
    /** Each card's name by ATW card id. */
    val names = mutableMapOf<String, String>()
    var turn = TurnState(alice, "1", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
    var priority: MtgPriority? = MtgPriority(alice)
    /** Players with "Full control" on (I-D41): Automatum pauses for them in every step in which they could act. */
    val fullControl = mutableSetOf<String>()

    fun container(player: String, zone: PlayZone) = id("container/$player/$zone")

    /** Puts cards named [names] into [player]'s [zone] (a library top first); returns their ids. */
    fun put(player: String, zone: PlayZone, vararg names: String, tapped: Boolean = false): List<String> = names.map { cardName ->
        val definition = Cards.definition(cardName)
        val data = AtwTokens.submitted(definition, token = false)
        submitted[data.digest] = data
        definitions[data.digest] = definition
        val cardId = id("card/${cards.size}")
        this.names[cardId] = cardName
        val list = zones.getOrPut(player to zone) { mutableListOf() }
        val public = zone != PlayZone.LIBRARY && zone != PlayZone.HAND
        cards += PlayCard(cardId, data.digest, player, container(player, zone), data.definition.faces.first().faceId,
            covered = !public, tapped = tapped && zone == PlayZone.BATTLEFIELD,
            enteredTurn = if (zone == PlayZone.BATTLEFIELD) "0" else "")
        list += cardId
        cardId
    }

    fun state(): PlayState {
        val containers = players.flatMap { p ->
            PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
                val members = zones[p to zone].orEmpty()
                PlayContainer(container(p, zone), zone, p, members,
                    layers = if (zone == PlayZone.LIBRARY && members.isNotEmpty()) LibraryLayers.initial(id("layer/$p"), members) else emptyList())
            }
        } + PlayContainer(id("container/stack"), PlayZone.STACK)
        // Automatum on: its automated events apply only then (SYS-057)
        val board = BoardState(players.map { PlayPlayer(it, fullControl = it in fullControl) }, cards.toList(), containers, turn, priority = priority, automatum = true)
        val definitionsList = submitted.values.sortedBy { it.digest }
        var state = PlayState(id("game"), id("play"), id("round"), id("start"), PlayRoot(id("root"), id("branch")), id("root"),
            board, definitionsList,
            players.map { p -> InitialShuffle(p, SEED, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(alice), alice))
        // Battlefield cards in free cells of their normal rows
        for (card in state.board.cards.filter { c -> containers.single { it.id == c.containerId }.zone == PlayZone.BATTLEFIELD }) {
            val cell = BattlefieldPlacement.cell(state, card.containerId, card)
            state = state.copy(board = state.board.copy(cards = state.board.cards.map {
                if (it.id == card.id) it.copy(placement = PlayPlacement(BoardPosition(cell.x, cell.y), id("pile/${card.id}"))) else it
            }))
        }
        return state.copy(board = state.board.copy(cards = state.board.cards.sortedBy { it.id }))
    }

    companion object {
        const val NAMESPACE = "5d1f0c2a-7e3b-4a96-8c41-0b9e2f6d3a18"
        val SEED: String = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    }
}
