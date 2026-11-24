package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Chunk 1: Channel (a discard-from-hand ability, CR 207.2c) and its cards. */
class NeoChunk01ChannelTest {
    private val flying = and(creature, ObjectFilter.HasKeyword(Keyword.Flying.kind))
    private val vehicle = sub("Vehicle")
    private val topOrBottom = arrayOf(
        Step.Choose(Choice.Option(options = listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(objects = target("permanent"))),
        Step.If(Condition.Chose(name = "position", option = "top"),
            then = listOf(move(target("permanent"), Zone.Library, position = 0)),
            otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
    )

    // CR 613.4c (+1/+1 for each), 301.5 (an Aura's enchanted permanent), 205.1a (a creature only while it is one); CR 111.1 (tokens), 702.122 (crew)
    @Test
    fun bornToRoam() = card(
        name = "Born to Roam",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets +1/+1 for each creature and/or Vehicle you control.
            Channel — {2}{W}, Discard this card: Create two 1/1 colorless Pilot creature tokens with "This token crews Vehicles as though its power were 2 greater."
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(
            Mechanic.ModifyPowerToughness(count(and(or(creature, vehicle), you)), count(and(or(creature, vehicle), you))),
            affects = enchanted,
            condition = Condition.ObjectMatches(enchanted, creature),
        ),
        channel("{2}{W}", create(pilotToken, count = n(2))),
    )

    // CR 113.6 (an ability that works from hand), 107.3 (X in a cost), 115.1 (X targets), 205.4 (nonlegendary)
    @Test
    fun shigekiJukaiVisionary() = card(
        name = "Shikoku, Jukai Seer",
        cost = "{1}{G}",
        type = "Legendary Enchantment Creature — Snake Druid",
        pt = "1/3",
        text = """
            {1}{G}, {T}, Return Shikoku to its owner's hand: Reveal the top four cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest into your graveyard.
            Channel — {X}{X}{G}{G}, Discard this card: Return X target nonlegendary cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{G}"), tap, CostPart.ReturnToHand(CostObjects.Self)),
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Zone.Graveyard)),
        channel("{X}{X}{G}{G}", move(target("cards"), Zone.Hand), announced = listOf("X"),
            targets = listOf(TargetSlot("cards", TargetKind.Objects(inYourGraveyard(not(legendary))), min = Value.Announced("X"), max = Value.Announced("X")))),
    )

    @Test
    fun bambooGroveSentry() = card(
        name = "Bamboo Grove Sentry",
        cost = "{1}{G}",
        type = "Enchantment Creature — Snake Archer",
        pt = "3/3",
        text = """
            Defender, reach
            Channel — {4}{G}, Discard this card: Destroy target creature with flying.
        """,
    ) parsesTo abilities(
        Keyword.Defender, Keyword.Reach,
        channel("{4}{G}", destroy(target("creature")), targets = listOf(slot("creature", flying))),
    )

    // CR 205.1a: "is a creature" reads the enchanted permanent (audit: the bonus went to the Aura itself, a misread of an earlier rule)
    @Test
    fun carefulGardening() = card(
        name = "Careful Gardening",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets +1/+3 and has reach and "{T}: Add {G}{G}."
            Channel — {1}{G}, Discard this card: Create a 1/1 green Human Monk creature token with "{T}: Add {G}."
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(
            pt(1, 3), has(Keyword.Reach, addMana("{G}{G}")), 
            affects = enchanted,
            condition = Condition.ObjectMatches(enchanted, creature),
        ),
        channel("{1}{G}", create(creatureToken("Human Monk", listOf(Color.Green), 1, 1, abilities = listOf(addMana("{G}"))))),
    )

    // CR 111.10 n/a; CR 702.1 (ward) and the channel's two abilities from one card
    @Test
    fun colossalSeaTurtle() = card(
        name = "Colossal Sea Turtle",
        cost = "{4}{G}{G}{U}",
        type = "Enchantment Creature — Turtle",
        pt = "6/5",
        text = """
            Flying, ward {2}
            Channel — {2}{G}, Discard this card: Return target card from your graveyard to your hand.
            Channel — {1}{U}, Discard this card: Return target creature to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Ward(manaCost("{2}")),
        channel("{2}{G}", move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
        channel("{1}{U}", move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))),
    )

    // CR 506.4 (attacking or blocking), 120.3 (damage), the cost reduction of CR 601.2f for abilities (602.2b)
    @Test
    fun eigenjoHallOfTheEmpire() = card(
        name = "Eiganjo, Hall of the Throne",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {W}.
            Channel — {2}{W}, Discard this card: It deals 4 damage to target attacking or blocking creature. This ability costs {1} less to activate for each legendary creature you control.
        """,
    ) parsesTo abilities(
        addMana("{W}"),
        channel("{2}{W}", damage(4, Recipients(objects = target("creature"))), costLess = perLegendaryCreature,
            targets = listOf(slot("creature", and(or(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Blocking)), creature)))),
    )

    // CR 205.1a, 613.4c: the Aura's bonus applies only to a creature; the channel ability is an instant-speed pump (CR 611.2a)
    @Test
    fun favorOfTheGrove() = card(
        name = "Favor of the Grove",
        cost = "{3}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets +3/+3 and has reach.
            Channel — {1}{G}, Discard this card: Target creature gets +3/+3 and gains reach until end of turn.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(pt(3, 3), has(Keyword.Reach), affects = enchanted, condition = Condition.ObjectMatches(enchanted, creature)),
        channel("{1}{G}", pump(target("creature"), 3, 3, has(Keyword.Reach)), targets = listOf(slot("creature", creature))),
    )

    // CR 701.23 (search), 305.1 n/a
    @Test
    fun lesserTanuki() = card(
        name = "Lesser Tanuki",
        cost = "{4}{G}{G}",
        type = "Enchantment Creature — Dog",
        pt = "6/5",
        text = """
            Trample
            Channel — {2}{G}, Discard this card: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        channel("{2}{G}", search(basicLand(), bind = "found"), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun ironhoofBull() = card(
        name = "Ironhoof Bull",
        cost = "{5}{R}",
        type = "Artifact Creature — Boar",
        pt = "5/4",
        text = """
            Trample, haste
            Channel — {1}{R}, Discard this card: Target creature gets +3/+1 and gains trample until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample, Keyword.Haste,
        channel("{1}{R}", pump(target("creature"), 3, 1, has(Keyword.Trample)), targets = listOf(slot("creature", creature))),
    )

    // CR 122.1 (counters), 115.1 (up to two targets), 601.2c
    @Test
    fun jukaiKeeper() = card(
        name = "Jukai Keeper",
        cost = "{3}{G}",
        type = "Enchantment Creature — Human Druid",
        pt = "3/3",
        text = """
            When this creature enters, put a +1/+1 counter on target creature you control.
            Channel — {2}{G}, Discard this card: Put a +1/+1 counter on each of up to two target creatures you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
        channel("{2}{G}", counters(target("creatures")), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
    )

    // CR 701.6 (counter), 118.12 (unless pays), 702.1 (ward)
    @Test
    fun mirrorshellLobster() = card(
        name = "Mirrorshell Lobster",
        cost = "{5}{U}{U}",
        type = "Artifact Creature — Crab",
        pt = "5/7",
        text = """
            Ward {3} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {3}.)
            Channel — {2}{U}, Discard this card: Counter target spell or ability unless its controller pays {3}.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{3}")),
        channel("{2}{U}", Step.Pay(manaCost("{3}"), payer = Players.ControllerOf(target("permanent")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Counter(target("permanent")))),
            targets = listOf(slot("permanent", or(spellFilter, ObjectFilter.Category(ObjectCategory.StackAbility))))),
    )

    // CR 121.1 (draw), 701.21 (sacrifice)
    @Test
    fun mnemonicOrb() = card(
        name = "Mnemonic Orb",
        cost = "{1}{U}",
        type = "Artifact",
        text = """
            {1}{U}, Sacrifice this artifact: Draw two cards.
            Channel — {U}, Discard this card: Draw a card.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{U}"), sacrificeSelf), draw(2)),
        channel("{U}", draw(1)),
    )

    // CR 602.2 (tap costs), 106.1 (colorless mana), 400.? n/a; "their choice of the top or bottom" CR 401.4
    @Test
    fun moonsnareModel() = card(
        name = "Moonsnare Model",
        cost = "{U}",
        type = "Artifact",
        text = """
            {T}, Tap an untapped artifact or creature you control: Add {C}.
            Channel — {4}{U}, Discard this card: The owner of target nonland permanent puts it on their choice of the top or bottom of their library.
        """,
    ) parsesTo abilities(
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), count = n(1)))), Step.Do(Effect.AddMana(
            ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        channel("{4}{U}", *topOrBottom, targets = listOf(slot("permanent", and(not(land), permanentFilter)))),
    )

    // CR 701.7 n/a; CR 400.7 (bounce), 603.1: an end step trigger returning this creature (CR 400.7: only if it's still on the battlefield)
    @Test
    fun reinforcedRoninRetreats() = card(
        name = "Reinforced Samurai",
        cost = "{R}",
        type = "Artifact Creature — Human Samurai",
        pt = "2/2",
        text = """
            Haste
            At the beginning of your end step, return this creature to its owner's hand.
            Channel — {1}{R}, Discard this card: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(yourEndStep, move(Objects.Self, Zone.Hand)),
        channel("{1}{R}", draw(1)),
    )

    // CR 207.2c (landfall), 122.1, 205.1b ("still a land"), 107.3 (X)
    @Test
    fun roaringSoil() = card(
        name = "Roaring Soil",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature or Vehicle you control.
            Channel — {X}{G}{G}, Discard this card: Put X +1/+1 counters on target land you control. It becomes a 0/0 green Spirit creature with haste. It's still a land.
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(target("permanent")), abilityWord = "Landfall",
            targets = listOf(slot("permanent", and(or(creature, vehicle), you)))),
        channel("{X}{G}{G}",
            counters(target("land"), Value.Announced("X")),
            doing(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
                Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green))),
                Mechanic.SetPowerToughness(n(0), n(0)), has(Keyword.Haste)), Affected.Objects(target("land")), Duration.Indefinitely)),
            targets = listOf(slot("land", and(land, you))), announced = listOf("X")),
    )

    // CR 502.3 (doesn't untap during the next untap step), 115.1 (up to two targets)
    @Test
    fun saibaWanderers() = card(
        name = "Saiba Wanderers",
        cost = "{4}{U}",
        type = "Artifact Creature — Moonfolk Rogue",
        pt = "3/5",
        text = "Channel — {3}{U}, Discard this card: Tap up to two target creatures you don't control. Those creatures don't untap during their controller's next untap step.",
    ) parsesTo abilities(
        channel("{3}{U}", tapIt(target("creatures")), doesntUntapNext(target("creatures")),
            targets = listOf(slot("creatures", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), min = 0, max = 2))),
    )

    // CR 111.1 (tokens), 611.2a (haste until end of turn)
    @Test
    fun sokenzanForge() = card(
        name = "Sokenzan, Forge of Spite",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {R}.
            Channel — {3}{R}, Discard this card: Create two 1/1 colorless Spirit creature tokens. They gain haste until end of turn. This ability costs {1} less to activate for each legendary creature you control.
        """,
    ) parsesTo abilities(
        addMana("{R}"),
        channel("{3}{R}", create(creatureToken("Spirit", emptyList(), 1, 1), n(2), bind = "tokens"), gains(Objects.Bound("tokens"), Keyword.Haste),
            costLess = perLegendaryCreature),
    )

    // CR 701.23 (search), 701.20 (reveal), 119.1 (gain life)
    @Test
    fun sunbeamSamurai() = card(
        name = "Sunbeam Samurai",
        cost = "{4}{W}",
        type = "Enchantment Creature — Human Samurai",
        pt = "4/4",
        text = """
            Vigilance (Attacking doesn't cause this creature to tap.)
            Channel — {2}, Discard this card: Search your library for a basic Plains card, reveal it, put it into your hand, then shuffle. You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        channel("{2}", search(and(ObjectFilter.Super(Supertype.Basic), sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle, gainLife(2)),
    )

    // CR 701.17 (mill), 700.2 n/a: "then return a creature or planeswalker card" is a choice made on resolution (CR 115.1: not targeted)
    @Test
    fun takenumaSunkenMire() = card(
        name = "Takenuma, Sunken Mire",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {B}.
            Channel — {3}{B}, Discard this card: Mill three cards, then return a creature or planeswalker card from your graveyard to your hand. This ability costs {1} less to activate for each legendary creature you control.
        """,
    ) parsesTo abilities(
        addMana("{B}"),
        channel("{3}{B}", mill(3),
            Step.Choose(Choice.Objects(and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), *yourGraveyard.toTypedArray()), n(1), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            costLess = perLegendaryCreature),
    )

    // CR 610.3 (exile until it leaves), 603.6a; the channel ability: exile and return at the next end step (CR 610.3 n/a, 603.7)
    @Test
    fun touchTheSpiritWorld() = card(
        name = "Touch the Spirit World",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, exile up to one target artifact or creature until this enchantment leaves the battlefield.
            Channel — {1}{W}, Discard this card: Exile target artifact or creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", or(artifact, creature), min = 0))),
        channel("{1}{W}", move(target("permanent"), Zone.Exile, postBind = "exiled"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any),
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            targets = listOf(slot("permanent", or(artifact, creature)))),
    )

    // CR 120.3 (damage from the source), 115.1 (any target)
    @Test
    fun twinshotArcher() = card(
        name = "Twinshot Archer",
        cost = "{3}{R}",
        type = "Artifact Creature — Goblin Archer",
        pt = "2/3",
        text = """
            Reach
            When this creature enters, it deals 2 damage to any target.
            Channel — {1}{R}, Discard this card: It deals 2 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(entersSelf, damage(2, toAny), targets = listOf(anyTarget)),
        channel("{1}{R}", damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 205.1a n/a; legendary-creature cost reduction (CR 601.2f, 602.2b)
    @Test
    fun otawaraRisingCity() = card(
        name = "Otawara, Rising City",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}: Add {U}.
            Channel — {3}{U}, Discard this card: Return target artifact, creature, enchantment, or planeswalker to its owner's hand. This ability costs {1} less to activate for each legendary creature you control.
        """,
    ) parsesTo abilities(
        addMana("{U}"),
        channel("{3}{U}", move(target("permanent"), Zone.Hand), costLess = perLegendaryCreature,
            targets = listOf(slot("permanent", or(artifact, creature, enchantment, ObjectFilter.Type(CardType.Planeswalker))))),
    )
}
