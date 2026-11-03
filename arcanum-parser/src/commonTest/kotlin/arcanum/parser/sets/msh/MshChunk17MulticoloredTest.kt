package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 17: multicoloured cards. A copy until your next turn with exceptions (CR 707.9b); a token getting a counter
 * for each counter on this enchantment; "you may pay {1}. When you do" (CR 603.12); attacking creatures that die returning to hand;
 * counters removed in any number as a cost (CR 601.2f, announced); flying while you've put a counter on it this turn; modes for each
 * nontoken Hero entering (CR 700.2); a sacrifice or a discard as a cost; untapping a creature the first time it becomes tapped in your
 * turn (CR 603.4); a revealed hand and a card or creature exiled until it leaves (CR 610.3); playing the exiled top card; toughness
 * assigning combat damage (CR 510.1c).
 */
class MshChunk17MulticoloredTest {
    // CR 707.9b: a copy with exceptions until your next turn
    @Test
    fun absorbentBrute() = card(
        name = "Absorbent Brute",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Villain",
        text = """
            Vigilance
            At the beginning of your first main phase, until your next turn, Absorbent Brute becomes a copy of up to one target artifact, non-Aura enchantment, or land, except his name is Absorbent Brute, he's a legendary 4/4 Human Villain creature in addition to his other types, and he has vigilance.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(stepBegins(arcanum.api.TurnStep.PrecombatMain, PlayerFilter.You),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("target"), except = listOf(
                arcanum.api.CopyException.Name("Absorbent Brute"),
                arcanum.api.CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Human")), SubtypeRef.Literal(Subtype("Villain"))),
                    addSupertypes = listOf(Supertype.Legendary)),
                arcanum.api.CopyException.PowerToughness(4, 4),
                arcanum.api.CopyException.AddAbilities(listOf(Keyword.Vigilance))))), arcanum.api.Affected.Objects(Objects.Self), Duration.UntilYourNextTurn)),
            targets = listOf(slot("target", or(artifact, and(not(sub("Aura")), enchantment), land), min = 0))),
    )

    // the new token gets a +1/+1 counter for each invasion counter, then this enchantment gets one
    @Test
    fun invadersFromBeyond() = card(
        name = "Invaders from Beyond",
        cost = "{1}{R}{R}{G}",
        type = "Enchantment",
        text = "At the beginning of combat on your turn, create a 1/1 red Alien creature token with haste and \"This token attacks each combat if able.\" Put a +1/+1 counter on it for each invasion counter on this enchantment, then put an invasion counter on this enchantment.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.Do(Effect.CreateToken(creatureToken("Alien", listOf(Color.Red), 1, 1, abilities = listOf(Keyword.Haste, static(Mechanic.MustAttack()))), n(1), Players.You, bind = "token")),
            counters(Objects.Bound("token"), Value.Of(NumericProperty.Counters(CounterKind.Named("invasion")), Objects.Self, Combine.Single)),
            counters(Objects.Self, 1, CounterKind.Named("invasion"))),
    )

    // CR 603.12: "you may pay {1}. When you do"; each counter you put on a creature (once each turn)
    @Test
    fun antKnightColonyCommander() = card(
        name = "Ant Knight, Colony Commander",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Rogue Hero",
        text = """
            Whenever Ant Knight attacks, you may pay {1}. When you do, put a +1/+1 counter on target creature.
            Whenever you put a +1/+1 counter on a creature, create a 1/1 green Insect creature token. This ability triggers only once each turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(attacksSelf, Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(counters(target("creature")), targets = listOf(slot("creature", creature)))))),
        triggered(on(EventPattern.CountersPut(creature, p1p1, causedBy = byYou)), create(creatureToken("Insect", listOf(Color.Green), 1, 1)), perTurn = 1),
    )

    // CR 508.1d: attacks each combat if able; the card that died returns (CR 400.7)
    @Test
    fun martiusGodOfWar() = card(
        name = "Martius, God of War",
        cost = "{1}{B}{R}",
        type = "Legendary Creature — God Warrior Villain",
        text = """
            Martius attacks each combat if able.
            Whenever an attacking creature you control dies, return that card to its owner's hand.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        static(Mechanic.MustAttack()),
        triggered(on(EventPattern.ZoneChange(and(attacking, creatureYouControl), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.EventObject(EventRole.After), Zone.Hand)),
    )

    // CR 601.2f: the number of counters removed is announced
    @Test
    fun theAstonishingHivemaster() = card(
        name = "The Astonishing Hivemaster",
        cost = "{G}{U}",
        type = "Legendary Creature — Human Scientist Hero",
        text = """
            Whenever you draw a card, put a +1/+1 counter on The Astonishing Hivemaster.
            {2}{G}, {T}, Remove any number of +1/+1 counters from The Astonishing Hivemaster: Create that many 1/1 green Insect creature tokens.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(on(EventPattern.Draw(PlayerFilter.You)), counters(Objects.Self)),
        Ability.Activated(cost(mana("{2}{G}"), tap, CostPart.RemoveCounters(p1p1, Value.Announced("X"), CostObjects.Self)),
            Instructions(steps = listOf(create(creatureToken("Insect", listOf(Color.Green), 1, 1), Value.Announced("X"))), announced = listOf("X")), anyTime),
    )

    // flying while you've put a +1/+1 counter on it this turn
    @Test
    fun bruinEruditeAerialist() = card(
        name = "Bruin, Erudite Aerialist",
        cost = "{3}{G/U}",
        type = "Legendary Creature — Mutant Scientist Hero",
        text = """
            As long as you've put one or more +1/+1 counters on Bruin this turn, he has flying.
            Whenever Bruin deals combat damage to a player, draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(has(Keyword.Flying), condition = Condition.HappenedThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1, causedBy = byYou),
            atLeast = 1, measure = Measure.Events)),
        triggered(combatDamageToAPlayer, draw(1)),
    )

    @Test
    fun shadowPantherVanguard() = card(
        name = "Shadow Panther, Vanguard",
        cost = "{2}{G}{W}",
        type = "Legendary Creature — Human Warrior Hero",
        text = """
            Whenever another nontoken Hero you control enters, choose one —
            • Create a 1/1 white Soldier creature token.
            • Creatures you control get +1/+1 until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Ability.Triggered(enters(and(another, not(token), hero, you)), Instructions(modes = modes(
            mode(create(soldier)), mode(pump(Objects.Each(creatureYouControl), 1, 1))))),
    )

    // one of two costs, as it enters (a reflexive trigger, CR 603.12) and as an activation cost
    @Test
    fun pinpointDeathDealer() = card(
        name = "Pinpoint, Death Dealer",
        cost = "{2}{B/R}",
        type = "Legendary Creature — Human Assassin Villain",
        text = """
            When Pinpoint enters, you may sacrifice an artifact or discard a nonland card. When you do, Pinpoint deals 2 damage to any target.
            {3}, {T}, Sacrifice an artifact or discard a nonland card: Pinpoint deals 2 damage to any target.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(Cost(listOf(sacrificeOrDiscardNonland)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(damage(2, toAny), targets = listOf(anyTarget))))),
        activated(cost(mana("{3}"), tap, sacrificeOrDiscardNonland), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 603.4: only the first time that creature became tapped this turn, in your turn
    @Test
    fun commanderLibertyLivingLegend() = card(
        name = "Commander Liberty, Living Legend",
        cost = "{1}{W}{U}",
        type = "Legendary Creature — Human Soldier Hero",
        text = """
            Vigilance
            Whenever a creature you control becomes tapped during your turn, if it's the first time that creature has become tapped this turn, untap it.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.Tap(creatureYouControl)), untapIt(Objects.EventObject(EventRole.Object)), triggersOnlyIf = Condition.YourTurn,
            interveningIf = Condition.And(listOf(
                Condition.HappenedThisTurn(EventPattern.Tap(ObjectFilter.Is(Objects.EventObject(EventRole.Object))), atLeast = 1, measure = Measure.Events),
                Condition.Not(Condition.HappenedThisTurn(EventPattern.Tap(ObjectFilter.Is(Objects.EventObject(EventRole.Object))), atLeast = 2, measure = Measure.Events))))),
    )

    // CR 610.3: a nonland card from the revealed hand or the chosen creature, exiled until they leave
    @Test
    fun veilAndBladeEntwined() = card(
        name = "Veil and Blade, Entwined",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Hero",
        text = """
            Deathtouch, lifelink
            When Veil and Blade enter, choose target opponent and up to one target creature they control. They reveal their hand. You may exile a nonland card from their hand or the chosen creature until Veil and Blade leave the battlefield.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Keyword.Lifelink,
        triggered(entersSelf,
            Step.Do(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))))),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Union(listOf(
                Objects.Each(and(nonland, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), target("creature")))),
                bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(targetOpponent, slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), min = 0))),
    )

    // CR 401.5: the top card looked at any time; the exiled top card playable this turn
    @Test
    fun nightseerManWithoutFear() = card(
        name = "Nightseer, Man Without Fear",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Human Hero",
        text = """
            Vigilance, haste
            Radar Sense — You may look at the top card of your library any time.
            Whenever you attack, you may exile the top card of your library. If that card is a Hero card, Nightseer gets +2/+1 until end of turn. You may play that card this turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Haste,
        Ability.Static(listOf(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, n(1), fromTop = true))), affects = arcanum.api.Affected.Players(Players.You),
            abilityWord = "Radar Sense"),
        triggered(youAttack,
            Step.May(listOf(move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled")), player = Players.You),
            Step.If(Condition.ObjectMatches(Objects.Bound("exiled"), hero), then = listOf(pump(Objects.Self, 2, 1))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), arcanum.api.Affected.Objects(Objects.Bound("exiled")),
                Duration.EndOfTurn))),
    )

    @Test
    fun driftshadeSpectralSaboteur() = card(
        name = "Driftshade, Spectral Saboteur",
        cost = "{2}{U/B}",
        type = "Legendary Creature — Human Rogue Villain",
        text = """
            Flash
            Intangibility — Driftshade can't be blocked.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = arcanum.api.Affected.Objects(Objects.Self), abilityWord = "Intangibility"),
    )

    // CR 603.4: an artifact entered this turn, checked as it triggers and resolves
    @Test
    fun steelSentinelMasterOfMachines() = card(
        name = "Steel Sentinel, Master of Machines",
        cost = "{2}{U}{R}",
        type = "Legendary Artifact Creature — Human Hero",
        text = """
            Flying, vigilance
            Steel Sentinel gets +1/+0 for each other artifact you control.
            Whenever Steel Sentinel attacks, if an artifact entered the battlefield under your control this turn, draw a card.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        static(Mechanic.ModifyPowerToughness(count(and(another, artifact, you)), n(0))),
        triggered(attacksSelf, draw(1), interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(artifact, you), to = Zone.Battlefield),
            atLeast = 1, measure = Measure.Events)),
    )

    @Test
    fun killRaiderScourge() = card(
        name = "Kill-Raider, Scourge of the Kingdom",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Human Mercenary Villain",
        text = """
            When Kill-Raider enters, you may sacrifice another creature. When you do, destroy target nonland permanent an opponent controls.
            As long as there are two or more creature cards in your graveyard, Kill-Raider gets +2/+1.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents))))))),
        static(pt(2, 1), condition = Condition.Exists(inYourGraveyard(creature), atLeast = 2)),
    )

    // CR 702.101a: extort; CR 510.1c: combat damage by toughness for creatures with toughness greater than their power
    @Test
    fun theOverlordOfCrime() = card(
        name = "The Overlord of Crime",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Villain",
        text = """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            Whenever you attack, you may pay 2 life. If you do, until end of turn, creatures you control with toughness greater than their power assign combat damage equal to their toughness rather than their power.
        """,
        pt = "1/5",
    ) parsesTo abilities(
        Keyword.Extort,
        triggered(youAttack, Step.Pay(Cost(listOf(CostPart.PayLife(n(2)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.AssignsDamageByToughness),
                arcanum.api.Affected.Objects(Objects.Each(and(creatureYouControl, ObjectFilter.Toughness(arcanum.api.Comparison.Greater, power(Objects.Tested))))),
                Duration.EndOfTurn))))),
    )

    private val sacrificeOrDiscardNonland = CostPart.OneOf(listOf(
        Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(artifact, count = n(1))))),
        Cost(listOf(CostPart.Discard(CostObjects.Choose(nonland, count = n(1)))))))
}
