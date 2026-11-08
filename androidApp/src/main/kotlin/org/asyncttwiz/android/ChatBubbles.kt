package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/**
 * LOB-136 (owner, 2026-11-07): the other player's oldest unread chat message as a speech bubble by their life square:
 * [index] of [count] unread messages of this stretch (a stretch starts when a message arrives with none unread and ends
 * when the last one is read). [counter] ("1/3") shows only when there are several.
 */
data class IncomingChatBubble(val messageId: String, val text: String, val index: Int, val count: Int) {
    val counter: String? get() = if (count > 1) "$index/$count" else null
}

/** LOB-137: a message the viewer just sent from this device ([key] tells one sending from the next), shown briefly by
 * their own life square. */
data class SentChat(val key: Long, val text: String)

/** LOB-139 (owner, 2026-11-07): the quick messages of your own Life panel, each sent with one tap. */
internal val QuickChatMessages = listOf("Thinking…", "One sec", "Your move?", "Got a response", "No responses", "GG")

internal object ChatBubbleRules {
    /** LOB-137: how long the viewer's own sent message stays before it fades, and the fade (ms). */
    const val OWN_SHOWN_MILLIS = 3_000L
    const val OWN_FADE_MILLIS = 400

    /**
     * LOB-136: the bubble of the other player's oldest unread message in [messages] (ascending, loaded ones) for [viewer]
     * with their read cursor at [readThrough]; [stretchStart] is the cursor when this stretch of unread messages began.
     * Null when nothing of the other player's is unread.
     */
    fun incoming(messages: List<ChatMessage>, viewer: String?, readThrough: Long, stretchStart: Long): IncomingChatBubble? {
        var oldest: ChatMessage? = null
        var unread = 0
        var stretch = 0
        // From the newest back to the stretch's start: the unread rows are a suffix, never the whole transcript.
        for (i in messages.indices.reversed()) {
            val message = messages[i]
            val revision = GameWire.ordinal(message.revision)
            if (revision <= minOf(readThrough, stretchStart)) break
            if (message.author == viewer) continue
            if (revision > stretchStart) stretch++
            if (revision > readThrough) { unread++; oldest = message }
        }
        oldest ?: return null
        val count = maxOf(stretch, unread)
        return IncomingChatBubble(oldest.id, oldest.text, count - unread + 1, count)
    }
}

/**
 * LOB-136, LOB-137: where a chat bubble goes by its player's life square, in the old status bubbles' look (white, the
 * player's colour as outline and tail, text wrapped over up to [MAX_LINES] lines and cut with "…", each line centred,
 * never narrower than the life square). It goes under the opponent's square and above the viewer's own when that room is
 * free, else to the nearest free room by the square; it never covers a card, a pile, a life square, an emblem, the
 * Stack, an open panel or the bars ([obstacles]). Where no room is free even for one line, there is no bubble.
 */
internal object ChatBubbleLayout {
    const val TEXT = 11f
    const val COUNTER_TEXT = 10f
    const val PADDING = 6f
    const val HEIGHT = 18f
    const val LINE = 13f
    const val TAIL = 5f
    const val MAX_WIDTH = 168f
    const val MAX_LINES = 4
    /** Room kept between the bubble (with its tail) and anything it must not cover. */
    const val MARGIN = 2f
    /** How far from the life square the bubble may go, and the search's step (dp). */
    const val RADIUS = 168f
    const val STEP = 4f
    /** The counter's gap after the text. */
    const val COUNTER_GAP = 6f

    /** The bubble's lines for [text] at most [maxLines] lines in [maxWidth] (text only). */
    fun lines(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> =
        ChipText.lines(text.replace('\n', ' '), maxWidth, maxLines, measure)

    /** The bubble's size for [lines] [textWidth] wide (the longest line) and a [counterWidth] wide counter (0: none),
     * never narrower than the [life] square. */
    fun size(life: TableBounds, textWidth: Float, lines: Int, counterWidth: Float): Pair<Float, Float> {
        val counter = if (counterWidth > 0f) counterWidth + COUNTER_GAP else 0f
        return maxOf(textWidth + counter + 2 * PADDING, life.width) to HEIGHT + (lines.coerceIn(1, MAX_LINES) - 1) * LINE
    }

    private fun overlaps(a: TableBounds, b: TableBounds) = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom
    private fun grow(b: TableBounds, by: Float) = TableBounds(b.x - by, b.y - by, b.width + 2 * by, b.height + 2 * by)

    /**
     * The bubble [width]×[height] by [life] ([below] it for the opponent, above it for the viewer) on a [screenWidth]×
     * [screenHeight] table, clear of [obstacles]: the old place (left-aligned with the square) when free, else the free
     * place nearest the square within [RADIUS], the preferred side first; null when there is none.
     */
    fun place(life: TableBounds, below: Boolean, width: Float, height: Float, screenWidth: Float, screenHeight: Float,
        obstacles: Obstacles): TableBounds? {
        val keep = TAIL + 1f
        val near = grow(life, keep)
        val blocked = obstacles.items.map { grow(it, MARGIN) }
        fun free(b: TableBounds): Boolean {
            if (b.x < 4f || b.y < 0f || b.right > screenWidth - 4f || b.bottom > screenHeight) return false
            if (overlaps(b, near) || obstacles.bars.any { overlaps(b, it) }) return false
            // The tail may cross into a bar (to a life square there), never onto a card
            val tailed = grow(b, keep)
            return blocked.none { overlaps(tailed, it) }
        }
        val preferred = TableBounds(life.x, if (below) life.bottom + keep else life.y - keep - height, width, height)
        if (free(preferred)) return preferred
        data class Candidate(val bounds: TableBounds, val score: Float)
        val candidates = ArrayList<Candidate>()
        var y = life.y - RADIUS - height
        while (y <= life.bottom + RADIUS) {
            var x = life.x - RADIUS - width
            while (x <= life.right + RADIUS) {
                val b = TableBounds(x, y, width, height)
                val dx = maxOf(0f, life.x - b.right, b.x - life.right)
                val dy = maxOf(0f, life.y - b.bottom, b.y - life.bottom)
                val distance = kotlin.math.hypot(dx, dy)
                if (distance <= RADIUS) {
                    // The preferred side (under the opponent's square, above your own) before the others
                    val wrongSide = if (below) b.bottom <= life.y + .5f else b.y >= life.bottom - .5f
                    candidates += Candidate(b, distance + (if (wrongSide) 48f else 0f) + kotlin.math.abs(b.x - life.x) / 100f)
                }
                x += STEP
            }
            y += STEP
        }
        candidates.sortBy { it.score }
        return candidates.firstOrNull { free(it.bounds) }?.bounds
    }

    /** Which edge of [bubble] the tail leaves from towards [life], and the tail's base centre on it. */
    fun tail(bubble: TableBounds, life: TableBounds): Tail = when {
        bubble.y >= life.bottom -> Tail(TailSide.TOP, life.center.x.coerceIn(bubble.x + 8f, bubble.right - 8f), bubble.y)
        bubble.bottom <= life.y -> Tail(TailSide.BOTTOM, life.center.x.coerceIn(bubble.x + 8f, bubble.right - 8f), bubble.bottom)
        bubble.x >= life.right -> Tail(TailSide.LEFT, bubble.x, life.center.y.coerceIn(bubble.y + 6f, bubble.bottom - 6f))
        else -> Tail(TailSide.RIGHT, bubble.right, life.center.y.coerceIn(bubble.y + 6f, bubble.bottom - 6f))
    }
    enum class TailSide { TOP, BOTTOM, LEFT, RIGHT }
    data class Tail(val side: TailSide, val x: Float, val y: Float)

    /** What a bubble must keep off: [items] (with room for its tail) and the [bars] (the bubble itself). */
    data class Obstacles(val items: List<TableBounds>, val bars: List<TableBounds> = emptyList())

    /** What a chat bubble must never cover on [scene] (all on the screen): cards, piles, dungeons, life squares but
     * [except]'s, emblems, the Stack, open card panels, the combat band's lanes, and the top bar and the [bottom] bar. */
    fun obstacles(scene: TableScene, except: String?, width: Float, height: Float, bottom: Float): Obstacles = Obstacles(buildList {
        for (item in scene.items) {
            if (item.kind == TableItemKind.HAND || item.id == except) continue
            addAll(item.visible)
            item.countBadge?.let(::add)
            if (item.kind == TableItemKind.PILE && item.visible.isNotEmpty()) add(item.pileCountBubble)
            addAll(item.emblemChips)
            item.stateChips.forEach { add(it.bounds) }
            item.emblemArea?.takeIf { item.emblems.isNotEmpty() }?.let(::add)
        }
        scene.stackRegion?.let(::add)
        scene.dayNightChip?.let { add(it.bounds) }
        scene.cardPanels.forEach { add(it.bounds) }
        scene.combatPanel?.let(::add)
    }, listOf(TableBounds(0f, 0f, width, TablePresentation.HEADER), TableBounds(0f, height - bottom, width, bottom)))
}
