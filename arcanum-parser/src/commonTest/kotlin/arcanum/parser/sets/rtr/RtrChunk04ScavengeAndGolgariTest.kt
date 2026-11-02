package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
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
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.another
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.counters
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.enchantment
import arcanum.parser.sets.rtr.Rtr.gainLife
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.nonland
import arcanum.parser.sets.rtr.Rtr.p1p1
import arcanum.parser.sets.rtr.Rtr.permanent
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.regenerate
import arcanum.parser.sets.rtr.Rtr.scavenge
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.sub
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.you
import kotlin.test.Test

/**
 * Return to Ravnica chunk 04: scavenge (CR 702.97a: from the graveyard, exile it as a cost for +1/+1 counters equal to its
 * power on target creature, as a sorcery) with other keywords, and the Golgari cards: regeneration (CR 701.19a) for one or
 * every creature, a "must be blocked by all" lure (CR 509.1c), a doubled counter replacement, values of the sacrificed
 * creature as it last existed (CR 608.2h), two different targets ("another target creature", CR 115.3), a search split
 * between hand and graveyard.
 */
class RtrChunk04ScavengeAndGolgariTest {
    private val creatureCardInYourGraveyard = all(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))

    // ---- Scavenge (CR 702.97a)

    /** Landwalk (CR 702.14c) and scavenge: two keywords, each its own meaning. */
    @Test
    fun gutterLurcher() = card(
        name = "Gutter Lurcher",
        cost = "{2}{B}",
        type = "Creature — Zombie",
        pt = "3/1",
        text = """
            Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
            Scavenge {2}{B} ({2}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Landwalk(sub("Swamp"), printed = "Swampwalk"),
        scavenge("{2}{B}"),
    )

    @Test
    fun mudcryptWurm() = card(
        name = "Mudcrypt Wurm",
        cost = "{6}{B}",
        type = "Creature — Zombie Wurm",
        pt = "6/6",
        text = "Scavenge {6}{B} ({6}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)",
    ) parsesTo abilities(
        scavenge("{6}{B}"),
    )

    @Test
    fun tombSwarmLocust() = card(
        name = "Tomb Swarm Locust",
        cost = "{5}{B}",
        type = "Creature — Insect",
        pt = "3/3",
        text = """
            Flying
            Scavenge {2}{B}{B} ({2}{B}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        scavenge("{2}{B}{B}"),
    )

    @Test
    fun rotspanBehemoth() = card(
        name = "Rotspan Behemoth",
        cost = "{2}{G}{G}",
        type = "Creature — Insect",
        pt = "6/2",
        text = "Scavenge {4}{G}{G} ({4}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)",
    ) parsesTo abilities(
        scavenge("{4}{G}{G}"),
    )

    @Test
    fun toilingScarab() = card(
        name = "Toiling Scarab",
        cost = "{1}{G}",
        type = "Creature — Insect",
        pt = "2/2",
        text = "Scavenge {5}{G} ({5}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)",
    ) parsesTo abilities(
        scavenge("{5}{G}"),
    )

    /** CR 509.1c: a requirement on every creature that could block it. */
    @Test
    fun rotFarmLure() = card(
        name = "Rot Farm Lure",
        cost = "{3}{G}",
        type = "Creature — Elf Rogue",
        pt = "2/2",
        text = """
            All creatures able to block this creature do so.
            Scavenge {3}{G}{G} ({3}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(Objects.Each(creature), Mechanic.MustBlock(ObjectFilter.Is(Objects.Self))),
        scavenge("{3}{G}{G}"),
    )

    @Test
    fun swampfenMonitor() = card(
        name = "Swampfen Monitor",
        cost = "{2}{G}{G}",
        type = "Creature — Lizard",
        pt = "3/3",
        text = """
            Trample
            Scavenge {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        scavenge("{5}{G}{G}"),
    )

    @Test
    fun siltMauler() = card(
        name = "Silt Mauler",
        cost = "{1}{B}{G}",
        type = "Creature — Plant Zombie",
        pt = "2/1",
        text = """
            Haste
            Scavenge {3}{B}{G} ({3}{B}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        scavenge("{3}{B}{G}"),
    )

    /** Scavenge {0}: only exiling the card is paid. */
    @Test
    fun creepingSprout() = card(
        name = "Creeping Sprout",
        cost = "{B/G}",
        type = "Creature — Plant Zombie",
        pt = "1/1",
        text = "Scavenge {0} ({0}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)",
    ) parsesTo abilities(
        scavenge("{0}"),
    )

    @Test
    fun drainageStinger() = card(
        name = "Drainage Stinger",
        cost = "{2}{B}{G}",
        type = "Creature — Scorpion",
        pt = "1/1",
        text = """
            Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
            Scavenge {1}{B}{G} ({1}{B}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        scavenge("{1}{B}{G}"),
    )

    // ---- Golgari

    @Test
    fun rotFarmSigil() = card(
        name = "Rot Farm Sigil",
        cost = "{B}{G}",
        type = "Instant",
        text = """
            Choose one —
            • All creatures get -1/-1 until end of turn.
            • Destroy target enchantment.
            • Regenerate each creature you control.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(pump(Objects.Each(creature), -1, -1)))),
            Mode(Instructions(targets = listOf(slot("enchantment", enchantment)), steps = listOf(Rtr.destroy(target("enchantment"))))),
            Mode(Instructions(steps = listOf(regenerate(Objects.Each(all(creature, you)))))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    /** A legend's short name (CR 201.5c); the sacrificed creature's power as it last existed (CR 608.2h); two sacrifices as
     * two cost parts; "return this card from your graveyard" works there (CR 113.6m). */
    @Test
    fun varkasUndercityLich() = card(
        name = "Varkas, Undercity Lich",
        cost = "{B}{B}{G}{G}",
        type = "Legendary Creature — Zombie Elf",
        pt = "1/1",
        text = """
            Varkas gets +1/+1 for each creature card in your graveyard.
            {1}{B}{G}, Sacrifice another creature: Each opponent loses life equal to the sacrificed creature's power.
            Sacrifice a Swamp and a Forest: Return this card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.ModifyPowerToughness(Value.Count(Objects.Each(creatureCardInYourGraveyard)), Value.Count(Objects.Each(creatureCardInYourGraveyard)))),
        activated(cost(manaPart("{1}{B}{G}"), CostPart.Sacrifice(CostObjects.Choose(all(another, creature), count = n(1)), preBind = "sacrificed")),
            Rtr.loseLife(Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Single), Players.Each(PlayerFilter.Opponent))),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(sub("Swamp"), count = n(1))), CostPart.Sacrifice(CostObjects.Choose(sub("Forest"), count = n(1)))),
            Rtr.move(Objects.Self, Zone.Hand)),
    )

    /** Up to two found; one of them into the hand, the other into the graveyard, as you choose. */
    @Test
    fun lichLordsDecree() = card(
        name = "Lich Lord's Decree",
        cost = "{2}{B}{G}",
        type = "Sorcery",
        text = "Search your library for up to two creature cards and reveal them. Put one into your hand and the other into your graveyard. Then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(Players.You, creature, max = n(2), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
            Rtr.move(Objects.Bound("chosen"), Zone.Hand),
            Rtr.move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Graveyard),
            Step.Do(Effect.Shuffle(Players.You)),
        ),
    )

    /** "X 1/1 … tokens, where X is the sacrificed creature's toughness" (CR 107.3c, 608.2h). */
    @Test
    fun fungalRiteKeeper() = card(
        name = "Fungal Rite-Keeper",
        cost = "{B}{G}",
        type = "Creature — Elf Shaman",
        pt = "2/2",
        text = """
            {1}{B}{G}: Target creature gets +1/+1 and gains intimidate until end of turn. (It can't be blocked except by artifact creatures and/or creatures that share a color with it.)
            {2}{B}{G}, Sacrifice a nontoken creature: Create X 1/1 green Saproling creature tokens, where X is the sacrificed creature's toughness.
        """,
    ) parsesTo abilities(
        activated(cost(manaPart("{1}{B}{G}")), pump(target("creature"), 1, 1, Mechanic.GainAbilities(listOf(Keyword.Intimidate))),
            targets = listOf(slot("creature", creature))),
        activated(cost(manaPart("{2}{B}{G}"), CostPart.Sacrifice(CostObjects.Choose(all(ObjectFilter.Not(Rtr.token), creature), count = n(1)), preBind = "sacrificed")),
            Step.Do(Effect.CreateToken(creatureToken("Saproling", listOf(Color.Green), 1, 1),
                count = Value.Of(NumericProperty.Toughness, Objects.Bound("sacrificed"), Combine.Single), controller = Players.You))),
    )

    @Test
    fun molderingTroll() = card(
        name = "Moldering Troll",
        cost = "{B}{G}",
        type = "Creature — Zombie Troll",
        pt = "2/1",
        text = """
            Trample
            Discard a creature card: Put a +1/+1 counter on this creature.
            {B}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        activated(cost(CostPart.Discard(CostObjects.Choose(creature, count = n(1)))), counters(Objects.Self)),
        activated(cost(manaPart("{B}")), regenerate(Objects.Self)),
    )

    @Test
    fun bridgepostTroll() = card(
        name = "Bridgepost Troll",
        cost = "{1}{B}{G}",
        type = "Creature — Troll",
        pt = "0/4",
        text = """
            Defender
            Reach (This creature can block creatures with flying.)
            {1}{B}{G}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        Keyword.Reach,
        activated(cost(manaPart("{1}{B}{G}")), regenerate(Objects.Self)),
    )

    /** "Another target creature": a second target that can't be the first (CR 115.3). */
    @Test
    fun harvestOfRot() = card(
        name = "Harvest of Rot",
        cost = "{4}{B}{G}",
        type = "Sorcery",
        text = "Target creature gets +3/+3 until end of turn. Another target creature gets -3/-3 until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 3), pump(target("other creature"), -3, -3), targets = listOf(
            slot("creature", creature),
            TargetSlot("other creature", TargetKind.Objects(creature), min = n(1), max = n(1), distinctFrom = listOf("creature")),
        )),
    )

    /** CR 614.1a: the number of counters put is doubled (CR 122.6). */
    @Test
    fun sporeswellHorror() = card(
        name = "Sporeswell Horror",
        cost = "{2}{B}{G}",
        type = "Creature — Fungus",
        pt = "4/4",
        text = "If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on it instead.",
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.Replacement(EventPattern.CountersPut(all(creature, you), p1p1), modify = listOf(Modification.MultiplyAmount(2)))),
    )

    @Test
    fun gruesomeScavenging() = card(
        name = "Gruesome Scavenging",
        cost = "{B}{G}",
        type = "Instant",
        text = "Reveal the top five cards of your library. You may put a creature or land card from among them into your hand. Put the rest into your graveyard.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Or(listOf(creature, land)), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Rtr.move(Objects.Bound("chosen"), Zone.Hand),
            Rtr.move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Graveyard),
        ),
    )

    @Test
    fun unearthedKeepsake() = card(
        name = "Unearthed Keepsake",
        cost = "{B}{G}",
        type = "Sorcery",
        text = "Return target card from your graveyard to your hand. Exile Unearthed Keepsake.",
    ) parsesTo abilities(
        spell(Rtr.move(target("card"), Zone.Hand), Rtr.move(Objects.Self, Zone.Exile),
            targets = listOf(slot("card", all(Rtr.card, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
    )

    @Test
    fun suddenRot() = card(
        name = "Sudden Rot",
        cost = "{B}{G}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Destroy target nonland permanent with mana value 3 or less.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.CantBeCountered),
        spell(Rtr.destroy(target("permanent")),
            targets = listOf(slot("permanent", all(nonland, permanent, ObjectFilter.ManaValue(Comparison.AtMost, n(3)))))),
    )

    /** A mana ability can't have a target (CR 605.1a): the first ability uses the Stack and is activated at instant speed. */
    @Test
    fun gravebloomDruid() = card(
        name = "Gravebloom Druid",
        cost = "{B/G}",
        type = "Creature — Elf Shaman",
        pt = "1/2",
        text = """
            {T}: Exile target land card from a graveyard. Add one mana of any color. (Activate only as an instant.)
            {B}, {T}: Exile target instant or sorcery card from a graveyard. Each opponent loses 2 life.
            {G}, {T}: Exile target creature card from a graveyard. You gain 2 life.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.TapSelf), exileCard(),
            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)),
            targets = listOf(slot("card", all(land, ObjectFilter.InZone(Zone.Graveyard))))),
        activated(cost(manaPart("{B}"), CostPart.TapSelf), exileCard(), Rtr.loseLife(n(2), Players.Each(PlayerFilter.Opponent)),
            targets = listOf(slot("card", all(ObjectFilter.Or(listOf(Rtr.instant, Rtr.sorcery)), ObjectFilter.InZone(Zone.Graveyard))))),
        activated(cost(manaPart("{G}"), CostPart.TapSelf), exileCard(), gainLife(2),
            targets = listOf(slot("card", all(creature, ObjectFilter.InZone(Zone.Graveyard))))),
    )

    private fun exileCard() = Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled"))
}
