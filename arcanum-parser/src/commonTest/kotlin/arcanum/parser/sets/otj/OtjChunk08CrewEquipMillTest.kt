package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/* OTJ chunk 08: Vehicles with crew (CR 702.122), doubling counters (CR 701.10e), Equipment (CR 702.6, 301.5) and milling (CR 701.17). */
class OtjChunk08CrewEquipMillTest {
    private val equipped: Objects = Objects.AttachedTo(Objects.Self)
    private val allPlayers = TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = null)
    private fun double(objects: Objects): Step = doing(Effect.Action(KeywordAction.Double(Doubled.Counters(objects, p1p1))))

    // CR 702.122a: crew; CR 602.5b: "Activate only once each turn" written after it limits the crew ability; CR 702.122c: the creatures
    // that crewed it
    @Test
    fun gildedParlorEngine() = card(
        name = "Gilded Parlor Engine",
        cost = "{5}",
        type = "Artifact — Vehicle",
        text = """
            Whenever this Vehicle attacks, create a Treasure token for each creature that crewed it this turn. (They're artifacts with "{T}, Sacrifice this token: Add one mana of any color.")
            Crew 1. Activate only once each turn. (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        triggered(attacksSelf, createTreasure(count(and(creature, ObjectFilter.CrewedThisTurn(Objects.Self))))),
        Keyword.Crew(1, perTurn = 1),
    )

    // CR 611.3a: haste while the condition holds; CR 702.122a
    @Test
    fun rollingRanchhouse() = card(
        name = "Rolling Ranchhouse",
        cost = "{2}",
        type = "Artifact — Vehicle",
        text = """
            This Vehicle has haste as long as you control a Mount.
            Whenever this Vehicle attacks, look at the top card of your library. If it's a land card, you may put it onto the battlefield tapped.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(has(Keyword.Haste), condition = Condition.Exists(and(sub("Mount"), you), atLeast = 1)),
        triggered(attacksSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true), players = Players.You, bind = "seen")),
            Step.If(Condition.ObjectMatches(Objects.Bound("seen"), land), then = listOf(
                Step.May(listOf(move(Objects.Bound("seen"), Zone.Battlefield, tapped = true)), Players.You)))),
        Keyword.Crew(2),
    )

    // CR 207.2c: landfall; CR 701.10e: doubling the +1/+1 counters on each creature you control
    @Test
    fun thornbackTillyBurrPlanter() = card(
        name = "Thornback Tilly, Burr Planter",
        cost = "{1}{G}",
        type = "Legendary Creature — Plant Druid",
        text = """
            Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature.
            {3}{G}{G}: Double the number of +1/+1 counters on each creature you control.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(landfall, counters(target("creature")), targets = listOf(slot("creature", creature)), abilityWord = "Landfall"),
        activated(manaCost("{3}{G}{G}"), double(Objects.Each(creatureYouControl))),
    )

    // CR 702.171b: attacks while saddled; CR 701.10e: doubling the +1/+1 counters on a target creature
    @Test
    fun cantankerousRollhog() = card(
        name = "Cantankerous Rollhog",
        cost = "{2}{G}",
        type = "Creature — Brushwagg Mount",
        text = """
            At the beginning of combat on your turn, put a +1/+1 counter on target creature.
            Whenever this creature attacks while saddled, double the number of +1/+1 counters on target creature.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")), targets = listOf(slot("creature", creature))),
        attacksWhileSaddled(double(target("creature")), targets = listOf(slot("creature", creature))),
        Keyword.Saddle(2),
    )

    // CR 610.3: exiled until it leaves; CR 607.2a: the cards exiled with it are linked to the copy ability; CR 701.3a: becoming attached;
    // CR 707.2, 611.2b: a copy of a card exiled with it for as long as it remains attached
    @Test
    fun mimicryBulwark() = card(
        name = "Mimicry Bulwark",
        cost = "{1}{W}{U}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, exile up to one target creature until this Equipment leaves the battlefield.
            Whenever this Equipment becomes attached to a creature, for as long as this Equipment remains attached to it, that creature becomes a copy of a creature card exiled with this Equipment.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self, postBind = "exiled")),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("creature", creature, min = 0))),
        triggered(on(EventPattern.Attach(ObjectFilter.Is(Objects.Self), creature)),
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Exile)), n(1), n(1), from = Objects.Remembered("exiled")),
                bind = "chosen", chooser = Players.You),
            Step.If(Condition.NonEmpty("chosen"), then = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("chosen"))),
                Affected.Objects(Objects.EventObject(EventRole.To)),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(to = Objects.EventObject(EventRole.To))))))))),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 510.1: "that many" is the combat damage dealt; CR 608.2g, 118.9: cast while the trigger resolves without paying its mana cost
    @Test
    fun theStrongboxSkeletonKey() = card(
        name = "The Strongbox Skeleton Key",
        cost = "{1}{U}",
        type = "Legendary Artifact — Equipment",
        text = """
            Whenever equipped creature deals combat damage to a player, look at that many cards from the top of your library. You may exile a nonland card from among them. Put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost.
            Equip {2}{U}
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.EventAmount, fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(nonland, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.If(Condition.NonEmpty("exiled"), then = listOf(Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast,
                withoutPaying = true, caster = Players.You))), payer = Players.You, bind = "cast")))),
        Keyword.Equip(manaCost("{2}{U}")),
    )

    // CR 111.10a: a Treasure; CR 301.5c: equipped creature gets +1/+1
    @Test
    fun prospectorsSieve() = card(
        name = "Prospector's Sieve",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Equipped creature gets +1/+1.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createTreasure()),
        static(pt(1, 1), affects = equipped),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 702.10a: haste; CR 702.21a: ward {1}
    @Test
    fun cinderheelSpurs() = card(
        name = "Cinderheel Spurs",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has haste and ward {1}. (Whenever it becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            Equip {1}
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Haste, Keyword.Ward(manaCost("{1}"))), affects = equipped),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 701.3a: attach it; CR 508.5: the defending player of the equipped creature
    @Test
    fun stormcrackLariat() = card(
        name = "Stormcrack Lariat",
        cost = "{2}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +1/+1.
            Whenever equipped creature attacks, tap target creature defending player controls.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 1), affects = equipped),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), tapIt(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(equipped))))))),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 700.13: committing a crime; CR 603.2h: once each turn; CR 701.17a: each opponent mills
    @Test
    fun siltBogBrigand() = card(
        name = "Silt-Bog Brigand",
        cost = "{2}{U}",
        type = "Creature — Homarid Mercenary",
        text = """
            Whenever you commit a crime, each opponent mills three cards. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
        """,
        pt = "2/4",
    ) parsesTo abilities(
        crime(mill(3, Players.Each(PlayerFilter.Opponent)), perTurn = 1),
    )

    // CR 701.17a: the milled cards in the graveyard; CR 609.3: a land card if able, else a Treasure
    @Test
    fun unhurriedHerbalist() = card(
        name = "Unhurried Herbalist",
        cost = "{2}{G}",
        type = "Creature — Human Scout",
        text = """
            When this creature enters, mill three cards. Put a land card from among the milled cards into your hand. If you can't, create a Treasure token. (To mill three cards, put the top three cards of your library into your graveyard.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf,
            mill(3, postBind = "milled"),
            Step.Choose(Choice.Objects(land, n(1), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.If(Condition.Not(Condition.NonEmpty("chosen")), then = listOf(createTreasure()))),
    )

    // CR 702.172a: spree; CR 701.17a; CR 702.11a, 702.12a: hexproof and indestructible until end of turn
    @Test
    fun bootleggersGambit() = card(
        name = "Bootlegger's Gambit",
        cost = "{G}",
        type = "Instant",
        text = """
            Spree (Choose one or more additional costs.)
            + {2} — Mill four cards. You may put up to two creature and/or land cards from among the milled cards into your hand.
            + {4}{G} — You may put up to two creature cards from your hand onto the battlefield.
            + {1} — Creatures you control with power 4 or greater gain hexproof and indestructible until end of turn.
        """,
    ) parsesTo spree(
        spreeMode("{2}",
            mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(or(creature, land), n(0), n(2), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        spreeMode("{4}{G}",
            Step.Choose(Choice.Objects(and(creature, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(2)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
        spreeMode("{1}", gains(Objects.Each(and(creature, you, power(Comparison.AtLeast, 4))), Keyword.Hexproof, Keyword.Indestructible)),
    )

    // CR 115.1: any number of target players; CR 701.17a, 119.3
    @Test
    fun littlebonesSignsOn() = card(
        name = "Littlebones Signs On",
        cost = "{B}",
        type = "Legendary Enchantment",
        text = """
            When Littlebones Signs On enters, any number of target players each discard a card.
            Whenever a legendary creature you control enters, any number of target players each mill a card and lose 1 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, discard(1, Players.Target("players")), targets = listOf(allPlayers)),
        triggered(enters(and(legendary, creature, you)), mill(1, Players.Target("players")), loseLife(1, Players.Target("players")), targets = listOf(allPlayers)),
    )

    // CR 702.15a: lifelink; CR 701.17a: target player mills two
    @Test
    fun franticVeinhunter() = card(
        name = "Frantic Veinhunter",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        text = """
            Lifelink
            When this creature enters, target player mills two cards. (They put the top two cards of their library into their graveyard.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, mill(2, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 702.171b: becoming saddled, the first time each turn (CR 603.2); X counted as the ability resolves (CR 608.2h)
    @Test
    fun mulishTunnelbrute() = card(
        name = "Mulish Tunnelbrute",
        cost = "{1}{G}",
        type = "Creature — Badger Beast Mount",
        text = """
            Whenever this creature becomes saddled for the first time each turn, mill two cards, then this creature gets +X/+X until end of turn, where X is the number of creature cards in your graveyard.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.GotMark(ObjectMark.Saddled, ObjectFilter.Is(Objects.Self))), nthThisTurn = 1),
            mill(2),
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(count(inYourGraveyard(creature)), count(inYourGraveyard(creature))))),
        Keyword.Saddle(2),
    )
}
