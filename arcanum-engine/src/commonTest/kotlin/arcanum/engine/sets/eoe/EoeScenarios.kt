package arcanum.engine.sets.eoe

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.KeywordExpansions
import arcanum.api.Mechanic
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario

/** Plain cards the Edge of Eternities scenarios play with. */
internal object EoeExtras {
    val bear = Cards.invent("Orbit Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Orbit Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Orbit Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val wall = Cards.invent("Orbit Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    val flier = Cards.invent("Orbit Hawk", "{1}{W}", "Creature — Bird", "Flying", "1/1")
    val legend = Cards.invent("Orbit Admiral, Steadfast", "{1}{W}", "Legendary Creature — Human Soldier", "", "2/2")
    val kavu = Cards.invent("Orbit Kavu", "{1}{R}", "Creature — Kavu", "", "2/2")
    val trinket = Cards.invent("Orbit Trinket", "{1}", "Artifact", "")
    val gizmo = Cards.invent("Orbit Gizmo", "{3}", "Artifact", "")
    val charm = Cards.invent("Orbit Charm", "{1}{W}", "Enchantment", "")
    val notes = Cards.invent("Orbit Notes", "{U}", "Sorcery", "Draw a card.")
    val zap = Cards.invent("Orbit Zap", "{R}", "Instant", "Orbit Zap deals 2 damage to any target.")
    val doom = Cards.invent("Orbit Doom", "{B}", "Instant", "Destroy target creature.")
    val shuttle = Cards.invent("Orbit Shuttle", "{1}", "Artifact — Spacecraft", """
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 3+.)
        3+ | Flying
    """.trimIndent(), "2/2")
    val warper = Cards.invent("Orbit Warpling", "{3}{G}", "Creature — Elf", """
        Warp {G} (You may cast this card from your hand for its warp cost. Exile this creature at the beginning of the next end step, then you may cast it from exile on a later turn.)
    """.trimIndent(), "3/3")
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val charge = KeywordExpansions.CHARGE
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

/** Answers the open target decisions one slot after another with the named objects or players, then resolves on. */
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

/** Pays an open mana payment as the engine suggests, then resolves on. */
internal fun Scenario.pay() {
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    resolveAll()
}

/** The way to cast [name] for its warp cost (CR 702.185a): its own warp ability, or the one another permanent gives it. */
internal fun Scenario.warpWay(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
    (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == Keyword.Warp.Kind } == true
}.origin)
