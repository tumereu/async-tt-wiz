package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** INR bug-class sweep: wordings that are siblings of parser fixes INR and FDN made one card at a time. Invented names. */
class InrSweepTest {
    // CR 603.7c, 608.2h: "it" is the creature that entered, named as the delayed trigger is created (its own event, the end step, names no object)
    @Test
    fun sweepNightWarden() = card(
        name = "Sweep Night Warden",
        cost = "{2}{B}",
        type = "Enchantment",
        text = "Whenever another nontoken creature you control enters, sacrifice it at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(enters(and(another, not(token), creature, you)),
            Step.Bind(Objects.EventObject(EventRole.After), "event object"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("event object"))))),
    )

    // CR 603.7c: the same for another effect and another event: the attacking creature, named now
    @Test
    fun sweepGateWarden() = card(
        name = "Sweep Gate Warden",
        cost = "{1}{W}",
        type = "Enchantment",
        text = "Whenever a creature you control attacks, exile it at the beginning of the next end step.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(and(creature, you))),
            Step.Bind(Objects.EventObject(EventRole.Object), "event object"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), move(Objects.Bound("event object"), Zone.Exile, postBind = "exiled"))),
    )

    // CR 113.6m: an Aura's ability that returns it after the object it enchants leaves the battlefield functions on the battlefield (it
    // triggers there); CR 400.7, 704.5m: by resolution the Aura is a new object in the graveyard, and that one returns
    @Test
    fun sweepLingeringScream() = card(
        name = "Sweep Lingering Scream",
        cost = "{2}{B}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -1/-1.
            When enchanted creature dies, return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(-1, -1), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.SelfAfter, Zone.Battlefield, postBind = "returned")),
    )
}
