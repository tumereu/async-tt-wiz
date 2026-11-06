package org.asyncttwiz.android

import org.asyncttwiz.protocol.TypedCounter

/** Which row of a battlefield a permanent lies in (TBL-004): creatures nearest the middle, lands and the other permanents
 * in the back row by the player's own edge, lands from the left and the others from the right. */
sealed interface BattlefieldRow {
    /** The row nearest the middle (the combat side). */
    data object Creatures : BattlefieldRow
    /** The back row, from the left. */
    data object Lands : BattlefieldRow
    /** The back row, from the right: artifacts, enchantments, planeswalkers, battles and any other permanent. */
    data object Others : BattlefieldRow

    /** The row counted from the player's own edge: 0 the back row, 2 the creature row (a free row between, where the combat
     * area floats, CMB-007). */
    val index: Int get() = if (this == Creatures) 2 else 0

    companion object {
        /** TBL-004: the row of a permanent with [typeLine] (the type line it has now): the first type in the shared order
         * (SYS-051) decides, so a creature of any other type too is in the creature row; a face-down permanent (or one the
         * viewer can't see) is in the creature row. */
        fun of(typeLine: String?, faceDown: Boolean): BattlefieldRow = when {
            faceDown || typeLine == null -> Creatures
            else -> when (cardTypeGroup(typeLine)) {
                CardTypeGroup.CREATURES -> Creatures
                CardTypeGroup.LANDS -> Lands
                else -> Others
            }
        }
    }
}

/**
 * What decides whether two permanents lie in one stack (TBL-053): the same card and face, controller and owner, and the
 * same state that matters for play: counters, damage, face up, in combat or not, the entered-this-turn mark (summoning
 * sickness shown), the P/T and marks as worked out. Tapped or not never splits a stack.
 */
data class StackIdentity(val definition: String, val faceId: String, val token: Boolean, val owner: String,
    val counters: List<TypedCounter>, val damage: String, val corner: String, val marks: List<String>,
    val untilEndOfTurn: Boolean, val entered: Boolean)

/** One battlefield card as the layout reads it: [identity] null when it never stacks (face down, in combat, attached to
 * another card); [attachedTo] the card it lies behind (an Aura, Equipment or a merged mutate creature's other card). */
data class BattlefieldCard(val id: String, val field: String, val row: BattlefieldRow, val identity: StackIdentity?,
    val attachedTo: String? = null, val tapped: Boolean = false)

/**
 * A group on a battlefield (TBL-053): a stack of identical permanents, a permanent with the cards attached to it, or one
 * card alone. [members] in arrival order, a host first and then what is attached to it.
 */
data class BattlefieldGroup(val key: String, val field: String, val row: BattlefieldRow, val members: List<String>,
    val kind: Kind) {
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

    /** The card drawn on top: a stack's last arrival, an attached group's host. */
    val top: String get() = if (kind == Kind.Stack) members.last() else members.first()

    /** TBL-111: the order a hold inspects the members in: the top card first. */
    val inspectOrder: List<String> get() = if (kind == Kind.Stack) members.reversed() else members
}

/** Where a battlefield card lies: [x] dp (at card size 1) from the left end of its battlefield's rows, [row] its
 * [BattlefieldRow.index], [lift] how many peek steps above its host an attached card lies, [order] its
 * drawing order on the battlefield (later on top), [copies] the ×N badge it carries (0: none). */
data class BattlefieldSpot(val x: Float, val row: Int, val lift: Int = 0, val order: Int = 0, val copies: Int = 0,
    val group: String = "", val lifts: Int = 0)

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
     * its own, and identical cards of one battlefield and row ([BattlefieldCard.identity]) form one stack. Groups are in
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
                groups[at] = stack.copy(members = stack.members + card.id, kind = BattlefieldGroup.Kind.Stack)
            } else {
                if (key != null) stacks[key] = groups.size
                groups += BattlefieldGroup("card:${card.id}", card.field, card.row, listOf(card.id), BattlefieldGroup.Kind.Single)
            }
        }
        // A stack is known by its first card, whichever joins or leaves later
        return groups.map { if (it.kind == BattlefieldGroup.Kind.Stack) it.copy(key = "stack:${it.members.first()}") else it }
    }

    /** The width [group] takes in its row, [expanded] or not. */
    fun width(group: BattlefieldGroup, expanded: Boolean): Float {
        val n = group.members.size
        return when {
            expanded && n > 1 -> n * SLOT
            group.kind == BattlefieldGroup.Kind.Stack && group.row == BattlefieldRow.Lands -> SLOT + (n - 1) * FAN
            group.kind == BattlefieldGroup.Kind.Stack -> SLOT + minOf(n - 1, TIGHT_SHOWN) * TIGHT
            else -> SLOT
        }
    }

    /**
     * Where each card of one battlefield's [groups] lies (TBL-004): creatures from the left in the row nearest the middle;
     * in the back row lands from the left and the other permanents from the right end, the first to arrive rightmost. The
     * right end is [rowWidth] dp from the left end, or further when the back row needs more room (nothing shrinks to fit;
     * the table pans, SYS-041). The group [expanded] lies with its members side by side (TBL-122).
     */
    fun place(groups: List<BattlefieldGroup>, rowWidth: Float, expanded: String? = null): Map<String, BattlefieldSpot> {
        val spots = LinkedHashMap<String, BattlefieldSpot>()
        var order = 0
        fun put(group: BattlefieldGroup, left: Float) {
            val open = group.key == expanded && group.members.size > 1
            val lifts = if (open || group.kind != BattlefieldGroup.Kind.Attached) 0 else group.members.size - 1
            // Behind first: attached cards (the highest peek first), then the host; a stack from its first arrival up
            val drawn = if (!open && group.kind == BattlefieldGroup.Kind.Attached) group.members.drop(1).reversed() + group.members.first()
                else group.members
            for (id in drawn) {
                val i = group.members.indexOf(id)
                val x = when {
                    open -> i * SLOT
                    group.kind == BattlefieldGroup.Kind.Stack && group.row == BattlefieldRow.Lands -> i * FAN
                    group.kind == BattlefieldGroup.Kind.Stack -> minOf(i, TIGHT_SHOWN) * TIGHT
                    else -> 0f
                }
                val lift = if (open || group.kind != BattlefieldGroup.Kind.Attached) 0 else i
                // The tight stack's ×N on its top card
                val copies = if (!open && group.kind == BattlefieldGroup.Kind.Stack && group.row != BattlefieldRow.Lands &&
                    id == group.top) group.members.size else 0
                spots[id] = BattlefieldSpot(left + x, group.row.index, lift, order++, copies, group.key, lifts)
            }
        }
        var creatures = 0f
        for (group in groups.filter { it.row == BattlefieldRow.Creatures }) {
            put(group, creatures); creatures += width(group, group.key == expanded)
        }
        val lands = groups.filter { it.row == BattlefieldRow.Lands }
        val others = groups.filter { it.row == BattlefieldRow.Others }
        var left = 0f
        for (group in lands) { put(group, left); left += width(group, group.key == expanded) }
        val othersWidth = others.sumOf { width(it, it.key == expanded).toDouble() }.toFloat()
        var right = maxOf(rowWidth, left + (if (lands.isNotEmpty() && others.isNotEmpty()) SLOT else 0f) + othersWidth)
        for (group in others) {
            right -= width(group, group.key == expanded)
            put(group, right)
        }
        return spots
    }

    /** The most peek steps any group of [groups] lifts its attached cards by (the rows grow by that much, TBL-003). */
    fun lifts(groups: List<BattlefieldGroup>): Int =
        groups.filter { it.kind == BattlefieldGroup.Kind.Attached }.maxOfOrNull { it.members.size - 1 } ?: 0

    /**
     * Every battlefield card of [board] as the layout reads it, each battlefield's in arrival order (its container's,
     * timestamp order): its row by the type line it has now ([computed]'s when an effect changed it, else its face's),
     * what it is attached to (a card on a battlefield) and its [StackIdentity]. A face-down card never stacks (two backs
     * lying together would tell the other player they are the same card), nor one in combat or attached to another.
     */
    fun cards(board: org.asyncttwiz.protocol.BoardState, definitions: Map<String, org.asyncttwiz.protocol.CardDefinition>,
        computed: org.asyncttwiz.automatum.ComputedBoard): List<BattlefieldCard> {
        val byId = board.cards.associateBy { it.id }
        val fields = board.containers.filter { it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD }
        val onField = fields.flatMapTo(HashSet()) { it.cards }
        val inCombat = org.asyncttwiz.protocol.CombatStackRules.occurrences(board.combat).mapTo(HashSet()) { it.sourceId }
        return fields.flatMap { field -> field.cards.mapNotNull { id -> byId[id]?.let { card ->
            val face = definitions[card.definition]?.faces?.firstOrNull { it.faceId == card.faceId }
            val auto = computed.cards[card.id]
            val hidden = card.isFaceDown
            val host = (card.attachedTo as? org.asyncttwiz.protocol.MtgRef.Card)?.cardId?.takeIf { it in onField && it != card.id }
            val identity = if (hidden || host != null || card.id in inCombat) null else StackIdentity(card.definition, card.faceId,
                card.token, card.owner, card.typedCounters, card.damage, auto?.corner ?: card.corner, auto?.marks.orEmpty(),
                auto?.untilEndOfTurn == true, org.asyncttwiz.protocol.EnteredTurn.marked(board, card))
            BattlefieldCard(card.id, field.id, BattlefieldRow.of(auto?.typeLine ?: face?.typeLine, hidden), identity, host, card.tapped)
        } } }
    }
}

/** The glows the table draws (TBL-117): [playable] the cyan glow's strength by card, [targets] the legal targets framed in
 * violet, [expand] the strength of the glow of the groups a tap opens (white). */
data class TableGlows(val playable: Map<String, Float>, val targets: Set<String>, val expand: Map<String, Float> = emptyMap()) {
    companion object {
        /**
         * TBL-117 for groups: a collapsed group of several ([taps]: what a tap on each does) glows as a whole when any member
         * glows: in the usual colours when the tap acts at once (every member as strong as its strongest; a legal target
         * framed whole), in the expand colour when the tap opens it. Cards alone and the opened group's members glow as
         * themselves.
         */
        fun of(groups: List<BattlefieldGroup>, taps: Map<String, GroupTap>, playable: Map<String, Float>, targets: Set<String>): TableGlows {
            if (taps.isEmpty()) return TableGlows(playable, targets)
            val cyan = playable.toMutableMap()
            val violet = targets.toMutableSet()
            val white = HashMap<String, Float>()
            for (group in groups) {
                val tap = taps[group.key] ?: continue
                val strength = group.members.maxOf { playable[it] ?: 0f }
                val target = group.members.any { it in targets }
                if (strength <= 0f && !target) continue
                if (tap == GroupTap.Expand) for (id in group.members) {
                    white[id] = if (target) 1f else strength; cyan -= id; violet -= id
                } else {
                    if (strength > 0f) for (id in group.members) cyan[id] = strength
                    if (target && tap is GroupTap.Act) violet += group.members
                }
            }
            return TableGlows(cyan, violet, white)
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
         * acts on it at once (in a stack, on an untapped usable member first, [tapped] telling), with several it expands,
         * with none it is the top card's tap.
         */
        fun of(group: BattlefieldGroup, usable: (String) -> Boolean, tapped: (String) -> Boolean): GroupTap {
            val units = group.units.map { unit -> unit.filter(usable) }.filter { it.isNotEmpty() }
            return when (units.size) {
                0 -> Top
                1 -> Act(units.single().let { members -> members.firstOrNull { !tapped(it) } ?: members.first() })
                else -> Expand
            }
        }
    }
}
