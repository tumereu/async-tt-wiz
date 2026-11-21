package org.asyncttwiz.protocol

import kotlin.random.Random
import kotlin.test.*

/**
 * DRF-119, DRF-302 (owner, 2026-11-21, problem report MT74XQ): a member's own draft is done, and their deck can be submitted, as soon
 * as no more picks or cards can reach them, whatever the others still draft. Whole drafts are played with random choices;
 * afterwards every step's answer is checked against what really happened to each member later on.
 */
class DraftOwnDoneTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 13 + 3).toByte() })

    /** One step of a played draft: each member's [waits], their pool sizes ([pools]) and their turns taken so far ([turns]:
     * their picks, Grid's skipped turns included). */
    private class Step(val waits: Map<String, PodDraftWait?>, val pools: Map<String, Int>, val turns: Map<String, Int>)

    /**
     * Checks each step's answers against the rest of the draft: done (null) exactly when the member's pool never changes
     * again; [PodDraftWait.Picks] exactly when they act again; [PodDraftWait.Cards] when only cards come; [piles] modes say
     * [PodDraftWait.Piles] until the end.
     */
    private fun verify(name: String, steps: List<Step>, final: Step, piles: Boolean = false) {
        assertTrue(steps.isNotEmpty())
        for ((index, step) in steps.withIndex()) for ((player, wait) in step.waits) {
            val actsAgain = final.turns.getValue(player) > step.turns.getValue(player)
            val poolChanges = final.pools.getValue(player) != step.pools.getValue(player)
            val where = "$name step $index $player"
            when {
                piles -> assertEquals(PodDraftWait.Piles, wait, where)
                !actsAgain && !poolChanges -> assertNull(wait, "$where: nothing more reaches them")
                actsAgain -> assertEquals(PodDraftWait.Picks, wait, where)
                else -> assertEquals(PodDraftWait.Cards, wait, "$where: cards still come without a pick")
            }
        }
    }

    // ---- Booster, Glimpse, Custom booster ----

    private fun booster(players: Int, packs: Int, cards: Int, keep: Int = 1, burn: Int = 0, discard: Int = 0, open: Int = 1,
        mode: DraftMode = DraftMode.BOOSTER, passes: List<DraftPass> = emptyList()): DraftState = DraftEngine.startWithPacks(
        List(players) { "p$it" }, List(players * packs) { pack -> List(cards) { DraftCopy("c$pack-$it", "d$pack-$it") } },
        DraftSettings(mode = mode, packsPerPlayer = packs, cardsPerPack = cards, keep = keep, burn = burn, discardRemainingAt = discard,
            packsOpenAtOnce = open, colorBalance = false, passes = passes), seed)

    private fun boosterPick(state: DraftState, player: String, random: Random): DraftState {
        val id = state.queues.getValue(player).first()
        val cards = state.packs.first { it.id == id }.cards.shuffled(random)
        val taken = state.log.count { !it.automatic && it.packId == id }
        val keep = state.settings.keepAt(taken)
        val burn = if (state.settings.keepsOnly(cards.size, keep.toLong(), taken)) emptyList() else cards.drop(keep).take(state.settings.burnAt(taken))
        val result = DraftEngine.pick(state, DraftPick(player, id, cards.take(keep), burn))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun playBooster(name: String, initial: DraftState, random: Random) {
        var state = initial
        val steps = mutableListOf<Step>()
        fun snapshot() = Step(state.seating.associateWith { DraftEngine.waitFor(state, it) },
            state.seating.associateWith { state.pools.getValue(it).size },
            state.seating.associateWith { player -> state.log.count { it.player == player && !it.automatic } })
        var guard = 0
        while (!state.finished) {
            assertTrue(guard++ < 5000, "$name must make progress")
            steps += snapshot()
            state = boosterPick(state, state.seating.filter { state.queues.getValue(it).isNotEmpty() }.random(random), random)
        }
        steps += snapshot()
        assertTrue(state.seating.all { DraftEngine.waitFor(state, it) == null })
        verify(name, steps, steps.last())
    }

    @Test fun boosterGlimpseAndCustomBoosterAreDoneOnceNothingMoreReachesTheMember() {
        val configs = listOf(
            // The last card of each pack is given on arrival: a member whose picks are over may still wait for it
            "two players" to booster(players = 2, packs = 3, cards = 15),
            "three, two open" to booster(players = 3, packs = 4, cards = 5, open = 2),
            // A discard threshold: a member can be done while another still picks
            "discard at 1" to booster(players = 2, packs = 1, cards = 3, discard = 1),
            "discard, keep two" to booster(players = 3, packs = 3, cards = 7, keep = 2, burn = 1, discard = 1),
            "glimpse" to booster(players = 3, packs = 5, cards = 15, burn = 2),
            "custom booster" to booster(players = 4, packs = 2, cards = 15, mode = DraftMode.CUSTOM_BOOSTER,
                passes = listOf(DraftPass(2, 1), DraftPass(1, 2), DraftPass(1, 1), DraftPass(1, 0))),
        )
        for ((index, config) in configs.withIndex()) repeat(3) { run -> playBooster(config.first, config.second, Random(index * 10 + run)) }
    }

    // MT74XQ's shape in a pack mode: with a discard threshold the first member is done after their two picks while the
    // other still has one to make
    @Test fun aBoosterMemberCanBeDoneWhileTheOtherStillPicks() {
        var state = booster(players = 2, packs = 1, cards = 3, discard = 1)
        val (first, second) = state.seating
        val random = Random(1)
        assertEquals(PodDraftWait.Picks, DraftEngine.waitFor(state, first))
        state = boosterPick(state, first, random)
        state = boosterPick(state, second, random)
        state = boosterPick(state, first, random)
        assertFalse(state.finished)
        assertNull(DraftEngine.waitFor(state, first))
        assertEquals(PodDraftWait.Picks, DraftEngine.waitFor(state, second))
        assertNull(DraftEngine.waitFor(state, "stranger"))
    }

    // A pack's last card comes without a pick: after their last pick the member still waits for it
    @Test fun aLastCardStillToComeKeepsTheDraftOpen() {
        var state = booster(players = 2, packs = 1, cards = 2)
        val (first, second) = state.seating
        state = boosterPick(state, first, Random(2))
        assertEquals(PodDraftWait.Cards, DraftEngine.waitFor(state, first))
        assertEquals(PodDraftWait.Picks, DraftEngine.waitFor(state, second))
        state = boosterPick(state, second, Random(2))
        assertTrue(state.finished)
        assertNull(DraftEngine.waitFor(state, first))
    }

    // ---- Arena ----

    @Test fun arenaIsDoneAfterTheMembersOwnLastPick() {
        val cards = ArenaEngine.cubeCards(List(40) { DraftCubeEntry("card-$it", 1) }) { SetCardRarity.COMMON }
        var state = ArenaEngine.start(listOf("ann", "bob"), cards,
            DraftSettings.arena().copy(packsPerPlayer = 3, cardsPerPack = 3, keep = 1, initialRares = 0, ignoreRarity = true), seed, cube = true)
        repeat(3) {
            assertEquals(PodDraftWait.Picks, DraftEngine.waitFor(state, "ann"))
            val head = DraftEngine.privateView(state, "ann").head!!
            state = assertNotNull(DraftEngine.pick(state, DraftPick("ann", head.id, listOf(head.cards.first().id))).state)
        }
        // MT74XQ: Ann is done though Bob has made no pick
        assertFalse(state.finished)
        assertNull(DraftEngine.waitFor(state, "ann"))
        assertEquals(PodDraftWait.Picks, DraftEngine.waitFor(state, "bob"))
        assertEquals(0, state.pools.getValue("bob").size)
    }

    // ---- Grid ----

    @Test fun gridIsDoneAfterTheMembersLastTurn() {
        val lines = GridLineType.entries.flatMap { type -> List(3) { GridLine(type, it) } }
        for ((index, settings) in listOf(DraftSettings.grid().copy(grids = 5), DraftSettings.grid().copy(grids = 6, gridsOpenAtOnce = 2),
                DraftSettings.grid().copy(grids = 4, twoPicks = true)).withIndex()) for (players in listOf(2, 3)) {
            if (settings.twoPicks && players == 3) continue
            val random = Random(index * 7 + players)
            val count = settings.supply(players, emptyList()).needed.toInt()
            var state = GridEngine.start(List(players) { "p$it" }, List(count) { DraftCubeEntry("definition-$it", 1) }, settings, seed)
            val steps = mutableListOf<Step>()
            // A turn skipped on an emptied board (twoPicks) counts as a turn: whether one comes depends on the lines chosen
            fun snapshot() = Step(state.seating.associateWith { GridEngine.waitFor(state, it) },
                state.seating.associateWith { state.pools.getValue(it).size },
                state.seating.associateWith { player -> state.log.count { it.player == player } })
            while (!state.finished) {
                val player = state.seating.filter { state.queues[it].orEmpty().isNotEmpty() }.random(random)
                steps += snapshot()
                val id = state.queues.getValue(player).first()
                val board = state.grids.first { it.id == id }.positions
                val line = lines.filter { line -> line.positions().any { board[it] != null } }.random(random)
                state = assertNotNull(GridEngine.pick(state, GridPick(player, id, line)).state)
            }
            steps += snapshot()
            verify("grid $index with $players", steps, steps.last())
        }
    }

    // ---- Rochester, Rotisserie, Winston, Winchester ----

    private fun cube(size: Int) = List(size) { DraftCubeEntry("card-" + it.toString().padStart(3, '0'), 1) }

    /** Table [index] of a pile draft: the state's own fields for the first, [LimitedDraftState.tables] after it. */
    private fun table(state: LimitedDraftState, index: Int) =
        if (index == 0) LimitedTable(state.stack, state.piles, state.turn, state.pile) else state.tables[index - 1]

    private fun limitedMove(state: LimitedDraftState, player: String, random: Random): LimitedDraftState {
        val entry = state.queues.getValue(player).first()
        fun act(move: LimitedMove) = LimitedEngine.act(state, move)
        val result = when (state.settings.mode) {
            DraftMode.ROTISSERIE -> {
                val taken = state.log.flatMap { it.cards }.toSet()
                act(LimitedMove(player, LimitedAction.TAKE_CARD, state.turn, state.board.filter { it !in taken }.random(random)))
            }
            DraftMode.ROCHESTER -> {
                val pack = state.packs.single { it.id == entry }
                act(LimitedMove(player, LimitedAction.TAKE_CARD, pack.picked.size, pack.cards.filter { it !in pack.picked }.random(random), pack.id))
            }
            DraftMode.WINSTON -> {
                val index = entry.substringAfter(":", "0").toInt()
                val table = table(state, index)
                val pass = act(LimitedMove(player, LimitedAction.PASS_PILE, table.turn, pile = table.pile, table = index))
                if (random.nextBoolean() && pass.state != null) pass
                else act(LimitedMove(player, LimitedAction.TAKE_PILE, table.turn, pile = table.pile, table = index))
                    .takeIf { it.state != null } ?: pass
            }
            else -> {
                val index = entry.substringAfter(":", "0").toInt()
                val table = table(state, index)
                act(LimitedMove(player, LimitedAction.TAKE_PILE, table.turn,
                    pile = table.piles.indices.filter { table.piles[it].isNotEmpty() }.random(random), table = index))
            }
        }
        return assertNotNull(result.state, "rejected: ${result.reason}")
    }

    private fun playLimited(name: String, initial: LimitedDraftState, random: Random) {
        var state = initial
        val steps = mutableListOf<Step>()
        fun snapshot() = Step(state.seating.associateWith { LimitedEngine.waitFor(state, it) },
            state.seating.associateWith { state.pools.getValue(it).size },
            state.seating.associateWith { player -> state.log.count { it.player == player } })
        var guard = 0
        while (!state.finished) {
            assertTrue(guard++ < 5000, "$name must make progress")
            steps += snapshot()
            state = limitedMove(state, state.seating.filter { state.queues.getValue(it).isNotEmpty() }.random(random), random)
        }
        steps += snapshot()
        assertTrue(state.seating.all { LimitedEngine.waitFor(state, it) == null })
        val piles = state.settings.mode == DraftMode.WINSTON || state.settings.mode == DraftMode.WINCHESTER
        // The pile engines' last step answers null (finished); every earlier one says a pile can still come
        verify(name, steps.dropLast(1), steps.last(), piles)
        verify(name, steps.takeLast(1), steps.last())
    }

    @Test fun rochesterAndRotisserieAreDoneAfterTheMembersLastPick() {
        val players = listOf("ann", "bob", "cat")
        val configs = listOf(
            "rochester, two" to LimitedEngine.startFromCube(players.take(2), cube(40), DraftSettings.rochester().copy(packsPerPlayer = 2, cardsPerPack = 5), seed),
            "rochester, three" to LimitedEngine.startFromCube(players, cube(60), DraftSettings.rochester().copy(packsPerPlayer = 2, cardsPerPack = 7), seed),
            "rochester, all open" to LimitedEngine.startFromCube(players, cube(60), DraftSettings.rochester().copy(packsPerPlayer = 2, cardsPerPack = 5, tablePacksOpen = 6), seed),
            "rotisserie" to LimitedEngine.startFromCube(players, cube(40), DraftSettings.rotisserie().copy(cardsPerPlayer = 5), seed),
            "rotisserie, two" to LimitedEngine.startFromCube(players.take(2), cube(40), DraftSettings.rotisserie().copy(cardsPerPlayer = 7, poolPerPlayer = 7), seed),
        )
        for ((index, config) in configs.withIndex()) repeat(3) { run -> playLimited(config.first, config.second, Random(index * 10 + run)) }
    }

    // Winston and Winchester: who takes the last cards depends on everyone's choices, so a pile can come to anyone until the
    // last one is taken
    @Test fun winstonAndWinchesterAreDoneOnlyWhenTheLastPileIsTaken() {
        val players = listOf("ann", "bob", "cat")
        val configs = listOf(
            "winston" to LimitedEngine.startFromCube(players.take(2), cube(40), DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 12), seed),
            "winston, two tables" to LimitedEngine.startFromCube(players, cube(40), DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 10, tables = 2), seed),
            "winchester" to LimitedEngine.startFromCube(players.take(2), cube(40), DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 12), seed),
            "winchester, three" to LimitedEngine.startFromCube(players, cube(40), DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 10, tables = 2), seed),
        )
        for ((index, config) in configs.withIndex()) repeat(3) { run -> playLimited(config.first, config.second, Random(index * 10 + run)) }
    }

    @Test fun sealedIsDoneFromTheStart() {
        val state = LimitedEngine.startFromCube(listOf("ann", "bob"), cube(40), DraftSettings.sealed().copy(cardsPerPlayer = 20), seed)
        assertNull(LimitedEngine.waitFor(state, "ann"))
    }
}
