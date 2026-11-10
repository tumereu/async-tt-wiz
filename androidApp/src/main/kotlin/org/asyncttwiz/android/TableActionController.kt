package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class TableActionScope(val gameId: String, val playId: String)
/** CMB-028, CMB-034: stage the given creatures as attackers of the active target; clear the staging or the blocks
 * (declaring is the step arrow's, TRN-063). */
/** [ASSUME_CLEAR]: CMB-040, the attacker clears the defender's blocks they placed to assume. */
enum class CombatTool { AUTOMATUM_STAGE, AUTOMATUM_CLEAR, ASSUME_CLEAR }
enum class EndingTool { SUGGEST_WIN, SUGGEST_LOSS, SUGGEST_DRAW, ACCEPT, CONCEDE }

/** The table host's routes for the table's actions; they introduce no second request/prediction queue. */
interface TableActionRoutes {
    fun combat(intent: TableActionIntent.Combat)
    fun ending(intent: TableActionIntent.Ending)
    /** Next step or End turn through Automatum (TRN-057): whether it was taken. */
    fun automatumStep(intent: TableActionIntent): Boolean = false
    /** The viewer's opening (I-D49): "Mulligan" or "Keep"; whether it was taken. */
    fun opening(intent: TableActionIntent): Boolean = false
}

sealed interface TableActionIntent {
    val scope: TableActionScope
    data class Next(override val scope: TableActionScope) : TableActionIntent
    data class End(override val scope: TableActionScope) : TableActionIntent
    data class Combat(override val scope: TableActionScope, val tool: CombatTool,
        val areaId: String?, val sourceIds: List<String> = emptyList()) : TableActionIntent
    data class Ending(override val scope: TableActionScope, val tool: EndingTool,
        val suggestionId: String? = null, val note: String = "") : TableActionIntent
    /** I-D49: the viewer's London mulligan with Automatum on (hand back, shuffle, draw the opening hand again). */
    data class OpeningMulligan(override val scope: TableActionScope) : TableActionIntent
    /** I-D49: the viewer keeps their hand with Automatum on: Ready, after putting the owed cards on the bottom. */
    data class Keep(override val scope: TableActionScope) : TableActionIntent
}

/** [canAct]: the viewer may act on the table now (their move); [atTable]: they are at the table at all, whoever's move it is
 * (TRN-064, CR 104.3a: a concession needs nobody's agreement, any time). */
class TableActionController(private val state: () -> TableBoard?, private val canAct: () -> Boolean,
    private val routes: TableActionRoutes, private val atTable: () -> Boolean = canAct) {

    private fun current(scope: TableActionScope, allowed: () -> Boolean = canAct): TableBoard? = state()?.takeIf {
        allowed() && it.gameId == scope.gameId && it.playId == scope.playId
    }
    fun scope(): TableActionScope? = state()?.let { TableActionScope(it.gameId, it.playId) }
    fun enabled(scope: TableActionScope) = current(scope) != null
    /** TRN-064: Concede works whenever the viewer is at the table, also while it isn't their move. */
    fun concedeEnabled(scope: TableActionScope) = current(scope, atTable) != null

    fun perform(intent: TableActionIntent): Boolean {
        val concession = intent is TableActionIntent.Ending && intent.tool == EndingTool.CONCEDE
        if (current(intent.scope, if (concession) atTable else canAct) == null) {
            if (concession) AppLog.warn("game", "Concede not taken: not at the table")
            return false
        }
        if (concession) AppLog.info("game", "Concede pressed" + if (canAct()) "" else " while it isn't the player's move")
        when (intent) {
            is TableActionIntent.Next, is TableActionIntent.End -> return routes.automatumStep(intent)
            is TableActionIntent.OpeningMulligan, is TableActionIntent.Keep -> return routes.opening(intent)
            is TableActionIntent.Combat -> routes.combat(intent)
            is TableActionIntent.Ending -> routes.ending(intent)
        }
        return true
    }
}
