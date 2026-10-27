package org.asyncttwiz.android

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PodPickTrayCardWidth = 48.dp / 1.4f
private val TrayPadding = 8.dp
internal val PodPickTrayGap = 4.dp

/** Reserve the round's slots, not the number already marked, so marking never changes layout. */
internal fun podPickTrayWidth(required: Int, labelWidth: Dp): Dp =
    TrayPadding + labelWidth + PodPickTrayCardWidth * required.coerceAtLeast(0) +
        PodPickTrayGap * (required - 1).coerceAtLeast(0)

internal fun podPickTraysFit(available: Dp, keep: Int, burn: Int, keepLabel: Dp, burnLabel: Dp): Boolean =
    burn == 0 || podPickTrayWidth(keep, keepLabel) + PodPickTrayGap + podPickTrayWidth(burn, burnLabel) <= available

internal data class PodPickTrayLayout(val keepWidth: Dp, val burnWidth: Dp, val singleRow: Boolean)

internal fun podPickTrayLayout(available: Dp, keep: Int, burn: Int, keepLabel: Dp, burnLabel: Dp): PodPickTrayLayout {
    val keepWidth = podPickTrayWidth(keep, keepLabel)
    if (burn == 0) return PodPickTrayLayout(available, 0.dp, true)
    val burnWidth = podPickTrayWidth(burn, burnLabel)
    if (!podPickTraysFit(available, keep, burn, keepLabel, burnLabel)) {
        return PodPickTrayLayout(minOf(keepWidth, available), minOf(burnWidth, available), false)
    }
    val usable = available - PodPickTrayGap
    val expandedKeep = keepWidth * (usable / (keepWidth + burnWidth))
    return PodPickTrayLayout(expandedKeep, usable - expandedKeep, true)
}
