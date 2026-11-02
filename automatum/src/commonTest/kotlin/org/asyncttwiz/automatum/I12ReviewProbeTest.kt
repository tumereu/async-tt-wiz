package org.asyncttwiz.automatum

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * A printed probe of what the cast panel's steps cost on a busy board (i12review #6, SYS-025): the app runs each of them on
 * the main thread. Prints numbers and asserts only outcomes, never elapsed time (no timing tests). About 180 cards.
 */
class I12ReviewProbeTest {
    private val table = AtwTable("i12probe")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(3)
    private fun seeds() = CastFlow.Seeds { org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32)) }

    @Test fun probeCastPanelStepsOnABusyBoard() {
        table.put(alice, PlayZone.BATTLEFIELD, *Array(25) { "Mountain" })
        table.put(alice, PlayZone.BATTLEFIELD, *Array(20) { "Tinkered Mite" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(25) { "Island" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(20) { "Tinkered Mite" }, tapped = true)
        val spark = table.put(alice, PlayZone.HAND, *Array(4) { "Storm Spark" }).first()
        table.put(bob, PlayZone.HAND, *Array(3) { "Island" })
        table.put(alice, PlayZone.LIBRARY, *Array(40) { "Mountain" })
        table.put(bob, PlayZone.LIBRARY, *Array(40) { "Island" })
        val clock = TimeSource.Monotonic
        val rounds = 6 // the first ones warm up the JVM
        repeat(rounds) { round ->
            val rt = RoundTrip(table.state(), table.definitions)
            var mark = clock.markNow()
            val plays = HandPlays.of(rt.state, table.definitions, alice, emptyList())
            val listing = mark.elapsedNow(); mark = clock.markNow()
            var flow = CastFlow.start(rt.state, table.definitions, alice, emptyList(), plays.of(spark).first(), seeds())
            val start = mark.elapsedNow(); mark = clock.markNow()
            flow = flow.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(bob)))))
            val target = mark.elapsedNow(); mark = clock.markNow()
            val payment = flow.pending as Decision.ManaPayment
            assertTrue(flow.pays(payment.suggestion))
            val pays = mark.elapsedNow(); mark = clock.markNow()
            // The mana section's toggling asks pays() for every use of a source (CastController.toggleMana)
            payment.options.forEach { flow.pays(listOf(it)) }
            val toggleAll = mark.elapsedNow(); mark = clock.markNow()
            flow = flow.answer(Answer.ManaPayment(payment.suggestion))
            val cast = mark.elapsedNow(); mark = clock.markNow()
            rt.apply(flow.call, alice)
            // The engine log of a long game's worth of cast batches' tags (each one different, as in a real history):
            // read whole once, then (the app's EngineLog.Decoder, kept per game) again with one more event, which reads
            // only the new tag (#6 fix: no full-history decode per cast start, follow or listing)
            val tags = rt.tags.filter { !it?.data.isNullOrEmpty() }
            var n = 0
            val history = List(2000 / tags.size.coerceAtLeast(1)) { tags }.flatten().map { it!!.copy(data = it.data + " ".repeat(++n % 64) + "\n".repeat(n / 64)) }
            val decoder = EngineLog.Decoder(table.definitions)
            mark = clock.markNow()
            val whole = decoder.log(history.dropLast(1), rt.state)
            val log = mark.elapsedNow(); mark = clock.markNow()
            val grown = decoder.log(history, rt.state)
            val incremental = mark.elapsedNow()
            assertTrue(grown.size >= whole.size)
            assertTrue(grown == EngineLog.from(history, rt.state, table.definitions), "kept reads give the same log")
            if (round == rounds - 1) println("i12 probe: hand plays $listing, cast start $start, target answer $target, " +
                "pays $pays, pays for all ${payment.options.size} uses $toggleAll, mana answer $cast; " +
                "engine log of ${history.size - 1} tags read whole $log, then with one more tag $incremental")
        }
    }
}
