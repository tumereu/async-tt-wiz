package org.asyncttwiz.android

import kotlin.math.abs

internal enum class PodPickSwipePhase { IDLE, DRAGGING, FADING }

/** The lifted copy keeps its final offset throughout dismissal, including cancellation. */
internal data class PodPickSwipeState(
    val offset: Float = 0f,
    val phase: PodPickSwipePhase = PodPickSwipePhase.IDLE,
    val decision: PodPickMark? = null,
    val generation: Int = 0,
) {
    fun drag(offset: Float) = PodPickSwipeState(offset, PodPickSwipePhase.DRAGGING,
        generation = if (phase == PodPickSwipePhase.DRAGGING) generation else generation + 1)

    fun progress(width: Int): Float = if (width > 0) (abs(offset) / width / .3f).coerceIn(0f, 1f) else 0f

    fun finish(width: Int, burnEnabled: Boolean, cancelled: Boolean = false): PodPickSwipeState {
        if (phase != PodPickSwipePhase.DRAGGING) return this
        val mark = when {
            cancelled || width <= 0 || progress(width) < 1f -> null
            offset > 0f -> PodPickMark.KEEP
            burnEnabled -> PodPickMark.BURN
            else -> null
        }
        return copy(phase = PodPickSwipePhase.FADING, decision = mark)
    }
}
