package org.asyncttwiz.android

import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.TableActions
import java.math.BigInteger

/** Current-face metadata also covers tokens and retained projection snapshots. No type/rules inference. */
internal fun changedPowerToughness(displayed: String, face: DefinitionFace?): Boolean =
    face != null && face.power.isNotEmpty() && face.toughness.isNotEmpty() &&
        displayed != "${face.power}/${face.toughness}"

internal const val ChangedPowerToughnessColor = 0xffa0522d.toInt()
internal const val EotPowerToughnessColor = 0xff007f80.toInt()

internal fun effectiveCorner(card: PlayCard): String {
    if (card.eotCorner.isEmpty()) return card.corner
    val base = card.corner.split('/')
    val delta = card.eotCorner.split('/')
    if (base.size != 2 || delta.size != 2) return card.corner
    return base.indices.joinToString("/") { TableActions.addNumber(base[it], delta[it]) ?: base[it] }
}

internal fun damageIsLethal(damage: String, corner: String): Boolean {
    val toughness = corner.split('/').takeIf { it.size == 2 }?.get(1) ?: return false
    if (!TableActions.numeric(damage) || !TableActions.numeric(toughness)) return false
    return BigInteger(damage) >= BigInteger(toughness)
}

internal fun inspectedCorner(card: PlayCard): String = effectiveCorner(card) +
    if (card.eotCorner.isEmpty()) "" else " (${card.eotCorner} until end of turn)"
