package arcanum.engine

/**
 * Which cards the engine plays fully by the rules (D61): the parts of a card it leaves to the players, and the version
 * a host keeps per-card results by.
 */
object CardSupport {
    /**
     * The version of what the engine carries out itself rather than leaving to the players: raised whenever an effect,
     * keyword or step it used to leave to the players ([manualParts], `KeywordMeanings.isManual`) becomes
     * automatic, or the other way round. A host that keeps per-card "fully supported" results worked out with this
     * engine keeps them per this version (and the parser's), so a new engine works them out again.
     */
    const val SUPPORT_VERSION: Int = 12

    /** The parts of [definition] this engine leaves to the players, one short line each (empty: it plays all of the card
     * by the rules): text the parser couldn't read, `Manual` steps and cost parts, and keywords done by hand. The engine doesn't
     * carry those out (a `Manual` step does nothing, a `Manual` cost part can't be paid), so a card with any isn't supported. Follows
     * [SUPPORT_VERSION]: a host keeping per-card results keeps them per that version. */
    fun manualParts(definition: arcanum.api.CardDefinition): List<String> = arcanum.engine.view.ManualParts.of(definition)
}
