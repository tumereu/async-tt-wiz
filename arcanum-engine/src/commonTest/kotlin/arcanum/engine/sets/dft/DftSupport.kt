package arcanum.engine.sets.dft

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain supporting cards shared by the Aetherdrift scenarios (invented, D15). */
internal object DftPlain {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Raceway Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Raceway Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Raceway Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val elf = Cards.invent("Raceway Elf", "{G}", "Creature — Elf", "", "1/1")
    val hawk = Cards.invent("Raceway Hawk", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val wall = Cards.invent("Raceway Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    val pirate = Cards.invent("Raceway Corsair", "{1}{U}", "Creature — Human Pirate", "", "2/2")
    val goblin = Cards.invent("Raceway Goblin", "{R}", "Creature — Goblin", "", "1/1")
    val insect = Cards.invent("Raceway Beetle", "{G}", "Creature — Insect", "", "1/1")
    val trinket = Cards.invent("Raceway Trinket", "{1}", "Artifact", "")
    val gizmo = Cards.invent("Raceway Gizmo", "{2}", "Artifact", "")
    val golem = Cards.invent("Raceway Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    val charm = Cards.invent("Raceway Charm", "{1}{W}", "Enchantment", "")
    val kart = Cards.invent("Raceway Kart", "{2}", "Artifact — Vehicle", "Crew 1", "3/3")
    val hauler = Cards.invent("Raceway Hauler", "{3}", "Artifact — Vehicle", "Crew 4", "6/6")
    val pony = Cards.invent("Raceway Pony", "{1}{W}", "Creature — Horse Mount", "Saddle 3", "2/2")
    val notes = Cards.invent("Raceway Notes", "{U}", "Sorcery", "Draw a card.")
    val zap = Cards.invent("Raceway Zap", "{R}", "Instant", "Raceway Zap deals 2 damage to any target.")
    val doom = Cards.invent("Raceway Doom", "{B}", "Instant", "Destroy target creature.")
    val growth = Cards.invent("Raceway Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    val counterspell = Cards.invent("Raceway Denial", "{U}{U}", "Instant", "Counter target spell.")
    val walker = Cards.invent("Raceway Walker", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")
}

/** Passes priority until the Stack is empty or a decision other than ordering triggers is open; triggers put on the Stack at once are
 * ordered as listed (CR 603.3b). */
internal fun Scenario.resolveAll() {
    var guard = 60
    while (guard-- > 0) {
        val open = pending
        if (open is Decision.OrderTriggers) { answer(Answer.Order(open.triggers.indices.toList())); continue }
        if (open != null || state.stack.isEmpty()) return
        allPass()
    }
}

/** Answers the target decisions one slot after another with the named objects or players (resolving the Stack until one is open), then
 * resolves on. */
internal fun Scenario.targets(vararg names: String) {
    if (pending !is Decision.Targets) resolveAll()
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

/** Answers an open yes/no decision with yes and pays its cost as the engine suggests, then resolves on. */
internal fun Scenario.payYes() {
    answer(Answer.YesNo(true))
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    resolveAll()
}

/** Chooses objects for the open decision by name, then resolves on. */
internal fun Scenario.choose(vararg names: String) {
    val options = (pending as? Decision.ChooseObjects)?.options ?: return resolveAll()
    val taken = mutableListOf<arcanum.engine.state.ObjectId>()
    names.forEach { taken += preferred(it, listOf { o -> o.id in options }, taken) }
    answer(Answer.Objects(taken)); resolveAll()
}

/** Answers the open option choice with the option starting with [text], then resolves on. */
internal fun Scenario.option(text: String) {
    val d = pending as? Decision.ChooseOption ?: error("No option choice open: $pending")
    answer(Answer.Option(d.options.firstOrNull { it.startsWith(text) } ?: error("No option \"$text\" among ${d.options}")))
    resolveAll()
}

/** Alice's and Bob's libraries of plain cards to draw from. */
internal fun Scenario.libraries(cards: Int = 6) {
    alice.library(*Array(cards) { DftPlain.notes })
    bob.library(*Array(cards) { DftPlain.notes })
}

/** Alice attacks with [attackers] (attack triggers resolved, targets answered with [picks]) and Bob doesn't block; combat damage is dealt. */
internal fun Scenario.hits(vararg attackers: String, picks: List<String> = emptyList()) {
    advanceTo(TurnStep.DeclareAttackers)
    alice.attacks(*attackers)
    if (picks.isNotEmpty()) targets(*picks.toTypedArray()) else resolveAll()
    advanceTo(TurnStep.DeclareBlockers)
    if (state.priority.holder == null) bob.blocks()
    advanceTo(TurnStep.CombatDamage)
    resolveAll()
}

/** Alice's speed (CR 702.179). */
internal fun Scenario.speedOf(player: arcanum.engine.scenario.ScenarioPlayer): Int = arcanum.engine.turn.Speed.of(state, player.id)

/** [player]'s speed becomes [speed] (CR 702.179). */
internal fun Scenario.setSpeed(player: arcanum.engine.scenario.ScenarioPlayer, speed: Int) =
    change(arcanum.engine.change.Change.Ledger(player.id, arcanum.engine.turn.Speed.KEY, speedOf(player), speed))

/** [player] activates the [ability]th activated ability of the [index]th card named [name] (from 0, in setup order): for two cards of one
 * name in different zones. */
internal fun Scenario.activateCopy(player: arcanum.engine.scenario.ScenarioPlayer, name: String, index: Int, ability: Int) {
    val id = card(name, index)
    val origin = view()[id]!!.characteristics!!.abilities.filter { it.ability is arcanum.api.Ability.Activated }[ability].origin
    perform(arcanum.engine.input.Input.Activate(player.id, id, origin))
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
}

/** Answers an optional cost ("you may sacrifice an artifact"): yes, paying it with the named objects, then resolves on. */
internal fun Scenario.payYesOrChoose(vararg names: String) {
    if (pending is Decision.May) answer(Answer.YesNo(true))
    if (pending is Decision.ChooseObjects) {
        val options = (pending as Decision.ChooseObjects).options
        val taken = mutableListOf<arcanum.engine.state.ObjectId>()
        names.forEach { taken += preferred(it, listOf { o -> o.id in options }, taken) }
        answer(Answer.Objects(taken))
    }
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    resolveAll()
}
