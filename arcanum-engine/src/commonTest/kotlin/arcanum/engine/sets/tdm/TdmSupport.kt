package arcanum.engine.sets.tdm

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario

/** Plain supporting cards shared by the TDM card scenarios (invented, D15). */
internal object Plain {
    val p1p1 = CounterKind.PowerToughness(1, 1)
    val bear = Cards.invent("Steppe Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    val ogre = Cards.invent("Steppe Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    val giant = Cards.invent("Steppe Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    val elf = Cards.invent("Steppe Elf", "{G}", "Creature — Elf", "", "1/1")
    val flyer = Cards.invent("Steppe Hawk", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val dragon = Cards.invent("Steppe Wyrm", "{3}{R}", "Creature — Dragon", "Flying", "4/4")
    val whiteDragon = Cards.invent("Steppe Sky Wyrm", "{3}{W}", "Creature — Dragon", "Flying", "3/3")
    val turtle = Cards.invent("Steppe Turtle", "{1}{U}", "Creature — Turtle", "", "1/4")
    val idol = Cards.invent("Steppe Idol", "{2}", "Artifact", "")
    val golem = Cards.invent("Steppe Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    val charm = Cards.invent("Steppe Charm", "{W}", "Enchantment", "")
    val shock = Cards.invent("Steppe Spark", "{R}", "Instant", "Steppe Spark deals 2 damage to any target.")
    val study = Cards.invent("Steppe Study", "{U}", "Sorcery", "Draw a card.")
    val heal = Cards.invent("Steppe Balm", "{W}", "Instant", "You gain 2 life.")
    val doom = Cards.invent("Steppe Doom", "{B}", "Instant", "Destroy target creature.")
    val growth = Cards.invent("Steppe Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    val pump = Cards.invent("Steppe Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    val bolt = Cards.invent("Steppe Bolt", "{R}", "Sorcery", "Steppe Bolt deals 1 damage to any target.")
    val wall = Cards.invent("Steppe Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    val walker = Cards.invent("Steppe Walker", "{2}{W}", "Legendary Planeswalker — Ajani", "[+1]: You gain 1 life.", loyalty = "3")
    val counterspell = Cards.invent("Steppe Denial", "{U}{U}", "Instant", "Counter target spell.")
    val pinger = Cards.invent("Steppe Pinger", "{R}", "Creature — Human", "{T}: This creature deals 1 damage to any target.", "1/1")
    val looter = Cards.invent("Steppe Looter", "{U}", "Creature — Human", "{T}: Draw a card.", "1/1")
}

/** Answers each open target decision in turn with those of [names] it offers. */
internal fun Scenario.pickTargets(vararg names: String) {
    var guard = 6
    while (guard-- > 0) {
        val d = pending as? Decision.Targets ?: return
        val offered = names.map { target(it) }.filter { it in d.options }.distinct().take(d.max ?: Int.MAX_VALUE)
        answer(Answer.Targets(offered))
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
internal fun Scenario.alternativeOf(name: String, kind: arcanum.api.KeywordKind): CastWay =
    CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == kind } == true
    }.origin)

/** The index among the activated abilities of the permanent named [name] of the one whose text contains [text]. */
internal fun Scenario.abilityIndex(name: String, text: String): Int =
    view()[card(name, zone = arcanum.api.Zone.Battlefield)]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        .indexOfFirst { (it.ability as Ability.Activated).text?.contains(text) == true }
        .also { check(it >= 0) { "$name has no activated ability with \"$text\"" } }

/** Alice's [saga] with [n] - 1 lore counters at her draw step of turn 3 ([library] on top): passing into the main phase adds a lore
 * counter and chapter [n] triggers (CR 714.3b). */
internal fun Scenario.loreChapter(saga: String, n: Int, vararg library: String) {
    alice.battlefield(saga, counters = listOf(CounterKind.Lore to n - 1))
    alice.library(*library, Plain.ogre, Plain.ogre)
    bob.library(Plain.ogre, Plain.ogre)
    turn(number = 3, step = TurnStep.Draw)
    allPass()
}

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

/** The Omen face of [name] (CR 720.3). */
internal val omen = CastWay(face = 1)
