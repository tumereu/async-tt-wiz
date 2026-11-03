package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 11: blue cards. Auras that tap and lock a creature ("can't become untapped and can't have counters put on it",
 * a replacement that skips the event, CR 614.1a), counting what was countered (CR 701.6), flash with a condition (CR 702.8a), hexproof
 * while untapped, spells with mana value 4 or greater cast, every nonland permanent becoming a copy (CR 707.2, layer 1), targets with
 * different controllers, an X cost reduction (CR 601.2f), "if you cast it" (CR 603.4), the owner choosing a library position (CR
 * 401.4), a discard unless a creature card is discarded (CR 118.12), spells cast during an opponent's turn, and cards returned "this
 * way" counted for tokens.
 */
class EclChunk11BlueTest {
    private fun ownerChooses(target: Objects, first: String, firstPosition: Int, second: String, secondPosition: Int): List<Step> = listOf(
        Step.Choose(Choice.Option(listOf(first, second)), bind = "position", chooser = Players.OwnerOf(target)),
        Step.If(Condition.Chose("position", first), then = listOf(move(target, Zone.Library, position = firstPosition)),
            otherwise = listOf(move(target, Zone.Library, position = secondPosition))))

    @Test
    fun tidewrightsBulwark() = card(
        name = "Tidewright's Bulwark",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature you control
            When this Aura enters, enchanted creature gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
            Enchanted creature gets +1/+2.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        triggered(entersSelf, gains(Objects.AttachedTo(Objects.Self), Keyword.Hexproof)),
        static(pt(1, 2), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 614.1a: counters that would be put on it aren't
    @Test
    fun petalbind() = card(
        name = "Petalbind",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature can't become untapped and can't have counters put on it.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(Objects.AttachedTo(Objects.Self))),
        static(Mechanic.CantBecomeUntapped, affects = Objects.AttachedTo(Objects.Self)),
        static(Mechanic.Replacement(EventPattern.CountersPut(ObjectFilter.Is(Objects.AttachedTo(Objects.Self))), modify = listOf(Modification.Skip))),
    )

    @Test
    fun upsetterOfTides() = card(
        name = "Upsetter of Tides",
        cost = "{3}{U}{U}",
        type = "Creature — Merfolk Wizard",
        text = """
            Flash
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            When this creature enters, return up to one other target nonland permanent to its owner's hand.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Convoke,
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0))),
    )

    // CR 700.2: a triggered ability with modes
    @Test
    fun glamourmite() = card(
        name = "Glamourmite",
        cost = "{2}{U}",
        type = "Creature — Faerie Rogue",
        text = """
            Flash
            Flying
            When this creature enters, choose one —
            • Tap target creature.
            • Untap target creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            mode(tapIt(target("creature")), targets = listOf(slot("creature", creature))),
            mode(untapIt(target("creature")), targets = listOf(slot("creature", creature)))), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 701.6a: what stays on the Stack wasn't countered
    @Test
    fun valeSylvanesRebuke() = card(
        name = "Vale Sylvane's Rebuke",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Counter all spells your opponents control and all abilities your opponents control. Create a 1/1 blue and black Faerie creature token with flying for each spell and ability countered this way.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        run {
            val countered = Objects.Bound("countered")
            spell(Step.Bind(Objects.Each(and(or(spellFilter, ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility)), opponents)), "countered"),
                doing(Effect.Counter(countered)),
                create(faerie, Value.Difference(Value.Count(countered), Value.Count(Objects.Where(countered, inZone(Zone.Stack))))))
        },
    )

    // CR 702.8a: as though it had flash, while you control a Faerie
    @Test
    fun phantasmWeavers() = card(
        name = "Phantasm Weavers",
        cost = "{4}{U}",
        type = "Creature — Faerie Wizard",
        text = """
            You may cast this spell as though it had flash if you control a Faerie.
            Flying
            This creature has hexproof as long as it's untapped. (It can't be the target of spells or abilities your opponents control.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        static(Mechanic.InstantSpeed, condition = Condition.Exists(and(sub("Faerie"), you), atLeast = 1)),
        Keyword.Flying,
        static(has(Keyword.Hexproof), condition = Condition.ObjectMatches(Objects.Self, not(tapped))),
    )

    @Test
    fun emberkinSeer() = card(
        name = "Emberkin Seer",
        cost = "{2}{U}",
        type = "Creature — Elemental Wizard",
        text = "Whenever you cast a spell with mana value 4 or greater, this creature gets +2/+0 and gains vigilance until end of turn.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(youCast(and(spellFilter, manaValue(Comparison.AtLeast, 4))), pump(Objects.Self, 2, 0, has(Keyword.Vigilance))),
    )

    @Test
    fun soaringVisions() = card(
        name = "Soaring Visions",
        cost = "{3}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Enchant creature
            When this Aura enters, draw a card.
            Enchanted creature gets +2/+2 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, draw(1)),
        static(pt(2, 2), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
    )

    // CR 707.2, 613.2a: layer 1, for good
    @Test
    fun mirrorshape() = card(
        name = "Mirrorshape",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = "Each nonland permanent you control becomes a copy of target non-Aura permanent.",
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("permanent"))), arcanum.api.Affected.Objects(Objects.Each(and(nonland, permanentFilter, you))),
            Duration.Indefinitely)), targets = listOf(slot("permanent", and(not(sub("Aura")), permanentFilter)))),
    )

    @Test
    fun frostkinHermit() = card(
        name = "Frostkin Hermit",
        cost = "{2}{U}",
        type = "Creature — Elemental Wizard",
        text = "When this creature enters, return up to one other target creature to its owner's hand.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("creature"), Zone.Hand), targets = listOf(slot("creature", and(another, creature), min = 0))),
    )

    // CR 601.2c: the second target's controller differs from the first's
    @Test
    fun fleeAsOne() = card(
        name = "Flee As One",
        cost = "{1}{U}",
        type = "Instant",
        text = "Choose two target creatures controlled by different players. Return those creatures to their owners' hands.",
    ) parsesTo abilities(
        spell(move(Objects.Union(listOf(target("creature"), target("other creature"))), Zone.Hand), targets = listOf(
            slot("creature", creature),
            TargetSlot("other creature", TargetKind.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.Is(Players.ControllerOf(target("creature"))))))),
                min = n(1), max = n(1), distinctFrom = listOf("creature")))),
    )

    @Test
    fun charmTrap() = card(
        name = "Charm Trap",
        cost = "{U}",
        type = "Instant",
        text = "Counter target spell with mana value 2.",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), targets = listOf(slot("spell", and(spellFilter, manaValue(Comparison.Equal, 2))))),
    )

    // CR 702.29e: basic landcycling
    @Test
    fun highcloudGlider() = card(
        name = "Highcloud Glider",
        cost = "{4}{U}",
        type = "Creature — Elemental",
        text = """
            Flying
            When this creature enters, target creature gains flying until end of turn.
            Basic landcycling {1}{U} ({1}{U}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creature))),
        Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{1}{U}")),
    )

    @Test
    fun peakWatchman() = card(
        name = "Peak Watchman",
        cost = "{1}{U}",
        type = "Creature — Elemental Soldier",
        text = "When this creature dies, draw a card.",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(diesSelf, draw(1)),
    )

    // CR 601.2f: {1} less for each, up to the generic part; CR 603.4: "if you cast it"
    @Test
    fun splitflock() = card(
        name = "Splitflock",
        cost = "{7}{U}{U}",
        type = "Creature — Elemental",
        text = """
            This spell costs {X} less to cast, where X is the greatest mana value among Elementals you control.
            Flying
            When this creature enters, if you cast it, return all non-Elemental creatures to their owners' hands.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        costsLess("{1}", Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Each(and(sub("Elemental"), you)), arcanum.api.Combine.Greatest)),
        Keyword.Flying,
        triggered(entersSelf, move(Objects.Each(and(not(sub("Elemental")), creature)), Zone.Hand), interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 401.4: the owner chooses the top or the bottom
    @Test
    fun brushAside() = card(
        name = "Brush Aside",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if a creature is attacking you.
            The owner of target spell or creature puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        costsLess("{2}", n(1), condition = Condition.Exists(and(creature, attacking,
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You))), atLeast = 1)),
        spell(*ownerChooses(target("permanent"), "top", 0, "bottom", -1).toTypedArray(), targets = listOf(slot("permanent", or(spellFilter, creature)))),
    )

    @Test
    fun velmorFrostcaller() = card(
        name = "Velmor Frostcaller",
        cost = "{3}{U}",
        type = "Creature — Elemental Wizard",
        text = "Whenever you cast a spell with mana value 4 or greater, draw a card.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(youCast(and(spellFilter, manaValue(Comparison.AtLeast, 4))), draw(1)),
    )

    @Test
    fun chronoRinse() = card(
        name = "Chrono Rinse",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            The owner of target nonland permanent puts it into their library second from the top or on the bottom.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(*ownerChooses(target("permanent"), "second from the top", 1, "bottom", -1).toTypedArray(),
            targets = listOf(slot("permanent", and(nonland, permanentFilter)))),
    )

    // CR 118.12: discarding a creature card instead of two cards
    @Test
    fun cravingForSelf() = card(
        name = "Craving for Self",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw three cards. Then discard two cards unless you discard a creature card.",
    ) parsesTo abilities(
        spell(draw(3), Step.Pay(cost(CostPart.Discard(CostObjects.Choose(creature, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(discard(2)))),
    )

    @Test
    fun suddenSuccor() = card(
        name = "Sudden Succor",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Draw three cards, then discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(draw(3), discard(1)),
    )

    // CR 603.2: "during an opponent's turn" is checked as it would trigger
    @Test
    fun uninvitedPixie() = card(
        name = "Uninvited Pixie",
        cost = "{1}{U}",
        type = "Creature — Faerie Rogue",
        text = """
            Flying
            Whenever you cast a spell during an opponent's turn, surveil 2. (Look at the top two cards of your library. You may put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(ObjectFilter.Any), doing(Effect.Surveil(Players.You, n(2))), triggersOnlyIf = Condition.Not(Condition.YourTurn)),
    )

    // CR 400.7: the permanents returned, as cards in their owners' hands
    @Test
    fun driftwineParting() = card(
        name = "Driftwine Parting",
        cost = "{5}{U}{U}",
        type = "Kindred Sorcery — Merfolk",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Return one or two target nonland permanents to their owners' hands. Then if you control a Merfolk, create a 1/1 white and blue Merfolk creature token for each permanent returned to its owner's hand this way.
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        spell(move(target("permanents"), Zone.Hand, postBind = "returned"),
            Step.If(Condition.Exists(and(sub("Merfolk"), you), atLeast = 1), then = listOf(
                create(merfolk, count(ObjectFilter.Any).let { Value.Count(Objects.Where(Objects.Bound("returned"), inZone(Zone.Hand))) }))),
            targets = listOf(slot("permanents", and(nonland, permanentFilter), min = 1, max = 2))),
    )
}
