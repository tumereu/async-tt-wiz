package arcanum.parser.sets.mh3

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.PlayerFilter
import arcanum.api.Value
import arcanum.api.Combine
import arcanum.api.CostPart
import arcanum.api.Cost
import arcanum.api.CountersToPut
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Instructions
import arcanum.api.NumericProperty
import arcanum.api.TurnStep
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 04: adapt (CR 701.46) with "Whenever one or more +1/+1 counters are put on this creature" (CR 122.6), modified permanents
 * (CR 700.9), exert with a reflexive trigger (CR 701.43, 603.12) and counter-doubling statics.
 */
class Mh3Chunk04AdaptTest {
    private val isSelf: ObjectFilter = ObjectFilter.Is(Objects.Self)
    private val countersPutOnSelf: Trigger = Trigger.On(EventPattern.CountersPut(isSelf, p1p1), oneOrMore = true)
    private val thatCreature: Objects = Objects.EventObject(arcanum.api.EventRole.Object)
    private fun adapt(cost: String, n: Int) = activated(cost(mana(cost)), doing(Effect.Action(KeywordAction.Adapt(n(n)))))

    // CR 701.46a, 122.6: the trigger fires once for the counters put on it, and a May ("you may create")
    @Test
    fun baskingBroodscale() = card(
        name = "Basking Broodscale",
        cost = "{1}{G}",
        type = "Creature — Eldrazi Lizard",
        pt = "2/2",
        text = """
            Devoid (This card has no color.)
            {1}{G}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
            Whenever one or more +1/+1 counters are put on this creature, you may create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        """,
    ) parsesTo abilities(
        devoid,
        adapt("{1}{G}", 1),
        triggered(countersPutOnSelf, Step.May(listOf(create(eldraziSpawn)), Players.You)),
    )

    // CR 122.6, 702.12 (menace until end of turn)
    @Test
    fun dreamdrinkerVampire() = card(
        name = "Dreamdrinker Vampire",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        pt = "2/1",
        text = """
            Lifelink
            {1}{B}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
            Whenever one or more +1/+1 counters are put on this creature, it gains menace until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        adapt("{1}{B}", 1),
        triggered(countersPutOnSelf, gains(thatCreature, Keyword.Menace)),
    )

    // CR 702.9b-style restriction "can block only" (CR 509.1b); CR 601.2f cost reduction for each other artifact you control
    @Test
    fun etheriumPteramander() = card(
        name = "Etherium Pteramander",
        cost = "{B}",
        type = "Artifact Creature — Salamander Drake",
        pt = "1/1",
        text = """
            Flying
            This creature can block only creatures with flying.
            {6}{B}: Adapt 4. This ability costs {1} less to activate for each other artifact you control. (If this creature has no +1/+1 counters on it, put four +1/+1 counters on it.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.CanBlockOnly(ObjectFilter.And(listOf(creature, kw(Keyword.Flying.kind))))),
        activated(cost(mana("{6}{B}")), doing(Effect.Action(KeywordAction.Adapt(n(4)))),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(and(another, artifact, you))))),
    )

    // CR 122.6; return a permanent card from the graveyard to hand (CR 400.7)
    @Test
    fun evolutionWitness() = card(
        name = "Evolution Witness",
        cost = "{2}{G}",
        type = "Creature — Elf Shaman Mutant",
        pt = "2/1",
        text = """
            {1}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
            Whenever one or more +1/+1 counters are put on this creature, return target permanent card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        adapt("{1}{G}", 2),
        triggered(countersPutOnSelf, doing(Effect.Move(target("card"), Destination(Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(permanentCard)))),
    )

    // CR 700.9: modified = has a counter, is equipped, or is enchanted by your Aura
    @Test
    fun expandingOoze() = card(
        name = "Expanding Ooze",
        cost = "{1}{B}{G}",
        type = "Creature — Ooze",
        pt = "3/3",
        text = """
            {B}{G}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
            Whenever this creature attacks, put a +1/+1 counter on target modified creature you control. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        adapt("{B}{G}", 1),
        triggered(attacksSelf, counters(target("creature"), 1), targets = listOf(slot("creature", and(creature, you, ObjectFilter.Modified)))),
    )

    // CR 122.6; "If you do" after a May (CR 608.2c)
    @Test
    fun fetidGargantua() = card(
        name = "Fetid Gargantua",
        cost = "{4}{B}",
        type = "Creature — Horror",
        pt = "4/4",
        text = """
            {2}{B}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
            Whenever one or more +1/+1 counters are put on this creature, you may draw two cards. If you do, you lose 2 life.
        """,
    ) parsesTo abilities(
        adapt("{2}{B}", 2),
        triggered(countersPutOnSelf,
            Step.Pay(null, listOf(draw(2)), Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), listOf(loseLife(2)))),
    )

    // CR 700.9, 613.1f (ability-adding static over modified creatures you control)
    @Test
    fun temperamentalOozewagg() = card(
        name = "Temperamental Oozewagg",
        cost = "{3}{G}",
        type = "Creature — Ooze Brushwagg",
        pt = "4/4",
        text = """
            {2}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
            Modified creatures you control have trample. (Equipment, Auras you control, and counters are modifications.)
        """,
    ) parsesTo abilities(
        adapt("{2}{G}", 2),
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, ObjectFilter.Modified))),
    )

    // CR 701.43d, 603.12: exert as it attacks, then a reflexive trigger with a target; X counts every counter on your permanents (CR 122.1)
    @Test
    fun hydraTrainer() = card(
        name = "Hydra Trainer",
        cost = "{1}{G}",
        type = "Creature — Human Warrior",
        pt = "1/1",
        text = """
            You may exert this creature as it attacks. When you do, target creature gets +X/+X until end of turn, where X is the number of counters on permanents you control. (An exerted creature won't untap during your next untap step.)
            {2}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.AsAttacks(Cost(listOf(CostPart.Action(KeywordAction.Exert(Objects.Self)))),
            Instructions(targets = listOf(slot("creature", creature)), steps = listOf(
                untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(counterTotal, counterTotal)))))), Affected.Objects(Objects.Self)),
        adapt("{2}{G}", 2),
    )

    private val counterTotal = Value.Of(NumericProperty.Counters(null), Objects.Each(and(permanentFilter, you)), Combine.Total)

    // CR 122.6 (extra counters), 603.2 and 603.2c via "once each turn"; a permanent's own granted trigger (CR 113.10)
    @Test
    fun cursedWombat() = card(
        name = "Cursed Wombat",
        cost = "{B}{G}",
        type = "Creature — Nightmare Wombat",
        pt = "2/3",
        text = """
            {2}{B}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
            Permanents you control have "Whenever one or more +1/+1 counters are put on this permanent, put an additional +1/+1 counter on it. This ability triggers only once each turn."
        """,
    ) parsesTo abilities(
        adapt("{2}{B}{G}", 2),
        static(Mechanic.GainAbilities(listOf(triggered(countersPutOnSelf, counters(thatCreature, 1), perTurn = 1))),
            affects = Objects.Each(and(permanentFilter, you))),
    )

    // CR 607.2a (cards exiled with this), 122.6 (finality), 603.7 (delayed sacrifice), 611.2a
    @Test
    fun emperorOfBones() = card(
        name = "Emperor of Bones",
        cost = "{1}{B}",
        type = "Creature — Skeleton Noble",
        pt = "2/2",
        text = """
            At the beginning of combat on your turn, exile up to one target card from a graveyard.
            {1}{B}: Adapt 2.
            Whenever one or more +1/+1 counters are put on this creature, put a creature card exiled with this creature onto the battlefield under your control with a finality counter on it. It gains haste. Sacrifice it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, move(target("card"), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
        adapt("{1}{B}", 2),
        triggered(countersPutOnSelf,
            Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Where(Objects.Remembered("exiled"), creature)), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You,
                counters = listOf(CountersToPut(CounterKind.Finality, n(1)))), postBind = "returned")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))), Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("returned")))))))),
    )
}
