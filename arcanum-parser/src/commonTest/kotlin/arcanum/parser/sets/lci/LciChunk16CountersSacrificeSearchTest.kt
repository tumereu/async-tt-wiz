package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** LCI chunk 16: +1/+1 and stun counters, sacrifice costs, "you may ... when you do", searches, Restless lands and "untap during each other player's untap step". */
class LciChunk16CountersSacrificeSearchTest {
    private val x: Value = Value.Announced(name = "X")
    private val stun: CounterKind = CounterKind.Stun
    private val cave = sub("Cave")
    private val anotherCreatureOrArtifact = and(another, or(creature, artifact))
    private val artifactOrCreatureOfOpponent = and(or(artifact, creature), opponents)
    private val foundBasicOrCave = or(basicLand(), cave)
    private val entersTappedOnBattlefield = move(Objects.Bound("found"), Zone.Battlefield, tapped = true)

    // CR 701.26a (tap cost), 117.1b (sorcery timing), 122.1
    @Test
    fun brassGemward() = card(
        name = "Brass Gemward",
        cost = "{3}{W}",
        type = "Artifact Creature — Gnome",
        pt = "3/3",
        text = "Tap two untapped artifacts and/or creatures you control: Put a +1/+1 counter on this creature. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), n(2)))), counters(Objects.Self), rules = asSorcery),
    )

    // CR 118.1: a sacrifice cost; CR 122.1
    @Test
    fun marrowpitWarden() = card(
        name = "Marrowpit Warden",
        cost = "{W}{B}",
        type = "Legendary Creature — Vampire Knight",
        pt = "2/1",
        text = "Sacrifice another creature or artifact: Put a +1/+1 counter on Marrowpit Warden.",
    ) parsesTo abilities(
        activated(cost(sacrificeA(anotherCreatureOrArtifact)), counters(Objects.Self)),
    )

    // CR 603.2, 115.1 (a target of a given subtype), 122.1
    @Test
    fun cosmicCorsair() = card(
        name = "Cosmic Corsair",
        cost = "{U}{R}",
        type = "Legendary Creature — Human Pirate",
        pt = "2/2",
        text = "Whenever an artifact you control enters, put a +1/+1 counter on target Pirate you control.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), counters(target("Pirate")), targets = listOf(slot("Pirate", and(sub("Pirate"), you)))),
    )

    // CR 122.1d (stun counter), 603.4 is not involved: "while" is checked only as the ability triggers
    @Test
    fun thunderhideBrute() = card(
        name = "Thunderhide Brute",
        cost = "{2}{G}",
        type = "Creature — Dinosaur",
        pt = "6/6",
        text = "Whenever this creature attacks while you don't control another Dinosaur, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        triggered(attacksSelf, counters(Objects.Self, 1, stun),
            triggersOnlyIf = Condition.Not(Condition.Exists(and(another, sub("Dinosaur"), you), atLeast = 1))),
    )

    // CR 118.1, 122.1, 702.110 (menace) until end of turn
    @Test
    fun bloodpactAcolyte() = card(
        name = "Bloodpact Acolyte",
        cost = "{3}{B}",
        type = "Creature — Vampire Knight",
        pt = "3/3",
        text = "{B}, Sacrifice another creature or artifact: Put a +1/+1 counter on this creature. It gains menace until end of turn.",
    ) parsesTo abilities(
        activated(cost(mana("{B}"), sacrificeA(anotherCreatureOrArtifact)), counters(Objects.Self), gains(Objects.Self, Keyword.Menace)),
    )

    // CR 603.12 (reflexive trigger), 122.1: the targets are chosen only when the sacrifice was made
    @Test
    fun sacrificialHerald() = card(
        name = "Sacrificial Herald",
        cost = "{2}{W}",
        type = "Creature — Vampire Soldier",
        pt = "3/2",
        text = "When this creature enters, you may sacrifice another creature or artifact. When you do, put a +1/+1 counter on each of up to two target creatures.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(anotherCreatureOrArtifact)), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))))),
    )

    // CR 603.12: paying mana then the reflexive trigger
    @Test
    fun lunarRaider() = card(
        name = "Lunar Raider",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "Whenever this creature attacks, you may pay {2}{W}. When you do, tap target creature.",
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(manaCost("{2}{W}"), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), reflexive(tapIt(target("creature")), targets = listOf(slot("creature", creature))))),
    )

    // CR 614.1d, 614.12 (enters tapped), 107.3 (X twice), 701.8
    @Test
    fun rustworkDisassembler() = card(
        name = "Rustwork Disassembler",
        cost = "{1}{W}",
        type = "Creature — Human Artificer",
        pt = "1/4",
        text = """
            Artifacts your opponents control enter tapped.
            {X}{X}{W}, Sacrifice this creature: Destroy each artifact with mana value X.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(artifact, opponents), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self)),
        Ability.Activated(cost(mana("{X}{X}{W}"), sacrificeSelf), Instructions(
            steps = listOf(destroy(Objects.Each(and(artifact, ObjectFilter.ManaValue(Comparison.Equal, x))))), announced = listOf("X")), anyTime),
    )

    // CR 614.1c (enters with counters), 700.4 (dies), 122.1, 122.2 (moving a counter), CR 701.26 not involved
    @Test
    fun stonewickCache() = card(
        name = "Stonewick Cache",
        cost = "{1}{G}",
        type = "Artifact",
        text = """
            This artifact enters with two +1/+1 counters on it.
            Whenever a creature you control with a +1/+1 counter on it dies, put a +1/+1 counter on this artifact.
            {T}: Move a +1/+1 counter from this artifact onto target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(2))))),
        triggered(dies(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))), counters(Objects.Self)),
        activated(cost(tap), doing(Effect.MoveCounters(from = Objects.Self, to = target("creature"), kind = p1p1, amount = n(1))),
            targets = listOf(slot("creature", creature)), rules = asSorcery),
    )

    // CR 614.1d, 614.12 (a land that enters tapped), 205.1b ("still a land"), 508.1 (attacking), 611.2c (affects creatures at resolution)
    @Test
    fun duskyPrairie() = card(
        name = "Dusky Prairie",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G} or {W}.
            {2}{G}{W}: This land becomes a 3/3 green and white Llama creature until end of turn. It's still a land.
            Whenever this land attacks, other creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("G", "W"),
        activated(cost(mana("{2}{G}{W}")), becomesCreature("Llama", listOf(Color.Green, Color.White), 3, 3)),
        triggered(attacksSelf, pump(Objects.Each(and(another, creature, you)), 1, 1)),
    )

    // CR 508.1 (attacking creature), 701.26b (untap)
    @Test
    fun emberRidgeline() = card(
        name = "Ember Ridgeline",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R} or {G}.
            {2}{R}{G}: This land becomes a 3/4 red and green Dinosaur creature until end of turn. It's still a land.
            Whenever this land attacks, another target attacking creature gets +2/+0 until end of turn. Untap that creature.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("R", "G"),
        activated(cost(mana("{2}{R}{G}")), becomesCreature("Dinosaur", listOf(Color.Red, Color.Green), 3, 4)),
        triggered(attacksSelf, pump(target("creature"), 2, 0), untapIt(target("creature")),
            targets = listOf(slot("creature", and(another, attacking, creature)))),
    )

    // CR 614.12, 701.9 (discard), 603.12 not involved: "if you do" follows a may-cost
    @Test
    fun gloomwaterVents() = card(
        name = "Gloomwater Vents",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B} or {R}.
            {1}{B}{R}: Until end of turn, this land becomes a 2/3 black and red Insect creature with menace. It's still a land.
            Whenever this land attacks, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("B", "R"),
        activated(cost(mana("{1}{B}{R}")), becomesCreature("Insect", listOf(Color.Black, Color.Red), 2, 3, has = Keyword.Menace)),
        triggered(attacksSelf, Step.Pay(cost(discardACard), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
    )

    // CR 608.2d / 201.3 (choose a card name), 702 (mana abilities excluded): the false flag is "unless they're mana abilities"
    @Test
    fun spectacleLens() = card(
        name = "Spectacle Lens",
        cost = "{2}",
        type = "Artifact",
        text = """
            As this artifact enters, look at an opponent's hand, then choose any card name.
            Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(ObjectFilter.Is(Objects.Self).let { EventPattern.ZoneChange(it, to = Zone.Battlefield) }, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent"))))), Players.You)),
            Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You, remember = true),
        ))))),
        static(Mechanic.CantBeActivated(manaAbilities = false), affects = Objects.Each(and(ObjectFilter.RememberedName("name"), ObjectFilter.Or(listOf(
            Zone.Battlefield, Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command).map { ObjectFilter.InZone(it) })))),
    )

    // CR 701.21a (sacrifice), 701.21b: each player chooses two of their own creatures
    @Test
    fun gravetideBrute() = card(
        name = "Gravetide Brute",
        cost = "{4}{B}{B}",
        type = "Creature — Horror",
        pt = "6/6",
        text = "When this creature enters, each player sacrifices two creatures of their choice.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(2), n(2)),
                bind = "sacrificed", chooser = Players.Bound("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed")))))),
    )

    // CR 701.23 (search), 701.20a (reveal), 701.24 (shuffle), 401.4 not involved: the found card goes on top after the shuffle
    @Test
    fun cartographersGnome() = card(
        name = "Cartographer's Gnome",
        cost = "{2}",
        type = "Artifact Creature — Gnome",
        pt = "2/1",
        text = "When this creature enters, you may search your library for a basic land card or Cave card, reveal it, then shuffle and put that card on top.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(
            search(foundBasicOrCave, reveal = true),
            Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0),
        ), Players.You)),
    )

    // CR 700.2 (modes), 701.23, 701.24, 614.12 (enters tapped)
    @Test
    fun delveTheHollow() = card(
        name = "Delve the Hollow",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Search your library for a basic Forest card, put that card onto the battlefield tapped, then shuffle.
            • Return target Cave card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(search(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), sub("Forest"))), entersTappedOnBattlefield, shuffle),
            mode(move(target("card"), Zone.Battlefield, tapped = true), targets = listOf(slot("card", inYourGraveyard(cave)))),
        ))),
    )

    // CR 701.23, 701.24, 614.12
    @Test
    fun pathfindingSurveyor() = card(
        name = "Pathfinding Surveyor",
        cost = "{4}",
        type = "Artifact Creature — Gnome",
        pt = "3/2",
        text = "When this creature enters, search your library for a basic land card or Cave card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        triggered(entersSelf, search(foundBasicOrCave), entersTappedOnBattlefield, shuffle),
    )

    // CR 603.4 (intervening "if"), 122.1d (stun counter), 701.26a
    @Test
    fun tidewatchPirates() = card(
        name = "Tidewatch Pirates",
        cost = "{3}{U}",
        type = "Creature — Human Pirate",
        pt = "3/3",
        text = "When this creature enters, if you control an artifact, tap target artifact or creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("permanent")), counters(target("permanent"), 1, stun),
            targets = listOf(slot("permanent", artifactOrCreatureOfOpponent)), interveningIf = Condition.Exists(and(artifact, you), atLeast = 1)),
    )

    // CR 502.3 (untap step), 502.4: untaps in each other player's untap step
    @Test
    fun moonlitInfantry() = card(
        name = "Moonlit Infantry",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/4",
        text = "Untap this creature during each other player's untap step.",
    ) parsesTo abilities(
        static(Mechanic.UntapsDuringOtherUntapSteps),
    )

    /** "This land becomes a P/T <colors> <Sub> creature until end of turn. It's still a land." (CR 205.1b, 613.1d) */
    private fun becomesCreature(sub: String, colors: List<Color>, power: Int, toughness: Int, has: Keyword? = null): Step =
        untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype(sub))), replaceSubtypes = true),
            Mechanic.SetColors(colors.map { arcanum.api.ColorRef.Literal(it) }), Mechanic.SetPowerToughness(n(power), n(toughness)),
            *listOfNotNull(has?.let { has(it) }).toTypedArray())
}
