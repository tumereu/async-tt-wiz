package arcanum.api

import kotlin.reflect.full.companionObjectInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Listing the subtypes of a sealed type needs JVM reflection, so this check lives in jvmTest. */
class KeywordKindCompletenessTest {
    @Test
    fun everyKeywordTypeHasAKindAndEveryKindIsListedOnce() {
        val kinds = Keyword::class.sealedSubclasses.mapNotNull { type ->
            type.objectInstance?.kind ?: type.companionObjectInstance as? KeywordKind
        }
        // Typecycling has no kind of its own: it is a kind of cycling (CR 702.29f).
        assertEquals(Keyword::class.sealedSubclasses.size - 1, kinds.size)
        assertEquals(kinds.map { it.id }.sorted(), KeywordKind.all.map { it.id }.sorted())
        assertEquals(KeywordKind.all.size, KeywordKind.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyKeywordCitesARuleOfChapter702() {
        KeywordKind.all.forEach { assertTrue(Regex("""702\.\d+[a-z]?""").matches(it.rule), "${it.id}: ${it.rule}") }
    }
}
