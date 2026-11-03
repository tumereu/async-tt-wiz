package org.asyncttwiz.protocol

import kotlin.test.*

/**
 * A printed probe of what accepting a 1000-member Automatum batch (I-D14, SYS-010) costs on a busy board with a long
 * history: the shared reducer as the server runs it ([PlayBatches.change]) and as the acting device previews it. It
 * prints timings and asserts only outcomes, never elapsed time (no timing tests).
 */
class AutomatedBatchCostTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun field(player: String) = if (player == a) id(20) else id(21)
    private fun graveyard(player: String) = if (player == a) id(22) else id(23)
    private fun library(player: String) = if (player == a) id(24) else id(25)
    private fun hand(player: String) = if (player == a) id(26) else id(27)
    private val stack = id(28)
    /** Card ids in no particular order, as a real deck's. */
    private fun card(player: String, n: Int) = CommandIds.uuidV5(id(99), "card/$player/$n")

    /** 300 tapped permanents per player, 60-card libraries, 7-card hands; Automatum on. */
    private fun root(permanents: Int = 300): PlayState {
        val definition = CardDefinition("Probe Bear", listOf(DefinitionFace(id(30), "Bear", corner = "2/2")))
        val digest = DeckJson.digest(definition)
        var stamp = 0L
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(a, b)) {
            val onField = (0 until permanents).map { n -> PlayCard(card(player, n), digest, player, field(player), id(30),
                covered = false, tapped = true, placement = PlayPlacement(BoardPosition(n % 30 * 100, n / 30 * 140), card(player, n)),
                timestamp = ++stamp) }
            val inLibrary = (0 until 60).map { n -> PlayCard(card(player, 1000 + n), digest, player, library(player), id(30)) }
            val inHand = (0 until 7).map { n -> PlayCard(card(player, 2000 + n), digest, player, hand(player), id(30)) }
            cards += onField + inLibrary + inHand
            containers += PlayContainer(library(player), PlayZone.LIBRARY, player, inLibrary.map { it.id },
                layers = LibraryLayers.initial(id(if (player == a) 40 else 41), inLibrary.map { it.id }))
            containers += PlayContainer(hand(player), PlayZone.HAND, player, inHand.map { it.id })
            containers += PlayContainer(field(player), PlayZone.BATTLEFIELD, player, onField.map { it.id })
            containers += PlayContainer(graveyard(player), PlayZone.GRAVEYARD, player)
        }
        containers += PlayContainer(stack, PlayZone.STACK)
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards, containers, TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a))
    }

    private fun command(state: PlayState, action: GameAction, n: Int) =
        GameCommand(id(n), GameScope(state.gameId, playId = state.id), action, id(90), "1")

    /** [events] ordinary manual events on [root] (taps, life), as a long game's history. */
    private fun history(events: Int): GameplayTree {
        var tree = GameplayTree(root())
        repeat(events) { n ->
            val state = tree.stateAt(tree.currentNodeId)!!
            val action = if (n % 3 == 0) PlayAction.AdjustLife(if (n % 2 == 0) a else b, "-1")
                else PlayAction.SetTapped(card(b, 299), !state.board.cards.first { it.id == card(b, 299) }.tapped)
            tree = checkNotNull(HistoryContinuation.change(tree, a, command(state, GameAction.Play(action), 500_000 + n)).tree)
        }
        return tree
    }

    /** One engine call's batch of 1000 tagged members: untaps, deaths, counters, ledgers, records, life. */
    private fun batch(state: PlayState): List<Pair<PlayAction, MtgTag?>> {
        val board = state.board
        fun cardOf(id: String) = board.cards.first { it.id == id }
        val cause = MtgCause(a, name = "Probe Wrath")
        val members = mutableListOf<Pair<PlayAction, MtgTag?>>()
        members += PlayAction.Mtg(MtgAction.SetPriority(board.priority, MtgPriority(a))) to MtgTag(board = MtgFacts.digest(board))
        for (n in 0 until 300) members += PlayAction.SetTapped(card(a, n), false) to MtgTag(listOf("untapped"))
        for (n in 0 until 200) {
            val c = cardOf(card(b, n))
            members += PlayAction.MoveCard(c.id, MoveSource(c.containerId, c.placement), MoveDestination(graveyard(b), MoveInsertion.UNORDERED)) to
                MtgTag(listOf("died"), cause)
        }
        for (n in 0 until 200) {
            val c = cardOf(card(a, n))
            members += PlayAction.Mtg(MtgAction.SetCounter(MtgRef.Card(c.id, c.zoneChanges), "+1/+1", null,
                TypedCounter("+1/+1", 1, 10_000L + n))) to MtgTag(listOf("counter-added"), cause, folded = true)
        }
        for (n in 0 until 200) {
            val c = cardOf(card(a, 100 + n))
            members += PlayAction.Mtg(MtgAction.SetLedger(MtgRef.Card(c.id, c.zoneChanges), "", """{"memory":$n}""")) to
                MtgTag(folded = true)
        }
        for (n in 0 until 50) members += PlayAction.Mtg(MtgAction.SetRecord("effect:$n", null,
            EngineRecord("effect:$n", EngineRecordKind.Effect, 20_000L + n, """{"mechanics":["anthem"],"n":$n}"""))) to MtgTag(folded = true)
        for (n in 0 until 49) members += PlayAction.AdjustLife(if (n % 2 == 0) a else b, "-1") to MtgTag(listOf("life-changed"), cause)
        check(members.size == PlayBatches.MAX_AUTOMATED)
        return members
    }

    private fun ms(nanos: Long) = "%.1f ms".format(nanos / 1e6)
    /** This thread's CPU time: other processes on a busy build machine don't count. */
    private fun cpu() = java.lang.management.ManagementFactory.getThreadMXBean().currentThreadCpuTime

    @Test fun probeAThousandMemberAutomatedBatch() {
        val tree = history(3000)
        val state = tree.stateAt(tree.currentNodeId)!!
        val members = batch(state)
        val action = GameAction.PlayBatch(members.map { it.first }, members.map { it.second })
        val rounds = 8 // the first ones warm up the JVM
        val server = mutableListOf<Long>()
        val device = mutableListOf<Long>()
        repeat(rounds) { round ->
            val command = command(state, action, 900_000 + round)
            // The server: the whole batch through the shared reducer, recorded on the history tree.
            val start = cpu()
            val change = PlayBatches.change(tree.materializedCopy(), a, command)
            server += cpu() - start
            assertEquals(null, change.reason)
            val next = change.tree!!
            assertEquals(tree.nodes.size + members.size, next.nodes.size)
            // The acting device: the same members previewed one after another (a provisional result).
            val begin = cpu()
            var predicted = state
            for (i in members.indices) predicted = GameplayRules.preview(predicted, a, PlayBatches.member(command, i, members[i].first)).state!!
            device += cpu() - begin
            assertEquals(next.stateAt(next.currentNodeId)!!.board, predicted.board)
        }
        val warm = server.drop(2).sorted()
        val local = device.drop(2).sorted()
        println("1000-member automated batch on ${state.board.cards.size} cards, ${tree.nodes.size} events: " +
            "server median ${ms(warm[warm.size / 2])} (min ${ms(warm.first())}), " +
            "device preview median ${ms(local[local.size / 2])} (min ${ms(local.first())})")
    }
}
