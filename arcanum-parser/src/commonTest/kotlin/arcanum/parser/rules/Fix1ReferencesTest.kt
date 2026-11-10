package arcanum.parser.rules

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.inr.*
import kotlin.test.Test

/**
 * Rules fixes (fix1): what "it" and "that card" refer to after "you may exile …. If you do, …" and after "this creature" earlier
 * in the same sentence.
 */
class Fix1ReferencesTest {
    private val nextEndStep = on(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any))
    private val exiled = Objects.Bound("exiled")

    // CR 400.7, 603.7c: "that card" is the card the exile made, so the delayed trigger returns that card from exile, not this creature
    @Test
    fun tideglassSentry() = card(
        name = "Tideglass Sentry",
        cost = "{4}{U}",
        type = "Creature — Merfolk Soldier",
        text = """
            Flash
            When this creature enters, you may exile target historic permanent you control. If you do, return that card to the battlefield under its owner's control at the beginning of the next end step. (Artifacts, legendaries, and Sagas are historic.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(
            entersSelf,
            Step.Pay(steps = listOf(move(target("permanent"), Zone.Exile, postBind = "exiled")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateDelayedTrigger(nextEndStep, Instructions(steps = listOf(
                move(exiled, Zone.Battlefield, controller = Players.OwnerOf(exiled), postBind = "returned"))))))),
            targets = listOf(slot("permanent", and(or(artifact, legendary, sub("Saga")), permanentFilter, you))),
        ),
    )

    // CR 400.7, 603.7c, 118.12: exiling itself is the action "if you do" asks about; "it" is the exiled card
    @Test
    fun akemiDuskWarden() = card(
        name = "Akemi, Dusk Warden",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Spirit",
        text = """
            Flying
            Whenever you cast a Spirit or Arcane spell, you may exile Akemi. If you do, return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(
            youCast(or(sub("Spirit"), sub("Arcane"))),
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Self, from = Zone.Battlefield, postBind = "exiled"))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateDelayedTrigger(nextEndStep, Instructions(steps = listOf(
                move(exiled, Zone.Battlefield, controller = Players.OwnerOf(exiled)))))))),
        ),
    )

    // CR 615.1, 608.2c: "it" after "this creature" in the same sentence is this creature, not the spell that targeted it
    @Test
    fun gildedHopeful() = card(
        name = "Gilded Hopeful",
        cost = "{W}",
        type = "Creature — Human Soldier",
        text = "Heroic — Whenever you cast a spell that targets this creature, put a +1/+1 counter on this creature and prevent all damage that would be dealt to it this turn.",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(
            youCast(ObjectFilter.Targets(objects = Objects.Self)),
            counters(Objects.Self, 1),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self))))),
                null, Duration.EndOfTurn)),
            abilityWord = "Heroic",
        ),
    )
}
