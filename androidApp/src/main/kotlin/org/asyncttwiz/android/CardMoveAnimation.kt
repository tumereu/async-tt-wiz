package org.asyncttwiz.android

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.hypot

internal const val DEFAULT_CARD_MOVE_SPEED = 2.5f

/** Average travel speed has a floor; the scene clock is unchanged, so short moves wait at the end. */
internal fun cardMoveDurationMillis(distanceDp: Float, speedDpPerMs: Float = DEFAULT_CARD_MOVE_SPEED,
    sceneDurationMillis: Int = 180): Int {
    require(speedDpPerMs.isFinite() && speedDpPerMs > 0f)
    require(distanceDp.isFinite() && distanceDp >= 0f && sceneDurationMillis > 0)
    return (distanceDp / speedDpPerMs).toInt().coerceIn(1, sceneDurationMillis)
}

internal fun cardMoveProgress(from: TableBounds, to: TableBounds, sceneProgress: Float, sceneDuration: Int, speed: Float): Float {
    val distance = hypot(to.center.x - from.center.x, to.center.y - from.center.y)
    if (distance == 0f) return sceneProgress // Rotation/fades retain their existing scene timing.
    val t = (sceneProgress * sceneDuration / cardMoveDurationMillis(distance, speed, sceneDuration)).coerceIn(0f, 1f)
    // Quadratic ease-out: no slow ease-in before a nearby card starts moving.
    return 1f - (1f - t) * (1f - t)
}

internal class CardMovePreferences(context: Context, accountId: String = "") {
    private val preferences = context.applicationContext.getSharedPreferences("card-move-animation", Context.MODE_PRIVATE)
    private val key = "dp-per-ms/$accountId"
    var speed: Float
        get() = if (BuildConfig.DEBUG) preferences.getFloat(key, preferences.getFloat("dp-per-ms", DEFAULT_CARD_MOVE_SPEED))
            .takeIf { it.isFinite() }?.coerceIn(.5f, 10f) ?: DEFAULT_CARD_MOVE_SPEED else DEFAULT_CARD_MOVE_SPEED
        set(value) { if (BuildConfig.DEBUG && value.isFinite()) preferences.edit().putFloat(key, value.coerceIn(.5f, 10f)).apply() }
}

@Composable
internal fun rememberCardMovePreferences(accountId: String = ""): CardMovePreferences {
    val context = LocalContext.current
    return remember(context, accountId) { CardMovePreferences(context, accountId) }
}
