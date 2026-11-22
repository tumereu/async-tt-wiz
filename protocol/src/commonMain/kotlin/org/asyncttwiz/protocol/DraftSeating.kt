package org.asyncttwiz.protocol

/**
 * A draft's seating (DRF-043, owner 2026-11-22): random, but bot seats ([PodBots]) are spread as evenly as possible between
 * the people, so the groups of bots between two neighbouring people differ by at most one (two people and four bots always
 * sit person, bot, bot, person, bot, bot; two people and three bots person, bot, person, bot, bot). Which person sits
 * where, which gaps hold the larger groups and the ring's rotation are random; the bots take their seats in number order.
 * Without bots it is the plain shuffle every engine always used, drawing the same numbers.
 */
object DraftSeating {
    /** [players] seated, each random choice drawn from [sample] (a number in 0 until its bound); independent of input order. */
    fun seat(players: List<String>, sample: (Int) -> Int): List<String> {
        val sorted = players.sorted()
        val bots = sorted.filter(PodBots::isBot).sortedWith(compareBy({ PodBots.number(it) ?: Int.MAX_VALUE }, { it }))
        if (bots.isEmpty()) return shuffled(sorted, sample)
        val people = shuffled(sorted.filterNot(PodBots::isBot), sample)
        if (people.isEmpty()) return bots
        // Each person is followed by a group of bots; the groups differ by at most one, the larger ones in random gaps
        val base = bots.size / people.size
        val groups = shuffled(List(people.size) { base + if (it < bots.size % people.size) 1 else 0 }, sample)
        val pattern: List<String?> = people.flatMapIndexed { index, person -> listOf(person) + List(groups[index]) { null } }
        val shift = sample(pattern.size)
        val numbered = bots.iterator()
        return List(pattern.size) { pattern[(it + shift) % pattern.size] }.map { it ?: numbered.next() }
    }

    private fun <T> shuffled(values: List<T>, sample: (Int) -> Int): List<T> {
        val result = values.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = sample(i + 1)
            val value = result[i]; result[i] = result[j]; result[j] = value
        }
        return result
    }
}
