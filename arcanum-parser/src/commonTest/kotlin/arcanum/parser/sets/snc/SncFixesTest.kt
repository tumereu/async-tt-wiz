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

    // CR 118.9 (an alternative cost), 106.3 (the source of mana): only mana produced by Treasures may pay it
    @Test
    fun securityRhox() = card(
        name = "Vault Rhino",
        cost = "{2}{R}{G}",
        type = "Creature — Rhino Warrior",
        pt = "5/4",
        text = "You may pay {R}{G} rather than pay this spell's mana cost. Spend only mana produced by Treasures to cast it this way.",
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = manaCost("{R}{G}"), manaFrom = sub("Treasure"))),
    )

    private val collection = CounterKind.Named("collection")

    // CR 122.1 (the counter put on each exiled card in exile), 113.8 (the controller of the ability that exiled it), 609.4b (mana as though of any
    // color), 601.2 / 305.1 (play: lands too); once each turn, any turn
    @Test
    fun evelynTheCovetous() = card(
        name = "Avelyne, the Grasping",
        cost = "{2}{U/B}{B}{B/R}",
        type = "Legendary Creature — Vampire Rogue",
        pt = "2/5",
        text = """
            Flash
            Whenever Avelyne or another Vampire you control enters, exile the top card of each player's library with a collection counter on it.
            Once each turn, you may play a card from exile with a collection counter on it if it was exiled by an ability you controlled, and you may spend mana as though it were mana of any color to cast it.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Vampire"), you))),
            doing(Effect.Move(Objects.LibraryCards(Players.Each(PlayerFilter.Any), n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.PutCounters(Objects.Bound("exiled"), collection, n(1)))),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, perTurn = 1, holder = PlayerFilter.You, manaAsAnyColor = true),
            affects = Objects.Each(and(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Exile), ObjectFilter.HasCounter(collection, 1),
                ObjectFilter.ExiledBy(PlayerFilter.You)))),
    )
}
