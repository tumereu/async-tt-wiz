package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.Inspect
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
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import org.asyncttwiz.protocol.ResultSuggestion as AtwSuggestion

/**
 * A game on engine state shown with ATW's table (I-D54 item 2, I-D56 step C): the board the table draws, made from the
 * engine's state ([Engine.view], [Host.visible]) and the table state, as one player sees it ([Board]: the plain data the
 * table draws, engine-responsiveness R4a; the engine state stays beside it, never on it, and nothing is ever applied to
 * it).
 *
 * - Cards keep the game's card ids (the engine plays the prepared cards by their ids) and their card data; a token
 *   (or a copy that is no card) shows card data made from its engine definition ([AtwTokens.submitted]).
 * - Containers are the play's own: libraries top first, hands, graveyards top first, each player's battlefield (by the
 *   controller), exile (by the owner) and the Stack (entries top first). A spell's card stays in the zone it was cast
 *   from with a Stack entry pointing at it (STK-001, STK-045).
 * - What [viewer] may not see is covered (a card in a hidden zone they may see, looked at or revealed, is known to them).
 * - A merged permanent's other cards (mutate, CR 730.2) lie on its Battlefield as cards attached to it (SYS-074).
 * - Battlefield places are the first free cell of the card's normal row ([org.asyncttwiz.protocol.BattlefieldPlacement]),
 *   in timestamp order.
 * - The combat lanes show the engine's combat (a pair per attacker), or the shared attack and block staging before they are
 *   declared ([EngineDrafts], I-D54 §8); a token is drawn with its best matching picture ([TokenArt], SYS-072).
 * - Dungeon cards (CR 309), outside the game or in the command zone, are cards of the board in no zone's list (like an ability's
 *   card on the Stack), with the card data the player brought ([dungeon] finds the one in a player's command zone): the choice
 *   that brings one in shows them, a room ability on the Stack points at its dungeon, and the table draws it (TRN-069).
 */
object EngineProjection {
    /** What the table draws of a game on engine state: [board] (cards, piles, players, turn, combat lanes, Stack entries,
     * records) as one player sees it, with the card data of every card on it ([definitions], by digest). */
    data class Board(val board: BoardState, val definitions: List<SubmittedDefinition>)

    /** The board of [model] for [viewer], on [base] (the play as it started: its ids, containers, players, card data);
     * [nodeId] is the shown event; [attention]: who acts on it for the viewer (TRN-013); [move]: whose move the game is
     * for the viewer (TRN-058); [tokenArt] finds the engine's tokens' pictures (SYS-072), which the caller keeps in
     * [pictures] across boards. */
    fun project(base: PlayState, model: GameModel, viewer: String, nodeId: String, attention: String? = null,
        staging: Staging? = null, tokenArt: TokenArt = TokenArt.None, pictures: MutableMap<ObjectId, SubmittedDefinition> = HashMap(),
        move: String? = null): Board {
        val state = model.engine
        val table = model.table
        val me = PlayerId(viewer)
        // The model's view and visible set (worked out once per state, engine-responsiveness R2)
        val visible = model.visible(me)
        val view = runCatching { model.view() }.getOrNull()
        val board0 = base.board
        val definitions = base.definitions.associateBy { it.digest }.toMutableMap()
        val byCard = board0.cards.associateBy { it.id }
        fun container(zone: PlayZone, player: String?): PlayContainer? =
            board0.containers.firstOrNull { it.zone == zone && it.accountId == player && !it.temp }
        val stackContainer = board0.containers.first { it.zone == PlayZone.STACK }

        // What made each token, while the log still says (this and last turn): for its picture (SYS-072)
        val madeBy = HashMap<ObjectId, arcanum.engine.change.CausedBy?>()
        for (logged in state.log) (logged.change as? Change.Created)?.let { c -> c.objects.forEach { madeBy[it.obj.id] = c.by } }
        // Card data of an object: the prepared card's, else made from its engine definition (tokens, copies); a token is
        // drawn with the picture of its best matching token printing (SYS-072)
        val digests = HashMap<ObjectId, String>()
        fun digestOf(obj: GameObject): String = digests.getOrPut(obj.id) {
            val prepared = byCard[obj.card.value]
            if (prepared != null && !obj.token && !obj.copyOfCard) return@getOrPut prepared.definition
            val definition = obj.definition ?: return@getOrPut prepared?.definition ?: UNKNOWN.digest.also { definitions[it] = UNKNOWN }
            val made = AtwTokens.submitted(definition, token = true, gained = obj.gainedInCopying)
            // Once found, a token keeps its picture ([pictures], kept by the caller) after the log forgets what made it;
            // a copy of a card (offspring, embalm, myriad, …) is drawn with that card's own picture
            val shown = copied(made, base)
                ?: if (!obj.token || tokenArt === TokenArt.None) made else pictures.getOrPut(obj.id) { pictured(made, madeBy[obj.id], base, byCard, tokenArt) }
            shown.also { definitions[it.digest] = it }.digest
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
        // Dungeon cards (CR 309.2): the card data their player brought, found by name, else made from the engine's definition
        for (obj in state.objects.values) {
            // A dungeon has a definition ([isDungeon]).
            val definition = obj.definition
            if ((obj.zone != Zone.Outside && obj.zone != Zone.Command) || definition == null || !isDungeon(obj)) continue
            val name = definition.faces.firstOrNull()?.name
            val digest = base.definitions.firstOrNull { d -> d.definition.name == name &&
                d.definition.faces.firstOrNull()?.typeLine?.startsWith("Dungeon") == true }?.digest
                ?: AtwTokens.submitted(definition, token = false).also { definitions[it.digest] = it }.digest
            cards += PlayCard(id = obj.card.value, definition = digest, owner = obj.owner.value, containerId = stackContainer.id,
                faceId = faceIdOf(obj, digest), covered = obj.id !in visible, timestamp = obj.timestamp)
        }
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
            // SYS-074 (Arena style, TBL-053): a merged permanent's other cards lie on its Battlefield behind it, as cards
            // attached to it (no permanents of their own). The card in front shows the top card's picture: merged on top,
            // the permanent's own card and its top component swap pictures
            val parts = if (playZone == PlayZone.BATTLEFIELD) obj.mergedAbove + obj.mergedBelow else emptyList()
            fun partShown(part: arcanum.engine.state.MergedPart): Pair<String, String> {
                val prepared = byCard[part.id.card.value]
                val partDigest = if (prepared != null && !part.token && !part.copyOfCard) prepared.definition
                    else part.definition?.let { AtwTokens.submitted(it, token = true).also { d -> definitions[d.digest] = d }.digest }
                        ?: prepared?.definition ?: UNKNOWN.digest.also { definitions[it] = UNKNOWN }
                val faces = definitions[partDigest]?.definition?.faces.orEmpty()
                return partDigest to ((faces.getOrNull(part.face) ?: faces.firstOrNull())?.faceId ?: "")
            }
            val top = obj.mergedAbove.firstOrNull()?.takeIf { playZone == PlayZone.BATTLEFIELD }
            add(target.id, obj, top?.let { partShown(it).let { (d, f) -> card.copy(definition = d, faceId = f) } } ?: card)
            for (part in parts) {
                val (partDigest, partFace) = if (part == top) card.definition to card.faceId else partShown(part)
                add(target.id, obj, PlayCard(id = part.id.card.value, definition = partDigest, owner = part.owner.value,
                    containerId = target.id, faceId = partFace, covered = card.covered, tapped = obj.tapped,
                    token = part.token || part.copyOfCard, zoneChanges = part.id.zoneChanges, timestamp = part.timestamp,
                    attachedTo = ref(obj.id)))
            }
        }

        // Battlefield places: the first free cell of the card's row, in timestamp order
        val placed = mutableMapOf<String, BoardPosition>()
        for (c in board0.containers.filter { it.zone == PlayZone.BATTLEFIELD }) {
            val here = members[c.id].orEmpty().sortedWith(compareBy<Pair<GameObject, String>> { it.first.timestamp }.thenBy { it.second })
            val taken = mutableSetOf<BoardPosition>()
            for ((obj, id) in here) {
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
            state.command.mapNotNull { state.objects[it] }.filterNot(::isDungeon).forEach { add(EngineCodec.emblemRecord(it, EngineCodec.emblemText(it))) }
            // The staged choices and early answers: the player's own staging kept on the device (behind the head), else
            // the table's shared drafts (I-D54 §8)
            if (staged != null) addAll(staged.records) else addAll(EngineDrafts.records(state, table))
        }.distinctBy { it.id }.sortedBy { it.id }
        val board = BoardState(players = players, cards = withPlaces + shownAbilities, containers = containers, turn = turn,
            schemaVersion = board0.schemaVersion, combat = if (staged != null) staged.combat else combat(state, table, base.gameId),
            stackEffects = effects, resultSuggestion = suggestion(table, nodeId),
            attentionHolder = attention, priority = state.priority.let { MtgPriority(it.holder?.value, it.passes) },
            engineRecords = records, move = move)
        return Board(board, definitions.values.sortedBy { it.digest })
    }

    /**
     * SYS-072 (owner, 2026-11-05): [made], the card data of a copy of a card (a token copy: offspring, populate, embalm,
     * eternalize, myriad, encore, …; or a copy that is no card), drawn with the copied card's own picture: each face takes
     * the picture of the game's card face of its name (the game's cards, not tokens). Null when no face is named so.
     */
    private fun copied(made: SubmittedDefinition, base: PlayState): SubmittedDefinition? {
        val pictures = base.definitions.asSequence().filter { !it.definition.token }.flatMap { it.definition.faces }
            .filter { it.scryfallId != null || it.imageUri != null }
        val faces = made.definition.faces.map { face ->
            pictures.firstOrNull { it.name == face.name }?.let { face.copy(scryfallId = it.scryfallId, imageUri = it.imageUri) } ?: face
        }
        if (faces == made.definition.faces) return null
        val card = made.definition.copy(faces = faces)
        return SubmittedDefinition(org.asyncttwiz.protocol.DeckJson.digest(card), card)
    }

    /** [token] drawn with the picture [tokenArt] finds for it and the card whose effect made it ([by]); as it is when
     * none is found (the plain frame). */
    private fun pictured(token: SubmittedDefinition, by: arcanum.engine.change.CausedBy?, base: PlayState, byCard: Map<String, PlayCard>,
        tokenArt: TokenArt): SubmittedDefinition {
        if (tokenArt === TokenArt.None) return token
        val face = token.definition.faces.singleOrNull() ?: return token
        val digest = (by?.source as? ObjectId.Card)?.let { byCard[it.card.value]?.definition }
        val source = base.definitions.firstOrNull { it.digest == digest }?.definition
            ?: by?.name?.let { name -> base.definitions.firstOrNull { !it.definition.token && it.definition.name == name }?.definition }
        val id = try { tokenArt.find(TokenArtQuery(face, source?.name ?: by?.name, source?.printingId)) }
            catch (problem: Exception) { if (problem is kotlin.coroutines.cancellation.CancellationException) throw problem; null }
            ?: return token
        val card = token.definition.copy(faces = listOf(face.copy(scryfallId = id)))
        return SubmittedDefinition(org.asyncttwiz.protocol.DeckJson.digest(card), card)
    }

    /** The combat lanes: the engine's combat (a pair per attacker, blockers in the pair of the first attacker they block,
     * also blocking the others' pairs, CMB-031), else the attack staged before it is declared. */
    private fun combat(state: GameState, table: TableState, game: String): CombatArea? {
        val combat = state.combat
        fun card(id: ObjectId) = (id as? ObjectId.Card)?.card?.value
        fun target(t: TargetRef) = ((t as? TargetRef.Object)?.id as? ObjectId.Card)?.card?.value
        if (combat != null) {
            // CMB-034 (I-D54 §8): before blockers are declared, the defender's shared block staging
            // Problem reports WCRF3Q, R8XVPA: only blocks of creatures on the battlefield and of this combat's attackers: a
            // staging left on the table from an earlier combat (a declaration made at an assumption can't end it) may name a
            // creature that is gone, which the board can't show
            val attacking = combat.attackers.map { it.id }.toSet()
            val drafted = table.blockDraft?.takeIf { combat.blockersDeclaredBy.isEmpty() && state.turn.step == arcanum.api.TurnStep.DeclareBlockers }
                ?.also { all -> leftOut(game, state, "block", all.filterNot { it.blocker in state.battlefield && it.attacker in attacking }) }
                ?.filter { it.blocker in state.battlefield && it.attacker in attacking }
                ?.groupBy { it.blocker }?.map { (blocker, blocks) -> blocker to blocks.map { it.attacker } }
            val blocking = drafted ?: combat.blockers.map { it.id to it.attackers }
            val pairs = combat.attackers.mapNotNull { a ->
                val attacker = card(a.id) ?: return@mapNotNull null
                val blockers = blocking.filter { it.second.firstOrNull() == a.id }.mapNotNull { (blocker, attackers) ->
                    val id = card(blocker) ?: return@mapNotNull null
                    CombatOccurrence(blockId(id), id, alsoBlocks = attackers.drop(1).mapNotNull(::card).map(::pairId))
                }
                CombatPair(pairId(attacker), listOf(CombatOccurrence(attackId(attacker), attacker)), blockers,
                    attackTarget = target(a.target), blocked = a.blocked)
            }
            return CombatArea(COMBAT_ID, pairs)
        }
        // WCRF3Q, R8XVPA: likewise only attackers on the battlefield
        val staged = table.attackDraft?.takeIf { state.turn.step == arcanum.api.TurnStep.DeclareAttackers }
            ?.also { all -> leftOut(game, state, "attack", all.filterNot { it.attacker in state.battlefield }) }
            ?.filter { it.attacker in state.battlefield }?.ifEmpty { null } ?: return null
        val pairs = staged.mapNotNull { a ->
            val attacker = card(a.attacker) ?: return@mapNotNull null
            CombatPair(pairId(attacker), listOf(CombatOccurrence(attackId(attacker), attacker)), attackTarget = target(a.target))
        }
        return CombatArea(COMBAT_ID, pairs).takeIf { pairs.isNotEmpty() || staged.isEmpty() }
    }

    /** WCRF3Q, R8XVPA: the Automatum log's note (once per game, turn, step and entries) of the entries of the table's [kind]
     * staging the board leaves out, as they name creatures not on the battlefield or not in this combat. */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private fun leftOut(game: String, state: GameState, kind: String, entries: List<Any>) {
        if (entries.isEmpty()) return
        val key = "$game/${state.turn.number}/${state.turn.step}/$entries"
        val seen = loggedStaging.load()
        if (key in seen) return
        loggedStaging.store((seen + key).toList().takeLast(50).toSet())
        AutomatumLog.mismatch(game, "combat staging", "The table's $kind staging names creatures not in this combat, left out: " +
            "$entries (turn ${state.turn.number} ${state.turn.step}, attackers ${state.combat?.attackers?.map { it.id }})")
    }
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val loggedStaging = kotlin.concurrent.atomics.AtomicReference<Set<String>>(emptySet())

    private fun suggestion(table: TableState, nodeId: String): AtwSuggestion? {
        val s = table.suggestion ?: return null
        val outcome = when (val r = s.result) {
            is arcanum.engine.setup.GameResult.Won -> if (r.winner == s.by) SuggestedResult.WIN else SuggestedResult.LOSS
            arcanum.engine.setup.GameResult.Draw -> SuggestedResult.DRAW
        }
        return AtwSuggestion("suggestion:${s.by.value}:$outcome", s.by.value, outcome, note = s.note)
    }

    /**
     * The declaring player's own staging of a declaration made behind the head (CMB-028, CMB-034: their lanes and attack
     * choices, as the combat controls make them) at the point [key] ([keyOf]: the turn, step and combat it belongs to):
     * shown in place of the engine's combat and the table's shared drafts ([EngineDrafts]) while that point lasts, and
     * sent only as the declaration itself (I-D56 §12).
     */
    data class Staging(val key: String, val combat: CombatArea?, val records: List<org.asyncttwiz.protocol.EngineRecord> = emptyList()) {
        companion object {
            fun keyOf(state: GameState): String = "${state.turn.number}/${state.turn.step}/${state.combat?.attackers?.map { it.id }}/" +
                "${state.combat?.blockersDeclaredBy}/${state.priority.holder}"
        }
    }

    private fun isDungeon(obj: GameObject): Boolean = obj.definition?.faces?.firstOrNull()?.types?.contains(arcanum.api.CardType.Dungeon) == true

    /** A dungeon in a player's command zone (CR 309.2b): its card on the board ([cardId]) and the room its venture marker is on. */
    data class ShownDungeon(val cardId: String, val name: String, val room: String)

    /** [player]'s dungeon in the command zone of [state] (a game's own engine state), or null. */
    fun dungeon(state: GameState, player: String): ShownDungeon? {
        val obj = state.command.mapNotNull { state.objects[it] }.firstOrNull { it.owner.value == player && isDungeon(it) } ?: return null
        val face = obj.definition?.faces?.firstOrNull() ?: return null
        val room = Inspect.ventureRoom(state, obj.id)?.let { face.rooms.getOrNull(it)?.name }.orEmpty()
        return ShownDungeon(obj.card.value, face.name, room)
    }

    /** ATW's turn position of the engine's [step] (its label for the table's words). */
    fun position(step: arcanum.api.TurnStep): TurnPosition = EngineCodec.position(step)

    /** The combat area's id, and below those of its pairs and copies, by card: UUIDs, as the table's combat controls
     * name them in their actions ([org.asyncttwiz.protocol.CombatStackRules.valid]: unstaging, retargeting, blocking). */
    const val COMBAT_ID = "c0b4a7a2-0000-5000-8000-00000000c0b4"
    fun pairId(attacker: String): String = org.asyncttwiz.protocol.CommandIds.uuidV5(COMBAT_ID, "pair/$attacker")
    fun attackId(attacker: String): String = org.asyncttwiz.protocol.CommandIds.uuidV5(COMBAT_ID, "attack/$attacker")
    fun blockId(blocker: String): String = org.asyncttwiz.protocol.CommandIds.uuidV5(COMBAT_ID, "block/$blocker")

    /** Card data for an object nobody can read (no definition): shown as a nameless card. */
    private val UNKNOWN: SubmittedDefinition = run {
        val face = org.asyncttwiz.protocol.DefinitionFace("00000000-0000-5000-8000-000000000000", "Card")
        val card = org.asyncttwiz.protocol.CardDefinition("Card", listOf(face))
        SubmittedDefinition(org.asyncttwiz.protocol.DeckJson.digest(card), card)
    }
}
