package arcanum.parser.sets.dft

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 14: green cards: cards looked at and revealed, cards leaving the graveyard in a batch, searches of the library and/or
 * graveyard for a creature card with no abilities (CR 113.1), base P/T and a creature type for your creatures (CR 613.1d, 613.4b), trample
 * for creatures attacking an opponent, putting a creature or Vehicle from the hand onto the battlefield, a fight mode (CR 701.14), four
 * targets of different kinds (CR 115.3), damage equal to power, and an Aura that untaps.
 */
class DftChunk14GreenTest {
    @Test
    fun beastriderOutrider() = card(
        name = "Beastrider Outrider",
        cost = "{1}{G}",
        type = "Creature — Human Knight",
        text = "{4}{G}: Look at the top three cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in any order.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{4}{G}")),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Library, position = -1)),
    )

    @Test
    fun bestowGrandeur() = card(
        name = "Bestow Grandeur",
        cost = "{2}{G}",
        type = "Instant",
        text = "Target creature gets +4/+4 and gains trample until end of turn.",
    ) parsesTo abilities(spell(pump(target("creature"), 4, 4, has(Keyword.Trample)), targets = listOf(slot("creature", creature))))

    @Test
    fun snappedPinions() = card(
        name = "Snapped Pinions",
        cost = "{2}{G}",
        type = "Instant",
        text = "Destroy target artifact, enchantment, or creature with flying.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying.kind)))))),
    )

    // CR 603.10a: cards leaving the graveyard are seen there
    @Test
    fun dredgersForesight() = card(
        name = "Dredger's Foresight",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Whenever one or more artifact and/or creature cards leave your graveyard, you gain 1 life.
            When this enchantment enters, mill four cards. You may put an artifact, creature, or land card from among the milled cards into your hand. (To mill four cards, put the top four cards of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(or(artifact, creature), ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            gainLife(1)),
        triggered(entersSelf, mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(or(artifact, creature, land), n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    @Test
    fun fangProtector() = card(
        name = "Fang Protector",
        cost = "{3}{G}",
        type = "Creature — Ape Druid",
        text = """
            Flash
            When this creature enters, another target creature or Vehicle you control gets +2/+2 until end of turn.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("permanent"), 2, 2), targets = listOf(slot("permanent", and(another, creatureOrVehicle, you)))),
    )

    // CR 701.23: the library is shuffled only if it was searched
    @Test
    fun fangDruidCaller() = card(
        name = "Fang-Druid Caller",
        cost = "{3}{G}",
        type = "Creature — Ape Druid",
        text = """
            Reach
            When this creature enters, you may search your library and/or graveyard for a creature card with no abilities, reveal it, and put it into your hand. If you search your library this way, shuffle.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, Step.May(listOf(
            doing(Effect.Search(Players.You, and(creature, ObjectFilter.NoAbilities), max = n(1), reveal = true, zones = listOf(Zone.Library, Zone.Graveyard),
                bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            Step.If(Condition.Searched("found", Zone.Library), then = listOf(shuffle)),
        ), player = Players.You)),
    )

    @Test
    fun jibberikOmnivore() = card(
        name = "Jibberik Omnivore",
        cost = "{1}{G}",
        type = "Creature — Beast",
        pt = "3/2",
    ) parsesTo abilities()

    // CR 613.4b, 613.1d: base P/T in layer 7b, the type in layer 4
    @Test
    fun marchOfTheEndlessOoze() = card(
        name = "March of the Endless Ooze",
        cost = "{3}{G}{G}{G}",
        type = "Enchantment",
        text = """
            Creatures you control have base power and toughness 6/6 and are Oozes in addition to their other types.
            Whenever an opponent casts a spell, if it's not their turn, you create a 3/3 green Elephant creature token.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(n(6), n(6)), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Ooze")))), affects = Objects.Each(creatureYouControl)),
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)), create(elephant),
            interveningIf = Condition.PlayerMatches(Players.EventPlayer, PlayerFilter.Not(PlayerFilter.Is(Players.Active)))),
    )

    @Test
    fun moltKeeper() = card(
        name = "Molt Keeper",
        cost = "{G}",
        type = "Creature — Insect Druid",
        text = """
            {T}: Mill a card. (Put the top card of your library into your graveyard.)
            {T}, Exile a card from your graveyard: Add one mana of any color.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(tap), mill(1)),
        activated(cost(tap, CostPart.Exile(CostObjects.Choose(cardFilter, count = n(1)), from = Zone.Graveyard)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // CR 608.2c: the counters are counted after the mill
    @Test
    fun oozeSentry() = card(
        name = "Ooze Sentry",
        cost = "{3}{G}",
        type = "Creature — Ooze",
        text = "When this creature enters, mill two cards, then put a +1/+1 counter on this creature for each artifact and/or creature card in your graveyard. (To mill two cards, put the top two cards of your library into your graveyard.)",
        pt = "2/2",
    ) parsesTo abilities(triggered(entersSelf, mill(2), counters(Objects.Self, count(inYourGraveyard(or(artifact, creature))))))

    @Test
    fun oviraAutomechArtisan() = card(
        name = "Ovira, Automech Artisan",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Artificer",
        text = """
            Each creature that's attacking one of your opponents has trample.
            {G}, {T}: You may put a creature or Vehicle card from your hand onto the battlefield. If you put an artifact onto the battlefield this way, put two +1/+1 counters on it.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, attacking,
            ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any, target = RecipientFilter(players = PlayerFilter.Opponent)))))),
        activated(cost(mana("{G}"), tap),
            Step.Choose(Choice.Objects(and(creatureOrVehicle, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            Step.If(Condition.ObjectMatches(Objects.Bound("put"), artifact), then = listOf(counters(Objects.Bound("put"), 2)))),
    )

    // CR 701.14: each deals damage equal to its power to the other
    @Test
    fun plowUnderFoe() = card(
        name = "Plow Under Foe",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target creature you control fights target creature an opponent controls. (Each deals damage equal to its power to the other.)
            • Destroy target Vehicle.
        """,
    ) parsesTo abilities(
        modal(
            mode(doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
            mode(destroy(target("Vehicle")), targets = listOf(slot("Vehicle", vehicle))),
        ),
    )

    @Test
    fun potholeVole() = card(
        name = "Pothole Vole",
        cost = "{2}{G}",
        type = "Creature — Mole",
        text = "When this creature enters, mill three cards, then you may return a land card from your graveyard to your hand. (To mill three cards, put the top three cards of your library into your graveyard.)",
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, mill(3),
            Step.Choose(Choice.Objects(inYourGraveyard(land), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    @Test
    fun regalImperiodon() = card(
        name = "Regal Imperiodon",
        cost = "{1}{G}{G}",
        type = "Creature — Dinosaur",
        text = "Other Dinosaurs you control get +1/+1.",
        pt = "5/4",
    ) parsesTo abilities(static(pt(1, 1), affects = Objects.Each(and(another, sub("Dinosaur"), you))))

    // CR 115.3: one target for each "target"; a card may be the target of more than one
    @Test
    fun riseFromTheRubble() = card(
        name = "Rise from the Rubble",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Return up to one target creature card, up to one target Mount card, up to one target Vehicle card, and up to one target creature card with no abilities from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(move(Objects.Union(listOf(target("creature card"), target("Mount card"), target("Vehicle card"), target("creature card with no abilities"))), Zone.Hand),
            targets = listOf(
                slot("creature card", inYourGraveyard(creature), min = 0),
                slot("Mount card", inYourGraveyard(mount), min = 0),
                slot("Vehicle card", inYourGraveyard(vehicle), min = 0),
                slot("creature card with no abilities", inYourGraveyard(creature, ObjectFilter.NoAbilities), min = 0))),
    )

    @Test
    fun runDown() = card(
        name = "Run Down",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if it targets a Mount or Vehicle you control.
            Target creature you control deals damage equal to its power to target creature an opponent controls.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{1}")!!, n(1))), condition = Condition.ObjectMatches(target("creature"), and(mountOrVehicle, you))),
        spell(damage(powerOf(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
    )

    @Test
    fun silkenVigor() = card(
        name = "Silken Vigor",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature or Vehicle
            When this Aura enters, untap enchanted permanent.
            Enchanted permanent gets +1/+2 and has reach.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreatureOrVehicle,
        triggered(entersSelf, untapIt(enchanted)),
        static(pt(1, 2), has(Keyword.Reach), affects = enchanted),
    )

    @Test
    fun terriusLandTyrant() = card(
        name = "Terrius, Land Tyrant",
        cost = "{2}{G}{G}{G}",
        type = "Legendary Creature — Dinosaur Ooze",
        pt = "9/7",
    ) parsesTo abilities()
}
