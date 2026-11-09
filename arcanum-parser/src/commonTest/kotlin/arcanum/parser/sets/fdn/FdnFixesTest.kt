package arcanum.parser.sets.fdn

import arcanum.api.CostChange
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Players
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** General parser fixes found while building FDN (the fix chunk). */
class FdnFixesTest {
    // CR 601.2f, CR 205.3: a spell with either creature type costs {1} less; "you cast" describes both kinds, not only the second
    @Test
    fun eitherTypeSpellsYouCast() = card(
        name = "Hillbanner Scout",
        cost = "{1}{W}",
        type = "Creature — Kithkin Soldier",
        pt = "2/1",
        text = "Kithkin or Soldier spells you cast cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(or(sub("Kithkin"), sub("Soldier")), spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 106.6a: the mana gives its spell haste only if that spell is a Dragon creature spell; until end of turn
    @Test
    fun orbOfDragonkind() = card(
        name = "Crimson Orb of Wyrms",
        cost = "{2}{R}",
        type = "Artifact",
        text = "{T}: Add {R}. If that mana is spent on a Dragon creature spell, it gains haste until end of turn.",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!), spellGets = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
            spellGetsIf = and(sub("Dragon"), creature, spellFilter), spellGetsUntil = Duration.EndOfTurn), Players.You))),
    )
}
