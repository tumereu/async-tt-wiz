package org.asyncttwiz.android

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider

/** Keep the label off the pressed target, falling below only when there is no room above. */
internal class HoldTooltipPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom + gap
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
internal fun HoldTooltip(name: String) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }) {
        Surface(color = MenuInk, contentColor = Color.White, shadowElevation = 4.dp,
            shape = MaterialTheme.shapes.extraSmall) {
            Text(name, Modifier.widthIn(max = 240.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}
