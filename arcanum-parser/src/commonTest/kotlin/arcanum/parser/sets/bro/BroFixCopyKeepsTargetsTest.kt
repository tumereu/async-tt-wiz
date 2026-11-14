package arcanum.parser.sets.bro

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO fix: a copy of a spell or ability keeps the original's targets unless the text lets its controller choose new ones. */
class BroFixCopyKeepsTargetsTest {
    private val instantOrSorcerySpell = and(or(instant, sorcery), spellFilter)
    private val castSpell = Objects.EventObject(EventRole.Object)

    // CR 707.10: the copy keeps the original's targets; CR 707.10c: new ones only when the effect says so
    @Test
    fun resonantScript() = card(
        name = "Resonant Script", cost = "{1}{U}{U}", type = "Instant",
        text = "Copy target instant or sorcery spell you control.",
    ) parsesTo abilities(spell(
        doing(Effect.CopySpell(target("spell"), Players.You, newTargets = false)),
        targets = listOf(slot("spell", and(or(instant, sorcery), spellFilter, you))),
    ))

    // CR 603.7: a delayed trigger for the turn; CR 707.10: its copy keeps the spell's targets (no "You may choose new targets", CR 707.10c)
    @Test
    fun twinnedReverie() = card(
        name = "Twinned Reverie", cost = "{1}{R}", type = "Sorcery",
        text = "When you next cast an instant or sorcery spell this turn, copy that spell.",
    ) parsesTo abilities(spell(
        doing(Effect.CreateDelayedTrigger(youCast(instantOrSorcerySpell), Instructions(steps = listOf(
            doing(Effect.CopySpell(castSpell, Players.You, newTargets = false)))), duration = Duration.EndOfTurn)),
    ))

    // CR 707.10: "copy it" in a cast trigger keeps the spell's targets (CR 707.10c: none chosen anew)
    @Test
    fun echoingTinkerer() = card(
        name = "Echoing Tinkerer", cost = "{3}{U}{R}", type = "Creature — Human Artificer", pt = "3/3",
        text = "Whenever you cast an instant or sorcery spell, copy it.",
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)), doing(Effect.CopySpell(castSpell, Players.You, newTargets = false))),
    )

    // CR 707.10c: "You may choose new targets for the copy." after a delayed copy turns new targets on
    @Test
    fun hollowRefrain() = card(
        name = "Hollow Refrain", cost = "{1}{R}", type = "Sorcery",
        text = "Draw a card. When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.",
    ) parsesTo abilities(spell(
        draw(1),
        doing(Effect.CreateDelayedTrigger(youCast(instantOrSorcerySpell), Instructions(steps = listOf(
            doing(Effect.CopySpell(castSpell, Players.You, newTargets = true)))), duration = Duration.EndOfTurn)),
    ))
}
