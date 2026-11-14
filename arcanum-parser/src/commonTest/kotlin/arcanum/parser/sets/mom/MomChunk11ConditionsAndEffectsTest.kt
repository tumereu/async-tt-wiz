package arcanum.parser.sets.mom

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
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
import arcanum.api.Choice
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.CostPart
import arcanum.api.FaceDefinition
import arcanum.api.NumericProperty
import arcanum.api.PlayKind
import arcanum.api.PrintedNumber
import arcanum.api.TokenSpec
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MOM chunk 11: conditions ("until", "would"), "becomes", casting, choosing modes and counters. */
class MomChunk11ConditionsAndEffectsTest {
    private val defense = CounterKind.Defense

    // CR 700.4 (dies), 614.1a (a replacement of its own destruction or other death)
    @Test
    fun thicketOutrider() = card(
        name = "Thicket Outrider",
        cost = "{4}{G}",
        type = "Creature — Elf Warrior",
        pt = "3/3",
        text = """
            When this creature enters, return target creature or battle card from your graveyard to your hand.
            If this creature would die, exile it instead.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(creature, battle))))),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 205.1b ("in addition to its other types", layer 4), 613.1f (layer 6); both last indefinitely (CR 611.2a)
    @Test
    fun ilvaneDawnMarshal() = card(
        name = "Ilvane, Dawn Marshal",
        cost = "{2}{W}{W}",
        type = "Legendary Planeswalker — Elspeth",
        loyalty = "4",
        text = """
            [+1]: Create a 1/1 white Soldier creature token with lifelink.
            [−2]: Put two +1/+1 counters on target creature. It becomes an Angel in addition to its other types and gains flying.
            [−6]: Return all nonland permanent cards with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        loyalty(1, create(creatureToken("Soldier", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Lifelink)))),
        loyalty(-2, counters(target("creature"), 2),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel")))), has(Keyword.Flying)),
                Affected.Objects(target("creature")), Duration.Indefinitely)),
            targets = listOf(slot("creature", creature))),
        loyalty(-6, move(Objects.Each(inYourGraveyard(nonland, permanentCard, manaValue(Comparison.AtMost, 3))), Zone.Battlefield)),
    )

    // CR 106.3 (mana from a source), 601.2h (spent to cast it), 603.4 (intervening "if")
    @Test
    fun orlaAndBryn() = card(
        name = "Orla and Bryn",
        cost = "{2}{G}{U}",
        type = "Legendary Creature — Human God",
        pt = "4/4",
        text = """
            Creatures you control have vigilance and "{T}: Add one mana of any color. Spend this mana only to cast a creature spell."
            Whenever you cast a creature spell, if three or more mana from creatures was spent to cast it, draw a card.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Vigilance, Ability.Activated(Cost(listOf(tap)), Instructions(steps = listOf(doing(Effect.AddMana(
            ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, creature)), Players.You)))), anyTime)),
            affects = Objects.Each(creatureYouControl)),
        triggered(youCast(creature), draw(1), interveningIf = Condition.Compare(
            Value.ManaSpent(of = Objects.EventObject(EventRole.Object), source = creature), Comparison.AtLeast, n(3))),
    )

    // CR 101.2 (a "can't" wins), each player's own spells counting for that player
    @Test
    fun compleatedArbiter() = card(
        name = "Compleated Arbiter",
        cost = "{2}{W}",
        type = "Creature — Phyrexian Wizard",
        pt = "3/3",
        text = """
            Each player can't cast more than one non-Phyrexian spell each turn.
            Non-Phyrexian creatures enter tapped.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantCast(not(sub("Phyrexian")))), affects = Affected.Players(Players.Each(PlayerFilter.Any)),
            condition = Condition.HappenedThisTurn(EventPattern.Cast(not(sub("Phyrexian")), PlayerFilter.Is(Players.Affected)), atLeast = 1,
                measure = Measure.Events)),
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(not(sub("Phyrexian")), creature), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 603.4 (intervening "if"), 105.2 (a blue and/or black spell), 701.20a (reveal)
    @Test
    fun dunewatchBasilisk() = card(
        name = "Dunewatch Basilisk",
        cost = "{1}{G}{G}",
        type = "Creature — Lizard",
        pt = "4/2",
        text = """
            Flash
            When this creature enters, if an opponent cast a blue and/or black spell this turn, look at the top four cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf,
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            interveningIf = Condition.HappenedThisTurn(EventPattern.Cast(and(or(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Blue)), ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Black))), spellFilter),
                PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)),
    )

    // CR 700.2 (modal), 310.8 (a battle's protector), 310.4 (defense counters)
    @Test
    fun inscribedLegionHarbinger() = card(
        name = "Inscribed Legion Harbinger",
        cost = "{4}{B}",
        type = "Creature — Phyrexian Demon",
        pt = "3/5",
        text = """
            When this creature enters, choose one —
            • Target opponent loses 2 life and you gain 2 life.
            • Choose target battle. If an opponent protects it, remove three defense counters from it. Otherwise, put three defense counters on it.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = modes(
            mode(loseLife(2, Players.Target("opponent")), gainLife(2), targets = listOf(targetOpponent)),
            mode(Step.If(Condition.PlayerMatches(Players.ProtectorOf(target("battle")), PlayerFilter.Opponent),
                then = listOf(doing(Effect.RemoveCounters(target("battle"), defense, n(3)))),
                otherwise = listOf(counters(target("battle"), 3, defense))),
                targets = listOf(slot("battle", battle))),
        ))),
    )

    // CR 700.2 (modal: both modes if an opponent has eight cards in their graveyard), 707.10 (a copy of a spell), 707.2 (a token copy)
    @Test
    fun twinReflection() = card(
        name = "Twin Reflection",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            This spell can't be copied.
            Choose one. If an opponent has eight or more cards in their graveyard, you may choose both instead.
            • Copy target spell. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
            • Create a token that's a copy of target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCopied),
        Ability.Spell(Instructions(modes = arcanum.api.Modes(listOf(
            mode(doing(Effect.CopySpell(target("spell"), controller = Players.You, newTargets = true)), targets = listOf(slot("spell", spellFilter))),
            mode(doing(Effect.CreateToken(arcanum.api.TokenSpec.CopyOf(target("creature")), n(1), Players.You)), targets = listOf(slot("creature", creature))),
        ), min = 1, max = 1, maxIf = eightInAGraveyard, maxIfCount = 2, upToMaxIf = true, chooser = Players.You))),
    )
    private val eightInAGraveyard = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Exists(
        ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested)))), atLeast = 8)))), atLeast = 1)

    // CR 122.6 (counters put on a permanent), 603.4 (only the first time this turn, of +1/+1 counters only)
    @Test
    fun greenhouseBruiser() = card(
        name = "Greenhouse Bruiser",
        cost = "{G}{W}",
        type = "Creature — Elemental Warrior",
        pt = "0/0",
        text = """
            Trample
            This creature enters with two +1/+1 counters on it.
            Whenever one or more +1/+1 counters are put on another permanent you control, if it's the first time +1/+1 counters have been put on that permanent this turn, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(2))))), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(Trigger.On(listOf(EventPattern.CountersPut(and(another, permanentFilter, you), p1p1)), oneOrMore = true),
            Instructions(steps = listOf(counters(Objects.Self))),
            interveningIf = Condition.Compare(Value.EventsThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.EventObject(EventRole.Object)), p1p1),
                Measure.Events), Comparison.Equal, n(1))),
    )

    // CR 513.1 (each end step), 603.4 (intervening "if"), 122.6
    @Test
    fun carnivalHerald() = card(
        name = "Carnival Herald",
        cost = "{2}{G}",
        type = "Creature — Elephant",
        pt = "2/2",
        text = "At the beginning of each end step, if a +1/+1 counter was put on a permanent under your control this turn, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), counters(Objects.Self),
            interveningIf = Condition.HappenedThisTurn(EventPattern.CountersPut(and(permanentFilter, you), p1p1), atLeast = 1, measure = Measure.Events)),
    )

    // CR 601.2d (distributed as it's cast, at least one to each target), 115.1d ("up to four")
    @Test
    fun rushTheGrovecore() = card(
        name = "Rush the Grovecore",
        cost = "{2}{G}{G}",
        type = "Sorcery",
        text = "Distribute four +1/+1 counters among up to four target creatures you control. Creatures you control gain vigilance and trample until end of turn.",
    ) parsesTo abilities(
        spell(counters(target("creatures"), Value.DividedShare), gains(Objects.Each(creatureYouControl), Keyword.Vigilance, Keyword.Trample),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(0), max = n(4), divided = n(4)))),
    )

    // CR 707.10 (a copy of a spell), 603.2 (once each turn), 611.2a (one spell in all from the exiled cards), 115.4 (any target), 107.3a
    @Test
    fun pyraEmberHerald() = card(
        name = "Pyra, Ember Herald",
        cost = "{4}{R}{R}",
        type = "Legendary Planeswalker — Chandra",
        loyalty = "5",
        text = """
            Whenever you cast an instant or sorcery spell, copy it. You may choose new targets for the copy. This ability triggers only once each turn.
            [+2]: Add two mana in any combination of colors.
            [+1]: Exile the top five cards of your library. Until the end of your next turn, you may cast an instant or sorcery spell from among those exiled cards.
            [−X]: Pyra deals X damage to each of up to two targets.
        """,
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)), doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)),
            perTurn = 1),
        loyalty(2, doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = true)), Players.You))),
        loyalty(1, move(Objects.LibraryCards(Players.You, n(5), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, resultingSpell = or(instant, sorcery), holder = PlayerFilter.You,
                total = 1)), Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))),
        Ability.Activated(cost(CostPart.Loyalty(Value.Times(Value.Announced("X"), -1))), Instructions(
            targets = listOf(anyTarget.copy(min = n(0), max = n(2))),
            steps = listOf(damage(Value.Announced("X"), toAny)),
            announced = listOf("X")), anyTime),
    )

    // CR 700.2 (modal), 508.4 (a token put onto the battlefield attacking), 107.3c ("where X is")
    @Test
    fun torvakAndSeline() {
        val others = Objects.Each(and(another, attacking, creature))
        val greatest = Value.Of(NumericProperty.Power, others, Combine.Greatest)
        card(
            name = "Torvak and Seline",
            cost = "{3}{G}{G}{W}{W}",
            type = "Legendary Creature — Dinosaur Vampire",
            pt = "12/12",
            text = """
                Trample
                Whenever you attack, choose one —
                • Create a tapped and attacking X/X green Dinosaur creature token with trample, where X is the greatest power among other attacking creatures.
                • Create X 1/1 white Vampire creature tokens with lifelink, where X is the number of other attacking creatures.
            """,
        ) parsesTo abilities(
            Keyword.Trample,
            Ability.Triggered(youAttack, Instructions(modes = modes(
                mode(doing(Effect.CreateToken(TokenSpec.Defined(FaceDefinition("Dinosaur Token", colorIndicator = listOf(Color.Green),
                    types = listOf(CardType.Creature), subtypes = listOf(Subtype("Dinosaur")), power = PrintedNumber.X, toughness = PrintedNumber.X,
                    abilities = listOf(Keyword.Trample)), power = greatest, toughness = greatest), n(1), Players.You, tapped = true,
                    attacking = AttackTarget.Chosen))),
                mode(Step.Do(Effect.CreateToken(creatureToken("Vampire", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Lifelink)), Value.Count(others),
                    Players.You))),
            ))),
        )
    }

    // CR 700.2 (modal), 120.3 (damage to creatures, planeswalkers and battles), 401.4 (cards put on the bottom), 121.2
    @Test
    fun blazeGambit() = card(
        name = "Blaze Gambit",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Blaze Gambit deals 2 damage to each creature, planeswalker, and battle.
            • Put any number of cards from your hand on the bottom of your library, then draw that many cards plus one.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(2, toObjects(Objects.Each(or(creature, ObjectFilter.Type(CardType.Planeswalker), battle))))),
            mode(Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.You))))),
                    bind = "chosen", chooser = Players.You),
                doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Library, libraryPosition = -1), postBind = "put")),
                doing(Effect.Draw(Players.You, Value.Sum(listOf(Value.Count(Objects.Bound("put")), n(1)))))),
        ))),
    )

    // CR 700.2 (modal), 603.6a / 603.10a (enters or dies), 701.24a (shuffle)
    @Test
    fun seerOfSorrows() = card(
        name = "Seer of Sorrows",
        cost = "{1}{U}",
        type = "Creature — Human Wizard",
        pt = "1/3",
        text = """
            When this creature enters or dies, choose one —
            • Draw a card, then discard a card.
            • Shuffle up to four target cards with mana value 3 or greater from your graveyard into your library.
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), Instructions(modes = modes(
            mode(draw(1), discard(1)),
            mode(move(target("cards"), Zone.Library), shuffle,
                targets = listOf(slot("cards", inYourGraveyard(cardFilter, manaValue(Comparison.AtLeast, 3)), min = 0, max = 4))),
        ))),
    )

    // CR 607.2a (cards exiled with it), 601.2a (cast from exile this turn), 118.9 (without paying its mana cost), 614.1a (bottom of its owner's library)
    @Test
    fun velmundLorekeeper() = card(
        name = "Velmund, Lorekeeper",
        cost = "{3}{R}{W}",
        type = "Legendary Creature — Elephant Cleric",
        pt = "3/5",
        text = """
            Vigilance
            At the beginning of your end step, exile target noncreature, nonland card from your graveyard. Create a 3/2 red and white Spirit creature token.
            {1}{R}{W}, {T}, Sacrifice a Spirit: Choose target card exiled with Velmund. You may cast that card this turn without paying its mana cost. If that spell would be put into a graveyard, put it on the bottom of its owner's library instead.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(yourEndStep, move(target("card"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            create(creatureToken("Spirit", listOf(Color.Red, Color.White), 3, 2)),
            targets = listOf(slot("card", inYourGraveyard(not(creature), nonland)))),
        activated(cost(mana("{1}{R}{W}"), tap, sacrificeA(sub("Spirit"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, then = Destination(Zone.Library, libraryPosition = -1),
                thenInsteadOf = Zone.Graveyard, holder = PlayerFilter.You)), Affected.Objects(target("card")), Duration.EndOfTurn)),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Exile), ObjectFilter.Is(Objects.Remembered("exiled")))))),
    )
}
