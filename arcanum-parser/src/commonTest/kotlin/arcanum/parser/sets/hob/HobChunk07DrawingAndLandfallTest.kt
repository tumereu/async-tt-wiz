package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
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
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 07: drawing and landfall. "Your second card each turn" and an opponent's (the nth draw of that player this
 * turn, CR 603.2), "whenever you draw a card" (once per card, CR 121.2), a count of graveyards with seven or more cards, landfall
 * (an ability word, CR 207.2c) with modes (CR 700.2), base power and toughness set until end of turn (layer 7b), a permanent
 * becoming a creature with a characteristic-defining ability for good (CR 611.2a, 604.3), the cast spell's mana value and the
 * mana spent on it, a land discarded this way put onto the battlefield, draws and token creation replaced (CR 614.1a).
 */
class HobChunk07DrawingAndLandfallTest {
    // CR 603.2: that opponent's second draw this turn; CR 115.1: two target players
    @Test
    fun glitteringRiches() = card(
        name = "Glittering Riches",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            Whenever an opponent draws their second card each turn, you create a Treasure token.
            {2}{W}: Two target players each draw a card.
        """,
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Draw(PlayerFilter.Opponent)), nthThisTurn = 2), createTreasure()),
        activated(cost(mana("{2}{W}")), draw(1, Players.Target("players")),
            targets = listOf(TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(2), max = n(2)))),
    )

    // CR 603.2
    @Test
    fun meresideHerbalist() = card(
        name = "Mereside Herbalist",
        cost = "{1}{U}",
        type = "Creature — Human Cleric",
        text = """
            Vigilance
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(drawSecondCard, counters(Objects.Self)),
    )

    // CR 404: each graveyard is one player's
    @Test
    fun mayorsAdvisers() = card(
        name = "Mayor's Advisers",
        cost = "{1}{U}",
        type = "Creature — Human Advisor",
        text = """
            Vigilance
            This creature gets +2/+0 for each graveyard with seven or more cards in it.
            Whenever you draw your second card each turn, target player mills three cards. (They put the top three cards of their library into their graveyard.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        static(Mechanic.ModifyPowerToughness(Value.Times(Value.CountPlayers(graveyardsWithSeven), 2), n(0))),
        triggered(drawSecondCard, mill(3, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 603.2
    @Test
    fun brandTheArcher() = card(
        name = "Brand the Archer",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Archer",
        text = """
            Reach
            Whenever you draw your second card each turn, put a +1/+1 counter on target creature. It gains lifelink until end of turn.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(drawSecondCard, counters(target("creature")), gains(target("creature"), Keyword.Lifelink), targets = listOf(slot("creature", creature))),
    )

    // CR 121.2: once for each card drawn
    @Test
    fun crowhillFlock() = card(
        name = "Crowhill Flock",
        cost = "{3}{U}",
        type = "Creature — Bird",
        text = """
            Flying
            Whenever you draw a card, put a +1/+1 counter on this creature.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Draw(PlayerFilter.You)), counters(Objects.Self)),
    )

    // CR 207.2c, 700.2
    @Test
    fun elvenBargePilot() = card(
        name = "Elven Barge-Pilot",
        cost = "{2}{U}",
        type = "Creature — Elf Pilot",
        text = """
            Landfall — Whenever a land you control enters, choose one —
            • Tap target creature an opponent controls.
            • Untap target creature you control.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Ability.Triggered(landfall, Instructions(modes = modes(
            mode(tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
            mode(untapIt(target("creature")), targets = listOf(slot("creature", creatureYouControl))))), abilityWord = "Landfall"),
    )

    // CR 613.4b: layer 7b
    @Test
    fun gloomwoodContemplator() = card(
        name = "Gloomwood Contemplator",
        cost = "{2}{U}",
        type = "Creature — Elf Druid",
        text = "Landfall — Whenever a land you control enters, you may have this creature's base power and toughness become 4/2 until end of turn.",
        pt = "2/4",
    ) parsesTo abilities(
        triggered(landfall, Step.May(listOf(untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(n(4), n(2)))), player = Players.You), abilityWord = "Landfall"),
    )

    @Test
    fun cobwebLurker() = card(
        name = "Cobweb Lurker",
        cost = "{1}{G}",
        type = "Creature — Spider",
        text = """
            Reach, deathtouch
            Landfall — Whenever a land you control enters, this creature gets +1/+1 until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Deathtouch,
        triggered(landfall, pump(Objects.Self, 1, 1), abilityWord = "Landfall"),
    )

    // CR 611.2a; 604.3a: the granted ability isn't characteristic-defining (layer 7b)
    @Test
    fun barnulfsWelcome() = card(
        name = "Barnulf's Welcome",
        cost = "{1}{G}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control.
            {5}{G}{G}: This enchantment becomes a Bear creature in addition to its other types and gains "This creature's power and toughness are each equal to the number of lands you control." (This effect doesn't end.)
        """,
    ) parsesTo abilities(
        triggered(landfall, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)), abilityWord = "Landfall"),
        activated(cost(mana("{5}{G}{G}")), Step.Do(Effect.ApplyMechanics(listOf(
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bear")))),
            has(static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you)))))),
            Affected.Objects(Objects.Self), Duration.Indefinitely))),
    )

    // CR 701.20: the revealed card goes to the hand, the rest to the bottom in a random order
    @Test
    fun branchsideRovers() = card(
        name = "Branchside Rovers",
        cost = "{4}{G}{G}",
        type = "Creature — Elf Scout",
        text = """
            When this creature enters, look at the top four cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Landfall — Whenever a land you control enters, this creature gets +2/+2 until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        triggered(landfall, pump(Objects.Self, 2, 2), abilityWord = "Landfall"),
    )

    // CR 202.3: the cast spell's mana value
    @Test
    fun revelsFromDuskToDawn() = card(
        name = "Revels from Dusk to Dawn",
        cost = "{3}{G}{G}",
        type = "Enchantment",
        text = """
            Whenever you cast a creature spell, put X +1/+1 counters on target creature you control, where X is that spell's mana value.
            Landfall — Whenever a land you control enters, create a 2/2 green Bear creature token.
        """,
    ) parsesTo abilities(
        triggered(youCast(creature), counters(target("creature"), Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single)),
            targets = listOf(slot("creature", creatureYouControl))),
        triggered(landfall, create(bear), abilityWord = "Landfall"),
    )

    // CR 305.2: an additional land each of your turns while you control another Elf
    @Test
    fun elriansRetinue() = card(
        name = "Elrian's Retinue",
        cost = "{2}{G}{U}",
        type = "Creature — Elf Soldier",
        text = """
            As long as you control another Elf, you may play an additional land on each of your turns.
            Landfall — Whenever a land you control enters, put two +1/+1 counters on target creature you control. It gains vigilance until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        playerStatic(Mechanic.ExtraLandPlays(1), condition = Condition.Exists(and(another, sub("Elf"), you), atLeast = 1)),
        triggered(landfall, counters(target("creature"), 2), gains(target("creature"), Keyword.Vigilance), targets = listOf(slot("creature", creatureYouControl)),
            abilityWord = "Landfall"),
    )

    // CR 400.7: the discarded land, a new object in the graveyard; 113.6m: the landfall ability works from the graveyard
    @Test
    fun woodlandMerrymaker() = card(
        name = "Woodland Merrymaker",
        cost = "{2}{G}{U}",
        type = "Creature — Elf Citizen",
        text = """
            When this creature enters, draw a card, then discard a card. If you discard a land card this way, put it from your graveyard onto the battlefield tapped.
            Landfall — Whenever a land you control enters, you may pay {1}{G}{U}. If you do, return this card from your graveyard to your hand.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, postBind = "discarded")),
            Step.If(Condition.ObjectMatches(Objects.Bound("discarded"), and(land, inZone(Zone.Graveyard))), then = listOf(
                move(Objects.Bound("discarded"), Zone.Battlefield, tapped = true)))),
        triggered(landfall, Step.Pay(cost(mana("{1}{G}{U}")), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(move(Objects.Self, Zone.Hand))),
            abilityWord = "Landfall", functionsIn = listOf(Zone.Graveyard)),
    )

    // CR 118.12: "you may draw … If you do"; 601.2h: the mana spent to cast that spell
    @Test
    fun revealTheMoonRunes() = card(
        name = "Reveal the Moon-Runes",
        cost = "{3}{U}",
        type = "Enchantment",
        text = "Whenever you cast a noncreature spell, you may draw X cards, where X is the amount of mana spent to cast that spell. If you do, discard two cards.",
    ) parsesTo abilities(
        triggered(youCast(noncreatureSpell),
            Step.Pay(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.ManaSpent(of = Objects.EventObject(EventRole.Object))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(discard(2)))),
    )

    @Test
    fun fortunateFind() = card(
        name = "Fortunate Find",
        cost = "{3}{U}{U}",
        type = "Enchantment",
        text = "Whenever an artifact you control enters, draw a card.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), draw(1)),
    )

    // CR 614.11: draws replaced except the draw step's first; 614.1a: twice that many tokens
    @Test
    fun brandKingOfTheVale() = card(
        name = "Brand, King of the Vale",
        cost = "{4}{W}{U}",
        type = "Legendary Creature — Human Noble Archer",
        text = """
            Reach, vigilance
            If you would draw a card except the first one you draw in each of your draw steps, draw two cards instead.
            If one or more tokens would be created under your control, twice that many of those tokens are created instead.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You, exceptFirstIn = TurnStep.Draw), instead = Instructions(steps = listOf(draw(2))))),
        static(Mechanic.Replacement(EventPattern.CreateToken(you), modify = listOf(Modification.MultiplyAmount(2)))),
    )
}
