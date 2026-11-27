package org.asyncttwiz.android

/**
 * TBL-240 (owner 2026-11-25, 2026-11-26): where the chips, badges and tags drawn on each card lie on the table (table dp, by item
 * id and the [part] of it: the combat overlay's chips and badges, a paying source's corner chip, a picked card's role chip), as
 * they were last laid out. They take no touch of their own: a touch there is the card's ([hit]), so a tap acts on the card and
 * holding inspects it, as anywhere on the card, also where a chip sticks out past the card's edge. Chips that take a touch (a
 * toggle the viewer changes, a corner chip with several ways) are over the table and get theirs first. Touched on the main thread
 * only.
 */
class CombatChipAreas {
    private val areas = LinkedHashMap<Pair<String, String>, TableBounds>()

    fun put(id: String, bounds: TableBounds, part: String = COMBAT) { val key = id to part; areas.remove(key); areas[key] = bounds }
    fun remove(id: String, part: String = COMBAT) { areas.remove(id to part) }

    /** The item whose chips lie at [point] (the one laid out last where several overlap), or null. */
    fun hit(point: TablePoint): String? = areas.entries.lastOrNull { it.value.contains(point) }?.key?.first

    /** [scene]'s item whose chips lie at [point], else what [scene] has there itself. */
    fun item(scene: TableScene, point: TablePoint): TableItem? =
        hit(point)?.let { id -> scene.items.firstOrNull { it.id == id } } ?: scene.hit(point)

    companion object {
        /** The combat overlay's chips and badges of a card (CMB-035, CMB-064, CMB-037, CMB-044). */
        const val COMBAT = "combat"
        /** CMB-029, CMB-050: the damage badge over a card's middle, wider than a narrow card. */
        const val DAMAGE = "damage"
        /** STK-024: a paying source's (or a pile's) corner chip with one way. */
        const val PAYS = "pays"
        /** STK-047: a picked card's role chip above its top left corner. */
        const val ROLE = "role"
    }
}
