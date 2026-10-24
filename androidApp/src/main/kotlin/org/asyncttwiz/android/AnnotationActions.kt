package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.math.floor
import kotlin.math.hypot
import androidx.compose.runtime.staticCompositionLocalOf

/** Menus use the table's existing annotation route, including its captured history context. */
internal data class TableMenuAnnotations(val reference: EventReference, val ping: (EventPingIntent) -> Boolean)
internal val LocalTableMenuAnnotations = staticCompositionLocalOf<TableMenuAnnotations?> { null }

/** The LOCATION domain is a logical point, not a container/card endpoint or a screen pixel.
 * Use the same footprint/camera/own-side inversion as table grid destinations. Interleave logical
 * rows by the stable inviter/recipient side because LOCATION has no container ID; signed rows are
 * retained, including personal camera margins. The inverse is shared by markers and input. */
internal class TableAnnotationGeometry(private val scene: TableScene, private val firstPlayer: String) {
    private fun cell(value: Float) = floor(value / scene.footprint).toInt()
    private fun rowHeight(field: TableField) = field.rowHeight

    fun location(point: TablePoint): PingTarget? {
        val field = scene.fields.firstOrNull { it.bounds.contains(point) } ?: return null
        if (!scene.worldInput(point)) return null
        val x = cell(point.x - field.gridBounds.x)
        val y = floor((if (field == scene.fields.last()) field.gridBounds.bottom - point.y else point.y - field.gridBounds.y) / rowHeight(field)).toInt()
        return PingTarget(PingTargetKind.LOCATION, position = BoardPosition(x, y * 2 + if (field.owner == firstPlayer) 0 else 1))
    }

    fun endpoint(target: PingTarget): TablePoint? {
        if (target.kind != PingTargetKind.LOCATION) return scene.items.firstOrNull { when (target.kind) {
            PingTargetKind.CARD, PingTargetKind.OCCURRENCE -> it.cardId == target.id
            PingTargetKind.PLAYER -> it.owner == target.id && it.kind == TableItemKind.LIFE
            PingTargetKind.AREA -> it.id == target.id
            PingTargetKind.LOCATION -> false
        } }?.visibleAnchor
        val position = target.position ?: return null
        val side = Math.floorMod(position.y, 2)
        val field = scene.fields.singleOrNull { (if (it.owner == firstPlayer) 0 else 1) == side } ?: return null
        val x = field.gridBounds.x + (position.x + .5f) * scene.footprint
        val y = (Math.floorDiv(position.y, 2) + .5f) * rowHeight(field)
        val point = TablePoint(x, if (field == scene.fields.last()) field.gridBounds.bottom - y else field.gridBounds.y + y)
        return point.takeIf { field.bounds.contains(it) && scene.worldInput(it) }
    }

    fun locations(field: TableField): List<Pair<PingTarget, TablePoint>> {
        val left = cell(field.bounds.x - field.gridBounds.x)
        val rowHeight = rowHeight(field)
        val top = floor((if (field == scene.fields.last()) field.gridBounds.bottom - field.bounds.bottom else field.bounds.y - field.gridBounds.y) / rowHeight).toInt()
        val right = cell(field.bounds.right - field.gridBounds.x)
        val bottom = top + kotlin.math.ceil(field.bounds.height / rowHeight).toInt()
        return (top..bottom).flatMap { row -> (left..right).mapNotNull { column ->
            val target = PingTarget(PingTargetKind.LOCATION,
                position = BoardPosition(column, row * 2 + if (field.owner == firstPlayer) 0 else 1))
            endpoint(target)?.takeIf { scene.hit(it) == null }?.let { target to it }
        } }
    }

    fun capture(play: PlayState, reference: EventReference, point: TablePoint): EventPingIntent? {
        if (reference.playId != play.id || reference.nodeId != play.currentNodeId) return null
        val hit = scene.hit(point)
        // A shortcut belongs to its captured card even if it covers another card's ping marker.
        if (hit?.id == "combatBubble/ping") {
            val card = scene.items.find { it.cardId == scene.combatBubbles?.entryId }
            return tablePingTarget(play, card)?.let { tablePingIntent(play, reference, it) }
        }
        // A drawn marker addresses its recorded reference, not the newer node produced by its Ping.
        val marker = active(play).asReversed().firstOrNull { record -> endpoint(record.target)?.let {
            hypot(point.x - it.x, point.y - it.y) <= 10f
        } == true }
        if (marker != null) return EventPingIntent(marker.reference.reference, listOf(marker.target), false)
        val target = if (hit == null) location(point) else tablePingTarget(play, hit)
        return target?.let { tablePingIntent(play, reference, it) }
    }

    private fun active(play: PlayState) = play.currentGroup?.id?.let { EventAnnotations.activePings(play.annotations, it) }.orEmpty()
}

/** Capture an explicit operation; a later group change must never turn Unping into Ping. */
internal fun tablePingIntent(play: PlayState, reference: EventReference, target: PingTarget): EventPingIntent {
    val existing = play.currentGroup?.id?.let { EventAnnotations.activePings(play.annotations, it) }
        ?.lastOrNull { it.target == target }
    return EventPingIntent(existing?.reference?.reference ?: reference, listOf(target), existing == null)
}
