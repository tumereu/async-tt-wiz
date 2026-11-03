package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Plain supporting cards and moves shared by the Bloomburrow card scenarios (invented, D15). */
internal object Blb {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Hedgerow Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Hedgerow Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    /** A four-mana spell: casting it spends the fourth mana of the turn (expend 4, CR 700.14). */
    val boar = Cards.invent("Hedgerow Boar", "{3}{G}", "Creature — Boar", "", "4/4")
    val giant = Cards.invent("Hedgerow Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val rabbit = Cards.invent("Clover Rabbit", "{W}", "Creature — Rabbit", "", "1/1")
    val mouse = Cards.invent("Brook Mouse", "{R}", "Creature — Mouse", "", "1/1")
    val rat = Cards.invent("Gutter Rat", "{B}", "Creature — Rat", "", "1/1")
    val bat = Cards.invent("Dusk Bat", "{B}", "Creature — Bat", "Flying", "1/1")
    val bird = Cards.invent("Reed Bird", "{W}", "Creature — Bird", "Flying", "1/1")
    val frog = Cards.invent("Lily Frog", "{G}", "Creature — Frog", "", "1/1")
    val lizard = Cards.invent("Sun Lizard", "{R}", "Creature — Lizard", "", "1/1")
    val otter = Cards.invent("River Otter", "{U}", "Creature — Otter", "", "1/1")
    val raccoon = Cards.invent("Trash Raccoon", "{G}", "Creature — Raccoon", "", "2/2")
    val squirrel = Cards.invent("Acorn Squirrel", "{G}", "Creature — Squirrel", "", "1/1")
    val legend = Cards.invent("Old Badger, the Elder", "{1}{G}", "Legendary Creature — Badger", "", "2/2")
    val flyer = Cards.invent("Hedgerow Hawk", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val shock = Cards.invent("Hedge Spark", "{R}", "Instant", "Hedge Spark deals 2 damage to any target.")
    val pump = Cards.invent("Hedge Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    val ponder = Cards.invent("Hedge Study", "{U}", "Sorcery", "Draw a card.")
    val heal = Cards.invent("Hedge Balm", "{W}", "Instant", "You gain 2 life.")
    val pact = Cards.invent("Hedge Pact", "{B}", "Sorcery", "You lose 2 life.")
    val rabbitCall = Cards.invent("Hedge Warren Call", "{W}", "Sorcery", "Create a 1/1 white Rabbit creature token.")
    val unsummon = Cards.invent("Hedge Gust", "{U}", "Instant", "Return target creature to its owner's hand.")
    val idol = Cards.invent("Hedge Idol", "{2}", "Artifact", "")
    val charm = Cards.invent("Hedge Charm", "{1}{W}", "Enchantment", "")
    val food = Cards.invent("Hedge Pie", "{1}", "Artifact — Food", "{2}, {T}, Sacrifice this artifact: You gain 3 life.")
    val bigSpell = Cards.invent("Hedge Saga", "{3}{U}", "Sorcery", "Draw two cards.")
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
    answer(Answer.Option(d.options.first { it.startsWith(text) }))
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
