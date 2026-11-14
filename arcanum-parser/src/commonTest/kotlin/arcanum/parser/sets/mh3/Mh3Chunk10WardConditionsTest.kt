package arcanum.parser.sets.mh3

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * MH3 chunk 10: ward (CR 702.21), conditions on what was sacrificed, exiled or modified (CR 608.2h, 607.2a, 700.9), coin flips (CR 705),
 * energy (CR 107.14) and copies until end of turn (CR 707.9, 611.2a).
 */
class Mh3Chunk10WardConditionsTest {
    private val sacrificed = Objects.Bound("sacrificed")
    private fun addMana(symbols: String) = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(symbols)!!)), Players.You))
    private val entersEvent = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    // CR 603.6 (a cast trigger), 107.1a (half rounded up), 702.21a (ward with a sacrifice cost), 614.1c (enters with counters; greatest mana
    // value among cards in exile, 0 with none), 702.86a (annihilator X, X its +1/+1 counters as the trigger resolves)
    @Test
    fun vorrakTheUnmaker() = card(
        name = "Vorrak, the Unmaker",
        cost = "{10}",
        type = "Legendary Creature — Eldrazi",
        pt = "7/7",
        text = """
            When you cast this spell, target opponent exiles the top half of their library, rounded up.
            Ward—Sacrifice two permanents.
            Vorrak enters with a number of +1/+1 counters on it equal to the greatest mana value among cards in exile.
            Vorrak has annihilator X, where X is the number of +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        triggered(youCastThis,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.Target("opponent"), Value.Half(Value.Count(Objects.Each(and(inZone(Zone.Library),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))), roundUp = true), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            targets = listOf(targetOpponent), functionsIn = null),
        Keyword.Ward(cost(CostPart.Sacrifice(CostObjects.Choose(permanentFilter, count = n(2))))),
        static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersWithCounters(p1p1,
            Value.Of(NumericProperty.ManaValue, Objects.Each(and(cardFilter, inZone(Zone.Exile))), Combine.Greatest))))),
        static(Mechanic.GainAbilities(listOf(Keyword.AnnihilatorX(Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single))))),
    )

    // CR 702.126a (improvise), 702.21a; "It can't be blocked this turn" is this creature, the object the counter went on, whichever object
    // entered (audit: it was read as the entering object)
    @Test
    fun shellboreGunner() = card(
        name = "Shellbore Gunner",
        cost = "{5}{U}",
        type = "Artifact Creature — Turtle Warrior",
        pt = "4/4",
        text = """
            Improvise (Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
            Ward {4}
            Whenever this creature or another artifact you control enters, put a +1/+1 counter on this creature. It can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Keyword.Improvise,
        Keyword.Ward(manaCost("{4}")),
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, artifact, you))), counters(Objects.Self),
            untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
    )

    // CR 613.1d, 613.4b (a 0/0 creature in addition to its other types with eight or more counters), 613.1f (annihilator 2)
    @Test
    fun effigyOfHollowGods() = card(
        name = "Effigy of Hollow Gods",
        cost = "{2}",
        type = "Kindred Artifact — Eldrazi",
        text = """
            {1}{C}, {T}: Create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
            Whenever another Eldrazi you control dies, put a +1/+1 counter on this artifact.
            As long as this artifact has eight or more +1/+1 counters on it, it's a 0/0 creature in addition to its other types and it has annihilator 2.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}{C}"), tap), create(eldraziSpawn)),
        triggered(dies(and(another, sub("Eldrazi"), you)), counters(Objects.Self)),
        static(Mechanic.SetTypes(add = listOf(CardType.Creature)), Mechanic.SetPowerToughness(n(0), n(0)), has(Keyword.Annihilator(2)),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 8))),
    )

    // CR 603.4 (intervening if), 608.2h (the sacrificed creature's mana value as it last existed), 401.4 (the rest in a random order)
    @Test
    fun riteOfSpawning() = card(
        name = "Rite of Spawning",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "At the beginning of your end step, if you control a creature, look at the top seven cards of your library. Then you may sacrifice a creature. If you do, you may put a creature card with mana value X or less from among those cards onto the battlefield, where X is 1 plus the sacrificed creature's mana value. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(yourEndStep,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "looked")),
            Step.Pay(cost(sacrificeA(creature, preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, Value.Sum(listOf(n(1),
                    Value.Of(NumericProperty.ManaValue, sacrificed, Combine.Single))))), n(0), n(1), from = Objects.Bound("looked")), bind = "chosen",
                    chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            interveningIf = Condition.Exists(creatureYouControl, atLeast = 1)),
    )

    // CR 608.2h: "that creature's power" is the sacrificed creature's as it last existed (it was read as the entering creature's); CR 712.8
    // (a modal double-faced card's back face)
    @Test
    fun acolyteOfTheGrove() = cardFaces(
        Layout.ModalDoubleFaced,
        card(
            name = "Acolyte of the Grove",
            cost = "{3}{G}{G}{G}",
            type = "Creature — Elf Druid",
            pt = "3/3",
            text = "When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power.",
        ) to abilities(
            triggered(entersSelf,
                Step.Pay(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(
                    Step.Do(Effect.GainLife(Players.You, power(sacrificed))),
                    Step.Do(Effect.Draw(Players.You, power(sacrificed)))))),
        ),
        card(
            name = "Grove of the Acolyte",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {G}.
            """,
        ) to abilities(
            static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.AsEnters(listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(n(3)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped)))))))),
            addOne("{G}"),
        ),
    )

    // CR 608.2h: "that creature" after "you may sacrifice another creature" is the sacrificed creature as it last existed, not the creature
    // that entered (it was read as the entering creature)
    @Test
    fun groveTenderOfBones() = card(
        name = "Grove Tender of Bones",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        pt = "2/2",
        text = "When this creature enters, you may sacrifice another creature. If you do, you gain life equal to that creature's power.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Pay(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.GainLife(Players.You, power(sacrificed)))))),
    )

    // CR 601.3, 608.2g (cast without paying its mana cost during the resolution), 608.2h (the triggering spell's mana value as it last existed)
    @Test
    fun scalesOfRetort() = card(
        name = "Scales of Retort",
        cost = "{R}{R}",
        type = "Enchantment",
        text = "Whenever an opponent casts a spell, you may reveal the top card of your library. If you do, you may cast that card without paying its mana cost if the two spells have the same mana value.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.Opponent)),
            Step.Pay(steps = listOf(Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, n(1), fromTop = true), bind = "revealed"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.May(listOf(Step.Do(Effect.Cast(Objects.Bound("revealed"), PlayKind.Cast, withoutPaying = true,
                caster = Players.You, resultingSpell = ObjectFilter.ManaValue(Comparison.Equal, Value.Of(NumericProperty.ManaValue,
                    Objects.EventObject(EventRole.Object), Combine.Single))))), player = Players.You)))),
    )

    // CR 607.2a: "exiled with this land" and "the exiled card" are the card its imprint ability exiled, while it stays in exile
    @Test
    fun mazeOfTheSpiritDragon() = card(
        name = "Maze of the Spirit Dragon",
        cost = null,
        type = "Land",
        text = """
            Imprint — When this land enters, you may exile a colorless card with mana value 7 or greater from your hand.
            {T}: Add {C}. If a card is exiled with this land, add {C}{C} instead.
            {T}: Return the exiled card to its owner's hand.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(ObjectFilter.Colorless, cardFilter, manaValue(Comparison.AtLeast, 7), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                n(0), n(1)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Remember("imprinted", Objects.Bound("exiled"))),
            abilityWord = "Imprint"),
        activated(cost(tap), Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Remembered("imprinted"), inZone(Zone.Exile))), Comparison.AtLeast, n(1)),
            then = listOf(addMana("{C}{C}")), otherwise = listOf(addMana("{C}")))),
        activated(cost(tap), move(Objects.Remembered("imprinted"), Zone.Hand)),
    )

    // CR 614.1a (one more energy counter), 107.14 (energy paid or lost this turn: counters removed from you)
    @Test
    fun sparkcoilDynamo() = card(
        name = "Sparkcoil Dynamo",
        cost = "{U}{R}",
        type = "Artifact",
        text = """
            If you would get one or more {E} (energy counters), you get that many plus one {E} instead.
            {T}: Draw a card. Activate only if you've paid or lost four or more {E} this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.PlayerCountersPut(PlayerFilter.You, CounterKind.Energy), modify = listOf(Modification.AddAmount(n(1))))),
        activated(cost(tap), draw(1), rules = anyTime.copy(condition = Condition.HappenedThisTurn(
            EventPattern.PlayerCountersRemoved(PlayerFilter.You, CounterKind.Energy), atLeast = 4, measure = Measure.Amount))),
    )

    // CR 601.2b (one of two additional costs), 700.9 (modified), 608.2h (the sacrificed creature as it last existed, whichever cost was chosen)
    @Test
    fun brutalTakedown() = card(
        name = "Brutal Takedown",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or sacrifice a modified creature. (Equipment, Auras you control, and counters are modifications.)
            Destroy target creature or planeswalker. If the modified creature was sacrificed, draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(listOf(cost(sacrificeA(creature, preBind = "sacrificed")),
            cost(sacrificeA(and(creature, ObjectFilter.Modified), preBind = "sacrificed"))))))),
        spell(destroy(target("permanent")), Step.If(Condition.ObjectMatches(sacrificed, and(creature, ObjectFilter.Modified)), then = listOf(draw(1))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker))))),
    )

    // CR 608.2h ("its toughness": the sacrificed Angel's as it last existed), 614.1c (finality counters as they enter), 205.1b (Vampires in addition)
    @Test
    fun vorskLordOfHunger() = card(
        name = "Vorsk, Lord of Hunger",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Elder Demon",
        pt = "6/6",
        text = """
            Flying
            Sacrifice another creature: Create a Blood token. If you sacrificed an Angel this way, create a number of Blood tokens equal to its toughness instead.
            {W/B}{W/B}{W/B}, Sacrifice six Blood tokens: Return each creature card from your graveyard to the battlefield with a finality counter on it. Those creatures are Vampires in addition to their other types.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(sacrificeA(and(another, creature), preBind = "sacrificed")), Step.If(Condition.ObjectMatches(sacrificed, sub("Angel")),
            then = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Blood"), toughness(sacrificed), Players.You))),
            otherwise = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Blood"), n(1), Players.You))))),
        activated(cost(mana("{W/B}{W/B}{W/B}"), CostPart.Sacrifice(CostObjects.Choose(and(sub("Blood"), token), count = n(6)))),
            Step.Do(Effect.Move(Objects.Each(inYourGraveyard(creature)), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Finality, n(1))),
                entersAs = listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vampire")))))), postBind = "returned"))),
    )

    // CR 118.9 (an alternative cost), 119.7, 119.8 (a life total that can't change: no life gained or lost), 611.2c (the permanents at resolution)
    @Test
    fun blazeOfSteadfastness() = card(
        name = "Blaze of Steadfastness",
        cost = "{2}{W}{W}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken white creature rather than pay this spell's mana cost.
            Until end of turn, your life total can't change, and permanents you control gain hexproof and indestructible.
        """,
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = cost(sacrificeA(and(not(token), ObjectFilter.OfColor(arcanum.api.Color.White), creature))))),
        spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantGainLife, Mechanic.CantLoseLife), Affected.Players(Players.You), Duration.EndOfTurn)),
            untilEndOfTurn(Objects.Each(and(permanentFilter, you)), Mechanic.GainAbilities(listOf(Keyword.Hexproof, Keyword.Indestructible)))),
    )

    // CR 705 (a coin flip), 115.7 (new targets), 701.6a (counter)
    @Test
    fun reverseCharge() = card(
        name = "Reverse Charge",
        cost = "{U}{U}{R}",
        type = "Instant",
        text = "Choose target spell, then flip a coin. If you win the flip, gain control of that spell and you may choose new targets for it. If you lose the flip, counter that spell.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.FlipCoin(players = Players.You, bind = "flip")),
            Step.If(Condition.WonFlip("flip"), then = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("spell")), Duration.Indefinitely)),
                Step.Do(Effect.ChangeTargets(target("spell"), Players.You, optional = true)))),
            Step.If(Condition.Not(Condition.WonFlip("flip")), then = listOf(Step.Do(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 207.2c (grandeur, an ability word; the name holds a comma), 602.1 (a cost of two parts)
    @Test
    fun pyraFlamecaller() = card(
        name = "Pyra, Flamecaller",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Goblin Wizard",
        pt = "4/4",
        text = """
            When Pyra enters, it deals 4 damage to any target.
            Grandeur — Discard another card named Pyra, Flamecaller, Sacrifice two Mountains: Pyra deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(4, toAny), targets = listOf(anyTarget)),
        arcanum.api.Ability.Activated(Cost(listOf(CostPart.Discard(CostObjects.Choose(and(ObjectFilter.Named("Pyra, Flamecaller"), not(ObjectFilter.Is(Objects.Self))), n(1))),
            CostPart.Sacrifice(CostObjects.Choose(sub("Mountain"), count = n(2))))),
            arcanum.api.Instructions(targets = listOf(anyTarget), steps = listOf(damage(4, toAny))), anyTime, abilityWord = "Grandeur"),
    )

    // CR 601.2f, 602.2b (a cost reduced by {4}{B} once three cards were drawn this turn), 602.5b (once each turn)
    @Test
    fun spiritOfEnviousHunger() = card(
        name = "Spirit of Envious Hunger",
        cost = "{2}{B}",
        type = "Creature — Spirit",
        pt = "1/3",
        text = """
            Deathtouch
            {4}{B}: Each opponent loses 2 life and you gain 2 life. This ability costs {4}{B} less to activate if you've drawn three or more cards this turn. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        activated(cost(mana("{4}{B}")), loseLife(2, Players.Each(PlayerFilter.Opponent)), gainLife(2),
            rules = anyTime.copy(perTurn = 1, costChange = CostChange.Less(ManaCost.parse("{4}{B}")!!, Value.Min(listOf(
                Value.Max(listOf(Value.Difference(Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events), n(2)), n(0))), n(1)))))),
    )

    // CR 614.12, 707.9b (a copy as it enters, except with haste), 611.2a (until end of turn: then it is this artifact again)
    @Test
    fun hauntedLookingGlass() = card(
        name = "Haunted Looking Glass",
        cost = "{2}{R}",
        type = "Artifact",
        text = """
            {T}: Add {R}.
            As this artifact enters, you may have it become a copy of any creature on the battlefield until end of turn, except it has haste.
        """,
    ) parsesTo abilities(
        addOne("{R}"),
        static(Mechanic.Replacement(entersEvent, modify = listOf(Modification.EntersAsCopy(creature, listOf(CopyException.AddAbilities(listOf(Keyword.Haste))),
            optional = true, duration = Duration.EndOfTurn)))),
    )
}
