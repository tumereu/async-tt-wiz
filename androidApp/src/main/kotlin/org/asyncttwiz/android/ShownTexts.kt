package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Who hears each short-lived text the screen shows (a table note, a refusal's message, the event text and its warning
 * flash) and each state of the bottom bar (its main button's words and quick actions, [bottomBarRecord]), as it appears:
 * nobody in the app. The end-to-end tests' phones record them, so a text shown for a few seconds, or a bottom bar shown
 * for a frame, is never missed by a test whose clock skips ahead (they read the record instead of the screen).
 */
internal val LocalShownTexts = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Tells [LocalShownTexts] that [text] shows now: once per [key] (by default the text itself) while it stays. */
@Composable
internal fun RecordShown(text: String?, key: Any? = text) {
    val record = LocalShownTexts.current
    DisposableEffect(key) {
        text?.let(record)
        onDispose {}
    }
}
