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
val QuickChatMessages = listOf("Thinking…", "One sec", "Your move?", "Got a response", "No responses", "GG")

object ChatBubbleRules {
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
 * LOB-136, LOB-137 (owner, 2026-11-08): where a chat bubble goes: always at its player's life square, under the opponent's
 * and above the viewer's own, covering whatever lies there, hand cards included, in the old status bubbles' look (white,
 * the player's colour as outline and tail, text wrapped over up to [MAX_LINES] lines and cut with "…", each line centred,
 * never narrower than the life square). Only the screen's edges move it.
 */
object ChatBubbleLayout {
    const val TEXT = 11f
    const val COUNTER_TEXT = 10f
    const val PADDING = 6f
    const val HEIGHT = 18f
    const val LINE = 13f
    const val TAIL = 5f
    const val MAX_WIDTH = 168f
    const val MAX_LINES = 4
    /** The counter's gap after the text. */
    const val COUNTER_GAP = 6f

    /** The bubble's lines for [text] at most [MAX_LINES] lines in [maxWidth] (text only). */
    fun lines(text: String, maxWidth: Float, measure: (String) -> Float): List<String> =
        ChipText.lines(text.replace('\n', ' '), maxWidth, MAX_LINES, measure)

    /** The bubble's size for [lines] [textWidth] wide (the longest line) and a [counterWidth] wide counter (0: none),
     * never narrower than the [life] square. */
    fun size(life: TableBounds, textWidth: Float, lines: Int, counterWidth: Float): Pair<Float, Float> {
        val counter = if (counterWidth > 0f) counterWidth + COUNTER_GAP else 0f
        return maxOf(textWidth + counter + 2 * PADDING, life.width) to HEIGHT + (lines.coerceIn(1, MAX_LINES) - 1) * LINE
    }

    /** The bubble [width]×[height] by [life] ([below] it for the opponent, above it for the viewer), left-aligned with the
     * square, on a [screenWidth]×[screenHeight] table: kept on the screen, whatever it covers. */
    fun place(life: TableBounds, below: Boolean, width: Float, height: Float, screenWidth: Float, screenHeight: Float): TableBounds {
        val keep = TAIL + 1f
        val y = if (below) life.bottom + keep else life.y - keep - height
        return TableBounds(life.x.coerceAtMost(screenWidth - 4f - width).coerceAtLeast(4f),
            y.coerceAtMost(screenHeight - height).coerceAtLeast(0f), width, height)
    }

    /** Which edge of [bubble] the tail leaves from towards [life] ([Tail.top]: its top edge), and the tail's base centre on it. */
    fun tail(bubble: TableBounds, life: TableBounds): Tail {
        val top = bubble.y >= life.bottom
        return Tail(top, life.center.x.coerceIn(bubble.x + 8f, bubble.right - 8f), if (top) bubble.y else bubble.bottom)
    }
    data class Tail(val top: Boolean, val x: Float, val y: Float)
}
