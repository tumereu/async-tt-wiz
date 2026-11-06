package arcanum.parser.sets.dft

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
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
 * Aetherdrift chunk 15: multicoloured cards: tokens for each card in the graveyard, a search for up to two cards, ward with two costs, a
 * pump for each card discarded with a damage trigger this turn (CR 603.7b), extra +1/+1 counters (CR 614.1a), reanimation with a finality
 * counter and a delayed exile (CR 603.7), a blink to the next end step, drain by artifacts, attacking only with cards in exile, cards exiled
 * as it enters and copies of them (CR 707.9b), discard and draw, damage that can't be prevented (CR 615.12), cards put third from the top,
 * odd and even counters (CR 700.? zero is even), and untapping at the end step.
 */
class DftChunk15MulticoloredTest {
    @Test
    fun aatcheekEmeraldHerald() = card(
        name = "Aatcheek, Emerald Herald",
        cost = "{3}{B}{B}{G}",
        type = "Legendary Creature — Insect Druid",
        text = """
            When Aatcheek enters, create a 1/1 green Insect creature token for each artifact and/or creature card in your graveyard.
            Whenever another Insect you control dies, put a +1/+1 counter on Aatcheek. Each opponent loses 1 life.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, create(creatureToken("Insect", listOf(Color.Green), 1, 1), count = count(inYourGraveyard(or(artifact, creature))))),
        triggered(dies(and(another, sub("Insect"), you)), counters(Objects.Self), loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    @Test
    fun brightglassColossus() = card(
        name = "Brightglass Colossus",
        cost = "{G}{G}{W}{W}",
        type = "Artifact Creature — Construct",
        text = """
            First strike, trample
            When this creature enters, you may search your library for up to two artifact, creature, and/or enchantment cards with mana value 1 or less, reveal them, put them into your hand, then shuffle.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Trample,
        triggered(entersSelf, Step.May(listOf(
            search(and(or(artifact, creature, enchantment), manaValue(Comparison.AtMost, 1)), max = n(2), reveal = true),
            move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
    )

    @Test
    fun broadsideVolley() = card(
        name = "Broadside Volley",
        cost = "{1}{U}{R}",
        type = "Instant",
        text = "Broadside Volley deals 5 damage to target creature or planeswalker. Draw a card, then discard a card.",
    ) parsesTo abilities(
        spell(damage(5, toObjects(target("permanent"))), draw(1), discard(1), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    @Test
    fun broodheartDevice() = card(
        name = "Broodheart Device",
        cost = "{B}{G}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, surveil 1.
            {2}{B}{G}, {T}, Sacrifice this artifact: Return target creature or Vehicle card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, surveil(1)),
        activated(cost(mana("{2}{B}{G}"), tap, sacrificeSelf), move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creatureOrVehicle))),
            rules = asSorcery),
    )

    // CR 603.7b: the damage trigger works this turn, for that creature
    @Test
    fun captainYowlerSeaTerror() = card(
        name = "Captain Yowler, Sea Terror",
        cost = "{2}{U}{R}",
        type = "Legendary Creature — Shark Pirate",
        text = """
            Ward—{2}, Pay 2 life.
            Whenever you discard one or more cards, target creature gets +2/+0 until end of turn for each card discarded this way. Whenever that creature deals combat damage to a player this turn, you draw a card.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Ward(cost(mana("{2}"), CostPart.PayLife(n(2)))),
        triggered(youDiscardCards,
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(thatManyDiscarded, 2), n(0))),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.Damage(ObjectFilter.Is(target("creature")), RecipientFilter(players = PlayerFilter.Any), combat = true)),
                Instructions(steps = listOf(draw(1))), duration = Duration.EndOfTurn, repeats = true)),
            targets = listOf(slot("creature", creature))),
    )

    // CR 614.1a: the event puts one more counter
    @Test
    fun caradoreHeartOfAlacria() = card(
        name = "Caradore, Heart of Alacria",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Human Knight",
        text = """
            When Caradore enters, you may search your library for a Mount or Vehicle card, reveal it, put it into your hand, then shuffle.
            If one or more +1/+1 counters would be put on a creature or Vehicle you control, that many plus one +1/+1 counters are put on it instead.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.May(listOf(search(mountOrVehicle, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle), player = Players.You)),
        static(Mechanic.Replacement(EventPattern.CountersPut(and(creatureOrVehicle, you), p1p1), modify = listOf(Modification.AddAmount(n(1))))),
    )

    // CR 603.7: the exile at your next end step is about that creature only
    @Test
    fun coalstokeColossus() = card(
        name = "Coalstoke Colossus",
        cost = "{1}{B}{B}{R}{R}",
        type = "Artifact Creature — Construct",
        text = """
            Menace, deathtouch
            When this creature enters, put target creature card with mana value 4 or less from a graveyard onto the battlefield under your control with a finality counter on it. That creature gains menace, deathtouch, and haste. At the beginning of your next end step, exile that creature.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Deathtouch,
        triggered(entersSelf,
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, controller = Players.You, counters = listOf(CountersToPut(CounterKind.Finality, n(1)))),
                postBind = "returned")),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Menace, Keyword.Deathtouch, Keyword.Haste)), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            delayed(yourEndStep, move(Objects.Bound("returned"), Zone.Exile)),
            targets = listOf(slot("card", and(creature, manaValue(Comparison.AtMost, 4), inZone(Zone.Graveyard))))),
    )

    // CR 603.7: the card returns under its owner's control at the next end step
    @Test
    fun explosiveEscape() = card(
        name = "Explosive Escape",
        cost = "{3}{R}{W}",
        type = "Sorcery",
        text = """
            Exile up to one target artifact or creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
            Explosive Escape deals 4 damage to each creature.
        """,
    ) parsesTo abilities(
        spell(
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any),
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")))))),
            damage(4, toObjects(Objects.Each(creature))),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(or(artifact, creature))), min = 0))),
    )

    @Test
    fun hauntTheGrid() = card(
        name = "Haunt the Grid",
        cost = "{3}{U}{B}",
        type = "Sorcery",
        text = "Choose target opponent. Create two 1/1 colorless Thopter artifact creature tokens with flying. Then the chosen player loses X life and you gain X life, where X is the number of artifacts you control.",
    ) parsesTo abilities(
        spell(create(thopter, count = n(2)),
            doing(Effect.LoseLife(Players.Target("opponent"), count(and(artifact, you)))),
            doing(Effect.GainLife(Players.You, count(and(artifact, you)))),
            targets = listOf(targetOpponent)),
    )

    @Test
    fun ketramosTheBrightDawn() = card(
        name = "Ketramos, the Bright Dawn",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — God",
        text = """
            Menace, lifelink, indestructible
            Ketramos can't attack or block unless there are seven or more cards in exile.
            Whenever one or more cards are put into exile from graveyards and/or the battlefield during your turn, you draw a card and lose 1 life.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Lifelink,
        Keyword.Indestructible,
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(Condition.Exists(and(cardFilter, inZone(Zone.Exile)), atLeast = 7))),
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(cardFilter, from = Zone.Graveyard, to = Zone.Exile),
            EventPattern.ZoneChange(cardFilter, from = Zone.Battlefield, to = Zone.Exile)), oneOrMore = true), draw(1), loseLife(1), triggersOnlyIf = Condition.YourTurn),
    )

    // CR 614.12: the cards exiled as it enters; CR 707.9b: the copy's exceptions; CR 607.2a: "exiled with it"
    @Test
    fun mimeoplastExaltedOne() = card(
        name = "Mimeoplast, Exalted One",
        cost = "{X}{B}{G}{U}",
        type = "Legendary Creature — Ooze",
        text = """
            As Mimeoplast enters, exile up to X creature cards from your graveyard. It enters with three +1/+1 counters on it for each creature card exiled this way.
            {2}: Mimeoplast becomes a copy of target creature card exiled with it, except it's 0/0 and has this ability.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Choose(creature, count = Value.CastX(Objects.Self), upTo = true), from = Zone.Graveyard,
                postBind = "exiled", remember = "exiled"))), payer = Players.You, mandatory = true, bind = "paid"),
            Step.Modify(Modification.EntersWithCounters(p1p1, Value.Times(Value.Count(Objects.Bound("exiled")), 3)))))))),
        activated(cost(mana("{2}")), doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("card"), except = listOf(CopyException.PowerToughness(0, 0),
            CopyException.ThisAbility))), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            targets = listOf(slot("card", and(creature, ObjectFilter.Is(Objects.Remembered("exiled")))))),
    )

    @Test
    fun oildeepColossus() = card(
        name = "Oildeep Colossus",
        cost = "{U}{U}{B}{B}",
        type = "Artifact Creature — Construct",
        text = """
            Lifelink, ward {1}
            When this creature enters, look at target player's hand. You may choose a card from it. If you do, that player discards that card, then draws a card.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Lifelink,
        Keyword.Ward(manaCost("{1}")),
        triggered(entersSelf,
            doing(Effect.LookAt(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.If(Condition.NonEmpty("chosen"), then = listOf(
                doing(Effect.Discard(Players.Target("player"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                draw(1, Players.Target("player")))),
            targets = listOf(targetPlayer)),
    )

    // CR 615.12: no damage can be prevented this turn
    @Test
    fun pyrewoodColossus() = card(
        name = "Pyrewood Colossus",
        cost = "{2}{R}{R}{G}{G}",
        type = "Artifact Creature — Construct",
        text = """
            Vigilance, menace
            When this creature enters, other creatures you control get +2/+2 and gain vigilance and menace until end of turn. Damage can't be prevented this turn.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Menace,
        triggered(entersSelf, pump(Objects.Each(and(another, creature, you)), 2, 2, has(Keyword.Vigilance, Keyword.Menace)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), null, Duration.EndOfTurn))),
    )

    // CR 601.2c: one target for each opponent
    @Test
    fun riptideColossus() = card(
        name = "Riptide Colossus",
        cost = "{1}{W}{W}{U}{U}",
        type = "Artifact Creature — Construct",
        text = """
            Double strike
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, for each opponent, put up to one target nonland permanent that player controls into its owner's library third from the top.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Keyword.Prowess,
        triggered(entersSelf, move(target("permanent"), Zone.Library, position = 2),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))), min = 0)
                .copy(perPlayer = PlayerFilter.Opponent))),
    )

    // CR 113.? : zero counters is even; the draw checks the count after the counter is put
    @Test
    fun sabSunarLuxaIncarnate() = card(
        name = "Sab-Sunar, Luxa Incarnate",
        cost = "{3}{G}{U}",
        type = "Legendary Creature — God",
        text = """
            Reach, trample, indestructible
            Sab-Sunar can't attack or block unless it has an even number of counters on it. (Zero is even.)
            At the beginning of your first main phase, put a +1/+1 counter on Sab-Sunar. Then if it has an odd number of counters on it, draw two cards.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        Keyword.Indestructible,
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Odd(Value.Of(NumericProperty.Counters(null), Objects.Self, arcanum.api.Combine.Single))),
        triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You), counters(Objects.Self),
            Step.If(Condition.Odd(Value.Of(NumericProperty.Counters(null), Objects.Self, arcanum.api.Combine.Single)), then = listOf(draw(2)))),
    )

    @Test
    fun veteranBeastrunner() = card(
        name = "Veteran Beastrunner",
        cost = "{1}{G}{W}",
        type = "Creature — Human Knight",
        text = """
            At the beginning of your end step, untap each creature you control.
            {2}{G}{W}: Creatures you control get +1/+1 until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(yourEndStep, untapIt(Objects.Each(creatureYouControl))),
        activated(cost(mana("{2}{G}{W}")), pump(Objects.Each(creatureYouControl), 1, 1)),
    )

    @Test
    fun voyageHomeward() = card(
        name = "Voyage Homeward",
        cost = "{5}{W}{U}",
        type = "Sorcery",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            You draw three cards and gain 3 life.
        """,
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), spell(draw(3), gainLife(3)))
}
