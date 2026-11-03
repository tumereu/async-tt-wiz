package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class TableActionScope(val gameId: String, val playId: String)
/** CMB-028, CMB-034: stage the given creatures as attackers of the active target; clear the staging or the blocks
 * (declaring is the step arrow's, TRN-063). */
enum class CombatTool { AUTOMATUM_STAGE, AUTOMATUM_CLEAR }
enum class EndingTool { SUGGEST_WIN, SUGGEST_LOSS, SUGGEST_DRAW, ACCEPT, CONCEDE }

/** The table host's routes for the table's actions; they introduce no second request/prediction queue. */
interface TableActionRoutes {
    fun combat(intent: TableActionIntent.Combat)
    fun ending(intent: TableActionIntent.Ending)
    /** Next step, End turn or a chosen step through Automatum (TRN-057): whether it was taken. */
    fun automatumStep(intent: TableActionIntent): Boolean = false
    /** The viewer's opening (I-D49): "Mulligan" or "Keep"; whether it was taken. */
    fun opening(intent: TableActionIntent): Boolean = false
}

sealed interface TableActionIntent {
    val scope: TableActionScope
    data class Next(override val scope: TableActionScope) : TableActionIntent
    data class End(override val scope: TableActionScope) : TableActionIntent
    /** A later step records every step passed on the way (rules v33). [pass]: go on into [position] itself as a
     * passed step, for a combined action that acts inside it before its own final step change. */
    data class Choose(override val scope: TableActionScope, val position: TurnPosition, val pass: Boolean = false) : TableActionIntent
    data class Combat(override val scope: TableActionScope, val tool: CombatTool,
        val areaId: String?, val sourceIds: List<String> = emptyList()) : TableActionIntent
    data class Ending(override val scope: TableActionScope, val tool: EndingTool,
        val suggestionId: String? = null, val note: String = "") : TableActionIntent
    /** I-D49: the viewer's London mulligan with Automatum on (hand back, shuffle, draw the opening hand again). */
    data class OpeningMulligan(override val scope: TableActionScope) : TableActionIntent
    /** I-D49: the viewer keeps their hand with Automatum on: Ready, after putting the owed cards on the bottom. */
    data class Keep(override val scope: TableActionScope) : TableActionIntent
}

class TableActionController(private val state: () -> PlayState?, private val canAct: () -> Boolean,
    private val routes: TableActionRoutes) {

    private fun current(scope: TableActionScope): PlayState? = state()?.takeIf {
        canAct() && it.gameId == scope.gameId && it.id == scope.playId
    }
    fun scope(): TableActionScope? = state()?.let { TableActionScope(it.gameId, it.id) }
    fun enabled(scope: TableActionScope) = current(scope) != null

    fun perform(intent: TableActionIntent): Boolean {
        current(intent.scope) ?: return false
        when (intent) {
            is TableActionIntent.Next, is TableActionIntent.End, is TableActionIntent.Choose -> return routes.automatumStep(intent)
            is TableActionIntent.OpeningMulligan, is TableActionIntent.Keep -> return routes.opening(intent)
            is TableActionIntent.Combat -> routes.combat(intent)
            is TableActionIntent.Ending -> routes.ending(intent)
        }
        return true
    }
}
