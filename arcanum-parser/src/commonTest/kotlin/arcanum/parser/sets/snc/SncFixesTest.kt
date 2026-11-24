package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC fixes: casualty X with the copy's exceptions; untapping as a cost; targets controlled by different players, exiled until it leaves. */
class SncFixesTest {
    // CR 702.153a, 107.3 (X is the power of the creature sacrificed), 707.9b (the copy isn't legendary), 306.5b (starting loyalty)
    @Test
    fun obNixilisTheAdversary() = card(
        name = "Ob Vexilis, the Contrarian",
        cost = "{1}{B}{R}",
        type = "Legendary Planeswalker — Nixilis",
        loyalty = "3",
        text = """
            Casualty X. The copy isn't legendary and has starting loyalty X. (As you cast this spell, you may sacrifice a creature with power X. When you do, copy this spell. The copy becomes a token.)
            [+1]: You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.CasualtyX(listOf(
            CopyException.Types(removeSupertypes = listOf(Supertype.Legendary)),
            CopyException.Loyalty(Value.Of(NumericProperty.Power, Objects.Bound(KeywordExpansions.CASUALTY_SACRIFICED), Combine.Single)))),
        activated(cost(CostPart.Loyalty(n(1))), Step.Do(Effect.GainLife(Players.You, n(2)))),
    )

    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)
    private fun untap(n: Int) = CostPart.UntapObjects(CostObjects.Choose(creature, count = n(n)))

    // CR 118.3, 701.26a (untapping as a cost untaps tapped permanents you control), 104.2a (you win the game)
    @Test
    fun haloFountain() = card(
        name = "Aureole Wellspring",
        cost = "{2}{W}",
        type = "Artifact",
        text = """
            {W}, {T}, Untap a tapped creature you control: Create a 1/1 green and white Citizen creature token.
            {W}{W}, {T}, Untap two tapped creatures you control: Draw a card.
            {W}{W}{W}{W}{W}, {T}, Untap fifteen tapped creatures you control: You win the game.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{W}"), tap, untap(1)), Step.Do(Effect.CreateToken(citizen, n(1), Players.You))),
        activated(cost(mana("{W}{W}"), tap, untap(2)), draw(1)),
        activated(cost(mana("{W}{W}{W}{W}{W}"), tap, untap(15)), doing(Effect.WinGame(Players.You))),
    )

    // CR 601.2c (targets controlled by different players), 610.3 (exiled until it leaves), 603.12 (a reflexive trigger of the return: once for
    // each card that enters under your control)
    @Test
    fun lagrellaTheMagpie() = card(
        name = "Lavinia, the Pilferer",
        cost = "{G}{W}{U}",
        type = "Legendary Creature — Human Soldier",
        pt = "2/3",
        text = "When Lavinia enters, exile any number of other target creatures controlled by different players until Lavinia leaves the battlefield. When an exiled card enters under your control this way, put two +1/+1 counters on it.",
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self, whenReturned = Instructions(steps = listOf(
                Step.ForEach(Each.Objects(Objects.Each(and(ObjectFilter.Is(Objects.Bound("returned")), you))), bind = "it", steps = listOf(
                    doing(Effect.ReflexiveTrigger(Instructions(steps = listOf(counters(Objects.Bound("it"), 2))))))))))),
            targets = listOf(slot("creature", and(another, creature), min = 0, max = null).copy(distinct = SharedQuality.Controller))),
    )
}
