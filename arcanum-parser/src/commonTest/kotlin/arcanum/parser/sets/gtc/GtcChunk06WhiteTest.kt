package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 06: white. Pump and exile spells, tokens, a chosen keyword for your creatures each combat, a land
 * granted a tap ability, regeneration, a planeswalker that becomes a creature until end of turn (CR 205.1b: still a
 * planeswalker), blocking any number of creatures and growing for each one blocked, a target for each opponent (CR
 * 601.2c), a dying creature's power (as it last existed, CR 603.10a), prevention for the turn and blocked creatures.
 */
class GtcChunk06WhiteTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val enchanted = Objects.AttachedTo(Objects.Self)

    @Test
    fun skywardManeuver() = card(
        name = "Skyward Maneuver",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +1/+1 and gains flying and first strike until end of turn.",
    ) parsesTo abilities(
        spell(pump(Objects.Target("creature"), 1, 1, Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.FirstStrike))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun seraphicEdict() = card(
        name = "Seraphic Edict",
        cost = "{4}{W}",
        type = "Sorcery",
        text = "Exile target creature or enchantment.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Exile))),
            targets = listOf(slot("permanent", ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Enchantment))))),
        ),
    )

    // CR 507.1: each player's beginning of combat; the chosen keyword is one of three options
    @Test
    fun combatSeraph() = card(
        name = "Combat Seraph",
        cost = "{4}{W}{W}",
        type = "Creature — Angel",
        pt = "4/4",
        text = """
            Flying
            At the beginning of each combat, choose first strike, vigilance, or lifelink. Creatures you control gain that ability until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any)),
            steps(
                Step.Choose(Choice.Option(listOf("first strike", "vigilance", "lifelink")), bind = "ability", chooser = Players.You),
                Step.If(Condition.Chose("ability", "first strike"), then = listOf(gains(Objects.Each(creatureYouControl), Keyword.FirstStrike))),
                Step.If(Condition.Chose("ability", "vigilance"), then = listOf(gains(Objects.Each(creatureYouControl), Keyword.Vigilance))),
                Step.If(Condition.Chose("ability", "lifelink"), then = listOf(gains(Objects.Each(creatureYouControl), Keyword.Lifelink))),
            ),
        ),
    )

    @Test
    fun plazaDenizen() = card(
        name = "Plaza Denizen",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = "Whenever another white creature you control enters, tap target creature an opponent controls.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.OfColor(Color.White), creature, you)),
                to = Zone.Battlefield)),
            Instructions(targets = listOf(slot("creature", creatureOpponentControls)), steps = listOf(Step.Do(Effect.Tap(Objects.Target("creature"))))),
        ),
    )

    // CR 113.10: the enchanted land has the ability; its controller activates it
    @Test
    fun debtcallersPulpit() = card(
        name = "Debtcaller's Pulpit",
        cost = "{4}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}: Tap target creature."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                cost = costOf(CostPart.TapSelf),
                instructions = Instructions(targets = listOf(slot("creature", creature)), steps = listOf(Step.Do(Effect.Tap(Objects.Target("creature"))))),
                rules = anyTime,
            )))),
            affects = Affected.Objects(enchanted),
        ),
    )

    // CR 701.19a: a regeneration shield
    @Test
    fun obedientThrull() = card(
        name = "Obedient Thrull",
        cost = "{W}",
        type = "Creature — Thrull",
        pt = "1/1",
        text = "{B}: Regenerate this creature.",
    ) parsesTo abilities(
        Ability.Activated(mana("{B}"), steps(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self)))), rules = anyTime),
    )

    // CR 606: loyalty abilities; CR 205.1b: it becomes a creature in addition to its types; CR 608.2h: its power and
    // toughness are the loyalty counters as the ability resolves
    @Test
    fun gideonParagonOfTheCourts() {
        val loyalty = Value.Of(NumericProperty.Counters(CounterKind.Loyalty), Objects.Self, Combine.Single)
        card(
            name = "Gideon, Paragon of the Courts",
            cost = "{2}{W}{W}",
            type = "Legendary Planeswalker — Gideon",
            loyalty = "4",
            text = """
                [+1]: Put a loyalty counter on Gideon for each creature target opponent controls.
                [0]: Until end of turn, Gideon becomes a Human Soldier creature with power and toughness each equal to the number of loyalty counters on him and gains indestructible. He's still a planeswalker. Prevent all damage that would be dealt to him this turn.
                [−15]: Exile all other permanents.
            """,
        ) parsesTo abilities(
            Ability.Activated(
                cost = costOf(CostPart.Loyalty(n(1))),
                instructions = Instructions(
                    targets = listOf(targetOpponent),
                    steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Loyalty,
                        Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))))))))),
                ),
                rules = anyTime,
            ),
            Ability.Activated(
                cost = costOf(CostPart.Loyalty(n(0))),
                instructions = steps(
                    untilEndOfTurn(Objects.Self,
                        Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Human")), SubtypeRef.Literal(Subtype("Soldier")))),  // CR 205.1b: "still a [type]" keeps all prior subtypes
                        Mechanic.SetPowerToughness(loyalty, loyalty),
                        Mechanic.GainAbilities(listOf(Keyword.Indestructible)),
                    ),
                    Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))))),
                        affects = null, duration = Duration.EndOfTurn)),
                ),
                rules = anyTime,
            ),
            Ability.Activated(
                cost = costOf(CostPart.Loyalty(n(-15))),
                instructions = steps(Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                    ObjectFilter.Category(ObjectCategory.Permanent)))), Destination(Zone.Exile)))),
                rules = anyTime,
            ),
        )
    }

    // CR 509.1a: it may block any number of attackers; the bonus counts the attackers it blocks
    @Test
    fun guardianOfTheOpenGate() {
        val blocked = Value.Count(Objects.EventObject(EventRole.OtherCombatant))
        card(
            name = "Guardian of the Open Gate",
            cost = "{4}{W}",
            type = "Creature — Angel",
            pt = "3/3",
            text = """
                Flying
                This creature can block any number of creatures.
                Whenever this creature blocks, it gets +1/+1 until end of turn for each creature it's blocking.
            """,
        ) parsesTo abilities(
            Keyword.Flying,
            Ability.Static(listOf(Mechanic.CanBlockAnyNumber), affects = Affected.Objects(Objects.Self)),
            Ability.Triggered(
                Trigger.On(EventPattern.Block(ObjectFilter.Is(Objects.Self), attacker = ObjectFilter.Any)),
                steps(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(blocked, blocked))),
            ),
        )
    }

    // CR 702.16: protection from multicolored
    @Test
    fun unityWard() = card(
        name = "Unity Ward",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has protection from multicolored.
        """,
    ) parsesTo abilities(
        enchantCreature,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Protection(ObjectFilter.Multicolored)))), affects = Affected.Objects(enchanted)),
    )

    @Test
    fun gatekeepersResolve() = card(
        name = "Gatekeeper's Resolve",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "Creatures you control get +0/+1 for each Gate you control and have vigilance.",
    ) parsesTo abilities(
        Ability.Static(
            listOf(
                Mechanic.ModifyPowerToughness(n(0), Value.Count(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Gate")), you))))),
                Mechanic.GainAbilities(listOf(Keyword.Vigilance)),
            ),
            affects = Affected.Objects(Objects.Each(creatureYouControl)),
        ),
    )

    @Test
    fun sanctifiedMantle() = card(
        name = "Sanctified Mantle",
        cost = "{2}{W}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has protection from creatures.
        """,
    ) parsesTo abilities(
        enchantCreature,
        Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(n(2), n(2)), Mechanic.GainAbilities(listOf(Keyword.Protection(creature)))),
            affects = Affected.Objects(enchanted),
        ),
    )

    @Test
    fun sentryWatch() = card(
        name = "Sentry Watch",
        cost = "{4}{W}",
        type = "Sorcery",
        text = "Create two 2/2 white Knight creature tokens with vigilance.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.CreateToken(creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance)), n(2), Players.You))),
    )

    // CR 601.2c: one target for each opponent; CR 608.2h: the exiled creature's power and controller as it last existed
    @Test
    fun radiantPrimordial() = card(
        name = "Radiant Primordial",
        cost = "{5}{W}{W}",
        type = "Creature — Avatar",
        pt = "4/7",
        text = """
            Vigilance
            When this creature enters, for each opponent, exile up to one target creature that player controls and that player gains life equal to its power.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        whenThisEnters(
            Step.ForEach(arcanum.api.Each.Objects(Objects.Target("creature")), "creature", listOf(
                Step.Do(Effect.Move(Objects.Bound("creature"), Destination(Zone.Exile))),
                Step.Do(Effect.GainLife(Players.ControllerOf(Objects.Bound("creature")), power(Objects.Bound("creature")))),
            )),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))))),
                min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    // CR 603.10a: "its power" is the enchanted creature's as it last existed on the battlefield
    @Test
    fun homicideInquest() = card(
        name = "Homicide Inquest",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When enchanted creature dies, create X 1/1 white Soldier creature tokens, where X is its power.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)),
            steps(Step.Do(Effect.CreateToken(creatureToken("Soldier", listOf(Color.White), 1, 1), power(Objects.EventObject(EventRole.Before)), Players.You))),
        ),
    )

    @Test
    fun vanguardCharge() = card(
        name = "Vanguard Charge",
        cost = "{1}{W}{W}",
        type = "Sorcery",
        text = "Creatures you control get +2/+2 until end of turn.",
    ) parsesTo abilities(spell(pump(Objects.Each(creatureYouControl), 2, 2)))

    // CR 615: all damage to it this turn is prevented
    @Test
    fun shieldingPassage() = card(
        name = "Shielding Passage",
        cost = "{W}",
        type = "Instant",
        text = "Prevent all damage that would be dealt to target creature this turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Target("creature")))))),
                affects = null, duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 509.1h: a blocked creature
    @Test
    fun smiteTheBlocked() = card(
        name = "Smite the Blocked",
        cost = "{W}",
        type = "Instant",
        text = "Destroy target blocked creature.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Blocked), creature)))),
        ),
    )

    @Test
    fun urbisWarden() = card(
        name = "Urbis Warden",
        cost = "{4}{W}{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "When this creature enters, create a 4/4 white Angel creature token with flying.",
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(creatureToken("Angel", listOf(Color.White), 4, 4, listOf(Keyword.Flying)), n(1), Players.You))),
    )

    @Test
    fun zarichiPanther() = card(
        name = "Zarichi Panther",
        cost = "{3}{W}",
        type = "Creature — Cat",
        pt = "2/3",
        text = "{1}{W}, {T}: You gain 2 life.",
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{1}{W}"), CostPart.TapSelf), steps(Step.Do(Effect.GainLife(Players.You, n(2)))), rules = anyTime),
    )
}
