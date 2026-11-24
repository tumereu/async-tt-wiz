package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 11: "until end of turn" pumps and keyword grants: a modified-creature bonus draw, an attacks-alone trigger that pumps (per Samurai or Warrior), grants
 * lifelink or allows a graveyard cast, a pump by the greatest mana value with an untap, gain control with an untap, a granted dies trigger, a
 * replacement for the rest of the turn, a become-artifact-creature with a draw, an indestructible grant for as long as you control this. */
class NeoChunk11ApplyTest {
    private val samuraiOrWarrior: ObjectFilter = and(or(sub("Samurai"), sub("Warrior")), you)
    private val aloneEvent = EventPattern.Attack(samuraiOrWarrior, alone = true)
    private val attacker: Objects = Objects.EventObject(EventRole.Object)

    // CR 700.9 (modified: Equipment, Auras you control and counters), 608.2c (the condition is checked as the spell resolves)
    @Test
    fun ambitiousAssault() = card(
        name = "Rallying Onslaught",
        cost = "{2}{R}",
        type = "Instant",
        text = "Creatures you control get +2/+0 until end of turn. If you control a modified creature, draw a card. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(
        spell(
            pump(Objects.Each(creatureYouControl), 2, 0),
            Step.If(Condition.Exists(and(creature, you, ObjectFilter.Modified), atLeast = 1), then = listOf(draw(1))),
        ),
    )

    // CR 506.5 (attacks alone), 603.2, 611.2a
    @Test
    fun asariCaptain() = card(
        name = "Kasumi Captain",
        cost = "{3}{R}{W}",
        type = "Creature — Human Samurai",
        pt = "4/3",
        text = """
            Haste
            Whenever a Samurai or Warrior you control attacks alone, it gets +1/+0 until end of turn for each Samurai or Warrior you control.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(on(aloneEvent),
            untilEndOfTurn(attacker, Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(samuraiOrWarrior)), n(0)))),
    )

    // CR 506.5, 603.2, 611.2a
    @Test
    fun eiganjoExemplar() = card(
        name = "Eiganjo Paragon",
        cost = "{1}{W}",
        type = "Enchantment Creature — Human Samurai",
        pt = "2/1",
        text = "Whenever a Samurai or Warrior you control attacks alone, it gets +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(on(aloneEvent), pump(attacker, 1, 1)),
    )

    // CR 506.5, 601.2a and 118.9 (cast from a graveyard), 611.2a (this turn)
    @Test
    fun heikoYamazaki() = card(
        name = "Heiko Yamasaki, the Colonel",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Samurai",
        pt = "3/3",
        text = """
            Trample
            Whenever a Samurai or Warrior you control attacks alone, you may cast target artifact card from your graveyard this turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(aloneEvent),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(target("card")), Duration.EndOfTurn)),
            targets = listOf(slot("card", inYourGraveyard(artifact)))),
    )

    // CR 506.5, 601.2a, 611.2a
    @Test
    fun norikaYamazaki() = card(
        name = "Noriko Yamasaki, the Bard",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Samurai",
        pt = "3/2",
        text = """
            Vigilance
            Whenever a Samurai or Warrior you control attacks alone, you may cast target enchantment card from your graveyard this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(aloneEvent),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
                Affected.Objects(target("card")), Duration.EndOfTurn)),
            targets = listOf(slot("card", inYourGraveyard(ObjectFilter.Type(CardType.Enchantment))))),
    )

    // CR 506.5, 702.15 (lifelink), 611.2a; 702.12 (indestructible), 701.8 ("destroy")
    @Test
    fun selflessSamurai() = card(
        name = "Devoted Ronin",
        cost = "{1}{W}",
        type = "Creature — Fox Samurai",
        pt = "2/2",
        text = """
            Whenever a Samurai or Warrior you control attacks alone, it gains lifelink until end of turn.
            Sacrifice this creature: Another target creature you control gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(
        triggered(on(aloneEvent), gains(attacker, Keyword.Lifelink)),
        activated(cost(sacrificeSelf), gains(target("creature"), Keyword.Indestructible),
            targets = listOf(slot("creature", and(another, creatureYouControl)))),
    )

    // CR 107.3 (X is the greatest mana value among the permanents, worked out as the spell resolves), 701.26 (untap)
    @Test
    fun boonOfBoseiju() = card(
        name = "Boon of Hakkaido",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature gets +X/+X until end of turn, where X is the greatest mana value among permanents you control. Untap it.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(greatestManaValue, greatestManaValue)),
            untapIt(target("creature")),
            targets = listOf(slot("creature", creature))),
    )

    // CR 701.12 (gain control), 701.26, 611.2a (permanently)
    @Test
    fun invokeTheWinds() = card(
        name = "Beckon the Gales",
        cost = "{1}{U}{U}{U}{U}",
        type = "Sorcery",
        text = "Gain control of target artifact or creature. Untap it.",
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("permanent")), Duration.Indefinitely)),
            untapIt(target("permanent")),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 113.6m-n and 611.2b (for as long as you control Kyodai), 702.12, 702.8 (flash), 602.5
    @Test
    fun kyodaiSoulOfKamigawa() = card(
        name = "Kyoden, Spirit of Hakkaido",
        cost = "{3}{W}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "3/3",
        text = """
            Flash
            Flying
            When Kyoden enters, another target permanent gains indestructible for as long as you control Kyoden.
            {W}{U}{B}{R}{G}: Kyoden gets +5/+5 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Indestructible)), Affected.Objects(target("permanent")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            targets = listOf(slot("permanent", and(another, permanentFilter)))),
        activated(cost(mana("{W}{U}{B}{R}{G}")), pump(Objects.Self, 5, 5)),
    )

    // CR 614.1a, 614.6, 611.2c (a replacement for the rest of the turn, for every creature)
    @Test
    fun maliciousMalfunction() = card(
        name = "Wicked Misfire",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = "All creatures get -2/-2 until end of turn. If a creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Each(creature), pt(-2, -2)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = null, duration = Duration.EndOfTurn))),
    )

    // CR 700.4 (dies), 603.10a, 400.7, 111.4; 702.15
    @Test
    fun returnToAction() = card(
        name = "Back in the Fray",
        cost = "{1}{B}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+0 and gains lifelink and \"When this creature dies, return it to the battlefield tapped under its owner's control.\"",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), pt(1, 0), Mechanic.GainAbilities(listOf(Keyword.Lifelink,
            triggered(diesSelf, move(enteredOne, Zone.Battlefield, tapped = true, controller = Players.OwnerOf(enteredOne)))))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 602.1 (sacrifice as a cost), 611.2a
    @Test
    fun scrapyardSteelbreaker() = card(
        name = "Junkyard Ironbreaker",
        cost = "{3}{R}",
        type = "Artifact Creature — Human Warrior",
        pt = "3/4",
        text = "{1}, Sacrifice another artifact: This creature gets +2/+1 until end of turn.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(and(another, artifact))), pump(Objects.Self, 2, 1)),
    )

    // CR 205.1b (becomes an artifact creature and keeps its types), 613.4b, 611.2a
    @Test
    fun suitUp() = card(
        name = "Gear Up",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Until end of turn, target creature or Vehicle becomes an artifact creature with base power and toughness 4/5.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("permanent"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(4), n(5))),
            draw(1),
            targets = listOf(slot("permanent", or(creature, sub("Vehicle"))))),
    )

    // CR 702.11 (hexproof), 702.12, 119.1
    @Test
    fun tamiyosSafekeeping() = card(
        name = "Tamara's Safeguard",
        cost = "{G}",
        type = "Instant",
        text = "Target permanent you control gains hexproof and indestructible until end of turn. You gain 2 life. (A permanent with hexproof and indestructible can't be the target of spells or abilities your opponents control. Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        spell(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible), gainLife(2),
            targets = listOf(slot("permanent", and(permanentFilter, you)))),
    )

    // CR 603.2, 611.2a
    @Test
    fun towashiSongshaper() = card(
        name = "Tokoshi Songweaver",
        cost = "{1}{R}",
        type = "Artifact Creature — Human Artificer",
        pt = "2/2",
        text = "Whenever another artifact you control enters, this creature gets +1/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(enters(and(another, artifact, you)), pump(Objects.Self, 1, 0)),
    )

    private val enteredOne = Objects.EventObject(EventRole.After)
    private val greatestManaValue: Value = Value.Of(NumericProperty.ManaValue, Objects.Each(and(permanentFilter, you)), Combine.Greatest)
}
