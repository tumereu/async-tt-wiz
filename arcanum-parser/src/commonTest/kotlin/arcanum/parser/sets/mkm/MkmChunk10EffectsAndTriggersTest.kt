package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
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
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 10: putting cards onto the battlefield and returning them (CR 400.7, 303.4f, 708), revealing and piles
 * (CR 701.20), sacrifices and searches (CR 701.21, 701.23), "attacked this combat" and must-block (CR 506.4, 509.1c), last known toughness
 * (CR 603.10a), draws this turn, attack batches (CR 508.1), reflexive triggers (CR 603.12) and Case solving as an event (CR 719.3). */
class MkmChunk10EffectsAndTriggersTest {
    private val x = Value.Announced("X")
    private val haste = Mechanic.GainAbilities(listOf(Keyword.Haste))
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val nextEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any))

    // CR 122.1b (stun counters), 611.2c: only the tapped targets you don't control get one, as it resolves
    @Test
    fun snarledLabyrinth() = card(
        name = "Snarled Labyrinth",
        cost = "{X}{U}{U}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, tap X target creatures. Put a stun counter on each of those creatures you don't control. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Tapped creatures you control have hexproof.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            entersSelf,
            tapIt(target("creature")),
            counters(Objects.Where(target("creature"), ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))), kind = CounterKind.Stun),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(creature), min = Value.CastX(Objects.Self), max = Value.CastX(Objects.Self))),
        ),
        static(has(Keyword.Hexproof), affects = Objects.Each(and(tapped, creature, you))),
    )

    // CR 709.3 (split card halves), 115.1 (targets from a single graveyard: one owner), 603.7 (sacrifice at the next end step)
    @Test
    fun shoveHaul() = cardFaces(
        Layout.Split,
        card(name = "Shove", cost = "{1}{W/B}", type = "Sorcery", text = "Destroy target tapped creature.") to abilities(
            spell(destroy(target("creature")), targets = listOf(slot("creature", and(tapped, creature)))),
        ),
        card(
            name = "Haul",
            cost = "{4}{B/R}{B/R}",
            type = "Sorcery",
            text = "Put up to two target creature cards from a single graveyard onto the battlefield under your control. They gain haste until end of turn. Sacrifice them at the beginning of the next end step.",
        ) to abilities(
            spell(
                move(target("cards"), Zone.Battlefield, controller = Players.You, postBind = "returned"),
                untilEndOfTurn(Objects.Bound("returned"), haste),
                delayed(nextEndStep, doing(Effect.Sacrifice(Objects.Bound("returned")))),
                targets = listOf(TargetSlot("cards", TargetKind.Objects(and(creature, inZone(Zone.Graveyard))), min = n(0), max = n(2), sameOwner = true)),
            ),
        ),
    )

    // CR 111.1 (the token), 122.1a, 107.3c: X counted as it resolves, cards you own in exile and in your graveyard
    @Test
    fun oozeAgainstOrder() = card(
        name = "Ooze Against Order",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Create a 0/0 green Ooze creature token with trample. Put X +1/+1 counters on it, where X is two plus the total number of cards you own in exile and in your graveyard that are Oozes or are named Ooze Against Order.
            A deck can have any number of cards named Ooze Against Order.
        """,
    ) parsesTo abilities(
        spell(
            create(creatureToken("Ooze", listOf(Color.Green), 0, 0, abilities = listOf(Keyword.Trample)), bind = "token"),
            counters(Objects.Bound("token"), Value.Sum(listOf(n(2), count(and(or(inZone(Zone.Exile), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You),
                or(sub("Ooze"), ObjectFilter.Named("Ooze Against Order"))))))),
        ),
        static(Mechanic.AnyNumberInDeck),
    )

    // CR 107.3a (X), 305.4 (lands put onto the battlefield aren't played): up to X from your hand and/or graveyard, chosen as it resolves
    @Test
    fun earthbloodFury() = card(
        name = "Earthblood Fury",
        cost = "{X}{R}{G}",
        type = "Sorcery",
        text = "Earthblood Fury deals X damage to any target. Put up to X land cards from your hand and/or graveyard onto the battlefield tapped.",
    ) parsesTo abilities(
        spell(
            damage(x, toAny),
            Step.Choose(Choice.Objects(and(land, or(inZone(Zone.Hand), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = x),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            targets = listOf(anyTarget),
            announced = listOf("X"),
        ),
    )

    // CR 603.10a: "other" than the card that died, chosen while it is still in the graveyard
    @Test
    fun unlawfulMasque() = card(
        name = "Unlawful Masque",
        cost = "{3}{B}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, put an impostor counter on each creature you control.
            Whenever a creature you control with an impostor counter on it dies, exile it. Return up to one other target creature card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, counters(Objects.Each(creatureYouControl), kind = CounterKind.Named("impostor"))),
        triggered(
            dies(and(creature, you, ObjectFilter.HasCounter(CounterKind.Named("impostor"), 1))),
            move(Objects.EventObject(EventRole.After), Zone.Exile, postBind = "exiled"),
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", and(not(ObjectFilter.Is(Objects.EventObject(EventRole.After))), creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0)),
        ),
    )

    // CR 301.5c, 303.4f: an Equipment put onto the battlefield attached to this creature
    @Test
    fun lodestoneSniffer() = card(
        name = "Lodestone Sniffer",
        cost = "{5}",
        type = "Artifact Creature — Construct",
        pt = "4/4",
        text = """
            When this creature enters, return target Equipment card from your graveyard to the battlefield attached to this creature.
            Whenever you sacrifice an artifact, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, attachedTo = Recipients(objects = Objects.Self)))),
            targets = listOf(slot("card", inYourGraveyard(equipment))),
        ),
        triggered(on(EventPattern.Sacrifice(and(artifact, you))), counters(Objects.Self)),
    )

    // CR 115.1 (three slots, each up to one), 613.1d, 613.4b: 5/5 Elemental creatures in addition to their types, for as long as they stay
    @Test
    fun revisitTheBygone() = card(
        name = "Revisit the Bygone",
        cost = "{5}{G}{W}",
        type = "Sorcery",
        text = "Return up to one target artifact card, up to one target land card, and up to one target non-Aura enchantment card from your graveyard to the battlefield. They are 5/5 Elemental creatures in addition to their other types.",
    ) parsesTo abilities(
        spell(
            move(Objects.Union(listOf(target("artifact card"), target("land card"), target("non-Aura enchantment card"))), Zone.Battlefield, postBind = "returned"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(5), n(5))), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            targets = listOf(
                slot("artifact card", inYourGraveyard(artifact), min = 0),
                slot("land card", inYourGraveyard(land), min = 0),
                slot("non-Aura enchantment card", inYourGraveyard(not(sub("Aura")), enchantment), min = 0),
            ),
        ),
    )

    // CR 708.2a (a face-down 2/2 without a mechanic), 708.8 (turned face up by an effect, not entering), 400.7 (the card in the graveyard)
    @Test
    fun varukHowlOfTheElders() = card(
        name = "Varuk, Howl of the Elders",
        cost = "{2}{R}{G}",
        type = "Legendary Creature — Centaur Druid",
        pt = "4/4",
        text = """
            Other creatures you control have haste.
            Whenever one or more face-down creatures you control deal combat damage to a player, draw a card.
            Whenever a face-down creature you control dies, return it to the battlefield face down under its owner's control if it's a permanent card, then turn it face up.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(another, creature, you))),
        Trigger.On(listOf(EventPattern.Damage(and(ObjectFilter.HasStatus(Status.FaceDown), creature, you), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            oneOrMore = true, batchPerPlayer = true).let { triggered(it, draw(1)) },
        triggered(
            dies(and(ObjectFilter.HasStatus(Status.FaceDown), creature, you)),
            ifThen(
                Condition.ObjectMatches(Objects.EventObject(EventRole.After), permanentCard),
                doing(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, faceDown = true,
                    controller = Players.OwnerOf(Objects.EventObject(EventRole.After))), postBind = "returned")),
                doing(Effect.TurnFaceUp(Objects.Bound("returned"))),
            ),
        ),
    )

    // CR 701.20a (look, reveal), 401.4 (bottom in a random order); the revealed card goes to your hand unless it was put onto the battlefield
    @Test
    fun burstLoose() = card(
        name = "Burst Loose",
        cost = "{R}{G}",
        type = "Sorcery",
        text = "Look at the top six cards of your library. You may reveal a creature card from among them. If that card has mana value 2 or less, you may put it onto the battlefield and it gains haste until end of turn. If you didn't put the revealed card onto the battlefield this way, put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(creature, min = n(0), max = n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            ifThen(
                Condition.ObjectMatches(Objects.Bound("chosen"), manaValue(Comparison.AtMost, 2)),
                Step.May(listOf(move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"), untilEndOfTurn(Objects.Bound("put"), haste)), player = Players.You),
            ),
            ifThen(Condition.Not(Condition.NonEmpty("put")), move(Objects.Bound("chosen"), Zone.Hand)),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 701.20a (reveal), two piles: you separate, an opponent chooses; the counters count the cards that reached your graveyard (CR 608.2c)
    @Test
    fun pryIntoTheThoughts() = card(
        name = "Pry into the Thoughts",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = "Reveal the top five cards of your library and separate them into two piles. An opponent chooses one of those piles. Put that pile into your hand and the other into your graveyard. Create a 0/0 colorless Thopter artifact creature token with flying, then put a +1/+1 counter on it for each card put into your graveyard this way.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(5), fromTop = true), bind = "revealed")),
            Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
            doing(Effect.SeparatePiles(Objects.Bound("revealed"), separator = Players.You, chooser = Players.Bound("opponent"), chosenBind = "chosen pile",
                otherBind = "other pile")),
            move(Objects.Bound("chosen pile"), Zone.Hand),
            move(Objects.Bound("other pile"), Zone.Graveyard, postBind = "put"),
            create(creatureToken("Thopter", emptyList(), 0, 0, abilities = listOf(Keyword.Flying), types = listOf(CardType.Artifact, CardType.Creature)), bind = "token"),
            counters(Objects.Bound("token"), Value.Count(Objects.Where(Objects.Bound("put"), inZone(Zone.Graveyard)))),
        ),
    )

    // CR 603.7: "your next end step" is your turn's end step (this turn's when it hasn't begun)
    @Test
    fun franticThopterwright() = card(
        name = "Frantic Thopterwright",
        cost = "{3}{R}",
        type = "Creature — Human Artificer",
        pt = "2/3",
        text = "At the beginning of combat on your turn, create a 1/1 colorless Thopter artifact creature token with flying. It gains haste until end of turn. Sacrifice it at the beginning of your next end step.",
    ) parsesTo abilities(
        triggered(
            beginningOfCombatOnYourTurn,
            create(thopter, bind = "token"),
            untilEndOfTurn(Objects.Bound("token"), haste),
            delayed(Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.You)), doing(Effect.Sacrifice(Objects.Bound("token")))),
        ),
    )

    // CR 118.12: the target opponent may sacrifice two; if they don't, you draw
    @Test
    fun zarkosLordOfDisorder() = card(
        name = "Zarkos, Lord of Disorder",
        cost = "{4}{B}{R}",
        type = "Legendary Creature — Demon",
        pt = "6/6",
        text = """
            Flying, trample
            At the beginning of your end step, target opponent may sacrifice two nonland, nontoken permanents of their choice. If they don't, you draw two cards.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        triggered(
            yourEndStep,
            Step.Pay(arcanum.api.Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(nonland, not(token), permanentFilter), count = n(2))))),
                payer = Players.Target("opponent"), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), draw(2)),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 701.23 (search of several zones; shuffle only if the library was searched), one card of each name at most
    @Test
    fun bureauQuartermaster() = card(
        name = "Bureau Quartermaster",
        cost = "{4}{U}{U}",
        type = "Creature — Sphinx Detective",
        pt = "4/3",
        text = """
            Flying
            When this creature enters, you may search your graveyard, hand and/or library for a card named Lens of Clues and/or a card named Sleuth's Cap and put them onto the battlefield. If you search your library this way, shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            entersSelf,
            Step.May(listOf(
                doing(Effect.Search(Players.You, or(ObjectFilter.Named("Lens of Clues"), ObjectFilter.Named("Sleuth's Cap")), max = n(2),
                    zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library), differentNames = true, bind = "found", searcher = Players.You)),
                move(Objects.Bound("found"), Zone.Battlefield),
                ifThen(Condition.Searched("found", Zone.Library), shuffle),
            ), player = Players.You),
        ),
    )

    // CR 506.4 ("attacked this combat" stays true after removal from combat), 509.1c (a requirement to block that attacker)
    @Test
    fun ravsimirDusksLantern() = card(
        name = "Ravsimir, Dusk's Lantern",
        cost = "{2}{G}{W}{W}",
        type = "Legendary Creature — Elf Scout",
        pt = "3/2",
        text = """
            Lifelink
            When Ravsimir enters, create Vorna Shadowpaw, a legendary 5/5 green and white Wolf creature token with trample.
            Whenever a Wolf you control attacks, if Ravsimir attacked this combat, target creature an opponent controls blocks that Wolf this combat if able.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, create(arcanum.api.TokenSpec.Defined(arcanum.api.FaceDefinition("Vorna Shadowpaw", colorIndicator = listOf(Color.Green, Color.White),
            supertypes = listOf(Supertype.Legendary), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Wolf")),
            power = arcanum.api.PrintedNumber.Fixed(5), toughness = arcanum.api.PrintedNumber.Fixed(5), abilities = listOf(Keyword.Trample))))),
        triggered(
            on(EventPattern.Attack(and(sub("Wolf"), you))),
            Step.Bind(Objects.EventObject(EventRole.Object), "attacker"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MustBlock(ObjectFilter.Is(Objects.Bound("attacker")))), Affected.Objects(target("creature")), Duration.EndOfCombat)),
            targets = listOf(slot("creature", creatureOpponentControls)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.AttackedThisCombat),
        ),
    )

    // CR 702.80a (wither), 603.10a: the toughness it last had on the battlefield
    @Test
    fun carnageLassKnownSlayer() = card(
        name = "Carnage Lass, Known Slayer",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Human Assassin",
        pt = "4/4",
        text = """
            Menace
            Creatures you control have wither. (They deal damage to creatures in the form of -1/-1 counters.)
            Whenever a creature an opponent controls dies, if its toughness was less than 1, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        static(has(Keyword.Wither), affects = Objects.Each(creatureYouControl)),
        triggered(dies(creatureOpponentControls), draw(1),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.Toughness(Comparison.Less, n(1)))),
    )

    // CR 121.2 (each card drawn is a draw), 402.2 (no maximum hand size): "more than one" is two or more
    @Test
    fun quillsPerfectRecall() = card(
        name = "Quill's Perfect Recall",
        cost = "{1}{U}",
        type = "Legendary Enchantment",
        text = """
            When Quill's Perfect Recall enters, draw a card.
            You have no maximum hand size.
            At the beginning of combat on your turn, if you've drawn more than one card this turn, put X +1/+1 counters on target creature you control, where X is the number of cards you've drawn this turn minus one.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        playerStatic(Mechanic.NoMaximumHandSize),
        triggered(
            beginningOfCombatOnYourTurn,
            counters(target("creature"), Value.Difference(Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events), n(1))),
            targets = listOf(slot("creature", creatureYouControl)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = 2, measure = Measure.Events),
        ),
    )

    // CR 508.1: whoever attacks, one trigger for a declaration of that many attackers
    @Test
    fun aurelixTheCodeAbove() = card(
        name = "Aurelix, the Code Above",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Angel",
        pt = "4/4",
        text = """
            Flying, vigilance, haste
            Whenever a player attacks with three or more creatures, you draw a card.
            Whenever a player attacks with five or more creatures, Aurelix deals 3 damage to each of your opponents and you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Haste,
        triggered(Trigger.On(listOf(EventPattern.Attack(creature)), oneOrMore = true, atLeast = 3), draw(1)),
        triggered(Trigger.On(listOf(EventPattern.Attack(creature)), oneOrMore = true, atLeast = 5), damage(3, eachOpponent), gainLife(3)),
    )

    // CR 508.1, 506.2 (the attacking player is the active player), 603.4 (the attackers must still be attacking as it resolves)
    @Test
    fun tormikBearerOfLaw() = card(
        name = "Tormik, Bearer of Law",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/4",
        text = """
            Affinity for planeswalkers (This spell costs {1} less to cast for each planeswalker you control.)
            Flying, vigilance
            Whenever an opponent attacks with creatures, if two or more of those creatures are attacking you and/or planeswalkers you control, that opponent loses 3 life and you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(planeswalker, "planeswalkers"),
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(
            Trigger.On(listOf(EventPattern.Attack(creatureOpponentControls, target = RecipientFilter(objects = and(planeswalker, you), players = PlayerFilter.You))),
                oneOrMore = true, atLeast = 2),
            loseLife(3, Players.Active),
            draw(1),
            interveningIf = Condition.Compare(Value.Count(Objects.Where(Objects.EventObject(EventRole.Object), attacking)), Comparison.AtLeast, n(2)),
        ),
    )

    // CR 603.12 (reflexive trigger), 608.2h: the greatest mana value among the cards discarded
    @Test
    fun untimelyBlast() = card(
        name = "Untimely Blast",
        cost = "{2}{U}{R}",
        type = "Sorcery",
        text = "Draw two cards. Then you may discard two cards. When you do, Untimely Blast deals X damage to each creature, where X is the greatest mana value among cards discarded this way.",
    ) parsesTo abilities(
        spell(
            draw(2),
            Step.Pay(arcanum.api.Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(2)), postBind = "discarded"))), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), reflexive(damage(Value.Of(NumericProperty.ManaValue, Objects.Bound("discarded"), Combine.Greatest), toObjects(Objects.Each(creature))))),
        ),
    )

    // CR 719.3a, 719.3b: solving a Case is its becoming solved; CR 609.4b: mana of any colour for Case spells
    @Test
    fun dossierExaminer() = card(
        name = "Dossier Examiner",
        cost = "{2}{W}",
        type = "Creature — Human Detective",
        pt = "1/4",
        text = """
            When this creature enters and whenever you solve a Case, look at the top six cards of your library. You may reveal an enchantment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            You may spend mana as though it were mana of any color to cast Case spells.
        """,
    ) parsesTo abilities(
        triggered(
            Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.GotMark(ObjectMark.Solved, and(sub("Case"), you)))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(enchantment, min = n(0), max = n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
        Ability.Static(listOf(Mechanic.SpendManaAsAnyColor(onlyFor = and(spellFilter, sub("Case")))), affects = Affected.Players(Players.You)),
    )

    // CR 701.21a: only a permanent's controller sacrifices it; "them" is that opponent
    @Test
    fun spitefulPursuer() = card(
        name = "Spiteful Pursuer",
        cost = "{1}{R}",
        type = "Creature — Human Detective",
        pt = "2/2",
        text = "Whenever an opponent sacrifices an artifact, this creature deals 2 damage to them.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Sacrifice(and(artifact, opponents))), damage(2, Recipients(players = Players.EventPlayer))),
    )
}
