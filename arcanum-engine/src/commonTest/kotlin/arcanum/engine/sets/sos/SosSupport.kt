package arcanum.engine.sets.sos

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.engine.input.CastWay
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain supporting cards and moves shared by the Secrets of Strixhaven card scenarios (the backfill of 2026-11-03) (invented, D15). */
internal object Sos {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Quad Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Quad Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Quad Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val human = Cards.invent("Quad Clerk", "{W}", "Creature — Human", "", "1/1")
    val flyer = Cards.invent("Quad Owl", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val demon = Cards.invent("Quad Demon", "{3}{B}", "Creature — Demon", "", "4/4")
    val charm = Cards.invent("Quad Charm", "{W}", "Enchantment", "")
    val curse = Cards.invent("Quad Curse", "{B}", "Enchantment", "")
    val idol = Cards.invent("Quad Idol", "{2}", "Artifact", "")
    val shock = Cards.invent("Quad Spark", "{R}", "Instant", "Quad Spark deals 2 damage to any target.")
    val ponder = Cards.invent("Quad Study", "{U}", "Sorcery", "Draw a card.")
    val heal = Cards.invent("Quad Balm", "{W}", "Instant", "You gain 2 life.")
    val doom = Cards.invent("Quad Doom", "{B}", "Instant", "Destroy target creature.")
    val shatter = Cards.invent("Quad Shatter", "{R}", "Instant", "Destroy target artifact.")
    val pump = Cards.invent("Quad Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    val elf = Cards.invent("Quad Elf", "{G}", "Creature — Elf", "", "1/1")
    val sphinx = Cards.invent("Quad Sphinx", "{4}{U}", "Creature — Sphinx", "Flying", "4/4")
    val walker = Cards.invent("Quad Walker", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")
    val dwarf = Cards.invent("Quad Dwarf", "{1}{R}", "Creature — Dwarf Warrior", "", "2/2")
    val goblin = Cards.invent("Quad Goblin", "{R}", "Creature — Goblin", "", "1/1")
    val sword = Cards.invent("Quad Sword", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
}

/** Answers the open target decision with [names] (players by their name). */
internal fun Scenario.targets(vararg names: String) {
    answer(Answer.Targets(names.map { target(it) }))
}

/** Answers a target decision for [names] when one is open (a single legal target may be chosen without asking). */
internal fun Scenario.targetsIfAsked(vararg names: String) {
    if (pending is Decision.Targets) targets(*names)
}

/** Answers each open target decision in turn with those of [names] it offers (a slot whose only legal target was chosen
 * without asking takes none of them). */
internal fun Scenario.pickTargets(vararg names: String) {
    var guard = 6
    while (guard-- > 0) {
        val d = pending as? Decision.Targets ?: return
        val offered = names.map { target(it) }.filter { it in d.options }.distinct().take(d.max ?: Int.MAX_VALUE)
        answer(Answer.Targets(offered))
    }
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

/** The loyalty counters on the permanent named [name]. */
internal fun Scenario.loyalty(name: String): Int =
    state.obj(card(name, zone = arcanum.api.Zone.Battlefield))!!.counters(CounterKind.Loyalty)

/** Whether the permanent named [name] is prepared (CR 722). */
internal fun Scenario.prepared(name: String): Boolean =
    state.obj(card(name, zone = arcanum.api.Zone.Battlefield))!!.marks.contains(arcanum.api.ObjectMark.Prepared)

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

/** Chooses [name] when the open object choice offers a card of that name (a choice the engine may make alone when
 * only one card qualifies). */
internal fun Scenario.chooseIfOffered(name: String) {
    val d = pending as? Decision.ChooseObjects ?: return
    val id = d.options.firstOrNull { state.obj(it)?.let { o -> nameOf(o) } == name } ?: return
    answer(Answer.Objects(listOf(id)))
}
