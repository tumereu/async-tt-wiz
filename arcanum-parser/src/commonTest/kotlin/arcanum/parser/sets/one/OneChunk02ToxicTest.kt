package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
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
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 02: toxic (CR 702.164): poison counters with combat damage to a player, "creature with toxic" as a quality
 * (any toxic value), toxic granted on top of printed toxic (total toxic value, CR 702.164b), poison counters given by effects (CR 122.1f), and
 * a trigger on becoming the target of a spell (CR 115.10).
 */
class OneChunk02ToxicTest {
    private val toxic = kw(Keyword.Toxic.Kind)
    private val chosenColor = ObjectFilter.OfColor(ColorRef.Chosen("color"))
    private val cantBlock = Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))
    /** 'a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."' */
    private val mite = creatureToken("Phyrexian Mite", emptyList(), 1, 1, abilities = listOf(Keyword.Toxic(1), cantBlock),
        types = listOf(CardType.Artifact, CardType.Creature))

    // CR 702.164a; CR 115.1: "another target creature you control with toxic" (any toxic value)
    @Test
    fun gristleKestrel() = card(
        name = "Gristle Kestrel",
        cost = "{2}{W}",
        type = "Creature — Phyrexian Bird",
        pt = "2/2",
        text = """
            Flying
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature enters, another target creature you control with toxic gets +1/+1 and gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Toxic(1),
        triggered(entersSelf, pump(target("creature"), 1, 1, has(Keyword.Flying)), targets = listOf(slot("creature", and(another, creature, you, toxic)))),
    )

    // CR 702.164b (toxic granted to other Rats adds to theirs); CR 401.4 (the rest in a random order); "any number of": no upper limit
    @Test
    fun oskrelTheVerminThane() = card(
        name = "Oskrel, the Vermin Thane",
        cost = "{1}{B}{B}",
        type = "Legendary Creature — Phyrexian Rat",
        pt = "3/3",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            Other Rats you control have toxic 1.
            When Oskrel enters, look at the top five cards of your library. You may reveal any number of Rat cards from among them and put the revealed cards into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        static(has(Keyword.Toxic(1)), affects = Objects.Each(and(another, sub("Rat"), you))),
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.LibraryCards(of = Players.You, count = n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(filter = sub("Rat"), min = n(0), max = null, from = Objects.Bound("seen")), bind = "revealed", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("revealed"))),
            doing(Effect.Move(Objects.Bound("revealed"), Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("revealed")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 122.1f: one more poison counter for the damaged player (besides toxic's, CR 702.164c)
    @Test
    fun blightmenderAugur() = card(
        name = "Blightmender Augur",
        cost = "{2}{B}{G}",
        type = "Creature — Phyrexian Zombie Cleric",
        pt = "1/5",
        text = """
            Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
            Whenever a creature you control with toxic deals combat damage to a player, that player gets an additional poison counter.
            {1}{B}{G}: Target creature you control with toxic gains deathtouch until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(2),
        triggered(on(EventPattern.Damage(and(creature, you, toxic), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.GivePlayerCounters(Players.EventPlayer, CounterKind.Poison, n(1)))),
        activated(cost(mana("{1}{B}{G}")), untilEndOfTurn(target("creature"), has(Keyword.Deathtouch)), targets = listOf(slot("creature", and(creature, you, toxic)))),
    )

    // CR 702.164b: toxic 1 granted on top of a creature's own toxic; CR 602.5b: once each turn
    @Test
    fun seepwardenNurse() = card(
        name = "Seepwarden Nurse",
        cost = "{3}{G}",
        type = "Creature — Phyrexian Cleric",
        pt = "3/4",
        text = """
            Toxic 2
            {2}{G}: Each other creature you control with toxic gains toxic 1 until end of turn. Activate only once each turn. (A player dealt combat damage by a creature with toxic also gets poison counters equal to that creature's total toxic value.)
        """,
    ) parsesTo abilities(
        Keyword.Toxic(2),
        activated(cost(mana("{2}{G}")), untilEndOfTurn(Objects.Each(and(another, creature, you, toxic)), has(Keyword.Toxic(1))),
            rules = anyTime.copy(perTurn = 1)),
    )

    // CR 702.11d (hexproof from the chosen colour), 509.1b (can't be blocked by creatures of it), 107.4f (Phyrexian mana)
    @Test
    fun thriskTurncoatMite() = card(
        name = "Thrisk, Turncoat Mite",
        cost = "{W}",
        type = "Legendary Artifact Creature — Phyrexian Mite",
        pt = "1/1",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            Thrisk can't block.
            {W/P}, {T}: Choose a color. Another target creature you control gains toxic 1 and hexproof from that color until end of turn. It can't be blocked by creatures of that color this turn. ({W/P} can be paid with either {W} or 2 life.)
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        cantBlock,
        activated(cost(mana("{W/P}"), tap),
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You),
            untilEndOfTurn(target("creature"), has(Keyword.Toxic(1), Keyword.HexproofFrom(chosenColor))),
            untilEndOfTurn(target("creature"), Mechanic.CantBeBlockedBy(and(creature, chosenColor))),
            targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 508.1m: each other attacking creature with toxic gets +1/+1
    @Test
    fun venomcantChorister() = card(
        name = "Venomcant Chorister",
        cost = "{G}{W}",
        type = "Creature — Phyrexian Cleric",
        pt = "2/2",
        text = """
            Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
            Whenever another creature you control with toxic attacks, it gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(2),
        triggered(on(EventPattern.Attack(and(another, creature, you, toxic))), pump(Objects.EventObject(EventRole.Object), 1, 1)),
    )

    // CR 115.10: becoming the target of a spell (any player's, not an ability); CR 122.1f
    @Test
    fun ichorblessAcolyte() = card(
        name = "Ichorbless Acolyte",
        cost = "{G}",
        type = "Creature — Phyrexian Druid",
        pt = "1/2",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            Whenever a creature you control becomes the target of a spell, target opponent gets a poison counter.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(on(EventPattern.BecomesTarget(and(creature, you), by = spellFilter)),
            doing(Effect.GivePlayerCounters(Players.Target("opponent"), CounterKind.Poison, n(1))), targets = listOf(targetOpponent)),
    )

    // CR 610.3: exiled until this creature leaves the battlefield
    @Test
    fun quarantineWarder() = card(
        name = "Quarantine Warder",
        cost = "{2}{W}",
        type = "Artifact Creature — Phyrexian Cleric",
        pt = "1/4",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature enters, exile target artifact or creature an opponent controls with mana value 3 or less until this creature leaves the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 702.2, 702.164
    @Test
    fun pustuleBurrower() = card(
        name = "Pustule Burrower",
        cost = "{B}",
        type = "Creature — Phyrexian Insect",
        pt = "1/1",
        text = """
            Deathtouch
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        """,
    ) parsesTo abilities(Keyword.Deathtouch, Keyword.Toxic(1))

    // CR 702.164a: "Toxic 2"
    @Test
    fun rotbarkProwler() = card(
        name = "Rotbark Prowler",
        cost = "{1}{G}",
        type = "Creature — Phyrexian Elf Scout",
        pt = "3/1",
        text = "Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)",
    ) parsesTo abilities(Keyword.Toxic(2))

    // CR 111.4: a token with a keyword and a quoted ability
    @Test
    fun seepingChoir() = card(
        name = "Seeping Choir",
        cost = "{W}",
        type = "Creature — Phyrexian Horror",
        pt = "1/1",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature dies, create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."
        """,
    ) parsesTo abilities(Keyword.Toxic(1), triggered(diesSelf, create(mite, bind = "token")))

    // CR 611.3a: first strike only during your turn
    @Test
    fun glintfaithDuelist() = card(
        name = "Glintfaith Duelist",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "2/2",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            During your turn, this creature has first strike.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.FirstStrike))), affects = Affected.Objects(Objects.Self), condition = Condition.YourTurn),
    )

    // CR 701.23a: search, reveal, shuffle, then put that card on top
    @Test
    fun sandsiftGolem() = card(
        name = "Sandsift Golem",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Golem",
        pt = "2/1",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(entersSelf,
            Step.May(
                steps = listOf(
                    doing(Effect.Search(library = Players.You, filter = and(ObjectFilter.Super(Supertype.Basic), land), max = n(1), reveal = true,
                        zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                    doing(Effect.Shuffle(library = Players.You, except = Objects.Bound("found"))),
                    doing(Effect.Move(objects = Objects.Bound("found"), to = Destination(zone = Zone.Library, libraryPosition = 0))),
                ),
                player = Players.You,
            ),
        ),
    )

    // CR 702.2, 702.164
    @Test
    fun bilegazeBasilisk() = card(
        name = "Bilegaze Basilisk",
        cost = "{2}{G}",
        type = "Creature — Phyrexian Basilisk",
        pt = "1/3",
        text = """
            Deathtouch
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        """,
    ) parsesTo abilities(Keyword.Deathtouch, Keyword.Toxic(1))

    // CR 608.2c: "If you do": only if a permanent was returned
    @Test
    fun doctrineSteward() = card(
        name = "Doctrine Steward",
        cost = "{3}{W}",
        type = "Creature — Phyrexian Cleric",
        pt = "3/4",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature enters, you may return another permanent you control to its owner's hand. If you do, create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(entersSelf,
            Step.Pay(cost(CostPart.ReturnToHand(CostObjects.Choose(and(another, permanentFilter, you), n(1)))), mandatory = false, bind = "paid", payer = Players.You),
            Step.If(Condition.Did("paid"), then = listOf(create(mite, bind = "token"))),
        ),
    )

    // CR 702.4, 702.164
    @Test
    fun mandibleFencer() = card(
        name = "Mandible Fencer",
        cost = "{1}{W}",
        type = "Creature — Phyrexian Soldier",
        pt = "1/1",
        text = """
            Double strike
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        """,
    ) parsesTo abilities(Keyword.DoubleStrike, Keyword.Toxic(1))

    // CR 605.1a: a mana ability with a life cost (CR 118.3)
    @Test
    fun myrApostate() = card(
        name = "Myr Apostate",
        cost = "{2}",
        type = "Artifact Creature — Phyrexian Myr",
        pt = "2/1",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            {T}, Pay 2 life: Add one mana of any color.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        activated(cost(tap, CostPart.PayLife(n(2))), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
    )

    // CR 115.1: a creature card with mana value 3 or less in your graveyard
    @Test
    fun graveriseTemplar() = card(
        name = "Graverise Templar",
        cost = "{4}{B}",
        type = "Creature — Phyrexian Knight",
        pt = "4/4",
        text = """
            Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
            When this creature enters, return target creature card with mana value 3 or less from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(2),
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 509.1b: can't be blocked by creatures with power 2 or less
    @Test
    fun preyhuntPaladin() = card(
        name = "Preyhunt Paladin",
        cost = "{5}{G}{G}",
        type = "Creature — Phyrexian Knight",
        pt = "6/7",
        text = """
            Toxic 6 (Players dealt combat damage by this creature also get six poison counters.)
            This creature can't be blocked by creatures with power 2 or less.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(6),
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
    )

    // CR 702.9, 702.164
    @Test
    fun venomGnat() = card(
        name = "Venom Gnat",
        cost = "{1}{B}",
        type = "Creature — Phyrexian Insect",
        pt = "1/1",
        text = """
            Flying
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Toxic(1))

    // CR 702.111, 702.164
    @Test
    fun oilcleaverBrute() = card(
        name = "Oilcleaver Brute",
        cost = "{3}{B}",
        type = "Creature — Phyrexian Warrior",
        pt = "2/4",
        text = """
            Menace
            Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
        """,
    ) parsesTo abilities(Keyword.Menace, Keyword.Toxic(2))

    // CR 111.4: the Mite token when this creature dies
    @Test
    fun stingswarmMatron() = card(
        name = "Stingswarm Matron",
        cost = "{2}{B}",
        type = "Creature — Phyrexian Warlock",
        pt = "3/2",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature dies, create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block."
        """,
    ) parsesTo abilities(Keyword.Toxic(1), triggered(diesSelf, create(mite, bind = "token")))

    // CR 702.10, 702.164
    @Test
    fun tyrranaxRavager() = card(
        name = "Tyrranax Ravager",
        cost = "{3}{G}{G}",
        type = "Creature — Phyrexian Dinosaur",
        pt = "4/4",
        text = """
            Haste
            Toxic 3 (Players dealt combat damage by this creature also get three poison counters.)
        """,
    ) parsesTo abilities(Keyword.Haste, Keyword.Toxic(3))

    // CR 101.2 (can't be countered); 702.19, 702.21, 702.10 on one line
    @Test
    fun tyrranaxTyrant() = card(
        name = "Tyrranax Tyrant",
        cost = "{4}{G}{G}{G}",
        type = "Creature — Phyrexian Dinosaur",
        pt = "8/8",
        text = """
            This spell can't be countered.
            Trample, ward {4}, haste
            Toxic 4 (Players dealt combat damage by this creature also get four poison counters.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        Keyword.Trample,
        Keyword.Ward(manaCost("{4}")),
        Keyword.Haste,
        Keyword.Toxic(4),
    )

    // CR 611.2b: for as long as this creature remains on the battlefield; CR 613.1d, 613.4b: types and base P/T
    @Test
    fun gearwrightRetooler() = card(
        name = "Gearwright Retooler",
        cost = "{2}{U}",
        type = "Creature — Phyrexian Artificer",
        pt = "2/3",
        text = """
            Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
            When this creature enters, up to one target artifact you control becomes an artifact creature with base power and toughness 4/4 for as long as this creature remains on the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Toxic(1),
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(4), n(4))),
                Affected.Objects(target("artifact")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(slot("artifact", and(artifact, you), min = 0))),
    )
}
