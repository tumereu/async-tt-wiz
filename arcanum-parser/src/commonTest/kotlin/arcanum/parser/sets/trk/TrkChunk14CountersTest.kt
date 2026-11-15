package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 14: counters put by triggers (quest counters, +1/+1 counters), counter-conditioned statics, filters and costs. */
class TrkChunk14CountersTest {
    private val quest = CounterKind.Named("quest")
    private fun questCount(n: Int) = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, atLeast = n))
    private val nonCreature = not(creature)
    private val withCounter = ObjectFilter.HasCounter(null, 1)
    private val totalPower8 = Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Total), Comparison.AtLeast, n(8))
    private val youAttack = Trigger.On(listOf(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You))), oneOrMore = true)
    private val borg = creatureToken("Borg", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature), name = "Borg Token")

    // CR 122.1 (quest counters), 608.2c (the discard is conditional on the counters after the first two steps)
    @Test
    fun anthropologicalMission() = card(
        name = "Ethnographic Errand",
        cost = "{2}{U}",
        type = "Enchantment",
        text = "Whenever you cast a noncreature spell, put a quest counter on this enchantment. Draw a card. Then discard a card unless this enchantment has three or more quest counters on it.",
    ) parsesTo abilities(
        triggered(youCast(nonCreature), counters(Objects.Self, 1, quest), draw(1),
            Step.If(Condition.Not(questCount(3)), then = listOf(discard(1)))),
    )

    // CR 700.4 (dies), 603.2c (another creature, any controller), 603.2g ("only once each turn")
    @Test
    fun battleScarredSurvivalist() = card(
        name = "Scarred Scrapper",
        cost = "{1}{B}",
        type = "Creature — Klingon Scout",
        pt = "1/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever another creature dies, put a +1/+1 counter on this creature. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self), perTurn = 1),
    )

    // CR 122.1: the counter is put first, so the sixth counter already makes two tokens; "nontoken" (CR 111.1)
    @Test
    fun conquestMission() = card(
        name = "Subjugation Errand",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "Whenever a nontoken artifact you control enters, put a quest counter on this enchantment. Create a 1/1 colorless Borg artifact creature token. If this enchantment has six or more quest counters on it, create two of those tokens instead.",
    ) parsesTo abilities(
        triggered(enters(and(not(token), artifact, you)), counters(Objects.Self, 1, quest),
            Step.If(questCount(6), then = listOf(create(borg, n(2))), otherwise = listOf(create(borg)))),
    )

    // CR 700.2 (modes), 106.1b ("two mana of any one color": one color for both, ManaKind.Choose with no color list)
    @Test
    fun culberAndStametsStarCrossed() = card(
        name = "Verrin and Dosk, Bound-Together",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Doctor Artificer",
        pt = "3/4",
        text = """
            When Verrin and Dosk enter, choose one —
            • Put a +1/+1 counter on target creature.
            • Add two mana of any one color.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(target("creature")), targets = listOf(slot("creature", creature))),
            mode(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(2)), Players.You))),
        ))),
    )

    // CR 122.1, 613.1f (the static ability grants trample to creatures with a counter of any kind)
    @Test
    fun dominionSupervisor() = card(
        name = "Hegemony Overseer",
        cost = "{2}{G}",
        type = "Creature — Vorta Doctor",
        pt = "3/1",
        text = """
            When this creature enters, put a +1/+1 counter on target creature.
            Creatures you control with counters on them have trample.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, withCounter))),
    )

    // CR 700.2, 122.1 (a permanent with a counter of any kind), 608.2c
    @Test
    fun federationFieldMedic() = card(
        name = "Alliance Ward Nurse",
        cost = "{3}{W}",
        type = "Creature — Human Doctor",
        pt = "3/2",
        text = """
            Vigilance
            When this creature enters, choose one —
            • Put a +1/+1 counter on target creature. It gains vigilance until end of turn.
            • Draw a card if you control a permanent with a counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(counters(target("creature")), gains(target("creature"), Keyword.Vigilance), targets = listOf(slot("creature", creature))),
            mode(Step.If(Condition.Exists(and(ObjectFilter.Category(ObjectCategory.Permanent), withCounter, you), 1), then = listOf(draw(1)))),
        ))),
    )

    // CR 122.1, 205.3m (each Klingon you control)
    @Test
    fun generalChangColdWarrior() = card(
        name = "General Kozar, Frost Veteran",
        cost = "{B}",
        type = "Legendary Creature — Klingon Warrior Officer",
        pt = "1/2",
        text = "{1}, Sacrifice a creature: Put a +1/+1 counter on each Klingon you control.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(creature)), counters(Objects.Each(and(sub("Klingon"), you)))),
    )

    // CR 506.2, 508.1 (you attack: one trigger however many attack), 603.4 ("while": checked as the trigger event happens, not an intervening if)
    @Test
    fun hologramJaneway() = card(
        name = "Projection Marlowe",
        cost = "{2}{W}",
        type = "Legendary Artifact Creature — Illusion Officer",
        pt = "3/3",
        text = "Whenever you attack while creatures you control have total power 8 or greater, put a +1/+1 counter on target creature.",
    ) parsesTo abilities(
        triggered(youAttack, counters(target("creature")), targets = listOf(slot("creature", creature)), triggersOnlyIf = totalPower8),
    )

    // CR 118.12 area: a cost removing counters spread over several creatures (CR 122.1), cast by any player
    @Test
    fun lieutenantCommanderData() = card(
        name = "Subcommander Ordo",
        cost = "{2}{U}",
        type = "Legendary Artifact Creature — Android Scientist",
        pt = "2/2",
        text = """
            Whenever a player casts a noncreature spell, put a +1/+1 counter on Subcommander Ordo.
            {1}, Remove three counters from among creatures you control: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(nonCreature, PlayerFilter.Any)), counters(Objects.Self)),
        activated(cost(mana("{1}"), CostPart.RemoveCounters(null, n(3), CostObjects.Choose(creatureYouControl, count = null), distributed = true)), draw(1)),
    )

    // CR 508.1, 603.2 (an attack trigger with a while-condition), then the untap (CR 701.26)
    @Test
    fun tacticalOfficer() = card(
        name = "Drill Lieutenant",
        cost = "{1}{W}",
        type = "Creature — Human Officer",
        pt = "2/1",
        text = "Whenever you attack while creatures you control have total power 8 or greater, put a +1/+1 counter on this creature. Untap it.",
    ) parsesTo abilities(
        triggered(youAttack, counters(Objects.Self), untapIt(Objects.Self), triggersOnlyIf = totalPower8),
    )

    // CR 122.1: the first counter is put on the target, then each creature you control with a counter (including that target if yours)
    @Test
    fun theDoctorEmh() = card(
        name = "The Surgeon, Holo",
        cost = "{3}{G/W}{G/W}",
        type = "Legendary Artifact Creature — Illusion Doctor",
        pt = "3/3",
        text = "When The Surgeon enters, put a +1/+1 counter on target creature. Then put a +1/+1 counter on each creature you control with a counter on it.",
    ) parsesTo abilities(
        triggered(entersSelf,
            counters(target("creature")), counters(Objects.Each(and(creature, you, withCounter))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 122.1, 608.2c ("instead": the counter is already there when the count is checked)
    @Test
    fun vengeanceMission() = card(
        name = "Reprisal Errand",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "Whenever a creature dies, put a quest counter on this enchantment. Target opponent loses 1 life. That player loses 2 life instead if there are seven or more quest counters on this enchantment.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Self, 1, quest),
            Step.If(questCount(7), then = listOf(loseLife(2, Players.Target("opponent"))), otherwise = listOf(loseLife(1, Players.Target("opponent")))),
            targets = listOf(targetOpponent)),
    )
}
