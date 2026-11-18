package org.asyncttwiz.android

import org.asyncttwiz.android.ReorderingConnection.Arrangement
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hardening 2026-11-17 C: a game in the list gets the server's messages out of order, twice or late
 * ([ReorderingConnection]); its summary never goes back and the device resubscribes from what it received
 * ([RevisionOrder]). The open game at the table: androidApp's ServerMessageOrderTest (it needs the engine's test cards).
 */
class GameSessionOrderTest {
    init { installTestBuild() }
    private val owner = AccountIdentity(randomUuid(), "Inviter")
    private val recipient = AccountIdentity(randomUuid(), "Recipient")
    private val playId = randomUuid()
    private val base = GameSummary(randomUuid(), "Azure Spark", "2026-10-22T00:00:00Z", owner, recipient, randomUuid(), randomUuid(),
        InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = playId)
    private fun at(revision: Int) = base.copy(revision = "$revision")
    private fun revision(result: Int) = GameRevision(base.id, "${result - 1}", "$result", randomUuid(), owner.accountId,
        GameScope(base.id, playId = playId), at(result))
    private fun head(revision: Int) = GameHead(base.id, "$revision", at(revision))

    private inner class Device : AutoCloseable {
        val posts = ArrayDeque<() -> Unit>()
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        val wire = ReorderingConnection(::drain)
        val shown = mutableListOf<String>()
        val session = GameSession(wire, { "A".repeat(43) }, { posts.add(it) }, {}, { if (it.id == base.id) shown += it.revision },
            { _, _ -> }, {}, accountId = { recipient.accountId }, engineLoad = { false })
        fun revision() = session.summary(at(0)).revision
        /** The summaries the list was told of never went back. */
        fun neverBack() = assertEquals(shown.sortedBy { it.toInt() }, shown, "the list's revisions: $shown")
        override fun close() = session.close()
    }

    private fun listed(at: Int) = Device().also { d ->
        d.session.watch(listOf(at(at)))
        d.wire.connect(d.session, base.id)
        d.wire.message(head(at))
    }

    // SYS-015: a list read is read whenever it was asked for: one ahead of the revisions shows; the revision that follows
    // still applies, and a list read from before it is old
    @Test fun aListReadBeforeItsRevision() = listed(3).use { d ->
        d.session.watch(listOf(at(4)))
        assertEquals("4", d.revision())
        d.wire.message(revision(4))
        d.session.watch(listOf(at(3)))
        assertEquals("4", d.revision())
        d.neverBack()
        assertEquals(1, d.wire.subscribes().size, "a game in the list asks for nothing")
    }

    // SYS-015: a head read before the revisions up to it were published comes first; they still come, nothing goes back
    @Test fun aHeadBeforeItsRevisions() = listed(3).use { d ->
        d.wire.serve(revision(4), revision(5), head(5))
        d.wire.deliver(Arrangement.HeadsFirst)
        assertEquals("5", d.revision())
        d.neverBack()
        assertEquals(1, d.wire.subscribes().size, "a game in the list asks for nothing")
    }

    // SYS-014: each revision twice (a replay crossing the publications) changes nothing the second time
    @Test fun aRepeatedRevision() = listed(3).use { d ->
        d.wire.serve(revision(4), revision(5))
        d.wire.deliver(Arrangement.Twice)
        assertEquals("5", d.revision())
        d.neverBack()
    }

    // SYS-014, SYS-019: after a reconnect the device resubscribes from the revision it received; a live revision before the
    // replay, the replay and its head bring it to the server's
    @Test fun aRevisionAfterAReconnect() = listed(3).use { d ->
        d.wire.serve(revision(4), revision(5))
        d.wire.deliver()
        d.wire.drop()
        d.wire.connect(d.session, base.id)
        assertEquals("5", d.wire.subscribes().last().revision, "from the revision it received")
        d.wire.serve(revision(7), revision(6), revision(7), head(7))
        d.wire.deliver()
        assertEquals("7", d.revision())
        d.neverBack()
        assertTrue(d.session.ready(base.id))
    }
}
