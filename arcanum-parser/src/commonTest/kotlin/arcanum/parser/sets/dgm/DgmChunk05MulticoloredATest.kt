package arcanum.parser.sets.dgm

import arcanum.api.DamageOccurrence
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
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.anyTime
import arcanum.parser.sets.gtc.attacksSelf
import arcanum.parser.sets.gtc.costOf
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureToken
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.entersSelf
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.mana
import arcanum.parser.sets.gtc.manaPart
import arcanum.parser.sets.gtc.n
import arcanum.parser.sets.gtc.p1p1
import arcanum.parser.sets.gtc.pump
import arcanum.parser.sets.gtc.slot
import arcanum.parser.sets.gtc.steps
import arcanum.parser.sets.gtc.untilEndOfTurn
import arcanum.parser.sets.gtc.you
import arcanum.parser.sets.gtc.yourUpkeep
import kotlin.test.Test

/**
 * Dragon's Maze chunk 05: multicoloured cards, Arrival of the Wurm to Haunter of Nightmist. Discarding then dealing the
 * discarded card's mana value (CR 608.2h), damage dealt by spells, a static ability with a two-part life condition,
 * combat requirements and restrictions for one combat (CR 508.1d, 509.1c), a chosen card name (CR 201.4), a random
 * graveyard card, life lost then gained, two different shrinks, a turn-long change of a creature into a Dragon and its
 * overloaded version (CR 702.96b), switching power and toughness (CR 613.4d), mana value X or less, and a target
 * chosen at random by the host.
 */
class DgmChunk05MulticoloredATest {
    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
    private val nonland = ObjectFilter.Not(land)

    @Test
    fun arrivalOfTheWurm() = card(
        name = "Arrival of the Wurm",
        cost = "{1}{G}{G}{W}",
        type = "Instant",
        text = "Create a 5/5 green Wurm creature token with trample.",
    ) parsesTo abilities(Ability.Spell(steps(Step.Do(Effect.CreateToken(creatureToken("Wurm", listOf(Color.Green), 5, 5, listOf(Keyword.Trample)), n(1), Players.You)))))

    // CR 602.5b: only once each turn
    @Test
    fun beetlewingMage() = card(
        name = "Beetlewing Mage",
        cost = "{1}{G}{U}",
        type = "Creature — Human Insect Wizard",
        pt = "2/2",
        text = "{G}{U}: This creature gets +2/+2 and gains flying until end of turn. Activate only once each turn.",
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{G}{U}")), Instructions(steps = listOf(pump(Objects.Self, 2, 2, Mechanic.GainAbilities(listOf(Keyword.Flying))))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    // CR 608.2h: the discarded card's mana value
    @Test
    fun burstOfGenius() = card(
        name = "Burst of Genius",
        cost = "{4}{U}{R}",
        type = "Sorcery",
        text = "Choose any target. Draw three cards, then discard a card. Burst of Genius deals damage equal to the discarded card's mana value to that permanent or player.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(anyTarget),
            steps = listOf(
                Step.Do(Effect.Draw(Players.You, n(3))),
                Step.Do(Effect.Discard(Players.You, n(1), filter = ObjectFilter.Any, postBind = "discarded")),
                Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Of(NumericProperty.ManaValue, Objects.Bound("discarded"), Combine.Single), source = Objects.Self)),
            ),
        )),
    )

    // CR 120.1: a spell deals damage
    @Test
    fun blazeVanguard() = card(
        name = "Blaze Vanguard",
        cost = "{3}{R}{W}",
        type = "Creature — Minotaur Soldier",
        pt = "5/3",
        text = "Whenever an instant or sorcery spell you control deals damage, create two 1/1 red and white Soldier creature tokens with haste.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.And(listOf(instantOrSorcery, ObjectFilter.Category(ObjectCategory.Spell), you)), RecipientFilter(), per = DamageOccurrence.Source)),
            steps(Step.Do(Effect.CreateToken(creatureToken("Soldier", listOf(Color.Red, Color.White), 1, 1, listOf(Keyword.Haste)), n(2), Players.You))),
        ),
    )

    // CR 702.16: protection from two colours; CR 611.3a: the condition is checked continuously
    @Test
    fun bloodCountOfVizkopa() = card(
        name = "Blood Count of Vizkopa",
        cost = "{3}{W}{B}",
        type = "Creature — Vampire",
        pt = "4/4",
        text = """
            Lifelink, protection from white and from black
            As long as you have 30 or more life and an opponent has 10 or less life, this creature gets +6/+6 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Keyword.Protection(ObjectFilter.OfColor(Color.White)),
        Keyword.Protection(ObjectFilter.OfColor(Color.Black)),
        Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(n(6), n(6)), Mechanic.GainAbilities(listOf(Keyword.Flying))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.And(listOf(
                Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, n(30)),
                Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.AtMost, n(10)))), atLeast = 1),
            )),
        ),
    )

    // CR 508.1d, 509.1c: for this combat only
    @Test
    fun borosWarshaper() = card(
        name = "Boros Warshaper",
        cost = "{5}{R}{W}",
        type = "Creature — Minotaur Soldier",
        pt = "5/5",
        text = "At the beginning of each combat, up to one target creature attacks or blocks this combat if able and up to one target creature can't attack or block this combat.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any)), Instructions(
            targets = listOf(slot("creature", creature, min = 0), slot("second creature", creature, min = 0)),
            steps = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MustAttack(), Mechanic.MustBlock(ObjectFilter.Any)), Affected.Objects(Objects.Target("creature")), Duration.EndOfCombat)),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(Objects.Target("second creature")), Duration.EndOfCombat)),
            ),
        )),
    )

    @Test
    fun bredForTheChase() = card(
        name = "Bred for the Chase",
        cost = "{1}{G}{U}",
        type = "Enchantment",
        text = "Whenever a creature you control with a +1/+1 counter on it deals combat damage to a player, you may draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.And(listOf(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            steps(Step.May(listOf(Step.Do(Effect.Draw(Players.You, n(1)))), Players.You)),
        ),
    )

    @Test
    fun bronzeplumeMoa() = card(
        name = "Bronzeplume Moa",
        cost = "{2}{G}{W}",
        type = "Creature — Bird",
        pt = "2/2",
        text = "Whenever another creature you control enters, this creature gets +3/+3 until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)), to = Zone.Battlefield)),
            Instructions(steps = listOf(pump(Objects.Self, 3, 3))),
        ),
    )

    // "that creature" is the blocking creature; CR 701.19a: regenerate
    @Test
    fun carnageDuelist() = card(
        name = "Carnage Duelist",
        cost = "{2}{B}{R}",
        type = "Creature — Skeleton Warrior",
        pt = "4/2",
        text = """
            Whenever a creature blocks, that creature's controller loses 1 life.
            {1}{B}{R}: Regenerate this creature.
        """,
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Block(creature, ObjectFilter.Any)),
            steps(Step.Do(Effect.LoseLife(Players.ControllerOf(Objects.EventObject(EventRole.Object)), n(1))))),
        Ability.Activated(costOf(manaPart("{1}{B}{R}")), steps(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Self)))), rules = anyTime),
    )

    // CR 201.4, 614.12: the name is chosen as it enters and remembered
    @Test
    fun councilOfTheSupreme() = card(
        name = "Council of the Supreme",
        cost = "{2}{W}{U}",
        type = "Creature — Human Advisor",
        pt = "2/4",
        text = """
            As this creature enters, choose a noncreature, nonland card name.
            Your opponents can't cast spells with the chosen name.
            Spells with the chosen name you cast cost {2} less to cast.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CardName(ObjectFilter.And(listOf(ObjectFilter.Not(creature), nonland))), bind = "name", chooser = Players.You, remember = true),
        ))))), affects = Affected.Objects(Objects.Self)),
        Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.RememberedName("name"))), affects = Affected.Players(Players.Each(PlayerFilter.Opponent))),
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.RememberedName("name"), you)),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))), affects = Affected.Objects(Objects.Self)),
    )

    // A card chosen at random, then put by its type
    @Test
    fun deadbridgeHymn() = card(
        name = "Deadbridge Hymn",
        cost = "{4}{B}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, mill ten cards.
            At the beginning of your upkeep, choose a card at random in your graveyard. If it's a creature card, put it onto the battlefield. Otherwise, put it into your hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, steps(Step.Do(Effect.Mill(Players.You, n(10))))),
        Ability.Triggered(yourUpkeep, steps(
            Step.Bind(Objects.Random(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), n(1)), "chosen"),
            Step.If(Condition.ObjectMatches(Objects.Bound("chosen"), creature),
                then = listOf(Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))))),
        )),
    )

    // CR 107.3: X announced; the life actually lost
    @Test
    fun debtOfTheDeathless() = card(
        name = "Debt of the Deathless",
        cost = "{X}{W}{W}{B}{B}",
        type = "Sorcery",
        text = "Each opponent loses two times X life. You gain life equal to the life lost this way.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Times(Value.Announced("X"), 2), bind = "lost")),
                Step.Do(Effect.GainLife(Players.You, Value.Bound("lost"))),
            ),
            announced = listOf("X"),
        )),
    )

    // "another target creature you control": other than this creature
    @Test
    fun marshalOfAcquittals() = card(
        name = "Marshal of Acquittals",
        cost = "{W}{U}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            When this creature enters, you may return another target creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(entersSelf, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)))),
            steps = listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand)))), Players.You)),
        )),
    )

    // CR 205.1a, 613.1: layers 4–7b; CR 702.96b: the overloaded text affects each creature you control
    @Test
    fun drakeshift() {
        fun dragon(objects: Objects) = untilEndOfTurn(objects,
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dragon"))), replaceSubtypes = true),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Red))),
            Mechanic.LoseAbilities(),
            Mechanic.GainAbilities(listOf(Keyword.Flying)),
            Mechanic.SetPowerToughness(n(4), n(4)))
        card(
            name = "Drakeshift",
            cost = "{1}{U}{R}",
            type = "Instant",
            text = """
                Until end of turn, target creature you control becomes a blue and red Dragon with base power and toughness 4/4, loses all abilities, and gains flying.
                Overload {3}{U}{U}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
            """,
        ) parsesTo abilities(
            Ability.Spell(Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(dragon(Objects.Target("creature"))))),
            Keyword.Overload(mana("{3}{U}{U}{R}{R}"), steps(dragon(Objects.Each(creatureYouControl)))),
        )
    }

    // -1/-1 for each land card, counted as it resolves after the mill
    @Test
    fun drownedInFilth() {
        val lands = Value.Count(Objects.Each(ObjectFilter.And(listOf(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))))
        card(
            name = "Drowned in Filth",
            cost = "{B}{G}",
            type = "Sorcery",
            text = "Choose target creature. Mill four cards, then that creature gets -1/-1 until end of turn for each land card in your graveyard.",
        ) parsesTo abilities(
            Ability.Spell(Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(
                    Step.Do(Effect.Mill(Players.You, n(4))),
                    untilEndOfTurn(Objects.Target("creature"), Mechanic.ModifyPowerToughness(Value.Times(lands, -1), Value.Times(lands, -1))),
                ),
            )),
        )
    }

    // CR 615: damage to creature tokens you control is prevented
    @Test
    fun emmaraTandril() = card(
        name = "Emmara Tandril",
        cost = "{5}{G}{W}",
        type = "Legendary Creature — Elf Shaman",
        pt = "5/7",
        text = "Prevent all damage that would be dealt to creature tokens you control.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(objects = ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Token), you)))))), affects = Affected.Objects(Objects.Self)),
    )

    // CR 608.2h: its power as the ability resolves
    @Test
    fun feralShaman() = card(
        name = "Feral Shaman",
        cost = "{1}{R}{G}",
        type = "Creature — Goblin Shaman",
        pt = "2/1",
        text = "{3}: This creature gets +X/+0 until end of turn, where X is its power.",
    ) parsesTo abilities(
        Ability.Activated(costOf(manaPart("{3}")),
            steps(untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), n(0)))), rules = anyTime),
    )

    // CR 613.4d: switching in layer 7d
    @Test
    fun fluxInverter() = card(
        name = "Flux Inverter",
        cost = "{2}{U}{R}",
        type = "Creature — Weird",
        pt = "1/5",
        text = """
            Flying
            Whenever you cast an instant or sorcery spell, you may switch this creature's power and toughness until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(Trigger.On(EventPattern.Cast(instantOrSorcery, by = PlayerFilter.You)),
            steps(Step.May(listOf(untilEndOfTurn(Objects.Self, Mechanic.SwitchPowerToughness)), Players.You))),
    )

    // CR 107.3: X in the mana value comparison
    @Test
    fun glareOfGranite() = card(
        name = "Glare of Granite",
        cost = "{X}{B}{B}{G}",
        type = "Sorcery",
        text = "Destroy each nonland permanent with mana value X or less.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.And(listOf(nonland, ObjectFilter.Category(ObjectCategory.Permanent),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced("X")))))))),
            announced = listOf("X"),
        )),
    )

    @Test
    fun glintOfBattle() = card(
        name = "Glint of Battle",
        cost = "{4}{R}{W}",
        type = "Enchantment",
        text = "Whenever a creature you control attacks, put a +1/+1 counter on it.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Attack(creatureYouControl)),
            steps(Step.Do(Effect.PutCounters(Objects.EventObject(EventRole.Object), p1p1, n(1))))),
    )

    // The target is chosen at random among the legal ones, by the host
    @Test
    fun goblinTrialPilot() = card(
        name = "Goblin Trial Pilot",
        cost = "{1}{U}{R}",
        type = "Creature — Goblin Pilot Wizard",
        pt = "0/2",
        text = """
            Flying
            {T}: This creature deals 2 damage to any target chosen at random.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Activated(costOf(arcanum.api.CostPart.TapSelf), Instructions(
            targets = listOf(anyTarget.copy(atRandom = true)),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(2), source = Objects.Self))),
        ), rules = anyTime),
    )

    // CR 702.111: menace for attacking creatures
    @Test
    fun gruulBattleChant() = card(
        name = "Gruul Battle Chant",
        cost = "{2}{R}{G}",
        type = "Enchantment",
        text = "Attacking creatures you control get +1/+0 and have menace.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Menace))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Attacking), creature, you))))),
    )

    @Test
    fun haunterOfNightmist() = card(
        name = "Haunter of Nightmist",
        cost = "{3}{U}{B}",
        type = "Creature — Spirit",
        pt = "3/4",
        text = "Creatures your opponents control get -1/-0.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(-1), n(0))), affects = Affected.Objects(Objects.Each(creatureOpponentControls))),
    )
}
