package arcanum.parser.sets.hob

import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 01: storied and the enduring story (CR 702.195). The keyword stays a keyword (its expansion gives the
 * designation, `KeywordExpansions`); "as long as you have an enduring story" is a condition on the player (702.195b); the
 * abilities it guards are the usual statics: a cost to attack (CR 508.1d), anthems, an alternative cost for the first equip
 * ability each turn (CR 118.9, 602.2b), "doesn't untap … unless", triggers an additional time (CR 603.2d), ward.
 */
class HobChunk01StoriedTest {
    // CR 702.195b, 508.1d
    @Test
    fun darviLordOfTheCopperFells() = card(
        name = "Darvi, Lord of the Copper Fells",
        cost = "{1}{W}",
        type = "Legendary Creature — Dwarf Noble",
        text = """
            Vigilance
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, creatures can't attack you unless their controller pays {1} for each of those creatures.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Storied,
        static(Mechanic.AttackCost(manaCost("{1}"), target = Recipients(players = Players.You)), affects = Objects.Each(creature), condition = enduringStory),
    )

    // CR 702.195b, 603.2
    @Test
    fun braliTheTrailfinder() = card(
        name = "Brali the Trailfinder",
        cost = "{3}{W}",
        type = "Legendary Creature — Dwarf Scout",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, creatures you control get +1/+1.
            Whenever Brali or another nontoken Dwarf you control enters, create a 2/2 red Dwarf creature token.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Storied,
        static(pt(1, 1), affects = Objects.Each(creatureYouControl), condition = enduringStory),
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, not(token), sub("Dwarf"), you))), create(dwarf)),
    )

    // CR 118.9, 602.2b: "the first equip ability you activate each turn"; the draw triggers only once each turn (CR 603.2h)
    @Test
    fun kraliTheHandy() = card(
        name = "Krali the Handy",
        cost = "{1}{W}",
        type = "Legendary Creature — Dwarf Scout",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, you may pay {0} rather than pay the equip cost of the first equip ability you activate each turn.
            Whenever another Dwarf or Equipment you control enters, draw a card. This ability triggers only once each turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Storied,
        playerStatic(Mechanic.AbilityAlternativeCost(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind), manaCost("{0}"), firstEachTurn = true),
            condition = enduringStory),
        triggered(enters(and(another, or(sub("Dwarf"), equipment), you)), draw(1), perTurn = 1),
    )

    // CR 702.195b
    @Test
    fun ondoKeeperOfBallads() = card(
        name = "Ondo, Keeper of Ballads",
        cost = "{2}{W}",
        type = "Legendary Creature — Dwarf Bard",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, Ondo gets +1/+0 and has vigilance.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Storied,
        static(pt(1, 0), has(Keyword.Vigilance), condition = enduringStory),
    )

    /** "Draw X cards" counts the cards discarded this way: none when the hand wasn't discarded (CR 608.2c). */
    // CR 702.195b, 701.9
    @Test
    fun borinSagaKeeper() = card(
        name = "Borin, Saga-Keeper",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Dwarf Bard",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            Whenever Borin or another Dwarf you control enters, you may discard your hand. Draw X cards, where X is the number of cards discarded this way. If you have an enduring story, Borin deals X damage to each opponent.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Storied,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Dwarf"), you))),
            Step.May(listOf(Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(inZone(Zone.Hand),
                ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You))), postBind = "discarded"))), player = Players.You),
            Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Bound("discarded")))),
            Step.If(enduringStory, then = listOf(damage(Value.Count(Objects.Bound("discarded")), eachOpponent)))),
    )

    // CR 502.3
    @Test
    fun bombrinGentleSleeper() = card(
        name = "Bombrin, Gentle Sleeper",
        cost = "{2}{R}",
        type = "Legendary Creature — Dwarf Bard",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            Bombrin doesn't untap during your untap step unless you have an enduring story.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Storied,
        static(Mechanic.DoesntUntap, condition = Condition.Not(enduringStory)),
    )

    // CR 702.195b, 702.10
    @Test
    fun ulfiTheBrave() = card(
        name = "Ulfi the Brave",
        cost = "{1}{R}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, Ulfi gets +1/+0 and has haste.
            {1}, {T}, Discard a card: Draw a card.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Storied,
        static(pt(1, 0), has(Keyword.Haste), condition = enduringStory),
        activated(cost(mana("{1}"), tap, discardACard), draw(1)),
    )

    // CR 603.2d: a Dwarf's triggered ability triggers an additional time while you have an enduring story
    @Test
    fun bofrinTunefulRider() = card(
        name = "Bofrin, Tuneful Rider",
        cost = "{4}{R/W}{R/W}",
        type = "Legendary Creature — Dwarf Bard",
        text = """
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            Whenever Bofrin enters or attacks, put a +1/+1 counter on target creature.
            As long as you have an enduring story, if a triggered ability of a Dwarf you control triggers, that ability triggers an additional time.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Storied,
        triggered(entersOrAttacksSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
        static(Mechanic.TriggersAdditionalTime(null, and(sub("Dwarf"), you)), condition = enduringStory),
    )

    // CR 702.21a
    @Test
    fun tharnIronbuckler() = card(
        name = "Tharn Ironbuckler",
        cost = "{R}{W}",
        type = "Legendary Creature — Dwarf Noble",
        text = """
            Trample
            Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
            As long as you have an enduring story, artifacts and creatures you control have ward {1}.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Storied,
        static(has(Keyword.Ward(Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{1}")!!))))), affects = Objects.Each(and(or(artifact, creature), you)),
            condition = enduringStory),
    )
}
