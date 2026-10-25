package org.asyncttwiz.android

import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class LatestReviewTest {
    @Test fun supersededPendingReviewsAreNotExecutedIncludingWhileAnotherRuns() {
        val tasks = ArrayDeque<Runnable>()
        val latest = LatestReview(Executor { tasks.add(it) })
        val ran = mutableListOf<Int>()
        latest.submit("game") {
            ran.add(1)
            latest.submit("game") { ran.add(2) }
            latest.submit("game") { ran.add(3) }
        }
        tasks.removeFirst().run()
        assertEquals(listOf(1), ran)
        tasks.removeFirst().run()
        assertEquals(listOf(1, 3), ran)
        repeat(100) { n -> latest.submit("game") { ran.add(n + 10) } }
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(1, 3, 109), ran)
        latest.submit("game") { fail("LIVE supersedes queued historical review") }
        latest.cancel("game")
        tasks.removeFirst().run()
    }
}
