package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardPosition
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatArea
import org.asyncttwiz.protocol.CombatOccurrence
import org.asyncttwiz.protocol.CombatPair
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayPlacement
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.PlayerStatus
import org.asyncttwiz.protocol.StackEffect
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.SuggestedResult
import org.asyncttwiz.protocol.TokenStacks
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import org.asyncttwiz.protocol.ResultSuggestion as AtwSuggestion

/**
 * A game on engine state shown with ATW's table (I-D54 item 2, I-D56 step C): the board the table draws, made from the
 * engine's state ([Engine.view], [Engine.visible]) and the table state, as one player sees it. The board carries the
 * state it was made from ([BoardState.engine]), so every engine reader of it ([EngineState.from], [ComputedBoard],
 * the flows) works on the engine's own state; nothing is ever applied to it.
 *
 * - Cards keep the game's card ids (the engine plays the prepared cards by their ids) and their card data; a token
 *   (or a copy that is no card) shows card data made from its engine definition ([AtwTokens.submitted]).
 * - Containers are the play's own: libraries top first, hands, graveyards top first, each player's battlefield (by the
 *   controller), exile (by the owner) and the Stack (entries top first). A spell's card stays in the zone it was cast
 *   from with a Stack entry pointing at it (STK-001, STK-045).
 * - What [viewer] may not see is covered (a card in a hidden zone they may see, looked at or revealed, is known to them).
 * - Battlefield places come from the table state, else the first free cell of the card's normal row
 *   ([org.asyncttwiz.protocol.BattlefieldPlacement]), in timestamp order.
 * - The combat lanes show the engine's combat (a pair per attacker), or the shared attack staging before it is declared.
 */
object EngineProjection {
    /** The board of [model] for [viewer], on [base] (the play as it started: its ids, containers, players, card data);
     * [nodeId] is the shown event; [attention]: whose move it is for the viewer (TRN-013). */
    fun project(base: PlayState, model: GameModel, viewer: String, nodeId: String, attention: String? = null,
        staging: Staging? = null): PlayState {
        val state = model.engine
        val table = model.table
        val me = PlayerId(viewer)
        val engineView = EngineView(state, table, me)
        val visible = Engine.visible(state, me)
        val view = runCatching { Engine.view(state) }.getOrNull()
        val board0 = base.board
        val definitions = base.definitions.associateBy { it.digest }.toMutableMap()
        val byCard = board0.cards.associateBy { it.id }
        fun container(zone: PlayZone, player: String?): PlayContainer? =
            board0.containers.firstOrNull { it.zone == zone && it.accountId == player && !it.temp }
        val stackContainer = board0.containers.first { it.zone == PlayZone.STACK }

        // Card data of an object: the prepared card's, else made from its engine definition (tokens, copies)
        fun digestOf(obj: GameObject): String {
            val prepared = byCard[obj.card.value]
            if (prepared != null && !obj.token && !obj.copyOfCard) return prepared.definition
            val definition = obj.definition ?: return prepared?.definition ?: UNKNOWN.digest.also { definitions[it] = UNKNOWN }
            return AtwTokens.submitted(definition, token = true).also { definitions[it.digest] = it }.digest
        }
        fun faceIdOf(obj: GameObject, digest: String): String {
            val faces = definitions[digest]?.definition?.faces.orEmpty()
            return (faces.getOrNull(obj.face) ?: faces.firstOrNull())?.faceId ?: ""
        }
        fun ref(id: ObjectId): MtgRef = when (id) {
            is ObjectId.Card -> MtgRef.Card(id.card.value, id.zoneChanges)
            is ObjectId.StackItem -> MtgRef.StackItem(id.id)
        }
        fun ref(target: TargetRef): MtgRef = when (target) {
            is TargetRef.Object -> ref(target.id)
            is TargetRef.Player -> MtgRef.Player(target.id.value)
        }

        // Where a spell's card came from (its last move to the Stack), so it stays there (STK-001)
        val castFrom = mutableMapOf<ObjectId, Zone>()
        for (logged in state.log) {
            val change = logged.change as? Change.ZoneChange ?: continue
            for (move in change.moves) if (move.to == Zone.Stack) castFrom[move.after.id] = move.from
        }

        val cards = mutableListOf<PlayCard>()
        val members = mutableMapOf<String, MutableList<Pair<GameObject, String>>>()
        fun add(containerId: String, obj: GameObject, card: PlayCard) {
            cards += card
            members.getOrPut(containerId) { mutableListOf() } += obj to card.id
        }
        val controllerOf = { obj: GameObject -> view?.get(obj.id)?.controller ?: obj.controller ?: obj.owner }
        for (obj in state.objects.values) {
            val zone = if (obj.zone == Zone.Stack) castFrom[obj.id] ?: Zone.Hand else obj.zone
            val playZone = when (zone) {
                Zone.Library -> PlayZone.LIBRARY
                Zone.Hand -> PlayZone.HAND
                Zone.Graveyard -> PlayZone.GRAVEYARD
                Zone.Battlefield -> PlayZone.BATTLEFIELD
                Zone.Exile -> PlayZone.EXILE
                // Outside the game and the command zone (emblems: their records) aren't on the table
                else -> continue
            }
            val holder = if (playZone == PlayZone.BATTLEFIELD) controllerOf(obj).value else obj.owner.value
            val target = container(playZone, holder) ?: continue
            val digest = digestOf(obj)
            val seen = obj.id in visible
            val hiddenZone = playZone == PlayZone.LIBRARY || playZone == PlayZone.HAND
            val card = PlayCard(
                id = obj.card.value, definition = digest, owner = obj.owner.value, containerId = target.id,
                faceId = faceIdOf(obj, digest),
                covered = !seen || obj.faceDown, tapped = obj.tapped,
                token = obj.token || obj.copyOfCard,
                explicitlyCovered = obj.faceDown,
                knownToAll = seen && hiddenZone && !(playZone == PlayZone.HAND && obj.owner == me),
                damage = obj.damage.takeIf { it > 0 }?.toString().orEmpty(),
                enteredTurn = obj.controlSince?.turn?.toString().orEmpty(),
                zoneChanges = obj.id.zoneChanges, timestamp = obj.timestamp,
                typedCounters = EngineCodec.counters(obj.counters),
                attachedTo = obj.attachedTo?.let(::ref),
            )
            add(target.id, obj, card)
        }

        // Battlefield places: the table state's, else the first free cell of the card's row, in timestamp order
        val placed = mutableMapOf<String, BoardPosition>()
        for (c in board0.containers.filter { it.zone == PlayZone.BATTLEFIELD }) {
            val here = members[c.id].orEmpty().sortedWith(compareBy<Pair<GameObject, String>> { it.first.timestamp }.thenBy { it.second })
            val taken = mutableSetOf<BoardPosition>()
            for ((obj, id) in here) table.placements[obj.id]?.let { p ->
                BoardPosition(p.x, p.y).takeIf { it !in taken }?.let { taken += it; placed[id] = it }
            }
            for ((obj, id) in here) if (id !in placed) {
                val row = if (obj.faceDown) 1 else TokenStacks.row(definitions[digestOf(obj)]?.definition?.faces
                    ?.firstOrNull { it.faceId == faceIdOf(obj, digestOf(obj)) })
                val cell = BoardPosition(generateSequence(0) { it + 1 }.first { BoardPosition(it, row) !in taken }, row)
                taken += cell; placed[id] = cell
            }
        }
        val withPlaces = cards.map { card -> placed[card.id]?.let { card.copy(placement = PlayPlacement(it, card.id)) } ?: card }

        // The Stack: entries top first; a spell points at its card, an ability at its source card on the table, else it
        // shows its own text as a card of its own (I-D13)
        val effects = mutableListOf<StackEffect>()
        val shownAbilities = mutableListOf<PlayCard>()
        val cardIds = withPlaces.map { it.id }.toSet()
        for (id in state.stack.reversed()) {
            val item = state.stackItems[id] ?: continue
            val entryId = "entry:$id"
            val mtg = EngineCodec.stackItem(item, ::ref)
            val sourceCard = when (id) {
                is ObjectId.Card -> id.card.value
                else -> (item.source as? ObjectId.Card)?.card?.value?.takeIf { it in cardIds }
            }
            if (sourceCard != null && item.kind !is StackItemKind.SpellCopy) {
                effects += StackEffect(entryId, sourceCard, controller = item.controller.value,
                    faceDown = (id as? ObjectId.Card)?.let { state.objects[it] }?.faceDown == true, item = mtg)
            } else {
                val name = item.snapshots.firstOrNull { it.id == item.source }?.characteristics?.name
                    ?: (item.kind as? StackItemKind.SpellCopy)?.let { state.stackItems[it.copied]?.let { c -> (c.id as? ObjectId.Card)?.let { cid -> state.objects[cid]?.definition?.faces?.firstOrNull()?.name } } }
                val definition = AtwTokens.ability(name, item.ability).also { definitions[it.digest] = it }
                val shown = PlayCard("ability:$id", definition.digest, item.controller.value, stackContainer.id,
                    definition.definition.faces.single().faceId, covered = false, token = true)
                shownAbilities += shown
                effects += StackEffect(entryId, shown.id, frozen = shown, controller = item.controller.value,
                    isCopy = item.kind is StackItemKind.SpellCopy, item = mtg)
            }
        }

        val containers = board0.containers.map { c ->
            val here = members[c.id].orEmpty()
            val byObject = here.associate { it.first.id to it.second }
            val player = state.players.firstOrNull { it.id.value == c.accountId }
            fun ordered(objects: List<ObjectId>) = objects.mapNotNull(byObject::get)
            // A spell's card stays in the zone it was cast from, on top (STK-001)
            val spells = here.filter { it.first.zone == Zone.Stack }.map { it.second }
            val ids = when (c.zone) {
                PlayZone.LIBRARY -> spells + ordered(player?.library.orEmpty())
                PlayZone.HAND -> ordered(player?.hand.orEmpty()) + spells
                PlayZone.GRAVEYARD -> spells + ordered(player?.graveyard.orEmpty().reversed())
                PlayZone.STACK -> effects.map { it.id }
                else -> spells + here.filter { it.first.zone != Zone.Stack }
                    .sortedWith(compareBy<Pair<GameObject, String>> { it.first.timestamp }.thenBy { it.second }).map { it.second }
            }
            c.copy(cards = ids, layers = emptyList())
        }

        val players = board0.players.map { p ->
            val engine = state.players.firstOrNull { it.id.value == p.accountId }
            val opening = state.turn.number == 0 && state.log.none { l ->
                (l.change as? Change.Happened)?.event.let { it is arcanum.engine.change.GameEvent.Kept && it.player.value == p.accountId }
            }
            val mulligans = state.log.count { l ->
                (l.change as? Change.Happened)?.event.let { it is arcanum.engine.change.GameEvent.Mulliganed && it.player.value == p.accountId }
            }
            p.copy(life = (engine?.life ?: 20).toString(), counters = emptyMap(), status = if (opening) PlayerStatus.OPENING else "",
                manaPool = engine?.manaPool.orEmpty().map(EngineCodec::mana), typedCounters = EngineCodec.counters(engine?.counters.orEmpty()),
                ledger = "", skipWhenNoChoice = false, fullControl = false, mulligans = mulligans)
        }

        val position = if (state.turn.number == 0) TurnPosition.UNTAP
            else EngineCodec.position(state.turn.step, state.combat?.firstStrikeStep == true)
        val turn = TurnState(state.turn.active.value, state.turn.number.coerceAtLeast(1).toString(), position.phase, position.step)
        val staged = staging?.takeIf { it.key == Staging.keyOf(state) }
        val records = buildList {
            state.combat?.let { add(EngineCodec.combatRecord(it, state.turn.number)) }
            state.command.mapNotNull { state.objects[it] }.forEach { add(EngineCodec.emblemRecord(it, EngineCodec.emblemText(it))) }
            staged?.records?.let { addAll(it) }
        }.distinctBy { it.id }.sortedBy { it.id }
        val board = BoardState(players = players, cards = withPlaces + shownAbilities, containers = containers, turn = turn,
            schemaVersion = board0.schemaVersion, combat = if (staged != null) staged.combat else combat(state, table),
            stackEffects = effects, resultSuggestion = suggestion(table, nodeId),
            attentionHolder = attention, priority = state.priority.let { MtgPriority(it.holder?.value, it.passes) },
            engineRecords = records, engine = engineView)
        return base.copy(board = board, currentNodeId = nodeId, currentGroup = null,
            definitions = definitions.values.sortedBy { it.digest })
    }

    /** The combat lanes: the engine's combat (a pair per attacker, blockers in the pair of the first attacker they block,
     * also blocking the others' pairs, CMB-031), else the attack staged before it is declared. */
    private fun combat(state: GameState, table: TableState): CombatArea? {
        val combat = state.combat
        fun card(id: ObjectId) = (id as? ObjectId.Card)?.card?.value
        fun target(t: TargetRef) = ((t as? TargetRef.Object)?.id as? ObjectId.Card)?.card?.value
        if (combat != null) {
            val pairs = combat.attackers.mapNotNull { a ->
                val attacker = card(a.id) ?: return@mapNotNull null
                val blockers = combat.blockers.filter { it.attackers.firstOrNull() == a.id }.mapNotNull { b ->
                    val id = card(b.id) ?: return@mapNotNull null
                    CombatOccurrence("block:$id", id, alsoBlocks = b.attackers.drop(1).mapNotNull(::card).map { "pair:$it" })
                }
                CombatPair("pair:$attacker", listOf(CombatOccurrence("attack:$attacker", attacker)), blockers,
                    attackTarget = target(a.target), blocked = a.blocked)
            }
            return CombatArea(COMBAT_ID, pairs)
        }
        val staged = table.attackDraft ?: return null
        val pairs = staged.mapNotNull { a ->
            val attacker = card(a.attacker) ?: return@mapNotNull null
            CombatPair("pair:$attacker", listOf(CombatOccurrence("attack:$attacker", attacker)), attackTarget = target(a.target))
        }
        return CombatArea(COMBAT_ID, pairs).takeIf { pairs.isNotEmpty() || staged.isEmpty() }
    }

    private fun suggestion(table: TableState, nodeId: String): AtwSuggestion? {
        val s = table.suggestion ?: return null
        val outcome = when (val r = s.result) {
            is arcanum.engine.setup.GameResult.Won -> if (r.winner == s.by) SuggestedResult.WIN else SuggestedResult.LOSS
            arcanum.engine.setup.GameResult.Draw -> SuggestedResult.DRAW
        }
        return AtwSuggestion("suggestion:${s.by.value}:$outcome", s.by.value, outcome)
    }

    /**
     * The declaring player's own staging of a declaration (CMB-028, CMB-034: their lanes and attack choices, as the
     * combat controls make them) at the point [key] ([keyOf]: the turn, step and combat it belongs to): shown in place of
     * the engine's combat while that point lasts, and sent only as the declaration itself (I-D56 §12).
     */
    data class Staging(val key: String, val combat: CombatArea?, val records: List<org.asyncttwiz.protocol.EngineRecord> = emptyList()) {
        companion object {
            fun keyOf(state: GameState): String = "${state.turn.number}/${state.turn.step}/${state.combat?.attackers?.map { it.id }}/" +
                "${state.combat?.blockersDeclaredBy}/${state.priority.holder}"
        }
    }

    /** ATW's turn position of the engine's [step] (its label for the table's words). */
    fun position(step: arcanum.api.TurnStep): TurnPosition = EngineCodec.position(step)

    const val COMBAT_ID = "combat"

    /** Card data for an object nobody can read (no definition): shown as a nameless card. */
    private val UNKNOWN: SubmittedDefinition = run {
        val face = org.asyncttwiz.protocol.DefinitionFace("00000000-0000-5000-8000-000000000000", "Card")
        val card = org.asyncttwiz.protocol.CardDefinition("Card", listOf(face))
        SubmittedDefinition(org.asyncttwiz.protocol.DeckJson.digest(card), card)
    }
}
