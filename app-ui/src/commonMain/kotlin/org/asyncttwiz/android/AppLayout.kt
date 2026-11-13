package org.asyncttwiz.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.desktopLayoutOffered
import org.asyncttwiz.appui.pointerHovers
import org.asyncttwiz.appui.screenHeightDp
import org.asyncttwiz.appui.screenWidthDp

/**
 * WEB-013 (docs/specs/web-client.md §4): the two layouts the app draws in. [Phone] is the Android app as it is (portrait or
 * landscape by the window's shape); [Desktop] arranges the same parts for a large window, a mouse and a keyboard (the desktop
 * table, WEB-015 to WEB-020). Android always draws the phone layout.
 */
sealed interface AppLayout {
    data object Phone : AppLayout
    data object Desktop : AppLayout
}

/** The layout the app is drawn in; [AppLayout.Phone] wherever nothing provides one (Android, tests of the phone layout). */
val LocalAppLayout = staticCompositionLocalOf<AppLayout> { AppLayout.Phone }

/** WEB-014: Settings → Table → "Layout", kept per browser: [Automatic] (the default) chooses by the window ([AppLayouts.choose]). */
sealed class LayoutChoice(val key: String, val label: String, val explanation: String) {
    data object Automatic : LayoutChoice("automatic", "Automatic", "Desktop on a large window with a mouse or trackpad, else phone")
    data object Phone : LayoutChoice("phone", "Phone", "The table and screens as in the Android app")
    data object Desktop : LayoutChoice("desktop", "Desktop", "The table and screens for a large screen, mouse and keyboard")

    companion object {
        val all: List<LayoutChoice> get() = listOf(Automatic, Phone, Desktop)
        fun of(key: String?): LayoutChoice = all.firstOrNull { it.key == key } ?: Automatic
    }
}

object AppLayouts {
    /** WEB-013: the smallest window (CSS px) the automatic choice draws the desktop layout in. */
    const val MIN_DESKTOP_WIDTH = 1024
    const val MIN_DESKTOP_HEIGHT = 600

    /**
     * WEB-013: the layout for [choice] in a window of [width] × [height] dp (CSS px in a browser) whose main pointer [hovers]
     * (a mouse or trackpad): Automatic draws the desktop layout when the window is at least 1024 × 600 and the pointer can
     * hover, otherwise the phone layout.
     */
    fun choose(choice: LayoutChoice, width: Int, height: Int, hovers: Boolean): AppLayout = when (choice) {
        LayoutChoice.Phone -> AppLayout.Phone
        LayoutChoice.Desktop -> AppLayout.Desktop
        LayoutChoice.Automatic ->
            if (width >= MIN_DESKTOP_WIDTH && height >= MIN_DESKTOP_HEIGHT && hovers) AppLayout.Desktop else AppLayout.Phone
    }
}

/** WEB-014: the Layout setting, saved in this browser (as card sizes are on a device, TBL-034). */
class LayoutPreferences(platform: AppPlatform) {
    private val store = platform.preferences("layout")
    var choice: LayoutChoice
        get() = LayoutChoice.of(store.getString(KEY, null))
        set(value) {
            AppLog.info("settings", "Layout: ${value.key}")
            store.edit().putString(KEY, value.key).apply()
        }
    fun observe(onChange: () -> Unit): () -> Unit = store.observe { changed -> if (changed == null || changed == KEY) onChange() }
    private companion object { const val KEY = "choice" }
}

@Composable
private fun rememberLayoutChoice(): Pair<State<LayoutChoice>, LayoutPreferences> {
    val platform = AppPlatform.current
    val preferences = remember(platform) { LayoutPreferences(platform) }
    val state = remember(preferences) { mutableStateOf(preferences.choice) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.choice }
        state.value = preferences.choice
        onDispose { stop() }
    }
    return state to preferences
}

/**
 * WEB-013: the layout to draw the app in now: always the phone layout on Android; in a browser the Layout setting's
 * ([LayoutChoice]), Automatic following the window's size and the pointer as they change. Logs each change of layout.
 */
@Composable
fun rememberAppLayout(): AppLayout {
    if (!desktopLayoutOffered) return AppLayout.Phone
    val choice = rememberLayoutChoice().first.value
    val width = screenWidthDp()
    val height = screenHeightDp()
    val hovers = pointerHovers()
    val layout = AppLayouts.choose(choice, width, height, hovers)
    val logged = remember { arrayOfNulls<AppLayout>(1) }
    SideEffect { if (logged[0] != layout) {
        logged[0] = layout
        AppLog.info("layout", "Layout ${if (layout == AppLayout.Desktop) "desktop" else "phone"} (setting ${choice.key}, " +
            "window ${width}x$height, pointer ${if (hovers) "hovers" else "doesn't hover"})")
    } }
    return layout
}

/** WEB-014: the Settings rows choosing the layout, in the Table section; shown only where there is a choice (the web). */
@Composable
fun LayoutSetting() {
    if (!desktopLayoutOffered) return
    val (state, preferences) = rememberLayoutChoice()
    val current = state.value
    Column(Modifier.fillMaxWidth().selectableGroup().testTag("layout-setting")) {
        SettingsLabel("Layout", "How the table and screens are arranged in this browser", Modifier.padding(top = 12.dp, bottom = 4.dp))
        for (choice in LayoutChoice.all) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("layout-${choice.key}")
                .selectable(choice == current, role = Role.RadioButton) { preferences.choice = choice },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(choice == current, onClick = null)
                SettingsLabel(choice.label, choice.explanation, Modifier.weight(1f).padding(vertical = 4.dp))
            }
        }
    }
}
