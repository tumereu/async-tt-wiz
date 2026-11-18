package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * Hardening 2026-11-17 C: the game server's connection for [GameSession] tests, delivering the server's messages out of
 * order, twice or late on purpose ([RevisionOrder]'s cases). The test says what the server sends, in the server's order
 * ([serve]); [deliver] hands it to the session arranged as asked ([Arrangement]): as sent, heads first (a head read
 * before the revisions up to it were published), reversed, each twice (a resubscription's replay crossing the live
 * publications), or some held back for a later [deliver] (a revision published late). [settle] runs after each message
 * (the test's main-thread queue).
 */
class ReorderingConnection(private val settle: () -> Unit = {}) : GameConnection {
    lateinit var listener: GameConnection.Listener
    /** What the session sent, in order. */
    val frames = mutableListOf<GameClientMessage>()
    var connects = 0
        private set
    private var generation = 0
    private val queued = mutableListOf<GameServerMessage>()

    override fun connect(token: String, listener: GameConnection.Listener) { connects++; this.listener = listener }
    override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
    override fun disconnect() = Unit
    override fun close() = Unit

    /** The subscriptions the session asked for (each answered by the revisions after its revision, then a head). */
    fun subscribes(): List<SubscribeGame> = frames.filterIsInstance<SubscribeGame>()

    /** [session] (re)connects: the socket opens, the server welcomes it and authorizes [gameId]'s stream. */
    fun connect(session: GameSession, gameId: String) {
        session.tick(); listener.opened(); settle()
        message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
        val stream = frames.mapNotNull { (it as? OpenGameStream)?.streamId ?: (it as? ResumeGameStream)?.streamId }.last()
        message(GameStreamReady(gameId, stream, "${++generation}"))
    }

    /** The socket drops (the session reconnects at its next tick). */
    fun drop() { listener.disconnected(false, "socketFailure"); settle() }

    /** One message handed to the session at once, in its place. */
    fun message(message: GameServerMessage) { listener.message(message); settle() }

    /** The server sends [messages], in this order; nothing reaches the session before [deliver]. */
    fun serve(vararg messages: GameServerMessage) { queued += messages }

    /** What the server sent and the session hasn't been handed yet. */
    val waiting: List<GameServerMessage> get() = queued.toList()

    /** Hands what the server sent to the session, arranged by [arrangement]; what it holds back waits for the next one. */
    fun deliver(arrangement: Arrangement = Arrangement.AsSent) {
        val (now, later) = arrangement.arrange(queued.toList())
        queued.clear(); queued += later
        now.forEach(::message)
    }

    /** How [deliver] hands the server's messages over. */
    sealed interface Arrangement {
        /** The messages to hand over now, in order, and those to keep for later. */
        fun arrange(sent: List<GameServerMessage>): Pair<List<GameServerMessage>, List<GameServerMessage>>

        /** In the server's order. */
        data object AsSent : Arrangement {
            override fun arrange(sent: List<GameServerMessage>) = sent to emptyList<GameServerMessage>()
        }
        /** Every head before every revision: heads read before the revisions up to them were published. */
        data object HeadsFirst : Arrangement {
            override fun arrange(sent: List<GameServerMessage>) =
                (sent.filterIsInstance<GameHead>() + sent.filter { it !is GameHead }) to emptyList<GameServerMessage>()
        }
        /** Last sent first. */
        data object Reversed : Arrangement {
            override fun arrange(sent: List<GameServerMessage>) = sent.reversed() to emptyList<GameServerMessage>()
        }
        /** Everything, then everything again: a resubscription's replay crossing what was published live. */
        data object Twice : Arrangement {
            override fun arrange(sent: List<GameServerMessage>) = (sent + sent) to emptyList<GameServerMessage>()
        }
        /** Everything but what [late] matches, which waits for the next [deliver]: published late, or lost with a socket. */
        class Holding(private val late: (GameServerMessage) -> Boolean) : Arrangement {
            override fun arrange(sent: List<GameServerMessage>) = sent.partition { !late(it) }
        }
    }
}
