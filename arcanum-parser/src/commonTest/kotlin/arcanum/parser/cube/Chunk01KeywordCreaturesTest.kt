package arcanum.parser.cube

import arcanum.api.Keyword
import kotlin.test.Test

/**
 * Chunk 1: creatures whose rules text is only keywords (and reminder text). The keyword table
 * ([arcanum.api.KeywordExpansions]) says what each keyword does; here the parser only has to recognise them.
 */
class Chunk01KeywordCreaturesTest {
    @Test
    fun tinkeredMite() = card(
        name = "Tinkered Mite",
        cost = "{0}",
        type = "Artifact Creature — Golem",
        pt = "1/1",
    ) parsesTo none

    @Test
    fun gearwingGlider() = card(
        name = "Gearwing Glider",
        cost = "{0}",
        type = "Artifact Creature — Thopter",
        text = "Flying",
        pt = "0/2",
    ) parsesTo abilities(
        Keyword.Flying,
    )

    @Test
    fun enamelPhalanxer() = card(
        name = "Enamel Phalanxer",
        cost = "{2}{W/P}",
        type = "Artifact Creature — Phyrexian Knight",
        text = """
            ({W/P} can be paid with either {W} or 2 life.)
            First strike
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.FirstStrike,
    )

    @Test
    fun festeringGremlin() = card(
        name = "Festering Gremlin",
        cost = "{1}{B}",
        type = "Creature — Zombie Gremlin",
        text = "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)",
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Persist,
    )

    @Test
    fun havenWarden() = card(
        name = "Haven Warden",
        cost = "{1}{G/W}",
        type = "Creature — Elf Soldier",
        text = "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)",
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Persist,
    )

    @Test
    fun splinteredEnvoy() = card(
        name = "Splintered Envoy",
        cost = "{1}{G}{U}",
        type = "Artifact Creature — Human Spy",
        text = "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)",
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Cascade,
    )

    @Test
    fun cryptFlitter() = card(
        name = "Crypt Flitter",
        cost = "{1}{B/P}",
        type = "Artifact Creature — Phyrexian Bat",
        text = """
            ({B/P} can be paid with either {B} or 2 life.)
            Flying
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
    )
}
