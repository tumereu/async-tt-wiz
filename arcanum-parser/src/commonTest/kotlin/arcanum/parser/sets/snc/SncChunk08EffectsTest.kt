package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 08: choices of counters and targets, sacrifices that share a type, copies, colour counts, counter kinds, damage and draws. */
class SncChunk08EffectsTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val opponentBound = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))
    private val angel = creatureToken("Angel", listOf(Color.White), 3, 3, abilities = listOf(Keyword.Flying))

    // CR 122.1b (keyword counters), 115.1 ("up to one": none may be chosen)
    @Test
    fun elspethResplendent() = card(
        name = "Ysolde Resplendent",
        cost = "{3}{W}{W}",
        type = "Legendary Planeswalker — Elspeth",
        loyalty = "5",
        text = """
            [+1]: Choose up to one target creature. Put a +1/+1 counter and a counter from among flying, first strike, lifelink, or vigilance on it.
            [−3]: Look at the top seven cards of your library. You may put a permanent card with mana value 3 or less from among them onto the battlefield with a shield counter on it. Put the rest on the bottom of your library in a random order.
            [−7]: Create five 3/3 white Angel creature tokens with flying.
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(1))),
            Step.Choose(Choice.Option(listOf("flying", "first strike", "lifelink", "vigilance")), bind = "counter", chooser = Players.You),
            counters(target("creature")),
            ifThen(Condition.Chose("counter", "flying"), counters(target("creature"), kind = CounterKind.Keyword(Keyword.Flying.kind)), otherwise = listOf(
                ifThen(Condition.Chose("counter", "first strike"), counters(target("creature"), kind = CounterKind.Keyword(Keyword.FirstStrike.kind)), otherwise = listOf(
                    ifThen(Condition.Chose("counter", "lifelink"), counters(target("creature"), kind = CounterKind.Keyword(Keyword.Lifelink.kind)), otherwise = listOf(
                        counters(target("creature"), kind = CounterKind.Keyword(Keyword.Vigilance.kind)))))))),
            targets = listOf(slot("creature", creature, min = 0))),
        activated(cost(CostPart.Loyalty(n(-3))),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(permanentCard, manaValue(Comparison.AtMost, 3)), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.Shield, n(1)))), postBind = "put")),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        activated(cost(CostPart.Loyalty(n(-7))), create(angel, n(5))),
    )

    // CR 701.21a (sacrifice of their choice), 101.4 (each opponent chooses in turn order, then all at once), 608.2h (the sacrificed permanent as it
    // last existed), 601.2h (the additional cost)
    @Test
    fun fatalGrudge() = card(
        name = "Festering Feud",
        cost = "{B}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a nonland permanent.
            Each opponent chooses a permanent they control that shares a card type with the sacrificed permanent and sacrifices it.
            Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(and(nonland, permanentFilter), preBind = "sacrificed")))),
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Choose(Choice.Objects(and(permanentFilter, opponentBound, ObjectFilter.Shares(SharedQuality.CardType, Objects.Bound("sacrificed"))), n(1), n(1)),
                    bind = "chosen", chooser = Players.Bound("opponent")),
                doing(Effect.Sacrifice(Objects.Bound("chosen")))), together = true),
            draw(1)),
    )

    // CR 115.3 (two targets with one controller), 608.2b (only the legal ones), 701.21a
    @Test
    fun incriminate() = card(
        name = "Frame the Accused",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Choose two target creatures controlled by the same player. That player sacrifices one of them of their choice.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = target("creatures")), bind = "chosen", chooser = Players.ControllerOf(target("creatures"))),
            doing(Effect.Sacrifice(Objects.Bound("chosen"))),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = n(2), max = n(2), sameController = true))),
    )

    // CR 700.2 (modes), 120.3
    @Test
    fun maestrosCharm() = card(
        name = "Virtuoso Charm",
        cost = "{U}{B}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Look at the top five cards of your library. Put one of those cards into your hand and the rest into your graveyard.
            • Each opponent loses 3 life and you gain 3 life.
            • Virtuoso Charm deals 5 damage to target creature or planeswalker.
        """,
    ) parsesTo listOf(Ability.Spell(Instructions(modes = modes(
        mode(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            move(Objects.Bound("kept"), Zone.Hand),
            move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Zone.Graveyard)),
        mode(loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3)),
        mode(damage(5, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker)))),
    ))))

    // CR 700.2, 701.21a (that player chooses among those tied for greatest), 117.1 (play until your next end step)
    @Test
    fun riveteersCharm() = card(
        name = "Ironworkers Charm",
        cost = "{B}{R}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Target opponent sacrifices a creature or planeswalker they control with the greatest mana value among creatures and planeswalkers they control.
            • Exile the top three cards of your library. Until your next end step, you may play those cards.
            • Exile target player's graveyard.
        """,
    ) parsesTo listOf(Ability.Spell(Instructions(modes = modes(
        mode(
            Step.Choose(Choice.Objects(and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))),
                ObjectFilter.ManaValue(Comparison.Equal, Value.Of(NumericProperty.ManaValue,
                    Objects.Each(and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))), Combine.Greatest))),
                n(1), n(1)), bind = "chosen", chooser = Players.Target("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("chosen"))),
            targets = listOf(targetOpponent)),
        mode(
            move(Objects.LibraryCards(Players.You, n(3), fromTop = true), Zone.Exile, postBind = "exiled"),
            untilEndOfYourNextEndStep(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You))),
        mode(move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
            targets = listOf(targetPlayer)),
    ))))
    private fun untilEndOfYourNextEndStep(mechanic: Mechanic): Step =
        Step.Do(Effect.ApplyMechanics(listOf(mechanic), Affected.Objects(Objects.Bound("exiled")), Duration.UntilStep(TurnStep.End, PlayerFilter.You)))

    // CR 105.1, 115.3 (one target per colour; a multicoloured card can be the target for only one of its colours), 400.7
    @Test
    fun roguesGallery() = card(
        name = "Lineup of Crooks",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "For each color, return up to one target creature card of that color from your graveyard to your hand.",
    ) parsesTo abilities(
        spell(move(Objects.Union(listOf("white", "blue", "black", "red", "green").map { target("$it card") }), Zone.Hand),
            targets = listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green).mapIndexed { i, c ->
                TargetSlot("${c.toString().lowercase()} card", TargetKind.Objects(and(ObjectFilter.OfColor(ColorRef.Literal(c)), creature, *yourGraveyard.toTypedArray())),
                    min = n(0), max = n(1), distinctFrom = listOf("white", "blue", "black", "red", "green").take(i).map { "$it card" })
            }),
    )

    // CR 707.10 (a copy wasn't cast), 707.10c (new targets), 601.2a
    @Test
    fun errantStreetArtist() = card(
        name = "Vagrant, Alley Painter",
        cost = "{U}",
        type = "Legendary Creature — Human Rogue",
        pt = "0/3",
        text = """
            Flash
            Defender, haste
            {1}{U}, {T}: Copy target spell you control that wasn't cast. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Defender,
        Keyword.Haste,
        activated(cost(mana("{1}{U}"), tap), doing(Effect.CopySpell(target("spell"), controller = Players.You, newTargets = true)),
            targets = listOf(slot("spell", and(spellFilter, you, not(ObjectFilter.WasCast()))))),
    )

    // CR 707.9b, 614.1c (the counter only as a copy, and only of a creature you control)
    @Test
    fun undercoverOperative() = card(
        name = "Deep Cover Agent",
        cost = "{2}{U}{U}",
        type = "Creature — Shapeshifter Rogue",
        pt = "0/0",
        text = "You may have this creature enter as a copy of any creature on the battlefield, except it enters with a shield counter on it if you control that creature. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(
            Modification.EntersAsCopy(creature, optional = true, withCounters = listOf(
                Modification.EntersWithCounters(CounterKind.Shield, n(1), ifCopied = ObjectFilter.ControlledBy(PlayerFilter.You))))))),
    )

    // CR 105.2 (number of colours), 608.2h (the sacrificed creature as it last existed), 400.7 (the exiled card is a new object), 608.2g (cast while
    // it resolves, paying its costs)
    @Test
    fun evolvingDoor() = card(
        name = "Shifting Portal",
        cost = "{2}{G}",
        type = "Artifact",
        text = "{1}, {T}, Sacrifice a creature: Count the colors of the sacrificed creature, then search your library for a creature card that's exactly that many colors plus one. Exile that card, then shuffle. You may cast the exiled card. Activate only as a sorcery.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, sacrificeA(creature, preBind = "sacrificed")),
            search(and(creature, ObjectFilter.Where(Condition.Compare(Value.CountDistinct(Objects.Tested, SharedQuality.Color), Comparison.Equal,
                Value.Sum(listOf(Value.CountDistinct(Objects.Bound("sacrificed"), SharedQuality.Color), n(1))))))),
            move(Objects.Bound("found"), Zone.Exile, postBind = "exiled"),
            shuffle,
            Step.Pay(steps = listOf(doing(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, caster = Players.You))), payer = Players.You, bind = "cast"),
            rules = asSorcery),
    )

    // CR 603.4 (intervening if, checked as it triggers and as it resolves), 122.1, 613.4c
    @Test
    fun obscuraAscendancy() = card(
        name = "Veiled Dominion",
        cost = "{W}{U}{B}",
        type = "Enchantment",
        text = """
            Whenever you cast a spell, if its mana value is equal to 1 plus the number of soul counters on this enchantment, put a soul counter on this enchantment, then create a 2/2 white Spirit creature token with flying.
            As long as there are five or more soul counters on this enchantment, Spirits you control get +3/+3.
        """,
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Any),
            counters(Objects.Self, kind = CounterKind.Named("soul")),
            create(creatureToken("Spirit", listOf(Color.White), 2, 2, abilities = listOf(Keyword.Flying))),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.ManaValue(Comparison.Equal,
                Value.Sum(listOf(n(1), Value.Of(NumericProperty.Counters(CounterKind.Named("soul")), Objects.Self, Combine.Single)))))),
        static(pt(3, 3), affects = Objects.Each(and(sub("Spirit"), you)),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("soul"), atLeast = 5))),
    )

    // CR 122.1 (each kind as it resolves), 111.10 (the tokens made first), 608.2d (the choice of token)
    @Test
    fun exoticPets() = card(
        name = "Curious Critters",
        cost = "{1}{W}{U}",
        type = "Instant",
        text = "Create two 1/1 blue Fish creature tokens with \"This token can't be blocked.\" Then for each kind of counter among creatures you control, put a counter of that kind on either of those tokens.",
    ) parsesTo abilities(
        spell(
            create(creatureToken("Fish", listOf(Color.Blue), 1, 1, abilities = listOf(static(Mechanic.CantBeBlocked))), n(2), bind = "tokens"),
            doing(Effect.PutCounterKindsAmong(Objects.Each(and(creature, you)), Objects.Bound("tokens")))),
    )

    // CR 601.2h (the exiled card), 608.2h (its power as the spell resolves), 120.2 (one damage event)
    @Test
    fun corpseExplosion() = card(
        name = "Cadaver Burst",
        cost = "{1}{B}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, exile a creature card from your graveyard.
            Cadaver Burst deals damage equal to the exiled card's power to each creature and each planeswalker.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(CostPart.Exile(CostObjects.Choose(creature, count = n(1)), from = Zone.Graveyard, postBind = "exiled")))),
        spell(damage(power(Objects.Bound("exiled")), toObjects(Objects.Each(or(creature, planeswalker))))),
    )

    // CR 115.3 ("other"), 608.2b (each target still legal, destroyed or not), 608.2h (its controller as it last existed), 101.4 (tokens at once)
    @Test
    fun soulOfEmancipation() = card(
        name = "Spirit of Liberation",
        cost = "{4}{G}{W}{U}",
        type = "Creature — Avatar",
        pt = "5/7",
        text = "When this creature enters, destroy up to three other target nonland permanents. For each of those permanents, its controller creates a 3/3 white Angel creature token with flying.",
    ) parsesTo abilities(
        triggered(entersSelf,
            destroy(target("permanents")),
            Step.ForEach(Each.Objects(target("permanents")), "permanent", listOf(
                doing(Effect.CreateToken(angel, n(1), Players.ControllerOf(Objects.Bound("permanent"))))), together = true),
            targets = listOf(slot("permanents", and(another, nonland, permanentFilter), min = 0, max = 3))),
    )

    // CR 608.2c (checked as it resolves, before the destruction), 101.4
    @Test
    fun depopulate() = card(
        name = "Thin the Streets",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = "Each player who controls a multicolored creature draws a card. Then destroy all creatures.",
    ) parsesTo abilities(
        spell(
            draw(1, Players.Each(PlayerFilter.Where(Condition.Exists(and(ObjectFilter.Multicolored, creature,
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))), atLeast = 1)))),
            destroy(Objects.Each(creature))),
    )

    // CR 506.2 (defending player), 506.4 (removed from combat), 608.2d (that player decides)
    @Test
    fun shakedownHeavy() = card(
        name = "Extortion Brute",
        cost = "{2}{B}",
        type = "Creature — Ogre Warrior",
        pt = "6/4",
        text = """
            Menace
            Whenever this creature attacks, defending player may have you draw a card. If they do, untap this creature and remove it from combat.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf,
            Step.Pay(steps = listOf(draw(1)), payer = Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object)), bind = "paid"),
            ifThen(Condition.Did("paid"), untapIt(Objects.Self), doing(Effect.RemoveFromCombat(Objects.Self)))),
    )

    // CR 614.1a, 614.11 (a draw replaced: the next one only), 608.2h (that player fixed as it resolves), 117.1 (may play it this turn)
    @Test
    fun urabraskHereticPraetor() = card(
        name = "Vexrask, Apostate Praetor",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Phyrexian Praetor",
        pt = "4/4",
        text = """
            Haste
            At the beginning of your upkeep, exile the top card of your library. You may play it this turn.
            At the beginning of each opponent's upkeep, the next time they would draw a card this turn, instead they exile the top card of their library. They may play it this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You),
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.Opponent),
            Step.ForEach(Each.Players(Players.EventPlayer), "player", listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.Is(Players.Bound("player"))), instead = Instructions(steps = listOf(
                    move(Objects.LibraryCards(Players.Bound("player"), n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
                    untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.Is(Players.Bound("player")))))))),
                    null, Duration.EndOfTurn, uses = 1))), together = true)),
    )
}
