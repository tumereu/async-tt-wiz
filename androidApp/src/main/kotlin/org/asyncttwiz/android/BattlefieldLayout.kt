package org.asyncttwiz.android

import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.TypedCounter

/** Which part of a battlefield a permanent lies in (TBL-004): every land in the land row by the player's own edge; the
 * other permanents in the upper band by the middle, creatures from the left and the others from the right. */
sealed interface BattlefieldRow {
    /** The upper band, from the left: creatures and face-down permanents. */
    data object Creatures : BattlefieldRow
    /** The land row, by the player's own edge, from the left. */
    data object Lands : BattlefieldRow
    /** The upper band, from the right: artifacts, enchantments, planeswalkers, battles and any other permanent. */
    data object Others : BattlefieldRow

    companion object {
        /** The land row's index, counted from the player's own edge. */
        const val LAND_ROW = 0
        /** The upper band's row behind the one nearest the middle. */
        const val BAND_BACK = 1
        /** The upper band's row nearest the middle (where the combat band opens between the battlefields, CMB-007). */
        const val BAND_FRONT = 2
        /** The rows of one battlefield: the land row and the upper band's two. */
        const val COUNT = 3

        /** TBL-004: where a permanent with [typeLine] (the type line it has now) lies: every land in the land row, whatever
         * its other types (an artifact land, a land creature, a land an effect made a creature); else every creature on
         * the creature side of the upper band and the other permanents on its other side. A face-down permanent (or one
         * the viewer can't see) lies with the creatures. */
        fun of(typeLine: String?, faceDown: Boolean): BattlefieldRow = when {
            faceDown || typeLine == null -> Creatures
            else -> types(typeLine).let { types ->
                when {
                    "land" in types -> Lands
                    "creature" in types -> Creatures
                    else -> Others
                }
            }
        }

        /** TBL-053: whether a permanent with [typeLine] (the type line it has now, a creature land or an animated land
         * included) is a creature. */
        fun creature(typeLine: String?): Boolean = typeLine != null && "creature" in types(typeLine)

        private fun types(typeLine: String) = typeLine.substringBefore("//").substringBefore('—').substringBefore(" - ")
            .split(' ', '\t').filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    }
}

/**
 * What decides whether two permanents lie in one stack (TBL-053): the same card and face, controller and owner, and the
 * same state that matters for play: counters, damage, face up, in combat or not, a creature's entered-this-turn mark
 * (summoning sickness shown; [entered] is false for any other permanent, which joins its identical stack at once), the
 * P/T and marks as worked out. Tapped or not never splits a stack.
 */
data class StackIdentity(val definition: String, val faceId: String, val token: Boolean, val owner: String,
    val counters: List<TypedCounter>, val damage: String, val corner: String, val marks: List<String>,
    val untilEndOfTurn: Boolean, val entered: Boolean)

/** One battlefield card as the layout reads it: [identity] null when it never stacks (face down, in combat, attached to
 * another card); [attachedTo] the card it lies behind (an Aura, Equipment or a merged mutate creature's other card);
 * [staged] its role while it is staged as an attacker or placed as a blocker being declared (CMB-028, CMB-034). */
data class BattlefieldCard(val id: String, val field: String, val row: BattlefieldRow, val identity: StackIdentity?,
    val attachedTo: String? = null, val tapped: Boolean = false, val staged: CombatRole? = null)

/**
 * A group on a battlefield (TBL-053): a stack of identical permanents, a permanent with the cards attached to it, or one
 * card alone. [members] from the bottom up: a stack's untapped members in arrival order, then its tapped ones in arrival
 * order, on top (owner, 2026-11-08); a host first and then what is attached to it; [staged] the members staged as
 * attackers or placed as blockers while those are declared (they stay in their stack then, CMB-047).
 */
data class BattlefieldGroup(val key: String, val field: String, val row: BattlefieldRow, val members: List<String>,
    val kind: Kind, val staged: Map<String, CombatRole> = emptyMap()) {
    sealed interface Kind {
        data object Single : Kind
        /** Identical permanents: lands fan out, others lie tight with a ×N badge. */
        data object Stack : Kind
        /** A permanent with what is attached to it peeking out above it. */
        data object Attached : Kind
    }

    /** The different things a tap may mean in this group (TBL-122): a stack of identical cards is one, a card with
     * attachments is one per card. */
    val units: List<List<String>> get() = if (kind == Kind.Stack) listOf(members) else members.map { listOf(it) }

    /** The card drawn on top: a stack's last member, an attached group's host. */
    val top: String get() = if (kind == Kind.Stack) members.last() else members.first()

    /** TBL-111: the order a hold inspects the members in: the top card first. */
    val inspectOrder: List<String> get() = if (kind == Kind.Stack) members.reversed() else members
}

/** Where a battlefield card lies: [x] dp (at card size 1) from the left end of its battlefield's rows, [row] the row
 * counted from the player's own edge ([BattlefieldRow.LAND_ROW], [BattlefieldRow.BAND_BACK], [BattlefieldRow.BAND_FRONT]),
 * [lift] how many peek steps above its host an attached card lies, [order] its drawing order on the battlefield (later on
 * top), [copies] the ×N badge it carries (0: none), [staged] its role when it is a stack's staged member drawn at the
 * stack's right end (CMB-047). */
data class BattlefieldSpot(val x: Float, val row: Int, val lift: Int = 0, val order: Int = 0, val copies: Int = 0,
    val group: String = "", val lifts: Int = 0, val staged: CombatRole? = null)

/** TBL-002, TBL-004, TBL-053: the automatic battlefield layout (MTG Arena style): rows, stacks and attachments. */
object BattlefieldLayout {
    /** The room one group takes in a row (a tapped card's width and a gap; TBL-002). */
    const val SLOT = 42f
    /** How far each land of a stack fans out to the right of the one before. */
    const val FAN = 8f
    /** How far each of a tight stack's first members shows to the right of the one before (at most [TIGHT_SHOWN]). */
    const val TIGHT = 3f
    const val TIGHT_SHOWN = 2
    /** How far each attached card peeks out above the one in front of it. */
    const val PEEK = 8f

    /**
     * The groups of [cards] (every battlefield's cards, each battlefield's in arrival order): an attached card joins the
     * group of the card it is attached to (its host's battlefield, wherever it is), a card with attachments is a group of
     * its own, and identical cards of one battlefield and row ([BattlefieldCard.identity]) form one stack, its tapped
     * members on top (TBL-053: whichever way it came together, stacks merging at a turn's end included). Groups are in
     * arrival order (a group arrives with its first card).
     */
    fun groups(cards: List<BattlefieldCard>): List<BattlefieldGroup> {
        val byId = cards.associateBy { it.id }
        fun root(card: BattlefieldCard): BattlefieldCard {
            var at = card
            val seen = mutableSetOf(card.id)
            while (true) {
                val host = at.attachedTo?.let(byId::get) ?: return at
                if (!seen.add(host.id)) return at
                at = host
            }
        }
        val attachments = cards.filter { root(it).id != it.id }.groupBy { root(it).id }
        val groups = mutableListOf<BattlefieldGroup>()
        val stacks = HashMap<Triple<String, BattlefieldRow, StackIdentity>, Int>()
        for (card in cards) {
            if (root(card).id != card.id) continue
            val attached = attachments[card.id].orEmpty()
            if (attached.isNotEmpty()) {
                groups += BattlefieldGroup("host:${card.id}", card.field, card.row, listOf(card.id) + attached.map { it.id },
                    BattlefieldGroup.Kind.Attached)
                continue
            }
            val identity = card.identity
            val key = identity?.let { Triple(card.field, card.row, it) }
            val at = key?.let(stacks::get)
            if (at != null) {
                val stack = groups[at]
                groups[at] = stack.copy(members = stack.members + card.id, kind = BattlefieldGroup.Kind.Stack,
                    staged = card.staged?.let { stack.staged + (card.id to it) } ?: stack.staged)
            } else {
                if (key != null) stacks[key] = groups.size
                groups += BattlefieldGroup("card:${card.id}", card.field, card.row, listOf(card.id), BattlefieldGroup.Kind.Single,
                    card.staged?.let { mapOf(card.id to it) }.orEmpty())
            }
        }
        // A stack is known by its first arrival, whichever joins or leaves later
        return groups.map { group -> if (group.kind != BattlefieldGroup.Kind.Stack) group else group.copy(key = "stack:${group.members.first()}",
            members = group.members.sortedBy { byId[it]?.tapped == true }) }
    }

    /** The width [group] takes in its row, [expanded] or not: a stack with staged members a slot more for each (CMB-047). */
    fun width(group: BattlefieldGroup, expanded: Boolean): Float {
        val n = group.members.size
        return when {
            expanded && n > 1 -> n * SLOT
            group.kind == BattlefieldGroup.Kind.Stack && group.row == BattlefieldRow.Lands -> SLOT + (n - 1) * FAN + stagedWidth(group)
            group.kind == BattlefieldGroup.Kind.Stack -> SLOT + minOf(n - 1, TIGHT_SHOWN) * TIGHT + stagedWidth(group)
            else -> SLOT
        }
    }

    private fun stagedWidth(group: BattlefieldGroup) = group.staged.size * SLOT

    /** TBL-004: the small margin always kept between the upper band's creatures and its other permanents. */
    const val MARGIN = SLOT / 2

    /**
     * Where each card of one battlefield's [groups] lies (TBL-004): lands from the left in the land row; in the upper
     * band creatures from the left and the other permanents from its right end, each side filling two-slot columns in
     * arrival order (the slot in the row nearest the middle first, then the one behind it, then the next column inward). A
     * column is as wide as its wider group; the creatures' columns align their groups to the left, the others' to the
     * right. The right end is [rowWidth] dp from the left end, or further when the creatures need the room: [MARGIN] always
     * separates the two sides (nothing shrinks to fit; the table pans, SYS-041). The group [expanded] lies with its
     * members side by side (TBL-122).
     */
    fun place(groups: List<BattlefieldGroup>, rowWidth: Float, expanded: String? = null): Map<String, BattlefieldSpot> {
        val spots = LinkedHashMap<String, BattlefieldSpot>()
        var order = 0
        fun put(group: BattlefieldGroup, left: Float, row: Int) {
            val open = group.key == expanded && group.members.size > 1
            val lifts = if (open || group.kind != BattlefieldGroup.Kind.Attached) 0 else group.members.size - 1
            // Behind first: attached cards (the highest peek first), then the host; a stack from its first arrival up
            val drawn = if (!open && group.kind == BattlefieldGroup.Kind.Attached) group.members.drop(1).reversed() + group.members.first()
                else group.members
            // CMB-047 (owner playtest 2026-11-07): while attackers or blockers are declared a stack's staged members lie at its
            // right end, side by side in the order they are taken (the topmost first); the others stay where they were
            val staged = if (open || group.kind != BattlefieldGroup.Kind.Stack) emptyList()
                else group.members.asReversed().filter { it in group.staged }
            val unstaged = group.members.filter { it !in staged }
            val stackWidth = width(group, false) - stagedWidth(group)
            for (id in drawn) {
                val i = group.members.indexOf(id)
                val x = when {
                    open -> i * SLOT
                    id in staged -> stackWidth + staged.indexOf(id) * SLOT
                    group.kind == BattlefieldGroup.Kind.Stack && group.row == BattlefieldRow.Lands -> i * FAN
                    group.kind == BattlefieldGroup.Kind.Stack -> minOf(i, TIGHT_SHOWN) * TIGHT
                    else -> 0f
                }
                val lift = if (open || group.kind != BattlefieldGroup.Kind.Attached) 0 else i
                // The tight stack's ×N on its top card (of those not staged)
                val copies = if (!open && group.kind == BattlefieldGroup.Kind.Stack && group.row != BattlefieldRow.Lands &&
                    id == unstaged.lastOrNull() && unstaged.size > 1) unstaged.size else 0
                spots[id] = BattlefieldSpot(left + x, row, lift, order++, copies, group.key, lifts,
                    if (id in staged) group.staged[id] else null)
            }
        }
        fun width(group: BattlefieldGroup) = width(group, group.key == expanded)
        var left = 0f
        for (group in groups.filter { it.row == BattlefieldRow.Lands }) { put(group, left, BattlefieldRow.LAND_ROW); left += width(group) }
        // The upper band's columns: two groups each, the first in the row nearest the middle
        val creatures = groups.filter { it.row == BattlefieldRow.Creatures }.chunked(2)
        val others = groups.filter { it.row == BattlefieldRow.Others }.chunked(2)
        fun columnWidth(column: List<BattlefieldGroup>) = column.maxOf { width(it) }
        val creaturesWidth = creatures.sumOf { columnWidth(it).toDouble() }.toFloat()
        val othersWidth = others.sumOf { columnWidth(it).toDouble() }.toFloat()
        fun row(n: Int) = if (n == 0) BattlefieldRow.BAND_FRONT else BattlefieldRow.BAND_BACK
        var x = 0f
        for (column in creatures) {
            column.forEachIndexed { n, group -> put(group, x, row(n)) }
            x += columnWidth(column)
        }
        var right = maxOf(rowWidth, creaturesWidth + (if (creatures.isNotEmpty() && others.isNotEmpty()) MARGIN else 0f) + othersWidth)
        for (column in others) {
            column.forEachIndexed { n, group -> put(group, right - width(group), row(n)) }
            right -= columnWidth(column)
        }
        return spots
    }

    /** The groups of [play]'s battlefields as [viewer]'s table lays them out (TBL-053), its computed values as far as known. */
    fun groups(play: TableBoard, viewer: String): List<BattlefieldGroup> = groups(cards(play.board,
        play.definitions.associate { it.digest to it.definition }, AutomatumBoards.compute(play, viewer)))

    /** The most peek steps any group of [groups] lifts its attached cards by (the rows grow by that much, TBL-003). */
    fun lifts(groups: List<BattlefieldGroup>): Int =
        groups.filter { it.kind == BattlefieldGroup.Kind.Attached }.maxOfOrNull { it.members.size - 1 } ?: 0

    /**
     * Every battlefield card of [board] as the layout reads it, each battlefield's in arrival order (its container's,
     * timestamp order): its row by the type line it has now ([computed]'s when an effect changed it, else its face's),
     * what it is attached to (a card on a battlefield) and its [StackIdentity]. A face-down card never stacks (two backs
     * lying together would tell the other player they are the same card), nor one in combat or attached to another; but
     * while attackers or blockers are declared (nobody holds priority) a staged attacker or placed blocker stays in its
     * stack, marked [BattlefieldCard.staged] (CMB-047, owner playtest 2026-11-07).
     */
    fun cards(board: org.asyncttwiz.protocol.BoardState, definitions: Map<String, org.asyncttwiz.protocol.CardDefinition>,
        computed: org.asyncttwiz.automatum.ComputedBoard): List<BattlefieldCard> {
        val byId = board.cards.associateBy { it.id }
        val fields = board.containers.filter { it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD }
        val onField = fields.flatMapTo(HashSet()) { it.cards }
        val inCombat = org.asyncttwiz.protocol.CombatStackRules.occurrences(board.combat).mapTo(HashSet()) { it.sourceId }
        val staged = staged(board)
        return fields.flatMap { field -> field.cards.mapNotNull { id -> byId[id]?.let { card ->
            val face = definitions[card.definition]?.faces?.firstOrNull { it.faceId == card.faceId }
            val auto = computed.cards[card.id]
            val hidden = card.isFaceDown
            val host = (card.attachedTo as? org.asyncttwiz.protocol.MtgRef.Card)?.cardId?.takeIf { it in onField && it != card.id }
            val typeLine = auto?.typeLine ?: face?.typeLine
            val identity = if (hidden || host != null || card.id in inCombat && card.id !in staged) null else StackIdentity(card.definition, card.faceId,
                card.token, card.owner, card.typedCounters, card.damage, auto?.corner ?: card.corner, auto?.marks.orEmpty(),
                auto?.untilEndOfTurn == true, org.asyncttwiz.protocol.EnteredTurn.marked(board, card) && BattlefieldRow.creature(typeLine))
            BattlefieldCard(card.id, field.id, BattlefieldRow.of(typeLine, hidden), identity, host, card.tapped,
                staged[card.id])
        } } }
    }

    /** CMB-047: the cards of [board] staged as attackers (in the declare attackers step) or placed as blockers (in the declare
     * blockers step) while they are declared (nobody holds priority), by card. */
    fun staged(board: org.asyncttwiz.protocol.BoardState): Map<String, CombatRole> {
        if (board.priority?.holder != null) return emptyMap()
        val role = when (org.asyncttwiz.protocol.TurnPosition.of(board.turn)) {
            org.asyncttwiz.protocol.TurnPosition.DECLARE_ATTACKERS -> CombatRole.ATTACKER
            org.asyncttwiz.protocol.TurnPosition.DECLARE_BLOCKERS -> CombatRole.BLOCKER
            else -> return emptyMap()
        }
        return board.combat?.pairs.orEmpty().flatMap { it.side(role) }.associate { it.sourceId to role }
    }
}

/** The glows the table draws (TBL-117): [playable] the cyan glow's strength by card, [targets] the legal targets framed in
 * violet, [attack] the creatures that may attack glowing red-orange (CMB-028), [expand] the strength of the glow of the
 * groups a tap opens (white), [picked] the targets picked so far, drawn picked (STK-047). */
data class TableGlows(val playable: Map<String, Float>, val targets: Set<String>, val expand: Map<String, Float> = emptyMap(),
    val attack: Set<String> = emptySet(), val picked: Set<String> = emptySet()) {
    companion object {
        /**
         * TBL-117 for groups: a collapsed group of several ([taps]: what a tap on each does) glows as a whole when any member
         * glows: in the usual colours when the tap acts at once (every member as strong as its strongest; a legal target
         * framed whole, a stack that may attack red-orange whole), in the expand colour when the tap opens it; one holding a
         * [picked] target shows picked as a whole (STK-047). Cards alone and the opened group's members glow as themselves:
         * a legal target framed, a picked one drawn picked, exactly as a card alone.
         */
        fun of(groups: List<BattlefieldGroup>, taps: Map<String, GroupTap>, playable: Map<String, Float>, targets: Set<String>,
            attack: Set<String> = emptySet(), picked: Set<String> = emptySet()): TableGlows {
            if (taps.isEmpty()) return TableGlows(playable, targets, attack = attack, picked = picked)
            val cyan = playable.toMutableMap()
            val violet = targets.toMutableSet()
            val orange = attack.toMutableSet()
            val white = HashMap<String, Float>()
            val chosen = picked.toMutableSet()
            for (group in groups) {
                val tap = taps[group.key] ?: continue
                if (group.members.any { it in picked }) chosen += group.members
                val strength = group.members.maxOf { playable[it] ?: 0f }
                val target = group.members.any { it in targets }
                val attacks = group.members.any { it in attack }
                if (strength <= 0f && !target && !attacks) continue
                if (tap == GroupTap.Expand) for (id in group.members) {
                    white[id] = if (target || attacks) 1f else strength; cyan -= id; violet -= id; orange -= id
                } else {
                    if (strength > 0f) for (id in group.members) cyan[id] = strength
                    if (target && tap is GroupTap.Act) violet += group.members
                    if (attacks && tap is GroupTap.Act) orange += group.members
                }
            }
            return TableGlows(cyan, violet, white, orange, chosen)
        }
    }
}

/** What a tap on a collapsed group does (TBL-122). */
sealed interface GroupTap {
    /** Nothing in it is usable now: the tap goes to its top card as to a card alone (its menu). */
    data object Top : GroupTap
    /** It can mean only one thing: act on [cardId] at once. */
    data class Act(val cardId: String) : GroupTap
    /** It could mean several things: the first tap opens the group. */
    data object Expand : GroupTap

    companion object {
        /**
         * TBL-122: a tap on [group] when [usable] tells which cards can do what the tap would do now (activate, one-tap mana,
         * stage an attacker, pick a blocker, be picked as a target): with one of its [BattlefieldGroup.units] usable it
         * acts on it at once (in a stack, on its topmost usable member, an untapped one first, [tapped] telling; owner
         * playtest 2026-11-07), with several it expands,
         * with none it is the top card's tap. With [alwaysOpen] (TBL-130, owner 2026-11-07: while targets are picked) a
         * group of several with anything usable in it always opens, so each member can be picked by a tap of its own.
         */
        fun of(group: BattlefieldGroup, usable: (String) -> Boolean, tapped: (String) -> Boolean,
            alwaysOpen: Boolean = false): GroupTap {
            val units = group.units.map { unit -> unit.filter(usable) }.filter { it.isNotEmpty() }
            return when {
                units.isEmpty() -> Top
                alwaysOpen && group.members.size > 1 -> Expand
                units.size == 1 -> Act(units.single().let { members -> members.lastOrNull { !tapped(it) } ?: members.last() })
                else -> Expand
            }
        }
    }
}
