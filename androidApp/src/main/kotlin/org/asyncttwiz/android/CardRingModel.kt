package org.asyncttwiz.android

import android.content.Context

/** Personal layout data; action identifiers are independent of their short visual labels. */
internal data class CardRingPage(val name: String, val actions: List<String>)
internal data class CardRingSlot(val id: String, val cell: Int, val span: Int)
internal object CardRingPages {
    const val capacity = 16
    val defaults = listOf(
        CardRingPage("Everyday", listOf("inspect", "note", "corner", "Declare + tap", "Declare without tapping", "flip", "switchFace",
            "Graveyard", "Exile", "Clone", "Ping / Unping", "Hand", "Library top", "Library bottom", "Shuffle into library")),
        CardRingPage("Counters", org.asyncttwiz.protocol.CounterColor.entries.map { "counter/${it.name}" }),
        CardRingPage("More", listOf("owner", "otherSide", "Link", "Unlink", "resetCorner",
            "corner/LEFT/1", "corner/RIGHT/1", "corner/LEFT/-1", "corner/RIGHT/-1")),
        CardRingPage("Move", emptyList()))

    private fun defaultLayout(available: List<String>, token: Boolean) = defaults.mapIndexed { index, page ->
        when (index) {
            0 -> page.copy(actions = page.actions.map { id ->
                when {
                    token && id == "Hand" -> if ("Remove token" in available) "Remove token" else "empty/Hand"
                    token && id in listOf("Library top", "Library bottom", "Shuffle into library") -> "empty/$id"
                    else -> id
                }
            })
            3 -> page.copy(actions = available.filter { isMove(it) && it !in defaults[0].actions }.sortedBy { it != "Move to Stack" })
            else -> page
        }
    }

    private fun isMove(id: String) = id.startsWith("Move ") || id in
        listOf("Hand", "Battlefield", "Library top", "Library bottom", "Graveyard", "Exile")

    fun span(id: String) = if (id == "corner" || id == "corner/counters" ||
        org.asyncttwiz.protocol.CounterColor.entries.any { id == "counter/${it.name}" }) 2 else 1

    /** Row-end padding belongs to the layout, never to an action or half of a widget. */
    fun layout(actions: List<String>): List<CardRingSlot> {
        var cell = 0
        return actions.map { id ->
            val span = span(id)
            if (cell % 4 + span > 4) cell += 4 - cell % 4
            CardRingSlot(id, cell, span).also { cell += span }
        }
    }

    fun resolve(available: List<String>, pages: List<CardRingPage>? = null, token: Boolean = false): List<CardRingPage> {
        val requested = pages ?: defaultLayout(available, token)
        val remaining = available.toMutableSet()
        val assigned = requested.map { page -> page.actions.filter { remaining.remove(it) || pages == null && it.startsWith("empty/") } }.toMutableList()
        assigned[2] = assigned[2] + remaining.filterNot(::isMove)
        assigned[3] = assigned[3] + remaining.filter(::isMove)
        val result = requested.map { it.copy(actions = emptyList()) }.toMutableList()
        for ((index, actions) in assigned.withIndex()) for (action in actions) {
            // Spill forward, wrapping to unused cells on Everyday after Move.
            val destination = result.indices.map { (index + it) % result.size }
                .firstOrNull { layout(result[it].actions + action).last().let { it.cell + it.span <= capacity } }
            if (destination != null) result[destination] = result[destination].copy(actions = result[destination].actions + action)
            else {
                // Unbounded player-created piles must not make their move actions unreachable.
                result += CardRingPage("Move ${result.size - 2}", listOf(action))
            }
        }
        return result
    }
}

internal class CardRingPageStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE)
    private val key = "${account.length}:$account:$game"
    fun read() = preferences.getInt(key, 0).coerceIn(0, 3)
    fun write(page: Int) { preferences.edit().putInt(key, page.coerceIn(0, 3)).apply() }
}

/** A press that began during the guard stays inert, even if released after the deadline. */
internal class CardRingInputGuard(val openedAt: Long, val duration: Long = 280L) {
    fun accepts(downAt: Long) = downAt - openedAt >= duration
}

internal enum class CardRingSide { ABOVE, BELOW, LEFT, RIGHT }
internal data class CardRingPlacement(val square: TableBounds, val side: CardRingSide)

/** Pure dp geometry, independent of page contents and font scale. */
internal object CardRingGeometry {
    const val cell = 48f
    const val gridSide = 4 * cell
    const val squareSide = gridSide + 24f // 192dp grid, 16dp tabs, 4dp top/bottom padding.
    private const val margin = 12f
    private const val gap = 8f
    fun place(width: Float, height: Float, card: TableBounds, own: Boolean,
        popupWidth: Float = squareSide, popupHeight: Float = squareSide): CardRingPlacement? {
        val screen = TableBounds(margin, margin, (width - 2 * margin).coerceAtLeast(0f), (height - 2 * margin).coerceAtLeast(0f))
        val spaces = listOf(
            screen.copy(height = (card.y - gap - screen.y).coerceIn(0f, screen.height)),
            screen.copy(y = (card.bottom + gap).coerceIn(screen.y, screen.bottom), height = (screen.bottom - card.bottom - gap).coerceIn(0f, screen.height)),
            screen.copy(width = (card.x - gap - screen.x).coerceIn(0f, screen.width)),
            screen.copy(x = (card.right + gap).coerceIn(screen.x, screen.right), width = (screen.right - card.right - gap).coerceIn(0f, screen.width)))
        val preferred = if (own) 0 else 1
        val side = spaces.indices.filter { spaces[it].width >= popupWidth && spaces[it].height >= popupHeight }
            .maxWithOrNull(compareBy<Int> { spaces[it].width * spaces[it].height }.thenBy { it == preferred }) ?: return null
        val region = spaces[side]
        val x = (when (side) {
            2 -> region.right - popupWidth
            3 -> region.x
            else -> card.center.x - popupWidth / 2
        }).coerceIn(region.x, region.right - popupWidth)
        val y = (when (side) {
            0 -> region.bottom - popupHeight
            1 -> region.y
            else -> card.center.y - popupHeight / 2
        }).coerceIn(region.y, region.bottom - popupHeight)
        return CardRingPlacement(TableBounds(x, y, popupWidth, popupHeight), CardRingSide.entries[side])
    }
}
