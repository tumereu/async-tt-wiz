package org.asyncttwiz.android

import org.asyncttwiz.appui.toRadians
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Your hand as a fan: on the desktop table (WEB-015, WEB-073, WEB-103) and in landscape on the phone (ZON-138 to ZON-142).
 * Its cards turned a little either way about a point below the fan and overlapping, partly below the bottom; a raised card
 * pops out along its own turn until whole in view; your cards from other zones at its right end after the divider (ZON-079).
 * One fan for both: the desktop's squeezes its cards closer when they don't fit ([desktop]); landscape's keeps each card's
 * part easy to tap and turns like a wheel when there are more ([landscape]).
 */
object HandFan {
    /** ZON-138: the narrowest part of a landscape fan a card keeps (dp), so it stays easy to tap: as many cards as fit with
     * this much each make a still fan; with more it turns ([Turning]). */
    const val MIN_SLOT = 20f
    /** ZON-138: the share of a landscape fan card's height in view at rest; the rest is below the screen's bottom. */
    const val LANDSCAPE_SHOWN = .8f
    /** ZON-139: a card turned away from a landscape fan's end slides on outwards this share of a slot and turns this many
     * more degrees, per slot it is turned away, as it sinks below the screen's bottom ([SINK_EXTRA] of its height further
     * than its part in view, so it is gone once a whole slot away). */
    private const val SINK_SHIFT = .5f
    private const val SINK_TURN = 14f
    private const val SINK_EXTRA = .3f

    /**
     * ZON-139: a landscape fan with more cards than its slots, turned like a wheel: [value] dp (a card each [pitch]) from the
     * first card at the fan's left end, between [min] (the first card at its right end) and [max] (the last card at its left
     * end); [slots] cards in view, [cards] the hand's cards in order (for the log). A still fan has [min] = [max] = 0.
     */
    data class Turning(val pitch: Float, val min: Float, val max: Float, val value: Float, val slots: Int,
        val cards: List<String> = emptyList()) {
        val turns: Boolean get() = max > min
        /** The first card of the hand at the fan's left end (negative: that many empty slots before the first card). */
        fun first(at: Float = value): Int = if (pitch <= 0f) 0 else (at / pitch).roundToInt()
        /** The turn [at] (dp) settled on a whole card, within the ends. */
        fun settled(at: Float): Float = if (pitch <= 0f) 0f else ((at / pitch).roundToInt() * pitch).coerceIn(min, max)
        /** The cards turned away at the left end and at the right end with the fan turned [at]. */
        fun hidden(at: Float = value): Pair<List<String>, List<String>> {
            val first = first(at)
            return cards.take(first.coerceIn(0, cards.size)) to cards.drop((first + slots).coerceIn(0, cards.size))
        }
    }

    /** A card of the fan: its turn and size ([face]), its centre at rest ([rest], sunk below the bottom when turned away),
     * popped out ([raised], turned [raisedFace]: a card turned away pops out from the end slot it went past), and the part of
     * the fan at rest that is its own ([slot], null while turned away). */
    data class Card(val face: TableCardTransform, val rest: TablePoint, val raised: TablePoint, val slot: TableBounds?,
        val raisedFace: TableCardTransform = face)

    /**
     * The fan laid out: each card's place ([cards]: the hand's, then the cards from other zones), the fan's own room
     * ([bounds]: from its first card to its count's room, the part in view at rest), the room nothing of a card goes beyond
     * ([port]), the divider's x, and in landscape its [turning] with the cards turned away at each end and where their "+N"s
     * go ([plusLeft], [plusRight]: centres).
     */
    data class Layout(val cards: List<Card>, val bounds: TableBounds, val port: TableBounds, val divider: Float?,
        val turning: Turning? = null, val hiddenLeft: Int = 0, val hiddenRight: Int = 0,
        val plusLeft: TablePoint? = null, val plusRight: TablePoint? = null)

    /** Owner 2026-11-16 (the fan was cut at its left): the room an end card's turn and pop-out take beside the fan. */
    private fun edge(cardHeight: Float) = (cardHeight * sin(toRadians(DesktopTable.FAN_MAX_TURN.toDouble()))).toFloat()

    /** Each card's turn from the next one's among [count] cards: [DesktopTable.FAN_TURN] at most, [DesktopTable.FAN_MAX_TURN]
     * either side. */
    private fun turnStep(count: Int) = if (count <= 1) 0f else minOf(DesktopTable.FAN_TURN, 2 * DesktopTable.FAN_MAX_TURN / (count - 1))

    /** A card [w] x [h] with its left edge at [x] and turned [rotation] degrees about a point below the fan: its centre, its
     * middle dropped as it turns, its top at [restTop] unturned. */
    private fun restCentre(x: Float, rotation: Float, restTop: Float, w: Float, h: Float): TablePoint {
        val radians = toRadians(rotation.toDouble())
        val drop = (DesktopTable.FAN_PIVOT * h * (1 - cos(radians))).toFloat()
        return TablePoint(x + w / 2, restTop + h / 2 + drop)
    }

    /** WEB-103: a raised card moved out along its turn until its lowest corner is [DesktopTable.FAN_LIFT] above [floorY]. */
    private fun raisedCentre(face: TableCardTransform, rest: TablePoint, floorY: Float): TablePoint {
        val radians = toRadians(face.rotation.toDouble())
        val lift = ((face.bounds(rest).bottom - (floorY - DesktopTable.FAN_LIFT)) / cos(radians).toFloat()).coerceAtLeast(0f)
        return TablePoint(rest.x + lift * sin(radians).toFloat(), rest.y - lift * cos(radians).toFloat())
    }

    /** The room that holds every card of the fan at rest and popped out (nothing of a card cut off), down to [floorY]. */
    private fun port(cards: List<Card>, bounds: TableBounds, restTop: Float, floorY: Float,
        atRest: (Int) -> Boolean = { true }): TableBounds {
        val reach = cards.flatMapIndexed { n, it -> listOfNotNull(it.face.bounds(it.rest).takeIf { atRest(n) }, it.raisedFace.bounds(it.raised)) }
        val portLeft = (reach.minOfOrNull { it.x } ?: bounds.x).coerceAtMost(bounds.x) - 2f
        val portRight = (reach.maxOfOrNull { it.right } ?: bounds.right).coerceAtLeast(bounds.right) + 2f
        val top = minOf(reach.minOfOrNull { it.y } ?: restTop, restTop) - 4f
        return TableBounds(portLeft, top, portRight - portLeft, floorY - top)
    }

    /**
     * WEB-073, WEB-103: the desktop table's fan of [hand] cards and [extras] from other zones, each [w] x [h], between
     * [areaLeft] and [areaRight] (its count's room inside), [shown] of it in view above [floorY]: each card
     * [DesktopTable.FAN_PITCH] of its width from the one before at most, closer when they don't fit, centred.
     */
    fun desktop(hand: Int, extras: Int, areaLeft: Float, areaRight: Float, floorY: Float, shown: Float, w: Float, h: Float): Layout {
        val count = hand + extras
        val edge = edge(h)
        val left = areaLeft + edge
        val right = (areaRight - TablePresentation.FAN_COUNT - edge).coerceAtLeast(left + w)
        val gap = if (extras > 0 && hand > 0) w * (1f - DesktopTable.FAN_PITCH) + TablePresentation.HAND_ROW_DIVIDER else 0f
        val pitch = if (count <= 1) 0f else minOf(w * DesktopTable.FAN_PITCH, (right - left - w - gap) / (count - 1)).coerceAtLeast(4f)
        val span = if (count == 0) 0f else (count - 1) * pitch + w + gap
        val start = (left + right) / 2 - span / 2
        val restTop = floorY - shown
        val step = turnStep(count)
        fun cardX(n: Int) = start + n * pitch + if (n >= hand) gap else 0f
        val cards = List(count) { n ->
            val face = TableCardTransform(w, h, (n - (count - 1) / 2f) * step)
            val rest = restCentre(cardX(n), face.rotation, restTop, w, h)
            val x = cardX(n)
            val slotRight = if (n + 1 < count) cardX(n + 1) else x + w
            Card(face, rest, raisedCentre(face, rest, floorY), TableBounds(x, restTop, slotRight - x, floorY - restTop))
        }
        val bounds = TableBounds(start, restTop, span + TablePresentation.FAN_COUNT, shown)
        return Layout(cards, bounds, port(cards, bounds, restTop, floorY),
            if (gap > 0f) (cardX(hand - 1) + w + cardX(hand)) / 2 else null)
    }

    /**
     * ZON-138 to ZON-142: landscape's fan of [hand] cards and [extras] from other zones, each [w] x [h], between [areaLeft]
     * and [areaRight] (its count's room inside), [LANDSCAPE_SHOWN] of it in view above [floorY]. As many cards as fit with at
     * least [MIN_SLOT] each make a still fan (closer than [DesktopTable.FAN_PITCH] of a card's width when they don't fit),
     * centred; with more, the hand's cards turn like a wheel ([turn] dp, [Turning]) through as many slots as fit, the cards
     * from other zones staying at its right end; cards turned away sink below [floorY] along the curve. [cardIds]: the
     * hand's cards, for the log.
     */
    fun landscape(hand: Int, extras: Int, areaLeft: Float, areaRight: Float, floorY: Float, w: Float, h: Float,
        turn: Float = 0f, cardIds: List<String> = emptyList()): Layout {
        val count = hand + extras
        val shown = h * LANDSCAPE_SHOWN
        val edge = edge(h)
        val left = areaLeft + edge
        val right = (areaRight - TablePresentation.FAN_COUNT - edge).coerceAtLeast(left + w)
        val room = right - left
        val divider = if (extras > 0 && hand > 0) TablePresentation.HAND_ROW_DIVIDER else 0f
        val widest = maxOf(w * DesktopTable.FAN_PITCH, MIN_SLOT)
        // The pitch all the cards fit with, the hand's last card whole before the divider
        val fitting = when {
            count <= 1 -> widest
            divider > 0f -> if (count > 2) (room - 2 * w - divider) / (count - 2) else widest
            else -> (room - w) / (count - 1)
        }
        val still = count <= 1 || fitting >= MIN_SLOT
        val pitch = if (still) minOf(widest, fitting) else MIN_SLOT
        // The cards from other zones' room after the hand's slots, and as many slots as fit before it
        val extrasRoom = if (extras == 0) 0f else divider + (extras - 1) * pitch + w
        val slots = if (still || hand == 0) hand
            else (floor((room - extrasRoom - w) / pitch).toInt() + 1).coerceIn(1, hand)
        val turning = Turning(pitch, if (slots < hand) -(slots - 1) * pitch else 0f, if (slots < hand) (hand - 1) * pitch else 0f,
            0f, slots, cardIds)
        val value = turn.coerceIn(turning.min, turning.max)
        val wheel = if (slots == 0) 0f else (slots - 1) * pitch + w
        val span = wheel + extrasRoom
        val start = (left + right) / 2 - span / 2
        val restTop = floorY - shown
        val total = slots + extras
        val step = turnStep(total)
        fun rotation(q: Float) = (q - (total - 1) / 2f) * step
        fun wheelX(q: Float) = start + q * pitch
        val sink = shown + h * SINK_EXTRA
        // Card i of the hand lies at slot q = i - turned (in slots); beyond the end slots it is turned away, sinking
        fun wheelCard(q: Float): Pair<TableCardTransform, TablePoint> {
            val end = q.coerceIn(0f, (slots - 1).toFloat())
            val away = q - end
            val face = TableCardTransform(w, h, rotation(end) + away * SINK_TURN)
            val rest = restCentre(wheelX(end) + away * pitch * SINK_SHIFT, face.rotation, restTop, w, h)
            return face to rest.copy(y = rest.y + abs(away) * sink)
        }
        val turned = if (pitch <= 0f) 0f else value / pitch
        val cards = List(count) { n ->
            if (n < hand) {
                val q = n - turned
                val (face, rest) = wheelCard(q)
                // ZON-141: a raised card turned away stays raised, popped out from its end slot
                val (endFace, endRest) = wheelCard(q.coerceIn(0f, (slots - 1).toFloat()))
                val slot = q.roundToInt().takeIf { it in 0 until slots }?.let { k ->
                    val x = wheelX(k.toFloat())
                    TableBounds(x, restTop, if (k + 1 < slots) pitch else w, floorY - restTop)
                }
                Card(face, rest, raisedCentre(endFace, endRest, floorY), slot, endFace)
            } else {
                val e = n - hand
                val x = start + wheel + divider + e * pitch
                val face = TableCardTransform(w, h, rotation((slots + e).toFloat()))
                val rest = restCentre(x, face.rotation, restTop, w, h)
                Card(face, rest, raisedCentre(face, rest, floorY), TableBounds(x, restTop, if (e + 1 < extras) pitch else w, floorY - restTop))
            }
        }
        val bounds = TableBounds(start, restTop, span + TablePresentation.FAN_COUNT, shown)
        // The port holds the cards in view at rest, and every card popped out (those turned away rest under the bottom)
        val hiddenLeft = (0 until hand).count { n -> n - turned < -.5f }
        val hiddenRight = (0 until hand).count { n -> n - turned > slots - .5f }
        val plusY = floorY - PLUS_HEIGHT / 2 - 3f
        return Layout(cards, bounds, port(cards, bounds, restTop, floorY) { n -> n >= hand || (n - turned) in -.5f..(slots - .5f) },
            if (divider > 0f) start + wheel + divider / 2 else null, turning.copy(value = value), hiddenLeft, hiddenRight,
            if (hiddenLeft > 0) TablePoint(start + MIN_SLOT / 2, plusY) else null,
            if (hiddenRight > 0) TablePoint(start + wheel - MIN_SLOT / 2, plusY) else null)
    }

    /** ZON-140: the "+N" chip's height (dp). */
    const val PLUS_HEIGHT = 15f
}
