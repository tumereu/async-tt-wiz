package arcanum.parser

import arcanum.api.Keyword
import kotlin.test.Test
import kotlin.test.assertEquals

/** Listing the subtypes of a sealed type needs JVM reflection, so this check lives in jvmTest. */
class KeywordSyntaxCompletenessTest {
    @Test
    fun parserReadsEveryKeywordTypeExactlyOnce() {
        val types = Keyword::class.sealedSubclasses.map { it.simpleName }.sortedBy { it }
        val parsed = KeywordSyntax.all.map { syntax ->
            OracleParser.keyword(syntax.text + when (syntax) {
                is KeywordSyntax.None -> ""; is KeywordSyntax.Amount -> " 1"
                is KeywordSyntax.Cost -> " {1}"; is KeywordSyntax.AmountAndCost -> " 1—{1}"
            })!!::class.simpleName
        }.sortedBy { it }
        assertEquals(types, parsed)
    }
}
