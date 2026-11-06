package arcanum.parser

import arcanum.api.RulesGlossary
import kotlin.test.Test
import kotlin.test.assertTrue

/** The rule explainers (arcanum-api [RulesGlossary]): every keyword the parser reads has a plain-words explanation, found
 * under the name the card prints. */
class KeywordExplanationsTest {
    @Test
    fun everyKeywordTheParserReadsIsExplained() {
        val unexplained = KeywordSyntax.all.mapNotNull { syntax ->
            val keyword = OracleParser.keyword(syntax.text + when (syntax) {
                is KeywordSyntax.None -> ""; is KeywordSyntax.Amount -> " 1"
                is KeywordSyntax.Cost -> " {1}"; is KeywordSyntax.AmountAndCost -> " 1—{1}"
            })!!
            val entry = RulesGlossary.of(keyword.kind)
            syntax.text.takeIf { entry == null || RulesGlossary.first(keyword.text) != entry }
        }
        assertTrue(unexplained.isEmpty(), "keywords the parser reads without an explanation under their printed name: $unexplained")
    }
}
