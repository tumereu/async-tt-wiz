package arcanum.parser

/**
 * Costs that read the spell's targets (CR 601.2f), a wording group added with the castability work (for the owner's review): what the cost
 * of a spell of any number of targets comes to as they are chosen. The first reads as strive's template, the others are merged from it.
 */
internal object CostPerTargetWordings {
    /** "This spell costs {1} less to cast for each target creature." */
    val LESS_FOR_EACH_TARGET = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast for each target (\w+)\.""")
    /** "This spell costs {1}{R} more to cast for each target creature beyond the first." */
    val MORE_FOR_EACH_TARGET_BEYOND_THE_FIRST = Regex("""This spell costs ((?:\{[^}]+\})+) more to cast for each target (\w+) beyond the first\.""")
    /** "This spell costs {B}{B} more to cast for each creature type among target creatures." */
    val MORE_FOR_EACH_TYPE_AMONG_TARGETS = Regex("""This spell costs ((?:\{[^}]+\})+) more to cast for each creature type among target (\w+)s\.""")
    /** "Spells your opponents cast that target this creature cost {2} more to cast." */
    val OPPONENTS_SPELLS_TARGETING_THIS_COST_MORE = Regex("""Spells your opponents cast that target (.+?) cost ((?:\{[^}]+\})+) more to cast\.""")
}
