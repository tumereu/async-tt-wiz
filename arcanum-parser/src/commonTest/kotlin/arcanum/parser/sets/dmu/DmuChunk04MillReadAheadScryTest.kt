package arcanum.parser.sets.dmu

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 04: mill (CR 701.17), read ahead Sagas (CR 702.155, 714) and scry (CR 701.22), with the other abilities of
 * the cards that carry them.
 */
class DmuChunk04MillReadAheadScryTest {
    private val readAhead = "Read ahead (Choose a chapter and start with that many lore counters. Add one after your draw step. Skipped chapters don't trigger. Sacrifice after III.)"
    private val inYourHand = listOf(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val topCard = Objects.LibraryCards(Players.You, n(1), fromTop = true)
    private val opponent = Players.Target("opponent")

    // CR 702.155a, b; CR 608.2g, 601.2 (cast during the resolution, without paying, CR 118.9); CR 701.17a (mill); CR 707.12 (cast a copy of a
    // card, paying its costs)
    @Test
    fun foundingTheThirdPath() = card(
        name = "Charting the Fourth Road",
        cost = "{1}{U}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — You may cast an instant or sorcery spell with mana value 1 or 2 from your hand without paying its mana cost.
            II — Target player mills four cards.
            III — Exile target instant or sorcery card from your graveyard. Copy it. You may cast the copy.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(
            Step.Choose(Choice.Objects(and(or(instant, sorcery), or(manaValue(Comparison.Equal, 1), manaValue(Comparison.Equal, 2)), *inYourHand.toTypedArray()),
                n(0), n(1)), bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)))),
        chapters(2, steps = listOf(mill(4, Players.Target("player"))), targets = listOf(targetPlayer)),
        chapters(3, steps = listOf(
            doing(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CopyCard(Objects.Bound("exiled"), owner = Players.You, bind = "copies")),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Bound("copies")), bind = "copy", steps = listOf(
                Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), PlayKind.Cast, caster = Players.You))), Players.You)))),
            targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
    )

    // CR 702.3 (defender); CR 701.17a; CR 608.2h (counted as it resolves)
    @Test
    fun coralColony() = card(
        name = "Reefstone Bulwark",
        cost = "{1}{U}",
        type = "Creature — Wall",
        pt = "1/4",
        text = """
            Defender
            {1}{U}, {T}: Target player mills X cards, where X is the number of creatures you control with defender. (To mill a card, a player puts the top card of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{1}{U}"), tap),
            doing(Effect.Mill(Players.Target("player"), count(and(creature, you, kw(Keyword.Defender))))),
            targets = listOf(targetPlayer)),
    )

    // CR 603.6a; CR 701.17a; CR 113.6 (a graveyard ability with an exile-this-card cost); "another" excludes the card itself (CR 109.2)
    @Test
    fun eerieSoultender() = card(
        name = "Hollow Gravetender",
        cost = "{2}{B}",
        type = "Creature — Spirit Cleric",
        pt = "3/1",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            {4}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, mill(3)),
        activated(cost(mana("{4}{B}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), move(target("card"), Zone.Hand),
            targets = listOf(slot("card", and(another, creature, *yourGraveyard.toTypedArray())))),
    )

    // CR 702.155; "You may sacrifice … If you do" (CR 608.2d, 701.21); CR 118.12 (target opponent may sacrifice; if they don't); CR 115.1
    @Test
    fun braidssFrightfulReturn() = card(
        name = "Mirela's Dread Homecoming",
        cost = "{2}{B}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — You may sacrifice a creature. If you do, each opponent discards a card.
            II — Return target creature card from your graveyard to your hand.
            III — Target opponent may sacrifice a nonland, nontoken permanent of their choice. If they don't, they lose 2 life and you draw a card.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(Step.Pay(cost(sacrificeA(creature)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(discard(1, Players.Each(PlayerFilter.Opponent)))))),
        chapters(2, steps = listOf(move(target("card"), Zone.Hand)), targets = listOf(slot("card", inYourGraveyard(creature)))),
        chapters(3, steps = listOf(Step.Pay(cost(sacrificeA(and(nonland, not(token), permanentFilter))), payer = opponent, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(loseLife(2, opponent), draw(1)))),
            targets = listOf(targetOpponent)),
    )

    // CR 702.26a, b (phased out, it phases in during its controller's untap step); CR 611.2b (for as long as you control this Saga); CR 701.8;
    // CR 608.2h (the controller of each destroyed creature as it last existed)
    @Test
    fun thePhasingOfZhalfir() = card(
        name = "The Fading of Ostmere",
        cost = "{2}{U}{U}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I, II — Another target nonland permanent phases out. It can't phase in for as long as you control this Saga.
            III — Destroy all creatures. For each creature destroyed this way, its controller creates a 2/2 black Phyrexian creature token.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, 2, steps = listOf(doing(Effect.PhaseOut(target("permanent"), whileYouControl = Objects.Self))),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter)))),
        chapters(3, steps = listOf(doing(Effect.Destroy(Objects.Each(creature), preBind = "destroyed")),
            Step.ForEach(arcanum.api.Each.Objects(Objects.Bound("destroyed")), bind = "creature", steps = listOf(
                doing(Effect.CreateToken(creatureToken("Phyrexian", listOf(Color.Black), 2, 2), n(1), controller = Players.ControllerOf(Objects.Bound("creature")))))))),
    )

    // CR 702.155; CR 401.4, 701.20 (look, reveal one, the rest on the bottom in a random order); CR 305.1 (putting a land onto the battlefield isn't
    // playing it)
    @Test
    fun theWorldSpell() = card(
        name = "The Primal Litany",
        cost = "{5}{G}{G}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I, II — Look at the top seven cards of your library. You may reveal a non-Saga permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            III — Put up to two non-Saga permanent cards from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, 2, steps = listOf(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(not(sub("Saga")), permanentCard), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))),
        chapters(3, steps = listOf(
            Step.Choose(Choice.Objects(and(not(sub("Saga")), permanentCard, *inYourHand.toTypedArray()), n(0), n(2)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield))),
    )

    // CR 702.155; CR 701.22 (scry), 701.20a (reveal); CR 305.1-like putting onto the battlefield; CR 606.3 (once each turn, unless an effect allows
    // more), CR 611.2c (not a characteristic: also planeswalkers that come under your control later this turn)
    @Test
    fun urzaAssemblesTheTitans() = card(
        name = "Odric Gathers the Colossi",
        cost = "{3}{W}{W}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — Scry 4, then you may reveal the top card of your library. If a planeswalker card is revealed this way, put it into your hand.
            II — You may put a planeswalker card with mana value 6 or less from your hand onto the battlefield.
            III — You may activate the loyalty abilities of planeswalkers you control twice this turn rather than only once.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(scry(4), Step.May(listOf(doing(Effect.Reveal(topCard, bind = "revealed"))), Players.You),
            Step.If(Condition.ObjectMatches(Objects.Bound("revealed"), planeswalker), then = listOf(move(Objects.Bound("revealed"), Zone.Hand))))),
        chapters(2, steps = listOf(
            Step.Choose(Choice.Objects(and(planeswalker, manaValue(Comparison.AtMost, 6), *inYourHand.toTypedArray()), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield))),
        chapters(3, steps = listOf(doing(Effect.ApplyMechanics(listOf(Mechanic.LoyaltyActivationsPerTurn(2)), Affected.Objects(Objects.Each(and(planeswalker, you))),
            Duration.EndOfTurn)))),
    )

    // CR 702.155; CR 101.4 (both draw at once); CR 111.1; CR 115.1 (up to two targets, one counter on each)
    @Test
    fun loveSongOfNightAndDay() = card(
        name = "Ballad of Dusk and Dawn",
        cost = "{2}{W}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — You and target opponent each draw two cards.
            II — Create a 1/1 white Bird creature token with flying.
            III — Put a +1/+1 counter on each of up to two target creatures.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(draw(2, Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(opponent)))))), targets = listOf(targetOpponent)),
        chapters(2, steps = listOf(create(creatureToken("Bird", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Flying))))),
        chapters(3, steps = listOf(counters(target("creatures"))), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    // CR 702.155; CR 701.9b (you choose the card discarded); CR 701.23 (search, then shuffle); CR 110.2 (put onto the battlefield under your control)
    @Test
    fun theCrueltyOfGix() = card(
        name = "The Malice of Vorrak",
        cost = "{3}{B}{B}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — Target opponent reveals their hand. You choose a creature or planeswalker card from it. That player discards that card.
            II — Search your library for a card, put that card into your hand, then shuffle. You lose 3 life.
            III — Put target creature card from a graveyard onto the battlefield under your control.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(opponent)))), bind = "revealed")),
            Step.Choose(Choice.Objects(or(creature, planeswalker), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(opponent, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen")))),
            targets = listOf(targetOpponent)),
        chapters(2, steps = listOf(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle, loseLife(3))),
        chapters(3, steps = listOf(move(target("card"), Zone.Battlefield, controller = Players.You)),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard))))),
    )

    // CR 702.155; CR 120.2 (one source dealing damage to each); CR 701.9 (discard any number), CR 608.2c (draw that many: those discarded); CR 111.1
    @Test
    fun theElderDragonWar() = card(
        name = "The Wyrmfire Conflict",
        cost = "{2}{R}{R}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — This Saga deals 2 damage to each creature and each opponent.
            II — Discard any number of cards, then draw that many cards.
            III — Create a 4/4 red Dragon creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(damage(2, Recipients(objects = Objects.Each(creature), players = Players.Each(PlayerFilter.Opponent))))),
        chapters(2, steps = listOf(
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))), n(0), null), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"), postBind = "discarded")),
            doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("discarded")))))),
        chapters(3, steps = listOf(create(creatureToken("Dragon", listOf(Color.Red), 4, 4, abilities = listOf(Keyword.Flying))))),
    )

    // CR 702.155; CR 111.4 (a token's name); CR 603.12 (a reflexive trigger: "When you do"), CR 608.2h ("that much": the number tapped); CR 205.1b,
    // 613.4b (base power and toughness)
    @Test
    fun yotiaDeclaresWar() = card(
        name = "Kessia Marches to Battle",
        cost = "{1}{R}",
        type = "Enchantment — Saga",
        text = """
            $readAhead
            I — Create a 0/2 colorless Thopter artifact creature token with flying named Ornithopter.
            II — Tap any number of untapped artifacts you control. When you do, this Saga deals that much damage to target creature or planeswalker.
            III — Up to one target artifact you control becomes an artifact creature with base power and toughness 4/4 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.ReadAhead,
        chapters(1, steps = listOf(create(creatureToken("Thopter", emptyList(), 0, 2, abilities = listOf(Keyword.Flying),
            types = listOf(CardType.Artifact, CardType.Creature), name = "Ornithopter")))),
        chapters(2, steps = listOf(
            Step.Choose(Choice.Objects(and(not(tapped), artifact, you), n(0), null), bind = "tapped", chooser = Players.You),
            tapIt(Objects.Bound("tapped")),
            Step.If(Condition.NonEmpty("tapped"), then = listOf(
                doing(Effect.ReflexiveTrigger(Instructions(targets = listOf(slot("permanent", or(creature, planeswalker))),
                    steps = listOf(damage(Value.Count(Objects.Bound("tapped")), toObjects(target("permanent"))))))))))),
        chapters(3, steps = listOf(untilEndOfTurn(target("artifact"), Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)),
            Mechanic.SetPowerToughness(n(4), n(4)))), targets = listOf(slot("artifact", and(artifact, you), min = 0))),
    )

    // CR 702.9 (flying); CR 613.1d (type-changing, layer 4) as long as it has four or more counters (CR 205.1b: in addition to its other types);
    // CR 700.4 (dies); CR 608.2c (the count after the counter is put); CR 701.22
    @Test
    fun weatherlightCompleated() = card(
        name = "Skyreaver Corrupted",
        cost = "{2}",
        type = "Legendary Artifact — Vehicle",
        pt = "5/5",
        text = """
            Flying
            As long as Skyreaver Corrupted has four or more phyresis counters on it, it's a Phyrexian creature in addition to its other types.
            Whenever a creature you control dies, put a phyresis counter on Skyreaver Corrupted. Then draw a card if it has seven or more phyresis counters on it. If it doesn't, scry 1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Phyrexian")))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(phyresis, atLeast = 4))),
        triggered(creatureYouControlDies, counters(Objects.Self, 1, phyresis),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(phyresis, atLeast = 7)), then = listOf(draw(1)), otherwise = listOf(scry(1)))),
    )

    // CR 603.6a; CR 701.22; CR 106.1, 605.1a (two mana abilities, each with the tap cost, CR 602.5a)
    @Test
    fun crystalGrotto() = card(
        name = "Quartz Hollow",
        cost = null,
        type = "Land",
        text = """
            When this land enters, scry 1.
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(1)),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))),
    )

    // CR 700.2 (choose one as it triggers); CR 603.7 (a delayed trigger returns it), CR 400.7 (the exiled card is a new object, returned under its
    // owner's control, CR 110.2); CR 701.22
    @Test
    fun djinnOfTheFountain() = card(
        name = "Djinn of the Wellspring",
        cost = "{4}{U}{U}",
        type = "Creature — Djinn",
        pt = "4/4",
        text = """
            Flying
            Whenever you cast an instant or sorcery spell, choose one —
            • This creature gets +1/+1 until end of turn.
            • Exile this creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
            • Scry 1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        arcanum.api.Ability.Triggered(youCast(or(instant, sorcery)), Instructions(modes = modes(
            mode(pump(Objects.Self, 1, 1)),
            mode(doing(Effect.Move(Objects.Self, Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                    Instructions(steps = listOf(doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))))))),
            mode(scry(1))))),
    )

    // CR 613.4c (layer 7c), 702.7 (first strike); CR 701.22
    @Test
    fun furiousBellow() = card(
        name = "Raging Roar",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+0 and gains first strike until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 0, has(Keyword.FirstStrike)), scry(1), targets = listOf(slot("creature", creature))),
    )

    // CR 701.26a (tap), CR 122.1d (stun counters); CR 701.22
    @Test
    fun impedeMomentum() = card(
        name = "Halt the Advance",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Tap target creature and put three stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
    ) parsesTo abilities(
        spell(tapIt(target("creature")), counters(target("creature"), 3, CounterKind.Stun), scry(1), targets = listOf(slot("creature", creature))),
    )

    // CR 120.3; CR 701.22
    @Test
    fun jayasFirenado() = card(
        name = "Sela's Firestorm",
        cost = "{4}{R}",
        type = "Sorcery",
        text = "Sela's Firestorm deals 5 damage to target creature or planeswalker. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        spell(damage(5, toObjects(target("permanent"))), scry(1), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    // CR 700.4 (dies); CR 701.22
    @Test
    fun phyrexianVivisector() = card(
        name = "Phyrexian Fleshcarver",
        cost = "{1}{B}",
        type = "Creature — Phyrexian Human",
        pt = "2/2",
        text = "Whenever a creature you control dies, scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        triggered(creatureYouControlDies, scry(1)),
    )

    // CR 602.1; CR 701.22
    @Test
    fun tidepoolTurtle() = card(
        name = "Shoalback Tortoise",
        cost = "{3}{U}",
        type = "Creature — Turtle",
        pt = "2/5",
        text = "{2}{U}: Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
    ) parsesTo abilities(
        activated(cost(mana("{2}{U}")), scry(1)),
    )

    private val phyresis = CounterKind.Named("phyresis")
}
