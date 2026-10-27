package org.asyncttwiz.android

import android.content.Context

/** Personal layout data; action identifiers are independent of their short visual labels. */
internal data class CardRingPage(val name: String, val actions: List<String>)
internal data class CardRingSlot(val id: String, val cell: Int, val span: Int)
internal object CardRingPages {
    const val columns = 5
    const val capacity = 25
    val defaults = listOf(
        CardRingPage("Everyday", listOf("inspect", "note", "corner", "Stack", "Declare without tapping", "flip", "switchFace",
            "Graveyard", "Exile", "Clone", "Ping / Unping", "Hand", "Library top", "Library bottom", "Shuffle into library",
            "damage", "eotCorner")),
        CardRingPage("Counters", org.asyncttwiz.protocol.CounterColor.entries.chunked(2).flatMapIndexed { row, colors ->
            colors.map { "counter/${it.name}" } + listOf("note", "eotNote", "resetCounters")[row]
        }),
        CardRingPage("More", listOf("owner", "otherSide", "Link", "Unlink", "eotNote",
            "corner/LEFT/1", "corner/LEFT/-1", "corner/RIGHT/1", "corner/RIGHT/-1", "resetCorner",
            "eotCorner/LEFT/1", "eotCorner/LEFT/-1", "eotCorner/RIGHT/1", "eotCorner/RIGHT/-1", "empty/More",
            "keywords", "eotKeywords")))

    val hand = listOf(
        CardRingPage("Hand", listOf("inspect", "Play", "Reveal", "Discard", "Exile", "Library top", "Library bottom", "Shuffle into library")),
        CardRingPage("Random", listOf("Discard random", "Reveal random", "Exile random", "Random to library bottom")))

    private fun defaultLayout(available: List<String>, token: Boolean) = defaults.mapIndexed { index, page ->
        when (index) {
            0 -> page.copy(actions = page.actions.map { id ->
                when {
                    id == "Declare without tapping" && "Attack →" in available -> "Attack →"
                    id == "Clone" && "Remove from combat" in available -> "Remove from combat"
                    token && id == "Hand" -> if ("Remove token" in available) "Remove token" else "empty/Hand"
                    token && id in listOf("Library top", "Library bottom", "Shuffle into library") -> "empty/$id"
                    else -> id
                }
            })
            2 -> page.copy(actions = page.actions.map { if (it in available || it.startsWith("empty/")) it else "empty/$it" })
            else -> page
        }
    }

    fun span(id: String) = if (id in listOf("corner", "corner/counters", "damage", "eotCorner") ||
        org.asyncttwiz.protocol.CounterColor.entries.any { id == "counter/${it.name}" }) 2 else 1

    /** Row-end padding belongs to the layout, never to an action or half of a widget. */
    fun layout(actions: List<String>): List<CardRingSlot> {
        var cell = 0
        return actions.map { id ->
            val span = span(id)
            if (cell % columns + span > columns) cell += columns - cell % columns
            CardRingSlot(id, cell, span).also { cell += span }
        }
    }

    fun resolve(available: List<String>, pages: List<CardRingPage>? = null, token: Boolean = false, inHand: Boolean = false): List<CardRingPage> {
        if (inHand) return hand.map { page -> page.copy(actions = page.actions.filter { it in available }) }
        val requested = pages ?: defaultLayout(available, token)
        val remaining = available.toMutableSet()
        val assigned = requested.map { page -> page.actions.distinct().filter {
            remaining.remove(it)
            it in available || pages == null && it.startsWith("empty/")
        } }.toMutableList()
        // Saved layouts retain their original capacity; the new default More is exactly four rows.
        if (pages != null) assigned[2] = assigned[2] + remaining
        val result = requested.map { it.copy(actions = emptyList()) }.toMutableList()
        val overflow = mutableListOf<String>()
        for ((index, actions) in assigned.withIndex()) for (action in actions) {
            if (fits(result[index].actions + action)) result[index] = result[index].copy(actions = result[index].actions + action)
            else overflow += action
        }
        if (pages == null) overflow += remaining
        for (action in overflow) {
            // Overflow never fills unrelated Everyday/Counter cells or creates Move pages.
            var index = result.lastIndex
            if (index < 2 || pages == null && index == 2 || !fits(result[index].actions + action)) {
                result += CardRingPage("More ${result.size - 1}", emptyList())
                index = result.lastIndex
            }
            result[index] = result[index].copy(actions = result[index].actions + action)
        }
        return result
    }
    private fun fits(actions: List<String>) = layout(actions).last().let { it.cell + it.span <= capacity }
    fun adjacent(selected: Int, delta: Int, count: Int) = (selected + delta).mod(count)
}

internal class CardRingPageStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE)
    private val key = "${account.length}:$account:$game"
    fun read() = preferences.getInt(key, 0).coerceAtLeast(0)
    fun write(page: Int) { preferences.edit().putInt(key, page.coerceAtLeast(0)).apply() }
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
    const val gridSide = CardRingPages.columns * cell
    const val squareSide = gridSide + 24f
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
