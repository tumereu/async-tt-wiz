package org.asyncttwiz.android

/**
 * TBL-240 (owner 2026-11-25): where each card's combat chips and badges lie on the table (table dp, by item id), as the combat
 * overlay last laid them out. They take no touch of their own: a touch there is the card's ([hit]), so a tap acts on the card and
 * holding inspects it, as anywhere on the card. Chips that take a touch (a toggle the viewer changes, an amber cost chip) are over
 * the table and get theirs first. Touched on the main thread only.
 */
class CombatChipAreas {
    private val areas = LinkedHashMap<String, TableBounds>()

    fun put(id: String, bounds: TableBounds) { areas.remove(id); areas[id] = bounds }
    fun remove(id: String) { areas.remove(id) }

    /** The item whose chips lie at [point] (the one laid out last where several overlap), or null. */
    fun hit(point: TablePoint): String? = areas.entries.lastOrNull { it.value.contains(point) }?.key

    /** [scene]'s item whose chips lie at [point], else what [scene] has there itself. */
    fun item(scene: TableScene, point: TablePoint): TableItem? =
        hit(point)?.let { id -> scene.items.firstOrNull { it.id == id } } ?: scene.hit(point)
}
