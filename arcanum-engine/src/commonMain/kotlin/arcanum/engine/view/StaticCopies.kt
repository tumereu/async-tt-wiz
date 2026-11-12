package arcanum.engine.view

import arcanum.engine.state.withStatus
import arcanum.engine.state.fixedCopies
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder

/**
 * CR 707.2c: the copiable values a static copy effect grants are determined only as it first starts to apply. Kept with each
 * permanent it applies to ([arcanum.engine.state.GameObject.fixedCopies]), recorded as the engine checks the game before a player
 * gets priority (with the state-based actions, CR 117.5), and dropped once that effect no longer applies, so applying again later
 * fixes the values anew. Until it is recorded, the effect copies the values the original has then (the same at that moment).
 */
internal object StaticCopies {
    fun keep(recorder: Recorder) {
        val state = recorder.state
        val copiable = Copiable(state)
        state.battlefield.forEach { id ->
            val now = copiable.fixedCopiesNow(id) ?: return@forEach
            val o = state.objOrThrow(id)
            if (now != o.fixedCopies) recorder.record(Change.ObjectUpdated(o, o.withStatus(arcanum.engine.state.ObjectStatus.FixedCopies(now).key, arcanum.engine.state.ObjectStatus.FixedCopies(now).takeIf { now.isNotEmpty() })))
        }
    }
}
