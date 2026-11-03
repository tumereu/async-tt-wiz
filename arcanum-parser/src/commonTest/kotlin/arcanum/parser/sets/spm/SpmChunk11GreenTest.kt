package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 11: green cards: a cost reduction for targeting a Spider (CR 601.2f), a creature that loses all abilities and becomes a 4/4 Lizard, Spiders, Boars and other animals pumped, looking at the top five cards for creatures, returning a land with this card from the graveyard.
 */
class SpmChunk11GreenTest {

    @Test
    fun ezraHoltWeaveTotem() = card(
        name = "Ezra Holt, Weave Totem",
        cost = "{4}{G}",
        type = "Legendary Creature — Spider Human Advisor",
        pt = "3/5",
        text = """
            Reach
            At the beginning of combat on your turn, target Spider you control gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            untilEndOfTurn(target("Spider"), pt(2, 2)),
            targets = listOf(slot("Spider", and(sub("Spider"), you))),
        ),
    )

    @Test
    fun sproutMoreLimbs() = card(
        name = "Sprout More Limbs",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets a Spider.
            Target creature gets +4/+4 until end of turn.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(mana = ManaCost.parse("{1}")!!, times = n(1))),
            condition = Condition.ObjectMatches(objects = target("creature"), filter = sub("Spider")),
        ),
        spell(untilEndOfTurn(target("creature"), pt(4, 4)), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun basementHacker() = card(
        name = "Basement Hacker",
        cost = "{2}{G}",
        type = "Creature — Human Advisor",
        pt = "2/3",
        text = """
            {T}: Add one mana of any color.
            Web Support — {2}{G}, {T}: Put a +1/+1 counter on target Spider. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(
            cost(tap),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Choose(n(1))), players = Players.You)),
        ),
        Ability.Activated(
            cost = cost(mana("{2}{G}"), tap),
            instructions = Instructions(
                targets = listOf(slot("Spider", sub("Spider"))),
                steps = listOf(doing(Effect.PutCounters(objects = target("Spider"), kind = p1p1, amount = n(1)))),
            ),
            rules = asSorcery,
            abilityWord = "Web Support",
        ),
    )

    @Test
    fun kablam() = card(
        name = "Kablam!",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Put a +1/+1 counter on target creature you control. It fights target creature an opponent controls. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(
            doing(Effect.PutCounters(objects = target("creature"), kind = p1p1, amount = n(1))),
            doing(Effect.Fight(first = target("creature"), second = target("second creature"))),
            targets = listOf(slot("creature", and(creature, you)), slot("second creature", and(creature, opponents))),
        ),
    )

    @Test
    fun hunterSPanthers() = card(
        name = "Hunter's Panthers",
        cost = "{1}{G}",
        type = "Creature — Cat Villain",
        pt = "2/2",
        text = "{2}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.",
    ) parsesTo abilities(
        activated(
            cost(mana("{2}{G}")),
            untilEndOfTurn(Objects.Self, pt(2, 2)),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1),
        ),
    )

    @Test
    fun saurianDoctorSBlight() = card(
        name = "Saurian, Doctor's Blight",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Lizard Villain",
        pt = "5/5",
        text = """
            Trample
            Lizard Formula — When Saurian, Doctor's Blight enters, up to one other target creature loses all abilities and becomes a green Lizard creature with base power and toughness 4/4.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.LoseAbilities(),
                        Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green))),
                        Mechanic.SetTypes(
                            add = listOf(CardType.Creature),
                            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Lizard"))),
                            replaceTypes = true,
                            replaceSubtypes = true,
                        ),
                        Mechanic.SetPowerToughness(power = n(4), toughness = n(4)),
                    ),
                    affects = Affected.Objects(target("creature")),
                    duration = Duration.Indefinitely,
                )),
            targets = listOf(slot("creature", and(another, creature), min = 0)),
            abilityWord = "Lizard Formula",
        ),
    )

    @Test
    fun sewerSkulkLizards() = card(
        name = "Sewer Skulk Lizards",
        cost = "{1}{G}",
        type = "Creature — Lizard Villain",
        pt = "1/3",
        text = """
            Trample
            Whenever you cast a spell with mana value 4 or greater, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(
            on(EventPattern.Cast(
                    spell = and(
                        ObjectFilter.Category(ObjectCategory.Spell),
                        ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    ),
                    by = PlayerFilter.You,
                )),
            doing(Effect.PutCounters(objects = Objects.Self, kind = p1p1, amount = n(1))),
        ),
    )

    @Test
    fun snapshotsOfTheMaskedClimber() = card(
        name = "Snapshots of the Masked Climber",
        cost = "{2}{G}",
        type = "Artifact",
        text = """
            When this artifact enters, look at the top five cards of your library. You may reveal up to two creature cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.
            {1}, {T}, Sacrifice this artifact: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = n(5), fromTop = true),
                    players = Players.You,
                    bind = "seen",
                )),
            Step.Choose(
                choice = Choice.Objects(filter = creature, min = n(0), max = n(2), from = Objects.Bound("seen")),
                bind = "revealed",
                chooser = Players.You,
            ),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            doing(Effect.Move(objects = Objects.Bound("revealed"), to = Destination(Zone.Hand))),
            doing(Effect.Move(
                    objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("revealed")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                )),
        ),
        activated(
            cost(mana("{1}"), tap, CostPart.Sacrifice(CostObjects.Self)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Treasure"), count = n(1), controller = Players.You)),
        ),
    )

    @Test
    fun ringBrawler() = card(
        name = "Ring Brawler",
        cost = "{3}{G}",
        type = "Creature — Human Warrior Performer",
        pt = "4/4",
        text = """
            When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            This creature can't be blocked by more than one creature.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Treasure"), count = n(1), controller = Players.You)),
        ),
        static(Mechanic.MaximumBlockers(1)),
    )

    @Test
    fun irradiatedArachnid() = card(
        name = "Irradiated Arachnid",
        cost = "{G}",
        type = "Creature — Spider",
        pt = "1/1",
        text = """
            Reach, deathtouch
            Fateful Bite — {2}, Sacrifice this creature: Search your library for a Spider Hero card, reveal it, put it into your hand, then shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Deathtouch,
        Ability.Activated(
            cost = cost(mana("{2}"), CostPart.Sacrifice(CostObjects.Self)),
            instructions = Instructions(
                steps = listOf(
                    doing(Effect.Search(
                            library = Players.You,
                            filter = and(sub("Spider"), sub("Hero")),
                            max = n(1),
                            reveal = true,
                            zones = listOf(Zone.Library),
                            bind = "found",
                            searcher = Players.You,
                        )),
                    doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(Zone.Hand))),
                    doing(Effect.Shuffle(Players.You)),
                ),
            ),
            rules = asSorcery,
            abilityWord = "Fateful Bite",
        ),
    )

    @Test
    fun dunewalkerDriftingRogue() = card(
        name = "Dunewalker, Drifting Rogue",
        cost = "{1}{G}{G}",
        type = "Legendary Creature — Sand Elemental Villain",
        pt = "*/*",
        text = """
            Dunewalker's power and toughness are each equal to the number of lands you control.
            Dunewalker can't be blocked by creatures with power 2 or less.
            {3}{G}{G}: Return this card and target land card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.SetPowerToughness(power = Value.Count(Objects.Each(and(land, you))), toughness = Value.Count(Objects.Each(and(land, you)))),
            characteristicDefining = true,
        ),
        static(
            Mechanic.CantBeBlockedBy(and(creature, ObjectFilter.Power(comparison = Comparison.AtMost, value = n(2)))),
        ),
        activated(
            cost(mana("{3}{G}{G}")),
            doing(Effect.Move(
                    objects = Objects.Union(listOf(Objects.Self, target("card"))),
                    to = Destination(zone = Zone.Battlefield, tapped = true),
                )),
            targets = listOf(slot("card", and(land, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
            functionsIn = listOf(Zone.Graveyard),
        ),
    )

    @Test
    fun hamsterWebhogSnoutOfJustice() = card(
        name = "Hamster Webhog, Snout of Justice",
        cost = "{1}{G}",
        type = "Legendary Creature — Spider Boar Hero",
        pt = "2/2",
        text = """
            When Hamster Webhog enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Animal May-Ham — Other Spiders, Boars, Bats, Bears, Birds, Cats, Dogs, Frogs, Jackals, Lizards, Mice, Otters, Rabbits, Raccoons, Rats, Squirrels, Turtles, and Wolves you control get +1/+1.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(token = TokenSpec.Predefined("Food"), count = n(1), controller = Players.You)),
        ),
        Ability.Static(
            mechanics = listOf(pt(1, 1)),
            affects = Affected.Objects(
                Objects.Each(
                    and(
                        another,
                        or(
                            sub("Spider"),
                            sub("Boar"),
                            sub("Bat"),
                            sub("Bear"),
                            sub("Bird"),
                            sub("Cat"),
                            sub("Dog"),
                            sub("Frog"),
                            sub("Jackal"),
                            sub("Lizard"),
                            sub("Mouse"),
                            sub("Otter"),
                            sub("Rabbit"),
                            sub("Raccoon"),
                            sub("Rat"),
                            sub("Squirrel"),
                            sub("Turtle"),
                            sub("Wolf"),
                        ),
                        you,
                    ),
                ),
            ),
            abilityWord = "Animal May-Ham",
        ),
    )

    @Test
    fun encouragingGuardians() = card(
        name = "Encouraging Guardians",
        cost = "{2}{G}",
        type = "Creature — Human Citizen",
        pt = "3/3",
        text = "Tap two untapped creatures you control: Add one mana of any color.",
    ) parsesTo abilities(
        activated(
            cost(CostPart.TapObjects(CostObjects.Choose(filter = creature, count = n(2)))),
            doing(Effect.AddMana(mana = ManaProduction(ManaKind.Choose(n(1))), players = Players.You)),
        ),
    )

    @Test
    fun splendidPartnership() = card(
        name = "Splendid Partnership",
        cost = "{3}{G}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if you control a permanent with mana value 4 or greater.
            One or two target creatures you control each get +1/+0 until end of turn. They each deal damage equal to their power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        static(
            Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(mana = ManaCost.parse("{2}")!!, times = n(1))),
            condition = Condition.Exists(
                filter = and(
                    ObjectFilter.Category(ObjectCategory.Permanent),
                    ObjectFilter.ManaValue(comparison = Comparison.AtLeast, value = n(4)),
                    you,
                ),
                atLeast = 1,
            ),
        ),
        spell(
            untilEndOfTurn(target("creatures"), pt(1, 0)),
            Step.ForEach(
                each = Each.Objects(target("creatures")),
                bind = "source",
                steps = listOf(
                    doing(Effect.DealDamage(
                            to = Recipients(target("creature")),
                            amount = Value.Of(property = NumericProperty.Power, objects = Objects.Bound("source"), how = Combine.Single),
                            source = Objects.Bound("source"),
                        )),
                ),
            ),
            targets = listOf(slot("creatures", and(creature, you), max = 2), slot("creature", and(creature, opponents))),
        ),
    )

    @Test
    fun brickfaceClimb() = card(
        name = "Brickface Climb",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, create a 2/1 green Spider creature token with reach, then you gain 1 life for each Spider you control.
            Spiders you control get +1/+1 and can't be blocked by creatures with defender.
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Spider Token",
                            colorIndicator = listOf(Color.Green),
                            types = listOf(CardType.Creature),
                            subtypes = listOf(Subtype("Spider")),
                            power = PrintedNumber.Fixed(2),
                            toughness = PrintedNumber.Fixed(1),
                            abilities = listOf(Keyword.Reach),
                        ),
                    ),
                    count = n(1),
                    controller = Players.You,
                )),
            doing(Effect.GainLife(players = Players.You, amount = Value.Count(Objects.Each(and(sub("Spider"), you))))),
        ),
        static(
            pt(1, 1),
            Mechanic.CantBeBlockedBy(and(creature, ObjectFilter.HasKeyword(Keyword.Defender))),
            affects = Objects.Each(and(sub("Spider"), you)),
        ),
    )

    @Test
    fun loomOfFateAndBeing() = card(
        name = "Loom of Fate and Being",
        cost = "{6}{G}{G}",
        type = "Enchantment",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            At the beginning of combat on your turn, look at the top five cards of your library. You may put a creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.BeginningOfCombat, whose = PlayerFilter.You)),
            doing(Effect.LookAt(
                    objects = Objects.LibraryCards(of = Players.You, count = n(5), fromTop = true),
                    players = Players.You,
                    bind = "seen",
                )),
            Step.Choose(
                choice = Choice.Objects(filter = creature, min = n(0), max = n(1), from = Objects.Bound("seen")),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = Objects.Bound("chosen"), to = Destination(Zone.Battlefield))),
            doing(Effect.Move(
                    objects = Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("chosen")),
                    to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true),
                )),
        ),
    )

    @Test
    fun arachnosaurBoldBeast() = card(
        name = "Arachnosaur, Bold Beast",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Spider Dinosaur Hero",
        pt = "6/6",
        text = """
            Reach, trample
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Keyword.Ward(cost(mana("{2}"))),
    )
}
