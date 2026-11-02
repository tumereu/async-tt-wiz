package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
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
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 07: life gained and lost. "If you gained or lost life this turn", "as long as you've lost life this
 * turn", "if an opponent lost life this turn" are this turn's events (CR 119.9, 119.10); "whenever you gain or lose life
 * during your turn" is one trigger with two events, only on your turn; "for each opponent who lost life this turn"
 * counts players. With them: drains, a defending player's life loss, counters an entering creature gets on a
 * condition, reflexive returns paid for with mana and life.
 */
class BlbChunk07LifeMattersTest {
    private val gainedLife = Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = 1, measure = Measure.Events)
    private val lostLife = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.You), atLeast = 1, measure = Measure.Events)
    private val opponentLostLife = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)
    private val gainOrLoseOnYourTurn = Trigger.On(listOf(EventPattern.GainLife(PlayerFilter.You), EventPattern.LoseLife(PlayerFilter.You)))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val bat = sub("Bat")
    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun drain(n: Int) = listOf(
        Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), one(n))),
        Step.Do(Effect.GainLife(Players.You, one(n))),
    )
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)

    /** "This creature enters with a +1/+1 counter on it if an opponent lost life this turn." (CR 614.1c, 614.12) */
    private val entersWithCounterIfOpponentLostLife = Ability.Static(
        listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, one())))),
        affects = Affected.Objects(Objects.Self),
        condition = opponentLostLife,
    )

    // CR 613.1f: abilities while the condition holds; CR 608.2h: the dead creature's counters as it last existed
    @Test
    fun lifesapConduit() = card(
        name = "Lifesap Conduit",
        cost = "{1}{W}",
        type = "Creature — Bat Cleric",
        pt = "2/1",
        text = """
            As long as you've lost life this turn, this creature has flying and vigilance.
            Whenever you gain life, put a +1/+1 counter on this creature.
            When this creature dies, put its counters on target creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Vigilance))), affects = Affected.Objects(Objects.Self), condition = lostLife),
        Ability.Triggered(trigger = Trigger.On(EventPattern.GainLife(PlayerFilter.You)), instructions = steps(counters(Objects.Self))),
        Ability.Triggered(
            trigger = thisDies,
            instructions = Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(Step.Do(Effect.MoveCounters(Objects.Self, Objects.Target("creature"), kind = null,
                    amount = Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single)))),
            ),
        ),
    )

    @Test
    fun constellationMapper() = card(
        name = "Constellation Mapper",
        cost = "{3}{W}",
        type = "Creature — Bat Cleric",
        pt = "3/1",
        text = """
            Flying
            At the beginning of your end step, if you gained or lost life this turn, look at the top four cards of your library. You may reveal a creature card with power 3 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = yourEndStep,
            instructions = steps(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, one(4), fromTop = true), players = Players.You, bind = "seen")),
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtMost, one(3)))), min = one(0), max = one(),
                    from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                    Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            ),
            interveningIf = Condition.Or(listOf(gainedLife, lostLife)),
        ),
    )

    // CR 603.2: two events, one ability, checked only on your turn as it would trigger
    @Test
    fun moonphaseWatcher() = card(
        name = "Moonphase Watcher",
        cost = "{3}{W}",
        type = "Creature — Bat Cleric",
        pt = "2/4",
        text = """
            Flying, vigilance
            Whenever you gain or lose life during your turn, this creature gets +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(trigger = gainOrLoseOnYourTurn, instructions = steps(pump(Objects.Self, 1, 0)), triggersOnlyIf = Condition.YourTurn),
    )

    // CR 506.2: the defending player of the attacking creature
    @Test
    fun quartzknifeKiller() = card(
        name = "Quartzknife Killer",
        cost = "{1}{B}",
        type = "Creature — Lizard Assassin",
        pt = "1/3",
        text = "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            instructions = steps(
                Step.Do(Effect.LoseLife(Players.DefendingPlayerOf(Objects.EventObject(EventRole.Object)), one())),
                Step.Do(Effect.GainLife(Players.You, one())),
            ),
        ),
    )

    @Test
    fun swoopwingPair() = card(
        name = "Swoopwing Pair",
        cost = "{4}{B}",
        type = "Creature — Bat Lizard",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, each opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(*drain(2).toTypedArray()),
    )

    @Test
    fun hestersLullaby() = card(
        name = "Hester's Lullaby",
        cost = "{3}{B}",
        type = "Instant",
        text = "Return up to two target creature cards from your graveyard to your hand. Each opponent loses 2 life and you gain 2 life.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("cards", ObjectFilter.And(listOf(creature) + yourGraveyard), min = 0, max = 2)),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("cards"), Destination(Zone.Hand)))) + drain(2),
        )),
    )

    // CR 603.2h: once each turn
    @Test
    fun lunastoneHerald() = card(
        name = "Lunastone Herald",
        cost = "{2}{B}",
        type = "Creature — Bat Warrior",
        pt = "1/3",
        text = """
            Flying, deathtouch
            Whenever you gain or lose life during your turn, Bats you control get +1/+0 and gain deathtouch until end of turn. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = gainOrLoseOnYourTurn,
            instructions = steps(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(one(1), one(0)), Mechanic.GainAbilities(listOf(Keyword.Deathtouch))),
                Affected.Objects(Objects.Each(ObjectFilter.And(listOf(bat, you)))),
                Duration.EndOfTurn,
            ))),
            perTurn = 1,
            triggersOnlyIf = Condition.YourTurn,
        ),
    )

    @Test
    fun ominousObelisk() = card(
        name = "Ominous Obelisk",
        cost = "{3}{B}",
        type = "Artifact",
        text = """
            At the beginning of combat on your turn, each opponent loses 1 life and you gain 1 life.
            {T}, Pay 2 life, Sacrifice this artifact: Draw two cards. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Triggered(trigger = yourCombat, instructions = steps(*drain(1).toTypedArray())),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf, CostPart.PayLife(one(2)), CostPart.Sacrifice(CostObjects.Self))),
            instructions = steps(Step.Do(Effect.Draw(Players.You, one(2)))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun starlightDiviner() = card(
        name = "Starlight Diviner",
        cost = "{2}{B}",
        type = "Creature — Bat Cleric",
        pt = "2/2",
        text = """
            Flying
            At the beginning of your end step, if you gained or lost life this turn, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(trigger = yourEndStep, instructions = steps(Step.Do(Effect.Surveil(Players.You, one()))),
            interveningIf = Condition.Or(listOf(gainedLife, lostLife))),
    )

    // CR 701.9b: you choose the discarded card if the opponent lost life this turn; otherwise they choose
    @Test
    fun mindprowlHexer() = card(
        name = "Mindprowl Hexer",
        cost = "{2}{B}",
        type = "Creature — Lizard Warlock",
        pt = "2/2",
        text = """
            Menace
            When this creature enters, choose target opponent. If they lost life this turn, they reveal their hand, you choose a nonland card from it, and they discard that card. Otherwise, they discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(Step.If(
                    Condition.PlayerMatches(Players.Target("opponent"), PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any))),
                    then = listOf(
                        Step.Do(Effect.Reveal(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand),
                            ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))), bind = "revealed")),
                        Step.Choose(Choice.Objects(ObjectFilter.Not(land), min = one(), max = one(), from = Objects.Bound("revealed")), bind = "chosen",
                            chooser = Players.You),
                        Step.Do(Effect.Discard(Players.Target("opponent"), one(), filter = ObjectFilter.Any, objects = Objects.Bound("chosen"))),
                    ),
                    otherwise = listOf(Step.Do(Effect.Discard(Players.Target("opponent"), one(), filter = ObjectFilter.Any))),
                )),
            ),
        ),
    )

    // CR 605.1b: a triggered ability that adds mana but doesn't trigger from mana isn't a mana ability
    @Test
    fun embercacheNewt() = card(
        name = "Embercache Newt",
        cost = "{1}{R}",
        type = "Creature — Lizard Warlock",
        pt = "2/2",
        text = """
            When this creature enters, if an opponent lost life this turn, add {B}{R}.
            {1}{R}, Discard a card: Draw a card.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{B}{R}")!!)), Players.You)),
            interveningIf = opponentLostLife),
        Ability.Activated(
            cost = Cost(listOf(mana("{1}{R}"), CostPart.Discard(CostObjects.Choose(anyCard, count = one())))),
            instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
            rules = any,
        ),
    )

    @Test
    fun crestedBoltslinger() = card(
        name = "Crested Boltslinger",
        cost = "{3}{R}",
        type = "Creature — Lizard Archer",
        pt = "3/3",
        text = """
            Reach, menace
            This creature enters with a +1/+1 counter on it if an opponent lost life this turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Menace,
        entersWithCounterIfOpponentLostLife,
    )

    // CR 508.3d: "whenever you attack with one or more Lizards" triggers once per declaration
    @Test
    fun paidTalon() = card(
        name = "Paid Talon",
        cost = "{R}",
        type = "Creature — Lizard Mercenary",
        pt = "1/2",
        text = """
            Whenever you attack with one or more Lizards, this creature deals 1 damage to target opponent.
            {1}{R}: Put a +1/+1 counter on this creature. Activate only if an opponent lost life this turn and only once each turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.And(listOf(sub("Lizard"), you))), oneOrMore = true),
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Target("opponent")), one(), source = Objects.Self))),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(mana("{1}{R}"))),
            instructions = steps(counters(Objects.Self)),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1, condition = opponentLostLife),
        ),
    )

    @Test
    fun smolderingThroatslitter() = card(
        name = "Smoldering Throatslitter",
        cost = "{2}{B/R}",
        type = "Creature — Lizard Assassin",
        pt = "3/2",
        text = """
            This creature enters with a +1/+1 counter on it if an opponent lost life this turn.
            {1}{B/R}: This creature gains menace until end of turn.
        """,
    ) parsesTo abilities(
        entersWithCounterIfOpponentLostLife,
        Ability.Activated(cost = Cost(listOf(mana("{1}{B/R}"))), instructions = steps(gains(Objects.Self, Keyword.Menace)), rules = any),
    )

    // CR 505.1a: the second main phase is the postcombat one
    @Test
    fun kilnglassTutor() = card(
        name = "Kilnglass Tutor",
        cost = "{B}{R}",
        type = "Creature — Lizard Warlock",
        pt = "2/1",
        text = "At the beginning of your second main phase, if an opponent lost life this turn, exile the top two cards of your library. Choose one of them. Until end of turn, you may play that card.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You)), nthThisTurn = 1),
            instructions = steps(
                Step.Do(Effect.Move(Objects.LibraryCards(Players.You, one(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                Step.Choose(Choice.Objects(ObjectFilter.Any, min = one(), max = one(), from = Objects.Bound("exiled")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("chosen")), Duration.EndOfTurn)),
            ),
            interveningIf = opponentLostLife,
        ),
    )

    // CR 614.1c: other creatures enter with more counters, one for each opponent who lost life this turn
    @Test
    fun vexxScorchedScale() = card(
        name = "Vexx, Scorched Scale",
        cost = "{B}{R}",
        type = "Legendary Creature — Lizard Mercenary",
        pt = "3/2",
        text = """
            Ward—Pay 2 life.
            Other creatures you control enter with an additional +1/+1 counter on them for each opponent who lost life this turn.
            Whenever you cast a Lizard spell, Vexx deals 1 damage to target opponent.
        """,
    ) parsesTo abilities(
        Keyword.Ward(Cost(listOf(CostPart.PayLife(one(2))))),
        Ability.Static(
            listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                    PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any)))))))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(sub("Lizard"), PlayerFilter.You)),
            instructions = Instructions(
                targets = listOf(targetOpponent),
                steps = listOf(Step.Do(Effect.DealDamage(arcanum.api.Recipients(players = Players.Target("opponent")), one(), source = Objects.Self))),
            ),
        ),
    )

    @Test
    fun moonlitAssembly() = card(
        name = "Moonlit Assembly",
        cost = "{W}{B}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, if you gained life this turn, each opponent loses 1 life.
            At the beginning of your end step, if you gained and lost life this turn, create a 1/1 black Bat creature token with flying.
            {1}{B}, Pay 2 life: Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(trigger = yourEndStep, instructions = steps(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), one()))),
            interveningIf = gainedLife),
        Ability.Triggered(
            trigger = yourEndStep,
            instructions = steps(Step.Do(Effect.CreateToken(creatureToken("Bat", listOf(Color.Black), 1, 1, listOf(Keyword.Flying)), count = one(),
                controller = Players.You))),
            interveningIf = Condition.And(listOf(gainedLife, lostLife)),
        ),
        Ability.Activated(cost = Cost(listOf(mana("{1}{B}"), CostPart.PayLife(one(2)))), instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
            rules = any),
    )

    @Test
    fun duskrisePriest() = card(
        name = "Duskrise Priest",
        cost = "{1}{W/B}{W/B}",
        type = "Creature — Bat Cleric",
        pt = "2/3",
        text = """
            Flying
            Whenever this creature attacks, you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), instructions = steps(Step.Do(Effect.GainLife(Players.You, one())))),
    )

    @Test
    fun skywatchElder() = card(
        name = "Skywatch Elder",
        cost = "{3}{W}{B}",
        type = "Creature — Bat Warlock",
        pt = "3/5",
        text = """
            Flying, vigilance
            At the beginning of your end step, if you gained or lost life this turn, target opponent loses 3 life unless they sacrifice a nonland permanent of their choice or discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(
            trigger = yourEndStep,
            instructions = Instructions(targets = listOf(targetOpponent), steps = losesUnlessSacrificesOrDiscards(Players.Target("opponent"), 3)),
            interveningIf = Condition.Or(listOf(gainedLife, lostLife)),
        ),
    )

    // CR 603.12: a reflexive trigger after paying mana and life; CR 122.1h: finality counter
    @Test
    fun noctaraStarcaller() = card(
        name = "Noctara, Starcaller",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Bat Cleric",
        pt = "3/3",
        text = """
            Flying, vigilance
            Whenever a Bat you control attacks, you gain 1 life.
            Whenever Noctara enters or attacks, you may pay {W}{B} and 2 life. When you do, return target nonland permanent card with mana value 3 or less from your graveyard to the battlefield with a finality counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Ability.Triggered(trigger = Trigger.On(EventPattern.Attack(ObjectFilter.And(listOf(bat, you)))), instructions = steps(Step.Do(Effect.GainLife(Players.You, one())))),
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.Attack(ObjectFilter.Is(Objects.Self)))),
            instructions = steps(
                Step.Pay(Cost(listOf(mana("{W}{B}"), CostPart.PayLife(one(2)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                    targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle,
                        CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
                        ObjectFilter.ManaValue(Comparison.AtMost, one(3))) + yourGraveyard))),
                    steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield,
                        counters = listOf(CountersToPut(CounterKind.Finality, one())))))),
                ))))),
            ),
        ),
    )
}
