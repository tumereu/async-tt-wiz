package org.asyncttwiz.protocol

/**
 * Mode-independent draft scheduling, shared by the pack engine ([DraftEngine]) and the Grid engine ([GridEngine]).
 *
 * Items (packs or grids) belong to a zero-based round and an opener, the member who gets them first. The schedule
 * tracks who holds each live item: a member's visible [queues] (they act only on the head) or [held] items that
 * reached them ahead of their [opened] round and wait unseen. Every member opens round r + 1 on their own as soon as
 * no live or unopened item of their opened rounds can still reach them for a pick; items that are neither queued nor
 * held are unopened.
 *
 * Modes supply the item facts and pick rules: how many picks an item had (queue order), whether an item can still
 * reach a member, and what happens automatically on arrival (pack awards, empty-grid skips). Subclasses are
 * transition-local scratch space; nothing caller-owned is mutated.
 */
internal abstract class DraftSchedule(
    protected val seating: List<String>,
    private val rounds: Int,
    queues: Map<String, List<String>>,
    held: Map<String, List<String>>,
    opened: Map<String, Int>,
) {
    protected val queues = seating.associateWith { queues[it].orEmpty().toMutableList() }
    protected val held = seating.associateWith { held[it].orEmpty().toMutableList() }
    protected val opened = seating.associateWith { opened.getValue(it) }.toMutableMap()

    /** Every item of the draft, in creation order (the order a member's own new items join their queue). */
    protected abstract val items: List<String>
    protected abstract fun round(item: String): Int
    protected abstract fun opener(item: String): String
    /** Whether the item can still be picked from at all. */
    protected abstract fun live(item: String): Boolean
    /** Picks already taken from the item; within a round a member's queue puts fresher items first. */
    protected abstract fun picks(item: String): Int
    /** Whether [item], now with [holder] (null while unopened), can still come to [player] for a pick. */
    protected abstract fun reaches(item: String, holder: String?, player: String): Boolean
    /** Called on arrival in an opened round: resolve the item without a pick (and pass it on) and return true, or queue it. */
    protected open fun automatic(item: String, player: String): Boolean = false

    fun queue(player: String): List<String> = queues.getValue(player)

    /** The item leaves the member's queue for a pick. */
    protected fun take(player: String, item: String) { queues.getValue(player).remove(item) }

    /**
     * The item reaches [player]: held unseen when its round is ahead of theirs, resolved by [automatic], or queued.
     * A visible queue runs by round, then picks already taken (fresh items first), then arrival. An arrival never goes
     * ahead of the head of a non-empty queue unless [protectHead] is false (a member opening a round with an empty
     * queue), since the member may be looking at or marking that head.
     */
    protected fun arrive(item: String, player: String, protectHead: Boolean = true) {
        if (round(item) > opened.getValue(player)) held.getValue(player).add(item)
        else if (!automatic(item, player)) {
            val queue = queues.getValue(player)
            fun rank(id: String) = round(id).toLong() * 1_000_000 + picks(id)
            val rank = rank(item)
            val start = if (protectHead && queue.isNotEmpty()) 1 else 0
            val at = (start until queue.size).firstOrNull { rank(queue[it]) > rank } ?: queue.size
            queue.add(at, item)
        }
    }

    /** Live items of rounds up to [upTo] that can still reach [player] for a pick. */
    protected fun reaching(player: String, upTo: Int): Sequence<String> {
        val holders = (queues.entries + held.entries).flatMap { (owner, ids) -> ids.map { it to owner } }.toMap()
        return items.asSequence().filter { live(it) && round(it) <= upTo && reaches(it, holders[it], player) }
    }

    /** Items outside [player]'s queue that can still come to them for a pick. */
    fun incoming(player: String): Int {
        val queued = queues.getValue(player).toSet()
        return reaching(player, opened.getValue(player)).count { it !in queued }
    }

    /**
     * Each member opens their own next-round items once nothing they opened can come back to them for a pick. An
     * opening only adds items that the other members' checks already counted as unopened, so seat order does not
     * change the result. Rounds emptied by automatic resolution cascade.
     */
    protected fun open() {
        val last = rounds - 1
        do {
            var changed = false
            for (player in seating) {
                val current = opened.getValue(player)
                if (current >= last || reaching(player, current).any()) continue
                opened[player] = current + 1
                // Released held items arrived before the member's own items open; the queue rule orders them all.
                // An empty queue has no head to protect, so fresh own items can come before half-picked held ones.
                val released = held.getValue(player).filter { round(it) <= current + 1 }
                held.getValue(player).removeAll(released.toSet())
                val protectHead = queues.getValue(player).isNotEmpty()
                for (id in released) arrive(id, player, protectHead)
                for (id in items) if (round(id) == current + 1 && opener(id) == player) arrive(id, player, protectHead)
                changed = true
            }
        } while (changed)
    }

    val finished: Boolean get() = opened.values.all { it >= rounds - 1 } && items.none(::live)
    /** Lowest round a member is still in, or the round count once the draft is finished. */
    val round: Int get() = if (finished) rounds else opened.values.min()
    fun openedRounds(): Map<String, Int> = opened.toMap()
    fun queueSnapshot(): Map<String, List<String>> = queues.mapValues { it.value.toList() }
    fun heldSnapshot(): Map<String, List<String>> = held.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
}
