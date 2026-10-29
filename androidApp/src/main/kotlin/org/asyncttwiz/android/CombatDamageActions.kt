package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Experimental combat-damage UI entries, deliberately kept together for easy removal.
 * Always both cells; a real card deals through its own combat copies, disabled when it has none. */
internal fun combatDamageMenuTools(toolkit: TableToolkitController, play: PlayState,
    bound: ToolkitCapture, enabled: Boolean): List<MenuControl> {
    val occurrences = CombatStackRules.occurrences(play.board.combat)
    val ids = bound.viewIds.filter { id -> occurrences.any { it.id == id } }
        .ifEmpty { occurrences.filter { it.sourceId in bound.sourceIds }.map { it.id } }
    return listOf("Deal to target" to true, "Deal to target or creatures" to false).map { (name, targetOnly) ->
        MenuControl(name, enabled && ids.isNotEmpty() && play.board.combat?.id == bound.areaId) {
            if (toolkit.dealCombatDamage(ids, targetOnly, areaId = bound.areaId)) toolkit.dismiss()
        }
    }
}

internal fun combatDamageQuickActions(play: PlayState): List<QuickAction> {
    val scope = TableActionScope(play.gameId, play.id)
    val area = play.board.combat?.id
    fun action(name: String, tool: CombatTool) = QuickAction(name, "", TableActionIntent.Combat(scope, tool, area))
    return buildList {
        if (CombatDamage.splitSteps(play)) {
            add(action("First strike damage", CombatTool.FIRST_STRIKE_DAMAGE))
            add(action("Regular damage", CombatTool.REGULAR_DAMAGE))
        } else add(action("Deal combat damage", CombatTool.DEAL_DAMAGE))
        add(action("Close + next", CombatTool.DAMAGE_CLOSE_AND_NEXT))
    }
}
