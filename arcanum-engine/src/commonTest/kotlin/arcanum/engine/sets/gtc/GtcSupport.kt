package arcanum.engine.sets.gtc

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.engine.input.CastWay
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain supporting cards and moves shared by the Gatecrash card scenarios (invented, D15). */
internal object Gtc {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Ninth District Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Ninth District Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Ninth District Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val human = Cards.invent("District Clerk", "{W}", "Creature — Human", "", "1/1")
    val flyer = Cards.invent("Belfry Owl", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val demon = Cards.invent("Sewer Demon", "{3}{B}", "Creature — Demon", "", "4/4")
    val charm = Cards.invent("Ninth District Charm", "{W}", "Enchantment", "")
    val curse = Cards.invent("Ninth District Curse", "{B}", "Enchantment", "")
    val idol = Cards.invent("Ninth District Idol", "{2}", "Artifact", "")
    val shock = Cards.invent("Ninth District Spark", "{R}", "Instant", "Ninth District Spark deals 2 damage to any target.")
    val ponder = Cards.invent("Ninth District Study", "{U}", "Sorcery", "Draw a card.")
    val heal = Cards.invent("Ninth District Balm", "{W}", "Instant", "You gain 2 life.")
    val doom = Cards.invent("Ninth District Doom", "{B}", "Instant", "Destroy target creature.")
    val shatter = Cards.invent("Ninth District Shatter", "{R}", "Instant", "Destroy target artifact.")
    val pump = Cards.invent("Ninth District Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
}

/** Answers the open target decision with [names] (players by their name). */
internal fun Scenario.targets(vararg names: String) {
    answer(Answer.Targets(names.map { target(it) }))
}

/** Answers a target decision for [names] when one is open (a single legal target may be chosen without asking). */
internal fun Scenario.targetsIfAsked(vararg names: String) {
    if (pending is Decision.Targets) targets(*names)
}

/** Answers the open modal choice with [modes] (from 0). */
internal fun Scenario.modes(vararg modes: Int) {
    decision<Decision.Modes>()
    answer(Answer.Modes(modes.toList()))
}

/** Chooses nothing in an open object choice (scry or surveil keeping everything on top). */
internal fun Scenario.chooseNone() {
    if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
}

/** Answers the open "you may" with yes or no. */
internal fun Scenario.may(yes: Boolean) {
    decision<Decision.May>()
    answer(Answer.YesNo(yes))
}

/** Pays an open mana payment with the engine's suggestion. */
internal fun Scenario.payIfAsked() {
    (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
}

/** Answers the open option choice with the option starting with [text]. */
internal fun Scenario.option(text: String) {
    val d = decision<Decision.ChooseOption>()
    answer(Answer.Option(d.options.firstOrNull { it.startsWith(text) } ?: error("No option \"$text\" among ${d.options}")))
}

/** Passes priority until the Stack is empty or a decision is open; triggers that trigger together are put on the Stack
 * in the order offered (CR 603.3b). */
internal fun Scenario.resolveAll() {
    var guard = 60
    while (guard-- > 0) {
        (pending as? Decision.OrderTriggers)?.let { d -> answer(Answer.Order(d.triggers.map { it.index })) }
        if (state.stack.isEmpty() || pending != null) break
        allPass()
    }
}

/** The cast of [name] for its own printed alternative cost (CR 118.9). */
internal fun Scenario.ownAlternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
    (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
}.origin)
