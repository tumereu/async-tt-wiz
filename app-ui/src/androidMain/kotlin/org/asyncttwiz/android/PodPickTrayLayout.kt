package org.asyncttwiz.android

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PodPickTrayCardWidth = 48.dp / 1.4f
private val TrayPadding = 8.dp
internal val PodPickTrayGap = 4.dp

/** A one-card keep tray is hard to pan once Librarian slots join it. */
internal fun podPickKeepSlots(requiredKeeps: Int, librarians: Boolean): Int =
    if (librarians) maxOf(requiredKeeps, 2) else requiredKeeps

/** Reserve the round's slots, not the number already marked, so marking never changes layout.
 * The x/y count overlays the tray corner and reserves no width. */
internal fun podPickTrayWidth(required: Int): Dp =
    TrayPadding + PodPickTrayCardWidth * required.coerceAtLeast(0) +
        PodPickTrayGap * (required - 1).coerceAtLeast(0)

internal fun podPickTraysFit(available: Dp, keep: Int, burn: Int): Boolean =
    burn == 0 || podPickTrayWidth(keep) + PodPickTrayGap + podPickTrayWidth(burn) <= available

internal data class PodPickTrayLayout(val keepWidth: Dp, val burnWidth: Dp, val singleRow: Boolean)

internal fun podPickTrayLayout(available: Dp, keep: Int, burn: Int): PodPickTrayLayout {
    val keepWidth = podPickTrayWidth(keep)
    if (burn == 0) return PodPickTrayLayout(available, 0.dp, true)
    val burnWidth = podPickTrayWidth(burn)
    if (!podPickTraysFit(available, keep, burn)) {
        return PodPickTrayLayout(minOf(keepWidth, available), minOf(burnWidth, available), false)
    }
    val usable = available - PodPickTrayGap
    val expandedKeep = keepWidth * (usable / (keepWidth + burnWidth))
    return PodPickTrayLayout(expandedKeep, usable - expandedKeep, true)
}
