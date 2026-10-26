package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.hypot
import kotlin.math.roundToInt

internal const val DEFAULT_CARD_MOVE_SPEED = 1.2f
internal const val MAX_CARD_MOVE_DURATION_MILLIS = 350
internal const val MIN_CARD_MOVE_MULTIPLIER = .25f
internal const val MAX_CARD_MOVE_MULTIPLIER = 3f
internal fun cardMoveMultiplier(value: Float) = if (value.isFinite())
    value.coerceIn(MIN_CARD_MOVE_MULTIPLIER, MAX_CARD_MOVE_MULTIPLIER) else 1f

/** Short moves finish early; long moves are bounded by the shared live/playback scene clock. */
internal fun cardMoveDurationMillis(distanceDp: Float, speedDpPerMs: Float = DEFAULT_CARD_MOVE_SPEED,
    sceneDurationMillis: Int = MAX_CARD_MOVE_DURATION_MILLIS): Int {
    require(speedDpPerMs.isFinite() && speedDpPerMs > 0f)
    require(distanceDp.isFinite() && distanceDp >= 0f && sceneDurationMillis > 0)
    return (distanceDp / speedDpPerMs).roundToInt().coerceIn(1, sceneDurationMillis)
}

internal fun cardMoveProgress(from: TableBounds, to: TableBounds, sceneProgress: Float, sceneDuration: Int, speed: Float): Float {
    val distance = hypot(to.center.x - from.center.x, to.center.y - from.center.y)
    if (distance == 0f) return sceneProgress // Size-only changes use the fixed move duration.
    val t = (sceneProgress * sceneDuration / cardMoveDurationMillis(distance, speed, sceneDuration)).coerceIn(0f, 1f)
    // Quadratic ease-out: no slow ease-in before a nearby card starts moving.
    return 1f - (1f - t) * (1f - t)
}

internal class CardMovePreferences(context: Context, accountId: String = "") {
    private val preferences = context.applicationContext.getSharedPreferences("card-move-animation", Context.MODE_PRIVATE)
    private val key = "multiplier/$accountId"
    var multiplier: Float
        get() = cardMoveMultiplier(preferences.getFloat(key, 1f))
        set(value) { preferences.edit().putFloat(key, cardMoveMultiplier(value)).apply() }
    val speed: Float
        get() = DEFAULT_CARD_MOVE_SPEED * multiplier

    @Composable
    fun multiplierState(): State<Float> {
        val state = remember(this) { mutableFloatStateOf(multiplier) }
        DisposableEffect(this) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                if (changed == key) state.floatValue = multiplier
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            state.floatValue = multiplier
            onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}

@Composable
internal fun rememberCardMovePreferences(accountId: String = ""): CardMovePreferences {
    val context = LocalContext.current
    return remember(context, accountId) { CardMovePreferences(context, accountId) }
}
