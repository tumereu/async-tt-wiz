package arcanum.engine.sets.spm

import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario

/** Plain supporting cards and moves shared by the SPM card scenarios of the backfill (2026-11-03) (invented, D15). */
internal object Spm {
    val elf = Cards.invent("Rooftop Elf", "{G}", "Creature — Elf", "", "1/1")
    val flyer = Cards.invent("Rooftop Owl", "{1}{W}", "Creature — Bird", "Flying", "2/2")
    val idol = Cards.invent("Rooftop Idol", "{2}", "Artifact", "")
    val charm = Cards.invent("Rooftop Charm", "{W}", "Enchantment", "")
    val shock = Cards.invent("Rooftop Spark", "{R}", "Instant", "Rooftop Spark deals 2 damage to any target.")
    val study = Cards.invent("Rooftop Study", "{U}", "Sorcery", "Draw a card.")
    val doom = Cards.invent("Rooftop Doom", "{B}", "Instant", "Destroy target creature.")
    val sword = Cards.invent("Rooftop Sword", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    val wall = Cards.invent("Rooftop Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    val bigSpell = Cards.invent("Rooftop Upheaval", "{3}{G}", "Sorcery", "You gain 1 life.")
    val hero = Cards.invent("Rooftop Spider-Hero", "{1}{G}", "Creature — Spider Hero", "", "2/2")
    val lure = Cards.invent("Rooftop Lure", "{2}{U}", "Instant", "Counter target spell.")
    val pinger = Cards.invent("Rooftop Pinger", "{R}", "Creature — Human", "Whenever this creature attacks, it deals 1 damage to each opponent.", "1/1")
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
 * name, a name given twice meaning two cards), "you may" with [may], trigger order as offered (CR 603.3b). Stops when the
 * Stack is empty with nothing open, or at a decision it can't answer.
 */
internal fun Scenario.playOut(vararg picks: String, may: Boolean = true) {
    var guard = 80
    while (guard-- > 0) {
        when (val d = pending) {
            null -> if (state.stack.isEmpty()) return else allPass()
            is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.map { it.index }))
            is Decision.May -> answer(Answer.YesNo(may && d.payable))
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

/** Alice's [saga] with [n] - 1 lore counters at her draw step of turn 3 (her [library] on top): passing into the main
 * phase adds a lore counter and chapter [n] triggers (CR 714.3b). */
internal fun Scenario.loreChapter(saga: String, n: Int, vararg library: String) {
    alice.battlefield(saga, counters = listOf(arcanum.api.CounterKind.Lore to n - 1))
    alice.library(*library, spmOgre, spmOgre)
    bob.library(spmOgre, spmOgre)
    turn(number = 3, step = TurnStep.Draw)
    allPass()
}

/** The index among the activated abilities of the permanent named [name] of the one whose text contains [text] (an
 * ability another object gives it comes after its own). */
internal fun Scenario.abilityIndex(name: String, text: String): Int =
    view()[card(name, zone = arcanum.api.Zone.Battlefield)]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        .indexOfFirst { (it.ability as Ability.Activated).text?.contains(text) == true }
        .also { check(it >= 0) { "$name has no activated ability with \"$text\"" } }

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

/** The alternative cost of [name] given by the keyword [kind] (web-slinging, mayhem), its own or one another object gives it. */
internal fun Scenario.alternativeOf(name: String, kind: arcanum.api.KeywordKind): CastWay =
    CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost && it.keyword == kind } == true
    }.origin)

/** The web-slinging cost of [name] (CR 702.188a). */
internal fun Scenario.webSlingingOf(name: String): CastWay = alternativeOf(name, Keyword.WebSlinging.Kind)

/** Alice attacks with [attackers] (attack triggers resolved with [picks], [playOut]) and Bob doesn't block: the declare
 * blockers step, Alice holding priority. */
internal fun Scenario.unblocked(vararg attackers: String, picks: List<String> = emptyList()) {
    advanceTo(TurnStep.DeclareAttackers)
    alice.attacks(*attackers)
    playOut(*picks.toTypedArray())
    advanceTo(TurnStep.DeclareBlockers)
    if (state.priority.holder == null) bob.blocks()
}

/** Alice attacks with [attackers], Bob doesn't block, and combat damage is dealt: the combat damage step, attack and
 * damage triggers resolved with [picks] ([playOut]). */
internal fun Scenario.hits(vararg attackers: String, picks: List<String> = emptyList()) {
    unblocked(*attackers, picks = picks)
    advanceTo(TurnStep.CombatDamage)
    playOut(*picks.toTypedArray())
}
