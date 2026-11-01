package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Color
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 7: counterspells. "Counter target [kind of] spell", sometimes "unless its controller pays {N}" (one step that
 * counters unless paid, CR 118.12a), sometimes with an alternative cost ("rather than pay this spell's mana cost",
 * CR 118.9), and sometimes changing where the countered spell goes ("If that spell is countered this way, … instead"),
 * which is the spell's own replacement (CR 614.15).
 */
class Chunk07CounterspellsTest {
    // ---- Shared shapes

    private val spell = ObjectFilter.Category(ObjectCategory.Spell)
    private val targetSpell = Objects.Target("spell")

    /** "Counter target [what]." with [more] steps after it. */
    private fun counter(
        what: ObjectFilter,
        replacements: List<Mechanic.Replacement> = emptyList(),
        more: List<Step> = emptyList(),
    ) = Ability.Spell(Instructions(
        targets = listOf(TargetSlot(
            name = "spell",
            what = TargetKind.Objects(what),
            min = Value.Fixed(1),
            max = Value.Fixed(1),
        )),
        steps = listOf(Step.Do(Effect.Counter(targetSpell))) + more,
        replacements = replacements,
    ))

    /** "Counter target [what] unless its controller pays [cost]." */
    private fun counterUnless(what: ObjectFilter, cost: String) = Ability.Spell(Instructions(
        targets = listOf(TargetSlot(
            name = "spell",
            what = TargetKind.Objects(what),
            min = Value.Fixed(1),
            max = Value.Fixed(1),
        )),
        steps = listOf(
            Step.Pay(
                cost = manaCost(cost),
                payer = Players.ControllerOf(targetSpell),
                bind = "paid",
            ),
            Step.If(
                condition = Condition.Not(Condition.Did("paid")),
                then = listOf(Step.Do(Effect.Counter(targetSpell))),
            ),
        ),
    ))

    /** "If that spell is countered this way, [put it there] instead of into that player's graveyard." */
    private fun counteredGoesTo(to: Destination) = Mechanic.Replacement(
        event = EventPattern.ZoneChange(ObjectFilter.Is(targetSpell), from = Zone.Stack, to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(to)),
    )

    /** "You may [parts] rather than pay this spell's mana cost." */
    private fun alternativeCost(vararg parts: CostPart, condition: Condition? = null) = Ability.Static(
        mechanics = listOf(Mechanic.AlternativeCost(cost = Cost(parts.toList()))),
        condition = condition,
        affects = Affected.Objects(Objects.Self),
    )

    private val noncreatureSpell = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)), spell))
    private val blueCardFromHand = CostPart.Exile(
        CostObjects.Choose(ObjectFilter.OfColor(Color.Blue), count = Value.Fixed(1)),
        from = Zone.Hand,
    )

    // ---- Plain counterspells

    @Test
    fun denial() = card(
        name = "Denial",
        cost = "{U}{U}",
        type = "Instant",
        text = "Counter target spell.",
    ) parsesTo abilities(
        counter(spell),
    )

    @Test
    fun spellNoose() = card(
        name = "Spell Noose",
        cost = "{U}",
        type = "Instant",
        text = "Counter target spell with mana value 2.",
    ) parsesTo abilities(
        counter(ObjectFilter.And(listOf(spell, ObjectFilter.ManaValue(Comparison.Equal, Value.Fixed(2))))),
    )

    @Test
    fun sharpRebuke() = card(
        name = "Sharp Rebuke",
        cost = "{U}",
        type = "Instant",
        text = "Counter target creature spell with power or toughness 2 or less.",
    ) parsesTo abilities(
        counter(ObjectFilter.And(listOf(
            ObjectFilter.Type(CardType.Creature),
            spell,
            ObjectFilter.Or(listOf(
                ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)),
                ObjectFilter.Toughness(Comparison.AtMost, Value.Fixed(2)),
            )),
        ))),
    )

    // ---- Unless its controller pays

    @Test
    fun manaSeep() = card(
        name = "Mana Seep",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {3}.",
    ) parsesTo abilities(
        counterUnless(spell, cost = "{3}"),
    )

    @Test
    fun manaLevy() = card(
        name = "Mana Levy",
        cost = "{W}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {1}.",
    ) parsesTo abilities(
        counterUnless(spell, cost = "{1}"),
    )

    @Test
    fun spellPrick() = card(
        name = "Spell Prick",
        cost = "{U}",
        type = "Instant",
        text = "Counter target noncreature spell unless its controller pays {2}.",
    ) parsesTo abilities(
        counterUnless(noncreatureSpell, cost = "{2}"),
    )

    // ---- Alternative costs

    @Test
    fun lapseOfFocus() = card(
        name = "Lapse of Focus",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            You may return an Island you control to its owner's hand rather than pay this spell's mana cost.
            Counter target spell unless its controller pays {1}.
        """,
    ) parsesTo abilities(
        alternativeCost(CostPart.ReturnToHand(CostObjects.Choose(ObjectFilter.And(listOf(
            ObjectFilter.Sub(Subtype("Island")),
            ObjectFilter.ControlledBy(PlayerFilter.You),
        )), count = Value.Fixed(1)))),
        counterUnless(spell, cost = "{1}"),
    )

    @Test
    fun blazeOfRefusal() = card(
        name = "Blaze of Refusal",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken blue creature rather than pay this spell's mana cost.
            Counter target spell.
        """,
    ) parsesTo abilities(
        alternativeCost(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
            ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
            ObjectFilter.OfColor(Color.Blue),
            ObjectFilter.Type(CardType.Creature),
        )), count = Value.Fixed(1)))),
        counter(spell),
    )

    @Test
    fun forceOfResolve() = card(
        name = "Force of Resolve",
        cost = "{3}{U}{U}",
        type = "Instant",
        text = """
            You may pay 1 life and exile a blue card from your hand rather than pay this spell's mana cost.
            Counter target spell.
        """,
    ) parsesTo abilities(
        alternativeCost(CostPart.PayLife(Value.Fixed(1)), blueCardFromHand),
        counter(spell),
    )

    @Test
    fun forceOfRebuttal() = card(
        name = "Force of Rebuttal",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            If it's not your turn, you may exile a blue card from your hand rather than pay this spell's mana cost.
            Counter target noncreature spell. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.
        """,
    ) parsesTo abilities(
        alternativeCost(blueCardFromHand, condition = Condition.Not(Condition.YourTurn)),
        counter(noncreatureSpell, replacements = listOf(counteredGoesTo(Destination(Zone.Exile)))),
    )

    // ---- Where the countered spell goes, and more

    @Test
    fun recallLapse() = card(
        name = "Recall Lapse",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell. If that spell is countered this way, put it on top of its owner's library instead of into that player's graveyard.",
    ) parsesTo abilities(
        counter(spell, replacements = listOf(counteredGoesTo(Destination(Zone.Library, libraryPosition = 0)))),
    )

    @Test
    fun sendBack() = card(
        name = "Send Back",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Counter target spell. If that spell is countered this way, put it into its owner's hand instead of into that player's graveyard.
            Draw a card.
        """,
    ) parsesTo abilities(
        counter(
            spell,
            replacements = listOf(counteredGoesTo(Destination(Zone.Hand))),
            more = listOf(Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))),
        ),
    )

    @Test
    fun manaSiphon() = card(
        name = "Mana Siphon",
        cost = "{U}{U}",
        type = "Instant",
        text = "Counter target spell. At the beginning of your next main phase, add an amount of {C} equal to that spell's mana value.",
    ) parsesTo abilities(
        counter(
            spell,
            more = listOf(
                Step.Do(Effect.CreateDelayedTrigger(
                    // "your next main phase": whichever comes first
                    trigger = Trigger.On(listOf(
                        EventPattern.StepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
                        EventPattern.StepBegins(TurnStep.PostcombatMain, PlayerFilter.You),
                    )),
                    instructions = Instructions(
                        steps = listOf(
                            Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(
                                mana = ManaCost.parse("{C}")!!,
                                times = Value.Of(NumericProperty.ManaValue, targetSpell, Combine.Single),
                            )), players = Players.You)),
                        ),
                    ),
                )),
            ),
        ),
    )
}
