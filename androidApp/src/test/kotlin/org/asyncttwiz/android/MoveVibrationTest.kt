package org.asyncttwiz.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.appui.MoveVibration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRN-075 (owner report 2026-11-19: the Your move vibration was never felt on a real phone): the cue vibrates through the
 * device's vibrator, a one-shot of about 70 ms at the default strength, not a view's haptic tick (which the phone's touch
 * feedback setting mutes); the app holds the VIBRATE permission it needs; the app log says when and why, and whether the
 * device has a vibrator.
 */
@RunWith(RobolectricTestRunner::class)
class MoveVibrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() = ShadowVibrator.reset()

    private fun vibrator(): Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    // TRN-075
    @Test @Config(sdk = [35]) fun theCueVibratesOnceThroughTheVibratorManagersVibrator() {
        val logged = AppLog.text().lines().count { "Your move vibration" in it }
        MoveVibration.vibrate(context, "the move passed to alice in game g")
        val shadow = shadowOf(vibrator())
        assertTrue(shadow.isVibrating, "a real vibration was started")
        assertEquals(70L, shadow.milliseconds, "a one-shot of 70 ms")
        val lines = AppLog.text().lines().filter { "Your move vibration" in it }
        assertEquals(logged + 1, lines.size)
        assertTrue("70 ms (the move passed to alice in game g); vibrator: yes" in lines.last(), lines.last())
    }

    // TRN-075: below Android 12, through the Vibrator service
    @Test @Config(sdk = [30]) fun theCueVibratesThroughTheVibratorBelowAndroid12() {
        MoveVibration.vibrate(context, "the move passed to alice in game g")
        @Suppress("DEPRECATION") val vibrator = context.getSystemService(Vibrator::class.java)
        assertTrue(shadowOf(vibrator).isVibrating)
        assertEquals(MoveVibration.MILLIS, shadowOf(vibrator).milliseconds)
    }

    // TRN-075: a device without a vibrator: nothing started, nothing thrown, and the log says so
    @Test @Config(sdk = [35]) fun aDeviceWithoutAVibratorLogsItAndVibratesNot() {
        shadowOf(vibrator()).setHasVibrator(false)
        MoveVibration.vibrate(context, "the move passed to bob in game h")
        assertFalse(shadowOf(vibrator()).isVibrating)
        assertTrue(AppLog.text().lines().last { "Your move vibration" in it }.contains("(the move passed to bob in game h); vibrator: none"))
    }

    // TRN-075: the app asks for the permission the vibrator needs (a real phone refuses the vibration without it)
    @Test @Config(sdk = [35]) fun theAppHoldsTheVibratePermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(android.Manifest.permission.VIBRATE in info.requestedPermissions.orEmpty())
    }
}
