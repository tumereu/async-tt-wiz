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
}
