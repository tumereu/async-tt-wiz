package arcanum.engine.cube

import arcanum.engine.cards.Cards

/** A vanilla artifact creature spell costing [cost] ("{B}{R}"), invented for paying with the mana cards under test. */
internal fun pipSpell(cost: String): String {
    val name = "Pip Golem " + cost.filter { it.isLetterOrDigit() }
    return Cards.invent(name, cost, "Artifact Creature — Golem", pt = "1/1")
}
