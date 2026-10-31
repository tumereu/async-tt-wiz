package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal fun basicMoveTools(play: PlayState, names: Map<String, String>, enabled: Boolean, scope: TableScope,
    sources: Map<String, TableMoveSource>, request: (TableIntent) -> Boolean, dismiss: () -> Unit): List<MenuControl> = buildList {
    val context = CombatStackRules.liveSource(play.board, scope.contextId) ?: return@buildList
    val owner = context.owner
    val current = play.board.containers.single { it.id == context.containerId }
    // A Temp pile is LIBRARY-zoned, but every ordinary zone is a real destination from it.
    val currentZone = current.zone.takeUnless { current.temp }
    for (zone in listOf(PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
        if (context.token && zone in setOf(PlayZone.HAND, PlayZone.LIBRARY)) continue
        val target = play.board.zoneContainer(owner, zone) ?: continue
        val choices = if (zone == PlayZone.LIBRARY) listOf(false, true) else listOf(false)
        for (bottom in choices) {
            val label = moveZoneLabel(zone, bottom)
            // Battlefield: no cell is chosen here; each card takes its normal row's first free cell when sent.
            val destination = if (zone == PlayZone.BATTLEFIELD) TableDestination.Grid.auto(target.id)
                else TableDestination.Container(target.id, zone, bottom)
            add(MenuControl(label, enabled && currentZone != zone) { if (request(TableIntent.Move(scope, destination, sources))) dismiss() })
        }
    }
    if (!context.token) add(MenuControl("Shuffle into library", enabled && currentZone != PlayZone.LIBRARY) {
        val invocation = java.util.UUID.randomUUID().toString()
        val library = play.board.zoneContainer(owner, PlayZone.LIBRARY) ?: return@MenuControl
        val libraries = scope.cardIds.mapNotNull { id -> CombatStackRules.liveSource(play.board, id)?.owner }
            .distinct().mapNotNull { account -> play.board.zoneContainer(account, PlayZone.LIBRARY) }
        val intents = listOf(TableIntent.Move(scope, TableDestination.Container(library.id, PlayZone.LIBRARY), sources, invocation)) +
            libraries.map { target -> TableIntent.Submit(PlayAction.ShuffleLibrary(target.id,
                RandomV1.encodeSeed(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })), invocation) }
        if (request(TableIntent.Batch(intents, invocation))) dismiss()
    })
    val stack = play.board.containers.single { it.zone == PlayZone.STACK }
    // Always shown; disabled where a card cannot go to the Stack (or is already there).
    add(MenuControl("Move to Stack", enabled && current.zone != PlayZone.STACK && scope.cardIds.all { id ->
        CombatStackRules.liveSource(play.board, id)?.let { CombatStackRules.canProject(play.board, it) } == true }) {
        if (request(TableIntent.Move(scope, TableDestination.Container(stack.id, stack.zone), sources))) dismiss()
    })
}
