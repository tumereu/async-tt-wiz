package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Experimental combat-damage UI entries, deliberately kept together for easy removal. */
internal fun combatDamageMenuTools(toolkit: TableToolkitController, play: PlayState,
    bound: ToolkitCapture, enabled: Boolean): List<MenuControl> {
    if (CombatStackRules.occurrences(play.board.combat).none { it.id == bound.contextViewId }) return emptyList()
    return listOf("Deal to target" to true, "Deal to target or creatures" to false).map { (name, targetOnly) ->
        MenuControl(name, enabled && play.board.combat?.id == bound.areaId) {
            if (toolkit.dealCombatDamage(bound.viewIds, targetOnly, areaId = bound.areaId)) toolkit.dismiss()
        }
    }
}

/** [dealt] is [CombatDamage.recentDamageEdits]: each damage button locks once its step has been dealt;
 * undoing that damage unlocks it again. */
internal fun combatDamageQuickActions(play: PlayState, dealt: Int = 0): List<QuickAction> {
    val scope = TableActionScope(play.gameId, play.id)
    val area = play.board.combat?.id
    fun action(name: String, tool: CombatTool, enabled: Boolean = true) =
        QuickAction(name, "", TableActionIntent.Combat(scope, tool, area), enabled = enabled)
    return buildList {
        if (CombatDamage.splitSteps(play)) {
            add(action("First strike damage", CombatTool.FIRST_STRIKE_DAMAGE, dealt < 1))
            add(action("Regular damage", CombatTool.REGULAR_DAMAGE, dealt < 2))
        } else add(action("Deal combat damage", CombatTool.DEAL_DAMAGE, dealt < 1))
        add(action("Close + next", CombatTool.DAMAGE_CLOSE_AND_NEXT))
    }
}
