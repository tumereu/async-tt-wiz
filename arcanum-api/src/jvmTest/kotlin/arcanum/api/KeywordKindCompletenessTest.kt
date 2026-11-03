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
        // Variants have no kind of their own: typecycling is cycling (CR 702.29f), multikicker is kicker (702.33c),
        // megamorph is morph (702.37b), hexproof from is hexproof (702.11d-e); tla: firebending X is firebending (702.189a).
        assertEquals(Keyword::class.sealedSubclasses.size - 5, kinds.size)
        assertEquals(kinds.map { it.id }.sorted(), KeywordKind.all.map { it.id }.sorted())
        assertEquals(KeywordKind.all.size, KeywordKind.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyKeywordCitesARuleOfChapter702() {
        // blb: a class level bar is a keyword ability defined with Class cards (CR 716.2)
        KeywordKind.all.forEach { assertTrue(Regex("""702\.\d+[a-z]?""").matches(it.rule) || it.rule == "716.2", "${it.id}: ${it.rule}") }
    }

    @Test
    fun definitionChecksListEveryKeywordOnlyMechanic() {
        assertEquals(Mechanic.KeywordOnly::class.sealedSubclasses.map { it.simpleName.orEmpty() }.sorted(),
            DefinitionChecks.keywordOnly.map { it::class.simpleName.orEmpty() }.sorted())
    }
}
