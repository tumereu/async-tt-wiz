package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 14: affinity, Treasure makers, restricted mana, additional costs and cost changes, and modified creatures ignoring defender. */
class NeoChunk14AffinityTreasureCostsTest {
    private val modifiedYouControl = and(creature, you, ObjectFilter.Modified)
    private fun lessEach(filter: ObjectFilter) = CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Each(filter)))
    private val oneLess = CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1))
    private fun spellOf(type: ObjectFilter) = and(type, spellFilter, you)
    private fun addColorless(restriction: ObjectFilter? = null) = Effect.AddMana(
        ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!), restriction = restriction), Players.You)

    // CR 702.41a
    @Test
    fun realityHeist() = card(
        name = "Reality Mirage",
        cost = "{5}{U}{U}",
        type = "Instant",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Look at the top seven cards of your library. You may reveal up to two artifact cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = artifact, printed = "artifacts"),
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(of = Players.You, count = n(7), fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(artifact, min = n(0), max = n(2), from = Objects.Bound("seen")), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            doing(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(base = Objects.Bound("seen"), minus = Objects.Bound("revealed")),
                to = Destination(zone = Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 702.41a
    @Test
    fun skyBlessedSamurai() = card(
        name = "Heaven-Kissed Warrior",
        cost = "{6}{W}",
        type = "Enchantment Creature — Human Samurai",
        pt = "4/4",
        text = """
            Affinity for enchantments (This spell costs {1} less to cast for each enchantment you control.)
            Flying
        """,
    ) parsesTo abilities(
        Keyword.Affinity(forWhat = enchantment, printed = "enchantments"),
        Keyword.Flying,
    )

    // CR 700.4 (dies), 700.2 (modes), 111.10a (Treasure), 611.2a (until the end of your next turn)
    @Test
    fun atsushi() = card(
        name = "Kazuro, the Gilded Dawn",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "4/4",
        text = """
            Flying, trample
            When Kazuro dies, choose one —
            • Exile the top two cards of your library. Until the end of your next turn, you may play those cards.
            • Create three Treasure tokens.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        Ability.Triggered(diesSelf, Instructions(modes = modes(
            mode(
                doing(Effect.Move(Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), Destination(zone = Zone.Exile), postBind = "exiled")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
            ),
            mode(createTreasure(n(3))),
        ))),
    )

    // CR 111.10a, 602.5b (activation instructions), 700.4 (died this turn)
    @Test
    fun undercityScrounger() = card(
        name = "Gutter Scavenger",
        cost = "{2}{B}",
        type = "Artifact Creature — Human Rogue",
        pt = "1/4",
        text = "{T}: Create a Treasure token. Activate only if a creature died this turn. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(
        Ability.Activated(cost(tap), Instructions(steps = listOf(createTreasure())),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events))),
    )

    // CR 106.6: spent only on abilities (of any source) or artifact spells
    @Test
    fun automatedArtificer() = card(
        name = "Tinker Clockwright",
        cost = "{2}",
        type = "Artifact Creature — Artificer",
        pt = "1/3",
        text = "{T}: Add {C}. Spend this mana only to activate an ability or cast an artifact spell.",
    ) parsesTo abilities(
        activated(cost(tap), doing(addColorless(or(ObjectFilter.AbilityOf(kind = AbilityKind.Activated), and(spellFilter, artifact))))),
    )

    // CR 106.6, 205.3 (Pilot and Vehicle are subtypes), 602.5d
    @Test
    fun mechHangar() = card(
        name = "Gantry Bay",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Pilot or Vehicle spell.
            {3}, {T}: Target Vehicle becomes an artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(addColorless())),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(amount = n(1)),
            restriction = and(spellFilter, or(sub("Pilot"), sub("Vehicle")))), Players.You))),
        activated(cost(mana("{3}"), tap), doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
            Affected.Objects(target("Vehicle")), Duration.EndOfTurn)), targets = listOf(slot("Vehicle", sub("Vehicle")))),
    )

    // CR 118.12 (tap another untapped permanent as a cost; a cost's objects are the payer's own, as in the tap-two cost of BRO's Warband Elite), 701.26a, 701.9 (discard)
    @Test
    fun networkTerminal() = card(
        name = "Relay Switchboard",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            {1}, {T}, Tap another untapped artifact you control: Draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(amount = n(1))), Players.You))),
        activated(cost(mana("{1}"), tap, CostPart.TapObjects(CostObjects.Choose(and(another, artifact), count = n(1)))), draw(1), discard(1)),
    )

    // CR 608.2c: each sentence's condition is checked as it resolves (CR 109.4: you control)
    @Test
    fun roadsideReliquary() = card(
        name = "Wayside Cache",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Sacrifice this land: Draw a card if you control an artifact. Draw a card if you control an enchantment.
        """,
    ) parsesTo abilities(
        addMana("{C}"),
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            ifThen(Condition.Exists(and(artifact, you), atLeast = 1), draw(1)),
            ifThen(Condition.Exists(and(enchantment, you), atLeast = 1), draw(1))),
    )

    // CR 601.2b, 118.8, 118.9 (alternative additional costs: tap an untapped artifact you control or pay {1})
    @Test
    fun disruptionProtocol() = card(
        name = "Signal Jammer",
        cost = "{U}{U}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, tap an untapped artifact you control or pay {1}.
            Counter target spell.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(
            cost(CostPart.TapObjects(CostObjects.Choose(artifact, count = n(1)))),
            cost(mana("{1}")),
        ))))),
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", spellFilter))),
    )

    private fun marchCards(color: Color) = Mechanic.AdditionalCost(cost = cost(CostPart.Exile(
        what = CostObjects.Choose(ObjectFilter.OfColor(color), count = null), from = Zone.Hand, postBind = "exiled")))
    private val marchLess = Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self),
        change = CostChange.Less(ManaCost.parse("{2}")!!, times = Value.Count(Objects.Bound("exiled"))))

    // CR 601.2b (optional additional cost, any number), 118.7 (cost reduction), 107.3 (X)
    @Test
    fun marchOfOtherworldlyLight() = card(
        name = "Procession of Bright Dawn",
        cost = "{X}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Exile target artifact, creature, or enchantment with mana value X or less.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(marchCards(Color.White), marchLess), affects = Affected.Objects(Objects.Self)),
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(slot("permanent", and(or(artifact, creature, enchantment), ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X"))))),
            steps = listOf(Step.Do(Effect.Move(target("permanent"), to = Destination(Zone.Exile)))),
        )),
    )

    // CR 601.2b, 118.7, 120.3 (damage), 119.3 (gain life), 107.3 (X)
    @Test
    fun marchOfWretchedSorrow() = card(
        name = "Procession of Grim Dusk",
        cost = "{X}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of black cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Procession of Grim Dusk deals X damage to target creature or planeswalker and you gain X life.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(marchCards(Color.Black), marchLess), affects = Affected.Objects(Objects.Self)),
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
            steps = listOf(
                damage(Value.Announced(name = "X"), toObjects(target("permanent"))),
                Step.Do(Effect.GainLife(Players.You, Value.Announced(name = "X"))),
            ),
        )),
    )

    // CR 601.2b, 608.2h (the sacrificed permanent's mana value as it last existed)
    @Test
    fun reckonerSBargain() = card(
        name = "Pawnbroker's Gambit",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature.
            You gain life equal to the sacrificed permanent's mana value. Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(or(artifact, creature), preBind = "sacrificed")))),
        spell(Step.Do(Effect.GainLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single))), draw(2)),
    )

    // CR 702.3b (defender), 700.9 (modified), 508.1a
    @Test
    fun guardiansOfOboro() = card(
        name = "Wardens of Hakone",
        cost = "{2}{U}",
        type = "Creature — Moonfolk Samurai",
        pt = "3/4",
        text = """
            Defender
            Modified creatures you control can attack as though they didn't have defender. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(Mechanic.CanAttackDespiteDefender, affects = Objects.Each(modifiedYouControl)),
    )

    // CR 118.7, 101.1: only the controller's artifact spells
    @Test
    fun enthusiasticMechanaut() = card(
        name = "Eager Tinkerer",
        cost = "{U}{R}",
        type = "Artifact Creature — Goblin Artificer",
        pt = "2/2",
        text = """
            Flying
            Artifact spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.CostChange(what = spellOf(artifact), change = oneLess)), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun jukaiNaturalist() = card(
        name = "Grove Scholar",
        cost = "{G}{W}",
        type = "Enchantment Creature — Human Monk",
        pt = "2/2",
        text = """
            Lifelink
            Enchantment spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Static(listOf(Mechanic.CostChange(what = spellOf(enchantment), change = oneLess)), affects = Affected.Objects(Objects.Self)),
    )

    // CR 118.7, 700.9
    @Test
    fun walkingSkyscraper() = card(
        name = "Strolling Spire",
        cost = "{8}",
        type = "Artifact Creature — Construct",
        pt = "8/8",
        text = """
            This spell costs {1} less to cast for each modified creature you control. (Equipment, Auras you control, and counters are modifications.)
            Trample
            This creature has hexproof as long as it's untapped. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = lessEach(modifiedYouControl), minimum = 0)),
            affects = Affected.Objects(Objects.Self)),
        Keyword.Trample,
        static(has(Keyword.Hexproof), condition = Condition.ObjectMatches(Objects.Self, not(tapped))),
    )
}
