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
    private val WAS_BARGAINED = Regex("""(.+?)(?: was|'s) bargained""") // woe chunk 04: "if it's bargained"
    /** "from anywhere": the ability says it functions in every zone it could be put into a graveyard from (CR 113.6b). */
    private val FROM_ANYWHERE = listOf<arcanum.api.Zone>(arcanum.api.Zone.Battlefield, arcanum.api.Zone.Hand, arcanum.api.Zone.Library, arcanum.api.Zone.Exile,
        arcanum.api.Zone.Stack, arcanum.api.Zone.Command)
    private val SELF_TO_GRAVEYARD_EXILE_INSTEAD = Regex("""If (.+) would be put into a graveyard from anywhere, exile it instead\.""")
    // fdn chunk 07: "If Progenitus would be put into a graveyard from anywhere, reveal Progenitus and shuffle it into its owner's library instead."
    private val SELF_TO_GRAVEYARD_SHUFFLE_INSTEAD = Regex("""If (.+) would be put into a graveyard from anywhere, reveal (.+) and shuffle it into its owner's library instead\.""")
    private val CHOSE_OTHER_RING_BEARER = Regex("""you chose a creature other than (.+) as your Ring-bearer""")

    /** Trigger events ("the Ring tempts you", "the creature it haunts dies"). */
    fun event(text: String): EventPattern? = when (text) {
        // CR 701.54d
        "the Ring tempts you" -> EventPattern.RingTempts(PlayerFilter.You)
        // CR 701.54a, d: the creature chosen as the Ring tempts you is the event's object; none chosen, it doesn't count
        "you choose a creature as your Ring-bearer" -> EventPattern.RingTempts(PlayerFilter.You, chosen = true)
        // CR 309.7
        "you complete a dungeon" -> EventPattern.CompletesDungeon(PlayerFilter.You)
        // CR 702.55b, c
        "the creature it haunts dies", "the creature this card haunts dies" -> hauntedDies
        else -> null
    }

    private val MUTATES = Regex("""(.+) mutates""")

    /** Trigger events with a subject ("this creature mutates", CR 702.140d); [subject] reads it. */
    fun subjectEvent(text: String, subject: (String) -> ObjectFilter?): EventPattern? {
        MUTATES.matchEntire(text)?.let { m -> return EventPattern.Mutates(subject(m.groupValues[1]) ?: return null) }
        return null
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
        // CR 702.166b: "if this spell was bargained", "if it was bargained" (a permanent's enters trigger), "if it's bargained" (a cost
        // reduction, CR 601.2f)
        WAS_BARGAINED.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] == "this spell" || isSelf(it.groupValues[1], self) }?.let {
            return Condition.Paid(Keyword.Bargain, times = 1, of = Objects.Self)
        }
        // CR 702.104b: "if tribute wasn't paid" (the opponent chose not to put the counters on it)
        if (text == "tribute wasn't paid")
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(arcanum.api.KeywordExpansions.TRIBUTE_PAID))))
        if (text == "you don't control a Ring-bearer") return Condition.Not(Condition.Exists(yourRingBearer, 1))
        // CR 309.7
        if (text == "you've completed a dungeon") return Condition.Compare(Value.DungeonsCompleted(arcanum.api.Players.You),
            arcanum.api.Comparison.AtLeast, Value.Fixed(1))
        if (text == "you control a Ring-bearer") return Condition.Exists(yourRingBearer, 1)
        // CR 701.37b: "As long as this creature is monstrous, …"; otj chunk 03: "if this creature is saddled" / "isn't saddled" (CR 702.171b)
        IS_MONSTROUS.matchEntire(text)?.takeIf { isSelf(it.groupValues[1], self) || it.groupValues[1] == "it" }?.let {
            val mark = if (it.groupValues[3] == "monstrous") ObjectMark.Monstrous else ObjectMark.Saddled
            val matches = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Marked(mark)))
            return if (it.groupValues[2] == "is") matches else Condition.Not(matches)
        }
        return null
    }

    private val PUT_COUNTERS_ON_RING_BEARER = Regex("""Put (a|an|one|two|three) (\S+) counters? on your Ring-bearer\.""")

    /** Effect sentences ("The Ring tempts you."). */
    fun effect(sentence: String): List<Step>? {
        // CR 701.54e: "Put a +1/+1 counter on your Ring-bearer." (none when you don't control one)
        PUT_COUNTERS_ON_RING_BEARER.matchEntire(sentence)?.let { m ->
            return listOf(Step.Do(Effect.PutCounters(Objects.Each(yourRingBearer), Counters.kind(m.groupValues[2]) ?: return null,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))))
        }
        return wholeEffect(sentence)
    }

    private fun wholeEffect(sentence: String): List<Step>? = when (sentence) {
        // CR 701.54a
        "The Ring tempts you." -> listOf(Step.Do(Effect.Action(KeywordAction.TheRingTemptsYou)))
        // CR 701.49a–d
        "Venture into the dungeon." -> listOf(Step.Do(Effect.Action(KeywordAction.Venture)))
        "Venture into Undercity." -> listOf(Step.Do(Effect.Action(KeywordAction.VentureInto(undercity))))
        // CR 726.1, 726.3
        "You take the initiative." -> listOf(Step.Do(Effect.GiveDesignation(arcanum.api.Players.You, arcanum.api.Designation.Initiative)))
        else -> null
    }

    /** "Undercity" (CR 205.3p, 701.49d). */
    private val undercity: ObjectFilter = ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Undercity")))

    // dungeons: the room effects of the dungeon cards (read anywhere, `Sentences.dungeonEffect`)
    /** "Draw three cards and reveal them." */
    val DRAW_AND_REVEAL = Regex("""Draw (\w+) cards and reveal them\.""")
    /** "You may cast one of them without paying its mana cost." (one of the cards "them" names) */
    val MAY_CAST_ONE_OF_THEM_FREE = Regex("""You may cast one of them without paying its mana cost\.""")
    /** "Put a creature card from among them onto the battlefield with three +1/+1 counters on it." (exactly one, if any) */
    val PUT_ONE_FROM_AMONG_ONTO_BATTLEFIELD = Regex("""Put an? (.+?) from among them onto the battlefield(?: with (\w+) (\S+) counters? on it)?\.""")
    /** "Sacrifice a creature, an artifact, and a land." (one object of each kind, sacrificed at once) */
    val SACRIFICE_LIST = Regex("""Sacrifice (an? [^,]+(?:, an? [^,]+)*,? and an? [^,]+)\.""")
    /** "Discard a card and sacrifice a creature, an artifact, and a land." (one action, then the other) */
    val DISCARD_AND_SACRIFICE = Regex("""(Discard .+?) and (sacrifice .+)\.""")

    private val ROOM = Regex("""(.+?) — (.+?)(?:\s*\((?:→|Leads to:) (.+?)\))?""")
    private val ONLY_INTO = Regex("""You can't enter this dungeon unless you ["“]venture into ([A-Z][a-z]+)\.["”]""")

    /** A dungeon card's text: its [rooms] and its restriction "You can't enter this dungeon unless you "venture into [quality]."" ([only],
     * CR 701.49d), as a static ability. */
    data class DungeonText(val rooms: List<arcanum.api.DungeonRoom>, val only: Ability?)

    /**
     * A dungeon card's rooms (CR 309.4): one line each, "[name] — [effect] (→ [room] or [room])" (card data also writes the arrows
     * "(Leads to: [room], [room])"), the topmost first; the arrows name the rooms they lead to. A first line "You can't enter this
     * dungeon unless you "venture into [quality]."" restricts how it is entered (CR 701.49d). [effect] reads a room's effect. Null when
     * another line isn't a room or a room's effect isn't read (the dungeon stays unread).
     */
    fun rooms(text: String, effect: (String) -> arcanum.api.Instructions?): DungeonText? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val only = lines.firstOrNull()?.let(ONLY_INTO::matchEntire)?.let { m ->
            Ability.Static(listOf(arcanum.api.Mechanic.OnlyVentureInto(ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype(m.groupValues[1]))))),
                arcanum.api.Affected.Objects(arcanum.api.Objects.Self), functionsIn = listOf(arcanum.api.Zone.Outside), text = lines.first())
        }
        val parsed = lines.drop(if (only != null) 1 else 0).map { ROOM.matchEntire(it) ?: return null }
        val names = parsed.map { it.groupValues[1] }
        return DungeonText(parsed.map { m ->
            val next = m.groupValues[3].takeIf { it.isNotEmpty() }?.split(" or ", ", ")
                ?.map { n -> names.indexOf(n.trim()).takeIf { it >= 0 } ?: return null }.orEmpty()
            val text = m.groupValues[2].trim()
            arcanum.api.DungeonRoom(m.groupValues[1], effect(text) ?: return null, next)
        }, only)
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
                modify = listOf(arcanum.api.Modification.ChangeDestination(arcanum.api.Destination(arcanum.api.Zone.Exile))))), affects = Affected.Objects(Objects.Self),
                functionsIn = FROM_ANYWHERE)
        }
        // fdn chunk 07: the card goes into its owner's library and that library is shuffled in place of the graveyard (CR 614.1a, 701.24)
        SELF_TO_GRAVEYARD_SHUFFLE_INSTEAD.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) && isSelf(it.groupValues[2]) }?.let {
            val self = Objects.Self
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(self), to = arcanum.api.Zone.Graveyard),
                instead = arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.Reveal(self)),
                    arcanum.api.Step.Do(arcanum.api.Effect.Move(self, arcanum.api.Destination(arcanum.api.Zone.Library))),
                    arcanum.api.Step.Do(arcanum.api.Effect.Shuffle(arcanum.api.Players.OwnerOf(self))))))), affects = Affected.Objects(self),
                functionsIn = FROM_ANYWHERE)
        }
        val m = PAIRED.matchEntire(line)?.takeIf { isSelf(it.groupValues[1]) } ?: return null
        val what = m.groupValues[3]
        val quoted = QUOTED.matchEntire(what)
        val mechanic = when {
            m.groupValues[2].startsWith("get") -> PT.matchEntire(what)?.let { pt ->
                Mechanic.ModifyPowerToughness(Value.Fixed(pt.groupValues[1].toInt()), Value.Fixed(pt.groupValues[2].toInt()))
            }
            quoted != null -> granted(quoted.groupValues[1])?.let { Mechanic.GainAbilities(listOf(it)) }
            else -> Mechanic.GainAbilities(what.split(LazyRegexes.AND_LIST).map { keyword(it) ?: return null })
        } ?: return null
        return Ability.Static(listOf(mechanic), affects = Affected.Objects(Objects.Union(listOf(Objects.Self, Objects.PairedWith(Objects.Self)))),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Paired))
    }

    private fun isSelf(name: String, self: Set<String>): Boolean = name in self || name.replaceFirstChar { it.lowercase() } in self

    // ---- Mechanics the engine plays since its lanes 1–4 (read by the `mechanic*` functions of `Sentences`)

    /** "this creature is monstrous" (CR 701.37b); "this creature is saddled", "isn't saddled" (CR 702.171b). */
    val IS_MONSTROUS = Regex("""(.+) (is|isn't) (monstrous|saddled)""")

    /** "You may exert this creature as it attacks. When you do, [effect]" (CR 701.43d, 508.1g). */
    val MAY_EXERT_AS_ATTACKS = Regex("""You may exert (.+?) as (?:it|he|she|they) attacks?\.(?: When you do, (.+))?""")
    /** "Exert this creature" as a cost (CR 701.43a). */
    val EXERT_COST = Regex("""[Ee]xert (.+)""")

    /** "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you." (CR 508.1d, h) */
    val CANT_ATTACK_YOU_UNLESS_PAYS_EACH_ATTACKING = Regex(
        """(.+?) can't attack you(?: or (planeswalkers you control))? unless their controller pays (\{[^}]+\}) for each creature they control that's attacking you(?: or a planeswalker you control)?(?:, where X is (.+))?\.""")
    /** "Enchanted creature can't attack unless its controller pays {3}.", "… can't attack or block unless …" (CR 508.1d, 509.1d) */
    val CANT_ATTACK_UNLESS_ITS_CONTROLLER_PAYS = Regex("""(.+?) can't (attack|block|attack or block) unless its controller pays (\{[^}]+\})\.""")
    /** "This creature can't attack unless you sacrifice a land." (CR 508.1h: a cost other than mana) */
    val SELF_CANT_ATTACK_UNLESS_YOU = Regex("""(.+?) can't (attack|block) unless you (sacrifice .+|return .+|pay .+|discard .+|exile .+|tap .+)\.""")
    /** "Black creatures can't attack unless their controller sacrifices a land of their choice for each black creature they control
     * that's attacking." */
    val CANT_ATTACK_UNLESS_CONTROLLER_SACRIFICES = Regex(
        """(.+?) can't attack unless their controller sacrifices (an? .+?)(?: of their choice)? for each (.+?) they control that's attacking\.""")
    /** "Creatures can't block unless their controller pays {1} for each of those creatures." / "… for each blocking creature they
     * control." (CR 509.1d) */
    val CANT_BLOCK_UNLESS_PAYS = Regex("""(.+?) can't block unless their controller pays (\{[^}]+\}|\d+ life) for each (?:of those creatures|blocking creature they control)\.""")

    /** "Any time you could mulligan and this card is in your hand, you may [effect]." (CR 103.5b) */
    val ANY_TIME_YOU_COULD_MULLIGAN = Regex("""Any time you could mulligan and (.+?) is in your hand, you may (.+)""")
    /** "Your starting hand size is increased by one." (CR 103.5) */
    val STARTING_HAND_SIZE = Regex("""Your starting hand size is (increased|reduced|decreased) by (\w+)\.""")

    /** "Enchanted creature is a copy of the chosen creature.", "Other creatures you control are copies of enchanted creature."
     * (CR 613.2a, 707.2) */
    val IS_A_COPY_OF = Regex("""(.+?) (?:is a copy|are copies) of (.+)\.""")

    /** Keyword actions (CR 701). */
    // lci chunk 03: "it explores X times", "That creature explores, then it explores again." (each a separate explore, CR 701.44b)
    val EXPLORES = Regex("""(.+?) explores(?:, then it explores (again)| (X|\w+) times)?\.""")
    val MONSTROSITY = Regex("""Monstrosity (\d+|X)\.""")
    val ADAPT = Regex("""Adapt (\d+|X)\.""")
    val SUPPORT = Regex("""Support (\d+|X)\.""")
    // lci chunk 01: "Then that player discovers X." (another player discovers, CR 701.57a)
    val DISCOVER = Regex("""(?:You |(?:Then )?([Tt]hat player|[Tt]arget player) )?[Dd]iscovers? (\d+|X)\.""")
    val MANIFEST_TOP = Regex("""(?:You )?[Mm]anifest the top (card|two cards|three cards) of your library\.""")
    val CLOAK_TOP = Regex("""(?:You )?[Cc]loak the top (card|two cards|three cards) of (your|that player's) library\.""") // mkm chunk 07: that player's
    /** "all creatures your opponents control", "each Merfolk creature you control" */
    val ALL_NOUN = Regex("""(?:[Aa]ll|[Ee]ach) (.+)""")
    val GOAD = Regex("""Goad (.+)\.""")
    val EXILE_HAND_DRAW_THAT_MANY = Regex("""Exile all (?:the )?cards from your hand, then draw that many cards\.""")
}
