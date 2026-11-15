package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK sweep: siblings of the parser fixes found while working the set. */
class TrkSweepTest {
    private fun searchToHand(filter: ObjectFilter) = abilities(spell(
        search(filter, reveal = true),
        move(Objects.Bound("found"), Zone.Hand),
        shuffle,
    ))

    private fun searchCard(name: String, wanted: String) = card(
        name = name,
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Search your library for a $wanted, reveal it, put it into your hand, then shuffle.",
    )

    // "a land card or creature card with …": a repeated "card" starts an alternative of its own, so the "with" describes the last one only,
    // as for mana value (trk chunk 05); here power or greater
    @Test
    fun repeatedCardWithPowerOrGreater() = searchCard("Trk Sweep Wild Summons", "land card or creature card with power 4 or greater") parsesTo
        searchToHand(or(land, and(creature, power(Comparison.AtLeast, 4))))

    // the same for total power and toughness
    @Test
    fun repeatedCardWithTotalPowerAndToughness() = searchCard("Trk Sweep Modest Summons", "land card or creature card with total power and toughness 5 or less") parsesTo
        searchToHand(or(land, and(creature, ObjectFilter.Where(Condition.Compare(
            Value.Sum(listOf(power(Objects.Tested), toughness(Objects.Tested))), Comparison.AtMost, n(5))))))

    // the same for {X} in its mana cost (CR 107.3)
    @Test
    fun repeatedCardWithXInItsManaCost() = searchCard("Trk Sweep Open Summons", "land card or instant card with {X} in its mana cost") parsesTo
        searchToHand(or(land, and(instant, ObjectFilter.XInManaCost)))

    // CR 608.2c: "they" is the player named before ("target opponent"), not the attacking player of the trigger event
    @Test
    fun theyLoseHalfTheirLifeIsThePlayerNamedBefore() = card(
        name = "Trk Sweep Hard Bargain",
        cost = "{2}{B}",
        type = "Creature — Human Advisor",
        pt = "2/2",
        text = "Whenever you attack, target opponent may have you draw a card. If they don't, they lose half their life, rounded up.",
    ) parsesTo abilities(
        triggered(youAttack,
            Step.Pay(steps = listOf(draw(1)), payer = Players.Target("opponent"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.LoseLife(Players.Target("opponent"),
                Value.Half(Value.LifeOf(Players.Target("opponent")), roundUp = true)))),
            targets = listOf(targetOpponent)),
    )

    // CR 608.2c, 508.1c: the same for "they can't attack you this turn": the creatures of the player named before
    @Test
    fun theyCantAttackYouIsThePlayerNamedBefore() = card(
        name = "Trk Sweep Firm Bargain",
        cost = "{2}{W}",
        type = "Creature — Human Advisor",
        pt = "2/2",
        text = "Whenever you attack, target opponent may have you draw a card. If they don't, they can't attack you this turn.",
    ) parsesTo abilities(
        triggered(youAttack,
            Step.Pay(steps = listOf(draw(1)), payer = Players.Target("opponent"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(Recipients(players = Players.You))),
                Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))), Duration.EndOfTurn))),
            targets = listOf(targetOpponent)),
    )
}
