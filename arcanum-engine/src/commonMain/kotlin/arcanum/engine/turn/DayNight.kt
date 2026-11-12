package arcanum.engine.turn

import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.engine.action.Act
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.query.History
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.view.View

/**
 * Day and night (CR 731) with daybound and nightbound (CR 702.145): the game's designation is kept in its ledger
 * ([Evaluator.DAY_NIGHT]: 0 neither, [Evaluator.DAY], [Evaluator.NIGHT]). It becomes day when a player controls a daybound
 * permanent and it's neither (702.145d), night when a nightbound one and no daybound one (702.145g); in the untap step the previous
 * turn's spells may change it (731.2); daybound permanents transform as it becomes night and nightbound ones as it becomes day, at
 * once (702.145c, f), and a daybound card enters transformed at night (702.145b).
 */
internal object DayNight {
    fun of(state: GameState): Int = state.ledger[Evaluator.DAY_NIGHT] ?: 0

    /** It becomes day or night; "day becomes night" and "night becomes day" are events (CR 731.1a). */
    fun set(recorder: Recorder, value: Int) {
        val before = of(recorder.state)
        if (before == value) return
        recorder.record(Change.Ledger(null, Evaluator.DAY_NIGHT, before, value))
        if (before != 0) recorder.record(Change.Happened(GameEvent.DayNightChanged(night = value == Evaluator.NIGHT)))
    }

    /** CR 731.2: as the turn of [state] begins, [previous]'s spells cast during the turn before decide. */
    fun untapCheck(recorder: Recorder, previous: arcanum.engine.state.PlayerId, previousTurn: Int) {
        val now = of(recorder.state)
        if (now == 0) return
        val snap = Snapshot(recorder.state)
        val cast = History.count(Evaluator(snap.board, Context(you = previous)), EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), Measure.Events,
            turn = previousTurn)
        if (now == Evaluator.DAY && cast == 0) set(recorder, Evaluator.NIGHT)
        else if (now == Evaluator.NIGHT && cast >= 2) set(recorder, Evaluator.DAY)
    }

    /** Checked with the state-based actions (not one of them, CR 702.145c, d, f, g): it becomes day or night for the daybound
     * and nightbound permanents there are, and those that don't match transform. */
    fun check(act: Act, view: View) {
        val state = act.state
        fun has(id: arcanum.engine.state.ObjectId, keyword: Keyword) = view[id]?.characteristics?.abilities.orEmpty().any { it.ability == keyword }
        val daybound = state.battlefield.filter { has(it, Keyword.Daybound) }
        val nightbound = state.battlefield.filter { has(it, Keyword.Nightbound) }
        if (of(state) == 0) {
            if (daybound.isNotEmpty()) set(act.recorder, Evaluator.DAY)
            else if (nightbound.isNotEmpty()) set(act.recorder, Evaluator.NIGHT)
        }
        val night = of(state) == Evaluator.NIGHT
        val day = of(state) == Evaluator.DAY
        (if (night) daybound.filter { state.obj(it)?.face == 0 } else emptyList()).plus(
            if (day) nightbound.filter { state.obj(it)?.face == 1 } else emptyList()).forEach { id ->
            val o = state.objOrThrow(id)
            if (o.definition.layout != Layout.Transform || o.definition.faces.size < 2) return@forEach
            if (view[id]?.has { it == arcanum.api.Mechanic.CantTransform } == true) return@forEach // CR 701.27, 101.2
            act.record(Change.Face(id, o.face, 1 - o.face, listOf(Meaning.Transformed)))
            act.record(Change.Restamped(id, o.timestamp, state.nextTimestamp()))
        }
    }

    /** CR 702.145b: at night, a daybound double-faced card enters transformed. */
    fun entersTransformed(state: GameState, o: GameObject): Boolean = of(state) == Evaluator.NIGHT && o.definition.layout == Layout.Transform &&
        o.face == 0 && o.definition.faces.firstOrNull()?.abilities.orEmpty().any { it == Keyword.Daybound }
}
