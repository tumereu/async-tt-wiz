package arcanum.engine.review

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.state.ObjectId

/** Helpers and invented cards for the third adversarial review's scenarios. */
internal object Review3 {
    /** A plain 3/3 Golem, for "Golems you control have first strike" (Golem Grafter). */
    val golem = Cards.invent("Review3 Golem", "{3}", "Artifact Creature — Golem", "", "3/3")

    /** "{0}: Target creature gains double strike until end of turn." */
    val doubleStrikeBanner = Cards.invent("Review3 Dueling Banner", "{0}", "Artifact",
        "{0}: Target creature gains double strike until end of turn.")

    /** "{0}: Target creature gains first strike until end of turn." */
    val firstStrikeBanner = Cards.invent("Review3 Striking Banner", "{0}", "Artifact",
        "{0}: Target creature gains first strike until end of turn.")

    /** A plain 2/2 creature with no abilities. */
    val bear = Cards.invent("Review3 Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    /** A plain 1/1 creature with no abilities. */
    val squire = Cards.invent("Review3 Squire", "{W}", "Creature — Human Soldier", "", "1/1")
}

internal fun Scenario.yes() = answer(Answer.YesNo(true))
internal fun Scenario.no() = answer(Answer.YesNo(false))

/** Answers every open "you may" with [yes]. */
internal fun Scenario.answerMays(yes: Boolean) {
    var guard = 20
    while (pending is Decision.May && guard-- > 0) answer(Answer.YesNo(yes))
}

/** Passes priority around until the Stack is empty, answering "you may" with [mays] when given; stops at any other
 * decision. */
internal fun Scenario.passUntilEmpty(mays: Boolean? = null) {
    var guard = 40
    while (guard-- > 0) {
        when {
            pending is Decision.May && mays != null -> answer(Answer.YesNo(mays))
            pending != null -> return
            state.stack.isEmpty() -> return
            else -> allPass()
        }
    }
}

/** Like [Scenario.advanceTo], answering "you may" with [mays] on the way. */
internal fun Scenario.advanceAnswering(step: arcanum.api.TurnStep, turn: Int = state.turn.number, mays: Boolean = true) {
    var guard = 200
    while ((state.turn.step != step || state.turn.number != turn) && guard-- > 0) {
        when {
            pending is Decision.May -> answer(Answer.YesNo(mays))
            pending != null -> kotlin.test.fail("Waiting for $pending before $step")
            state.priority.holder == null -> declareNone()
            else -> passPriority()
        }
    }
}

/** The [Input.Cast] options [player] has now for the card named [name] (any zone). */
internal fun Scenario.castOptions(player: ScenarioPlayer, name: String): List<Input.Cast> {
    val ids = cards(name).map { it.id }.toSet()
    return Engine.options(state, player.id).filterIsInstance<Input.Cast>().filter { it.card in ids }
}

/** The [Input.Activate] options [player] has now for abilities of objects named [name]. */
internal fun Scenario.activateOptions(player: ScenarioPlayer, name: String): List<Input.Activate> {
    val ids = cards(name).map { it.id }.toSet()
    return Engine.options(state, player.id).filterIsInstance<Input.Activate>().filter { it.source in ids }
}

/** Whether [Engine.perform] refuses [input] now (with [IllegalArgumentException]). */
internal fun Scenario.refuses(input: Input): Boolean = try {
    Engine.perform(state, input)
    false
} catch (e: IllegalArgumentException) {
    true
}

/** The object id of the card named [name] in [zone]. */
internal fun Scenario.idIn(name: String, zone: arcanum.api.Zone): ObjectId = card(name, zone = zone)
