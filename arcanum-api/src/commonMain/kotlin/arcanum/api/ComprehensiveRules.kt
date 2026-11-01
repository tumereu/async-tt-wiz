package arcanum.api

/**
 * The one Comprehensive Rules version the Arcanum libraries follow. Code and test comments cite rule numbers of this
 * version (`// CR 613.4c`). Moving to a newer version is a deliberate task: compare the two rule texts, then review
 * every place that cites a rule that was added, changed, removed or renumbered (docs/specs/arcanum-automatum.md §4.3).
 */
object ComprehensiveRules {
    /** Effective date of the pinned version, as printed at the top of the rules text. */
    const val EFFECTIVE_DATE: String = "2026-09-25"
}
