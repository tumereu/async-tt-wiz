package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.window.PopupProperties

// The platform's own pieces of drawing, windows and input the UI uses (docs/specs/web-client.md §1 item 2). Android:
// each is the code the app used before, moved here unchanged, so it draws and behaves exactly as it did.

/** Calls [onBack] on the system's Back while composed and [enabled] (Android: the activity's BackHandler). */
@Composable expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * [BackHandler] for Back that moves between screens (leaving a saved deck for the list, a game's lobby for Games), not
 * one that closes a dialog, panel or other layer. Android: [BackHandler] exactly. The web: only the on-screen Back
 * (WEB-003) calls it; the browser's Back moves between screens by their addresses and Esc closes only layers.
 */
@Composable expect fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** The screen's width in dp (Android: the configuration's screenWidthDp). */
@Composable expect fun screenWidthDp(): Int

/** The screen's height in dp (Android: the configuration's screenHeightDp). */
@Composable expect fun screenHeightDp(): Int

/** The screen's orientation: [ScreenOrientation.PORTRAIT] or [ScreenOrientation.LANDSCAPE] (Android: the configuration's). */
@Composable expect fun screenOrientation(): Int

/** Orientation numbers, the Android configuration's own (some are part of saved settings' keys). */
object ScreenOrientation {
    const val PORTRAIT = 1
    const val LANDSCAPE = 2
}

/** The display's refresh rate in frames per second, 60 when unknown. */
@Composable expect fun displayRefreshRate(): Float

/** Shows [text] briefly over the app (Android: a short Toast); remembered for the composition. */
@Composable expect fun rememberShortNote(): (String) -> Unit

/** Inside a dialog: its window fills the screen (a wrap-content window can flip between neighbouring pixel widths). */
@Composable expect fun DialogFillsScreen()

/** Inside a dialog: how much the screen behind it dims (0 to 1). */
@Composable expect fun DialogDim(amount: Float)

/** A popup window that is never focused or touched and may extend beyond the screen, for a card dragged across clipping
 * (no back press or outside click dismisses it). */
expect fun passThroughPopupProperties(): PopupProperties

/** [image] (a Coil image) as a Compose bitmap to draw, null when it isn't a bitmap. */
expect fun coilImageBitmap(image: coil3.Image): ImageBitmap?

/** The platform canvas's text paint (Android: android.graphics.Paint), for text drawn at exact pixel positions. */
interface NativeTextPaint {
    /** Text size in pixels. */
    var textSize: Float
    /** ARGB colour. */
    var color: Int
    /** The colour's alpha, 0 to 255 (setting it keeps the colour's red, green and blue). */
    var alpha: Int
    var isFakeBoldText: Boolean
    /** Whether x is the text's left edge ([TextAlign.Left]) or its centre ([TextAlign.Center]). */
    var textAlign: TextAlign
    fun measureText(text: String): Float
    /** The font's metrics at the current size. */
    fun fontMetrics(): FontMetrics

    /** Distances from the baseline (negative: above it): the most any glyph reaches ([top], [bottom]) and the font's
     * recommended [ascent] and [descent]. */
    class FontMetrics(val top: Float, val ascent: Float, val descent: Float, val bottom: Float)

    sealed interface TextAlign {
        data object Left : TextAlign
        data object Center : TextAlign
    }
}

/** A new anti-aliased [NativeTextPaint]. */
expect fun NativeTextPaint(): NativeTextPaint

/** Android's dark grey and white text colours ([NativeTextPaint.color]). */
object NativeColors {
    const val DKGRAY: Int = 0xFF444444.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
}

/** Draws [text] with its baseline starting at [x], [y] with [paint] on the platform canvas. */
expect fun DrawScope.drawNativeText(text: String, x: Float, y: Float, paint: NativeTextPaint)

/** The platform canvas's save count (to restore to after layers). */
expect fun DrawScope.nativeSaveCount(): Int

/** Restores the platform canvas to [count] ([nativeSaveCount]). */
expect fun DrawScope.nativeRestoreToCount(count: Int)

/**
 * Draws [text] [size] px tall in [color] at font [weight] with its ink centred exactly on [center] (owner gallery review
 * 2026-11-06): the letters' own outline is centred, not the font's line box, so a "G", a "2" or a "12" sits in the middle
 * of its badge however the font places it on its line. With [maxWidth] the text is drawn smaller when its ink would be
 * wider than that, so a longer number fits the same badge.
 */
expect fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float = Float.POSITIVE_INFINITY)

/** A dialog window of the screen's whole size that draws under the system bars (Android: usePlatformDefaultWidth and
 * decorFitsSystemWindows off). */
expect fun edgeToEdgeDialogProperties(): androidx.compose.ui.window.DialogProperties
