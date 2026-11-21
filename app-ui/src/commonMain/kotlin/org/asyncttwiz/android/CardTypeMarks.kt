package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * TBL-233 (owner, 2026-11-21): a battlefield card's card types now as small icons at its bottom left, one per type, each a plain
 * silhouette: artifact a goblet, battle a shield, creature a paw, enchantment a starburst, land a mountain, planeswalker a spark.
 */
object CardTypeIcons {
    /** The card types that have an icon, by the engine's names; a permanent's other types (kindred) have none. */
    val drawn: Set<String> = setOf("Artifact", "Battle", "Creature", "Enchantment", "Land", "Planeswalker")

    /** The icons' ink. */
    val Ink = Color(0xff3c3c3c)

    /** Of [types], those whose icons fit [room] at [step] each: when not all fit, the creature icon goes first (its P/T says it is
     * one), then the last ones. */
    fun fit(types: List<String>, room: Float, step: Float): List<String> {
        var shown = types.filter { it in drawn }
        while (shown.isNotEmpty() && shown.size * step > room + .01f)
            shown = if (shown.size > 1 && "Creature" in shown) shown - "Creature" else shown.dropLast(1)
        return shown
    }

    /** The icon of card type [type] filling a [size]-wide square from its top left (px); null for a type without one. */
    fun path(type: String, size: Float): Path? {
        val h = size / 2
        fun x(u: Float) = h + u * h
        fun y(v: Float) = h + v * h
        return Path().apply {
            when (type) {
                "Land" -> {
                    moveTo(x(-1f), y(.8f)); lineTo(x(-.35f), y(-.45f)); lineTo(x(-.02f), y(.12f))
                    lineTo(x(.38f), y(-.8f)); lineTo(x(1f), y(.8f)); close()
                }
                "Creature" -> {
                    addOval(androidx.compose.ui.geometry.Rect(x(-.52f), y(.02f), x(.52f), y(.9f)))
                    for ((u, v) in listOf(-.78f to -.12f, -.3f to -.62f, .3f to -.62f, .78f to -.12f))
                        addOval(androidx.compose.ui.geometry.Rect(x(u - .23f), y(v - .27f), x(u + .23f), y(v + .27f)))
                }
                "Artifact" -> {
                    moveTo(x(-.75f), y(-.95f)); lineTo(x(.75f), y(-.95f)); lineTo(x(.5f), y(-.15f))
                    quadraticTo(x(0f), y(.12f), x(-.5f), y(-.15f)); close()
                    addRect(androidx.compose.ui.geometry.Rect(x(-.14f), y(-.1f), x(.14f), y(.6f)))
                    addRect(androidx.compose.ui.geometry.Rect(x(-.6f), y(.58f), x(.6f), y(.92f)))
                }
                "Enchantment" -> {
                    val points = 8
                    for (k in 0 until points * 2) {
                        val angle = kotlin.math.PI * k / points - kotlin.math.PI / 2
                        val r = if (k % 2 == 0) 1f else .42f
                        val px = x((r * kotlin.math.cos(angle)).toFloat()); val py = y((r * kotlin.math.sin(angle)).toFloat())
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                "Planeswalker" -> {
                    moveTo(x(0f), y(-1f)); lineTo(x(.24f), y(.05f)); lineTo(x(0f), y(1f)); lineTo(x(-.24f), y(.05f)); close()
                    for (side in listOf(-1f, 1f)) {
                        moveTo(x(.68f * side), y(-.55f)); lineTo(x(.8f * side), y(.15f)); lineTo(x(.62f * side), y(.65f))
                        lineTo(x(.48f * side), y(.15f)); close()
                    }
                }
                "Battle" -> {
                    moveTo(x(-.82f), y(-.88f)); lineTo(x(.82f), y(-.88f)); lineTo(x(.82f), y(-.05f))
                    quadraticTo(x(.8f), y(.62f), x(0f), y(1f)); quadraticTo(x(-.8f), y(.62f), x(-.82f), y(-.05f)); close()
                }
                else -> return null
            }
        }
    }
}

/**
 * TBL-235 (owner, 2026-11-21): a permanent's subtype tags ([TableItem.subtypeTags]) as drawn: up to two, one under another, as
 * many as the art has [rows] for; with more, the last row says how many more ("+2"), or with room for one only, the first with
 * the count ("Goblin +2"). What the card reads aloud names them all.
 */
object SubtypeTags {
    const val MAX = 2
    /** The tags' fill and text. */
    val Fill = Color(0xe6263238)
    val Text = Color.White

    fun shown(tags: List<String>, rows: Int = MAX): List<String> {
        val room = minOf(rows, MAX)
        return when {
            room <= 0 -> emptyList()
            tags.size <= room -> tags
            room == 1 -> listOf("${tags.first()} +${tags.size - 1}")
            else -> tags.take(room - 1) + "+${tags.size - room + 1}"
        }
    }
}
