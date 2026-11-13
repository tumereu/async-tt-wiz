package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.BigInteger

// A change flash on the table (TBL-022): what changed (life, a counter, a corner value, ...) and by how much; the engine
// side works them out with each transition ([EngineGame.Transition.changes]), the table shows them ([TableChanges]).

enum class TableChangeKind { LIFE, PLAYER_COUNTER, CORNER, COUNTER, NOTE, OWNER, FACE, LINK, TEXT_MARK, STEP }
data class TableChangeKey(val target: String, val kind: TableChangeKind, val color: CounterColor? = null,
    val line: Int? = null)
data class TableChange(val key: TableChangeKey, val delta: List<BigInteger> = emptyList(), val owner: String? = null,
    val link: PlayLink? = null, val projectionLink: ProjectionLink? = null)
data class ActiveTableChange(val change: TableChange, val started: Long, val serial: Long) {
    val tag: String get() = change.delta.joinToString("/") {
        when { it.signum() < 0 -> it.toString()
            it.signum() == 0 && change.delta.any { n -> n.signum() < 0 } -> "-0"
            else -> "+$it" }
    }
}
