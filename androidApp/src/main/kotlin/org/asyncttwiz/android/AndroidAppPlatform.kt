package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.window.DialogWindowProvider
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.Preferences

/** The shared UI's platform on Android ([AppPlatform]): the application's preferences, assets and drawables, and the
 * main activity's input tracking. Installed by [TabletopApplication]. */
internal class AndroidAppPlatform(context: Context) : AppPlatform {
    private val application = context.applicationContext

    override fun preferences(name: String): Preferences =
        AndroidPreferences(application.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun readAsset(path: String): ByteArray = application.assets.open(path).use { it.readBytes() }

    override fun assetFont(path: String): Font = Font(path, application.assets)

    override fun freshInstall(): Boolean = runCatching {
        val info = application.packageManager.getPackageInfo(application.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    @Composable override fun swordsOutlined(): Painter = painterResource(R.drawable.swords_outlined)

    @Composable override fun atwLogo(): Painter = painterResource(R.drawable.atw_logo)

    /** Dialogs have their own Window and bypass Activity.dispatchTouchEvent. Observe,
     * never consume, so scrolling/holds/typing there count just like the table. */
    @Composable override fun ObserveDialogInput() {
        var context = LocalContext.current
        while (context is android.content.ContextWrapper && context !is MainActivity) context = context.baseContext
        val host = context as? MainActivity ?: return
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
        DisposableEffect(host, window) {
            val original = window.callback
            val observer = object : android.view.Window.Callback by original {
                override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                    host.gameInput(); return original.dispatchTouchEvent(event)
                }
                override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
                    host.gameInput(); return original.dispatchGenericMotionEvent(event)
                }
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    host.gameInput(); return original.dispatchKeyEvent(event)
                }
            }
            window.callback = observer
            onDispose { if (window.callback === observer) window.callback = original }
        }
    }
}

/** [Preferences] on a SharedPreferences file, call for call. */
internal class AndroidPreferences(private val preferences: SharedPreferences) : Preferences {
    override fun getString(key: String, default: String?): String? = preferences.getString(key, default)
    override fun getStringSet(key: String, default: Set<String>?): Set<String>? = preferences.getStringSet(key, default)
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    override fun getInt(key: String, default: Int) = preferences.getInt(key, default)
    override fun getLong(key: String, default: Long) = preferences.getLong(key, default)
    override fun getFloat(key: String, default: Float) = preferences.getFloat(key, default)
    override fun contains(key: String) = preferences.contains(key)
    override fun edit(): Preferences.Editor = Editor(preferences.edit())

    override fun observe(listener: (String?) -> Unit): () -> Unit {
        // SharedPreferences keeps its listeners weakly: the returned closer holds this one while observing.
        val observer = SharedPreferences.OnSharedPreferenceChangeListener { _, changed -> listener(changed) }
        preferences.registerOnSharedPreferenceChangeListener(observer)
        return { preferences.unregisterOnSharedPreferenceChangeListener(observer) }
    }

    private class Editor(private val editor: SharedPreferences.Editor) : Preferences.Editor {
        override fun putString(key: String, value: String?): Editor { editor.putString(key, value); return this }
        override fun putStringSet(key: String, value: Set<String>?): Editor { editor.putStringSet(key, value); return this }
        override fun putBoolean(key: String, value: Boolean): Editor { editor.putBoolean(key, value); return this }
        override fun putInt(key: String, value: Int): Editor { editor.putInt(key, value); return this }
        override fun putLong(key: String, value: Long): Editor { editor.putLong(key, value); return this }
        override fun putFloat(key: String, value: Float): Editor { editor.putFloat(key, value); return this }
        override fun remove(key: String): Editor { editor.remove(key); return this }
        override fun clear(): Editor { editor.clear(); return this }
        override fun apply() { editor.apply() }
        override fun commit(): Boolean = editor.commit()
    }
}
