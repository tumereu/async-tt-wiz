package arcanum.parser.sets.vow

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** VOW fixes: wordings later chunks found unread. Invented names, real oracle templates. */
class VowFixesTest {
    // CR 111.4, 608.2c: a token with a keyword, then a second effect joined by "and" (two effects in the order written, not one token)
    @Test
    fun tokenWithKeywordAndDraw() = card(
        name = "Lantern Vigil Rite",
        cost = "{2}{W}",
        type = "Sorcery",
        text = "Create a 1/1 white Spirit creature token with flying and draw a card.",
    ) parsesTo abilities(
        spell(create(creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying))), draw(1)),
    )
}
