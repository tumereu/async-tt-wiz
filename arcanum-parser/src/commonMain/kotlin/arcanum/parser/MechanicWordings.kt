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
    private val WAS_BARGAINED = Regex("""(.+) was bargained""")
    private val SELF_TO_GRAVEYARD_EXILE_INSTEAD = Regex("""If (.+) would be put into a graveyard from anywhere, exile it instead\.""")
    private val CHOSE_OTHER_RING_BEARER = Regex("""you chose a creature other than (.+) as your Ring-bearer""")

    /** Trigger events ("the Ring tempts you", "the creature it haunts dies"). */
    fun event(text: String): EventPattern? = when (text) {
        // CR 701.54d
        "the Ring tempts you" -> EventPattern.RingTempts(PlayerFilter.You)
        // CR 702.55b, c
        "the creature it haunts dies", "the creature this card haunts dies" -> hauntedDies
        else -> null
    }

    /** "the creature it haunts dies" (CR 702.55b): the creature the haunt ability targeted, kept by the exiled card. */
    val hauntedDies: EventPattern = EventPattern.ZoneChange(ObjectFilter.Is(Objects.LinkedTo(Keyword.Haunt)), from = arcanum.api.Zone.Battlefield,
        to = arcanum.api.Zone.Graveyard)

    private val ENTERS_OR_HAUNTED_DIES = Regex("""(.+) enters or the creature it haunts dies""")

    /** Several events of one trigger ("this creature enters or the creature it haunts dies"). */
    fun events(text: String, self: Set<String>): List<EventPattern>? {
        ENTERS_OR_HAUNTED_DIES.matchEntire(text)?.takeIf { isSelf(it.groupValues[1], self) }?.let {
            return listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = arcanum.api.Zone.Battlefield), hauntedDies)
        }
        return null
    }

    /** CR 702.55c: a triggered ability that refers to the creature its card haunts can trigger in exile (and also on the battlefield when
     * it has another event there). */
    fun haunting(ability: Ability?): Ability? {
        val triggered = ability as? Ability.Triggered ?: return ability
        val events = (triggered.trigger as? arcanum.api.Trigger.On)?.events ?: return ability
        if (hauntedDies !in events) return ability
        val zones = if (events.all { it == hauntedDies }) listOf(arcanum.api.Zone.Exile) else listOf(arcanum.api.Zone.Battlefield, arcanum.api.Zone.Exile)
        return triggered.copy(functionsIn = zones)
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
        // CR 702.166b: "if this spell was bargained", "if it was bargained" (a permanent's enters trigger)
        WAS_BARGAINED.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] == "this spell" || isSelf(it.groupValues[1], self) }?.let {
            return Condition.Paid(Keyword.Bargain, times = 1, of = Objects.Self)
        }
        // CR 702.104b: "if tribute wasn't paid" (the opponent chose not to put the counters on it)
        if (text == "tribute wasn't paid")
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(arcanum.api.KeywordExpansions.TRIBUTE_PAID))))
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
        // CR 614.1a: "If [this] would be put into a graveyard from anywhere, exile it instead." (a disturb back face, CR 702.146b)
        SELF_TO_GRAVEYARD_EXILE_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = arcanum.api.Zone.Graveyard),
                modify = listOf(arcanum.api.Modification.ChangeDestination(arcanum.api.Destination(arcanum.api.Zone.Exile))))), affects = Affected.Objects(Objects.Self))
        }
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
