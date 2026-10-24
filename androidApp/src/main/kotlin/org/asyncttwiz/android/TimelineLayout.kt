package org.asyncttwiz.android

/** The portrait transport replaces the unused 56dp quick-action lane. Landscape has no such lane. */
internal fun timelinePanelHeight(landscape: Boolean) = if (landscape) 52f else 116f
internal fun timelineHudOffset(landscape: Boolean) = timelinePanelHeight(landscape) - if (landscape) 0f else 56f

/** Transform only screen-fixed personal HUD geometry. World coordinates/home/camera stay untouched. */
internal fun timelineLayout(scene: TableScene, viewer: String, width: Float, height: Float, landscape: Boolean): TableScene {
    val offset = timelineHudOffset(landscape)
    fun TableBounds.lift() = copy(y = y - offset)
    fun TableBounds.trim() = copy(height = (this.height - offset).coerceAtLeast(0f))
    val hud = scene.items.filter { it.owner == viewer && (it.kind == TableItemKind.HAND ||
        it.zone == org.asyncttwiz.protocol.PlayZone.HAND || it.viewport == "dock/$viewer" ||
        it.viewport == "counters/$viewer") }.map { it.viewport }.toSet()
    val world = scene.world ?: return scene
    val visible = world.visibleViewport.trim()
    val worldPorts = scene.fields.map { it.containerId }.toSet() + "combat"
    return TablePresentation.clip(scene.copy(
        world = world.copy(clipViewport = visible),
        fields = scene.fields.map { it.copy(bounds = it.bounds.intersection(visible) ?: it.bounds.copy(height = 0f)) },
        items = scene.items.map { if (it.viewport in hud) it.copy(bounds = it.bounds.lift(), footprint = it.footprint.lift()) else it },
        viewports = scene.viewports.map { port -> when {
            port.id in hud -> port.copy(bounds = port.bounds.lift())
            port.id in worldPorts -> port.copy(bounds = port.bounds.intersection(visible) ?: port.bounds.copy(height = 0f))
            port.id == "sidebar" -> port.copy(bounds = port.bounds.trim())
            else -> port
        } }), width, height)
}
