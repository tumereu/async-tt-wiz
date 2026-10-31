package org.asyncttwiz.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

/** The player's answer to "Send error reports?", stored on this device (LOB-104). */
internal enum class ReportChoice { UNASKED, ON, OFF }

internal class ReportChoiceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("diagnostics", Context.MODE_PRIVATE))
    var choice: ReportChoice
        get() = when (preferences.getString(KEY, null)) { "on" -> ReportChoice.ON; "off" -> ReportChoice.OFF; else -> ReportChoice.UNASKED }
        set(value) { preferences.edit().apply {
            if (value == ReportChoice.UNASKED) remove(KEY) else putString(KEY, if (value == ReportChoice.ON) "on" else "off")
        }.apply() }
    private companion object { const val KEY = "reports" }
}

/** One switch for crash, slow-action, stutter and reconnect reports (SYS-030, LOB-090). Test builds always
 * report; production builds only after the player opts in on this device (LOB-104). */
internal object Diagnostics {
    /** Test and debug builds: always on, no question asked. */
    val alwaysOn: Boolean get() = BuildConfig.DIAGNOSTICS
    @Volatile var enabled: Boolean = BuildConfig.DIAGNOSTICS
        private set

    fun enabledFor(alwaysOn: Boolean, choice: ReportChoice) = alwaysOn || choice == ReportChoice.ON

    fun start(application: Application) {
        enabled = enabledFor(alwaysOn, ReportChoiceStore(application).choice)
        if (enabled) DebugCrashes.install(application)
    }

    /** Takes effect at once: turning on starts recording; turning off stops it and deletes reports not yet sent. */
    fun choose(context: Context, on: Boolean) {
        ReportChoiceStore(context).choice = if (on) ReportChoice.ON else ReportChoice.OFF
        val now = enabledFor(alwaysOn, ReportChoiceStore(context).choice)
        enabled = now
        val application = context.applicationContext as? Application
        if (now) application?.let(DebugCrashes::install) else DebugCrashes.discard()
    }
}

/** Asked once, on the main menu or the Games list after sign-in: never over a game, pod or another prompt. */
internal object ReportPrompt {
    fun shouldPrompt(alwaysOn: Boolean, choice: ReportChoice, signedIn: Boolean, destination: ShellDestination,
        selectedGame: String?, blocked: Boolean): Boolean =
        !alwaysOn && choice == ReportChoice.UNASKED && signedIn && !blocked &&
            (destination == ShellDestination.MENU || (destination == ShellDestination.GAMES && selectedGame == null))
}
