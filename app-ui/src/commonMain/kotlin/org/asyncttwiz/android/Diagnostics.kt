package org.asyncttwiz.android

import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.Preferences
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.File

/** The player's answer to "Send error reports?", stored on this device (LOB-104). */
enum class ReportChoice { UNASKED, ON, OFF }

class ReportChoiceStore(private val preferences: Preferences) {
    constructor(platform: AppPlatform) : this(platform.preferences("diagnostics"))
    var choice: ReportChoice
        get() = when (preferences.getString(KEY, null)) { "on" -> ReportChoice.ON; "off" -> ReportChoice.OFF; else -> ReportChoice.UNASKED }
        set(value) { preferences.edit().apply {
            if (value == ReportChoice.UNASKED) remove(KEY) else putString(KEY, if (value == ReportChoice.ON) "on" else "off")
        }.apply() }
    private companion object { const val KEY = "reports" }
}

/** One switch for crash, slow-action, stutter and reconnect reports (SYS-030, LOB-090). Test builds always
 * report; production builds only after the player opts in on this device (LOB-104). */
object Diagnostics {
    /** Test and debug builds: always on, no question asked. */
    val alwaysOn: Boolean get() = AppBuild.DIAGNOSTICS
    @Volatile var enabled: Boolean = AppBuild.DIAGNOSTICS
        private set
    /** What recording reports needs from the platform, from [start]. */
    private var hooks: ReportHooks? = null

    init { installEngineDiagnostics() }

    fun enabledFor(alwaysOn: Boolean, choice: ReportChoice) = alwaysOn || choice == ReportChoice.ON

    fun start(hooks: ReportHooks) {
        this.hooks = hooks
        enabled = enabledFor(alwaysOn, ReportChoiceStore(AppPlatform.current).choice)
        if (enabled) DebugCrashes.install(hooks)
    }

    /** Takes effect at once: turning on starts recording; turning off stops it and deletes reports not yet sent. */
    fun choose(on: Boolean) {
        ReportChoiceStore(AppPlatform.current).choice = if (on) ReportChoice.ON else ReportChoice.OFF
        val now = enabledFor(alwaysOn, ReportChoiceStore(AppPlatform.current).choice)
        enabled = now
        if (now) hooks?.let(DebugCrashes::install) else DebugCrashes.discard()
    }
}

/** What recording reports needs from the platform (Android: [AndroidReportHooks], from the Application). */
interface ReportHooks {
    /** Where reports wait to be sent. */
    val directory: File
    /** Starts the platform's own watchers once reports are recorded: [DebugLatency.appBackgrounded] when the app goes to
     * the background, and the screens' frames for [DebugJank] ([DebugJank.track], [DebugJank.frame]). */
    fun start()
}

/** Asked once, on the main menu or the Games list after sign-in: never over a game, pod or another prompt. */
object ReportPrompt {
    fun shouldPrompt(alwaysOn: Boolean, choice: ReportChoice, signedIn: Boolean, destination: ShellDestination,
        selectedGame: String?, blocked: Boolean): Boolean =
        !alwaysOn && choice == ReportChoice.UNASKED && signedIn && !blocked &&
            (destination == ShellDestination.MENU || (destination == ShellDestination.GAMES && selectedGame == null))
}
