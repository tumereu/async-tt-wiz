package org.asyncttwiz.android

import android.content.Context
import kotlin.math.*

/** Personal layout data; action identifiers are independent of their short visual labels. */
internal data class CardRingPage(val name: String, val actions: List<String>)
internal object CardRingPages {
    val defaults = listOf(
        CardRingPage("Everyday", listOf("inspect", "Declare + tap", "Declare without tapping", "owner", "note", "corner", "Clone", "Ping / Unping")),
        CardRingPage("Counters", org.asyncttwiz.protocol.CounterColor.entries.map { "counter/${it.name}" }),
        CardRingPage("More", listOf("flip", "otherSide", "Link", "Unlink", "resetCorner",
            "corner/LEFT/1", "corner/RIGHT/1", "corner/LEFT/-1", "corner/RIGHT/-1", "Add Stack effect")))

    private fun defaultLayout(available: List<String>) = defaults.take(2) + defaults[2].copy(
        actions = defaults[2].actions.take(2) + available.filter { it.startsWith("face/") } + defaults[2].actions.drop(2))

    fun resolve(available: List<String>, pages: List<CardRingPage> = defaultLayout(available)): List<CardRingPage> {
        val assigned = pages.take(2).flatMap { it.actions }.toSet()
        val more = available.filterNot { it in assigned }
        return pages.take(2).map { it.copy(actions = it.actions.filter(available::contains)) } +
            pages[2].copy(actions = pages[2].actions.filter(more::contains).let { ordered -> ordered + more.filterNot(ordered::contains) })
    }
}

internal class CardRingPageStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE)
    private val key = "${account.length}:$account:$game"
    fun read() = preferences.getInt(key, 0).coerceIn(0, 2)
    fun write(page: Int) { preferences.edit().putInt(key, page.coerceIn(0, 2)).apply() }
}

/** A press that began during the guard stays inert, even if released after the deadline. */
internal class CardRingInputGuard(val openedAt: Long, val duration: Long = 280L) {
    fun accepts(downAt: Long) = downAt - openedAt >= duration
}

internal data class RingSize(val width: Float, val height: Float)
internal data class CardRingPlacement(val bubbles: List<TableBounds>, val grid: TableBounds? = null)
// 24dp icon + two 9sp lines still fit at 1.3×. Grow only beyond that, not into oversized targets.
internal fun ringBubbleSide(fontScale: Float) = (56f * (fontScale / 1.3f).coerceAtLeast(1f)).coerceAtMost(80f)

/** Pure dp geometry. No clamping of individual bubbles (which could silently overlap targets).
 * Try one/two ordered arcs, then a compact grid in the largest card-free rectangle. */
internal object CardRingGeometry {
    private const val gap = 8f
    fun place(width: Float, height: Float, card: TableBounds, sizes: List<RingSize>, own: Boolean): CardRingPlacement {
        val screen = TableBounds(12f, 12f, width - 24f, height - 24f)
        val exclusion = card.copy(x = card.x - 8, y = card.y - 8, width = card.width + 16, height = card.height + 16)
        val spaces = listOf(
            TableBounds(4f, 4f, width - 8f, (card.y - gap - 4).coerceAtLeast(0f)),
            TableBounds(4f, card.bottom + gap, width - 8f, (height - card.bottom - gap - 4).coerceAtLeast(0f)),
            TableBounds(4f, 4f, (card.x - gap - 4).coerceAtLeast(0f), height - 8f),
            TableBounds(card.right + gap, 4f, (width - card.right - gap - 4).coerceAtLeast(0f), height - 8f))
        val preferred = if (own) 0 else 1
        val side = spaces.indices.maxBy { spaces[it].width * spaces[it].height + if (it == preferred) 1f else 0f }
        val direction = listOf(-PI / 2, PI / 2, PI, 0.0)[side]
        fun fits(b: TableBounds) = b.x >= screen.x && b.y >= screen.y && b.right <= screen.right && b.bottom <= screen.bottom && b.intersection(exclusion) == null
        fun separated(a: TableBounds, b: TableBounds) = a.copy(x = a.x - 3, y = a.y - 3, width = a.width + 6, height = a.height + 6).intersection(b) == null
        val radius = hypot(card.width / 2, card.height / 2) + 44f
        val open = spaces[0].height >= 120 && spaces[1].height >= 120 && spaces[2].width >= 100 && spaces[3].width >= 100
        for (sweep in (if (open) listOf(2 * PI, PI) else listOf(PI))) {
            for (layers in 1..2) for (extra in 0..6) {
                val r = radius + extra * 12f
                for (split in (if (layers == 1) listOf(sizes.size) else (1 until sizes.size).toList())) {
                    val result = mutableListOf<TableBounds>()
                    for ((i, size) in sizes.withIndex()) {
                        val outer = i >= split
                        val count = if (outer) sizes.size - split else split
                        val at = if (outer) i - split else i
                        val angle = direction - sweep / 2 + if (sweep == 2 * PI) sweep * at / count else sweep * (at + .5) / count
                        val rr = r + if (outer) maxOf(76f, sizes.maxOf { it.height } + gap) else 0f
                        val b = TableBounds(card.center.x + cos(angle).toFloat() * rr - size.width / 2,
                            card.center.y + sin(angle).toFloat() * rr - size.height / 2, size.width, size.height)
                        if (!fits(b) || result.any { !separated(it, b) }) break
                        result += b
                    }
                    if (result.size == sizes.size) return CardRingPlacement(result)
                }
            }
        }
        // Width/height, not area alone: a narrow strip cannot hold a compound 3×48dp pill.
        val region = spaces.withIndex().filter { (_, area) -> area.width >= sizes.maxOf { s -> s.width } && area.height >= sizes.maxOf { s -> s.height } + 56 }
            .maxByOrNull { (index, area) ->
                val rows = ringGridRows(sizes.dropLast(1), minOf(area.width, 408f))
                val needed = rows.sumOf { row -> row.maxOf { it.height }.toDouble() + 8 } + 48
                val fits = needed <= area.height
                var remaining = area.height - 56f
                val visible = rows.take(4).takeWhile { row ->
                    val height = row.maxOf { it.height } + 8
                    remaining -= height
                    remaining >= -8f
                }.sumOf { it.size }
                (if (fits) 100_000_000f else 0f) + visible * 1_000_000f +
                    (if (index == preferred) 100_000f else 0f) + area.width * area.height
            }?.value ?: spaces.maxBy { it.width * it.height }
        return CardRingPlacement(emptyList(), region)
    }
}
