package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 01: blitz (CR 702.152a), also with an additional cost, and the creatures that carry it. */
class SncChunk01BlitzTest {
    private fun blitz(cost: String): Keyword = Keyword.Blitz(manaCost(cost))
    private fun steps(vararg steps: Step) = steps.toList()

    // CR 702.152a (blitz), 701.6 / 603.1 (a drawing trigger on death)
    @Test
    fun caldaiaStrongarm() = card(
        name = "Caldera Bruiser",
        cost = "{4}{G}",
        type = "Creature — Human Warrior",
        pt = "2/3",
        text = """
            When this creature enters, put two +1/+1 counters on target creature.
            Blitz {3}{G} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature"), 2), targets = listOf(slot("creature", creature))),
        blitz("{3}{G}"),
    )

    // CR 702.152a, 111.1 (a token from the effect), 110.5b (the token enters tapped)
    @Test
    fun girderGoons() = card(
        name = "Rampart Goons",
        cost = "{4}{B}",
        type = "Creature — Ogre Warrior",
        pt = "4/4",
        text = """
            When this creature dies, create a tapped 2/2 black Rogue creature token.
            Blitz {3}{B} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, Step.Do(Effect.CreateToken(creatureToken("Rogue", listOf(Color.Black), 2, 2), n(1), Players.You, tapped = true))),
        blitz("{3}{B}"),
    )

    // CR 702.152a, 611.2a (until end of turn)
    @Test
    fun mayhemPatrol() = card(
        name = "Gutter Patrol",
        cost = "{1}{R}",
        type = "Creature — Devil Warrior",
        pt = "1/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever this creature attacks, target creature gets +1/+0 until end of turn.
            Blitz {1}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, pump(target("creature"), 1, 0), targets = listOf(slot("creature", creature))),
        blitz("{1}{R}"),
    )

    // CR 702.152a, 613.4c
    @Test
    fun nightClubber() = card(
        name = "Gloom Clubber",
        cost = "{1}{B}{B}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = """
            When this creature enters, creatures your opponents control get -1/-1 until end of turn.
            Blitz {2}{B} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(creatureOpponentControls), -1, -1)),
        blitz("{2}{B}"),
    )

    // CR 702.152a, 509.1b (a creature that can't block)
    @Test
    fun plasmaJockey() = card(
        name = "Spark Jockey",
        cost = "{3}{R}",
        type = "Creature — Lizard Warrior",
        pt = "3/1",
        text = """
            Whenever this creature attacks, target creature an opponent controls can't block this turn.
            Blitz {2}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureOpponentControls))),
        blitz("{2}{R}"),
    )

    // CR 702.152a, 508.4 (a token put onto the battlefield tapped and attacking), 120.3
    @Test
    fun pugnaciousPugilist() = card(
        name = "Brawling Pugilist",
        cost = "{3}{R}{R}",
        type = "Creature — Ogre Warrior",
        pt = "4/4",
        text = """
            Whenever this creature attacks, create a tapped and attacking 1/1 red Devil creature token with "When this token dies, it deals 1 damage to any target."
            Blitz {3}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Do(Effect.CreateToken(
            creatureToken("Devil", listOf(Color.Red), 1, 1, listOf(triggered(diesSelf, damage(1, toAny), targets = listOf(anyTarget)))),
            n(1), Players.You, tapped = true, attacking = AttackTarget.Chosen))),
        blitz("{3}{R}"),
    )

    // CR 702.152a, 509.1c (must be blocked if able)
    @Test
    fun riveteersDecoy() = card(
        name = "Gutter Decoy",
        cost = "{1}{G}",
        type = "Creature — Human Warrior",
        pt = "3/1",
        text = """
            This creature must be blocked if able.
            Blitz {3}{G} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        static(Mechanic.MustBeBlocked),
        blitz("{3}{G}"),
    )

    // CR 702.152a, 111.10a (Treasure)
    @Test
    fun riveteersRequisitioner() = card(
        name = "Gutter Requisitioner",
        cost = "{1}{R}",
        type = "Creature — Lizard Rogue",
        pt = "3/1",
        text = """
            When this creature dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Blitz {2}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        triggered(diesSelf, createTreasure()),
        blitz("{2}{R}"),
    )

    // CR 702.152a, 119.1, 111.1
    @Test
    fun workshopWarchief() = card(
        name = "Foundry Warchief",
        cost = "{3}{G}{G}",
        type = "Creature — Rhino Warrior",
        pt = "5/3",
        text = """
            Trample
            When this creature enters, you gain 3 life.
            When this creature dies, create a 4/4 green Rhino Warrior creature token.
            Blitz {4}{G}{G} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, gainLife(3)),
        triggered(diesSelf, create(creatureToken("Rhino Warrior", listOf(Color.Green), 4, 4), n(1))),
        blitz("{4}{G}{G}"),
    )

    // CR 702.152a with an additional cost in the blitz cost (601.2b, 118.8), 702.152a: cast from the graveyard using blitz (CR 601.2, 604.2)
    @Test
    fun tenaciousUnderdog() = card(
        name = "Stubborn Underhound",
        cost = "{1}{B}",
        type = "Creature — Human Warrior",
        pt = "3/2",
        text = """
            Blitz—{2}{B}{B}, Pay 2 life. (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
            You may cast this card from your graveyard using its blitz ability.
        """,
    ) parsesTo abilities(
        Keyword.Blitz(cost(mana("{2}{B}{B}"), CostPart.PayLife(amount = n(2)))),
        static(Mechanic.MayPlay(Zone.Graveyard, viaAlternative = Keyword.Blitz.Kind, how = PlayKind.Cast, holder = PlayerFilter.You)),
    )

    // CR 707.2 (a token copy), 603.7 (a delayed trigger), 602.5d (as a sorcery), 702.152a; the token gains haste and the dies trigger
    @Test
    fun jaxisTheTroublemaker() = card(
        name = "Kazra, the Rabblerouser",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Warrior",
        pt = "2/3",
        text = """
            {R}, {T}, Discard a card: Create a token that's a copy of another target creature you control. It gains haste and "When this token dies, draw a card." Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
            Blitz {1}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        """,
    ) parsesTo abilities(
        activated(cost(mana("{R}"), tap, discardACard),
            doing(Effect.CreateToken(TokenSpec.CopyOf(target("creature")), n(1), Players.You, bind = "token")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste, triggered(diesSelf, draw(1))))),
                Affected.Objects(Objects.Bound("token")), Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any),
                Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("token"), by = Players.You)))))),
            targets = listOf(slot("creature", and(another, creatureYouControl))), rules = asSorcery),
        blitz("{1}{R}"),
    )

    // CR 510.2 (the damage dealt), 305.1 (play a land), 601.2 / 608.2g (cast without paying), 608.2c (if you don't)
    @Test
    fun ziatorasEnvoy() = card(
        name = "Ziator's Messenger",
        cost = "{1}{B}{R}{G}",
        type = "Creature — Lizard Warrior",
        pt = "5/4",
        text = """
            Trample
            Whenever this creature deals combat damage to a player, look at the top card of your library. You may play a land from the top of your library or cast a spell with mana value less than or equal to the damage dealt from the top of your library without paying its mana cost. If you don't, put that card into your hand.
            Blitz {2}{B}{R}{G}
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(combatDamageToAPlayer,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), Players.You, bind = "top")),
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("top"), PlayKind.Play, withoutPaying = true, caster = Players.You,
                resultingSpell = ObjectFilter.ManaValue(Comparison.AtMost, Value.EventAmount)))), payer = Players.You, bind = "played"),
            Step.If(Condition.Not(Condition.Did("played")), then = listOf(move(Objects.Bound("top"), Zone.Hand)))),
        blitz("{2}{B}{R}{G}"),
    )
}
