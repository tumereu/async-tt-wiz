package arcanum.parser

import arcanum.api.Condition
import arcanum.api.Objects

/** Wordings first met in Ravnica Remastered (RVR). */
internal object Rvr {
    // rvr chunk 01: Unbreakable Formation (addendum)
    val IF_COUNTER_ON_EACH_OF_THOSE_AND_THEY_GAIN = Regex("""If (.+?), put an? (\S+) counter on each of those creatures and they gain ([a-z ,]+) until end of turn\.""")
}

/** RVR phrases read by the shared readers. */
internal object RvrPhrases {
    /** Conditions. */
    fun condition(text: String, self: Set<String>): Condition? {
        // rvr chunk 01: addendum, "If you cast this spell during your main phase" (CR 207.2c, 505.1)
        if (text == "you cast this spell during your main phase") return Condition.CastDuringMainPhase(Objects.Self)
        return null
    }
}
