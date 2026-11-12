package arcanum.engine.sets.fin

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain cards the Final Fantasy scenarios play with. */
internal object FinExtras {
    val bear = Cards.invent("Crystal Meadow Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Crystal Alley Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Crystal Hill Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val flier = Cards.invent("Crystal Sky Hawk", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    val wall = Cards.invent("Crystal Ward Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    val trinket = Cards.invent("Crystal Trinket", "{1}", "Artifact", "")
    val charm = Cards.invent("Crystal Charm", "{1}{W}", "Enchantment", "")
    val notes = Cards.invent("Crystal Notes", "{U}", "Sorcery", "Draw a card.")
    val zap = Cards.invent("Crystal Zap", "{R}", "Instant", "Crystal Zap deals 2 damage to any target.")
    val doom = Cards.invent("Crystal Doom", "{B}", "Instant", "Destroy target creature.")
    val legend = Cards.invent("Garrin, Crystal Warden", "{1}{W}", "Legendary Creature — Human Soldier", "", "2/2")
    val lance = Cards.invent("Crystal Lance", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val m1m1 = CounterKind.PowerToughness(-1, -1)
    val lore = CounterKind.Lore
    val stun = CounterKind.Stun
    val finality = CounterKind.Finality
    val indestructible = CounterKind.Keyword(arcanum.api.Keyword.Indestructible)
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

/** Answers an open option decision, then resolves on. */
internal fun Scenario.option(chosen: String) { answer(Answer.Option(chosen)); resolveAll() }

/** Answers an open player decision, then resolves on. */
internal fun Scenario.choosePlayer(player: arcanum.engine.scenario.ScenarioPlayer) { answer(Answer.Player(player.id)); resolveAll() }

/** Chooses objects for the open decision by name, then resolves on. */
internal fun Scenario.choose(vararg names: String) {
    if (pending !is Decision.ChooseObjects) return resolveAll()
    val options = (pending as? Decision.ChooseObjects)?.options.orEmpty()
    val taken = mutableListOf<arcanum.engine.state.ObjectId>()
    names.forEach { taken += preferred(it, listOf { o -> o.id in options }, taken) }
    answer(Answer.Objects(taken)); resolveAll()
}

/** Pays an open mana payment as the engine suggests, then resolves on. */
internal fun Scenario.pay() {
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    resolveAll()
}

/** Answers the open target decision with the ability or spell [below] items under the top of the Stack (the top before the spell
 * being cast, which is on the Stack already), then pays as suggested. */
internal fun Scenario.targetStackItem(below: Int = 1) {
    val d = pending as Decision.Targets
    val item = state.stack.reversed().filter { arcanum.engine.state.TargetRef.Object(it) in d.options }.getOrNull(below - 1)
        ?: state.stack.first { arcanum.engine.state.TargetRef.Object(it) in d.options }
    answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(item))))
    pay()
}

/** Sets [name] up with its back face up (setup only). */
internal fun Scenario.backFaceUp(name: String) {
    val id = card(name)
    state._objects[id] = state.objOrThrow(id).copy(face = 1)
}

/** Gives both players a library of plain cards to draw from. */
internal fun Scenario.libraries(count: Int = 6) {
    alice.library(*Array(count) { FinExtras.notes })
    bob.library(*Array(count) { FinExtras.notes })
}
