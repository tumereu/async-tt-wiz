package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import arcanum.api.CounterKind
import org.asyncttwiz.automatum.ShownCounter

/**
 * TBL-135 (owner, 2026-11-11: counters as visuals): how a counter's bubble on a card's art looks. Playtesting decides changes,
 * so the whole palette is here, in one place.
 *
 * The common counters have designed colours with just their number, their fills stepping apart in lightness as well as hue
 * (CIE L* about 13, 35, 46, 59, 72, 81, 92), each with ink of at least 4.5:1 contrast on it. Every other counter is one neutral
 * white bubble with its number and the counter's initial ("3T" for three time counters); no colours are made from names.
 */
object CounterBubbles {
    /** A bubble's [fill], its [edge] and [ink], and the [text] it shows. */
    data class Look(val fill: Color, val edge: Color, val ink: Color, val text: String)

    private val darkInk = Color(0xff1d2326)
    private val lightInk = Color.White
    private val edgeOnColour = Color.White.copy(alpha = .85f)
    private val neutralEdge = Color(0xff6f7679)

    /** The designed colours (fill to ink), darkest first. */
    private val designed: Map<CounterKind, Pair<Color, Color>> = mapOf(
        CounterKind.PowerToughness(-1, -1) to (Color(0xff212121) to lightInk), // L* 13: near black
        CounterKind.Loyalty to (Color(0xff5e35b1) to lightInk), //                 L* 35: deep violet
        CounterKind.PowerToughness(1, 1) to (Color(0xff2e7d32) to lightInk), //   L* 46: green
        CounterKind.Lore to (Color(0xffa1887f) to darkInk), //                     L* 59: parchment brown
        CounterKind.Defense to (Color(0xfff0a04b) to darkInk), //                  L* 72: orange
        CounterKind.Shield to (Color(0xff81d4fa) to darkInk), //                   L* 81: sky blue
        CounterKind.Stun to (Color(0xffffeb3b) to darkInk), //                     L* 92: yellow
    )

    /** How [counter]'s bubble looks. */
    fun look(counter: ShownCounter): Look {
        val colours = counter.kind?.let(designed::get)
        if (colours != null) return Look(colours.first, edgeOnColour, colours.second, counter.count.toString())
        val initial = counter.name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: counter.name.take(1).ifEmpty {
            org.asyncttwiz.protocol.Faults.placeholder("CounterBubbles", "counter without a name", "?", mapOf("kind" to counter.kind.toString()))
        }
        return Look(Color.White, neutralEdge, darkInk, "${counter.count}$initial")
    }
}
