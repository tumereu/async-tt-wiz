package arcanum.engine.casting

import arcanum.api.CounterKind
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId

/**
 * mh3 sweep: the counters a "remove N counters" cost of no one kind takes ("Remove two counters from this creature", "Remove three
 * counters from among creatures you control"): any counters, of whichever kinds the payer chooses (CR 118.3, 601.2h, 602.2b). The payer
 * picks one counter at a time where there is a choice; where every remaining counter must go, or only one kind is left, they are taken
 * without asking.
 */
internal object CounterCosts {
    /** Where paying stands after the [picks] so far. */
    sealed interface Progress {
        /** Every counter is settled: remove [removals] (object, kind, how many). */
        data class Done(val removals: List<Triple<ObjectId, CounterKind, Int>>) : Progress
        /** The payer picks the next counter among [labels]. */
        data class Ask(val labels: List<String>) : Progress
        /** Not enough counters (or a pick that isn't an option). */
        data object Unpayable : Progress
    }

    /**
     * The counters [n] are taken from: from each of [ids] ([distributed] false) or from among them, after [picks] (labels of earlier
     * answers, in order). [name] names an object in a label when there are several.
     */
    fun progress(state: GameState, ids: List<ObjectId>, n: Int, distributed: Boolean, picks: List<String>, name: (ObjectId) -> String): Progress {
        val left = ids.distinct().associateWith { id ->
            state.obj(id)?.counters.orEmpty().filter { it.count > 0 }.associate { it.kind to it.count }.toMutableMap()
        }
        val labels = labels(left, name)
        val taken = ids.distinct().associateWith { mutableMapOf<CounterKind, Int>() }
        fun take(id: ObjectId, kind: CounterKind, count: Int) {
            left.getValue(id)[kind] = left.getValue(id).getValue(kind) - count
            taken.getValue(id)[kind] = (taken.getValue(id)[kind] ?: 0) + count
        }
        picks.forEach { pick ->
            val (id, kind) = labels.entries.firstOrNull { it.value == pick }?.key ?: return Progress.Unpayable
            if ((left.getValue(id)[kind] ?: 0) <= 0) return Progress.Unpayable
            take(id, kind, 1)
        }
        // what is still to take, by object (from among them: all objects share one need)
        val groups = if (distributed) listOf(ids.distinct()) else ids.distinct().map { listOf(it) }
        for (group in groups) {
            val need = n - group.sumOf { id -> taken.getValue(id).values.sum() }
            if (need <= 0) continue
            val pool = group.flatMap { id -> left.getValue(id).filterValues { it > 0 }.map { (kind, count) -> Triple(id, kind, count) } }
            val available = pool.sumOf { it.third }
            if (available < need) return Progress.Unpayable
            if (pool.size == 1 || available == need) pool.forEach { (id, kind, count) -> take(id, kind, if (pool.size == 1) need else count) }
            else return Progress.Ask(pool.map { (id, kind, _) -> labels.getValue(id to kind) })
        }
        return Progress.Done(taken.flatMap { (id, kinds) -> kinds.filterValues { it > 0 }.map { (kind, count) -> Triple(id, kind, count) } })
    }

    /** A label for each (object, kind): the kind ("+1/+1", "oil"), with the object's name in front when there are several objects. */
    private fun labels(left: Map<ObjectId, Map<CounterKind, Int>>, name: (ObjectId) -> String): Map<Pair<ObjectId, CounterKind>, String> {
        val several = left.size > 1
        val used = mutableSetOf<String>()
        return left.flatMap { (id, kinds) -> kinds.keys.map { id to it } }.associateWith { (id, kind) ->
            val base = arcanum.engine.resolution.Effects.counterLabel(kind).let { if (several) "${name(id)}: $it" else it }
            var label = base
            var i = 2
            while (!used.add(label)) label = "$base (${i++})"
            label
        }
    }
}
