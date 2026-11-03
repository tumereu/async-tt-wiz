package org.asyncttwiz.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.*

/**
 * The batch cost work (I-D14: 1000-member Automatum batches) changed how the shared reducer checks and records each
 * event, not what it accepts, refuses or records. Random batches of valid and invalid members, single plays and undos
 * on a busy board give the very same outcomes and recorded events (ids, seals, deltas, concerns, groups) as before:
 * [EXPECTED] is the transcript's digest made by the reducer before that change. SYS-010.
 */
class AutomatedBatchEquivalenceTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val players = listOf(a, b)
    private fun field(player: String) = if (player == a) id(20) else id(21)
    private fun graveyard(player: String) = if (player == a) id(22) else id(23)
    private fun library(player: String) = if (player == a) id(24) else id(25)
    private fun hand(player: String) = if (player == a) id(26) else id(27)
    private fun card(player: String, n: Int) = CommandIds.uuidV5(id(99), "card/$player/$n")

    /** 40 permanents per player (one of them a pile of two), 20-card libraries, 3-card hands; Automatum on. */
    private fun root(): PlayState {
        val definition = CardDefinition("Probe Bear", listOf(DefinitionFace(id(30), "Bear", corner = "2/2")))
        val digest = DeckJson.digest(definition)
        var stamp = 0L
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in players) {
            val onField = (0 until 40).map { n -> PlayCard(card(player, n), digest, player, field(player), id(30), covered = false,
                tapped = n % 3 == 0, placement = PlayPlacement(BoardPosition(n % 10 * 100, n / 10 * 140), card(player, if (n == 39) 38 else n)),
                timestamp = ++stamp) }
            val inLibrary = (0 until 20).map { n -> PlayCard(card(player, 1000 + n), digest, player, library(player), id(30)) }
            val inHand = (0 until 3).map { n -> PlayCard(card(player, 2000 + n), digest, player, hand(player), id(30)) }
            cards += onField + inLibrary + inHand
            containers += PlayContainer(library(player), PlayZone.LIBRARY, player, inLibrary.map { it.id },
                layers = LibraryLayers.initial(id(if (player == a) 40 else 41), inLibrary.map { it.id }))
            containers += PlayContainer(hand(player), PlayZone.HAND, player, inHand.map { it.id })
            containers += PlayContainer(field(player), PlayZone.BATTLEFIELD, player,
                GameplayRules.canonicalBoard(BoardState(emptyList(), onField, listOf(PlayContainer(field(player), PlayZone.BATTLEFIELD,
                    player, onField.map { it.id })), TurnState(a))).containers.single().cards)
            containers += PlayContainer(graveyard(player), PlayZone.GRAVEYARD, player)
        }
        containers += PlayContainer(id(28), PlayZone.STACK)
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards, containers, TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a))
    }

    private var request = 100_000
    private fun command(state: PlayState, action: GameAction) =
        GameCommand(id(request++), GameScope(state.gameId, playId = state.id), action, id(90), "1")

    /** A random member on [state]: mostly applicable, some stale or impossible. */
    private fun member(random: Random, state: PlayState): PlayAction {
        val board = state.board
        val player = players.random(random)
        fun on(zone: PlayZone, owner: String = player) = board.cards.filter { card ->
            board.containers.any { it.id == card.containerId && it.zone == zone && it.accountId == owner } }
        fun ref(card: PlayCard) = MtgRef.Card(card.id, if (random.nextInt(8) == 0) card.zoneChanges + 1 else card.zoneChanges)
        val permanent = on(PlayZone.BATTLEFIELD).randomOrNull(random)
        return when (random.nextInt(13)) {
            0, 1 -> PlayAction.SetTapped((permanent ?: board.cards.random(random)).id, random.nextBoolean())
            2 -> permanent?.let { PlayAction.MoveCard(it.id, MoveSource(it.containerId, it.placement), MoveDestination(graveyard(player), MoveInsertion.UNORDERED)) }
                ?: PlayAction.AdjustLife(player, "-1")
            3 -> (on(PlayZone.GRAVEYARD) + on(PlayZone.LIBRARY).take(1)).randomOrNull(random)?.let {
                PlayAction.MoveCard(it.id, MoveSource(it.containerId), MoveDestination(field(player), MoveInsertion.EMPTY,
                    BoardPosition(random.nextInt(12) * 100, random.nextInt(5) * 140)))
            } ?: PlayAction.AdjustLife(player, "+1")
            4 -> permanent?.let { card ->
                val held = card.typedCounters.singleOrNull { it.kind == "+1/+1" }
                PlayAction.Mtg(MtgAction.SetCounter(ref(card), "+1/+1", if (random.nextInt(6) == 0) null else held,
                    TypedCounter("+1/+1", (held?.count ?: 0) + 1, 50_000L + random.nextInt(100))))
            } ?: PlayAction.AdjustLife(player, "-1")
            5 -> permanent?.let { PlayAction.Mtg(MtgAction.SetLedger(ref(it), if (random.nextInt(6) == 0) "{}" else it.ledger,
                """{"n":${random.nextInt(100)}}""")) } ?: PlayAction.AdjustLife(player, "-1")
            6 -> "effect:${random.nextInt(4)}".let { record -> board.engineRecords.singleOrNull { it.id == record }.let { current ->
                PlayAction.Mtg(MtgAction.SetRecord(record, current,
                    if (current != null && random.nextBoolean()) null else EngineRecord(record, EngineRecordKind.Effect, 60_000L + random.nextInt(9), """{"r":${random.nextInt(9)}}""")))
            } }
            7 -> PlayAction.AdjustLife(player, if (random.nextBoolean()) "-2" else "+1")
            8 -> PlayAction.Mtg(MtgAction.SetPriority(if (random.nextInt(5) == 0) MtgPriority(b, 7) else board.priority,
                MtgPriority(players.random(random), random.nextInt(2))))
            9 -> when (val area = board.combat) {
                null -> PlayAction.Combat(CombatAction.Open)
                else -> if (random.nextInt(4) == 0) PlayAction.Combat(CombatAction.Close(area.id))
                    else PlayAction.Combat(CombatAction.Declare(area.id, (permanent ?: board.cards.random(random)).id))
            }
            10 -> board.contentsDisplays.firstOrNull()?.let {
                PlayAction.DismissContents(it.id, ContentsRest.entries.filter { rest -> rest != ContentsRest.GRAVEYARD && rest != ContentsRest.EXILE }
                    .random(random), RandomV1.encodeSeed(random.nextBytes(32)))
            } ?: PlayAction.ShowContents(library(player), ContentsSelection.TOP, 1 + random.nextInt(3))
            11 -> permanent?.let { PlayAction.Mtg(MtgAction.Restamp(it.id, MtgStamp(it.zoneChanges, it.timestamp),
                MtgStamp(it.zoneChanges, it.timestamp + 1 + random.nextInt(3)))) } ?: PlayAction.AdjustLife(player, "-1")
            else -> PlayAction.Mtg(MtgAction.SetCounter(MtgRef.Player(player), "poison",
                board.players.single { it.accountId == player }.typedCounters.singleOrNull { it.kind == "poison" },
                TypedCounter("poison", 1 + random.nextInt(3), 70_000L)))
        }
    }

    /** The transcript of a seeded random game: each command's outcome and every event it recorded. */
    private fun transcript(seed: Int): String {
        val random = Random(seed)
        var tree = GameplayTree(root())
        val lines = mutableListOf<String>()
        repeat(60) { step ->
            val state = tree.stateAt(tree.currentNodeId)!!
            val before = tree.currentNodeId
            val action: GameAction = when (random.nextInt(10)) {
                0 -> GameAction.Undo
                1 -> GameAction.Play(member(random, state))
                else -> {
                    var planned = state
                    val actions = mutableListOf<PlayAction>()
                    val tags = mutableListOf<MtgTag?>()
                    // Most batches are planned on (only members that apply); some keep one that may not
                    val dirty = random.nextInt(4) == 0
                    val size = 1 + random.nextInt(60)
                    val kept = random.nextInt(size)
                    repeat(size) { i ->
                        fun applies(candidate: PlayAction) = GameplayRules.preview(planned, a,
                            PlayBatches.member(command(state, GameAction.PlayBatch(listOf(candidate))), 0, candidate)).state != null
                        var next = member(random, planned)
                        if (!(dirty && i == kept)) repeat(10) { if (!applies(next)) next = member(random, planned) }
                        actions += next
                        tags += if (i == 0) MtgTag(listOf("probe"), board = MtgFacts.digest((if (random.nextInt(10) == 0) root() else state).board))
                            else if (random.nextInt(3) == 0) null else if (random.nextBoolean()) MtgTag(folded = true) else MtgTag(listOf("probe-$i"))
                        // Plan on as a device does: a refused member leaves the plan where it was
                        GameplayRules.preview(planned, a, PlayBatches.member(command(state, GameAction.PlayBatch(listOf(next))), 0, next))
                            .state?.let { planned = it }
                    }
                    GameAction.PlayBatch(actions, tags)
                }
            }
            val change = HistoryContinuation.change(tree, a, command(state, action))
            val next = change.tree
            lines += "$step ${action::class.simpleName} ${change.outcome} ${change.reason}"
            if (next != null) {
                // Every event the command recorded, oldest first
                val recorded = generateSequence(next.currentNodeId) { next.nodes[it]?.parentId }.takeWhile { it != before && it in next.nodes }
                    .toList().asReversed().map { next.nodes.getValue(it) }
                if (action !is GameAction.Undo) for (node in recorded) {
                    lines += GameWire.json.encodeToString(GameplayMutation.serializer(), node)
                    // The server reads a new event's group from its bookkeeping (GameplayStore.groupAt)
                    assertEquals(next.stateAt(node.id)!!.currentGroup, node.bookkeeping!!.toGroup)
                }
                lines += MtgFacts.digest(next.stateAt(next.currentNodeId)!!.board)
                tree = next
            }
        }
        return lines.joinToString("\n")
    }

    // SYS-010, SYS-055
    @Test fun randomBatchesAreCheckedAndRecordedAsBefore() {
        val transcripts = (1..4).map(::transcript)
        // Batches both taken and refused, so both are compared
        for (lines in transcripts) {
            assertTrue(lines.lines().count { it.contains(" PlayBatch accepted") } >= 5, lines.lines().filter { it.contains("PlayBatch") }.toString())
            assertTrue(lines.lines().count { it.contains(" PlayBatch rejected") } >= 5, lines.lines().filter { it.contains("PlayBatch") }.toString())
        }
        val digests = transcripts.map { DeckJson.intent("batch-equivalence", it) }
        assertEquals(EXPECTED, digests)
    }

    @Test fun canonicalJsonAndDigestsAreWrittenAsBefore() {
        // Every UTF-16 unit as a string, alone and as an object key, is written as the JSON library writes it
        for (c in 0..0xFFFF) {
            val text = c.toChar().toString()
            assertEquals(JsonPrimitive(text).toString(), DeckJson.canonicalElement(JsonPrimitive(text)))
            assertEquals("{" + JsonPrimitive(text).toString() + ":1}", DeckJson.canonicalElement(buildJsonObject { put(text, 1) }))
        }
        val random = Random(5)
        repeat(200) {
            val bytes = random.nextBytes(random.nextInt(100))
            assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, sha256(bytes))
        }
    }

    private companion object {
        /** Made by the reducer before the batch cost work (master df7258dd); made again when the board lost its
         * Automatum switch (I-D52), which the boards' digests in the transcript held. */
        val EXPECTED = listOf("4b399be52d89ed2aeeec425e4e23e234947d6d7c30f98610bb11763e15224f4a",
            "5d3f44906f1ca333e7939629bb245ef024efccf99754736b54d24e173cc7e9f5",
            "356ac874589fa2e65e8b8b025bf9a7bb5c01a1d378c53f1ea5356c9d47c6d423",
            "7d32b4c4554ed14e9464e0aa60299e06ef335ac88e1ec0bd8c0d67f46416e818")
    }
}
