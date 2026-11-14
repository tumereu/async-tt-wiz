package arcanum.parser.sets.dmu

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 13: life-loss drains, tribal anthems with sacrifice and search abilities, counter engines, a library tutor,
 * characteristic-defining power, tap and untap effects.
 */
class DmuChunk13LifeCountersSearchTapTest {
    private val instantOrSorcery = or(instant, sorcery)
    private fun othersGetPlusOne(subtype: String) = Ability.Static(
        mechanics = listOf(Mechanic.ModifyPowerToughness(n(1), n(1))),
        affects = Affected.Objects(Objects.Each(and(another, sub(subtype), you))),
    )

    // CR 702.3 (defender), CR 602.1, 119.3: the loss is counted when the ability resolves
    @Test
    fun mirewallHusk() = card(
        name = "Mirewall Husk", cost = "{1}{B}", type = "Creature — Phyrexian", pt = "3/3",
        text = """
            Defender
            {2}{B}, {T}: Each opponent loses X life, where X is the number of creatures with defender you control.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{2}{B}"), tap), doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), count(and(creature, kw(Keyword.Defender.kind), you))))),
    )

    // CR 603.1, 119.3, 601.2 (cast trigger); CR 118.9, 702.138-style cast from graveyard with an additional discard cost
    @Test
    fun sorrelWyrdcaller() = card(
        name = "Sorrel Wyrdcaller", cost = "{1}{U}{B}{B}", type = "Legendary Creature — Human Wizard", pt = "3/4",
        text = """
            Whenever you cast an instant or sorcery spell, each opponent loses 1 life.
            You may cast this card from your graveyard by discarding two cards in addition to paying its other costs.
        """,
    ) parsesTo abilities(
        triggered(youCast(instantOrSorcery), loseLife(1, Players.Each(PlayerFilter.Opponent))),
        // the discard is part of the cost of casting it from the graveyard (CR 601.2b, 118.8); discarding is from the hand (CR 701.9a)
        static(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You, additionalCost = cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(2)))))),
    )

    // CR 603.6a, 119.3
    @Test
    fun noxiousLump() = card(
        name = "Noxious Lump", cost = "{1}{B}", type = "Creature — Phyrexian Zombie", pt = "3/2",
        text = "When this creature enters, you lose 2 life.",
    ) parsesTo abilities(
        triggered(entersSelf, loseLife(2)),
    )

    // CR 613.4c (anthem), 205.3m (creature types), 117.3 / 601.2h (optional payment on resolution)
    @Test
    fun thornboughSeer() = card(
        name = "Thornbough Seer", cost = "{G}{G}", type = "Creature — Elf Druid", pt = "1/1",
        text = """
            Other Elves you control get +1/+1.
            Whenever you cast an Elf spell, you may pay {G}. If you do, draw a card.
        """,
    ) parsesTo abilities(
        othersGetPlusOne("Elf"),
        triggered(youCast(sub("Elf")), Step.Pay(manaCost("{G}"), bind = "paid", payer = Players.You),
            Step.If(Condition.Did("paid"), listOf(draw(1)))),
    )

    // CR 613.4c, 701.19 (search), 701.21a (sacrifice as a cost)
    @Test
    fun duskVigilAcolyte() = card(
        name = "Dusk-Vigil Acolyte", cost = "{1}{B}", type = "Creature — Human Cleric", pt = "2/2",
        text = """
            Other Clerics you control get +1/+1.
            {3}{B}{B}, {T}, Sacrifice another Cleric: Search your library for a black creature card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        othersGetPlusOne("Cleric"),
        activated(cost(mana("{3}{B}{B}"), tap, sacrificeA(and(another, sub("Cleric")))),
            search(and(ObjectFilter.OfColor(ColorRef.Literal(Color.Black)), creature)),
            move(Objects.Bound("found"), Zone.Battlefield), shuffle),
    )

    // CR 613.4c, 702.xx graveyard activation, 122.1
    @Test
    fun stalwartSergeant() = card(
        name = "Stalwart Sergeant", cost = "{1}{W}", type = "Creature — Kor Soldier", pt = "2/2",
        text = """
            Other Soldiers you control get +1/+1.
            {3}{W}{W}, Exile this card from your graveyard: Put a +1/+1 counter on each Soldier you control.
        """,
    ) parsesTo abilities(
        othersGetPlusOne("Soldier"),
        // CR 602.2 / 113.6k: it works from the graveyard because exiling itself is the cost there
        Ability.Activated(cost(mana("{3}{W}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), Instructions(steps = listOf(
            counters(Objects.Each(and(sub("Soldier"), you)))
        )), anyTime),
    )

    // CR 613.4c, 701.6a (counter a spell unless its controller pays), 701.21a
    @Test
    fun tidewellSpellsnare() = card(
        name = "Tidewell Spellsnare", cost = "{1}{U}", type = "Creature — Merfolk Wizard", pt = "1/1",
        text = """
            Flash
            Other Merfolk you control get +1/+1.
            Sacrifice a Merfolk: Counter target noncreature spell unless its controller pays {1}.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        othersGetPlusOne("Merfolk"),
        activated(cost(sacrificeA(sub("Merfolk"))),
            Step.Pay(manaCost("{1}"), bind = "paid", payer = Players.ControllerOf(target("spell"))),
            Step.If(Condition.Not(Condition.Did("paid")), listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", and(noncreatureSpell, spellFilter)))),
    )

    // CR 610.3 (exile until it leaves), 603.6a
    @Test
    fun magistratesWrit() = card(
        name = "Magistrate's Writ", cost = "{1}{W}{W}", type = "Enchantment",
        text = "When this enchantment enters, exile target creature or planeswalker an opponent controls until this enchantment leaves the battlefield.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(creature, planeswalker), opponents)))),
    )

    // CR 702.110 (menace), 602.1, 122.1
    @Test
    fun corvaneRuinWarden() = card(
        name = "Corvane, Ruin Warden", cost = "{W}{W}{B}", type = "Legendary Creature — Phyrexian Human", pt = "3/3",
        text = """
            Menace
            {W}{B}, {T}, Sacrifice another creature: Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{W}{B}"), tap, sacrificeA(and(another, creature))),
            counters(Objects.Each(creatureYouControl))),
    )

    // CR 603.1, 122.1
    @Test
    fun sparkgritMarcher() = card(
        name = "Sparkgrit Marcher", cost = "{1}{R}", type = "Creature — Dwarf Wizard", pt = "1/2",
        text = """
            Trample
            Whenever you cast an instant or sorcery spell, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(youCast(instantOrSorcery), counters(Objects.Self)),
    )

    // CR 122.1, 400.7, 603.1
    @Test
    fun maelisTinkererSage() = card(
        name = "Maelis, Tinkerer Sage", cost = "{U}{R}", type = "Legendary Creature — Human Artificer", pt = "2/3",
        text = "{T}: Put two ingenuity counters on Maelis, then you may put an artifact card with mana value X or less from your hand onto the battlefield, where X is the number of ingenuity counters on Maelis.",
    ) parsesTo abilities(
        // CR 122.1; "you may put" a card is a choice of zero or one cards, MV limit counted after the counters are placed
        activated(cost(tap),
            counters(Objects.Self, 2, CounterKind.Named("ingenuity")),
            Step.Choose(
                Choice.Objects(
                    and(artifact, ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.Counters(CounterKind.Named("ingenuity")), Objects.Self, Combine.Single)),
                        inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                    min = n(0), max = n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 700.4, 603.2c ("one or more"), 603.2g-style once per turn limit (CR 603.4 note: "triggers only once each turn")
    @Test
    fun crimsonGourmand() = card(
        name = "Crimson Gourmand", cost = "{3}{B}{B}", type = "Creature — Vampire", pt = "3/3",
        text = """
            Flying
            Whenever one or more other creatures die, put a +1/+1 counter on this creature. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(and(another, creature), from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            Instructions(steps = listOf(counters(Objects.Self))), perTurn = 1),
    )

    // CR 701.19, 701.20 (reveal), 701.24 (shuffle)
    @Test
    fun bulwarkAutomaton() = card(
        name = "Bulwark Automaton", cost = "{4}", type = "Artifact Creature — Golem", pt = "1/3",
        text = """
            Defender
            When this creature enters, you may search your library for a creature card with defender, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf, Step.May(listOf(
            search(and(creature, kw(Keyword.Defender.kind)), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
    )

    // CR 604.3, 208.2a-style characteristic-defining power; CR 601.2f, 118.7 (cost reduction)
    @Test
    fun proudZephyrine() = card(
        name = "Proud Zephyrine", cost = "{1}{U}{U}", type = "Creature — Djinn", pt = "*/4",
        text = """
            Flying
            Proud Zephyrine's power is equal to the number of instant and sorcery cards in your graveyard.
            Instant and sorcery spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(count(inYourGraveyard(instantOrSorcery)), null), characteristicDefining = true),
        static(Mechanic.CostChange(and(instantOrSorcery, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 604.3, 702.62-style surveil (CR 701.25), 602.1
    @Test
    fun grubbSpawnOfMire() = card(
        name = "Grubb, Spawn of Mire", cost = "{B}{B}{G}", type = "Legendary Creature — Frog Beast", pt = "*/5",
        text = """
            Grubb's power is equal to the number of land cards in your graveyard.
            At the beginning of your upkeep, surveil 1.
            {B}{G}, Sacrifice a land: You gain 2 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(inYourGraveyard(land)), null), characteristicDefining = true),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), doing(Effect.Surveil(Players.You, n(1)))),
        activated(cost(mana("{B}{G}"), sacrificeA(land)), gainLife(2)),
    )

    // CR 702.3, 701.26a (tap), 602.1
    @Test
    fun gearworkPortcullis() = card(
        name = "Gearwork Portcullis", cost = "{W}", type = "Artifact Creature — Wall", pt = "0/3",
        text = """
            Defender
            {2}{W}, {T}: Tap target creature.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{2}{W}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 701.26b (untap), 115.1b (up to two targets), 611.2
    @Test
    fun swiftMuster() = card(
        name = "Swift Muster", cost = "{2}{W}", type = "Instant",
        text = "Untap up to two target creatures. They each get +2/+2 until end of turn.",
    ) parsesTo abilities(
        spell(untapIt(target("creatures")), pump(target("creatures"), 2, 2), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )
}
