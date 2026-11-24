package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 9: modes in triggers ("That Vehicle"), a look-and-put under a total mana value, damage that changes amount ("X plus 2", paid), split
 * damage, damage to each of X targets, counters distributed among a target player's permanents, put-or-remove for each counter kind, an
 * intervening "if it doesn't have a counter", "combat damage is dealt to you", "X minus one" tokens for each opponent, a named token. */
class NeoChunk09ChooseCounterCreateDamageTest {
    private val vehicle = sub("Vehicle")
    private val planeswalker: ObjectFilter = ObjectFilter.Type(CardType.Planeswalker)
    private val x: Value = Value.Announced(name = "X")
    private val samuraiToken = creatureToken("Samurai", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance))
    private val indestructibleCounter: CounterKind = CounterKind.Keyword(Keyword.Indestructible.kind)
    private val charge: CounterKind = CounterKind.Named("charge")
    private val chargeCounters: Value = Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single)

    // CR 702.122a (crew), 702.171a: it crews as though its power were 2 greater
    @Test
    fun hotwireMechanic() = card(
        name = "Hotwire Mechanic",
        cost = "{W}",
        type = "Artifact Creature — Fox Pilot",
        pt = "2/1",
        text = "This creature crews Vehicles as though its power were 2 greater.",
    ) parsesTo abilities(static(Mechanic.PowerGreaterFor(Keyword.Crew.Kind, 2)))

    // CR 700.2 (modes), 601.2c (both may be chosen only if the condition held as it was cast)
    @Test
    fun essenceShift() = card(
        name = "Essence Shift",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            Choose one. If you control an artifact and an enchantment as you cast this spell, you may choose both instead.
            • Exile target creature or planeswalker.
            • Return target creature or planeswalker card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", or(creature, planeswalker)))),
            mode(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(creature, planeswalker)))))),
            min = 1, max = 1, chooser = Players.You, maxIf = controlArtifactAndEnchantment, maxIfCount = 2, upToMaxIf = true))),
    )

    // CR 700.2 (modes), 614.1c, 616.1 (the next event in which such creatures enter: each of them enters with the counters, once)
    @Test
    fun storyweave() = card(
        name = "Talespinner's Weave",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Put two +1/+1 counters on target creature you control.
            • Put two lore counters on target Saga you control. The next time one or more enchantment creatures you control enter this turn, each enters with two additional +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(counters(target("creature"), 2), targets = listOf(slot("creature", and(creature, you)))),
            mode(
                counters(target("Saga"), 2, CounterKind.Lore),
                doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.ZoneChange(and(enchantment, creature, you), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(p1p1, n(2))))), null, Duration.EndOfTurn, uses = 1, perEvent = true)),
                targets = listOf(slot("Saga", and(sub("Saga"), you))),
            ),
        ))),
    )

    // CR 700.2 (a modal triggered ability), 608.2c ("any number": none to all seven), 401.4 (the rest on the bottom in a random order)
    @Test
    fun reiTheMorningSky() = card(
        name = "Rei, the Morning Sky",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Dragon Spirit",
        pt = "5/4",
        text = """
            Flying, vigilance
            When Rei dies, choose one —
            • Look at the top seven cards of your library. Put any number of nonland permanent cards with total mana value 4 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
            • Put two +1/+1 counters on each permanent you control that's a creature or Vehicle.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(diesSelf, Instructions(modes = modes(
            mode(
                doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(and(nonland, permanentCard), n(0), n(7), from = Objects.Bound("seen"),
                    totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(4))), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
                doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ),
            mode(counters(Objects.Each(and(permanentFilter, you, or(creature, vehicle))), 2)),
        ))),
    )

    // CR 508.1a (only creatures attack: "that Vehicle" is the attacking creature), 700.2
    @Test
    fun kitsuneWingrider() = card(
        name = "Kitsune Wingrider",
        cost = "{1}{W}",
        type = "Creature — Fox Pilot",
        pt = "2/2",
        text = """
            Whenever a Vehicle you control attacks, choose one —
            • That Vehicle gains first strike until end of turn.
            • Untap this creature.
        """,
    ) parsesTo abilities(
        Ability.Triggered(on(EventPattern.Attack(and(vehicle, you))), Instructions(modes = modes(
            mode(gains(Objects.EventObject(EventRole.Object), Keyword.FirstStrike)),
            mode(untapIt(Objects.Self)),
        ))),
    )

    // CR 601.2f (each card exiled reduces the cost by {2}), 107.3a (X), 115.1 (the target), 201.2 (a card with the same name), 701.23a
    @Test
    fun marchOfSproutingKin() = card(
        name = "March of Sprouting Kin",
        cost = "{X}{G}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of green cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Choose target creature with mana value less than X. Search your library for a creature card with the same name as that creature, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = Cost(listOf(
                CostPart.Exile(what = CostObjects.Choose(ObjectFilter.OfColor(Color.Green), count = null), from = Zone.Hand, postBind = "exiled"),
            ))),
            Mechanic.CostChange(what = ObjectFilter.Is(Objects.Self), change = CostChange.Less(ManaCost.parse("{2}")!!, times = Value.Count(Objects.Bound("exiled")))),
        ), affects = Affected.Objects(Objects.Self)),
        spell(
            search(and(creature, ObjectFilter.SameNameAs(target("creature")))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
            targets = listOf(TargetSlot("creature", TargetKind.Objects(and(creature, ObjectFilter.ManaValue(Comparison.Less, x))), min = n(1), max = n(1))),
            announced = listOf("X"),
        ),
    )

    // CR 118.12 ("You may sacrifice … If you do"), 400.7 (the returned permanent is a new object), 614.1a (exiled instead of leaving)
    @Test
    fun sisterSpiritsSummons() = card(
        name = "Sister-Spirit's Summons",
        cost = "{3}{W}{B}",
        type = "Enchantment",
        text = "At the beginning of your end step, choose target permanent card in your graveyard. You may sacrifice a permanent that shares a card type with the chosen card. If you do, return the chosen card from your graveyard to the battlefield and it gains \"If this permanent would leave the battlefield, exile it instead of putting it anywhere else.\"",
    ) parsesTo abilities(
        triggered(yourEndStep,
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(permanentFilter, ObjectFilter.Shares(SharedQuality.CardType, target("card"))),
                count = n(1))))), payer = Players.You, bind = "paid"),
            ifThen(Condition.Did("paid"),
                move(target("card"), Zone.Battlefield, postBind = "returned"),
                doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.Replacement(
                    EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, notTo = Zone.Exile),
                    modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))), affects = Affected.Objects(Objects.Self))))),
                    Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely))),
            targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
    )

    // CR 122.1 (each kind of counter on it as this resolves), 608.2d (a choice for each kind)
    @Test
    fun playwrightsMarionette() = card(
        name = "Playwright's Marionette",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        pt = "2/4",
        text = "When this creature enters, for each kind of counter on target permanent, put another counter of that kind on it or remove one from it.",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.OneMoreCounterOfEachKind(Recipients(objects = target("permanent")), orRemove = true)),
            targets = listOf(slot("permanent", permanentFilter))),
    )

    // CR 115.1 (only the player is a target), 608.2d (the permanents chosen as it resolves, each at least one counter)
    @Test
    fun callForthJustice() = card(
        name = "Call Forth Justice",
        cost = "{1}{W}{W}{W}{W}",
        type = "Sorcery",
        text = "Return target permanent card from your graveyard to the battlefield, then distribute four +1/+1 counters among any number of creatures and/or Vehicles target player controls.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield),
            Step.Choose(Choice.Objects(and(or(creature, vehicle), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(4)),
                bind = "chosen", chooser = Players.You),
            doing(Effect.PutCounters(Objects.Bound("chosen"), p1p1, n(4), distributed = true)),
            targets = listOf(slot("card", inYourGraveyard(permanentCard)), targetPlayer),
        ),
    )

    // CR 603.4 (checked as it triggers and resolves), 122.1b (an indestructible counter); CR 510.2, 603.2c (once for each combat damage step)
    @Test
    fun kaedeAsariCaptain() = card(
        name = "Kaede, Asari Captain",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Samurai",
        pt = "3/3",
        text = """
            Haste
            Whenever Kaede deals combat damage to a player, if it doesn't have an indestructible counter on it, put an indestructible counter on it.
            Whenever combat damage is dealt to you, remove an indestructible counter from Kaede.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(combatDamageToAPlayer, counters(Objects.Self, 1, indestructibleCounter),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(indestructibleCounter, atLeast = 1)))),
        triggered(Trigger.On(listOf(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You), combat = true)), oneOrMore = true),
            doing(Effect.RemoveCounters(Objects.Self, indestructibleCounter, n(1)))),
    )

    // CR 107.3a (X), 107.1b (X minus one is at least 0 tokens), 111.2 (each opponent creates and controls theirs)
    @Test
    fun sokenzanUprising() = card(
        name = "Sokenzan Uprising",
        cost = "{X}{R}{W}",
        type = "Sorcery",
        text = """
            Create X 2/2 white Samurai creature tokens with vigilance. They gain menace and haste until end of turn.
            Each opponent creates X minus one 2/2 white Samurai creature tokens with vigilance.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.CreateToken(samuraiToken, x, Players.You, bind = "tokens")),
            untilEndOfTurn(Objects.Bound("tokens"), has(Keyword.Menace, Keyword.Haste)),
            Step.Do(Effect.CreateToken(samuraiToken, Value.Difference(x, n(1)), Players.Each(PlayerFilter.Opponent))),
            announced = listOf("X"),
        ),
    )

    // CR 601.2f (paid: 4 damage instead of 2), 608.2c
    @Test
    fun arcSurge() = card(
        name = "Arc Surge",
        cost = "{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may sacrifice an artifact.
            Arc Surge deals 2 damage to target creature or planeswalker. If this spell's additional cost was paid, Arc Surge deals 4 damage instead.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(artifact, count = n(1))))), optional = true)),
            affects = Affected.Objects(Objects.Self)),
        spell(
            ifThen(Condition.Paid(times = 1, of = Objects.Self),
                damage(4, toObjects(target("permanent"))), otherwise = listOf(damage(2, toObjects(target("permanent"))))),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )

    // CR 107.3a (X), 700.9 (modified: Equipment, Auras you control, counters), 601.2 (judged as it was cast)
    @Test
    fun blazeDischarge() = card(
        name = "Blaze Discharge",
        cost = "{X}{R}",
        type = "Instant",
        text = "Blaze Discharge deals X damage to target creature or planeswalker. If you controlled a modified creature as you cast this spell, it deals X plus 2 damage instead. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.AsCast(Condition.Exists(and(creature, you, ObjectFilter.Modified), atLeast = 1)),
                damage(Value.Sum(listOf(x, n(2))), toObjects(target("permanent"))), otherwise = listOf(damage(x, toObjects(target("permanent"))))),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
            announced = listOf("X"),
        ),
    )

    // CR 120.2 (one source dealing both amounts), 115.1 (the opponent is a target)
    @Test
    fun tremorWave() = card(
        name = "Tremor Wave",
        cost = "{2}{R}",
        type = "Instant",
        text = "Tremor Wave deals 2 damage to any target and 1 damage to each nonartifact creature target opponent controls.",
    ) parsesTo abilities(
        spell(
            damage(2, toAny),
            damage(1, toObjects(Objects.Each(and(not(artifact), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))))),
            targets = listOf(anyTarget, targetOpponent),
        ),
    )

    // CR 107.3a (exactly X targets), 115.3 (not "other": the creature you control may be one of them), 120.3
    @Test
    fun whirlingWheelKick() = card(
        name = "Whirling Wheel Kick",
        cost = "{X}{X}{G}{G}",
        type = "Sorcery",
        text = "Target creature you control deals damage equal to its power to each of X target creatures and/or planeswalkers.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toObjects(target("planeswalkers")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), TargetSlot("planeswalkers", TargetKind.Objects(or(creature, planeswalker)), min = x, max = x)),
            announced = listOf("X"),
        ),
    )

    // CR 702.150a (compleated), 606.3 (loyalty abilities), 707.2 (a copy of the exiled card), 111.4, 201.5 (a named legendary token)
    @Test
    fun kiyomiCompleatedScholar() = card(
        name = "Kiyomi, Compleated Scholar",
        cost = "{2}{G}{G/U/P}{U}",
        type = "Legendary Planeswalker — Kiyomi",
        loyalty = "5",
        text = """
            Compleated ({G/U/P} can be paid with {G}, {U}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Tap up to one target artifact or creature. It doesn't untap during its controller's next untap step.
            [−X]: Exile target nonland permanent card with mana value X from your graveyard. Create a token that's a copy of that card.
            [−7]: Create Kiyomi's Journal, a legendary colorless Book artifact token with "Spells you cast cost {2} less to cast" and "{T}: Draw a card."
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1, tapIt(target("permanent")), doesntUntapNext(target("permanent")), targets = listOf(slot("permanent", or(artifact, creature), min = 0))),
        Ability.Activated(cost(CostPart.Loyalty(Value.Times(x, -1))), Instructions(
            targets = listOf(slot("card", inYourGraveyard(nonland, permanentCard, ObjectFilter.ManaValue(Comparison.Equal, x)))),
            steps = listOf(
                move(target("card"), Zone.Exile, postBind = "exiled"),
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("exiled")), n(1), Players.You)),
            ),
            announced = listOf("X"),
        ), anyTime),
        loyalty(-7, Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
            name = "Kiyomi's Journal",
            colorIndicator = emptyList(),
            supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Artifact),
            subtypes = listOf(Subtype("Book")),
            abilities = listOf(
                Ability.Static(listOf(Mechanic.CostChange(what = and(spellFilter, you), change = CostChange.Less(ManaCost.parse("{2}")!!, times = n(1)))),
                    affects = Affected.Objects(Objects.Self)),
                activated(cost(tap), draw(1)),
            ),
        )), n(1), Players.You))),
    )

    // CR 122.1 (charge counters), 602.2 (sacrificed as a cost: the counters as it last existed, CR 608.2h), 120.2 (that much to each)
    @Test
    fun sparkcoilReactor() = card(
        name = "Sparkcoil Reactor",
        cost = "{1}{R}",
        type = "Artifact",
        text = """
            Whenever this artifact or another artifact you control enters, put a charge counter on this artifact.
            {4}, Sacrifice this artifact: It deals damage equal to the number of charge counters on it to target player and that much damage to up to one target creature.
        """,
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, artifact, you))), counters(Objects.Self, 1, charge)),
        activated(cost(mana("{4}"), sacrificeSelf),
            damage(chargeCounters, Recipients(players = Players.Target("player"))),
            damage(chargeCounters, toObjects(target("creature"))),
            targets = listOf(targetPlayer, slot("creature", creature, min = 0))),
    )
}
