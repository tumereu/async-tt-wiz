package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class BroChunk05MillAndScryTest {
    private val artifactCreature: List<arcanum.api.CardType> = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature)

    /** "mill three cards. You may put a [filter] from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on
     * this creature." (CR 701.17a; "if you don't", CR 608.2c) */
    private fun millMayPutElseCounter(filter: ObjectFilter) = triggered(entersSelf, mill(3, postBind = "milled"),
        Step.Choose(Choice.Objects(filter, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
        move(Objects.Bound("chosen"), Zone.Hand),
        Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(counters(Objects.Self))))

    // CR 701.17a, 608.2c: one card of either kind among the milled ones, or the counter
    @Test
    fun skywardAlmoner() = card(
        name = "Skyward Almoner",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = """
            Flying
            When this creature enters, mill three cards. You may put a Plains card or a creature card with mana value 3 or less from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on this creature. (To mill a card, put the top card of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        millMayPutElseCounter(or(sub("Plains"), and(creature, manaValue(Comparison.AtMost, 3)))),
    )

    // CR 701.17a, 608.2c
    @Test
    fun rootmawStalker() = card(
        name = "Rootmaw Stalker",
        cost = "{1}{G}",
        type = "Creature — Elemental",
        pt = "1/1",
        text = "When this creature enters, mill three cards. You may put a land card from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on this creature. (To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(millMayPutElseCounter(land))

    // CR 701.17a, 608.2c
    @Test
    fun duneRelicSeeker() = card(
        name = "Dune Relic Seeker",
        cost = "{1}{U}",
        type = "Creature — Human Scout",
        pt = "0/3",
        text = "When this creature enters, mill three cards. You may put a noncreature, nonland card from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on this creature. (To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(millMayPutElseCounter(and(not(creature), not(land))))

    // CR 701.17a, 608.2c
    @Test
    fun hungryTunnelmole() = card(
        name = "Hungry Tunnelmole",
        cost = "{3}{B}",
        type = "Creature — Mole Horror",
        pt = "2/3",
        text = "When this creature enters, mill three cards. You may put a creature card from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on this creature. (To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(millMayPutElseCounter(creature))

    // CR 701.17a, 608.2c
    @Test
    fun forgeScrapdigger() = card(
        name = "Forge Scrapdigger",
        cost = "{2}{R}",
        type = "Creature — Human Artificer",
        pt = "2/1",
        text = "When this creature enters, mill three cards. You may put an artifact card from among the cards milled this way into your hand. If you don't, put a +1/+1 counter on this creature. (To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(millMayPutElseCounter(artifact))

    // CR 506.2: the defending player of this attacking creature; CR 107.1a: half rounded up; CR 701.17a; CR 702.84a (unearth)
    @Test
    fun cogmindDevourer() = card(
        name = "Cogmind Devourer",
        cost = "{7}",
        type = "Artifact Creature — Juggernaut",
        pt = "6/4",
        text = """
            Whenever this creature attacks, defending player mills half their library, rounded up.
            Unearth {1}{U}{U}{U} ({1}{U}{U}{U}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(attacksSelf, doing(Effect.Mill(Players.DefendingPlayerOf(Objects.Self), Value.Half(Value.Count(Objects.Each(
            and(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self)))))), roundUp = true)))),
        Keyword.Unearth(manaCost("{1}{U}{U}{U}")),
    )

    // CR 701.17a: a dies trigger
    @Test
    fun gravelfangDigger() = card(
        name = "Gravelfang Digger",
        cost = "{2}{G}",
        type = "Creature — Beast",
        pt = "4/2",
        text = "When this creature dies, mill four cards. (Put the top four cards of your library into your graveyard.)",
    ) parsesTo abilities(triggered(diesSelf, mill(4)))

    // CR 701.17a, 115.1: a target player mills; a dies trigger targeting a creature you don't control
    @Test
    fun sewerNibbler() = card(
        name = "Sewer Nibbler",
        cost = "{B}",
        type = "Creature — Rat",
        pt = "1/1",
        text = """
            When this creature enters, target player mills two cards.
            When this creature dies, target creature you don't control gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(2, Players.Target("player")), targets = listOf(targetPlayer)),
        triggered(diesSelf, pump(target("creature"), -1, -1),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 701.22a; "If you do" after an optional tap (CR 608.2c); CR 114.3: an emblem with two abilities, a static boost (CR 613.4c) and a
    // cost reduction (CR 601.2f) working from the command zone (CR 113.6p)
    @Test
    fun veskaGearwrightAdept() = card(
        name = "Veska, Gearwright Adept",
        cost = "{2}{U}{R}",
        type = "Legendary Planeswalker — Veska",
        loyalty = "3",
        text = """
            [+1]: Scry 1. You may tap an untapped artifact you control. If you do, draw a card.
            [−2]: Create two 1/1 colorless Thopter artifact creature tokens with flying. They gain haste until end of turn.
            [−4]: You get an emblem with "Artifact creatures you control get +1/+1" and "Artifact spells you cast cost {1} less to cast."
        """,
    ) parsesTo abilities(
        loyalty(1, scry(1),
            Step.Pay(arcanum.api.Cost(listOf(CostPart.TapObjects(CostObjects.Choose(artifact, count = n(1))))), mandatory = false, payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
        loyalty(-2, create(creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying), types = artifactCreature), n(2), bind = "tokens"),
            gains(Objects.Bound("tokens"), Keyword.Haste)),
        loyalty(-4, doing(Effect.CreateEmblem(listOf(
            static(pt(1, 1), affects = Objects.Each(and(artifact, creature, you))),
            static(Mechanic.CostChange(and(artifact, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1))))), Players.You))),
    )

    // CR 701.22a (scry); CR 701.22a, 603.2: a trigger on choosing the bottom while scrying, "that many" its count; CR 607.2a: the exiled
    // cards are linked to the play permission; CR 611.3a: only during your turn
    @Test
    fun theChronalLodestone() = card(
        name = "The Chronal Lodestone",
        cost = "{3}{U}{U}{U}",
        type = "Legendary Artifact",
        text = """
            At the beginning of your upkeep, scry 2.
            Whenever you choose to put one or more cards on the bottom of your library while scrying, exile that many cards from the bottom of your library.
            During your turn, you may play cards exiled with The Chronal Lodestone.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You), scry(2)),
        triggered(on(EventPattern.ScriedToBottom(PlayerFilter.You)),
            move(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = false), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember(arcanum.parser.EXILED_WITH, Objects.Bound("exiled")))),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You), affects = Objects.Remembered(arcanum.parser.EXILED_WITH),
            condition = Condition.YourTurn),
    )

    // CR 700.2: two different modes; CR 111.10h (Powerstone); CR 613.4c (the Construct's own size)
    @Test
    fun archivistsDecree() = card(
        name = "Archivist's Decree",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            Choose two —
            • Creatures you don't control get -2/-0 until end of turn.
            • Create a tapped Powerstone token.
            • Create a tapped 0/0 colorless Construct artifact creature token with "This token gets +1/+1 for each artifact you control."
            • Scry 1, then draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(min = 2, max = 2, options = arrayOf(
            mode(pump(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))), -2, 0)),
            mode(createPowerstone()),
            mode(doing(Effect.CreateToken(creatureToken("Construct", emptyList(), 0, 0, types = artifactCreature, abilities = listOf(
                static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), count(and(artifact, you)))))), n(1), Players.You, tapped = true))),
            mode(scry(1), draw(1)),
        )))),
    )

    // CR 401.4: the owner chooses top or bottom; CR 701.22a
    @Test
    fun unmoorTimeline() = card(
        name = "Unmoor Timeline",
        cost = "{4}{U}",
        type = "Instant",
        text = "Target nonland permanent's owner puts it on their choice of the top or bottom of their library. Scry 2.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            ifThen(Condition.Chose("position", "top"), move(target("permanent"), Zone.Library, position = 0),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            scry(2),
            targets = listOf(slot("permanent", and(not(land), permanentFilter)))),
    )

    // CR 700.2; a +1/+1 counter on a creature chosen as the mode resolves, not targeted (CR 115.10); CR 701.23a; CR 701.22a
    @Test
    fun wardensDecree() = card(
        name = "Warden's Decree",
        cost = "{1}{W}{W}",
        type = "Sorcery",
        text = """
            Choose two —
            • Create a 2/2 colorless Construct artifact creature token.
            • Put a +1/+1 counter on a creature you control. It gains double strike until end of turn.
            • Search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.
            • You gain 2 life and scry 2.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(min = 2, max = 2, options = arrayOf(
            mode(create(creatureToken("Construct", emptyList(), 2, 2, types = artifactCreature))),
            mode(Step.Choose(Choice.Objects(creatureYouControl, n(1), n(1)), bind = "chosen", chooser = Players.You),
                counters(Objects.Bound("chosen")), gains(Objects.Bound("chosen"), Keyword.DoubleStrike)),
            mode(search(and(ObjectFilter.Super(Supertype.Basic), sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
            mode(gainLife(2), scry(2)),
        )))),
    )

    // CR 702.11a, 702.12a; CR 701.22a
    @Test
    fun scholarsFlight() = card(
        name = "Scholar's Flight",
        cost = "{W}",
        type = "Instant",
        text = "Target artifact or creature gains hexproof and indestructible until end of turn. Scry 1.",
    ) parsesTo abilities(
        spell(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible), scry(1), targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 113.6m: exiling this card from the graveyard as a cost makes the ability work only there, no functionsIn needed; CR 701.22a
    @Test
    fun lastOfTheGarrison() = card(
        name = "Last of the Garrison",
        cost = "{W}",
        type = "Creature — Human Soldier",
        pt = "1/1",
        text = """
            First strike
            {1}{W}, Exile this card from your graveyard: Scry 2.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        activated(cost(mana("{1}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), scry(2)),
    )

    // CR 614.1c (enters tapped unless); CR 602.5d (as a sorcery); CR 712.4a: the melded back face as chunk 04's Sylvara
    @Test
    fun thornmereSanctumOfMoss() {
        val lands = count(and(land, you))
        val landCards = and(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
        cardFaces(Layout.Meld("Mossa, Voice of the Grove"),
            card(
                name = "Thornmere, Sanctum of Moss",
                cost = null,
                type = "Land",
                text = """
                    This land enters tapped unless you control a legendary green creature.
                    {T}: Add {G}.
                    {2}{G}{G}, {T}: Create a 2/2 green Bear creature token, then mill three cards. Activate only as a sorcery.
                    (Melds with Mossa, Voice of the Grove.)
                """,
            ) to abilities(
                static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                    condition = Condition.Not(Condition.Exists(and(legendary, ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Green)), creature, you), atLeast = 1))),
                activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
                activated(cost(mana("{2}{G}{G}"), tap), create(creatureToken("Bear", listOf(Color.Green), 2, 2)), mill(3), rules = asSorcery),
            ),
            card(
                name = "Mossa, Grove Incarnate",
                cost = null,
                type = "Legendary Creature — Elemental Avatar",
                colorIndicator = "G",
                pt = "*/*",
                text = """
                    Reach, vigilance, trample, haste
                    Mossa's power and toughness are each equal to the number of lands you control.
                    When Mossa enters, return all land cards from your graveyard to the battlefield tapped.
                    {3}{G}: Put four +1/+1 counters on target land you control. It becomes a 0/0 Elemental creature with haste. It's still a land.
                """,
            ) to abilities(
                Keyword.Reach, Keyword.Vigilance, Keyword.Trample, Keyword.Haste,
                static(Mechanic.SetPowerToughness(lands, lands), characteristicDefining = true),
                triggered(entersSelf, move(Objects.Each(landCards), Zone.Battlefield, tapped = true)),
                activated(manaCost("{3}{G}"),
                    counters(target("land"), 4),
                    doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Creature), addSubtypes = listOf(subtype("Elemental"))),
                        Mechanic.SetPowerToughness(n(0), n(0)), has(Keyword.Haste)), Affected.Objects(target("land")), Duration.Indefinitely)),
                    targets = listOf(slot("land", and(land, you)))),
            ),
        )
    }
}
