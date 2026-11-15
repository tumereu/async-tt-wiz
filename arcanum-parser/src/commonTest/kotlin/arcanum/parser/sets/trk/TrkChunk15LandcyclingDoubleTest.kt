package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK chunk 15: basic landcycling, doubled power, total-power attack triggers, mana restricted to artifacts, two abilities sharing a tap. */
class TrkChunk15LandcyclingDoubleTest {
    private val landcycling = Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{2}"))
    private val totalPower8 = Condition.Compare(Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Total), Comparison.AtLeast, n(8))
    private val artifactCreature = and(artifact, creature)

    // CR 702.29 (cycling), 509.1b (blocking restriction by power), 122.1
    @Test
    fun coldBloodedCrew() = card(
        name = "Reptile Vanguard",
        cost = "{4}{G}{G}",
        type = "Creature — Gorn Warrior",
        pt = "4/4",
        text = """
            When this creature enters, put two +1/+1 counters on target creature.
            This creature can't be blocked by creatures with power 2 or less.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature"), 2), targets = listOf(slot("creature", creature))),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        landcycling,
    )

    // CR 702.29, 611.2c (the pump affects only creatures you control when it resolves)
    @Test
    fun freeBorgRevolutionaries() = card(
        name = "Unbound Drone Rebels",
        cost = "{3}{W}{W}",
        type = "Artifact Creature — Borg Rebel",
        pt = "4/5",
        text = """
            When this creature enters, creatures you control get +1/+1 until end of turn.
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, pump(Objects.Each(creatureYouControl), 1, 1)),
        landcycling,
    )

    // CR 700.2 (modes), 701.25 (surveil), 702.29
    @Test
    fun rogueArtificialIntelligence() = card(
        name = "Renegade Logic Engine",
        cost = "{4}{B}{B}",
        type = "Artifact Creature — Construct",
        pt = "6/5",
        text = """
            When this creature enters, choose one —
            • You gain 3 life.
            • Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(gainLife(3)),
            mode(surveil(2)),
        ))),
        landcycling,
    )

    // CR 101.5 (can't be countered), 701.10 (double a life total: gain life equal to it), 402.2 (no maximum hand size)
    @Test
    fun liveLongAndProsper() = card(
        name = "Long Life and Fortune",
        cost = "{5}{W}{U}",
        type = "Sorcery",
        text = """
            This spell can't be countered.
            Double your life total. Draw cards equal to the number of cards in your hand. You have no maximum hand size until your next turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        spell(
            Step.Do(Effect.SetLife(Players.You, Value.Times(Value.LifeOf(Players.You), 2))),
            Step.Do(Effect.Draw(Players.You, count(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.UntilYourNextTurn)),
        ),
    )

    // CR 701.10b (double power: +X/+0 with X its power as it resolves), 700.5 (total power), 508.1, 603.2
    @Test
    fun worfHonorBoundWarrior() = card(
        name = "Kaleth, Steadfast Warrior",
        cost = "{R/W}{R/W}",
        type = "Legendary Creature — Klingon Warrior Officer",
        pt = "1/1",
        text = """
            Whenever you attack while creatures you control have total power 8 or greater, double Kaleth's power until end of turn.
            Tap two untapped artifacts and/or creatures you control: Put a +1/+1 counter on Kaleth. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youAttack, untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(power(Objects.Self), n(0))), triggersOnlyIf = totalPower8),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), n(2)))), counters(Objects.Self), rules = asSorcery),
    )

    // CR 605.1a (a mana ability), 106.6 (spend only on artifact spells and abilities of artifact sources), 602.2, 701.9
    @Test
    fun isolinearProcessor() = card(
        name = "Linear Cogitator",
        cost = "{2}",
        type = "Artifact",
        text = """
            {T}: Add {C}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
            {2}, {T}: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!),
            restriction = or(and(spellFilter, artifact), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = artifact))), Players.You))),
        activated(cost(mana("{2}"), tap), draw(1), discard(1)),
    )

    // CR 602.2, 701.21 (sacrifice), 701.23 (search), 118.3
    @Test
    fun unexploredTerritory() = card(
        name = "Uncharted Frontier",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    // CR 603.7 (delayed trigger on "this turn"), 700.4 (dies), 608.2h
    @Test
    fun aGoodDayToDie() = card(
        name = "A Fine Day for It",
        cost = "{B}",
        type = "Instant",
        text = "Target creature gets +2/+0 until end of turn. When that creature dies this turn, you draw a card.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0),
            doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(draw(1))), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 508.1 (beginning of combat), 603.4, 702.110 (menace), 700.5
    @Test
    fun cantankerousCaptain() = card(
        name = "Querulous Skipper",
        cost = "{2}{R}",
        type = "Creature — Tellarite Officer",
        pt = "2/3",
        text = "At the beginning of combat on your turn, target creature you control gets +1/+0 until end of turn. Then that creature gains menace until end of turn if creatures you control have total power 8 or greater. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 1, 0),
            Step.If(totalPower8, then = listOf(gains(target("creature"), Keyword.Menace))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 700.2, 701.8 (-1/-1 until end of turn on all other creatures), 701.21 (each player sacrifices a creature of their choice)
    @Test
    fun dghorBloodletter() = card(
        name = "Garn the Bloodletter",
        cost = "{2}{B}",
        type = "Creature — Klingon Assassin",
        pt = "2/2",
        text = """
            When this creature enters, choose one —
            • All other creatures get -1/-1 until end of turn.
            • Each player sacrifices a creature of their choice.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(pump(Objects.Each(and(another, creature)), -1, -1)),
            mode(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed",
                    chooser = Players.Bound("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))), together = true)),
        ))),
    )

    // CR 700.5, 508.1, 509.1b (can't block), 603.2
    @Test
    fun laAnNoonienSingh() = card(
        name = "Ilya Nunyen-Sorn, Warden",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Human Soldier",
        pt = "4/3",
        text = "Whenever you attack while creatures you control have total power 8 or greater, target creature can't block this turn.",
    ) parsesTo abilities(
        triggered(youAttack, untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creature", creature)), triggersOnlyIf = totalPower8),
    )

    // CR 702.110 (menace), 611.2c (only artifact creatures you control as the ability resolves)
    @Test
    fun organicAvulsionUnit() = card(
        name = "Cellular Reaver Unit",
        cost = "{2}{B}",
        type = "Artifact Creature — Borg",
        pt = "2/3",
        text = "{2}: Artifact creatures you control gain menace until end of turn. (A creature with menace can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        activated(manaCost("{2}"), gains(Objects.Each(and(artifact, creature, you)), Keyword.Menace)),
    )

    // CR 602.5d (sorcery timing), 611.2c, 107.3 (X is the creature's power as the ability resolves)
    @Test
    fun picardLeadingByExample() = card(
        name = "Capt. Lorrin, Leading by Deed",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Officer",
        pt = "2/2",
        text = "{1}, {T}: Another target creature gets +X/+X until end of turn, where X is Capt. Lorrin's power. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(power(Objects.Self), power(Objects.Self))), targets = listOf(slot("creature", and(another, creature))), rules = asSorcery),
    )

    // CR 702.11 (hexproof), 115.1
    @Test
    fun saruUnthreatened() = card(
        name = "Sarn, Undaunted",
        cost = "{2}{G}",
        type = "Legendary Creature — Kelpien Officer",
        pt = "3/2",
        text = """
            Flash
            When Sarn enters, another target creature you control gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, gains(target("creature"), Keyword.Hexproof), targets = listOf(slot("creature", and(another, creatureYouControl)))),
    )

    // CR 702.12 (indestructible), 702.11, 122.1, 608.2c ("if it's a creature" of the chosen target)
    @Test
    fun shieldsUp() = card(
        name = "Raise the Barriers",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target artifact or creature you control gains hexproof and indestructible until end of turn. If it's a creature, put a +1/+1 counter on it. (It can't be the target of spells or abilities your opponents control. Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible),
            Step.If(Condition.ObjectMatches(target("permanent"), creature), then = listOf(counters(target("permanent")))),
            targets = listOf(slot("permanent", and(or(artifact, creature), you)))),
    )

    // CR 700.5, 508.1, 702.7 (first strike), 603.2
    @Test
    fun unaChinRiley() = card(
        name = "Ulla Chen-Rayne, Second",
        cost = "{4}{W}",
        type = "Legendary Creature — Illyrian Officer",
        pt = "4/5",
        text = "Whenever you attack while creatures you control have total power 8 or greater, target attacking creature gets +2/+0 and gains first strike until end of turn.",
    ) parsesTo abilities(
        triggered(youAttack, pump(target("creature"), 2, 0, has(Keyword.FirstStrike)), targets = listOf(slot("creature", and(attacking, creature))), triggersOnlyIf = totalPower8),
    )
}
