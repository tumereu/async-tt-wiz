package arcanum.parser.sets.hob

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 02: recruit (CR 701.70a), a keyword action written out by `KeywordExpansions.expand`: on enters, dies,
 * attack and cast triggers, after a counter (the countered spell as it last existed, CR 608.2h) and in a Saga; one target for
 * each opponent exiled until this permanent leaves (CR 601.2c, 610.3); flash only while you control a Human (CR 702.8a).
 */
class HobChunk02RecruitTest {
    // CR 601.2c, 610.3: one target for each opponent, back at once when this enchantment leaves
    @Test
    fun toastTheHillKing() = card(
        name = "Toast the Hill-King",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, for each opponent, exile up to one target nonland permanent that player controls until this enchantment leaves the battlefield.
            When this enchantment enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(TargetSlot("permanent", TargetKind.Objects(and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer)))),
                min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent))),
        triggered(entersSelf, recruit),
    )

    // CR 604.3: a characteristic-defining power
    @Test
    fun fenmereGarrison() = card(
        name = "Fenmere Garrison",
        cost = "{4}{W}",
        type = "Creature — Human Soldier",
        text = """
            Fenmere Garrison's power is equal to the number of creatures you control.
            When this creature enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        """,
        pt = "*/5",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(creatureYouControl), null), characteristicDefining = true),
        triggered(entersSelf, recruit),
    )

    // CR 701.70a
    @Test
    fun marshTownLookout() = card(
        name = "Marsh-town Lookout",
        cost = "{W}",
        type = "Creature — Human Scout",
        text = "When this creature dies, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(diesSelf, recruit),
    )

    // CR 603.2: the first noncreature spell that opponent casts each turn
    @Test
    fun theDuchessOfVale() = card(
        name = "The Duchess of Vale",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Noble",
        text = "Whenever an opponent casts their first noncreature spell each turn, you recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Cast(noncreatureSpell, PlayerFilter.Opponent)), nthThisTurn = 1), recruit),
    )

    // CR 508.3d, 702.122a
    @Test
    fun grandPaintedBarge() = card(
        name = "Grand Painted Barge",
        cost = "{2}{U}",
        type = "Artifact — Vehicle",
        text = """
            Whenever you attack, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(youAttack, recruit),
        Keyword.Crew(2),
    )

    @Test
    fun wideMerePest() = card(
        name = "Wide Mere Pest",
        cost = "{3}{U}",
        type = "Creature — Bird",
        text = """
            Flying
            When this creature enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, recruit),
    )

    // CR 608.2h: the countered spell's mana value as it last existed
    @Test
    fun blowTheClarions() = card(
        name = "Blow the Clarions",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = "Counter target spell. If that spell's mana value was 2 or less, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Counter(target("spell"))),
            Step.If(Condition.ObjectMatches(target("spell"), manaValue(Comparison.AtMost, 2)), then = listOf(recruit)),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 702.8a: as though it had flash only while you control a Human
    @Test
    fun archersMuster() = card(
        name = "Archer's Muster",
        cost = "{2}{W}{U}",
        type = "Creature — Human Citizen",
        text = """
            You may cast this spell as though it had flash if you control a Human.
            Other creatures you control get +1/+1.
            Whenever this creature enters or attacks, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(Mechanic.InstantSpeed, condition = Condition.Exists(and(sub("Human"), you), atLeast = 1)),
        static(pt(1, 1), affects = Objects.Each(and(another, creature, you))),
        triggered(entersOrAttacksSelf, recruit),
    )

    @Test
    fun steadyTutor() = card(
        name = "Steady Tutor",
        cost = "{2}{W/U}",
        type = "Creature — Human Citizen",
        text = """
            Vigilance
            When this creature enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, recruit),
    )
}
