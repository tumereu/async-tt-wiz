package org.asyncttwiz.protocol

import kotlin.test.*

/** DRF-043 (owner, 2026-11-22): random seating with the bot seats spread as evenly as possible between the people. */
class DraftSeatingTest {
    private fun seed(n: Int) = RandomV1.encodeSeed(ByteArray(32) { if (it == 0) n.toByte() else if (it == 1) (n shr 8).toByte() else it.toByte() })
    private fun seat(players: List<String>, n: Int): List<String> {
        val words = RandomV1.Words(seed(n))
        return DraftSeating.seat(players) { RandomV1.sample(it, words::next) }
    }
    private fun people(count: Int) = (1..count).map { "person-$it" }

    /** The bot groups after each person, going round the ring from the first person seated. */
    private fun groups(seating: List<String>): List<Int> {
        val seats = seating.indices.filter { !PodBots.isBot(seating[it]) }
        return seats.indices.map { i ->
            val next = seats[(i + 1) % seats.size].let { if (it <= seats[i]) it + seating.size else it }
            next - seats[i] - 1
        }
    }

    // DRF-043: any number of people and bots: everyone seated once, the bot groups between people differ by at most one
    @Test fun botGroupsBetweenPeopleDifferByAtMostOne() {
        for (humans in 1..8) for (bots in 0..8) for (n in 0 until 20) {
            val players = people(humans) + PodBots.ids(bots)
            val seating = seat(players.shuffled(kotlin.random.Random(n)), n)
            assertEquals(players.sorted(), seating.sorted(), "$humans people, $bots bots")
            val sizes = groups(seating)
            assertEquals(bots, sizes.sum())
            assertTrue(sizes.max() - sizes.min() <= 1, "$humans people, $bots bots: $seating")
        }
    }

    // DRF-043: two people and four bots always sit person, bot, bot, person, bot, bot; two and three person, bot, person, bot, bot
    @Test fun theOwnersExamples() {
        for (n in 0 until 50) {
            assertEquals(listOf(2, 2), groups(seat(people(2) + PodBots.ids(4), n)))
            assertEquals(listOf(1, 2), groups(seat(people(2) + PodBots.ids(3), n)).sorted())
            assertEquals(listOf(1, 1), groups(seat(people(2) + PodBots.ids(2), n)))
        }
    }

    // DRF-043: still random: who sits where among the people, which gaps hold the larger groups, and the ring's rotation
    @Test fun whoSitsWhereAndTheRotationStayRandom() {
        val players = people(3) + PodBots.ids(4)
        val seatings = (0 until 200).map { seat(players, it) }
        // The people's order round the ring, from person 1: both ways round occur
        val orders = seatings.map { seating -> seating.filter { !PodBots.isBot(it) }.let { p -> val i = p.indexOf("person-1"); p.drop(i) + p.take(i) } }
        assertEquals(setOf(listOf("person-1", "person-2", "person-3"), listOf("person-1", "person-3", "person-2")), orders.toSet())
        // Person 1 sits in every seat, and so does a bot in the first one and a person
        assertEquals(players.indices.toSet(), seatings.map { it.indexOf("person-1") }.toSet())
        assertTrue(seatings.any { PodBots.isBot(it.first()) } && seatings.any { !PodBots.isBot(it.first()) })
        // The group of two follows each person sometimes
        for (person in people(3)) assertTrue(seatings.any { s -> val i = s.indexOf(person); PodBots.isBot(s[(i + 1) % s.size]) &&
            PodBots.isBot(s[(i + 2) % s.size]) }, person)
    }

    // The bots take their seats in number order; without bots the seating is the plain shuffle the engines always drew
    @Test fun botsInNumberOrderAndPlainShuffleWithoutBots() {
        for (n in 0 until 20) {
            assertEquals(PodBots.ids(5), seat(people(3) + PodBots.ids(5).reversed(), n).filter(PodBots::isBot))
            val words = RandomV1.Words(seed(n))
            val plain = people(5).sorted().toMutableList()
            for (i in plain.lastIndex downTo 1) {
                val j = RandomV1.sample(i + 1, words::next)
                val value = plain[i]; plain[i] = plain[j]; plain[j] = value
            }
            assertEquals(plain, seat(people(5).reversed(), n))
        }
        // Only bots: in number order
        assertEquals(PodBots.ids(3), seat(PodBots.ids(3).reversed(), 1))
    }

    // DRF-043, DRF-296: every engine with bot seats seats them spread out
    @Test fun everyEngineWithBotSeatsSpreadsThem() {
        val cube = listOf(DraftCubeEntry("a", 60), DraftCubeEntry("b", 60))
        val humans = listOf("alice", "bob")
        for (n in 0 until 10) {
            val booster = DraftEngine.start(humans + PodBots.ids(4), cube, DraftSettings(packsPerPlayer = 1, cardsPerPack = 5, bots = 4), seed(n))
            assertEquals(listOf(2, 2), groups(booster.seating))
            val packs = List(6) { pack -> List(5) { DraftCopy("p$pack-$it", "a") } }
            val prebuilt = DraftEngine.startWithPacks(humans + PodBots.ids(4), packs, DraftSettings(packsPerPlayer = 1, cardsPerPack = 5, bots = 4), seed(n))
            assertEquals(listOf(2, 2), groups(prebuilt.seating))
            val grid = GridEngine.start(humans + PodBots.ids(2), cube, DraftSettings.grid().copy(grids = 2, bots = 2), seed(n))
            assertEquals(listOf(1, 1), groups(grid.seating))
            val rochester = LimitedEngine.startFromCube(humans + PodBots.ids(3), cube,
                DraftSettings.rochester().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 3), seed(n))
            assertEquals(listOf(1, 2), groups(rochester.seating).sorted())
            val rotisserie = LimitedEngine.startFromCube(humans + PodBots.ids(6), cube, DraftSettings.rotisserie().copy(cardsPerPlayer = 5, bots = 6), seed(n))
            assertEquals(listOf(3, 3), groups(rotisserie.seating))
        }
    }
}
