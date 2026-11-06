package org.asyncttwiz.android

/**
 * Which system bars the app shows (SYS-079): the status bar never; the navigation bar only on a phone that uses button
 * navigation (three or two buttons), and never on the game table, which keeps it hidden (a swipe shows it for a moment).
 * Gesture navigation keeps it hidden everywhere.
 */
internal object SystemBars {
    /** Android's navigation mode setting: 0 three buttons, 1 two buttons, 2 gestures; absent before Android 10. */
    private const val GESTURES = 2

    fun navigationShown(table: Boolean, buttonNavigation: Boolean): Boolean = buttonNavigation && !table

    /** Button navigation unless the phone's navigation mode is gestures; [mode] null when the phone doesn't say. */
    fun buttonNavigation(mode: Int?): Boolean = mode != GESTURES

    fun buttonNavigation(context: android.content.Context): Boolean = buttonNavigation(
        runCatching { android.provider.Settings.Secure.getInt(context.contentResolver, "navigation_mode") }.getOrNull())
}
