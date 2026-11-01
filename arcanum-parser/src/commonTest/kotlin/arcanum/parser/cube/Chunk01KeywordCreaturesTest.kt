package arcanum.parser.cube

import arcanum.api.Keyword
import kotlin.test.Test

/**
 * Chunk 1: creatures whose rules text is only keywords (and reminder text). The keyword table
 * ([arcanum.api.KeywordExpansions]) says what each keyword does; here the parser only has to recognise them.
 */
class Chunk01KeywordCreaturesTest {
    @Test fun tinkeredMite() = card("Tinkered Mite", "{0}", "Artifact Creature — Golem", pt = "1/1") parsesTo none

    @Test fun gearwingGlider() = card("Gearwing Glider", "{0}", "Artifact Creature — Thopter", "Flying", pt = "0/2") parsesTo
        abilities(Keyword.Flying)

    @Test fun enamelPhalanxer() = card("Enamel Phalanxer", "{2}{W/P}", "Artifact Creature — Phyrexian Knight",
        "({W/P} can be paid with either {W} or 2 life.)\nFirst strike", pt = "3/1") parsesTo
        abilities(Keyword.FirstStrike)

    @Test fun festeringGremlin() = card("Festering Gremlin", "{1}{B}", "Creature — Zombie Gremlin",
        "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)",
        pt = "2/2") parsesTo abilities(Keyword.Persist)

    @Test fun havenWarden() = card("Haven Warden", "{1}{G/W}", "Creature — Elf Soldier",
        "Persist (When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)",
        pt = "2/2") parsesTo abilities(Keyword.Persist)

    @Test fun splinteredEnvoy() = card("Splintered Envoy", "{1}{G}{U}", "Artifact Creature — Human Spy",
        "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)",
        pt = "2/2") parsesTo abilities(Keyword.Cascade)

    @Test fun cryptFlitter() = card("Crypt Flitter", "{1}{B/P}", "Artifact Creature — Phyrexian Bat",
        "({B/P} can be paid with either {B} or 2 life.)\nFlying\nLifelink (Damage dealt by this creature also causes you to gain that much life.)",
        pt = "1/1") parsesTo abilities(Keyword.Flying, Keyword.Lifelink)
}
