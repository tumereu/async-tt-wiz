package arcanum.parser.sets.vow

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * VOW chunk 07: training (CR 702.149) and "whenever this creature trains", and effects with conditions: night (CR 732.1), the target's
 * controller controlling no other creatures, the damaged permanent's subtype, counters you've put this turn, a prevention that removes counters.
 */
class VowChunk07TrainingAndConditionsTest {
    private val trainingLine = "Training (Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature.)"
    private val trains = Trigger.On(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), p1p1,
        causedBy = ObjectFilter.AbilityOf(AbilityKind.Triggered, keyword = Keyword.Training)), oneOrMore = true)

    // CR 702.149a; CR 603.2c (one trigger for counters put on several Humans at once), 603.2h (once each turn)
    @Test
    fun cloakedCadet() = card(
        name = "Hooded Recruit",
        cost = "{4}{G}",
        type = "Creature — Human Ranger",
        pt = "2/4",
        text = """
            $trainingLine
            Whenever one or more +1/+1 counters are put on one or more Humans you control, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(Trigger.On(EventPattern.CountersPut(and(sub("Human"), you), p1p1), oneOrMore = true), draw(1), perTurn = 1),
    )

    // CR 702.149c ("trains": a resolving training ability puts counters on it); CR 607.2a (the exiled cards are linked to the leaves trigger),
    // 610.3 (they return under their owners' control)
    @Test
    fun saviorOfOllenbock() = card(
        name = "Warden of Ollenmark",
        cost = "{1}{W}{W}",
        type = "Creature — Human Soldier",
        pt = "1/2",
        text = """
            $trainingLine
            Whenever this creature trains, exile up to one other target creature from the battlefield or creature card from a graveyard.
            When this creature leaves the battlefield, put the exiled cards onto the battlefield under their owners' control.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(trains,
            move(target("targets"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("targets", or(and(another, creature, inZone(Zone.Battlefield)), and(creature, inZone(Zone.Graveyard))), min = 0))),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            move(exiledWith, Zone.Battlefield, controller = Players.OwnerOf(exiledWith))),
    )

    // CR 702.149a
    @Test
    fun apprenticeSharpshooter() = card(
        name = "Novice Bowyer",
        cost = "{2}{G}",
        type = "Creature — Human Archer",
        pt = "1/4",
        text = """
            Reach
            $trainingLine
        """,
    ) parsesTo abilities(Keyword.Reach, Keyword.Training)

    // CR 702.149a
    @Test
    fun gryffRider() = card(
        name = "Gryphon Outrider",
        cost = "{2}{W}",
        type = "Creature — Human Knight",
        pt = "2/1",
        text = """
            Flying
            $trainingLine
        """,
    ) parsesTo abilities(Keyword.Flying, Keyword.Training)

    // CR 702.149a; CR 118.12 (you may pay; if you do), 115.1 (the target is chosen as the trigger is put on the Stack)
    @Test
    fun gryffwingCavalry() = card(
        name = "Gryphonwing Escort",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            Flying
            $trainingLine
            Whenever this creature attacks, you may pay {1}{W}. If you do, target attacking creature without flying gains flying until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Training,
        triggered(attacksSelf,
            Step.Pay(cost(mana("{1}{W}")), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), gains(target("creature"), Keyword.Flying)),
            targets = listOf(slot("creature", and(attacking, creature, not(kw(Keyword.Flying)))))),
    )

    // CR 702.149a; CR 122.1 (counters removed from among creatures as a cost), 118.3
    @Test
    fun hopefulInitiate() = card(
        name = "Earnest Acolyte",
        cost = "{W}",
        type = "Creature — Human Warlock",
        pt = "1/2",
        text = """
            $trainingLine
            {2}{W}, Remove two +1/+1 counters from among creatures you control: Destroy target artifact or enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        activated(cost(mana("{2}{W}"), CostPart.RemoveCounters(p1p1, n(2), CostObjects.Choose(and(creature, you), count = null), distributed = true)),
            destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
    )

    // CR 702.149a; CR 122.2 (its counters, as it last existed, move to the target)
    @Test
    fun parishBladeTrainee() = card(
        name = "Chapel-Blade Squire",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "1/2",
        text = """
            $trainingLine
            When this creature dies, put its counters on target creature you control.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(diesSelf,
            doing(Effect.MoveCounters(from = Objects.Self, to = target("creature"), kind = null,
                amount = Value.Of(NumericProperty.Counters(kind = null), Objects.Self, Combine.Single))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 702.149a; CR 111.1
    @Test
    fun ruralRecruit() = card(
        name = "Hamlet Conscript",
        cost = "{3}{G}",
        type = "Creature — Human Peasant",
        pt = "1/1",
        text = """
            $trainingLine
            When this creature enters, create a 3/1 green Boar creature token.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(entersSelf, create(creatureToken("Boar", listOf(Color.Green), 3, 1))),
    )

    // CR 702.149a; CR 111.10 (a token with training)
    @Test
    fun torensFistOfTheAngels() = card(
        name = "Toran, Fist of the Host",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "2/2",
        text = """
            $trainingLine
            Whenever you cast a creature spell, create a 1/1 green and white Human Soldier creature token with training.
        """,
    ) parsesTo abilities(
        Keyword.Training,
        triggered(on(EventPattern.Cast(creature, by = PlayerFilter.You)),
            create(creatureToken("Human Soldier", listOf(Color.Green, Color.White), 1, 1, listOf(Keyword.Training)))),
    )

    // CR 615.1a (prevented while it has a counter, all of it), 615.5 (removing counters is the prevention's additional effect), 603.12 (a
    // reflexive trigger dealing the number removed, which can be fewer than the damage prevented)
    @Test
    fun magmaPummeler() = card(
        name = "Lava Bruiser",
        cost = "{X}{R}{R}",
        type = "Creature — Elemental",
        pt = "0/0",
        text = """
            This creature enters with X +1/+1 counters on it.
            If damage would be dealt to this creature while it has a +1/+1 counter on it, prevent that damage and remove that many +1/+1 counters from it. When one or more counters are removed from this creature this way, it deals that much damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))), Affected.Objects(Objects.Self)),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))),
            additional = Instructions(steps = listOf(
                doing(Effect.RemoveCounters(Objects.Self, p1p1, Value.EventAmount, bind = "removed")),
                ifThen(Condition.Compare(Value.Bound("removed"), Comparison.AtLeast, n(1)),
                    reflexive(damage(Value.Bound("removed"), toAny), targets = listOf(anyTarget)))))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(p1p1, atLeast = 1))),
    )

    // CR 732.1 (night, checked as the spell resolves), 608.2c ("it" is the first target), 608.2h; the targets are chosen either way (CR 601.2c)
    @Test
    fun wolfStrike() = card(
        name = "Lupine Pounce",
        cost = "{2}{G}",
        type = "Instant",
        text = "Target creature you control gets +2/+0 until end of turn if it's night. Then it deals damage equal to its power to target creature you don't control.",
    ) parsesTo abilities(
        spell(
            ifThen(Condition.IsNight, pump(target("creature"), 2, 0)),
            damage(power(target("creature")), toObjects(target("creature you don't control")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("creature you don't control", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 608.2c ("that opponent": the target's controller), 608.2h (checked as the ability resolves); "other": other than the target
    @Test
    fun skulkingKiller() = card(
        name = "Lurking Cutthroat",
        cost = "{3}{B}",
        type = "Creature — Vampire Assassin",
        pt = "4/2",
        text = "When this creature enters, target creature an opponent controls gets -2/-2 until end of turn if that opponent controls no other creatures.",
    ) parsesTo abilities(
        triggered(entersSelf,
            ifThen(Condition.Not(Condition.Exists(and(not(ObjectFilter.Is(target("creature"))), creature,
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(target("creature"))))), atLeast = 1)),
                pump(target("creature"), -2, -2)),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 608.2h (whether it is a Spirit as the spell resolves), 120.3 (damage to the permanent's controller)
    @Test
    fun rendingFlame() = card(
        name = "Tearing Blaze",
        cost = "{2}{R}",
        type = "Instant",
        text = "Tearing Blaze deals 5 damage to target creature or planeswalker. If that permanent is a Spirit, Tearing Blaze also deals 2 damage to that permanent's controller.",
    ) parsesTo abilities(
        spell(
            damage(5, toObjects(target("permanent"))),
            ifThen(Condition.ObjectMatches(target("permanent"), sub("Spirit")),
                damage(2, Recipients(players = Players.ControllerOf(target("permanent"))))),
            targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)))),
        ),
    )

    // CR 611.3a (a static ability's condition), 122.6 (counters you put, on any creature)
    @Test
    fun sigardianPaladin() = card(
        name = "Sigmark Templar",
        cost = "{2}{G}{W}",
        type = "Creature — Human Knight",
        pt = "4/4",
        text = """
            As long as you've put one or more +1/+1 counters on a creature this turn, this creature has trample and lifelink.
            {1}{G}{W}: Target creature you control with a +1/+1 counter on it gains trample and lifelink until end of turn.
        """,
    ) parsesTo abilities(
        static(has(Keyword.Trample, Keyword.Lifelink),
            condition = Condition.HappenedThisTurn(EventPattern.CountersPut(creature, p1p1, causedBy = you), atLeast = 1, measure = Measure.Events)),
        activated(manaCost("{1}{G}{W}"), gains(target("creature"), Keyword.Trample, Keyword.Lifelink),
            targets = listOf(slot("creature", and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
    )

    // CR 115.3 (each "other target creature" differs from the targets before it), 611.2c (the vigilance goes to those creatures as it resolves)
    @Test
    fun armTheCathars() = card(
        name = "Rally the Wardens",
        cost = "{1}{W}{W}",
        type = "Sorcery",
        text = "Until end of turn, target creature gets +3/+3, up to one other target creature gets +2/+2, and up to one other target creature gets +1/+1. Those creatures gain vigilance until end of turn.",
    ) parsesTo abilities(
        spell(
            pump(target("creature"), 3, 3),
            pump(target("other creature"), 2, 2),
            pump(target("second other creature"), 1, 1),
            gains(Objects.Union(listOf(target("creature"), target("other creature"), target("second other creature"))), Keyword.Vigilance),
            targets = listOf(
                slot("creature", creature),
                slot("other creature", creature, min = 0).copy(distinctFrom = listOf("creature")),
                slot("second other creature", creature, min = 0).copy(distinctFrom = listOf("creature", "other creature")),
            ),
        ),
    )
}
