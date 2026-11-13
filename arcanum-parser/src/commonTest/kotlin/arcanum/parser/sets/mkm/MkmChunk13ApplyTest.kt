package arcanum.parser.sets.mkm

import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 13: pump and keyword-granting effects. */
class MkmChunk13ApplyTest {
    private val smallCreature = ObjectFilter.Power(Comparison.AtMost, n(2))

    // CR 603.7c: a delayed trigger for this turn on the targeted creature; CR 702.10 haste
    @Test
    fun felloniousRageLike() = card(
        name = "Furious Outburst Test",
        cost = "{R}",
        type = "Instant",
        text = "Target creature you control gets +2/+0 and gains haste until end of turn. When that creature dies this turn, create a 2/2 white and blue Detective creature token.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0, has(Keyword.Haste)),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(create(creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2)))), duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 611.2c: the count is fixed as it resolves
    @Test
    fun getALegUpLike() = card(
        name = "Lend a Shoulder Test",
        cost = "{G}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+1 for each creature you control and gains reach.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(count(creatureYouControl), count(creatureYouControl)), has(Keyword.Reach)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.2: "another creature you control with power 2 or less"
    @Test
    fun marketwatchLike() = card(
        name = "Wispy Watcher Test",
        cost = "{1}{W}",
        type = "Creature — Spirit Detective",
        pt = "2/2",
        text = "Whenever another creature you control with power 2 or less enters, this creature gains flying until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you, smallCreature)), gains(Objects.Self, Keyword.Flying)),
    )

    @Test
    fun neighborhoodLike() = card(
        name = "Kindly Warden Test",
        cost = "{1}{W}",
        type = "Creature — Unicorn",
        pt = "2/2",
        text = "Whenever another creature you control with power 2 or less enters, target creature you control gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(another, creature, you, smallCreature)), pump(target("creature"), 1, 1),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.2: one event for entering, one for turning face up (CR 701.40)
    @Test
    fun perimeterLike() = card(
        name = "Sharp Eyed Watchman Test",
        cost = "{1}{W}",
        type = "Creature — Human Detective",
        pt = "1/1",
        text = """
            Flying, lifelink
            Whenever another Detective you control enters and whenever a Detective you control is turned face up, this creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(and(another, sub("Detective"), you), to = Zone.Battlefield),
            EventPattern.TurnedFaceUp(and(sub("Detective"), you)))), pump(Objects.Self, 1, 1)),
    )

    // CR 603.2c: once for the batch of creature cards leaving your graveyard
    @Test
    fun rotFarmLike() = card(
        name = "Graveling Crawler Test",
        cost = "{3}{B}",
        type = "Creature — Insect",
        pt = "3/4",
        text = "Whenever one or more creature cards leave your graveyard, this creature gets +1/+0 and gains menace and lifelink until end of turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            pump(Objects.Self, 1, 0, has(Keyword.Menace, Keyword.Lifelink))),
    )

    // CR 508.1: a count of attackers declared at once
    @Test
    fun seasonedLike() = card(
        name = "Steady Adviser Test",
        cost = "{1}{W}",
        type = "Creature — Human Detective",
        pt = "1/3",
        text = "Whenever you attack with three or more creatures, this creature gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(creatureYouControl)), oneOrMore = true, atLeast = 3), pump(Objects.Self, 2, 0)),
    )

    @Test
    fun dualleechLike() = card(
        name = "Slimy Twinleech Test",
        cost = "{3}{B}",
        type = "Creature — Leech",
        pt = "2/4",
        text = "At the beginning of combat on your turn, target creature you control with power 2 or less gets +1/+0 and gains deathtouch until end of turn.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 1, 0, has(Keyword.Deathtouch)),
            targets = listOf(slot("creature", and(creature, you, smallCreature)))),
    )

    // CR 611.2: -4/-4 until end of turn; CR 603.2c
    @Test
    fun soulLike() = card(
        name = "Spirit Drain Test",
        cost = "{3}{B}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, target creature gets -4/-4 until end of turn.
            Whenever one or more creature cards leave your graveyard, each opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("creature"), -4, -4), targets = listOf(slot("creature", creature))),
        triggered(Trigger.On(EventPattern.ZoneChange(and(creature, ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            loseLife(1, Players.Each(arcanum.api.PlayerFilter.Opponent)), gainLife(1)),
    )

    // CR 602.1: three activated abilities granting a keyword
    @Test
    fun trostaniLike() = card(
        name = "Three Voices Test",
        cost = "{G}{G/W}{W}",
        type = "Legendary Creature — Dryad",
        pt = "4/4",
        text = """
            {1}{G}: Target creature gains deathtouch until end of turn.
            {G/W}: Target creature gains vigilance until end of turn.
            {2}{W}: Target creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{G}")), gains(target("creature"), Keyword.Deathtouch), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{G/W}")), gains(target("creature"), Keyword.Vigilance), targets = listOf(slot("creature", creature))),
        activated(cost(mana("{2}{W}")), gains(target("creature"), Keyword.DoubleStrike), targets = listOf(slot("creature", creature))),
    )
}
