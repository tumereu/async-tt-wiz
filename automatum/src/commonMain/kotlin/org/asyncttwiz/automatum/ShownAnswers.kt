package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.GameState

/**
 * I-D27 (final cast review #1): the engine lists answers to "choose a card name" and "choose a creature type" from every
 * object of the state, and the flows' states hold full information (I-D17), so its sample would name the other player's
 * hidden hand. The flows list only names and types of objects the deciding player may see; the app's panel offers its own
 * controls for these choices anyway (any name from the device's card catalog, every creature type), never the game's
 * card data.
 */
internal object ShownAnswers {
    fun of(state: GameState, decision: Decision, answers: List<Answer>): List<Answer> {
        if (decision !is Decision.ChooseCardName && !(decision is Decision.ChooseSubtype && decision.among == null)) return answers
        val seen = Engine.visible(state, decision.player)
        val view = Engine.view(state)
        val names = seen.mapNotNull { state.obj(it)?.definition?.faces?.firstOrNull()?.name }.toSet()
        val types = seen.flatMap { view[it]?.characteristics?.subtypes.orEmpty() }.toSet()
        return answers.filter { answer ->
            when (answer) {
                is Answer.CardName -> answer.name in names
                is Answer.SubtypeChoice -> answer.subtype in types
                else -> true
            }
        }
    }
}
