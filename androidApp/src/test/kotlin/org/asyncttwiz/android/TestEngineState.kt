@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.state.Attacker
import arcanum.engine.state.Blocker
import arcanum.engine.state.CardId
import arcanum.engine.state.CombatState
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import arcanum.engine.state.of
import arcanum.engine.state.setup
import org.asyncttwiz.automatum.PrintedCards
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition

/**
 * A test board's engine state (engine-responsiveness R4d): the screenshot and table tests draw boards made by hand, and
 * [EngineFixture] gives each the engine state of the same cards, built directly with the engine's state types, with full
 * information. Cards keep their ids; players in the board's order; library, hand and graveyard in their order (a
 * graveyard is listed top first on the board, bottom first in the engine); the Battlefield and Exile in timestamp order;
 * a combat drawn in the lanes is taken as declared (attackers in the declare attackers step once someone holds priority,
 * blockers from the declare blockers step on).
 *
 * A board the engine can't hold ([NotHeld]: a manual Stack entry, cards in a Temp pile, engine records, a life total,
 * damage or turn that isn't a number) has no engine state. A card's typed counters are read (a suspended card's time
 * counters, ZON-091); other engine data on a board (players' counters, mana, ledgers, Stack items) isn't: a board holding
 * any fails the test instead, as no test board needs it.
 */
internal object TestEngineState {
    /** A typed counter's kind as the app writes it: "+1/+1", "time", else a named kind. */
    private fun counterKind(kind: String): CounterKind = Regex("([+-]\\d+)/([+-]\\d+)").matchEntire(kind)
        ?.let { CounterKind.PowerToughness(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        ?: if (kind == "time") CounterKind.Time else CounterKind.Named(kind)

    /** The engine can't hold the board. */
    class NotHeld(reason: String) : IllegalStateException(reason)

    fun of(play: PlayState): GameState {
        val board = play.board
        problems(board)?.let { throw NotHeld(it) }
        check(CombatStackRules.stack(board).cards.isEmpty() && board.cards.none { it.ledger.isNotEmpty() } &&
            board.players.none { it.ledger.isNotEmpty() || it.typedCounters.isNotEmpty() || it.manaPool.isNotEmpty() }) {
            "Engine data on a test board isn't read by the test builder"
        }
        val definitions = AutomatumBoards.definitions(play.definitions)
        val printed = mutableMapOf<String, CardDefinition?>()
        fun definition(card: PlayCard): CardDefinition? = definitions[card.definition] ?: printed.getOrPut(card.definition) {
            play.definitions.singleOrNull { it.digest == card.definition }?.definition?.let(PrintedCards::definition)
        }
        val containers = board.containers.associateBy { it.id }
        val objects = LinkedHashMap<ObjectId, GameObject>()
        for (card in board.cards) {
            val container = containers.getValue(card.containerId)
            val zone = zone(container.zone)
            val id = ObjectId.Card(CardId(card.id), card.zoneChanges)
            val faces = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
            objects[id] = GameObject(
                id = id,
                // The player whose deck the card came from; a token's owner is who made it
                owner = PlayerId(if (card.token) card.owner else play.shuffles.firstOrNull { card.id in it.order }?.accountId ?: card.owner),
                controller = if (zone == Zone.Battlefield) container.accountId?.let(::PlayerId) else null,
                zone = zone,
                definition = checkNotNull(definition(card)) { "No definition for ${card.id}" },
                face = faces.indexOfFirst { it.faceId == card.faceId }.coerceAtLeast(0),
                token = card.token,
                tapped = card.tapped,
                faceDown = card.isFaceDown,
                damage = card.damage.toIntOrNull() ?: 0,
                attachedTo = card.attachedTo?.let { ref -> target(board, ref) },
                timestamp = card.timestamp,
                counters = card.typedCounters.map { CounterEntry(counterKind(it.kind), it.count, it.timestamp) },
                controlSince = if (zone != Zone.Battlefield || container.accountId == null) null
                    else ControlSince(PlayerId(container.accountId!!), card.enteredTurn.toIntOrNull() ?: 0),
            )
        }
        fun ordered(zone: PlayZone, owner: String): List<ObjectId> {
            val container = board.containers.single { it.zone == zone && it.accountId == owner && !it.temp }
            val cards = container.cards.map { id -> ObjectId.Card(CardId(id), board.cards.single { it.id == id }.zoneChanges) }
            return if (zone == PlayZone.GRAVEYARD) cards.reversed() else cards
        }
        val order = compareBy<GameObject> { it.timestamp }.thenBy { it.id.toString() }
        fun byTimestamp(zone: Zone) = objects.values.filter { it.zone == zone }.sortedWith(order).map { it.id }
        val players = board.players.map { p ->
            PlayerState(id = PlayerId(p.accountId), life = p.life.toInt(), library = ordered(PlayZone.LIBRARY, p.accountId),
                hand = ordered(PlayZone.HAND, p.accountId), graveyard = ordered(PlayZone.GRAVEYARD, p.accountId),
                skipWhenNoChoice = p.skipWhenNoChoice)
        }
        val position = TurnPosition.of(board.turn)!!
        val number = board.turn.number.toInt()
        val other = board.players.firstOrNull { it.accountId != board.turn.accountId }?.accountId
        val turnStarted = mapOf(PlayerId(board.turn.accountId) to number) +
            listOfNotNull(other?.takeIf { number > 1 }?.let { PlayerId(it) to number - 1 })
        val state = GameState.of(
            players = players.toMutableList(),
            objects = objects,
            battlefield = byTimestamp(Zone.Battlefield).toMutableList(),
            exile = byTimestamp(Zone.Exile).toMutableList(),
            turn = TurnState(number, PlayerId(board.turn.accountId), step(position), turnStarted = turnStarted),
            priority = board.priority?.let { PriorityState(it.holder?.let(::PlayerId), it.passes) } ?: PriorityState(null),
            combat = declared(board, lanes(board), position),
            timestampCounter = board.cards.maxOfOrNull { it.timestamp } ?: 0L,
        )
        firstStrikeFromLanes(state, position)
        return state
    }

    /** Why the engine can't hold [board], or null. */
    private fun problems(board: BoardState): String? = when {
        CombatStackRules.stack(board).cards.any { id -> board.stackEffects.singleOrNull { it.id == id }?.item == null } ->
            "a Stack entry made by hand"
        board.containers.any { it.temp && it.cards.isNotEmpty() } -> "cards in a Temp pile"
        board.engineRecords.isNotEmpty() -> "engine records"
        board.cards.any { it.damage.isNotEmpty() && it.damage.toIntOrNull() == null } -> "damage that isn't a number"
        board.players.any { it.life.toIntOrNull() == null } -> "a life total that isn't a number"
        TurnPosition.of(board.turn) == null || board.turn.number.toIntOrNull() == null -> "an unknown step"
        else -> null
    }

    private fun zone(zone: PlayZone): Zone = when (zone) {
        PlayZone.LIBRARY -> Zone.Library
        PlayZone.HAND -> Zone.Hand
        PlayZone.BATTLEFIELD -> Zone.Battlefield
        PlayZone.GRAVEYARD -> Zone.Graveyard
        PlayZone.EXILE -> Zone.Exile
        PlayZone.STACK -> Zone.Stack
    }

    /** The engine's step of ATW's [position] (the first-strike damage step is a combat damage step). */
    private fun step(position: TurnPosition): TurnStep = when (position) {
        TurnPosition.FIRST_STRIKE_DAMAGE -> TurnStep.CombatDamage
        else -> STEPS[TurnPosition.MANUAL.indexOf(position)]
    }

    private val STEPS = listOf(TurnStep.Untap, TurnStep.Upkeep, TurnStep.Draw, TurnStep.PrecombatMain,
        TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage,
        TurnStep.EndOfCombat, TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup)

    private fun target(board: BoardState, ref: org.asyncttwiz.protocol.MtgRef): TargetRef = when (ref) {
        is org.asyncttwiz.protocol.MtgRef.Player -> TargetRef.Player(PlayerId(ref.accountId))
        is org.asyncttwiz.protocol.MtgRef.Card -> TargetRef.Object(ObjectId.Card(CardId(ref.cardId), ref.zoneChanges))
        is org.asyncttwiz.protocol.MtgRef.StackItem -> TargetRef.Object(ObjectId.StackItem(ref.id))
    }

    /** The combat the lanes show: each pair's attackers attack its target (a planeswalker or battle card) or the
     * defending player; its blockers block them, and those of the pairs they also block (CMB-031). */
    private fun lanes(board: BoardState): CombatState? {
        val area = board.combat?.takeIf { it.pairs.isNotEmpty() } ?: return null
        val defender = CombatStackRules.defendingPlayer(board)
        fun obj(cardId: String) = ObjectId.Card(CardId(cardId), board.cards.single { it.id == cardId }.zoneChanges)
        val battlefield = board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.map { it.id }.toSet()
        val attackers = area.pairs.flatMap { pair ->
            val gone = pair.attackTarget != null && board.cards.none { it.id == pair.attackTarget && it.containerId in battlefield }
            val target = pair.attackTarget?.takeUnless { gone }?.let { TargetRef.Object(obj(it)) } ?: TargetRef.Player(PlayerId(defender!!))
            pair.attackers.map { Attacker(obj(it.sourceId), target, blocked = pair.blocked || pair.blockers.isNotEmpty() ||
                CombatStackRules.blockedFromElsewhere(area, pair.id), targetRemoved = gone) }
        }
        val blockers = CombatStackRules.blocks(area).groupBy({ it.first.sourceId }, { it.second.sourceId }).map { (b, attackers) ->
            Blocker(obj(b), attackers.map(::obj), defender?.let(::PlayerId)) }
        return CombatState(attackers, blockers)
    }

    /** The lanes' combat as declared at [position]: in the declare attackers step once someone holds priority; blocks
     * after the declare blockers step, or in it once someone holds priority. */
    private fun declared(board: BoardState, lanes: CombatState?, position: TurnPosition): CombatState? {
        val held = board.priority?.holder != null
        if (position == TurnPosition.DECLARE_ATTACKERS && !held) return null
        val combat = lanes ?: return null
        val declared = position in AFTER_BLOCKS || position == TurnPosition.DECLARE_BLOCKERS && held
        if (!declared) return combat.copy(attackers = combat.attackers.map { it.copy(blocked = false) }, blockers = emptyList())
        val defender = CombatStackRules.defendingPlayer(board)
        return combat.copy(blockersDeclaredBy = listOfNotNull(defender?.let(::PlayerId)), blocksDecided = true)
    }

    private val AFTER_BLOCKS = setOf(TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE, TurnPosition.END_COMBAT)

    /** In the first-strike damage step (CR 510.4) the creatures with first or double strike dealt their damage. */
    private fun firstStrikeFromLanes(state: GameState, position: TurnPosition) {
        val combat = state.combat ?: return
        if (position != TurnPosition.FIRST_STRIKE_DAMAGE || combat.firstStrikeStep || combat.firstStrikers != null) return
        val view = Engine.view(state)
        fun strikesFirst(id: ObjectId) = view[id]?.has { it == Mechanic.FirstStrike || it == Mechanic.DoubleStrike } == true
        val inCombat = (combat.attackers.map { it.id } + combat.blockers.map { it.id }).distinct().filter { it in state.battlefield }
        state.setup.combat = combat.copy(firstStrikeStep = true, firstStrikers = inCombat.filter(::strikesFirst))
    }
}
