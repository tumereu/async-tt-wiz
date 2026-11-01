package arcanum.parser

import arcanum.api.Keyword
import kotlin.test.Test
import kotlin.test.assertEquals

/** Listing the subtypes of a sealed type needs JVM reflection, so this check lives in jvmTest. */
class KeywordSyntaxCompletenessTest {
    /** Keyword types written with values the parser can't read yet (a quality, an object, a non-mana cost); each is
     * removed from here when its wording group is built. */
    private val notReadYet = setOf("CompanionAbility", "CommanderNinjutsu", "Enchant", "Protection", "HexproofFrom", "Landwalk", "Affinity", "Typecycling",
        "Splice", "PartnerWith", "Prototype")

    @Test
    fun parserReadsEveryKeywordTypeExactlyOnce() {
        val types = Keyword::class.sealedSubclasses.map { it.simpleName }.filter { it !in notReadYet }.sortedBy { it }
        val parsed = KeywordSyntax.all.map { syntax ->
            OracleParser.keyword(syntax.text + when (syntax) {
                is KeywordSyntax.None -> ""; is KeywordSyntax.Amount -> " 1"
                is KeywordSyntax.Cost -> " {1}"; is KeywordSyntax.AmountAndCost -> " 1—{1}"
            })!!::class.simpleName
        }.sortedBy { it }
        assertEquals(types, parsed)
    }
}
