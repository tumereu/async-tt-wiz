package org.asyncttwiz.appui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.asyncttwiz.android.AppLog

@Composable actual fun rememberMoveVibration(): (why: String) -> Unit {
    val context = LocalContext.current
    val platform = AppPlatform.current
    return remember(context, platform) {
        val preferences = org.asyncttwiz.android.MoveVibrationPreferences(platform)
        val vibrate: (String) -> Unit = { why ->
            // TRN-167: its own setting, apart from the Your move sound
            if (preferences.enabled) MoveVibration.vibrate(context, why)
            else AppLog.info("game", "Your move vibration: off in Settings ($why)")
        }
        vibrate
    }
}

/** TRN-167: Settings offers "Vibrate on your move" in the Android app. */
actual val moveVibrationSetting: Boolean = true

/**
 * TRN-075 (owner report 2026-11-19, the cue never felt): the Your move cue's vibration on Android. It was a Compose haptic
 * tick (HapticFeedbackType.SegmentFrequentTick: the lightest tick there is, CLOCK_TICK below Android 14, which many phones
 * don't play at all), and a view's haptic feedback is dropped whenever the phone's touch feedback setting is off. Now a
 * one-shot of [MILLIS] at the default strength through the device's vibrator (VibratorManager from Android 12), which
 * needs the VIBRATE permission and plays whatever the touch feedback setting.
 */
object MoveVibration {
    /** How long the vibration lasts: clearly felt, still short. */
    const val MILLIS = 70L

    /** Vibrates once for the cue ([why]: what it is for), with a line in the app log saying why and what the device
     * reports (a vibrator, amplitude control, the ringer mode, which mutes vibration on some phones); never throws. */
    fun vibrate(context: Context, why: String) {
        val vibrator = try { vibrator(context) } catch (e: RuntimeException) {
            AppLog.warn("game", "Your move vibration: no vibrator service ($why)", e); null
        }
        val has = vibrator?.hasVibrator() == true
        val ringer = try {
            when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                AudioManager.RINGER_MODE_NORMAL -> "normal"
                else -> "unknown"
            }
        } catch (_: RuntimeException) { "unknown" }
        AppLog.info("game", "Your move vibration: $MILLIS ms ($why); vibrator: ${if (has) "yes" else "none"}" +
            (if (has) ", amplitude control: ${if (vibrator?.hasAmplitudeControl() == true) "yes" else "no"}" else "") +
            ", ringer: $ringer, Android ${Build.VERSION.SDK_INT}")
        if (vibrator == null || !has) return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: RuntimeException) {
            AppLog.warn("game", "Your move vibration couldn't start ($why)", e)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}
