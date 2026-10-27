package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DraftEngineTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun settings(packs: Int = 1, cards: Int = 5, keep: Int = 1, burn: Int = 0, open: Int = 1) =
        DraftSettings(packsPerPlayer = packs, cardsPerPack = cards, keep = keep, burn = burn,
            packsOpenAtOnce = open, colorBalance = false)
    private fun cube(count: Int) = List(count) { DraftCubeEntry("definition-$it", 1) }
    private fun start(settings: DraftSettings = settings(), players: Int = 2, extra: Int = 0) = DraftEngine.start(
        List(players) { "player-$it" }, cube(players * settings.packsPerPlayer * settings.cardsPerPack + extra), settings, seed)

    private fun choice(state: DraftState, player: String): DraftPick {
        val id = state.queues.getValue(player).first()
        val pack = state.packs.first { it.id == id }
        return DraftPick(player, id, pack.cards.take(state.settings.keep),
            if (pack.cards.size <= state.settings.keep + state.settings.burn) emptyList()
            else pack.cards.drop(state.settings.keep).take(state.settings.burn))
    }

    private fun pick(state: DraftState, player: String): DraftState {
        val result = DraftEngine.pick(state, choice(state, player))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun next(state: DraftState) = pick(state, state.seating.first { state.queues.getValue(it).isNotEmpty() })

    /** Picks the head pack of the lowest round, so members who opened the next round early wait until it starts. */
    private fun nextLowest(state: DraftState) = pick(state, state.seating.filter { state.queues.getValue(it).isNotEmpty() }
        .minBy { player -> state.packs.first { it.id == state.queues.getValue(player).first() }.round })

    private fun finish(initial: DraftState, check: (DraftState) -> Unit = {}): DraftState {
        var state = initial
        var actions = 0
        while (!state.finished) {
            check(state)
            assertTrue(actions++ < 10000, "Draft must make progress")
            state = next(state)
        }
        check(state)
        return state
    }

    @Test fun presetsAndSettingsBoundaries() {
        assertEquals(DraftSettings(DraftMode.BOOSTER, 3, 15, 1, 0, 0, 1, true, null), DraftSettings.booster())
        assertEquals(DraftSettings(DraftMode.GLIMPSE, 9, 15, 1, 2, 0, 1, true, null), DraftSettings.glimpse())
        val base = DraftSettings.booster()
        for (players in 2..8) assertTrue(base.validate(players).isEmpty())
        for (players in listOf(-1, 0, 1, 9)) assertContains(base.validate(players), DraftSettingsIssue.PLAYERS)
        val invalid = listOf(
            base.copy(packsPerPlayer = 0) to DraftSettingsIssue.PACKS_PER_PLAYER,
            base.copy(packsPerPlayer = 31) to DraftSettingsIssue.PACKS_PER_PLAYER,
            base.copy(cardsPerPack = 0) to DraftSettingsIssue.CARDS_PER_PACK,
            base.copy(cardsPerPack = 31) to DraftSettingsIssue.CARDS_PER_PACK,
            base.copy(keep = 0) to DraftSettingsIssue.KEEP,
            base.copy(burn = -1) to DraftSettingsIssue.BURN,
            base.copy(keep = 14, burn = 2) to DraftSettingsIssue.KEEP_AND_BURN,
            base.copy(keep = Int.MAX_VALUE, burn = Int.MAX_VALUE) to DraftSettingsIssue.KEEP_AND_BURN,
            base.copy(discardRemainingAt = -1) to DraftSettingsIssue.DISCARD_REMAINING_AT,
            base.copy(discardRemainingAt = 16) to DraftSettingsIssue.DISCARD_REMAINING_AT,
            base.copy(packsOpenAtOnce = 0) to DraftSettingsIssue.PACKS_OPEN_AT_ONCE,
            base.copy(packsOpenAtOnce = 4) to DraftSettingsIssue.PACKS_OPEN_AT_ONCE,
            base.copy(sideboardLimit = -1) to DraftSettingsIssue.SIDEBOARD_LIMIT,
        )
        for ((settings, reason) in invalid) assertContains(settings.validate(2), reason)
        for (settings in listOf(settings(cards = 1), settings(packs = 30, cards = 30, keep = 30, open = 30),
            base.copy(discardRemainingAt = 15, sideboardLimit = 0), base.copy(keep = 10, burn = 5))) {
            assertTrue(settings.validate(2).isEmpty(), settings.toString())
        }
    }

    @Test fun supplyAllowsExactCubeAndRejectsTooSmall() {
        val settings = DraftSettings.glimpse().copy(packsPerPlayer = 18, burn = 4)
        assertEquals(DraftSupply(540, 540), settings.supply(2, cube(540)))
        assertTrue(settings.supply(2, cube(540)).sufficient)
        assertTrue(settings.supply(2, cube(541)).sufficient)
        assertFalse(settings.supply(2, cube(539)).sufficient)
        assertEquals(36, DraftEngine.start(listOf("a", "b"), cube(540), settings, seed).packs.size)
        assertFailsWith<IllegalArgumentException> { DraftEngine.start(listOf("a", "b"), cube(539), settings, seed) }
        val counted = listOf(DraftCubeEntry("same", 539), DraftCubeEntry("other", 1))
        assertEquals(540, settings.supply(2, counted).available)
        // Counted supply does not overflow Int before promotion.
        assertEquals(2L * Int.MAX_VALUE, settings.supply(2, listOf(DraftCubeEntry("a", Int.MAX_VALUE),
            DraftCubeEntry("b", Int.MAX_VALUE))).available)
    }

    @Test fun invalidSourcesAndMembersFailBeforeDrafting() {
        val settings = settings()
        assertFailsWith<IllegalArgumentException> { DraftEngine.start(listOf("a"), cube(10), settings, seed) }
        assertFailsWith<IllegalArgumentException> { DraftEngine.start(listOf("a", "a"), cube(10), settings, seed) }
        assertFailsWith<IllegalArgumentException> { DraftEngine.start(listOf("a", ""), cube(10), settings, seed) }
        for (bad in listOf(DraftCubeEntry("bad", 0), DraftCubeEntry("bad", -1), DraftCubeEntry("", 1))) {
            assertFailsWith<IllegalArgumentException> { DraftEngine.start(listOf("a", "b"), cube(10) + bad, settings, seed) }
        }
        assertFailsWith<IllegalArgumentException> {
            DraftEngine.start(listOf("a", "b"), listOf(DraftCubeEntry("same", 5, setOf(DraftColor.W)),
                DraftCubeEntry("same", 5, setOf(DraftColor.U))), settings, seed)
        }
    }

    @Test fun countedCopiesAreStableUniqueAndNeverReplaced() {
        val entries = listOf(DraftCubeEntry("a", 7), DraftCubeEntry("a", 3), DraftCubeEntry("b", 20))
        val initial = DraftEngine.start(listOf("a", "b"), entries, settings(packs = 2), seed)
        assertEquals(30, initial.copies.size)
        assertEquals(30, initial.copies.map { it.id }.toSet().size)
        assertEquals(10, initial.copies.count { it.definition == "a" })
        assertEquals(20, initial.packs.flatMap { it.cards }.toSet().size)
        val combined = DraftEngine.start(listOf("a", "b"), listOf(DraftCubeEntry("a", 10), DraftCubeEntry("b", 20)),
            settings(packs = 2), seed)
        assertEquals(initial, combined)
        val final = finish(initial)
        assertEquals(20, final.pools.values.flatten().toSet().size)
        assertTrue(final.packs.all { it.cards.isEmpty() })
        assertEquals(final.log.flatMap { it.kept }.toSet(), final.pools.values.flatten().toSet())
    }

    @Test fun balancingGuaranteesAvailableColorsWithoutInventingCopies() {
        val entries = DraftColor.entries.map { DraftCubeEntry(it.name, 10, setOf(it)) }
        val state = DraftEngine.start(listOf("a", "b"), entries, settings(packs = 3, cards = 5).copy(colorBalance = true), seed)
        val copies = state.copies.associateBy { it.id }
        for (pack in state.packs) assertEquals(DraftColor.entries.toSet(), pack.cards.flatMap { copies.getValue(it).colors }.toSet())
        assertEquals(30, state.packs.flatMap { it.cards }.toSet().size)
        val scarce = listOf(DraftCubeEntry("white", 1, setOf(DraftColor.W)), DraftCubeEntry("blue", 1, setOf(DraftColor.U)),
            DraftCubeEntry("multi", 1, DraftColor.entries.toSet()), DraftCubeEntry("blank", 27))
        val scarceState = DraftEngine.start(listOf("a", "b"), scarce, settings(packs = 3).copy(colorBalance = true), seed)
        val dealt = scarceState.packs.flatMap { it.cards }
        assertEquals(30, dealt.size)
        assertEquals(30, dealt.distinct().size)
        assertEquals(scarceState.copies.map { it.id }.toSet(), dealt.toSet())
        // Packs smaller than five are valid too; balancing is a heuristic, not a quota.
        assertEquals(4, start(settings(cards = 2).copy(colorBalance = true)).packs.sumOf { it.cards.size })
    }

    @Test fun seededDraftIsDeterministicIncludingResultsAndInputOrder() {
        val settings = settings(packs = 4, cards = 7, burn = 2, open = 3).copy(colorBalance = true)
        val entries = List(56) { DraftCubeEntry("d-$it", 1, setOf(DraftColor.entries[it % 5])) }
        val a = DraftEngine.start(listOf("a", "b"), entries, settings, seed)
        val b = DraftEngine.start(listOf("b", "a"), entries.reversed(), settings, seed)
        assertEquals(a, b)
        assertEquals(finish(a), finish(b))
        assertNotEquals(a, DraftEngine.start(listOf("a", "b"), entries, settings, RandomV1.encodeSeed(ByteArray(32) { 9 })))
        val seatings = (0..15).map { byte ->
            DraftEngine.start(listOf("a", "b", "c"), cube(84), settings, RandomV1.encodeSeed(ByteArray(32) { byte.toByte() })).seating
        }
        assertTrue(seatings.distinct().size > 1)
    }

    @Test fun twoAndThreePlayerPassingUsesFifoAndFlipsEachRound() {
        for (players in listOf(2, 3)) {
            var state = start(settings(packs = 3, cards = 4), players)
            for (round in 0..2) {
                assertEquals(round, state.round)
                assertEquals(if (round % 2 == 0) DraftDirection.NEXT_SEAT else DraftDirection.PREVIOUS_SEAT, state.direction)
                val actor = state.seating[0]
                val target = state.seating[if (round % 2 == 0) 1 else players - 1]
                val pack = state.queues.getValue(actor).first()
                val targetHead = state.queues.getValue(target).first()
                state = pick(state, actor)
                assertEquals(listOf(targetHead, pack), state.queues.getValue(target))
                assertTrue(state.queues.getValue(actor).isEmpty())
                state = pick(state, target)
                assertEquals(pack, state.queues.getValue(target).first())
                // Explicitly verify this same pack passes again, rather than remaining with the recipient.
                val nextTarget = state.seating[(state.seating.indexOf(target) + if (round % 2 == 0) 1 else players - 1) % players]
                state = pick(state, target)
                assertEquals(pack, state.queues.getValue(nextTarget).last())
                while (!state.finished && state.round == round) state = nextLowest(state)
            }
            assertTrue(state.finished)
            assertEquals(players * 3 * 4, state.pools.values.sumOf { it.size })
        }
    }

    @Test fun multipleOpenPacksAreFifoAndFinalRoundOpensOnlyRemainder() {
        for (open in listOf(1, 3)) {
            var state = start(settings(packs = 4, cards = 4, open = open), players = 3)
            assertTrue(state.queues.values.all { it.size == open })
            for (round in 0 until state.settings.rounds) {
                assertEquals(round, state.round)
                val expected = minOf(open, 4 - round * open)
                assertTrue(state.queues.values.all { it.size == expected })
                val player = state.seating.first()
                val original = state.queues.getValue(player)
                val other = state.seating[if (round % 2 == 0) 1 else 2]
                state = pick(state, player)
                assertEquals(original.drop(1), state.queues.getValue(player))
                assertEquals(original.first(), state.queues.getValue(other).last())
                while (!state.finished && state.round == round) state = nextLowest(state)
            }
            assertTrue(state.finished)
        }
    }

    @Test fun roundBarrierHoldsWhileACurrentPackCanStillReachTheMember() {
        var state = start(settings(packs = 2, cards = 3))
        val a = state.seating[0]
        val b = state.seating[1]
        val held = state.queues.getValue(b).first()
        val future = state.packs.filter { it.round == 1 }
        state = pick(state, a)
        // B's original pack will still reach A for a pick. A cannot open another round early.
        assertEquals(0, state.round)
        assertTrue(state.queues.getValue(a).isEmpty())
        assertEquals(held, state.queues.getValue(b).first())
        assertEquals(future, state.packs.filter { it.round == 1 })
        state = pick(state, b)
        state = pick(state, b) // empties the passed pack via an automatic award to A
        assertEquals(1, state.openedRound(b)) // only A's final pick of B's pack remains; B opens early
        assertEquals(0, state.round)
        assertTrue(state.packs.any { it.round == 0 && it.cards.isNotEmpty() })
        state = pick(state, a)
        assertEquals(1, state.round)
        assertTrue(state.packs.filter { it.round == 0 }.all { it.cards.isEmpty() })
        assertEquals(future, state.packs.filter { it.round == 1 })
    }

    @Test fun automaticAwardsOnArrivalNeedNoActionAndCascadeAcrossRounds() {
        val allAuto = start(settings(packs = 7, cards = 2, keep = 2, open = 3), players = 3)
        assertTrue(allAuto.finished)
        assertTrue(allAuto.queues.values.all { it.isEmpty() })
        assertTrue(allAuto.log.all { it.automatic })
        assertEquals(21, allAuto.log.size)
        assertTrue(allAuto.pools.values.all { it.size == 14 })
        var state = start(settings(cards = 3, keep = 2))
        val actor = state.seating[0]
        val recipient = state.seating[1]
        val queued = state.queues.getValue(recipient)
        val awarded = state.packs.first { it.id == state.queues.getValue(actor).first() }.cards.last()
        state = pick(state, actor)
        assertEquals(queued, state.queues.getValue(recipient))
        assertEquals(listOf(awarded), state.pools.getValue(recipient))
        assertTrue(state.log.last().automatic)
        assertEquals(listOf(awarded), state.log.last().kept)
        state = pick(state, recipient)
        assertTrue(state.finished)
        assertEquals(listOf(2, 1, 2, 1), state.log.map { it.kept.size })
        assertEquals(3, state.pools.getValue(actor).size)
        assertEquals(3, state.pools.getValue(recipient).size)
    }

    @Test fun nearEndRequiresOnlyKeepsAndAllowsAnExactExplicitBurnList() {
        for (explicit in listOf(false, true)) {
            var state = start(settings(cards = 7, keep = 2, burn = 2))
            val a = state.seating[0]
            val b = state.seating[1]
            val id = state.queues.getValue(a).first()
            state = pick(state, a) // three left, passes to B
            state = pick(state, b) // B's own initial pack
            val cards = state.packs.first { it.id == id }.cards
            assertEquals(3, cards.size)
            val result = DraftEngine.pick(state, DraftPick(b, id, cards.take(2), if (explicit) cards.drop(2) else emptyList()))
            state = assertNotNull(result.state)
            assertNull(result.reason)
            assertEquals(cards.take(2), state.log.last().kept)
            assertEquals(cards.drop(2), state.log.last().burned)
            assertFalse(state.log.last().automatic)
            assertTrue(state.packs.first { it.id == id }.cards.isEmpty())
        }
        val exact = start(settings(cards = 3, keep = 1, burn = 2))
        val request = choice(exact, exact.seating.first())
        assertTrue(request.burn.isEmpty())
        val after = assertNotNull(DraftEngine.pick(exact, request).state)
        assertEquals(2, after.log.last().burned.size)
    }

    @Test fun discardThresholdRemovesRemainderBeforePassingOrAutomaticAward() {
        for (threshold in listOf(2, 3, 5)) {
            val initial = start(settings(cards = 5, keep = 2, burn = 1).copy(discardRemainingAt = threshold))
            val actor = initial.seating.first()
            val original = choice(initial, actor)
            val after = pick(initial, actor)
            val event = after.log.single()
            assertEquals(2, event.discarded.size)
            assertFalse(event.automatic)
            assertEquals(original.keep, after.pools.getValue(actor))
            assertTrue(after.packs.first { it.id == original.packId }.cards.isEmpty())
            assertEquals(1, after.queues.getValue(initial.seating[1]).size)
        }
        val final = finish(start(settings(cards = 6, keep = 1, burn = 1).copy(discardRemainingAt = 1)))
        assertTrue(final.log.all { it.discarded.isEmpty() }) // no positive remainder at/below threshold
    }

    @Test fun invalidPicksHaveStableReasonsAndNeverMutateTheSource() {
        val state = start(settings(cards = 8, keep = 2, burn = 1, packs = 3, open = 3))
        val snapshot = Json.encodeToString(state)
        val actor = state.seating[0]
        val other = state.seating[1]
        val good = choice(state, actor)
        val cards = state.packs.first { it.id == good.packId }.cards
        fun rejected(request: DraftPick, reason: DraftPickRejection) {
            val result = DraftEngine.pick(state, request)
            assertEquals(reason, result.reason, request.toString())
            assertNull(result.state)
            assertEquals(snapshot, Json.encodeToString(state))
        }
        rejected(good.copy(player = "outsider"), DraftPickRejection.NOT_YOUR_PACK)
        rejected(good.copy(player = other), DraftPickRejection.NOT_YOUR_PACK)
        rejected(good.copy(packId = "missing"), DraftPickRejection.NOT_YOUR_PACK)
        rejected(good.copy(packId = state.queues.getValue(actor)[1]), DraftPickRejection.NOT_HEAD_OF_QUEUE)
        rejected(good.copy(keep = listOf("missing", cards[0])), DraftPickRejection.UNKNOWN_CARD)
        rejected(good.copy(burn = listOf("missing")), DraftPickRejection.UNKNOWN_CARD)
        rejected(good.copy(keep = emptyList()), DraftPickRejection.WRONG_COUNTS)
        rejected(good.copy(keep = cards.take(3)), DraftPickRejection.WRONG_COUNTS)
        rejected(good.copy(keep = listOf(cards[0], cards[0])), DraftPickRejection.WRONG_COUNTS)
        rejected(good.copy(burn = listOf(cards[0])), DraftPickRejection.WRONG_COUNTS)
        rejected(good.copy(burn = emptyList()), DraftPickRejection.WRONG_COUNTS)
        rejected(good.copy(burn = cards.drop(2).take(2)), DraftPickRejection.WRONG_COUNTS)
        pick(state, actor)
        assertEquals(snapshot, Json.encodeToString(state)) // accepted transitions are immutable too
        assertEquals(DraftPickRejection.DRAFT_FINISHED, DraftEngine.pick(finish(state), good).reason)
        val nearEnd = start(settings(cards = 4, keep = 2, burn = 2))
        val nearChoice = choice(nearEnd, nearEnd.seating.first())
        val rest = nearEnd.packs.first { it.id == nearChoice.packId }.cards.drop(2)
        assertEquals(DraftPickRejection.WRONG_COUNTS,
            DraftEngine.pick(nearEnd, nearChoice.copy(burn = rest.take(1))).reason)
    }

    @Test fun projectionsRevealOnlyOwnQueueAndAwardsAndNoCardsInPublicStatus() {
        var state = start(settings(packs = 3, cards = 5, burn = 1, open = 3), extra = 5)
        state = pick(state, state.seating.first())
        val copies = state.copies.associateBy { it.id }
        val public = DraftEngine.publicStatus(state)
        assertEquals(1, public.round)
        for (member in public.members) {
            assertEquals(state.queues.getValue(member.player).size, member.packsWaiting)
            assertEquals(state.log.count { it.player == member.player }, member.picksMade)
            assertFalse(member.finished)
        }
        val publicJson = Json.encodeToString(public)
        for (copy in state.copies) {
            assertFalse(copy.id in publicJson)
            assertFalse(copy.definition in publicJson)
        }
        for (player in state.seating) {
            val view = DraftEngine.privateView(state, player)
            val queue = state.queues.getValue(player)
            val expected = state.packs.first { it.id == queue.first() }
            assertEquals(expected.cards.map(copies::getValue), view.head?.cards)
            assertEquals(queue.size - 1, view.packsBehind)
            assertEquals(queue.drop(1), view.queuedPacks.map { it.id })
            assertEquals(queue.drop(1).flatMap { id -> state.packs.first { it.id == id }.cards }.map(copies::getValue),
                view.queuedPacks.flatMap { it.cards })
            assertEquals(state.pools.getValue(player).map(copies::getValue), view.pool)
            val exposed = (view.head?.cards.orEmpty() + view.queuedPacks.flatMap { it.cards } + view.pool).map { it.id }.toSet()
            val privateJson = Json.encodeToString(view)
            for (copy in state.copies.filter { it.id !in exposed }) assertFalse("\"${copy.id}\"" in privateJson)
            for (pack in state.packs.filter { it.id !in queue }) assertFalse("\"${pack.id}\"" in privateJson)
        }
        assertFailsWith<IllegalArgumentException> { DraftEngine.privateView(state, "outsider") }
        val final = finish(state)
        assertTrue(DraftEngine.publicStatus(final).members.all { it.finished && it.packsWaiting == 0 })
        assertNull(DraftEngine.privateView(final, final.seating.first()).head)
        assertEquals(0, DraftEngine.privateView(final, final.seating.first()).packsBehind)
        assertTrue(DraftEngine.privateView(final, final.seating.first()).queuedPacks.isEmpty())
    }

    @Test fun serializationRoundTripCanResumeWithoutSeedOrCatalog() {
        var state = start(settings(packs = 4, cards = 7, burn = 2, open = 3))
        repeat(5) { state = next(state) }
        val decoded = Json.decodeFromString<DraftState>(Json.encodeToString(state))
        assertEquals(state, decoded)
        assertEquals(finish(state), finish(decoded))
        val final = finish(decoded)
        assertEquals(final, Json.decodeFromString<DraftState>(Json.encodeToString(final)))
        val view = DraftEngine.privateView(state, state.seating.first())
        assertEquals(view, Json.decodeFromString<DraftPrivateView>(Json.encodeToString(view)))
    }

    @Test fun synergyCubeUsesAll540CopiesAndAwards54PerPlayerWithOneOrThreeOpenPacks() {
        for (open in listOf(1, 3)) {
            val settings = DraftSettings.glimpse().copy(packsPerPlayer = 18, burn = 4, packsOpenAtOnce = open)
            val initial = start(settings)
            val all = initial.packs.flatMap { it.cards }.toSet()
            assertEquals(540, all.size)
            val final = finish(initial) { state ->
                val awards = state.pools.values.flatten()
                val removed = state.log.flatMap { it.burned + it.discarded }
                val circulatingAndUnopened = state.packs.flatMap { it.cards }
                val accounted = awards + removed + circulatingAndUnopened
                assertEquals(540, accounted.size)
                assertEquals(all, accounted.toSet())
            }
            assertTrue(final.pools.values.all { it.size == 54 })
            assertEquals(108, final.log.size)
            assertEquals(432, final.log.sumOf { it.burned.size })
            assertTrue(final.log.all { !it.automatic && it.discarded.isEmpty() })
            assertEquals(if (open == 1) 18 else 6, final.settings.rounds)
            assertTrue(final.packs.all { it.cards.isEmpty() })
        }
    }

    @Test fun asynchronousSchedulesConserveCopiesForTwoThroughEightPlayersAndVariedRules() {
        val scheduler = RandomV1.Words(seed)
        for (players in 2..8) {
            for (open in listOf(1, 3)) {
                val settings = settings(packs = 4, cards = 9, keep = 1 + players % 3, burn = players % 4, open = open)
                    .copy(discardRemainingAt = players % 3, colorBalance = true)
                val size = players * 4 * 9
                val cube = DraftColor.entries.map { DraftCubeEntry("color-${it.name}", size / 5 + 1, setOf(it)) }
                var state = DraftEngine.start(List(players) { "p-$it" }, cube, settings, seed)
                val dealt = state.packs.flatMap { it.cards }.toSet()
                var steps = 0
                while (!state.finished) {
                    assertTrue(steps++ <= size)
                    val ready = state.seating.filter { state.queues.getValue(it).isNotEmpty() }
                    val actor = ready[RandomV1.sample(ready.size, scheduler::next)]
                    state = pick(state, actor)
                    val accounted = state.packs.flatMap { it.cards } + state.log.flatMap { it.kept + it.burned + it.discarded }
                    assertEquals(size, accounted.size)
                    assertEquals(dealt, accounted.toSet())
                    for (player in state.seating) {
                        assertEquals(state.log.filter { it.player == player }.flatMap { it.kept }, state.pools.getValue(player))
                    }
                    val queued = state.queues.values.flatten()
                    assertEquals(queued.size, queued.toSet().size)
                    if (!state.finished) assertEquals(state.seating.minOf { state.openedRound(it) }, state.round)
                    for (pack in state.packs) {
                        val opened = pack.round <= state.openedRound(pack.initialPlayer)
                        if (pack.id in queued) {
                            assertTrue(opened && pack.round >= state.round)
                            assertTrue(pack.cards.size > settings.keep)
                        } else if (opened) assertTrue(pack.cards.isEmpty())
                        else assertEquals(9, pack.cards.size)
                    }
                }
                assertEquals(state, Json.decodeFromString<DraftState>(Json.encodeToString(state)))
            }
        }
    }
}
