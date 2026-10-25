package org.asyncttwiz.android

import org.asyncttwiz.protocol.CardTapDiagnostic
import java.time.Instant
import kotlin.math.roundToInt

/** Fixed-size debug observations, independent of the gameplay/history queue. */
internal class CardTapRecorder(private val now: () -> Long = System::nanoTime,
    private val record: (CardTapDiagnostic) -> Unit) {
    private data class Rotation(val game: String, val up: Long, var sample: CardTapDiagnostic,
        var started: Boolean = false, var first: Long? = null, var last: Long? = null)
    private val rotations = ArrayDeque<Rotation>()
    @Synchronized fun gesture(game: String, event: String, popup: Boolean = false, catchUp: Boolean = false, tree: Boolean = false) {
        expire()
        if (event == "recognized") {
            if (rotations.size == 32) finish(rotations.removeFirst(), "animationNotObserved")
            rotations.add(Rotation(game, now(), CardTapDiagnostic(Instant.now().toString(), game, "dropped",
                "callbackNotObserved", popupOpen = popup, catchUpInFlight = catchUp, treeWorkInFlight = tree)))
        } else if (event.startsWith("nearMiss:")) {
            record(CardTapDiagnostic(Instant.now().toString(), game, "nearMiss", event.substringAfter(':'),
                popupOpen = popup, catchUpInFlight = catchUp, treeWorkInFlight = tree))
        } else {
            val pending = rotations.lastOrNull { it.game == game && it.first == null } ?: return
            when {
                event == "popupClosing" -> pending.sample = pending.sample.copy(popupClosing = true)
                event == "rotated" -> pending.sample = pending.sample.copy(outcome = "rotated", reason = "predictedAndQueued")
                event.startsWith("dropped:") -> {
                    pending.sample = pending.sample.copy(outcome = "dropped", reason = event.substringAfter(':').take(100))
                    rotations.remove(pending); record(pending.sample)
                }
            }
        }
    }
    @Synchronized fun animation(game: String) { rotations.filter { it.game == game }.forEach { it.started = true } }
    @Synchronized fun frame(game: String, progress: Float, refreshRate: Float, catchUp: Boolean, tree: Boolean) {
        expire()
        val at = now()
        val complete = mutableListOf<Rotation>()
        for (r in rotations) {
            if (r.game != game || !r.started || r.sample.outcome != "rotated" || progress <= 0f) continue
            if (r.first == null) {
                r.first = at
                r.sample = r.sample.copy(firstAnimationFrameMs = (at - r.up).coerceAtLeast(0) / 1_000_000.0)
            }
            val missed = r.last?.let { ((at - it) / (1_000_000_000.0 / refreshRate.coerceAtLeast(1f))).roundToInt() - 1 } ?: 0
            r.sample = r.sample.copy(droppedFrames = r.sample.droppedFrames + missed.coerceAtLeast(0),
                catchUpInFlight = r.sample.catchUpInFlight || catchUp, treeWorkInFlight = r.sample.treeWorkInFlight || tree)
            r.last = at
            if (progress >= 1f || at - r.first!! >= 90_000_000) complete.add(r)
        }
        complete.forEach { rotations.remove(it); record(it.sample) }
    }
    @Synchronized fun expire() {
        while (rotations.firstOrNull()?.let { now() - it.up >= 3_000_000_000 } == true)
            finish(rotations.removeFirst(), "animationNotObserved")
    }
    private fun finish(rotation: Rotation, reason: String) {
        record(rotation.sample.copy(reason = if (rotation.sample.outcome == "rotated") reason else rotation.sample.reason))
    }
}

internal class CardTapBuffer(private val capacity: Int = 100) {
    private val values = ArrayDeque<CardTapDiagnostic>()
    @Synchronized fun add(value: CardTapDiagnostic) { values.addLast(value); while (values.size > capacity) values.removeFirst() }
    @Synchronized fun snapshot() = values.toList()
    @Synchronized fun acknowledge(batch: List<CardTapDiagnostic>) { batch.forEach { values.remove(it) } }
}
