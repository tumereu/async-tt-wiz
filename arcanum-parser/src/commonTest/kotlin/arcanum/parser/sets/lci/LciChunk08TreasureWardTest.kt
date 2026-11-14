package arcanum.parser.sets.lci

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** lci chunk 08: Treasure, ward, and "entered this turn". */
class LciChunk08TreasureWardTest {
    private fun mayPlay(objects: Objects) = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(objects), Duration.EndOfTurn))
    private fun exileTop(count: Int) =
        Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(count), fromTop = true), Destination(Zone.Exile), postBind = "exiled"))
    private fun exiledAtLeastOne(filter: ObjectFilter) =
        Condition.Compare(Value.Count(Objects.Where(Objects.Bound("exiled"), filter)), Comparison.AtLeast, n(1))
    private val dinosaur = creatureToken("Dinosaur", listOf(Color.Red), 3, 1)
    private fun ward(mana: String) = Keyword.Ward(manaCost(mana))
    private val descended = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(permanentCard, cardFilter, ObjectFilter.OwnedBy(PlayerFilter.You)),
        to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)

    // CR 406.1, 601.2a: the two exiled cards may be played this turn; each "if" looks at what was exiled (CR 608.2c)
    @Test
    fun hoardmawDrake() = card(
        name = "Hoardmaw Drake",
        cost = "{3}{R}{R}",
        type = "Creature — Dinosaur Dragon",
        pt = "5/5",
        text = """
            Flying, first strike
            At the beginning of your upkeep, exile the top two cards of your library. You may play them this turn. If you exiled a land card this way, create a 3/1 red Dinosaur creature token. If you exiled a nonland card this way, create a Treasure token.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            exileTop(2),
            mayPlay(Objects.Bound("exiled")),
            ifThen(exiledAtLeastOne(land), create(dinosaur)),
            ifThen(exiledAtLeastOne(nonland), createTreasure())),
    )

    // CR 510.2, 603.2c: combat damage is dealt at once, so the first ability triggers once; CR 506.2: the active player is the attacking player
    @Test
    fun disputedTrophyOrb() = card(
        name = "Disputed Trophy Orb",
        cost = "{2}",
        type = "Artifact",
        text = """
            Whenever you're dealt combat damage, the attacking player gains control of this artifact and untaps it.
            {2}, {T}: Draw a card and put a point counter on this artifact. Then if it has five or more point counters on it, sacrifice it and create a Treasure token.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You), combat = true)), oneOrMore = true),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Active)), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            untapIt(Objects.Self)),
        activated(cost(mana("{2}"), tap),
            draw(1),
            counters(Objects.Self, 1, CounterKind.Named("point")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("point"), atLeast = 5)),
                sacrificeIt, createTreasure())),
    )

    // CR 700.11: descended this turn, checked as the step begins and as it resolves (CR 603.4)
    @Test
    fun delvingDeckhand() = card(
        name = "Delving Deckhand",
        cost = "{1}{R}",
        type = "Creature — Goblin Pirate",
        pt = "2/2",
        text = "At the beginning of your end step, if you descended this turn, create a Treasure token. (You descended if a permanent card was put into your graveyard from anywhere.)",
    ) parsesTo abilities(
        triggered(yourEndStep, createTreasure(), interveningIf = descended),
    )

    // CR 111.10a: a Treasure token
    @Test
    fun forebearsBoon() = card(
        name = "Forebears' Boon",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +2/+0 and gains first strike until end of turn.
            Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 0, has(Keyword.FirstStrike)), createTreasure(), targets = listOf(slot("creature", creature))),
    )

    // CR 700.2b, 700.2d: a mode chosen already this turn can't be chosen again; each Pirate's attack triggers it (CR 508.1m)
    @Test
    fun gritchGreedyCorsair() = card(
        name = "Gritch, Greedy Corsair",
        cost = "{2}{R}",
        type = "Legendary Creature — Goblin Pirate",
        pt = "3/3",
        text = """
            First strike
            Whenever a Pirate you control attacks, choose one that hasn't been chosen this turn —
            • Create a Treasure token.
            • Target creature can't block this turn.
            • Exile the top card of your library. You may play it this turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Ability.Triggered(on(EventPattern.Attack(and(sub("Pirate"), you))), Instructions(modes = Modes(
            options = listOf(
                mode(createTreasure()),
                mode(untilEndOfTurn(target("creature"), Mechanic.CantBlock(ObjectFilter.Any)), targets = listOf(slot("creature", creature))),
                mode(exileTop(1), mayPlay(Objects.Bound("exiled"))),
            ),
            onlyUnchosenThisTurn = true,
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )

    // CR 603.2c: one trigger for all the Dinosaurs attacking; "that many" counts them (CR 508.1m)
    @Test
    fun saurianWindfall() = card(
        name = "Saurian Windfall",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Whenever one or more Dinosaurs you control attack, create that many Treasure tokens.
            Whenever you cast an artifact spell, create a 3/1 red Dinosaur creature token. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(and(sub("Dinosaur"), you))), oneOrMore = true),
            createTreasure(count = Value.EventAmount)),
        triggered(youCast(artifact), create(dinosaur), perTurn = 1),
    )

    // CR 701.23a: "that player" is the destroyed land's controller, as it last existed (CR 608.2h)
    @Test
    fun brittleFissure() = card(
        name = "Brittle Fissure",
        cost = null,
        type = "Land — Cave",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this land: Destroy target nonbasic land an opponent controls. That player may search their library for a basic land card, put it onto the battlefield, then shuffle. You create a Treasure token.
        """,
    ) parsesTo run {
        val landController = Players.ControllerOf(target("land"))
        abilities(
            addOne("{C}"),
            activated(cost(mana("{1}"), tap, sacrificeSelf),
                destroy(target("land")),
                Step.May(player = landController, steps = listOf(
                    Step.Do(Effect.Search(landController, basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = landController)),
                    move(Objects.Bound("found"), Zone.Battlefield, controller = landController),
                    Step.Do(Effect.Shuffle(landController)))),
                createTreasure(),
                targets = listOf(slot("land", and(not(ObjectFilter.Super(Supertype.Basic)), land, opponents)))),
        )
    }

    // CR 601.2c: up to one target for each player; CR 205.1a: "become Treasure artifacts" replaces their card types and subtypes (layer 4),
    // they lose their other abilities and keep the granted one (layer 6, CR 613.1f), until this creature leaves (CR 611.2b)
    @Test
    fun skysailPilferer() = card(
        name = "Skysail Pilferer",
        cost = "{2}{U}",
        type = "Creature — Human Pirate",
        pt = "2/3",
        text = """
            Flying, ward {1}
            When this creature enters, for each player, choose up to one other target artifact or creature that player controls. For as long as this creature remains on the battlefield, the chosen permanents become Treasure artifacts with "{T}, Sacrifice this artifact: Add one mana of any color" and lose all other abilities.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        ward("{1}"),
        triggered(entersSelf,
            Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Treasure"))),
                    replaceTypes = true, replaceSubtypes = true),
                Mechanic.LoseAbilities(),
                Mechanic.GainAbilities(listOf(activated(cost(tap, sacrificeSelf), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))))),
            ), Affected.Objects(target("permanent")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(TargetSlot("permanent", TargetKind.Objects(and(another, or(artifact, creature), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer)))),
                min = n(0), max = n(1), perPlayer = PlayerFilter.Any))),
    )

    // CR 208.4b: base power ignores effects and counters that only modify power; each creature gets its own difference
    @Test
    fun regentItzalMoch() = card(
        name = "Regent Itzal Moch",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Cat Noble",
        pt = "3/4",
        text = """
            Ward {2}
            Whenever Regent Itzal Moch attacks, for each creature you control with power greater than that creature's base power, put a number of +1/+1 counters on that creature equal to the difference.
        """,
    ) parsesTo run {
        val each = Objects.Bound("creature")
        abilities(
            ward("{2}"),
            triggered(attacksSelf, Step.ForEach(Each.Objects(Objects.Each(and(creature, you,
                ObjectFilter.Power(Comparison.Greater, Value.Of(NumericProperty.BasePower, Objects.Tested, Combine.Single))))), bind = "creature", steps = listOf(
                counters(each, Value.Difference(Value.Of(NumericProperty.Power, each, Combine.Single), Value.Of(NumericProperty.BasePower, each, Combine.Single)))))),
        )
    }

    // CR 603.7a: the delayed trigger returns the exiled card, a new object (CR 400.7), under its owner's control
    @Test
    fun abuelitaEchoOfElders() = card(
        name = "Abuelita, Echo of Elders",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Spirit",
        pt = "2/2",
        text = """
            Flying, ward {2}
            {1}{W}{U}: Exile another target creature or artifact you control. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        ward("{2}"),
        activated(manaCost("{1}{W}{U}"),
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            targets = listOf(slot("permanent", and(another, or(creature, artifact), you)))),
    )

    // CR 601.2b, 601.2h: the sacrifice is an additional cost; CR 610.3: the exiled permanent returns when this artifact leaves
    @Test
    fun duskbloomReliquary() = card(
        name = "Duskbloom Reliquary",
        cost = "{W}",
        type = "Artifact",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            Ward {2}
            When this artifact enters, exile target artifact or creature an opponent controls until this artifact leaves the battlefield.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature))))),
        ward("{2}"),
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
    )

    // CR 702.21a: ward; CR 401.2: the bottom of its owner's library
    @Test
    fun driftstoneWanderer() = card(
        name = "Driftstone Wanderer",
        cost = "{5}",
        type = "Artifact Creature — Golem",
        pt = "2/5",
        text = """
            Flying
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            {2}: Put target card from a graveyard on the bottom of its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        ward("{2}"),
        activated(manaCost("{2}"), move(target("card"), Zone.Library, position = -1), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 505.1a: the first main phase is the precombat main phase
    @Test
    fun hulkingBramblesaur() = card(
        name = "Hulking Bramblesaur",
        cost = "{2}{G}{G}",
        type = "Creature — Dinosaur",
        pt = "5/3",
        text = """
            Ward {2}
            At the beginning of your first main phase, add {G}{G}.
        """,
    ) parsesTo abilities(
        ward("{2}"),
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}{G}")!!)), Players.You))),
    )

    // CR 614.12: the type is chosen as it enters; CR 603.2d: another creature of that type's triggered abilities trigger an additional time
    @Test
    fun wanderingDais() = card(
        name = "Wandering Dais",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        pt = "4/4",
        text = """
            Ward {2}
            As this creature enters, choose a creature type.
            This creature is the chosen type in addition to its other types.
            If a triggered ability of another creature you control of the chosen type triggers, it triggers an additional time.
        """,
    ) parsesTo abilities(
        ward("{2}"),
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))),
        )), affects = Affected.Objects(Objects.Self)),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
        static(Mechanic.TriggersAdditionalTime(null, and(another, creature, you, ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered("type"))))),
    )

    // CR 701.14a: the two fight; the counter only if the creature you control entered this turn (checked as it resolves, CR 608.2c)
    @Test
    fun jungleWarSigil() = card(
        name = "Jungle War Sigil",
        cost = "{G}",
        type = "Sorcery",
        text = "Choose target creature you control and target creature you don't control. If the creature you control entered this turn, put a +1/+1 counter on it. Then those creatures fight each other.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.ObjectMatches(target("creature"), ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield, objects = ObjectFilter.Any))),
                counters(target("creature"))),
            Step.Do(Effect.Fight(target("creature"), target("other creature"))),
            targets = listOf(
                slot("creature", creatureYouControl),
                slot("other creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )
}
