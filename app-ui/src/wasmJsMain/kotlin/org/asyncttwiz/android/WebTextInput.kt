package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput

/**
 * WEB-090 (prod reports 9FNZGG, SLCCVC, 2026-11-19: typing in the table's chat pressed "No blocks", changed the response mode and
 * opened the timeline): whether a text field of the app has the keyboard as Compose sees it, its text input running. The
 * page's own focus can be elsewhere meanwhile (a click on the table moves it off Compose's hidden input while the field keeps
 * its caret, and Compose still types the keys into it): the hotkeys then held back too, as the caret says.
 */
object WebTextInput {
    private var running = 0

    /** Whether a text field's input is running now. */
    val active: Boolean get() = running > 0

    /** Counts the text inputs [content]'s fields start, while they run. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun Track(content: @Composable () -> Unit) {
        InterceptPlatformTextInput({ request, next ->
            running++
            AppLog.info("keys", "Text input: a text field took the keyboard ($running running)")
            try {
                next.startInputMethod(request)
            } finally {
                running--
                AppLog.info("keys", "Text input: a text field let go of the keyboard ($running running)")
            }
        }, content)
    }
}
