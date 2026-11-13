package arcanum.parser.sets.one

import arcanum.api.*
import arcanum.parser.cube.*
import kotlin.test.Test

/**
 * Chunk 14: Vehicles with oil counters and crew (CR 301.7, 702.122, 122.1), ward (CR 702.21), "equipped" conditions (CR 301.5),
 * mana abilities limited to a land that just entered (CR 605.1a), additional costs (CR 118.8, 601.2b), "can't be blocked by"
 * (CR 509.1b), cost reductions and increases that read the spell's target or the board (CR 601.2f), countering by mana value
 * (CR 701.6, 202.3) and Mite tokens (CR 111.1, 702.164).
 */
class OneChunk14VehiclesCostsAndTokensTest {
    private val oil = CounterKind.Named("oil")
    private val toxic = Keyword.Toxic(1)
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
    /** 'a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."' */
    private val mite = creatureToken("Phyrexian Mite", emptyList(), 1, 1, abilities = listOf(toxic, cantBlock),
        types = listOf(CardType.Artifact, CardType.Creature))
    private val goblin = creatureToken("Phyrexian Goblin", listOf(Color.Red), 1, 1)
    private val hasOil = ObjectFilter.HasCounter(oil, atLeast = 1)
    private fun diesFilter(filter: ObjectFilter) = Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))

    private val entersWithOil = Ability.Static(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersWithCounters(oil, n(1))))), affects = Affected.Objects(Objects.Self))
    private val becomesArtifactCreature = untilEndOfTurn(Objects.Self,
        Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))

    // CR 614.1c, 122.1, 702.122a, 301.7: the Vehicle becomes a creature by crew or by removing an oil counter
    @Test
    fun meldwebStrider() = card(
        name = "Meldfield Strider",
        cost = "{4}{U}",
        type = "Artifact — Vehicle",
        pt = "5/5",
        text = """
            Vigilance
            This Vehicle enters with an oil counter on it.
            Remove an oil counter from this Vehicle: It becomes an artifact creature until end of turn.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        entersWithOil,
        activated(cost(CostPart.RemoveCounters(oil, n(1), CostObjects.Self)), becomesArtifactCreature),
        Keyword.Crew(3),
    )

    // CR 702.164a: toxic; CR 702.122a
    @Test
    fun ribskiff() = card(
        name = "Ribcrest",
        cost = "{4}",
        type = "Artifact — Vehicle",
        pt = "4/4",
        text = """
            Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
            When this Vehicle enters, draw a card.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Toxic(2),
        triggered(entersSelf, draw(1)),
        Keyword.Crew(3),
    )

    // CR 702.21a: ward; CR 301.5: equipped is attached by an Equipment
    @Test
    fun leoninLightbringer() = card(
        name = "Leonin Lampbearer",
        cost = "{2}{W}",
        type = "Creature — Cat Rebel",
        pt = "3/2",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            As long as this creature is equipped, it gets +1/+1.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        static(pt(1, 1), condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(attachments = equipment))),
    )

    // CR 605.1a, 400.7: a mana ability only while the land entered this turn
    @Test
    fun mirrex() = card(
        name = "Mirrow",
        cost = null,
        type = "Land — Sphere",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Activate only if this land entered this turn.
            {3}, {T}: Create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block." (Players dealt combat damage by it also get a poison counter.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)),
            rules = anyTime.copy(condition = Condition.ObjectMatches(Objects.Self,
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield))))),
        activated(cost(mana("{3}"), tap), create(mite, bind = "token")),
    )

    // CR 707.2, 107.3: X is paid and read by the target's mana value
    @Test
    fun theMycosynthGardens() = card(
        name = "The Mycosynth Hollows",
        cost = null,
        type = "Land — Sphere",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
            {X}, {T}: This land becomes a copy of target nontoken artifact you control with mana value X.
        """,
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
        Ability.Activated(cost(mana("{X}"), tap), Instructions(
            announced = listOf("X"),
            targets = listOf(slot("artifact", and(not(token), artifact, you, ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("artifact"))), Affected.Objects(Objects.Self), Duration.Indefinitely)))),
            anyTime),
    )

    // CR 118.8, 601.2b: pay {4} or sacrifice an artifact or creature
    @Test
    fun annihilatingGlare() = card(
        name = "Annihilating Gaze",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, pay {4} or sacrifice an artifact or creature.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(manaCost("{4}"), cost(sacrificeA(or(artifact, creature)))))))),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 107.3, 601.2b, 601.2h: the sacrificed permanent's mana value is X; X damage is divided among any number of creatures (CR 601.2d)
    @Test
    fun nahirisSacrifice() = card(
        name = "Ishmar's Sacrifice",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or creature with mana value X.
            Ishmar's Sacrifice deals X damage divided as you choose among any number of target creatures.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(and(or(artifact, creature), ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))))),
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(slot("creatures", creature, min = 0, max = null).copy(divided = Value.Announced("X"))),
            steps = listOf(damage(Value.DividedShare, toObjects(target("creatures")))))),
    )

    // CR 118.8: discarding a card is an additional cost
    @Test
    fun thrillOfPossibility() = card(
        name = "Thrill of Fortune",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(discardACard))),
        spell(draw(2)),
    )

    // CR 509.1b: can't be blocked by creatures with flying
    @Test
    fun skyscytheEngulfer() = card(
        name = "Skyscorch Engulfer",
        cost = "{5}{G}",
        type = "Creature — Phyrexian Beast",
        pt = "6/5",
        text = """
            Reach, trample
            This creature can't be blocked by creatures with flying.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        static(Mechanic.CantBeBlockedBy(and(creature, kw(Keyword.Flying.kind)))),
    )

    // CR 601.2f: {1} less for each permanent you control with an oil counter; CR 120.3: damage to each creature opponents control
    @Test
    fun cinderslashRavager() = card(
        name = "Cinderscorch Ravager",
        cost = "{4}{R}{G}",
        type = "Creature — Phyrexian Warrior",
        pt = "5/5",
        text = """
            This spell costs {1} less to cast for each permanent you control with oil counters on it.
            Vigilance
            When this creature enters, it deals 1 damage to each creature your opponents control.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(and(permanentFilter, you, hasOil))))),
        Keyword.Vigilance,
        triggered(entersSelf, damage(1, toObjects(Objects.Each(and(creature, opponents))))),
    )

    // CR 601.2f: {3} more when the spell targets a creature (the spell's own target, read as it is cast)
    @Test
    fun vanishIntoEternity() = card(
        name = "Vanish into Aeons",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            This spell costs {3} more to cast if it targets a creature.
            Exile target nonland permanent.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.More(ManaCost.parse("{3}")!!, Value.Fixed(1)))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Targets(Objects.Each(creature))),
        ),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 701.6, 202.3: counter a spell with mana value 1 or less
    @Test
    fun minorMisstep() = card(
        name = "Minor Misdeed",
        cost = "{U}",
        type = "Instant",
        text = "Counter target spell with mana value 1 or less.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.AtMost, 1))))),
    )

    // CR 111.1, 702.164: two Mite tokens
    @Test
    fun basilicaShepherd() = card(
        name = "Basilica Herdsman",
        cost = "{3}{W}{W}",
        type = "Creature — Phyrexian Angel",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, create two 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block." (Players dealt combat damage by them also get a poison counter.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(mite, n(2), bind = "token")),
    )

    // CR 700.4, 614.1c, 122.1; CR 406.3 / 601.2: an exiled card may be played this turn
    @Test
    fun charforger() = card(
        name = "Cinderforger",
        cost = "{1}{B}{R}",
        type = "Creature — Phyrexian Beast",
        pt = "2/3",
        text = """
            When this creature enters, create a 1/1 red Phyrexian Goblin creature token.
            Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
            Remove three oil counters from this creature: Exile the top card of your library. You may play that card this turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(goblin)),
        triggered(diesFilter(and(another, or(creature, artifact), you)), counters(Objects.Self, 1, oil)),
        activated(cost(CostPart.RemoveCounters(oil, n(3), CostObjects.Self)),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You))),
    )

    @Test
    fun chimneyRabble() = card(
        name = "Chimney Scrabbler",
        cost = "{3}{R}",
        type = "Creature — Phyrexian Goblin Warrior",
        pt = "3/3",
        text = """
            Haste
            When this creature enters, create a 1/1 red Phyrexian Goblin creature token.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf, create(goblin)),
    )
}
