package org.asyncttwiz.protocol

import kotlin.test.*

class LimitedEngineTest {
    private val players = listOf("ann", "bob", "cat", "dan")
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private val otherSeed = RandomV1.encodeSeed(ByteArray(32) { (it * 7 + 1).toByte() })
    private fun cube(size: Int) = List(size) { DraftCubeEntry("card-" + it.toString().padStart(3, '0'), 1) }

    // DRF-021, DRF-023, DRF-141: every engine's defaults, ranges and player limits.
    @Test fun defaultsRangesAndPlayerLimits() {
        assertEquals(DraftSettings(DraftMode.ROCHESTER, packsPerPlayer = 3, cardsPerPack = 15), DraftSettings.rochester())
        assertNull(DraftSettings.rochester().tablePacksOpen)
        assertEquals(45, DraftSettings.rotisserie().cardsPerPlayer)
        assertEquals(listOf(3, 3), DraftSettings.winston().let { listOf(it.packsPerPlayer, it.piles) })
        assertEquals(listOf(3, 4), DraftSettings.winchester().let { listOf(it.packsPerPlayer, it.piles) })
        assertEquals(listOf(6, 45), DraftSettings.sealed().let { listOf(it.packsPerPlayer, it.cardsPerPlayer) })
        val limits = mapOf(DraftMode.BOOSTER to 8, DraftMode.GRID to 4, DraftMode.ROCHESTER to 8, DraftMode.ROTISSERIE to 8,
            DraftMode.WINSTON to 4, DraftMode.WINCHESTER to 4, DraftMode.SEALED to 8)
        for ((mode, most) in limits) {
            val settings = DraftSettings.defaults(mode)
            assertEquals(2..most, mode.players)
            for (count in 2..most) assertTrue(settings.validate(count).isEmpty(), "$mode $count")
            for (count in listOf(1, most + 1)) assertEquals(listOf(DraftSettingsIssue.PLAYERS), settings.validate(count), "$mode $count")
        }
        fun issues(settings: DraftSettings) = settings.validate(2)
        val rochester = DraftSettings.rochester()
        assertEquals(listOf(DraftSettingsIssue.PACKS_PER_PLAYER), issues(rochester.copy(packsPerPlayer = 16)))
        assertEquals(listOf(DraftSettingsIssue.CARDS_PER_PACK), issues(rochester.copy(cardsPerPack = 4)))
        assertTrue(issues(rochester.copy(packsPerPlayer = 15, cardsPerPack = 30, tablePacksOpen = 120)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PACKS_OPEN_AT_ONCE), issues(rochester.copy(tablePacksOpen = 0)))
        assertEquals(listOf(DraftSettingsIssue.CARDS_PER_PLAYER), issues(DraftSettings.rotisserie().copy(cardsPerPlayer = 226)))
        assertTrue(issues(DraftSettings.rotisserie().copy(cardsPerPlayer = 5)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PILES), issues(DraftSettings.winston().copy(piles = 7)))
        assertTrue(issues(DraftSettings.winchester().copy(piles = 8)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PILES), issues(DraftSettings.winchester().copy(piles = 1)))
        assertEquals(listOf(DraftSettingsIssue.PACKS_PER_PLAYER), issues(DraftSettings.sealed().copy(packsPerPlayer = 31)))
        assertEquals(listOf(DraftSettingsIssue.LIBRARIANS), issues(DraftSettings.rochester().copy(librarians = 1)))
        // Cube supply: cards each for Rotisserie and Sealed, packs × pack size for the others.
        assertEquals(DraftSupply(90, 100), DraftSettings.rotisserie().supply(2, cube(100)))
        assertEquals(DraftSupply(135, 100), DraftSettings.winston().supply(3, cube(100)))
        assertEquals(DraftSupply(180, 100), DraftSettings.sealed().supply(4, cube(100)))
    }

    private val set = SetSourceSnapshot("TST", "Test set", List(30) { index ->
        SetSourceCard("sha256:" + index.toString(16).padStart(64, '0'), "00000000-0000-4000-8000-" + index.toString().padStart(12, '0'),
            listOf(SetCardRarity.COMMON, SetCardRarity.UNCOMMON, SetCardRarity.RARE)[index % 3])
    })

    // DRF-142: Sealed deals every pool at the start, with no draft phase.
    @Test fun sealedDealsPrivatePoolsFromACubeOrBoosters() {
        val settings = DraftSettings.sealed().copy(cardsPerPlayer = 20)
        val state = LimitedEngine.startFromCube(players, cube(100), settings, seed)
        assertTrue(state.finished)
        assertEquals(players.toSet(), state.pools.keys)
        assertTrue(state.pools.values.all { it.size == 20 })
        assertEquals(80, state.pools.values.flatten().distinct().size) // without replacement
        assertTrue(state.queues.values.all { it.isEmpty() })
        assertEquals(state, LimitedEngine.startFromCube(players.reversed(), cube(100), settings, seed))
        assertNotEquals(state.pools, LimitedEngine.startFromCube(players, cube(100), settings, otherSeed).pools)
        assertFailsWith<IllegalArgumentException> { LimitedEngine.startFromCube(players, cube(79), settings, seed) }
        val view = LimitedEngine.view(state, "bob")
        assertEquals(state.pools.getValue("bob"), view.pool.map { it.id })
        assertTrue(view.board.isEmpty() && view.piles.isEmpty() && view.picks.isEmpty())
        // From a set: each player opens their own boosters.
        val boosters = LimitedEngine.startFromSet(players.take(2), set, DraftSettings.sealed().copy(packsPerPlayer = 3, cardsPerPack = 10), seed)
        assertTrue(boosters.finished)
        assertTrue(boosters.pools.values.all { it.size == 30 })
        assertEquals(60, boosters.copies.size)
    }

    private fun move(state: LimitedDraftState, player: String, action: LimitedAction, card: String? = null, packId: String? = null,
        pile: Int? = null, turn: Int = state.turn): LimitedResult = LimitedEngine.act(state, LimitedMove(player, action, turn, card, packId, pile))
    private fun ok(result: LimitedResult): LimitedDraftState = assertNotNull(result.state, "rejected: ${result.reason}")

    // DRF-143: snake order one card at a time until everyone has their cards; the whole pool is public.
    @Test fun rotisserieSnakesThroughTheWholePoolUntilEveryoneHasTheirCards() {
        val three = players.take(3)
        var state = LimitedEngine.startFromCube(three, cube(30), DraftSettings.rotisserie().copy(cardsPerPlayer = 5), seed)
        assertEquals(15, state.board.size)
        val seating = state.seating
        val order = mutableListOf<String>()
        while (!state.finished) {
            val player = state.queues.entries.single { it.value.isNotEmpty() }.key
            val view = LimitedEngine.view(state, player)
            assertEquals(player, view.currentPlayer)
            assertEquals(15, view.turns)
            if (state.turn == 0) {
                val other = three.first { it != player }
                assertEquals(LimitedRejection.NOT_YOUR_TURN, move(state, other, LimitedAction.TAKE_CARD, state.board[0]).reason)
                assertEquals(LimitedRejection.UNKNOWN_CARD, move(state, player, LimitedAction.TAKE_CARD, "missing").reason)
                assertEquals(LimitedRejection.STALE_TURN, move(state, player, LimitedAction.TAKE_CARD, state.board[0], turn = 1).reason)
                assertEquals(LimitedRejection.WRONG_ACTION, move(state, player, LimitedAction.TAKE_PILE, pile = 0).reason)
            }
            val free = view.board.first { it.pickedBy == null }.copy.id
            val taken = view.board.firstOrNull { it.pickedBy != null }?.copy?.id
            if (taken != null) assertEquals(LimitedRejection.UNKNOWN_CARD, move(state, player, LimitedAction.TAKE_CARD, taken).reason)
            // The pick badge: two in a row at the snake's turns, otherwise one; nothing for the others.
            assertEquals(if (state.turn % 3 == 2 && state.turn < 14) 2 else 1, LimitedEngine.consecutivePicks(state, player))
            assertTrue(three.filter { it != player }.all { LimitedEngine.consecutivePicks(state, it) == 0 })
            order += player
            state = ok(move(state, player, LimitedAction.TAKE_CARD, free))
        }
        val (x, y, z) = seating
        assertEquals(listOf(x, y, z, z, y, x, x, y, z, z, y, x, x, y, z), order)
        assertTrue(state.pools.values.all { it.size == 5 })
        assertEquals(LimitedRejection.DRAFT_FINISHED, move(state, x, LimitedAction.TAKE_CARD, state.board[0]).reason)
        // Every member sees every card and who took it.
        val view = LimitedEngine.view(state, y)
        assertTrue(view.board.all { it.pickedBy != null })
        assertEquals(state.pools.getValue(y).toSet(), view.board.filter { it.pickedBy == y }.map { it.copy.id }.toSet())
    }

    /** Plays a Rochester draft to its end, each move the first free card of the mover's first pack; returns pickers per pack. */
    private fun playRochester(start: LimitedDraftState, check: (LimitedDraftState) -> Unit = {}): Pair<LimitedDraftState, Map<String, List<String>>> {
        var state = start
        val pickers = mutableMapOf<String, MutableList<String>>()
        var guard = 0
        while (!state.finished) {
            check(state)
            val (player, queue) = state.queues.entries.first { it.value.isNotEmpty() }
            val pack = state.packs.single { it.id == queue.first() }
            val card = pack.cards.first { it !in pack.picked }
            state = ok(move(state, player, LimitedAction.TAKE_CARD, card, pack.id, turn = pack.picked.size))
            pickers.getOrPut(pack.id) { mutableListOf() } += player
            assertTrue(++guard < 1000)
        }
        return state to pickers
    }

    // DRF-145: packs face up, each picked in snake order from its rotating opener; the next round opens when all are empty.
    @Test fun rochesterPicksEachOpenPackInSnakeOrderAndOpensRoundsTogether() {
        val three = players.take(3)
        val settings = DraftSettings.rochester().copy(packsPerPlayer = 2, cardsPerPack = 6)
        val start = LimitedEngine.startFromCube(three, cube(40), settings, seed)
        val seating = start.seating
        assertEquals(6, start.packs.size)
        // One pack per player is open: each member opens one and picks first in it.
        assertTrue(seating.all { start.queues.getValue(it).size == 1 })
        assertTrue(seating.all { LimitedEngine.consecutivePicks(start, it) == 1 })
        val first = start.packs[0]
        assertEquals(seating[0], first.opener)
        assertEquals(LimitedRejection.NOT_YOUR_TURN, move(start, seating[1], LimitedAction.TAKE_CARD, first.cards[0], first.id, turn = 0).reason)
        assertEquals(LimitedRejection.STALE_TURN, move(start, seating[0], LimitedAction.TAKE_CARD, first.cards[0], first.id, turn = 1).reason)
        assertEquals(LimitedRejection.UNKNOWN_CARD, move(start, seating[0], LimitedAction.TAKE_CARD, start.packs[1].cards[0], first.id, turn = 0).reason)
        assertEquals(LimitedRejection.NOT_YOUR_TURN, move(start, seating[0], LimitedAction.TAKE_CARD, start.packs[3].cards[0], "pack-3", turn = 0).reason)
        val (end, pickers) = playRochester(start) { state ->
            // Only the open round is shown, the same to everyone; later packs stay hidden.
            val view = LimitedEngine.view(state, seating[1])
            assertTrue(view.packs.all { pack -> state.packs.single { it.id == pack.id }.round == state.round })
            assertEquals(3, view.packs.size)
            assertEquals(view.packs, LimitedEngine.view(state, seating[2]).packs)
            val hidden = state.packs.filter { it.round > state.round }.flatMap { it.cards }.toSet()
            assertTrue(view.packs.flatMap { it.slots }.none { it.copy.id in hidden })
            if (state.round == 1) assertTrue(state.packs.filter { it.round == 0 }.all { it.empty })
        }
        for ((index, pack) in end.packs.withIndex()) {
            val o = seating.indexOf(pack.opener)
            assertEquals(seating[index % 3], pack.opener)
            assertEquals(listOf(0, 1, 2, 2, 1, 0).map { seating[(o + it) % 3] }, pickers.getValue(pack.id))
        }
        assertTrue(end.pools.values.all { it.size == 12 })
        assertEquals(LimitedRejection.DRAFT_FINISHED, move(end, seating[0], LimitedAction.TAKE_CARD, "x", "pack-0", turn = 0).reason)
        // One pack at a time: its opener rotates around the table.
        val single = LimitedEngine.startFromCube(three, cube(40), settings.copy(tablePacksOpen = 1), seed)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), single.packs.map { it.round })
        assertEquals(1, single.queues.values.sumOf { it.size })
        val (singleEnd, _) = playRochester(single)
        assertEquals(seating + seating, singleEnd.packs.map { it.opener })
    }

    // DRF-147, DRF-148: piles stay hidden until looked at, and what you have seen stays face up for you.
    @Test fun winstonPilesHiddenUntilLookedAtAndTurnsRunToTheEnd() {
        val two = players.take(2)
        var state = LimitedEngine.startFromCube(two, cube(20), DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 10), seed)
        val (x, y) = state.seating
        assertEquals(listOf(1, 1, 1), state.piles.map { it.size })
        assertEquals(17, state.stack.size)
        assertEquals(listOf("turn"), state.queues.getValue(x))
        fun shown(player: String) = LimitedEngine.view(state, player).piles.map { pile -> pile.map { it != null } }
        assertEquals(listOf(listOf(true), listOf(false), listOf(false)), shown(x))
        assertEquals(listOf(listOf(false), listOf(false), listOf(false)), shown(y))
        assertEquals(LimitedRejection.NOT_YOUR_TURN, move(state, y, LimitedAction.TAKE_PILE, pile = 0).reason)
        assertEquals(LimitedRejection.WRONG_PILE, move(state, x, LimitedAction.TAKE_PILE, pile = 1).reason)
        assertEquals(LimitedRejection.STALE_TURN, move(state, x, LimitedAction.PASS_PILE, pile = 0, turn = 1).reason)
        assertEquals(LimitedRejection.WRONG_ACTION, move(state, x, LimitedAction.TAKE_CARD, card = state.piles[0][0]).reason)
        // Passing puts one more card face down on the pile and looks at the next one.
        state = ok(move(state, x, LimitedAction.PASS_PILE, pile = 0))
        assertEquals(1, state.pile)
        assertEquals(listOf(listOf(true, false), listOf(true), listOf(false)), shown(x))
        val taken = state.piles[1]
        state = ok(move(state, x, LimitedAction.TAKE_PILE, pile = 1))
        assertEquals(taken, state.pools.getValue(x))
        assertEquals(1, state.piles[1].size) // replaced from the stack
        assertEquals(listOf("turn"), state.queues.getValue(y))
        assertEquals(listOf(listOf(true, true), listOf(false), listOf(false)), shown(y))
        // The other player never saw the taken card: it is a back in their view of x's picks.
        assertEquals(listOf(null), LimitedEngine.view(state, y).picks.single().cards)
        assertEquals(taken, LimitedEngine.view(state, x).picks.single().cards.map { it!!.id })
        // y passes every pile and takes the top card of the stack.
        val top = state.stack[state.stack.size - 4] // each pass first puts a card on its pile
        repeat(3) { pile -> state = ok(move(state, y, LimitedAction.PASS_PILE, pile = pile)) }
        assertEquals(listOf(top), state.pools.getValue(y))
        assertTrue(LimitedEngine.view(state, y).picks.last().fromStack)
        assertEquals(listOf(null), LimitedEngine.view(state, x).picks.last().cards)
        // y still sees the two cards it looked at in pile 1; the card it added since is a back. x, whose turn starts, looks again.
        assertEquals(listOf(true, true, false), shown(y)[0])
        assertEquals(listOf(true, true, true), shown(x)[0])
        // With the stack empty you can't pass the last pile, and the last stack card goes to you, not the pile.
        assertEquals(2, state.turn)
        val ending = state.copy(stack = listOf(state.stack.first()), pile = 2)
        val last = ok(move(ending, x, LimitedAction.PASS_PILE, pile = 2, turn = 2))
        assertEquals(ending.piles[2].size, last.piles[2].size)
        assertEquals(ending.stack, last.log.last().cards)
        assertEquals(LimitedRejection.CANNOT_PASS, move(ending.copy(stack = emptyList()), x, LimitedAction.PASS_PILE, pile = 2, turn = 2).reason)
        // Play out: take a pile of two or more, or when passing is impossible.
        var guard = 0
        while (!state.finished) {
            val player = state.queues.entries.single { it.value.isNotEmpty() }.key
            val pile = state.piles[state.pile]
            val canPass = state.stack.isNotEmpty() || state.piles.drop(state.pile + 1).any { it.isNotEmpty() }
            state = ok(move(state, player, if (pile.size >= 2 || !canPass) LimitedAction.TAKE_PILE else LimitedAction.PASS_PILE, pile = state.pile))
            assertTrue(++guard < 500)
        }
        assertEquals(20, state.pools.values.sumOf { it.size })
        assertEquals(state.copies.map { it.id }.toSet(), state.pools.values.flatten().toSet())
        assertTrue(state.queues.values.all { it.isEmpty() })
    }

    // DRF-149: every pile gets a card from the stack each turn, then the player takes one whole pile.
    @Test fun winchesterDealsToEveryPileEachTurnAndTakesWholePiles() {
        val three = players.take(3)
        var state = LimitedEngine.startFromCube(three, cube(30), DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 5), seed)
        val seating = state.seating
        assertEquals(listOf(1, 1, 1, 1), state.piles.map { it.size })
        assertEquals(11, state.stack.size)
        // Piles are face up for everyone; the stack is only counted.
        assertTrue(three.all { player -> LimitedEngine.view(state, player).piles.flatten().all { it != null } })
        assertEquals(LimitedRejection.NOT_YOUR_TURN, move(state, seating[1], LimitedAction.TAKE_PILE, pile = 0).reason)
        assertEquals(LimitedRejection.WRONG_PILE, move(state, seating[0], LimitedAction.TAKE_PILE, pile = 4).reason)
        assertEquals(LimitedRejection.WRONG_ACTION, move(state, seating[0], LimitedAction.PASS_PILE, pile = 0).reason)
        assertEquals(LimitedRejection.EMPTY_PILE, move(state.copy(piles = listOf(emptyList<String>()) + state.piles.drop(1)),
            seating[0], LimitedAction.TAKE_PILE, pile = 0).reason)
        state = ok(move(state, seating[0], LimitedAction.TAKE_PILE, pile = 2))
        assertEquals(listOf(2, 2, 1, 2), state.piles.map { it.size })
        assertEquals(7, state.stack.size)
        assertEquals(LimitedRejection.STALE_TURN, move(state, seating[1], LimitedAction.TAKE_PILE, pile = 0, turn = 0).reason)
        val order = mutableListOf(seating[0])
        var guard = 0
        while (!state.finished) {
            val player = state.queues.entries.single { it.value.isNotEmpty() }.key
            order += player
            state = ok(move(state, player, LimitedAction.TAKE_PILE, pile = state.piles.indexOfFirst { it.isNotEmpty() }))
            assertTrue(++guard < 100)
        }
        assertEquals(order.indices.map { seating[it % 3] }, order)
        assertEquals(15, state.pools.values.sumOf { it.size })
        // Everyone's takes are public.
        assertTrue(LimitedEngine.view(state, seating[1]).picks.all { take -> take.cards.all { it != null } })
    }
}
