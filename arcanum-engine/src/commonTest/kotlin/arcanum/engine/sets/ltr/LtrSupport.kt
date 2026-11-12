package arcanum.engine.sets.ltr

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer

/** Plain supporting cards shared by the Lord of the Rings card scenarios (invented, D15). */
internal object LtrPlain {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Shirefolk Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Shirefolk Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Shirefolk Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val elf = Cards.invent("Shirefolk Elf", "{G}", "Creature — Elf", "", "1/1")
    val hawk = Cards.invent("Shirefolk Hawk", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val knight = Cards.invent("Shirefolk Knight", "{1}{B}", "Creature — Human Knight", "", "2/2")
    val merfolk = Cards.invent("Shirefolk Merfolk", "{U}", "Creature — Merfolk", "", "1/1")
    val squirrel = Cards.invent("Shirefolk Squirrel", "{G}", "Creature — Squirrel", "", "1/1")
    val wizard = Cards.invent("Shirefolk Wizard", "{1}{U}", "Creature — Human Wizard", "", "1/2")
    val trinket = Cards.invent("Shirefolk Trinket", "{1}", "Artifact", "")
    val zeroTrinket = Cards.invent("Shirefolk Bauble", "{0}", "Artifact", "")
    val golem = Cards.invent("Shirefolk Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    val charm = Cards.invent("Shirefolk Charm", "{1}{W}", "Enchantment", "")
    val notes = Cards.invent("Shirefolk Notes", "{U}", "Sorcery", "Draw a card.")
    val zap = Cards.invent("Shirefolk Zap", "{R}", "Instant", "Shirefolk Zap deals 2 damage to any target.")
    val ping = Cards.invent("Shirefolk Ping", "{R}", "Instant", "Shirefolk Ping deals 1 damage to target creature.")
    val doom = Cards.invent("Shirefolk Doom", "{B}", "Instant", "Destroy target creature.")
    val growth = Cards.invent("Shirefolk Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    val heal = Cards.invent("Shirefolk Balm", "{W}", "Instant", "You gain 2 life.")
    val counterspell = Cards.invent("Shirefolk Denial", "{U}{U}", "Instant", "Counter target spell.")
    val walker = Cards.invent("Shirefolk Walker", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")
    val wastes = Cards.invent("Shirefolk Wastes", null, "Land", "{T}: Add {C}.")
    val orc = Cards.invent("Shirefolk Orc", "{1}{B}", "Creature — Orc Soldier", "", "2/2")
    val goblin = Cards.invent("Shirefolk Goblin", "{R}", "Creature — Goblin", "", "1/1")
    val human = Cards.invent("Shirefolk Guard", "{W}", "Creature — Human Soldier", "", "1/1")
    val legend = Cards.invent("Shirefolk Elder", "{1}{W}", "Legendary Creature — Human Noble", "", "2/2")
    val bigLegend = Cards.invent("Shirefolk Champion", "{3}{G}", "Legendary Creature — Human Warrior", "", "4/4")
    val sword = Cards.invent("Shirefolk Sword", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    val picnic = Cards.invent("Shirefolk Picnic", "{G}", "Sorcery", "Create a Food token.")
    val beckon = Cards.invent("Shirefolk Beckoning", "{B}", "Sorcery", "The Ring tempts you.")
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
            // without priority (a step begun by the harness): the Stack resolves on its own
            null -> if (state.stack.isEmpty()) return else if (state.priority.holder == null) resolve() else allPass()
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
                // an ordering (all of them, as offered): the scry's or a library's order
                if (chosen.size < d.min && d.min == d.options.size) { answer(Answer.Objects(d.options)); continue }
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
    alice.library(*library, LtrPlain.ogre, LtrPlain.ogre)
    bob.library(LtrPlain.ogre, LtrPlain.ogre)
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

/** The name of [player]'s Ring-bearer, or null (CR 701.54a). */
internal fun Scenario.ringBearer(player: ScenarioPlayer = alice): String? = state.battlefield.firstOrNull { id ->
    state.objOrThrow(id).let { o -> arcanum.api.ObjectMark.RingBearer in o.marks && view()[id]?.controller == player.id }
}?.let { nameOf(state.objOrThrow(it)) }

/** How many times the Ring has tempted [player] (CR 701.54c). */
internal fun Scenario.temptations(player: ScenarioPlayer = alice): Int = arcanum.engine.ring.TheRing.temptations(state, player.id)

/** The tokens named [name] on the battlefield. */
internal fun Scenario.tokens(name: String): List<arcanum.engine.state.ObjectId> =
    state.battlefield.filter { id -> state.obj(id)?.let { it.token && nameOf(it) == name } == true }
