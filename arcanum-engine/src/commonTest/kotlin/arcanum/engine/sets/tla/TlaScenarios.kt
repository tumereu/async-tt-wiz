package arcanum.engine.sets.tla

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain cards the Avatar: The Last Airbender scenarios play with. */
internal object TlaExtras {
    val bear = Cards.invent("Meadow Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val brute = Cards.invent("Boulder Brute", "{3}{G}", "Creature — Giant", "", "4/4")
    val flier = Cards.invent("Cliff Hawk", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact", "")
    val zap = Cards.invent("Spark Zap", "{R}", "Instant", "Spark Zap deals 2 damage to any target.")
    val notes = Cards.invent("Scribbled Notes", "{U}", "Sorcery", "Draw a card.")
    val lesson = Cards.invent("Dusty Lesson", "{W}", "Sorcery — Lesson", "You gain 1 life.")
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val quest = CounterKind.Named("quest")
}

/** Passes priority until the Stack is empty or a decision other than ordering triggers is open; triggers put on the Stack at
 * once are ordered as listed (CR 603.3b). */
internal fun Scenario.resolveAll() {
    var guard = 60
    while (guard-- > 0) {
        val open = pending
        if (open is Decision.OrderTriggers) { answer(Answer.Order(open.triggers.indices.toList())); continue }
        if (open != null || state.stack.isEmpty()) return
        allPass()
    }
}

/** Answers the open target decisions one slot after another with the named objects or players (as many as each slot takes), then
 * resolves on; a slot with one legal choice is chosen by the engine and asks nothing. */
internal fun Scenario.targets(vararg names: String) {
    val queue = ArrayDeque(names.toList())
    while (queue.isNotEmpty()) {
        val d = pending as? Decision.Targets ?: break
        answer(Answer.Targets(List(minOf(queue.size, d.max ?: queue.size)) { target(queue.removeFirst()) }))
        resolveAll()
    }
    resolveAll()
}

/** Answers an open yes/no decision, then resolves on. */
internal fun Scenario.yes(answer: Boolean = true) { answer(Answer.YesNo(answer)); resolveAll() }

/** Answers an open modes decision, then resolves on. */
internal fun Scenario.modes(vararg modes: Int) { answer(Answer.Modes(modes.toList())); resolveAll() }

/** Chooses objects for the open decision by name, then resolves on. */
internal fun Scenario.choose(vararg names: String) {
    if (pending !is Decision.ChooseObjects) return resolveAll()
    val options = (pending as? Decision.ChooseObjects)?.options.orEmpty()
    val taken = mutableListOf<arcanum.engine.state.ObjectId>()
    names.forEach { taken += preferred(it, listOf { o -> o.id in options }, taken) }
    answer(Answer.Objects(taken)); resolveAll()
}

/** Sets [name] up with its back face up (setup only). */
internal fun Scenario.backFaceUp(name: String) {
    val id = card(name)
    state.objects[id] = state.objOrThrow(id).copy(face = 1)
}
