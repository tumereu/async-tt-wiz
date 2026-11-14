package arcanum.parser.sets.inr

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Choice
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import arcanum.api.Trigger
import arcanum.api.Duration
import arcanum.api.RecipientFilter
import kotlin.test.Test

/**
 * INR chunk 07: conditions ("if you do … repeat", "while your library has no cards in it", "unless a player has no cards in
 * hand", "the sacrificed creature was a Human"), cost reductions, counters of spells and abilities, copies and tokens.
 */
class InrChunk07ConditionsCopiesCountersTest {
    private val yourHand = listOf(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val yourLibraryEmpty = Condition.Not(Condition.Exists(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))
    private val died = EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard)
    private fun stackAbility(kind: AbilityKind) = and(ObjectFilter.Category(ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = kind))

    // CR 608.2c (repeat the process while the player does it), 305.4 (putting a land isn't playing it), 604.3 (CDA)
    @Test
    fun tillageBehemoth() = card(
        name = "Tillage Behemoth",
        cost = "{4}{G}{G}{G}",
        type = "Creature — Plant Beast",
        pt = "*/*",
        text = """
            Trample
            Tillage Behemoth's power and toughness are each equal to the number of lands you control.
            When this creature enters, you may put a land card from your hand onto the battlefield tapped. If you do, draw a card and repeat this process.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you))), characteristicDefining = true),
        triggered(entersSelf, Step.RepeatWhile(listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(land) + yourHand), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            Step.If(Condition.NonEmpty("chosen"), then = listOf(draw(1)))), Condition.NonEmpty("chosen"))),
    )

    // CR 614.1a (a replacement while the condition holds), 104.2b (an effect may say a player wins), 121.4
    @Test
    fun archiveLunatic() = card(
        name = "Archive Lunatic",
        cost = "{2}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            If you would draw a card while your library has no cards in it, you win the game instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = Instructions(steps = listOf(doing(Effect.WinGame(Players.You))))),
            condition = yourLibraryEmpty),
    )

    // CR 608.2h: the sacrificed creature as it last existed; CR 602.1 (sacrifice as a cost)
    @Test
    fun gravecourtTormentor() = card(
        name = "Gravecourt Tormentor",
        cost = "{2}{B}",
        type = "Creature — Vampire",
        pt = "2/1",
        text = """
            Sacrifice a creature: This creature gains flying until end of turn. If the sacrificed creature was a Human, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        activated(cost(sacrificeA(creature, preBind = "sacrificed")), gains(Objects.Self, Keyword.Flying),
            Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), sub("Human")), then = listOf(counters(Objects.Self)))),
    )

    // CR 508.1c, 509.1b (restrictions), 402 (any player's hand)
    @Test
    fun houndAutomaton() = card(
        name = "Hound Automaton",
        cost = "{2}",
        type = "Artifact Creature — Wolf Construct",
        pt = "5/5",
        text = """
            This creature can't attack or block unless a player has no cards in hand.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(Condition.PlayerExists(
            PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.Equal, n(0))), atLeast = 1))),
    )

    // CR 603.2, 601.2i (a cast trigger), 712 (meld pair), 101.2 (the back face's restriction)
    @Test
    fun brunhildTheDimmingLight() = cardFaces(Layout.Meld("Gilda, the Shattered Edge"),
        card(
            name = "Brunhild, the Dimming Light",
            cost = "{5}{W}{W}",
            type = "Legendary Creature — Angel Horror",
            pt = "5/7",
            text = """
                When you cast this spell, you may return target Angel or Human creature card from your graveyard to the battlefield.
                Flying, vigilance
                (Melds with Gilda, the Shattered Edge.)
            """,
        ) to abilities(
            triggered(youCast(ObjectFilter.Is(Objects.Self)), Step.May(listOf(move(target("card"), Zone.Battlefield)), Players.You),
                targets = listOf(slot("card", inYourGraveyard(or(sub("Angel"), sub("Human")), creature)))),
            Keyword.Flying, Keyword.Vigilance,
        ),
        card(
            name = "Briseis, Voice of Dread",
            cost = null,
            type = "Legendary Creature — Eldrazi Angel",
            colorIndicator = "W",
            pt = "9/10",
            text = """
                Flying, first strike, vigilance, lifelink
                Your opponents can't cast spells with mana value 3 or less.
            """,
        ) to abilities(Keyword.Flying, Keyword.FirstStrike, Keyword.Vigilance, Keyword.Lifelink,
            Ability.Static(listOf(Mechanic.CantCast(and(spellFilter, manaValue(Comparison.AtMost, 3)))), affects = Affected.Players(Players.Each(PlayerFilter.Opponent)))),
    )

    // CR 601.2f (cost reductions, each with its condition)
    @Test
    fun hauntingTrap() = card(
        name = "Haunting Trap",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            This spell costs {1} less to cast if you control a Spirit. It also costs {1} less to cast if you control an enchantment.
            Counter target spell unless its controller pays {3}.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(sub("Spirit"), you), atLeast = 1)),
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, n(1))),
            condition = Condition.Exists(and(enchantment, you), atLeast = 1)),
        spell(Step.Pay(manaCost("{3}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 700.2 (modes chosen as the trigger is put on the stack), 104.3e (an effect says a player loses), 101.4 (each player)
    @Test
    fun thirteenfoldDread() = card(
        name = "Thirteenfold Dread",
        cost = "{3}{B}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, choose one —
            • Each player with exactly 13 life loses the game, then each player gains 1 life.
            • Each player with exactly 13 life loses the game, then each player loses 1 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), Instructions(modes = modes(
            mode(doing(Effect.LoseGame(Players.Each(PlayerFilter.Life(Comparison.Equal, n(13))))), gainLife(1, Players.Each(PlayerFilter.Any))),
            mode(doing(Effect.LoseGame(Players.Each(PlayerFilter.Life(Comparison.Equal, n(13))))), loseLife(1, Players.Each(PlayerFilter.Any)))))),
    )

    // CR 614.1c (enters with counters), 700.4 (dies), the creatures that died this turn before it enters
    @Test
    fun carrionUrsine() = card(
        name = "Carrion Ursine",
        cost = "{2}{B}{G}",
        type = "Creature — Zombie Bear",
        pt = "4/3",
        text = """
            Trample
            This creature enters with a +1/+1 counter on it for each creature that died this turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.EventsThisTurn(died, Measure.Events))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 702.110b (exploits), 701.6a / 113.1c (counter a spell or an ability on the stack)
    @Test
    fun surgingConglomerate() = card(
        name = "Surging Conglomerate",
        cost = "{2}{U}{U}",
        type = "Creature — Zombie Horror",
        pt = "3/3",
        text = """
            Flash
            Flying
            Exploit (When this creature enters, you may sacrifice a creature.)
            When this creature exploits a creature, counter target spell, activated ability, or triggered ability.
        """,
    ) parsesTo abilities(
        Keyword.Flash, Keyword.Flying, Keyword.Exploit,
        triggered(on(EventPattern.Exploits(ObjectFilter.Is(Objects.Self))), doing(Effect.Counter(target("spell"))),
            targets = listOf(slot("spell", or(spellFilter, stackAbility(AbilityKind.Activated), stackAbility(AbilityKind.Triggered))))),
    )

    // CR 701.6a, 113.1c (every ability on the stack), 406 (exile the other spells)
    @Test
    fun abruptDismissal() = card(
        name = "Abrupt Dismissal",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            Exile all other spells and counter all abilities.
        """,
    ) parsesTo abilities(
        spell(move(Objects.Each(and(another, spellFilter)), Zone.Exile), doing(Effect.Counter(Objects.Each(ObjectFilter.Category(ObjectCategory.StackAbility))))),
    )

    // CR 207.2c (fateful hour: an ability word), 111.4; the second paragraph replaces the first when its condition holds
    @Test
    fun rallyTheHamlet() = card(
        name = "Rally the Hamlet",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Create two 1/1 white Human creature tokens.
            Fateful hour — If you have 5 or less life, create five of those tokens instead.
        """,
    ) parsesTo abilities(
        spell(Step.If(Condition.Compare(Value.LifeOf(Players.You), Comparison.AtMost, n(5)),
            then = listOf(create(creatureToken("Human", listOf(arcanum.api.Color.White), 1, 1), n(5))),
            otherwise = listOf(create(creatureToken("Human", listOf(arcanum.api.Color.White), 1, 1), n(2))))).copy(abilityWord = "Fateful hour"),
    )

    // CR 707.2 (a copy of the entering creature), 111.1
    @Test
    fun necrotwinning() = card(
        name = "Necrotwinning",
        cost = "{3}{U}",
        type = "Enchantment",
        text = """
            Whenever a nontoken Zombie you control enters, create a token that's a copy of that creature.
        """,
    ) parsesTo abilities(
        triggered(enters(and(not(token), sub("Zombie"), you)), create(TokenSpec.CopyOf(Objects.EventObject(arcanum.api.EventRole.After)))),
    )

    // CR 707.9b (copy exceptions), 400.7 / 608.2c (the exiled card), 111.4 (the Zombie's power and toughness set as it's created)
    @Test
    fun spiritSplitter() = card(
        name = "Spirit Splitter",
        cost = "{3}",
        type = "Artifact",
        text = """
            {5}, {T}, Sacrifice this artifact: Exile target creature card from your graveyard. Create a token that's a copy of that card, except it's 1/1, it's a Spirit in addition to its other types, and it has flying. Create a black Zombie creature token with power equal to that card's power and toughness equal to that card's toughness.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{5}"), tap, sacrificeSelf),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            create(TokenSpec.CopyOf(Objects.Bound("exiled"), listOf(CopyException.PowerToughness(1, 1),
                CopyException.Types(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))), CopyException.AddAbilities(listOf(Keyword.Flying))))),
            create(TokenSpec.Defined(arcanum.api.FaceDefinition(name = "Zombie Token", colorIndicator = listOf(arcanum.api.Color.Black), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Zombie")), power = arcanum.api.PrintedNumber.X, toughness = arcanum.api.PrintedNumber.X),
                power = Value.Of(NumericProperty.Power, Objects.Bound("exiled"), Combine.Single), toughness = Value.Of(NumericProperty.Toughness, Objects.Bound("exiled"), Combine.Single))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 601.2f (a reduction for each card type, CR 205.2a), 603.2 / 601.2i (a cast trigger), 723.1 (controlling another player's turn),
    // 500.7 (the extra turn comes after that turn)
    @Test
    fun vorthakTheForetoldEnd() = card(
        name = "Vorthak, the Foretold End",
        cost = "{13}",
        type = "Legendary Creature — Eldrazi",
        pt = "13/13",
        text = """
            This spell costs {1} less to cast for each card type among cards in your graveyard.
            When you cast this spell, you gain control of target opponent during that player's next turn. After that turn, that player takes an extra turn.
            Flying, trample, protection from instants
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!,
            Value.CountDistinct(Objects.Each(inYourGraveyard(cardFilter)), SharedQuality.CardType)))),
        triggered(youCast(ObjectFilter.Is(Objects.Self)), doing(Effect.ControlNextTurn(Players.Target("opponent"), Players.You, thenExtraTurn = true)),
            targets = listOf(targetOpponent)),
        Keyword.Flying, Keyword.Trample, Keyword.Protection(instant),
    )

    // CR 608.2h (each keyword checked as it resolves), 611.2c (the creatures you control then), 702.118 (skulk)
    @Test
    fun odilaLunarCaptain() {
        val yours = and(creature, you)
        val keywords = listOf(Keyword.FirstStrike, Keyword.Flying, Keyword.Deathtouch, Keyword.DoubleStrike, Keyword.Haste, Keyword.Hexproof,
            Keyword.Indestructible, Keyword.Lifelink, Keyword.Menace, Keyword.Reach, Keyword.Skulk, Keyword.Trample, Keyword.Vigilance)
        card(
            name = "Odila, Lunar Captain",
            cost = "{3}{W}",
            type = "Legendary Creature — Human Soldier",
            pt = "3/3",
            text = """
                At the beginning of each combat, creatures you control gain first strike until end of turn if a creature you control has first strike. The same is true for flying, deathtouch, double strike, haste, hexproof, indestructible, lifelink, menace, reach, skulk, trample, and vigilance.
            """,
        ) parsesTo abilities(
            triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), *keywords.map { k ->
                Step.If(Condition.Exists(and(yours, kw(k.kind)), atLeast = 1), then = listOf(gains(Objects.Each(yours), k)))
            }.toTypedArray()),
        )
    }

    // CR 603.7c (the delayed trigger is about the chosen targets), 606 (loyalty abilities), 302.6 / 502.3 (doesn't untap), 114 (emblem), 118.9
    @Test
    fun tamsinFieldScholar() = card(
        name = "Tamsin, Field Scholar",
        cost = "{1}{G}{W}{U}",
        type = "Legendary Planeswalker — Tamsin",
        loyalty = "4",
        text = """
            [+1]: Choose up to two target creatures. Until your next turn, whenever either of those creatures deals combat damage, you draw a card.
            [−2]: Tap up to two target nonland permanents. They don't untap during their controller's next untap step.
            [−7]: Draw three cards. You get an emblem with "You may cast spells from your hand without paying their mana costs."
        """,
    ) parsesTo abilities(
        loyalty(1, doing(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.Damage(ObjectFilter.Is(target("creatures")), RecipientFilter(), combat = true, per = DamageOccurrence.Source)),
            Instructions(steps = listOf(draw(1))), duration = Duration.UntilYourNextTurn, repeats = true)),
            targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        loyalty(-2, tapIt(target("permanents")), doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(target("permanents")),
            Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(target("permanents"))))),
            targets = listOf(slot("permanents", and(nonland, permanentFilter), min = 0, max = 2))),
        loyalty(-7, draw(3), doing(Effect.CreateEmblem(listOf(static(Mechanic.MayPlay(Zone.Hand, arcanum.api.PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You),
            affects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))), Players.You))),
    )

    // CR 614.1c (enters as a copy), 707.9b, 707.9e (the counters are an exception of the copy effect: only when it copies), 107.3m (X of the
    // spell it was), 101.6 (can't be countered)
    @Test
    fun mirroredSelf() = card(
        name = "Mirrored Self",
        cost = "{X}{2}{G}{U}",
        type = "Creature — Shapeshifter",
        pt = "0/0",
        text = """
            This spell can't be countered.
            You may have this creature enter as a copy of any creature on the battlefield, except it enters with X additional +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersAsCopy(creature, optional = true, withCounters = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))))),
    )
}
