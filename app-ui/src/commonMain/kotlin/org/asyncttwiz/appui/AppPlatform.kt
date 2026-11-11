package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.Font

/**
 * The platform services the shared UI reads through one object (docs/specs/web-client.md §1 item 2): device settings,
 * files bundled with the app, its pictures, and what dialogs need from the platform's window. The app's shell installs
 * one at start ([install]; Android: the Application, before anything else runs), and the UI reads [current].
 */
interface AppPlatform {
    /** The small settings kept on this device under [name] (Android: the SharedPreferences file of that name). */
    fun preferences(name: String): Preferences

    /** The whole of the file at [path] among the app's bundled files (Android: its assets); throws when it is missing. */
    fun readAsset(path: String): ByteArray

    /** The font file at [path] among the app's bundled files (Android: from its assets). */
    fun assetFont(path: String): Font

    /** Whether this app was installed fresh rather than updated (its first install is its last update); false when the
     * platform can't tell. */
    fun freshInstall(): Boolean

    /** The crossed-swords icon (combat). */
    @Composable fun swordsOutlined(): Painter

    /** The app's logo, the wizard hat. */
    @Composable fun atwLogo(): Painter

    /**
     * Observes, never consumes, the input a dialog's own window receives, so that it counts as the player doing something
     * in the game (Android: a dialog's window bypasses the activity's input dispatch). Called inside the dialog.
     */
    @Composable fun ObserveDialogInput()

    companion object {
        private var installed: AppPlatform? = null

        /** The installed platform; the shell installs it before any UI or app code runs. */
        val current: AppPlatform get() = checkNotNull(installed) { "No AppPlatform installed" }

        fun install(platform: AppPlatform) { installed = platform }
    }
}

/** A file of small device settings (Android: SharedPreferences, whose behaviour this keeps). */
interface Preferences {
    fun getString(key: String, default: String?): String?
    fun getStringSet(key: String, default: Set<String>?): Set<String>?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getFloat(key: String, default: Float): Float
    fun contains(key: String): Boolean
    fun edit(): Editor

    /** Calls [listener] with each changed key (null: all were cleared) until the returned function is called. */
    fun observe(listener: (String?) -> Unit): () -> Unit

    /** A batch of changes, written by [apply] (in the background) or [commit] (at once, saying whether it was). */
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, value: Set<String>?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}
