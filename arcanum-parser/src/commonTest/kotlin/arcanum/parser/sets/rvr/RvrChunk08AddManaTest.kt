package arcanum.parser.sets.rvr

import arcanum.api.Affected
import arcanum.api.CopyException
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class RvrChunk08AddManaTest {
    private fun add(mana: String) = doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(mana)!!)), Players.You))

    // CR 605.1a, 106.1: a mana ability with a mana and tap cost adding two coloured mana
    @Test
    fun azoriusSignetLookalike() = card(
        name = "Azoriushall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {W}{U}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{W}{U}")))

    @Test
    fun borosSignetLookalike() = card(
        name = "Boroshall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {R}{W}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{R}{W}")))

    @Test
    fun dimirSignetLookalike() = card(
        name = "Dimirhall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {U}{B}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{U}{B}")))

    @Test
    fun golgariSignetLookalike() = card(
        name = "Golgarihall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {B}{G}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{B}{G}")))

    @Test
    fun gruulSignetLookalike() = card(
        name = "Gruulhall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {R}{G}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{R}{G}")))

    @Test
    fun izzetSignetLookalike() = card(
        name = "Izzethall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {U}{R}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{U}{R}")))

    @Test
    fun orzhovSignetLookalike() = card(
        name = "Orzhovhall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {W}{B}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{W}{B}")))

    @Test
    fun rakdosSignetLookalike() = card(
        name = "Rakdoshall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {B}{R}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{B}{R}")))

    @Test
    fun selesnyaSignetLookalike() = card(
        name = "Selesnyahall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {G}{W}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{G}{W}")))

    @Test
    fun simicSignetLookalike() = card(
        name = "Simichall Signet",
        cost = "{2}",
        type = "Artifact",
        text = "{1}, {T}: Add {G}{U}.",
    ) parsesTo abilities(activated(cost(mana("{1}"), tap), add("{G}{U}")))

    // CR 106.1, 605.1a: sacrificing itself as the cost of a mana ability
    @Test
    fun wildCantorLookalike() = card(
        name = "Brightsprout Cantor",
        cost = "{R/G}",
        type = "Creature — Human Druid",
        pt = "1/1",
        text = """
            ({R/G} can be paid with either {R} or {G}.)
            Sacrifice this creature: Add one mana of any color.
        """,
    ) parsesTo abilities(activated(cost(sacrificeSelf), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))))

    // CR 707.9b: the copy keeps this ability
    @Test
    fun thespiansStageLookalike() = card(
        name = "Playhouse Stage",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}: This land becomes a copy of target land, except it has this ability.
        """,
    ) parsesTo abilities(
        activated(cost(tap), add("{C}")),
        activated(cost(mana("{2}"), tap),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Target("land"), listOf(CopyException.ThisAbility))),
                Affected.Objects(Objects.Self), Duration.Indefinitely)),
            targets = listOf(slot("land", land))),
    )
}
