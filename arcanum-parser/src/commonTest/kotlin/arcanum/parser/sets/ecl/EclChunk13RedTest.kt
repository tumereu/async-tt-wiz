package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.CostPart
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CountersToPut
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
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 13: red cards. Two different "any target"s (CR 115.3), a delayed trigger when the damaged creature dies this turn
 * that reads its power as it last existed (CR 603.10a), damage when you cast big spells, a replacement for the rest of the turn (CR 614.1a),
 * mana for Elemental spells and abilities (CR 106.6), gaining control until end of turn (layer 2), a spell exiled with a dream counter as it
 * resolves and cast later for free (CR 614.1a, 122.1), spells that can't be countered (CR 101.2), ward paid with life (CR 702.21), "each
 * end step" with a counter put this turn, a land's extra mana (CR 106.12a), a creature put from your hand for one turn, and a spell
 * copied when it has a single target (CR 707.10, 115.10).
 */
class EclChunk13RedTest {
    private val anyFilter = or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker), ObjectFilter.Type(arcanum.api.CardType.Battle))
    private val treasure = TokenSpec.Predefined("Treasure")
    private val playUntilEndOfNextTurn = doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn))
    private val bigSpell = and(spellFilter, manaValue(Comparison.AtLeast, 4))

    @Test
    fun highcragAssailant() = card(
        name = "Highcrag Assailant",
        cost = "{3}{R}{R}",
        type = "Creature — Giant Warrior",
        text = """
            Double strike
            Other Giants you control have double strike.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        static(has(Keyword.DoubleStrike), affects = Objects.Each(and(another, sub("Giant"), you))),
    )

    @Test
    fun bonemaulFrenzier() = card(
        name = "Bonemaul Frenzier",
        cost = "{3}{R}",
        type = "Creature — Goblin Berserker",
        text = "This creature gets +2/+0 for each other Goblin you control.",
        pt = "2/4",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.Times(count(and(another, sub("Goblin"), you)), 2), n(0))),
    )

    // CR 115.3: the second target differs from the first
    @Test
    fun rockslideRush() = card(
        name = "Rockslide Rush",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Rockslide Rush deals 2 damage to any target and 1 damage to any other target.",
    ) parsesTo abilities(
        spell(damage(2, toAny), damage(1, Recipients(target = "other")), targets = listOf(anyTarget,
            TargetSlot("other", TargetKind.Either(anyFilter, PlayerFilter.Any), min = n(1), max = n(1), distinctFrom = listOf("any")))),
    )

    @Test
    fun eldestGranny() = card(
        name = "Eldest Granny",
        cost = "{2}{R}",
        type = "Creature — Goblin Warlock",
        text = "When this creature enters, create a 1/1 black and red Goblin creature token.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, create(goblin)),
    )

    // CR 603.7c; CR 603.10a: its power as it last existed
    @Test
    fun lastFlameInsight() = card(
        name = "Last-Flame Insight",
        cost = "{X}{R}",
        type = "Instant",
        text = "Last-Flame Insight deals X damage to target creature. When that creature dies this turn, exile a number of cards from the top of your library equal to its power, then choose a card exiled this way. Until the end of your next turn, you may play that card.",
    ) parsesTo abilities(
        spell(damage(Value.Announced(name = "X"), toObjects(target("creature"))),
            doing(Effect.CreateDelayedTrigger(on(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(
                    move(Objects.LibraryCards(Players.You, power(Objects.EventObject(EventRole.Before)), fromTop = true), Zone.Exile, postBind = "exiled"),
                    Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
                    doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                        arcanum.api.Affected.Objects(Objects.Bound("chosen")), Duration.UntilEndOfYourNextTurn)))),
                duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    @Test
    fun furiousFirecaller() = card(
        name = "Furious Firecaller",
        cost = "{2}{R}",
        type = "Creature — Elemental Sorcerer",
        text = """
            Reach
            Whenever you cast a spell with mana value 4 or greater, this creature deals 2 damage to each opponent.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(youCast(bigSpell), damage(2, eachOpponent)),
    )

    // CR 614.1a: a replacement effect for the rest of the turn
    @Test
    fun stokeThePyre() = card(
        name = "Stoke the Pyre",
        cost = "{3}{R}",
        type = "Instant",
        text = "Stoke the Pyre deals 5 damage to target creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(damage(5, toObjects(target("creature"))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield,
                to = Zone.Graveyard), modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), null, Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun fireLinkBruiser() = card(
        name = "Fire-Link Bruiser",
        cost = "{1}{R}",
        type = "Creature — Elemental Warrior",
        text = "{1}{R}: This creature gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(manaCost("{1}{R}"), pump(Objects.Self, 1, 0, has(Keyword.Menace))),
    )

    // CR 106.6
    @Test
    fun flameplaiter() = card(
        name = "Flameplaiter",
        cost = "{1}{R}",
        type = "Creature — Elemental Bard",
        text = "{T}: Add two mana in any combination of colors. Spend this mana only to cast Elemental spells or activate abilities of Elemental sources.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = true), restriction = or(and(spellFilter, sub("Elemental")),
            ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = sub("Elemental")))), Players.You))),
    )

    @Test
    fun emberkinGildsmith() = card(
        name = "Emberkin Gildsmith",
        cost = "{3}{R}",
        type = "Creature — Elemental Sorcerer",
        text = """
            Trample
            When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, create(treasure)),
    )

    @Test
    fun titanfallBlow() = card(
        name = "Titanfall Blow",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Target creature you control deals damage equal to its power to target creature an opponent controls.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(damage(power(target("creature")), toObjects(target("creature an opponent controls")), source = target("creature")),
                targets = listOf(slot("creature", creatureYouControl), slot("creature an opponent controls", creatureOpponentControls))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact)))), min = 1, max = 1, chooser = Players.You))),
    )

    // layer 2 until end of turn
    @Test
    fun goatgrab() = card(
        name = "Goatgrab",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. If that creature is a Goat, it also gets +3/+0 until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)), untapIt(target("creature")), gains(target("creature"), Keyword.Haste),
            Step.If(Condition.ObjectMatches(target("creature"), sub("Goat")), then = listOf(pump(target("creature"), 3, 0))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 614.1a: the spell gets the replacement; CR 122.1: a dream counter on the card in exile
    @Test
    fun colossusReverist() = card(
        name = "Colossus Reverist",
        cost = "{2}{R}{R}",
        type = "Creature — Giant Wizard",
        text = """
            Whenever you cast an instant or sorcery spell from your hand, exile that card with a dream counter on it instead of putting it into your graveyard as it resolves.
            Whenever this creature attacks, you may cast a spell from among cards you own in exile with dream counters on them without paying its mana cost.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(and(or(instant, sorcery), spellFilter), PlayerFilter.You, from = Zone.Hand)),
            doing(Effect.SpellGets(Objects.EventObject(EventRole.Object), listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), from = Zone.Stack, to = Zone.Graveyard, resolving = true),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile, counters = listOf(CountersToPut(CounterKind.Named("dream"), n(1))))))))))),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(inZone(Zone.Exile), ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.HasCounter(CounterKind.Named("dream"), atLeast = 1)),
                n(0), n(1)), bind = "chosen", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("chosen"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
    )

    // CR 702.21a: ward paid with life, given to other creatures too
    @Test
    fun jinxingSilencer() = card(
        name = "Jinxing Silencer",
        cost = "{1}{R}",
        type = "Creature — Goblin Sorcerer",
        text = """
            This spell can't be countered.
            Ward—Pay 2 life.
            Spells you control can't be countered.
            Other creatures you control have "Ward—Pay 2 life."
        """,
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Ward(cost(CostPart.PayLife(n(2)))),
        static(Mechanic.CantBeCountered, affects = Objects.Each(and(spellFilter, you))),
        static(has(Keyword.Ward(cost(CostPart.PayLife(n(2))))), affects = Objects.Each(and(another, creature, you))),
    )

    @Test
    fun rudeArrival() = card(
        name = "Rude Arrival",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Target creature gains trample and haste until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(gains(target("creature"), Keyword.Trample, Keyword.Haste), draw(1), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun emberkinFanatic() = card(
        name = "Emberkin Fanatic",
        cost = "{5}{R}",
        type = "Creature — Elemental Warrior",
        text = """
            When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.
            Basic landcycling {1}{R} ({1}{R}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        triggered(entersSelf, move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"), playUntilEndOfNextTurn),
        Keyword.Typecycling(basicLand(), "Basic landcycling", manaCost("{1}{R}")),
    )

    // CR 603.4; counters put by your spells and abilities this turn
    @Test
    fun lingeringPitchblaze() = card(
        name = "Lingering Pitchblaze",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "At the beginning of each end step, if you put a counter on a creature this turn, this enchantment deals 2 damage to each opponent.",
    ) parsesTo abilities(
        triggered(eachEndStep, damage(2, eachOpponent), interveningIf = Condition.HappenedThisTurn(
            EventPattern.CountersPut(creature, null, causedBy = you), atLeast = 1, measure = Measure.Events)),
    )

    // CR 106.12a: a triggered mana ability
    @Test
    fun magmavaulter() = card(
        name = "Magmavaulter",
        cost = "{3}{R}",
        type = "Creature — Elemental",
        text = """
            All creatures have haste.
            Whenever a player taps a basic land for mana, that player adds one mana of any type that land produced.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(creature)),
        triggered(on(EventPattern.Tap(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), land), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.SameAsProduced(n(1))), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // CR 603.7: the next end step, once
    @Test
    fun timidAssault() = card(
        name = "Timid Assault",
        cost = "{2}{R}",
        type = "Enchantment",
        text = "{1}{R}: You may put a creature card with total power and toughness 5 or less from your hand onto the battlefield. That creature gains haste. At the beginning of the next end step, sacrifice that creature.",
    ) parsesTo abilities(
        activated(manaCost("{1}{R}"),
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.Where(Condition.Compare(Value.Sum(listOf(power(Objects.Tested), toughness(Objects.Tested))),
                Comparison.AtMost, n(5))), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            doing(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), arcanum.api.Affected.Objects(Objects.Bound("put")), Duration.Indefinitely)),
            doing(Effect.CreateDelayedTrigger(eachEndStep, Instructions(steps = listOf(doing(Effect.Sacrifice(Objects.Bound("put")))))))),
    )

    @Test
    fun heedlessPlundering() = card(
        name = "Heedless Plundering",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature gets +3/+2 until end of turn. Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 2), create(treasure), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun scorch() = card(
        name = "Scorch",
        cost = "{1}{R}",
        type = "Instant",
        text = "Scorch deals 4 damage to target creature or planeswalker.",
    ) parsesTo abilities(
        spell(damage(4, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker))))),
    )

    // CR 707.10: a copy on the Stack; CR 115.10: a single target
    @Test
    fun spikestoneDespot() = card(
        name = "Spikestone Despot",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        text = """
            Flying
            Wither (This deals damage to creatures in the form of -1/-1 counters.)
            Whenever you cast an instant or sorcery spell with a single target, you may copy it. If you do, those spells gain wither. You may choose new targets for the copy.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Wither,
        triggered(youCast(and(or(instant, sorcery), spellFilter, ObjectFilter.SingleTarget())),
            Step.Pay(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true, bind = "copy"))), payer = Players.You,
                bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.ApplyMechanics(listOf(has(Keyword.Wither)),
                arcanum.api.Affected.Objects(Objects.Union(listOf(Objects.EventObject(EventRole.Object), Objects.Bound("copy")))), Duration.Indefinitely))))),
    )

    @Test
    fun pinchBlast() = card(
        name = "Pinch Blast",
        cost = "{2}{R}",
        type = "Instant",
        text = "Pinch Blast deals 3 damage to any target. You may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        spell(damage(3, toAny), *mayPay(discardACard, then = listOf(draw(1))).toTypedArray(), targets = listOf(anyTarget)),
    )
}
