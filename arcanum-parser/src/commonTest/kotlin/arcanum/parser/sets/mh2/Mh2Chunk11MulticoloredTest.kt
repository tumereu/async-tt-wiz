package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 multicoloured cards, first part (chunk 11): counters for other artifact creatures with modular, outlast granted to
 * creatures without counters (CR 702.107), an alternative cost after discarding, creatures that entered from a graveyard or were cast
 * from one, support on an Aura (CR 701.41), looking for a planeswalker card with an additional loyalty cost (CR 606.4), revealing two
 * cards and splitting them, flying for creature tokens, a planeswalker entering with loyalty from lands (CR 306.5b), the cards you
 * discarded this turn with jump-start (CR 702.133), cascade (CR 702.85), a sacrificed permanent's type, hexproof from monocolored
 * (CR 702.11d), protection from permanents with corruption counters (CR 702.16), coloured spells costing less and a return with an
 * additional counter.
 */
class Mh2Chunk11MulticoloredTest {
    @Test
    fun arcwiredHunter() = card(
        name = "Arcwired Hunter",
        cost = "{1}{R}{W}",
        type = "Artifact Creature — Cat Soldier",
        text = """
            First strike
            When this creature enters, put a +1/+1 counter on each other artifact creature you control.
            Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(entersSelf, counters(Objects.Each(and(another, artifact, creature, you)))),
        Keyword.Modular(2),
    )

    // CR 702.107a outlast; a creature with no +1/+1 counter gets it
    @Test
    fun arcusDevotee() = card(
        name = "Arcus Devotee",
        cost = "{G}{W}",
        type = "Creature — Human Cleric Archer",
        text = """
            Reach, lifelink
            Outlast {G/W} ({G/W}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)
            Each other creature you control without a +1/+1 counter on it has outlast {G/W}.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Lifelink,
        Keyword.Outlast(manaCost("{G/W}")),
        static(has(Keyword.Outlast(manaCost("{G/W}"))), affects = Objects.Each(and(another, creature, you, not(ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
    )

    // CR 118.9: an alternative cost while you've discarded a card this turn
    @Test
    fun pollywhistlebrambleton() = card(
        name = "Pollywhistlebrambleton",
        cost = null,
        type = "Legendary Creature — Human Wizard",
        text = """
            As long as you've discarded a card this turn, you may pay {B/R} to cast this spell.
            When Pollywhistlebrambleton enters, you may search your library for a card named The Underworld Cookbook, reveal it, put it into your hand, then shuffle.
            Sacrifice two Foods: Target creature deals 6 damage to itself.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = manaCost("{B/R}")), condition = Condition.HappenedThisTurn(
            EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events)),
        triggered(entersSelf, Step.May(listOf(search(ObjectFilter.Named("The Underworld Cookbook"), reveal = true), move(Objects.Bound("found"), Zone.Hand),
            shuffle), Players.You)),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(sub("Food"), count = n(2)))),
            damage(6, toObjects(target("creature")), source = target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 603.6a: the creature that entered; from a graveyard, or cast from one (CR 601.2a)
    @Test
    fun breathlessPaladin() = card(
        name = "Breathless Paladin",
        cost = "{1}{W}{B}",
        type = "Creature — Spirit Knight",
        text = """
            Flying, lifelink
            Whenever this creature or another creature you control enters, if that creature entered from a graveyard or you cast it from a graveyard, put a +1/+1 counter on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(Trigger.On(listOf(
            EventPattern.ZoneChange(creatureYouControl, from = Zone.Graveyard, to = Zone.Battlefield),
            EventPattern.ZoneChange(and(creature, you, ObjectFilter.WasCast(from = Zone.Graveyard)), to = Zone.Battlefield),
        )), counters(Objects.Self)),
    )

    // CR 701.41a: support from a permanent: up to two other target creatures
    @Test
    fun snaredByLagacs() = card(
        name = "Snared by Lagacs",
        cost = "{1}{G}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
            When this Aura enters, support 2. (Put a +1/+1 counter on each of up to two target creatures.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = equipped),
        triggered(entersSelf, doing(Effect.Action(KeywordAction.Support(n(2), "supported"))),
            targets = listOf(TargetSlot("supported", TargetKind.Objects(and(another, creature)), min = n(0), max = n(2)))),
    )

    // CR 606.4: an additional [+1] loyalty cost
    @Test
    fun marthTheLynx() = card(
        name = "Marth the Lynx",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Human Warrior",
        text = """
            Whenever Marth enters or a planeswalker you control dies, look at the top seven cards of your library. You may reveal a planeswalker card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Planeswalkers' loyalty abilities you activate cost an additional [+1] to activate.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), EventPattern.ZoneChange(and(planeswalker, you), from = Zone.Battlefield, to = Zone.Graveyard))),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(planeswalker, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        playerStatic(Mechanic.AdditionalLoyaltyCost(1)),
    )

    // CR 608.2c: "this way" is the card put into your hand
    @Test
    fun chromeHerald() = card(
        name = "Chrome Herald",
        cost = "{1}{W}{U}",
        type = "Artifact Creature — Thopter",
        text = """
            Flying
            When this creature enters, reveal the top two cards of your library. Put one of them into your hand and the other into your graveyard. If you put an artifact card into your hand this way, you gain 3 life.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(2), fromTop = true), bind = "revealed")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("revealed")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("kept")), Zone.Graveyard),
            Step.If(Condition.ObjectMatches(Objects.Bound("kept"), artifact), then = listOf(gainLife(3)))),
    )

    @Test
    fun mergeChrysalis() = card(
        name = "Merge Chrysalis",
        cost = "{G}{U}",
        type = "Artifact",
        text = """
            Creature tokens you control have flying.
            {2}{G}{U}, {T}, Sacrifice a token: Create a 4/4 green Beast creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), affects = Objects.Each(and(creature, token, you))),
        activated(cost(mana("{2}{G}{U}"), tap, sacrificeA(token)), create(creatureToken("Beast", listOf(Color.Green), 4, 4)), rules = asSorcery),
    )

    // CR 306.5b: it enters with loyalty counters equal to the number of lands you control
    @Test
    fun varrokShadowSlayer() = card(
        name = "Varrok, Shadow Slayer",
        cost = "{W}{U}{B}",
        type = "Legendary Planeswalker — Dakkon",
        text = """
            Varrok enters with a number of loyalty counters on him equal to the number of lands you control.
            [+1]: Surveil 2.
            [−3]: Exile target creature.
            [−6]: You may put an artifact card from your hand or graveyard onto the battlefield.
        """,
        loyalty = "0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(
            Modification.EntersWithCounters(CounterKind.Loyalty, count(and(land, you)))))),
        activated(cost(CostPart.Loyalty(n(1))), surveil(2)),
        activated(cost(CostPart.Loyalty(n(-3))), move(target("creature"), Zone.Exile), targets = listOf(slot("creature", creature))),
        activated(cost(CostPart.Loyalty(n(-6))),
            Step.Choose(Choice.Objects(and(artifact, or(inZone(Zone.Hand), inZone(Zone.Graveyard)), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield)),
    )

    // CR 702.133a jump-start; the cards you discarded this turn
    @Test
    fun dihadasGambit() = card(
        name = "Dihada's Gambit",
        cost = "{1}{U}{B}",
        type = "Instant",
        text = """
            Draw two cards, then discard a card. You gain life equal to the number of cards you've discarded this turn.
            Jump-start (You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card.)
        """,
    ) parsesTo abilities(
        spell(draw(2), discard(1),
            doing(Effect.GainLife(Players.You, Value.EventsThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), Measure.Objects)))),
        Keyword.JumpStart,
    )

    @Test
    fun dreyWarden() = card(
        name = "Drey Warden",
        cost = "{3}{B}{G}",
        type = "Creature — Elf Druid",
        text = """
            When this creature enters, create two 1/1 green Squirrel creature tokens.
            {3}{B}: Squirrels you control get +1/+0 and gain menace until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, create(squirrel, count = n(2))),
        activated(cost(mana("{3}{B}")), untilEndOfTurn(Objects.Each(and(sub("Squirrel"), you)), pt(1, 0), has(Keyword.Menace))),
    )

    // CR 702.85a cascade
    @Test
    fun etherforgedSphinx() = card(
        name = "Etherforged Sphinx",
        cost = "{7}{W}{U}",
        type = "Artifact Creature — Sphinx",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card with lesser mana value. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), Keyword.Flying, Keyword.Cascade)

    // CR 601.2b, 608.2h: the sacrificed permanent as it last existed
    @Test
    fun smelterHelix() = card(
        name = "Smelter Helix",
        cost = "{1}{R}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a permanent.
            Smelter Helix deals 4 damage to any target. If the sacrificed permanent was an artifact, you gain 4 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(permanentFilter, count = n(1)), preBind = "sacrificed"))))),
        spell(damage(4, toAny), Step.If(Condition.ObjectMatches(Objects.Bound("sacrificed"), artifact), then = listOf(gainLife(4))), targets = listOf(anyTarget)),
    )

    // CR 702.11d hexproof from monocolored
    @Test
    fun generalFerrusRokard() = card(
        name = "General Ferrus Rokard",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Soldier",
        text = """
            Hexproof from monocolored
            Whenever you cast a multicolored spell, create a 4/4 red and white Golem artifact creature token.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.HexproofFrom(ObjectFilter.Monocolored),
        triggered(youCast(ObjectFilter.Multicolored), create(creatureToken("Golem", listOf(Color.Red, Color.White), 4, 4,
            types = listOf(CardType.Artifact, CardType.Creature)))),
    )

    // CR 702.16: protection from permanents with a corruption counter; CR 613.1b gaining control
    @Test
    fun vesadroneDihada() = card(
        name = "Vesadrone Dihada",
        cost = "{1}{U}{B}{R}",
        type = "Legendary Planeswalker — Dihada",
        text = """
            Protection from permanents with corruption counters on them
            [+1]: Each opponent loses 2 life and you gain 2 life. Put a corruption counter on up to one other target creature or planeswalker.
            [−3]: Gain control of target creature or planeswalker until end of turn. Untap it and put a corruption counter on it. It gains haste until end of turn.
            [−7]: Gain control of each permanent with a corruption counter on it.
        """,
        loyalty = "4",
    ) parsesTo abilities(
        Keyword.Protection(and(permanentFilter, ObjectFilter.HasCounter(CounterKind.Named("corruption"), atLeast = 1))),
        activated(cost(CostPart.Loyalty(n(1))), loseLife(2, eachOpponentPlayers), gainLife(2), counters(target("permanent"), 1, CounterKind.Named("corruption")),
            targets = listOf(slot("permanent", and(another, or(creature, planeswalker)), min = 0))),
        activated(cost(CostPart.Loyalty(n(-3))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("permanent")), Duration.EndOfTurn)),
            untapIt(target("permanent")),
            counters(target("permanent"), 1, CounterKind.Named("corruption")),
            gains(target("permanent"), Keyword.Haste),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
        activated(cost(CostPart.Loyalty(n(-7))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)),
                Affected.Objects(Objects.Each(and(permanentFilter, ObjectFilter.HasCounter(CounterKind.Named("corruption"), atLeast = 1)))), Duration.Indefinitely)),
        ),
    )

    // CR 601.2f: {1} less for each spell you cast that's red or green
    @Test
    fun goblinChaosmonger() = card(
        name = "Goblin Chaosmonger",
        cost = "{R}{G}",
        type = "Creature — Goblin Shaman",
        text = "Each spell you cast that's red or green costs {1} less to cast.",
        pt = "2/2",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(spellFilter, or(ObjectFilter.OfColor(Color.Red), ObjectFilter.OfColor(Color.Green)), you),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 122.6: it enters with the counter
    @Test
    fun gentleRestoration() = card(
        name = "Gentle Restoration",
        cost = "{3}{W}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Return target creature card from your graveyard to the battlefield with an additional +1/+1 counter on it.
            • Return up to two target creature cards with power 2 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        modal(
            mode(doing(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(arcanum.api.CountersToPut(p1p1, n(1)))))),
                targets = listOf(slot("card", inYourGraveyard(creature)))),
            mode(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature, power(Comparison.AtMost, 2)), min = 0, max = 2))),
        ),
    )

    // CR 707.12: a copy of a card that needn't be in the game, cast paying its costs
    @Test
    fun gorvanHalfEye() = card(
        name = "Gorvan Half-Eye",
        cost = "{W}{U}{B}{R}{G}",
        type = "Legendary Creature — Human Wizard",
        text = "{T}: Choose a card name that hasn't been chosen from among Horizon Unmaking, Horizon Torrent, Horizon Dread, Horizon Wyrm, Horizon Regrowth, and Horizon Lotus. Create a copy of the card with the chosen name. You may cast the copy. (You still pay its costs.)",
        pt = "5/5",
    ) parsesTo abilities(
        activated(cost(tap),
            Step.Choose(Choice.CardNameAmong(listOf("Horizon Unmaking", "Horizon Torrent", "Horizon Dread", "Horizon Wyrm", "Horizon Regrowth", "Horizon Lotus"),
                unchosen = true), bind = "name", chooser = Players.You),
            doing(Effect.CopyNamedCard("name", Players.You, bind = "copy")),
            Step.May(listOf(doing(Effect.Cast(Objects.Bound("copy"), arcanum.api.PlayKind.Cast, caster = Players.You))), player = Players.You)),
    )
}
