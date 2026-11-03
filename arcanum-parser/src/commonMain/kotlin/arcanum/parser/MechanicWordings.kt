package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Condition
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Value
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.KeywordAction
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Status
import arcanum.api.Step

/**
 * Wordings of mechanics that used to be done by hand (the Ring tempting you, CR 701.54; …), read wherever the general rules
 * look for events, conditions and effects. Each wording is read whole; anything else falls through.
 */
internal object MechanicWordings {
    /** "your Ring-bearer" (CR 701.54e): a creature you control with the Ring-bearer designation. */
    val yourRingBearer: ObjectFilter = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Marked(ObjectMark.RingBearer)),
        ObjectFilter.ControlledBy(PlayerFilter.You)))

    private val IS_YOUR_RING_BEARER = Regex("""(.+) is your Ring-bearer""")
    private val CHOSE_OTHER_RING_BEARER = Regex("""you chose a creature other than (.+) as your Ring-bearer""")

    /** Trigger events ("the Ring tempts you"). */
    fun event(text: String): EventPattern? = when (text) {
        // CR 701.54d
        "the Ring tempts you" -> EventPattern.RingTempts(PlayerFilter.You)
        else -> null
    }

    /** Conditions ("Frodo is your Ring-bearer", "you don't control a Ring-bearer", "you chose a creature other than Faramir as your
     * Ring-bearer" in a "whenever the Ring tempts you" trigger). */
    fun condition(text: String, self: Set<String>): Condition? {
        IS_YOUR_RING_BEARER.matchEntire(text)?.takeIf { isSelf(it.groupValues[1], self) }?.let {
            return Condition.ObjectMatches(Objects.Self, yourRingBearer)
        }
        // CR 701.54d: the creature chosen is the event's object; none was chosen when the player controlled no creature
        CHOSE_OTHER_RING_BEARER.matchEntire(text)?.takeIf { isSelf(it.groupValues[1], self) }?.let {
            return Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))
        }
        if (text == "you don't control a Ring-bearer") return Condition.Not(Condition.Exists(yourRingBearer, 1))
        if (text == "you control a Ring-bearer") return Condition.Exists(yourRingBearer, 1)
        return null
    }

    /** Effect sentences ("The Ring tempts you."). */
    fun effect(sentence: String): List<Step>? = when (sentence) {
        // CR 701.54a
        "The Ring tempts you." -> listOf(Step.Do(Effect.Action(KeywordAction.TheRingTemptsYou)))
        else -> null
    }

    private val PAIRED = Regex("""As long as (.+) is paired with another creature, (?:both creatures|each of those creatures) (have|has|gets?) (.+?)\.?""")
    private val PT = Regex("""([+-]\d+)/([+-]\d+)""")
    private val QUOTED = Regex("\"(.+)\"")

    /**
     * Static abilities: soulbond's "As long as [this] is paired with another creature, both creatures have [keywords]" / "each of those
     * creatures gets +N/+N" / "each of those creatures has "[ability]"" (CR 702.95b): the creature and the one it's paired with, while
     * it is paired. [isSelf] says whether a name is this object's; [keyword] reads one keyword, [granted] a quoted ability.
     */
    fun static(line: String, isSelf: (String) -> Boolean, keyword: (String) -> Keyword?, granted: (String) -> Ability?): Ability? {
        val m = PAIRED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) } ?: return null
        val what = m.groupValues[3]
        val quoted = QUOTED.matchEntire(what)
        val mechanic = when {
            m.groupValues[2].startsWith("get") -> PT.matchEntire(what)?.let { pt ->
                Mechanic.ModifyPowerToughness(Value.Fixed(pt.groupValues[1].toInt()), Value.Fixed(pt.groupValues[2].toInt()))
            }
            quoted != null -> granted(quoted.groupValues[1])?.let { Mechanic.GainAbilities(listOf(it)) }
            else -> Mechanic.GainAbilities(what.split(Regex(""",? and |, """)).map { keyword(it) ?: return null })
        } ?: return null
        return Ability.Static(listOf(mechanic), affects = Affected.Objects(Objects.Union(listOf(Objects.Self, Objects.PairedWith(Objects.Self)))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Paired))
    }

    private fun isSelf(name: String, self: Set<String>): Boolean = name in self || name.replaceFirstChar { it.lowercase() } in self
}
