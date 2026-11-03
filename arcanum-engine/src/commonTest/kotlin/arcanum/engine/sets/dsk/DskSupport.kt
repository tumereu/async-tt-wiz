package arcanum.engine.sets.dsk

import arcanum.api.CounterKind
import arcanum.engine.input.CastWay
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer

/** Plain supporting cards and moves shared by the Duskmourn card scenarios (invented, D15). */
internal object Dsk {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Hallway Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Hallway Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Hallway Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val human = Cards.invent("Lost Camper", "{W}", "Creature — Human", "", "1/1")
    val flyer = Cards.invent("Attic Owl", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val demon = Cards.invent("Cellar Demon", "{3}{B}", "Creature — Demon", "", "4/4")
    /** An enchantment to cast: it makes eerie trigger (CR 207.2c). */
    val charm = Cards.invent("Hallway Charm", "{W}", "Enchantment", "")
    val curse = Cards.invent("Hallway Curse", "{B}", "Enchantment", "")
    val idol = Cards.invent("Hallway Idol", "{2}", "Artifact", "")
    val shock = Cards.invent("Hallway Spark", "{R}", "Instant", "Hallway Spark deals 2 damage to any target.")
    val ponder = Cards.invent("Hallway Study", "{U}", "Sorcery", "Draw a card.")
    val heal = Cards.invent("Hallway Balm", "{W}", "Instant", "You gain 2 life.")
    val pump = Cards.invent("Hallway Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
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

/** Casts the [door]th half (0 left, 1 right) of the Room [room] (CR 709.3, 709.5d). */
internal fun ScenarioPlayer.castsDoor(room: String, door: Int, target: String? = null, paying: List<String>? = null) =
    casts(room, target = target, way = CastWay(face = door), paying = paying)
