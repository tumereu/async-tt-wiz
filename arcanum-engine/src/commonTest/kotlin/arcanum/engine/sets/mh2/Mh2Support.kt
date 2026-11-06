package arcanum.engine.sets.mh2

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario

/** Plain supporting cards shared by the Modern Horizons 2 card scenarios (invented, D15). */
internal object Mh2Plain {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Horizon Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Horizon Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Horizon Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val elf = Cards.invent("Horizon Elf", "{G}", "Creature — Elf", "", "1/1")
    val hawk = Cards.invent("Horizon Hawk", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val knight = Cards.invent("Horizon Knight", "{1}{B}", "Creature — Human Knight", "", "2/2")
    val merfolk = Cards.invent("Horizon Merfolk", "{U}", "Creature — Merfolk", "", "1/1")
    val squirrel = Cards.invent("Horizon Squirrel", "{G}", "Creature — Squirrel", "", "1/1")
    val wizard = Cards.invent("Horizon Wizard", "{1}{U}", "Creature — Human Wizard", "", "1/2")
    val trinket = Cards.invent("Horizon Trinket", "{1}", "Artifact", "")
    val zeroTrinket = Cards.invent("Horizon Bauble", "{0}", "Artifact", "")
    val golem = Cards.invent("Horizon Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    val charm = Cards.invent("Horizon Charm", "{1}{W}", "Enchantment", "")
    val notes = Cards.invent("Horizon Notes", "{U}", "Sorcery", "Draw a card.")
    val zap = Cards.invent("Horizon Zap", "{R}", "Instant", "Horizon Zap deals 2 damage to any target.")
    val ping = Cards.invent("Horizon Ping", "{R}", "Instant", "Horizon Ping deals 1 damage to target creature.")
    val doom = Cards.invent("Horizon Doom", "{B}", "Instant", "Destroy target creature.")
    val growth = Cards.invent("Horizon Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    val heal = Cards.invent("Horizon Balm", "{W}", "Instant", "You gain 2 life.")
    val counterspell = Cards.invent("Horizon Denial", "{U}{U}", "Instant", "Counter target spell.")
    val walker = Cards.invent("Horizon Walker", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")
    val wastes = Cards.invent("Horizon Wastes", null, "Land", "{T}: Add {C}.")
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

/**
 * Resolves the Stack, answering what comes up on the way: targets and object choices with those of [picks] offered (by
 * name, a name given twice meaning two cards), "you may" with [may], options starting with one of [options], trigger order as
 * offered (CR 603.3b). Stops when the Stack is empty with nothing open, or at a decision it can't answer.
 */
internal fun Scenario.playOut(vararg picks: String, may: Boolean = true, options: List<String> = emptyList()) {
    var guard = 80
    while (guard-- > 0) {
        when (val d = pending) {
            null -> if (state.stack.isEmpty()) return else allPass()
            is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.map { it.index }))
            is Decision.May -> answer(Answer.YesNo(may && d.payable))
            is Decision.ChooseOption -> {
                val o = d.options.firstOrNull { opt -> options.any { opt.startsWith(it) } } ?: return
                answer(Answer.Option(o))
            }
            is Decision.Targets -> {
                val offered = picks.map { target(it) }.filter { it in d.options }.distinct().take(d.max ?: Int.MAX_VALUE)
                if (offered.size < d.min) return
                answer(Answer.Targets(offered))
            }
            is Decision.ChooseObjects -> {
                val chosen = picks.fold(emptyList<arcanum.engine.state.ObjectId>()) { taken, name ->
                    val id = d.options.firstOrNull { it !in taken && state.obj(it)?.let { o -> nameOf(o) } == name }
                    if (id == null) taken else taken + id
                }.take(d.max ?: Int.MAX_VALUE)
                if (chosen.size < d.min) return
                answer(Answer.Objects(chosen))
            }
            is Decision.ManaPayment -> if (d.suggestion.isEmpty()) return else answer(Answer.ManaPayment(d.suggestion))
            is Decision.Divide -> answer(Answer.Division(d.suggested.takeIf { it.sumOf { s -> s.amount } == d.amount } ?: d.among.mapIndexed { i, t ->
                arcanum.engine.decision.Share(t, d.amount / d.among.size + if (i < d.amount % d.among.size) 1 else 0) }))
            else -> return
        }
    }
}

/** Answers the open modal choice with [modes] (from 0). */
internal fun Scenario.modes(vararg modes: Int) {
    decision<Decision.Modes>()
    answer(Answer.Modes(modes.toList()))
}

/** Answers the open option choice with the option starting with [text]. */
internal fun Scenario.option(text: String) {
    val d = decision<Decision.ChooseOption>()
    answer(Answer.Option(d.options.firstOrNull { it.startsWith(text) } ?: error("No option \"$text\" among ${d.options}")))
}

/** The alternative cost of [name] given by the keyword [kind], its own or one another object gives it. */
internal fun Scenario.alternativeOf(name: String, kind: arcanum.api.KeywordKind?): CastWay =
    CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == kind } == true
    }.origin)

/** The index among the activated abilities of the permanent named [name] of the one whose text contains [text]. */
internal fun Scenario.abilityIndex(name: String, text: String, zone: arcanum.api.Zone = arcanum.api.Zone.Battlefield): Int =
    view()[card(name, zone = zone)]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        .indexOfFirst { (it.ability as Ability.Activated).text?.contains(text) == true }
        .also { check(it >= 0) { "$name has no activated ability with \"$text\"" } }

/** Alice attacks with [attackers] (attack triggers resolved with [picks] and [options], [playOut]) and Bob doesn't block: the
 * declare blockers step, Alice holding priority. */
internal fun Scenario.unblocked(vararg attackers: String, picks: List<String> = emptyList(), options: List<String> = emptyList()) {
    advanceTo(TurnStep.DeclareAttackers)
    alice.attacks(*attackers)
    playOut(*picks.toTypedArray(), options = options)
    advanceTo(TurnStep.DeclareBlockers)
    if (state.priority.holder == null) bob.blocks()
}

/** Alice attacks with [attackers], Bob doesn't block, and combat damage is dealt: attack and damage triggers resolved with [picks]. */
internal fun Scenario.hits(vararg attackers: String, picks: List<String> = emptyList(), options: List<String> = emptyList()) {
    unblocked(*attackers, picks = picks, options = options)
    advanceTo(TurnStep.CombatDamage)
    playOut(*picks.toTypedArray(), options = options)
}

/** The way to cast [name] by a permission (a [Mechanic.MayPlay] of another object, or its own), as the engine offers it to [player]. */
internal fun Scenario.permitted(name: String, player: arcanum.engine.scenario.ScenarioPlayer = alice): CastWay =
    arcanum.engine.Engine.options(state, player.id).filterIsInstance<arcanum.engine.input.Input.Cast>()
        .first { it.card == card(name) && it.way.permission != null }.way

/** Alice's upkeep of turn 3 with her library topped by [library]: "at the beginning of your upkeep" triggers are on the Stack or ordered. */
internal fun Scenario.aliceUpkeep(vararg library: String) {
    alice.library(*library, Mh2Plain.ogre, Mh2Plain.ogre)
    bob.library(Mh2Plain.ogre, Mh2Plain.ogre)
    turn(number = 3, step = TurnStep.Upkeep)
    beginStep(TurnStep.Upkeep)
}

/** [player] suspends [name] from their hand, paying its suspend cost with the engine's suggestion (CR 702.62a, a special action). */
internal fun Scenario.suspends(name: String, player: arcanum.engine.scenario.ScenarioPlayer = alice) {
    val suspend = view()[card(name)]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }
    perform(arcanum.engine.input.Input.Activate(player.id, card(name), suspend.origin))
    if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
}

/** The index among [name]'s activated abilities, as the view lists them where the card is ([zone]), of the one that is a keyword's. */
internal fun Scenario.keywordAbility(name: String, zone: arcanum.api.Zone): Int =
    view()[card(name, zone = zone)]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        .indexOfFirst { it.origin.toString().contains("Keyword") }.also { check(it >= 0) { "$name has no keyword ability in $zone" } }
