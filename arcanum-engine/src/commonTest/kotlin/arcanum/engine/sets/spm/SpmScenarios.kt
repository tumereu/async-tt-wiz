package arcanum.engine.sets.spm

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/* Shared pieces of the Marvel's Spider-Man (SPM) scenarios: invented vanilla creatures and small helpers. */

internal val p1p1 = CounterKind.PowerToughness(1, 1)
internal val spmBear = Cards.invent("Rooftop Bear", "{1}{G}", "Creature — Bear", "", "2/2")
internal val spmOgre = Cards.invent("Rooftop Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
internal val spmGiant = Cards.invent("Rooftop Giant", "{4}{G}", "Creature — Giant", "", "5/5")
internal val spmSpider = Cards.invent("Rooftop Spider", "{1}{G}", "Creature — Spider", "", "2/2")
internal val spmVillain = Cards.invent("Rooftop Thug", "{1}{B}", "Creature — Human Villain", "", "2/2")
internal val spmHero = Cards.invent("Rooftop Champion, Masked", "{1}{W}", "Legendary Creature — Human Hero", "", "2/2")

/** Passes until the Stack is empty or a decision is open. */
internal fun Scenario.resolveAll() {
    var guard = 40
    while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
}

/** Answers an open yes/no question. */
internal fun Scenario.yes(answer: Boolean = true) = answer(Answer.YesNo(answer))

/** Answers the open decision with no objects (declining "you may choose"). */
internal fun Scenario.none() = answer(Answer.Objects(emptyList()))

/** Answers the open target decision with [names] (players by their name). */
internal fun Scenario.targets(vararg names: String) = answer(Answer.Targets(names.map { target(it) }))

/** Answers whatever open decisions [answers] know, then resolves, until none is open and the Stack is empty. */
internal fun Scenario.drive(vararg answers: Pair<(Decision) -> Boolean, Scenario.() -> Unit>) {
    var guard = 60
    while (guard-- > 0) {
        val d = pending
        if (d == null) { if (state.stack.isEmpty()) return; allPass(); continue }
        val handler = answers.firstOrNull { it.first(d) } ?: return
        handler.second(this)
    }
}

/** Passes (declaring no blockers) until [step], answering the decisions [answers] know on the way. */
internal fun Scenario.untilStep(step: arcanum.api.TurnStep, vararg answers: Pair<(Decision) -> Boolean, Scenario.() -> Unit>) {
    var guard = 80
    while (state.turn.step != step && guard-- > 0) {
        val d = pending
        if (d == null) {
            if (state.priority.holder == null && state.turn.step == arcanum.api.TurnStep.DeclareBlockers) bob.blocks() else allPass()
            continue
        }
        val handler = answers.firstOrNull { it.first(d) } ?: kotlin.test.fail("Unexpected decision $d")
        handler.second(this)
    }
}
