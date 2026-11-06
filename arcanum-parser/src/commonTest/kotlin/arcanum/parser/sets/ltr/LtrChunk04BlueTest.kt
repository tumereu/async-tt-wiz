package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 04: blue cards, second part. Counters counted on a permanent exiled to pay a cost (CR 608.2h) and an
 * extra turn (CR 500.7), "whenever you scry" once each turn (CR 701.22), mana spent only on instants and sorceries (CR 106.6),
 * bouncing a spell or permanent then casting a cheaper spell for free (CR 601.2), control for as long as you control this creature
 * (CR 611.2b), the second spell each turn (CR 603.2), a copy that isn't legendary (CR 707.9b), "the amassed Army's power"
 * (CR 701.47c), stun counters as it enters and draws during an opponent's turn (CR 122.1d), X from the cards looked at while
 * scrying, and tapping any number of Humans to draw.
 */
class LtrChunk04BlueTest {
    @Test
    fun knightsOfTheSwanCoast() = card(
        name = "Knights of the Swan Coast",
        cost = "{3}{U}",
        type = "Creature — Human Knight",
        text = "Whenever you draw your second card each turn, put a +1/+1 counter on this creature.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(drawSecondCard, counters(Objects.Self)),
    )

    // CR 702.29e: islandcycling
    @Test
    fun goldenWoodRevealed() = card(
        name = "Golden Wood Revealed",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = """
            Draw three cards.
            Islandcycling {1} ({1}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        spell(draw(3)),
        Keyword.Typecycling(sub("Island"), "Islandcycling", manaCost("{1}")),
    )

    // CR 608.2h: the counters it had as it was exiled to pay the cost; CR 500.7: an extra turn
    @Test
    fun farIsleCalling() = card(
        name = "Far Isle Calling",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            Whenever you scry, put a verse counter on this enchantment.
            {4}{U}{U}, Exile this enchantment: Draw a card for each verse counter on this enchantment. If it had seven or more verse counters on it, take an extra turn after this one. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(youScry, counters(Objects.Self, kind = verse)),
        activated(cost(mana("{4}{U}{U}"), CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)),
            draw(versesOnSelf),
            Step.If(Condition.Compare(versesOnSelf, Comparison.AtLeast, n(7)), then = listOf(doing(Effect.ExtraTurn(Players.You)))),
            rules = asSorcery),
    )

    // CR 701.54a in a combat damage trigger; CR 400.7
    @Test
    fun thorondilSwiftSavior() = card(
        name = "Thorondil, Swift Savior",
        cost = "{3}{U}",
        type = "Legendary Creature — Bird Soldier",
        text = """
            Flying
            Whenever Thorondil deals combat damage to a player, exile up to one target creature you own, then return it to the battlefield under your control.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, move(target("creature"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.You),
            targets = listOf(slot("creature", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0))),
    )

    // CR 701.22; once each turn
    @Test
    fun silverlodeWatcher() = card(
        name = "Silverlode Watcher",
        cost = "{1}{U}",
        type = "Creature — Elf Scout",
        text = "Whenever you scry, this creature gets +1/+0 until end of turn and can't be blocked this turn. This ability triggers only once each turn.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(youScry, untilEndOfTurn(Objects.Self, pt(1, 0), Mechanic.CantBeBlocked), perTurn = 1),
    )

    // CR 106.6: mana only for instant and sorcery spells
    @Test
    fun harborTownSurvivor() = card(
        name = "Harbor Town Survivor",
        cost = "{1}{U}",
        type = "Creature — Human Peasant",
        text = """
            {T}: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.
            {5}{U}, {T}: Target player mills three cards. (They put the top three cards of their library into their graveyard.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, or(instant, sorcery))), Players.You))),
        activated(cost(mana("{5}{U}"), tap), mill(3, Players.Target("player")), targets = listOf(targetPlayer)),
    )

    // CR 601.2: cast from hand without paying, its mana value compared with the returned object's as it last existed (CR 608.2h)
    @Test
    fun driveBackTheFoe() = card(
        name = "Drive Back the Foe",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = "Return target spell or nonland permanent an opponent controls to its owner's hand. You may cast an instant or sorcery spell with equal or lesser mana value from your hand without paying its mana cost.",
    ) parsesTo abilities(
        spell(move(target("target"), Zone.Hand),
            Step.Choose(Choice.Objects(and(or(instant, sorcery), ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.ManaValue, target("target"), Combine.Single)),
                inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(0), n(1)), bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
            targets = listOf(slot("target", or(and(spellFilter, opponents), and(nonland, permanentFilter, opponents))))),
    )

    // CR 611.2b: for as long as you control this creature; CR 701.54a
    @Test
    fun rangersOfTheHiddenVale() = card(
        name = "Rangers of the Hidden Vale",
        cost = "{2}{U}{U}",
        type = "Creature — Human Ranger",
        text = """
            Vigilance
            When this creature enters, gain control of up to one target creature with lesser power for as long as you control this creature. Then the Ring tempts you.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))),
            ringTempts,
            targets = listOf(slot("creature", and(creature, ObjectFilter.Power(Comparison.Less, power(Objects.Self))), min = 0))),
    )

    // CR 603.2: the second spell each turn
    @Test
    fun curunilTheWhite() = card(
        name = "Curunil the White",
        cost = "{4}{U}",
        type = "Legendary Creature — Avatar Wizard",
        text = """
            Ward {2}
            Whenever you cast your second spell each turn, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        triggered(secondSpell, amassOrcs(2)),
    )

    @Test
    fun wizardsTrickery() = card(
        name = "Wizard's Trickery",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Counter target spell.
            Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), amassOrcs(1), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 611.2b, 714.2b; CR 122.1d: stun counters on each of them
    @Test
    fun scrollOfTheLastKing() = card(
        name = "Scroll of the Last King",
        cost = "{2}{U}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Gain control of up to one target artifact for as long as you control this Saga. The Ring tempts you.
            II — Tap up to two target creatures. Put a stun counter on each of them.
            III — Draw a card for each tapped creature target opponent controls.
        """,
    ) parsesTo abilities(
        chapter(1, doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("artifact")),
            Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you)))), ringTempts,
            targets = listOf(slot("artifact", artifact, min = 0))),
        chapter(2, tapIt(target("creatures")), counters(target("creatures"), kind = CounterKind.Stun),
            targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        chapter(3, draw(count(and(tapped, creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))),
            targets = listOf(targetOpponent)),
    )

    @Test
    fun soothingOfTheWretch() = card(
        name = "Soothing of the Wretch",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target nontoken creature to its owner's hand. The Ring tempts you.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Hand), ringTempts, targets = listOf(slot("creature", and(not(token), creature)))),
    )

    // CR 707.9b, 707.10: a copy that isn't legendary, new targets
    @Test
    fun stormOfTheWhiteWizard() = card(
        name = "Storm of the White Wizard",
        cost = "{4}{U}{U}",
        type = "Enchantment",
        text = """
            Ward {3}
            Whenever you cast your second spell each turn, copy it, except the copy isn't legendary. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{3}")),
        triggered(secondSpell, doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true,
            except = listOf(arcanum.api.CopyException.Types(removeSupertypes = listOf(arcanum.api.Supertype.Legendary)))))),
    )

    // CR 701.47c: the amassed Army's power
    @Test
    fun hemmedInByOrcs() = card(
        name = "Hemmed In by Orcs",
        cost = "{3}{U}",
        type = "Sorcery",
        text = "Amass Orcs 3, then target player mills X cards, where X is the amassed Army's power. (To amass Orcs 3, put three +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
    ) parsesTo abilities(
        spell(amassOrcs(n(3), bind = "army"), Step.Do(Effect.Mill(Players.Target("player"), power(Objects.Bound("army")))), targets = listOf(targetPlayer)),
    )

    @Test
    fun treasonOfTheTower() = card(
        name = "Treason of the Tower",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Put up to one target instant or sorcery card from your graveyard on top of your library.
            Amass Orcs 2. (To amass Orcs 2, put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Library, position = 0), amassOrcs(2), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery)), min = 0))),
    )

    // CR 122.1d: nine stun counters; draws during an opponent's turn; a Tentacle dying untaps a Kraken and stuns a permanent
    @Test
    fun theLurkerInTheLake() = card(
        name = "The Lurker in the Lake",
        cost = "{3}{U}{U}",
        type = "Legendary Creature — Kraken",
        text = """
            The Lurker in the Lake enters tapped with nine stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Whenever you draw a card during an opponent's turn, create a 1/1 blue Tentacle creature token.
            Whenever a Tentacle you control dies, untap up to one target Kraken and put a stun counter on up to one target nonland permanent.
        """,
        pt = "9/9",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped, Modification.EntersWithCounters(CounterKind.Stun, n(9))))),
        triggered(on(EventPattern.Draw(PlayerFilter.You)), create(creatureToken("Tentacle", listOf(arcanum.api.Color.Blue), 1, 1)),
            triggersOnlyIf = Condition.PlayerMatches(Players.Active, PlayerFilter.Opponent)),
        triggered(on(EventPattern.ZoneChange(and(sub("Tentacle"), you), from = Zone.Battlefield, to = Zone.Graveyard)),
            untapIt(target("Kraken")), counters(target("permanent"), kind = CounterKind.Stun),
            targets = listOf(slot("Kraken", sub("Kraken"), min = 0), slot("permanent", and(nonland, permanentFilter), min = 0))),
    )

    @Test
    fun reedWind() = card(
        name = "Reed-Wind",
        cost = "{4}{U}",
        type = "Creature — Elemental",
        text = """
            Flying
            When this creature enters, scry 2.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, scry(2)),
    )

    // CR 701.22: X is the number of cards looked at while scrying
    @Test
    fun elvishSailwright() = card(
        name = "Elvish Sailwright",
        cost = "{2}{U}",
        type = "Creature — Elf Pilot",
        text = """
            Whenever this creature attacks, scry 1.
            Whenever you scry, tap up to X target nonland permanents, where X is the number of cards looked at while scrying this way.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(attacksSelf, scry(1)),
        triggered(youScry, tapIt(target("permanents")), targets = listOf(arcanum.api.TargetSlot("permanents",
            arcanum.api.TargetKind.Objects(and(nonland, permanentFilter)), min = n(0), max = Value.EventAmount))),
    )

    // CR 604.3: power equal to the cards in hand; tapping any number of Humans to draw
    @Test
    fun whiteCityGarrison() = card(
        name = "White City Garrison",
        cost = "{3}{U}",
        type = "Creature — Human Soldier",
        text = """
            White City Garrison's power is equal to the number of cards in your hand.
            Whenever this creature attacks, you may tap any number of untapped Humans you control. Draw a card for each Human tapped this way.
        """,
        pt = "*/5",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.SetPowerToughness(count(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), null)), affects = Affected.Objects(Objects.Self), characteristicDefining = true),
        triggered(attacksSelf,
            Step.Choose(Choice.Objects(and(ObjectFilter.Not(tapped), sub("Human"), you), n(0), null), bind = "tapped", chooser = Players.You),
            tapIt(Objects.Bound("tapped")),
            draw(Value.Count(Objects.Bound("tapped")))),
    )

    private val verse = CounterKind.Named("verse")
    private val versesOnSelf = Value.Of(NumericProperty.Counters(verse), Objects.Self, Combine.Single)
    private val secondSpell = Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2)
    @Suppress("unused") private val unused = Ability.Unparsed("")
}
