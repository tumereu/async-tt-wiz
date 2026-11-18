package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameRevision
import org.asyncttwiz.protocol.GameWire

/**
 * The one ordering rule for a game's server state on this device (hardening 2026-11-17 C: the "a head or a list read
 * overtakes the revisions" race had been fixed one symptom at a time, five times). [GameSession] puts every message that
 * carries a game's state through it: the stream's revisions, heads, list reads, the stream ready's ended summary.
 *
 * Why messages come out of order: the server numbers a game's revisions one by one, each naming the one before
 * ([GameRevision.baseRevision]), and publishes each a moment after its commit; a head (the heartbeat's, or the answer to a
 * subscription, read after the revisions it replays) and a list read are the game's summary read whenever they were asked
 * for. So a head or a list read can come before the revisions up to it, and a live revision can come before (or again
 * after) what a resubscription replays.
 *
 * Each game has a received revision: the last one whose state the device holds as the server's. The rule:
 * - **Behind**: a revision at or before it, or a head or list read before it, is a repeat or an old read: ignored.
 * - **In order**: a revision on the received one (or any, while nothing is received yet) applies and moves it; the
 *   revisions held after it then apply too, in order.
 * - **Ahead**: what can stand without the revisions in between applies (a summary, a lobby's preparation, an ended game,
 *   an engine head: each is whole). The open play at the table can't: it is built from its revisions' events, and only
 *   they play its holds, animations and ending at the table (HIS-006, HIS-035, HIS-205, TRN-065). So, for a [stepped]
 *   game, a revision ahead is held until the ones before it come, and a head or list read ahead waits for them; either
 *   way the revisions after the received one are asked for ([GameSession]'s askHead: the server replays them, then
 *   sends its head). The play loads from a head only when that head, read after the replay, is still ahead of what it
 *   could follow ([GameSession]'s engineHead: more than a replay carries, or committed meanwhile); it never replays from
 *   the play's start.
 *
 * Main thread. Each decision out of order is logged (what came, what was expected, what was done) through [log], whose
 * repeats are counted ([ConnectionLog]), so a burst of replayed duplicates doesn't push the rest out of a problem report.
 */
class RevisionOrder(private val log: ConnectionLog) {
    private val received = mutableMapOf<String, String>()
    private val held = mutableMapOf<String, MutableList<GameRevision>>()

    /** Where a revision stands against the received one ([revision]). */
    sealed interface Revision {
        /** A repeat of one received (a replay's or a publication's twin): only its own request's result is correlated. */
        data object Behind : Revision
        /** It follows what was received (or stands on its own): it applies. */
        data object InOrder : Revision
        /** It came before the revisions it rests on: held until they come, and they are to be asked for. */
        data object Held : Revision
    }

    /** Where a head or list read stands against the received revision ([snapshot]). */
    sealed interface Snapshot {
        /** Older than what was received: ignored. */
        data object Behind : Snapshot
        /** At the received revision, or ahead and whole on its own: it applies. */
        data object Applies : Snapshot
        /** Ahead of the open play at the table, which moves only by its revisions: it waits for them (asked for). */
        data object Waits : Snapshot
    }

    /** [gameId]'s received revision, null before any. */
    fun received(gameId: String): String? = received[gameId]

    /**
     * Places [message] against [gameId]'s received revision. [stepped]: the game is the open play at the table (its state
     * built from the revisions' events). A held revision stays here until one before it applies ([applied]).
     */
    fun revision(message: GameRevision, stepped: Boolean): Revision {
        val gameId = message.gameId
        val at = received[gameId] ?: return Revision.InOrder
        if (ordinal(message.resultRevision) <= ordinal(at)) {
            log.info("order:behind:$gameId", "Game ${gameId.take(8)}: revision ${message.resultRevision} came again " +
                "(received $at): ignored")
            return Revision.Behind
        }
        if (message.baseRevision == at) return Revision.InOrder
        if (!stepped) {
            log.info("order:gap:$gameId", "Game ${gameId.take(8)}: revision ${message.resultRevision} on ${message.baseRevision} " +
                "came after $at, not open at the table: applies (a summary stands on its own)")
            return Revision.InOrder
        }
        val list = held.getOrPut(gameId) { mutableListOf() }
        if (list.none { it.resultRevision == message.resultRevision }) list += message
        list.sortBy { ordinal(it.resultRevision) }
        if (list.size > MAX_HELD) {
            log.warn("order:overflow:$gameId", "Game ${gameId.take(8)}: ${list.size} revisions held after $at: dropped, " +
                "the revisions after $at asked for")
            held.remove(gameId)
        } else log.info("order:held:$gameId", "Game ${gameId.take(8)}: revision ${message.resultRevision} on ${message.baseRevision} " +
            "came before the revisions after $at: held (${list.size} held), the revisions after $at asked for")
        return Revision.Held
    }

    /** [gameId]'s received revision moves to [revision] (never back): a revision applied in order, or a whole state taken
     * at it. Returns the held revisions that now follow, in order, taken out (each to apply as [revision] places it). */
    fun applied(gameId: String, revision: String): List<GameRevision> {
        val at = received[gameId]
        if (at == null || ordinal(revision) > ordinal(at)) received[gameId] = revision
        return following(gameId)
    }

    /**
     * The open play at the table loaded from a head at [revision] ([GameSession]'s engineHead): the received revision is
     * that head's, also when it is behind what was held (the held revisions at or before it are dropped). Returns the held
     * revisions that now follow.
     */
    fun loaded(gameId: String, revision: String): List<GameRevision> {
        received[gameId] = revision
        held[gameId]?.let { list ->
            val dropped = list.count { ordinal(it.resultRevision) <= ordinal(revision) }
            list.removeAll { ordinal(it.resultRevision) <= ordinal(revision) }
            if (dropped > 0) log.info("order:loaded:$gameId", "Game ${gameId.take(8)}: loaded from the head at $revision, " +
                "$dropped held revisions it covers dropped, ${list.size} still held")
        }
        return following(gameId)
    }

    /**
     * Places a head or list read of [gameId] at [revision] ([source]: what it is, for the log). [stepped]: the game is the
     * open play at the table; [whole]: what it carries stands for the revisions before it (an engine head, an ended game:
     * nothing comes after a cancellation or deletion, and the server sends those as heads).
     */
    fun snapshot(gameId: String, revision: String, source: String, stepped: Boolean, whole: Boolean): Snapshot {
        val at = received[gameId] ?: return Snapshot.Applies
        if (ordinal(revision) < ordinal(at)) {
            log.info("order:old:$source:$gameId", "Game ${gameId.take(8)}: $source at revision $revision came after $at " +
                "was received: ignored")
            return Snapshot.Behind
        }
        if (ordinal(revision) == ordinal(at) || !stepped || whole) return Snapshot.Applies
        log.info("order:waits:$source:$gameId", "Game ${gameId.take(8)}: $source at revision $revision is ahead of the open " +
            "play's $at: the table waits for the revisions after $at (asked for), the read isn't applied")
        return Snapshot.Waits
    }

    /** [gameId] is no longer the open play at the table (left, or its table let go at the end): what was held for it is
     * let go, in order, to apply as it stands (a summary stands on its own). */
    fun letGo(gameId: String): List<GameRevision> {
        val list = held.remove(gameId) ?: return emptyList()
        if (list.isNotEmpty()) log.info("order:letgo:$gameId", "Game ${gameId.take(8)}: no longer open at the table, " +
            "${list.size} held revisions apply as they stand (${list.first().resultRevision}..${list.last().resultRevision})")
        return list.toList()
    }

    /** Whether revisions of [gameId] are held, waiting for the ones before them. */
    fun holding(gameId: String): Boolean = held[gameId].orEmpty().isNotEmpty()

    /** Everything of [gameId] is forgotten (it was cancelled, deleted, or became unavailable). */
    fun forget(gameId: String) { received.remove(gameId); held.remove(gameId) }

    fun clear() { received.clear(); held.clear() }

    private fun following(gameId: String): List<GameRevision> {
        val list = held[gameId] ?: return emptyList()
        val at = received[gameId] ?: return emptyList()
        list.removeAll { ordinal(it.resultRevision) <= ordinal(at) }
        val next = mutableListOf<GameRevision>()
        var tip = at
        while (true) {
            val follower = list.firstOrNull { it.baseRevision == tip } ?: break
            next += follower; list.remove(follower); tip = follower.resultRevision
        }
        if (list.isEmpty()) held.remove(gameId)
        if (next.isNotEmpty()) log.info("order:released:$gameId", "Game ${gameId.take(8)}: the revisions up to $at came, " +
            "${next.size} held after them apply (${next.first().resultRevision}..${next.last().resultRevision})" +
            if (list.isEmpty()) "" else ", ${list.size} still held")
        return next
    }

    private fun ordinal(revision: String) = GameWire.ordinal(revision)

    companion object {
        /** The most revisions held for one game; past it they are dropped and asked for again (a replay brings them). */
        const val MAX_HELD = 128
    }
}
