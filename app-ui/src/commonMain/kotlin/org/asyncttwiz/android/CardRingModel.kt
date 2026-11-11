package org.asyncttwiz.android

import kotlin.jvm.JvmName


/** Personal layout data; action identifiers are independent of their short visual labels. */
data class CardRingPage(val name: String, val actions: List<String>)
data class CardRingSlot(val id: String, val cell: Int, val span: Int)

/** One fixed layout per context. A layout applies fully: every cell is always shown (disabled when it does not apply). */
enum class CardMenuLayout { CARD, HAND, PANEL }

object CardRingPages {
    const val columns = 5
    const val capacity = 25

    /**
     * TBL-116 (I-D46, I-D50, I-D52): the engine's actions for the card are rows above these cells; a combat copy (CMB-003,
     * owner 2026-11-06: no Combat menu) and a hand card (ZON-079) have no menu at all. No Inspect and no Other side: holding a card
     * inspects it, every face (TBL-104). No Declare and no attack target: taps stage attackers and the lanes pick their
     * targets (CMB-028, owner 2026-11-04).
     */
    fun page(layout: CardMenuLayout): CardRingPage = when (layout) {
        CardMenuLayout.CARD -> CardRingPage("Automatum", emptyList())
        CardMenuLayout.HAND -> CardRingPage("Hand", emptyList())
        CardMenuLayout.PANEL -> CardRingPage("Card", emptyList())
    }

    /** A real card's zone picks its layout. Stack entries are managed in Manage Stack; a real card is never in the Stack
     * (STK-003), so one shown from a state recorded before r35 has no menu either. */
    fun context(board: org.asyncttwiz.protocol.BoardState, viewId: String): CardMenuLayout? {
        val card = board.cards.singleOrNull { it.id == viewId } ?: return null
        val container = board.containers.singleOrNull { it.id == card.containerId }
        return when (container?.zone) {
            org.asyncttwiz.protocol.PlayZone.HAND -> CardMenuLayout.HAND
            org.asyncttwiz.protocol.PlayZone.STACK -> null
            org.asyncttwiz.protocol.PlayZone.BATTLEFIELD, null -> CardMenuLayout.CARD
            else -> CardMenuLayout.PANEL
        }
    }

    /** CMB-003, TBL-116 (owner 2026-11-06): [viewId] is a combat copy, which opens no menu. */
    fun combatCopy(board: org.asyncttwiz.protocol.BoardState, viewId: String) =
        org.asyncttwiz.protocol.CombatStackRules.occurrences(board.combat).any { it.id == viewId }

    /** True when [cardIds] include a source with an attacker copy in the current combat. */
    fun declaredAttacker(board: org.asyncttwiz.protocol.BoardState, cardIds: Collection<String>) =
        board.combat?.pairs.orEmpty().any { pair -> pair.attackers.any { it.sourceId in cardIds } }

    /** Row-end padding belongs to the layout, never to an action or half of a widget. */
    fun layout(actions: List<String>): List<CardRingSlot> {
        var cell = 0
        return actions.map { id ->
            val span = 1
            if (cell % columns + span > columns) cell += columns - cell % columns
            CardRingSlot(id, cell, span).also { cell += span }
        }
    }

    fun fits(actions: List<String>) = actions.isEmpty() || layout(actions).last().let { it.cell + it.span <= capacity }
}

/** A press that began during the guard stays inert, even if released after the deadline. */
class CardRingInputGuard(val openedAt: Long, val duration: Long = 280L) {
    fun accepts(downAt: Long) = downAt - openedAt >= duration
}

enum class CardRingSide { ABOVE, BELOW, LEFT, RIGHT }
data class CardRingPlacement(val square: TableBounds, val side: CardRingSide)

/** Pure dp geometry, independent of page contents and font scale. */
object CardRingGeometry {
    const val cell = 48f
    const val gridSide = CardRingPages.columns * cell
    const val squareSide = gridSide + 24f
    private const val margin = 12f
    private const val gap = 8f
    /** Beside the card, [focusY] (default: the middle) of the popup is lined up with the card's center. A popup keeps
     * off the [avoid] areas (the hands, TBL-067) whenever it fits elsewhere, e.g. above the hand rather than over it. */
    fun place(width: Float, height: Float, card: TableBounds, own: Boolean,
        popupWidth: Float = squareSide, popupHeight: Float = squareSide, focusY: Float = popupHeight / 2,
        avoid: List<TableBounds> = emptyList()): CardRingPlacement? {
        val screen = TableBounds(margin, margin, (width - 2 * margin).coerceAtLeast(0f), (height - 2 * margin).coerceAtLeast(0f))
        val spaces = listOf(
            screen.copy(height = (card.y - gap - screen.y).coerceIn(0f, screen.height)),
            screen.copy(y = (card.bottom + gap).coerceIn(screen.y, screen.bottom), height = (screen.bottom - card.bottom - gap).coerceIn(0f, screen.height)),
            screen.copy(width = (card.x - gap - screen.x).coerceIn(0f, screen.width)),
            screen.copy(x = (card.right + gap).coerceIn(screen.x, screen.right), width = (screen.right - card.right - gap).coerceIn(0f, screen.width)))
        // An area the card itself lies in (a menu of a hand card) isn't avoided.
        val blocks = avoid.filter { it.intersection(card) == null }
        if (blocks.isNotEmpty()) {
            val free = spaces.mapIndexed { side, space -> clear(space, blocks, side, card) }
            place(free, card, own, popupWidth, popupHeight, focusY)?.let { return it }
        }
        return place(spaces, card, own, popupWidth, popupHeight, focusY)
    }

    /** The part of [space] (on [side] of [card]) free of every block, nearest the card, that a popup could use:
     * the space is cut into horizontal bands between the blocks. */
    private fun clear(space: TableBounds, blocks: List<TableBounds>, side: Int, card: TableBounds): List<TableBounds> {
        var bands = listOf(space)
        for (block in blocks) bands = bands.flatMap { band ->
            if (band.intersection(block) == null) listOf(band)
            else listOfNotNull(band.copy(height = block.y - gap - band.y).takeIf { it.height > 0f },
                band.copy(y = block.bottom + gap, height = band.bottom - block.bottom - gap).takeIf { it.height > 0f })
        }
        return when (side) {
            0 -> bands.sortedByDescending { it.bottom }
            1 -> bands.sortedBy { it.y }
            else -> bands.sortedBy { if (it.contains(card.center.copy(x = it.x))) 0f else minOf(kotlin.math.abs(it.y - card.center.y),
                kotlin.math.abs(it.bottom - card.center.y)) }
        }
    }

    private fun place(spaces: List<TableBounds>, card: TableBounds, own: Boolean,
        popupWidth: Float, popupHeight: Float, focusY: Float): CardRingPlacement? =
        place(spaces.map { listOf(it) }, card, own, popupWidth, popupHeight, focusY)

    @JvmName("placeInBands")
    private fun place(sides: List<List<TableBounds>>, card: TableBounds, own: Boolean,
        popupWidth: Float, popupHeight: Float, focusY: Float): CardRingPlacement? {
        val spaces = sides.map { bands -> bands.firstOrNull { it.width >= popupWidth && it.height >= popupHeight } }
        val preferred = if (own) 0 else 1
        val (side, region) = spaces.withIndex().mapNotNull { (index, space) -> space?.let { index to it } }
            .maxWithOrNull(compareBy<Pair<Int, TableBounds>> { it.second.width * it.second.height }.thenBy { it.first == preferred }) ?: return null
        val x = (when (side) {
            2 -> region.right - popupWidth
            3 -> region.x
            else -> card.center.x - popupWidth / 2
        }).coerceIn(region.x, region.right - popupWidth)
        val y = (when (side) {
            0 -> region.bottom - popupHeight
            1 -> region.y
            else -> card.center.y - focusY
        }).coerceIn(region.y, region.bottom - popupHeight)
        return CardRingPlacement(TableBounds(x, y, popupWidth, popupHeight), CardRingSide.entries[side])
    }
}
