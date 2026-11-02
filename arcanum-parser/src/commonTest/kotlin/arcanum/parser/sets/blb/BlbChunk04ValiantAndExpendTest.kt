package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
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
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 04: valiant and expend. Valiant is an ability word (CR 207.2c) on "Whenever this creature becomes
 * the target of a spell or ability you control for the first time each turn" (the first such event each turn, CR
 * 603.2); "it" is this creature, the event's object. "Whenever you expend N" triggers as the mana a player spent on
 * spells this turn reaches N (CR 700.14). With them: "Mice you control", look-and-reveal choices that go to the
 * battlefield or the hand, "until the end of your next turn" permissions, mana "{R} or {G} for each".
 */
class BlbChunk04ValiantAndExpendTest {
    private val valiant = Trigger.On(
        listOf(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), by = you)),
        nthThisTurn = 1,
    )
    private fun expend(n: Int) = Trigger.On(EventPattern.Expend(n, PlayerFilter.You))
    private val it = Objects.EventObject(EventRole.Object)
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val mouse = sub("Mouse")
    private val raccoon = sub("Raccoon")

    private fun valiant(vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) = Ability.Triggered(
        trigger = valiant,
        instructions = Instructions(targets = targets, steps = steps.toList()),
        abilityWord = "Valiant",
    )

    private fun expend(n: Int, vararg steps: Step, targets: List<arcanum.api.TargetSlot> = emptyList()) = Ability.Triggered(
        trigger = expend(n),
        instructions = Instructions(targets = targets, steps = steps.toList()),
    )

    private val exileTopAndMayPlay = listOf(
        Step.Do(Effect.Move(Objects.LibraryCards(Players.You, one(), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
        Step.Do(Effect.ApplyMechanics(
            listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
            Affected.Objects(Objects.Bound("exiled")),
            Duration.EndOfTurn,
        )),
    )

    // ---- Valiant

    // CR 603.2: "for the first time each turn" is the first such event
    @Test
    fun petalstepDuelist() = card(
        name = "Petalstep Duelist",
        cost = "{W}",
        type = "Creature — Mouse Soldier",
        pt = "1/2",
        text = """
            Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, Mice you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Offspring(manaCost("{2}")),
        valiant(pump(Objects.Each(ObjectFilter.And(listOf(mouse, you))), 1, 0)),
    )

    @Test
    fun whiskerSnarer() = card(
        name = "Whisker Snarer",
        cost = "{2}{W}",
        type = "Creature — Mouse Soldier",
        pt = "3/2",
        text = """
            Flash
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, tap target creature an opponent controls.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        valiant(Step.Do(Effect.Tap(Objects.Target("creature"))), targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents))))),
    )

    @Test
    fun thistleSentry() = card(
        name = "Thistle Sentry",
        cost = "{1}{W}",
        type = "Creature — Mouse Soldier",
        pt = "3/1",
        text = """
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, it gets +0/+2 until end of turn.
            {1}, Sacrifice this creature: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        valiant(pump(it, 0, 2)),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{1}")!!), CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                targets = listOf(slot("permanent", ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))))),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
            ),
            rules = any,
        ),
    )

    // CR 118.12: "You may put it onto the battlefield if it's your turn. If you don't put it onto the battlefield, put it
    // into your hand." is a choice to put it there, only on your turn; otherwise it goes to the hand
    @Test
    fun whiskerdalePathfinder() = card(
        name = "Whiskerdale Pathfinder",
        cost = "{3}{W}",
        type = "Creature — Mouse Bard",
        pt = "3/4",
        text = """
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, look at the top five cards of your library. You may reveal a creature card with mana value 3 or less from among them. You may put it onto the battlefield if it's your turn. If you don't put it onto the battlefield, put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        valiant(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, one(5), fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(
                Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(Comparison.AtMost, one(3)))), min = one(0), max = one(),
                    from = Objects.Bound("seen")),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Reveal(Objects.Bound("chosen"))),
            Step.If(Condition.YourTurn, then = listOf(Step.Pay(
                steps = listOf(Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield)))),
                payer = Players.You,
                bind = "put",
            ))),
            Step.If(Condition.Not(Condition.Did("put")), then = listOf(Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))),
            Step.Do(Effect.Move(
                Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true),
            )),
        ),
    )

    // CR 702.108a: prowess; the exiled card may be played this turn (CR 601.2a, 305.1)
    @Test
    fun cinderheartContender() = card(
        name = "Cinderheart Contender",
        cost = "{1}{R}",
        type = "Creature — Mouse Warrior",
        pt = "2/2",
        text = """
            Haste
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, exile the top card of your library. Until end of turn, you may play that card.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Keyword.Prowess,
        valiant(*exileTopAndMayPlay.toTypedArray()),
    )

    // CR 608.2h: the dead creature's power as it last existed
    @Test
    fun hearthflameChampion() = card(
        name = "Hearthflame Champion",
        cost = "{R}",
        type = "Creature — Mouse Soldier",
        pt = "1/1",
        text = """
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, put a +1/+1 counter on it.
            When this creature dies, it deals damage equal to its power to each opponent.
        """,
    ) parsesTo abilities(
        valiant(counters(it)),
        Ability.Triggered(
            trigger = thisDies,
            instructions = steps(Step.Do(Effect.DealDamage(
                Recipients(players = Players.Each(PlayerFilter.Opponent)),
                Value.Of(NumericProperty.Power, Objects.Self, Combine.Single),
                source = Objects.Self,
            ))),
        ),
    )

    @Test
    fun inkwhiskerClerk() = card(
        name = "Inkwhisker Clerk",
        cost = "{1}{R}",
        type = "Creature — Mouse Citizen",
        pt = "2/2",
        text = """
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, you may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        valiant(
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one())))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, one())))),
        ),
    )

    @Test
    fun acornbladeTutor() = card(
        name = "Acornblade Tutor",
        cost = "{1}{R}{W}",
        type = "Creature — Mouse Soldier",
        pt = "3/2",
        text = """
            Vigilance, haste
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Haste,
        valiant(counters(it)),
    )

    @Test
    fun grizzledMousewarden() = card(
        name = "Grizzled Mousewarden",
        cost = "{3}{R/W}",
        type = "Creature — Mouse Soldier",
        pt = "3/4",
        text = """
            Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, it gets +1/+0 and gains first strike until end of turn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
    ) parsesTo abilities(
        valiant(
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(one(1), one(0)), Mechanic.GainAbilities(listOf(Keyword.FirstStrike))),
                Affected.Objects(it),
                Duration.EndOfTurn,
            )),
            Step.Do(Effect.Scry(Players.You, one())),
        ),
    )

    // ---- Expend (CR 700.14)

    @Test
    fun backroadHaggler() = card(
        name = "Backroad Haggler",
        cost = "{2}{R}",
        type = "Creature — Raccoon Rogue",
        pt = "3/3",
        text = """
            Menace
            Whenever you expend 4, you may discard your hand. If you do, draw two cards. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        expend(
            4,
            Step.Pay(
                Cost(listOf(CostPart.Discard(CostObjects.These(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.You)))))))),
                payer = Players.You,
                bind = "paid",
            ),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, one(2))))),
        ),
    )

    // CR 603.2: one ability with two trigger events; "it" is this enchantment
    @Test
    fun packratsSurplus() = card(
        name = "Packrat's Surplus",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters and whenever you expend 4, put a stash counter on it. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
            {1}{R}, Sacrifice this enchantment: Discard your hand, then draw cards equal to the number of stash counters on this enchantment.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), EventPattern.Expend(4, PlayerFilter.You))),
            instructions = steps(counters(Objects.Self, kind = CounterKind.Named("stash"))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{1}{R}")!!), CostPart.Sacrifice(CostObjects.Self))),
            instructions = steps(
                Step.Do(Effect.Discard(
                    Players.You,
                    objects = Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                    count = one(),
                    filter = ObjectFilter.Any,
                )),
                Step.Do(Effect.Draw(Players.You, Value.Of(NumericProperty.Counters(CounterKind.Named("stash")), Objects.Self, Combine.Single))),
            ),
            rules = any,
        ),
    )

    @Test
    fun rowdyPair() = card(
        name = "Rowdy Pair",
        cost = "{2}{R}",
        type = "Creature — Mouse Raccoon",
        pt = "3/2",
        text = """
            Trample
            Whenever you expend 4, target creature you control gets +1/+1 and gains trample until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        expend(
            4,
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(one(1), one(1)), Mechanic.GainAbilities(listOf(Keyword.Trample))),
                Affected.Objects(Objects.Target("creature")),
                Duration.EndOfTurn,
            )),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    @Test
    fun kettleHurler() = card(
        name = "Kettle Hurler",
        cost = "{3}{R}",
        type = "Creature — Raccoon Warrior",
        pt = "3/4",
        text = """
            Menace
            Whenever you expend 4, this creature deals 2 damage to each opponent. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        expend(4, Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), one(2), source = Objects.Self))),
    )

    @Test
    fun pastrythiefPair() = card(
        name = "Pastrythief Pair",
        cost = "{1}{G}",
        type = "Creature — Squirrel Raccoon",
        pt = "2/2",
        text = """
            When this creature enters, create a Food token.
            Whenever you expend 4, this creature gets +1/+1 until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Food"), count = one(), controller = Players.You))),
        expend(4, pump(Objects.Self, 1, 1)),
    )

    @Test
    fun oakFistBrawler() = card(
        name = "Oak-Fist Brawler",
        cost = "{1}{G}",
        type = "Creature — Raccoon Berserker",
        pt = "3/2",
        text = """
            Whenever you expend 4, this creature gains indestructible until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        expend(4, gains(Objects.Self, Keyword.Indestructible)),
    )

    // CR 611.2c: the Raccoons are fixed as the effect begins
    @Test
    fun thornhedgeOldTimer() = card(
        name = "Thornhedge Old-Timer",
        cost = "{1}{G}{G}",
        type = "Creature — Raccoon Warrior",
        pt = "3/4",
        text = """
            Whenever you expend 4, Raccoons you control get +1/+1 and gain vigilance until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        expend(4, Step.Do(Effect.ApplyMechanics(
            listOf(Mechanic.ModifyPowerToughness(one(1), one(1)), Mechanic.GainAbilities(listOf(Keyword.Vigilance))),
            Affected.Objects(Objects.Each(ObjectFilter.And(listOf(raccoon, you)))),
            Duration.EndOfTurn,
        ))),
    )

    @Test
    fun scrapswordThug() = card(
        name = "Scrapsword Thug",
        cost = "{3}{R/G}{R/G}",
        type = "Creature — Raccoon Berserker",
        pt = "4/5",
        text = """
            Trample
            Whenever you expend 4, this creature gets +2/+1 until end of turn. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        expend(4, pump(Objects.Self, 2, 1)),
    )

    // CR 505.1a: the first main phase is the precombat main phase; each mana is {R} or {G} as chosen (CR 106.1)
    @Test
    fun rizzoRubbishStrategist() = card(
        name = "Rizzo, Rubbish Strategist",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Raccoon Warrior",
        pt = "2/4",
        text = """
            At the beginning of your first main phase, add {R} or {G} for each Raccoon you control.
            Whenever you expend 4, you gain 3 life. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
            Whenever you expend 8, exile the top two cards of your library. Until the end of your next turn, you may play those cards.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(
                amount = Value.Count(Objects.Each(ObjectFilter.And(listOf(raccoon, you)))),
                colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)),
                combination = true,
            )), Players.You))),
        ),
        expend(4, Step.Do(Effect.GainLife(Players.You, one(3)))),
        expend(
            8,
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, one(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")),
                Duration.UntilEndOfYourNextTurn,
            )),
        ),
    )

    @Test
    fun roamstoryElder() = card(
        name = "Roamstory Elder",
        cost = "{R}{G}",
        type = "Creature — Raccoon Bard",
        pt = "2/2",
        text = """
            Whenever you expend 4, put a +1/+1 counter on this creature. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
        expend(4, counters(Objects.Self)),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(
                amount = one(),
                colors = listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)),
            )), Players.You))),
            rules = any,
        ),
    )
}
