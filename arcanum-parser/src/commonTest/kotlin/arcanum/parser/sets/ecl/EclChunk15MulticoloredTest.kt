package arcanum.parser.sets.ecl

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 15: multicoloured and hybrid cards. Keyword counters (CR 122.1b) on a creature that lost its abilities, casting the
 * first nonland card exiled if its mana value is at most the life you gained (CR 601.2a), creature spells with toughness greater than power
 * costing less, the difference between power and toughness, opponents exiling until a total mana value (CR 608.2c), a look at the top four
 * with a card revealed (CR 701.20), a creature that levels up by becoming new creature types with base power and toughness and protection
 * from each opponent (CR 702.16j), a triggered ability copied (CR 707.10), cards exiled with a legend this turn cast for free once each
 * turn, conspire given to spells (CR 702.78), hexproof from each of its colours (CR 702.11d), all colours, and extra triggers (CR 603.2d).
 */
class EclChunk15MulticoloredTest {
    private val treasure = TokenSpec.Predefined("Treasure")
    private val entersOrDies = arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)))

    /** "look at the top four cards of your library. You may reveal a [kind] card from among them and put it into your hand. Put the rest on
     * the bottom of your library in a random order." */
    private fun lookAtFour(vararg kinds: String) = triggered(entersSelf,
        doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(or(*kinds.map { sub(it) }.toTypedArray()), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
        doing(Effect.Reveal(Objects.Bound("chosen"))),
        move(Objects.Bound("chosen"), Zone.Hand),
        doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))

    // CR 122.1b: keyword counters give the creature those abilities again after it lost all others
    @Test
    fun wrenArticulateFreshling() = card(
        name = "Wren, Articulate Freshling",
        cost = "{W/B}{W/B}",
        type = "Legendary Creature — Bird Bard",
        text = """
            Flying, first strike, lifelink
            When Wren enters, up to one other target creature loses all abilities. Put a flying counter, a first strike counter, and a lifelink counter on that creature.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        Keyword.Lifelink,
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.LoseAbilities()), arcanum.api.Affected.Objects(target("creature")), Duration.Indefinitely)),
            counters(target("creature"), 1, CounterKind.Keyword(Keyword.Flying.kind)),
            counters(target("creature"), 1, CounterKind.Keyword(Keyword.FirstStrike.kind)),
            counters(target("creature"), 1, CounterKind.Keyword(Keyword.Lifelink.kind)),
            targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    @Test
    fun hobgoblinHexsmith() = card(
        name = "Hobgoblin Hexsmith",
        cost = "{B}{R}",
        type = "Creature — Goblin Warlock",
        text = """
            Deathtouch
            Whenever another Goblin you control dies, this creature deals 1 damage to each opponent.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(on(EventPattern.ZoneChange(and(another, sub("Goblin"), you), from = Zone.Battlefield, to = Zone.Graveyard)), damage(1, eachOpponent)),
    )

    // CR 601.2a: cast as the trigger resolves, if its mana value is low enough; otherwise to your hand
    @Test
    fun haldOfClanBroadhand() = card(
        name = "Hald of Clan Broadhand",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Giant Warrior",
        text = """
            {1}{W}, {T}: Another target creature you control gains flying and lifelink until end of turn.
            At the beginning of your end step, if you gained life this turn, exile cards from the top of your library until you exile a nonland card. You may cast that card without paying its mana cost if the spell's mana value is less than or equal to the amount of life you gained this turn. Otherwise, put it into your hand.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), tap), gains(target("creature"), Keyword.Flying, Keyword.Lifelink), targets = listOf(slot("creature", and(another, creature, you)))),
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            doing(Effect.RevealUntil(nonland, Players.You, n(1), reveal = true, into = Zone.Exile, foundBind = "found")),
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("found"), PlayKind.Cast, withoutPaying = true, caster = Players.You,
                resultingSpell = ObjectFilter.ManaValue(Comparison.AtMost, Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount))))),
                payer = Players.You, bind = "cast"),
            Step.If(Condition.Not(Condition.Did("cast")), then = listOf(move(Objects.Bound("found"), Zone.Hand))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
    )

    // CR 601.2f; the difference is the greater less the smaller
    @Test
    fun oakhartWeatheredByAges() = card(
        name = "Oakhart, Weathered by Ages",
        cost = "{1}{W}{B}{G}",
        type = "Legendary Creature — Treefolk Druid",
        text = """
            Each creature spell you cast with toughness greater than its power costs {1} less to cast.
            Whenever a creature you control attacks or blocks, it gets +X/+X until end of turn, where X is the difference between its power and toughness.
        """,
        pt = "0/5",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(spellFilter, creature, you, ObjectFilter.Toughness(Comparison.Greater, power(Objects.Tested))),
            arcanum.api.CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1)))),
        run {
            val it = Objects.EventObject(EventRole.Object)
            val x = Value.Difference(Value.Max(listOf(power(it), toughness(it))), Value.Min(listOf(power(it), toughness(it))))
            triggered(arcanum.api.Trigger.On(listOf(EventPattern.Attack(creatureYouControl), EventPattern.Block(creatureYouControl, attacker = ObjectFilter.Any))),
                pumpBy(it, x, x))
        },
    )

    // CR 608.2c: one card at a time until the total is reached or the library is empty; each opponent at once
    @Test
    fun reverieReaping() = card(
        name = "Reverie Reaping",
        cost = "{5}{U/B}{U/B}",
        type = "Sorcery",
        text = "Each opponent exiles cards from the top of their library until they have exiled cards with total mana value 5 or greater this way. Until end of turn, you may cast cards exiled this way without paying their mana costs.",
    ) parsesTo abilities(
        run {
            val theirs = ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent")))
            spell(
                Step.ForEach(arcanum.api.Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", together = true, steps = listOf(Step.RepeatWhile(listOf(
                    move(Objects.LibraryCards(Players.Bound("opponent"), n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
                    doing(Effect.Remember("harvested", Objects.Bound("exiled")))),
                    Condition.And(listOf(
                        Condition.Compare(Value.Of(NumericProperty.ManaValue, Objects.Where(Objects.Remembered("harvested"), theirs), arcanum.api.Combine.Total),
                            Comparison.Less, n(5)),
                        Condition.Exists(and(inZone(Zone.Library), theirs), atLeast = 1)))))),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You)),
                    arcanum.api.Affected.Objects(Objects.Remembered("harvested")), Duration.EndOfTurn)))
        },
    )

    @Test
    fun shadowedHobgoblin() = card(
        name = "Shadowed Hobgoblin",
        cost = "{B/R}{B/R}{B/R}",
        type = "Creature — Goblin Scout",
        text = "When this creature enters, look at the top four cards of your library. You may reveal a Goblin, Swamp, or Mountain card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "2/3",
    ) parsesTo abilities(lookAtFour("Goblin", "Swamp", "Mountain"))

    @Test
    fun shadowedElf() = card(
        name = "Shadowed Elf",
        cost = "{B/G}{B/G}{B/G}",
        type = "Creature — Elf Scout",
        text = "When this creature enters, look at the top four cards of your library. You may reveal an Elf, Swamp, or Forest card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "3/2",
    ) parsesTo abilities(lookAtFour("Elf", "Swamp", "Forest"))

    @Test
    fun shadowedEmberkin() = card(
        name = "Shadowed Emberkin",
        cost = "{1}{U/R}{U/R}",
        type = "Creature — Elemental Scout",
        text = "When this creature enters, look at the top four cards of your library. You may reveal an Elemental, Island, or Mountain card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "1/4",
    ) parsesTo abilities(lookAtFour("Elemental", "Island", "Mountain"))

    @Test
    fun shadowedHamletfolk() = card(
        name = "Shadowed Hamletfolk",
        cost = "{G/W}{G/W}",
        type = "Creature — Kithkin Scout",
        text = "When this creature enters, look at the top four cards of your library. You may reveal a Kithkin, Forest, or Plains card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "2/1",
    ) parsesTo abilities(lookAtFour("Kithkin", "Forest", "Plains"))

    @Test
    fun shadowedShoalfolk() = card(
        name = "Shadowed Shoalfolk",
        cost = "{W/U}{W/U}{W/U}",
        type = "Creature — Merfolk Scout",
        text = "When this creature enters, look at the top four cards of your library. You may reveal a Merfolk, Plains, or Island card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
        pt = "2/3",
    ) parsesTo abilities(lookAtFour("Merfolk", "Plains", "Island"))

    // CR 205.1a: the new creature types replace the old ones; layer 7b; CR 702.16j: protection from each opponent
    @Test
    fun shapeOfLegend() = card(
        name = "Shape of Legend",
        cost = "{G/W}",
        type = "Creature — Kithkin",
        text = """
            {G/W}: This creature becomes a Kithkin Scout with base power and toughness 2/3.
            {1}{G/W}{G/W}: If this creature is a Scout, it becomes a Kithkin Soldier with base power and toughness 4/5.
            {3}{G/W}{G/W}{G/W}: If this creature is a Soldier, it becomes a Kithkin Avatar with base power and toughness 7/8 and protection from each of your opponents.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(manaCost("{G/W}"), becomes("Scout", 2, 3)),
        activated(manaCost("{1}{G/W}{G/W}"), Step.If(Condition.ObjectMatches(Objects.Self, sub("Scout")), then = listOf(becomes("Soldier", 4, 5)))),
        activated(manaCost("{3}{G/W}{G/W}{G/W}"), Step.If(Condition.ObjectMatches(Objects.Self, sub("Soldier")), then = listOf(
            becomes("Avatar", 7, 8, has(Keyword.Protection(opponents)))))),
    )

    private fun becomes(type: String, p: Int, t: Int, vararg also: Mechanic): Step = doing(Effect.ApplyMechanics(listOf(
        Mechanic.SetTypes(addSubtypes = listOf("Kithkin", type).map { SubtypeRef.Literal(Subtype(it)) }, replaceSubtypes = true),
        Mechanic.SetPowerToughness(n(p), n(t))) + also, arcanum.api.Affected.Objects(Objects.Self), Duration.Indefinitely))

    // one ability, two events
    @Test
    fun flickeringEmber() = card(
        name = "Flickering Ember",
        cost = "{1}{U/R}{U/R}",
        type = "Creature — Elemental Sorcerer",
        text = "When this creature enters and whenever you cast a spell with mana value 4 or greater, you may discard a card. If you do, draw a card.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.Cast(and(spellFilter, manaValue(Comparison.AtLeast, 4)), PlayerFilter.You))),
            *mayPay(discardACard, then = listOf(draw(1))).toTypedArray()),
    )

    // CR 707.10: a copy of a triggered ability on the Stack
    @Test
    fun dovinWatchfulFreshling() = card(
        name = "Dovin, Watchful Freshling",
        cost = "{1}{R/W}{R/W}",
        type = "Legendary Creature — Vampire Cleric",
        text = "Tap two untapped creatures you control: Copy target triggered ability you control. You may choose new targets for the copy. Activate only once each turn.",
        pt = "3/3",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(2)))),
            doing(Effect.CopySpell(target("ability"), Players.You, newTargets = true)),
            targets = listOf(slot("ability", and(ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility),
                ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Triggered), you))),
            rules = anyTime.copy(perTurn = 1)),
    )

    // CR 701.17a: a milled card put back on top
    @Test
    fun ferraFlawedBotanist() = card(
        name = "Ferra, Flawed Botanist",
        cost = "{B/G}{B/G}",
        type = "Legendary Creature — Elf Druid",
        text = """
            When Ferra enters, mill four cards, then you may put a creature or land card from among the milled cards on top of your library.
            {2}{B/G}{B/G}{B/G}, {T}, Discard a land card: Create a 1/1 black and green Worm creature token for each land card in your graveyard.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(entersSelf, mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(or(creature, land), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Library, position = 0)),
        activated(cost(mana("{2}{B/G}{B/G}{B/G}"), tap, CostPart.Discard(CostObjects.Choose(land, count = n(1)))),
            create(creatureToken("Worm", listOf(Color.Black, Color.Green), 1, 1), count(inYourGraveyard(land)))),
    )

    // CR 607.2a: the cards exiled with it; this turn only, once each turn, for free
    @Test
    fun sylvaineFaeAscendant() = card(
        name = "Sylvaine, Fae Ascendant",
        cost = "{2}{B}{G}{U}",
        type = "Legendary Creature — Elf Faerie Noble",
        text = """
            Flying
            Whenever Sylvaine or another Elf or Faerie you control enters, exile the top two cards of target opponent's library.
            Once each turn, you may cast a spell with mana value less than or equal to the number of Elves and Faeries you control from among cards exiled with Sylvaine this turn without paying its mana cost.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, or(sub("Elf"), sub("Faerie")), you))),
            move(Objects.LibraryCards(Players.Target("opponent"), n(2), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(targetOpponent)),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, perTurn = 1, holder = PlayerFilter.You,
            resultingSpell = ObjectFilter.ManaValue(Comparison.AtMost, count(and(or(sub("Elf"), sub("Faerie")), you)))),
            affects = Objects.Each(and(ObjectFilter.Is(Objects.Remembered("exiled")), inZone(Zone.Exile),
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Exile), role = EventRole.After)))),
    )

    @Test
    fun shoalfolkCloudswimmer() = card(
        name = "Shoalfolk Cloudswimmer",
        cost = "{3}{W/U}{W/U}",
        type = "Creature — Merfolk Soldier",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Flying, vigilance
            When this creature enters, create a 1/1 white and blue Merfolk creature token.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(entersSelf, create(merfolk)),
    )

    // CR 400.7: "another" than this card in the graveyard
    @Test
    fun velsynsDevotee() = card(
        name = "Velsyn's Devotee",
        cost = "{1}{B}{G}",
        type = "Creature — Elf Warrior",
        text = """
            Other Elves you control get +1/+1.
            When this creature dies, return another target Elf card from your graveyard to your hand.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.Each(and(another, sub("Elf"), you))),
        triggered(diesSelf, move(target("card"), Zone.Hand),
            targets = listOf(slot("card", and(not(ObjectFilter.Is(Objects.SelfAfter)), sub("Elf"), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    @Test
    fun nogglePilferer() = card(
        name = "Noggle Pilferer",
        cost = "{1}{R/G}{R/G}",
        type = "Creature — Noggle Rogue",
        text = """
            When this creature enters or dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersOrDies, create(treasure)),
    )

    // CR 702.78a: conspire on each noncreature spell you control
    @Test
    fun plunderingPlots() = card(
        name = "Plundering Plots",
        cost = "{3}{R}{G}",
        type = "Enchantment",
        text = "Each noncreature spell you cast has conspire. (As you cast a noncreature spell, you may tap two untapped creatures you control that share a color with it. When you do, copy it and you may choose new targets for the copy. A copy of a permanent spell becomes a token.)",
    ) parsesTo abilities(
        static(has(Keyword.Conspire), affects = Objects.Each(and(not(creature), spellFilter, you))),
    )

    @Test
    fun stolidThicketGuide() = card(
        name = "Stolid Thicket-Guide",
        cost = "{4}{B/G}",
        type = "Creature — Elf Druid",
        text = "{1}{B/G}, Exile this card from your graveyard: Create a 2/2 black and green Elf creature token. Activate only as a sorcery.",
        pt = "5/4",
    ) parsesTo abilities(
        activated(cost(mana("{1}{B/G}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(elf), rules = asSorcery),
    )

    // CR 702.11d: hexproof from each of the creature's own colours; layer 5: all colours
    @Test
    fun ressaThoughtfulFreshling() = card(
        name = "Ressa, Thoughtful Freshling",
        cost = "{1}{G/U}",
        type = "Legendary Creature — Gorgon Wizard",
        text = """
            Each other creature you control has hexproof from each of its colors.
            {T}: Target creature you control becomes all colors until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(has(Keyword.HexproofFrom(ObjectFilter.Shares(SharedQuality.Color, Objects.Self))), affects = Objects.Each(and(another, creature, you))),
        activated(cost(tap), untilEndOfTurn(target("creature"), Mechanic.SetColors(Color.all.map { ColorRef.Literal(it) })),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun mindweaveCaptain() = card(
        name = "Mindweave Captain",
        cost = "{G}{W}",
        type = "Creature — Kithkin Soldier",
        text = "Whenever this creature or another Kithkin you control enters, target creature you control gets +1/+1 and gains trample until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Kithkin"), you))), pump(target("creature"), 1, 1, has(Keyword.Trample)),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 603.2d
    @Test
    fun pairblazeWanderers() = card(
        name = "Pairblaze Wanderers",
        cost = "{2}{U}{R}",
        type = "Creature — Elemental Sorcerer",
        text = """
            Flying
            If a triggered ability of another Elemental you control triggers, it triggers an additional time.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.TriggersAdditionalTime(null, and(another, sub("Elemental"), you))),
    )

    @Test
    fun ravenousPageFlicker() = card(
        name = "Ravenous Page-Flicker",
        cost = "{U/B}{U/B}{U/B}",
        type = "Creature — Faerie Rogue",
        text = """
            Flying
            Whenever you cast a spell during an opponent's turn, you may pay 1 life. If you do, draw a card.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(ObjectFilter.Any), *mayPay(CostPart.PayLife(n(1)), then = listOf(draw(1))).toTypedArray(),
            triggersOnlyIf = Condition.Not(Condition.YourTurn)),
    )

    @Test
    fun cautiousGrower() = card(
        name = "Cautious Grower",
        cost = "{1}{G/W}{G/W}",
        type = "Creature — Kithkin Citizen",
        text = "At the beginning of your end step, if another creature entered the battlefield under your control this turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), doing(Effect.Surveil(Players.You, n(1))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(another, creature, you), to = Zone.Battlefield), atLeast = 1,
                measure = Measure.Events)),
    )
}
