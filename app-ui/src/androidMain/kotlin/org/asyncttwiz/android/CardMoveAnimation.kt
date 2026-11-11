package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.appui.AppPlatform
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

internal class CardMovePreferences(platform: AppPlatform, accountId: String = "") {
    private val preferences = platform.preferences("card-move-animation")
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
            val stop = preferences.observe { changed ->
                if (changed == key) state.floatValue = multiplier
            }
            state.floatValue = multiplier
            onDispose { stop() }
        }
        return state
    }
}

@Composable
internal fun rememberCardMovePreferences(accountId: String = ""): CardMovePreferences {
    val platform = AppPlatform.current
    return remember(platform, accountId) { CardMovePreferences(platform, accountId) }
}
