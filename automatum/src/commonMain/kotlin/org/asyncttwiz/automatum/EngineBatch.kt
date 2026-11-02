package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.state.CardId
import arcanum.engine.state.CombatState
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BattlefieldPlacement
import org.asyncttwiz.protocol.CombatAction
import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatRole
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.IdentityGap
import org.asyncttwiz.protocol.MoveDestination
import org.asyncttwiz.protocol.MoveInsertion
import org.asyncttwiz.protocol.MoveSource
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgCause
import org.asyncttwiz.protocol.MtgFacts
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStamp
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StackAction
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.zoneContainer

/** An engine change ATW can't say with its actions; the call can't be sent (report it, D32's way out: turn Automatum off). */
class Unmappable(message: String) : IllegalStateException(message)

/**
 * One engine call's changes as one ATW batch (SYS-010, D59): [actions] with their [tags], and [state], the play state
 * after them as ATW's reducer makes it (every member was run through it while planning, with the member request ids
 * the batch's [GameCommand] gives them, so the ids it creates are the ones the server will create).
 */
data class BatchPlan(val actions: List<PlayAction>, val tags: List<MtgTag?>, val state: PlayState) {
    /** The batch to send with the planned command's request id. */
    val action: GameAction.PlayBatch get() = GameAction.PlayBatch(actions, tags.dropLastWhile { it == null })
}

/**
 * Turns engine [Change]s into ATW actions (integration spec §2 step 4, I0 "change mapping").
 *
 * Each change is applied to the engine state and then said in ATW:
 * - its structure with ATW's own actions: moves (`MoveCard`), a spell going on the Stack as an entry pointing at its card
 *   (`AddEffect`, STK-001) and leaving it by pulling the entry, abilities as entries of their source, tokens
 *   (`CreateToken`/`RemoveToken`), shuffles (`ShuffleLibrary` with the seed the host answered with), library order,
 *   steps and turns (`ChooseStep`, `EndTurn`, `BeginTurn` for an extra turn), combat lanes;
 * - then every engine value ATW keeps is brought to the engine's: tapped, damage, face, face down, life with ATW's own
 *   actions; zone-change counts and timestamps (`Restamp`), typed counters, attachments, ledgers, mana pools, priority,
 *   Stack items and engine records with [MtgAction]s.
 *
 * The first action of each change carries its tag ([MtgTag]: meanings, what caused it, and its part of the engine's event
 * log, [EngineLog]); an event with no action of its own (damage dealt, a coin flip) rides on the next one.
 */
object EngineBatch {
    /**
     * The batch for [call]'s changes, sent by [actor] as [command] (its request id names the members). The plan's
     * [BatchPlan.state] is the board the server will make of it: the acting client shows it as its prediction without
     * running the members through the reducer again.
     *
     * @throws Unmappable when a change can't be said with ATW's actions, or the batch wouldn't pass the server's checks
     *   ([GameWire.valid]) or fit one game message.
     */
    fun plan(call: EngineCall, actor: String, command: GameCommand): BatchPlan {
        val game = call.state.gameId
        val plan = try {
            Planner(call, actor, command).run()
        } catch (problem: Unmappable) {
            // I-D43: a batch that can't be said in ATW is a mapping problem worth a report
            AutomatumLog.refusal(game, "plan of call ${call.logNumber ?: "-"}", problem.message.orEmpty())
            throw problem
        }
        AutomatumLog.add(AutomatumEntry.Batch(AutomatumLog.now(), game, call.logNumber, call.changes.size, plan.actions.size,
            Automatum.flowOpen(call.state.board), Automatum.flowOpen(plan.state.board)))
        return plan
    }

    /** The largest command one game message carries: a quarter of the game socket's text message (I-D14), so the
     * revision answering it (each member recorded with its delta) fits one message too. */
    const val MAX_COMMAND_BYTES = GameWire.MAX_MESSAGE_BYTES / 4
}

private class Planner(private val call: EngineCall, private val actor: String, command: GameCommand) {
    private val template = command.copy(action = GameAction.PlayBatch(listOf(PlayAction.PassAttention)))
    private var atw: PlayState = call.state
    private val engine: GameState = call.start.deepCopy()
    private val ids = call.ids
    private val actions = mutableListOf<PlayAction>()
    private val tags = mutableListOf<MtgTag?>()
    private val pendingLog = mutableListOf<LogOp>()
    private var pendingCause: MtgCause? = null
    private val shuffles = call.shuffles.toMutableList()
    /** The ATW Stack entry of each engine Stack object. */
    private val entries: MutableMap<ObjectId, String> = EngineState.entries(atw.board)
        .mapNotNull { e -> e.item?.let { (if (it.data.isEmpty()) ids.obj(it.id)!! else EngineCodec.stackItem(it).id) to e.id } }.toMap().toMutableMap()

    /** The index in [EngineCall.changes] of the change being said. */
    private var current = 0

    fun run(): BatchPlan {
        for ((index, change) in call.changes.withIndex()) {
            current = index
            val logged = engine.log.size
            val objectsBefore = HashMap(engine.objects)
            val cardsBefore = board.cards
            engine.apply(change)
            when {
                engine.log.size > logged -> pendingLog += LogOp.Add(engine.log.last())
                engine.log.size < logged -> pendingLog += LogOp.Drop
            }
            val first = actions.size
            primary(change, first)
            settle(changedObjects(objectsBefore) + changedCards(cardsBefore))
            if (actions.size > first) {
                val tag = tags[first]
                val data = if (pendingLog.isEmpty()) "" else codec.encode(TagData(pendingLog.toList()))
                tags[first] = tag(tag?.meanings ?: meanings(change), tag?.causedBy ?: cause(change) ?: pendingCause, data, tag?.folded == true)
                pendingLog.clear(); pendingCause = null
            } else if (pendingLog.isNotEmpty()) pendingCause = pendingCause ?: cause(change)
        }
        // Each change settled the values it touched; one look over everything makes sure nothing was missed.
        current = call.changes.lastIndex
        settle(null)
        // I-D34: with Automatum on the move follows who must act (folded into the batch's event, I-D35)
        if (actions.isNotEmpty()) acting(engine)?.let { who -> if (board.effectiveAttentionHolder != who) play(PlayAction.PassAttention) }
        if (pendingLog.isNotEmpty()) {
            if (actions.isEmpty()) throw Unmappable("engine events without any ATW action to carry them: ${call.changes}")
            val last = tags.last()
            val earlier = last?.data?.takeIf { it.isNotEmpty() }?.let { codec.decode(it).log }.orEmpty()
            tags[tags.lastIndex] = tag(last?.meanings.orEmpty(), last?.causedBy ?: pendingCause, codec.encode(TagData(earlier + pendingLog)),
                last?.folded == true)
        }
        // #4 (SYS-057): the batch applies only to the board it was planned on; the first member names it.
        if (actions.isNotEmpty()) tags[0] = (tags[0] ?: MtgTag()).copy(board = MtgFacts.digest(call.state.board))
        val plan = BatchPlan(actions.toList(), tags.toList(), atw)
        if (plan.actions.isNotEmpty()) check(plan)
        return plan
    }

    /** The batch passes the server's own checks ([GameWire.valid]) and fits one game message; else it can't be sent. */
    private fun check(plan: BatchPlan) {
        val command = template.copy(action = plan.action)
        val largestTag = plan.tags.maxOfOrNull { it?.data?.encodeToByteArray()?.size ?: 0 } ?: 0
        if (!GameWire.valid(command)) throw Unmappable("the server would refuse this batch: ${plan.actions.size} actions, " +
            "largest tag $largestTag bytes")
        val bytes = GameWire.json.encodeToString(GameCommand.serializer(), command).encodeToByteArray().size
        if (bytes > EngineBatch.MAX_COMMAND_BYTES) throw Unmappable("the batch is $bytes bytes, more than one game message takes")
    }

    // ---- Playing one member through ATW's reducer

    /** Plays [action] as the next member; false when it changes nothing (then it isn't part of the batch). Engine
     * bookkeeping is tagged to be folded into the visible event before it ([folds]). */
    private fun play(action: PlayAction, tag: MtgTag? = null): Boolean {
        val sent = if (actions.isNotEmpty() && folds(action)) (tag ?: MtgTag()).copy(folded = true) else tag
        val member = PlayBatches.member(template, actions.size, action).let { if (sent == null) it else it.copy(action = GameAction.Play(action, sent)) }
        val change = GameplayRules.preview(atw, actor, member)
        if (change.reason != null) throw Unmappable("ATW refused $action (${change.reason})")
        if (change.mutation == null) return false
        atw = change.state!!
        actions += action
        tags += sent
        return true
    }

    /**
     * Whether [action] is shown inside the visible event before it (I-D22, I-D35: history events are natural branching
     * points, one per point where a player can act; [org.asyncttwiz.protocol.MtgFacts.folds]): everything after the
     * batch's first member. A call runs from one player's input to the next point where a player can act, so the batch
     * is one event: the input leads it, and what Automatum did by itself (turn-based actions, triggers, resolutions,
     * state-based actions, the host's passes, bookkeeping) is folded into it.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun folds(action: PlayAction): Boolean = actions.isNotEmpty()

    /** Priority the host gives before the input (a call's first changes, [HostPriority]) waits for the first member the
     * input itself makes, so that leads the batch's event (I-D35, i12review #11); the final settle sends it anyway. */
    private fun waitsForInput(): Boolean = actions.isEmpty() && current !in call.ownPasses && current < call.changes.lastIndex

    /**
     * Who must act once the call is done (I-D34): the player an open decision belongs to, else the priority holder, else
     * the player who declares attackers or blockers (CR 508.1, 509.1); null when the game is over.
     */
    private fun acting(s: GameState): String? {
        if (arcanum.engine.Engine.result(s) != null) return null
        s.pending?.decision?.player?.let { return it.value }
        s.priority.holder?.let { return it.value }
        return when (s.turn.step) {
            TurnStep.DeclareAttackers -> s.turn.active.value.takeIf { s.combat == null }
            TurnStep.DeclareBlockers -> s.players.firstOrNull { it.id != s.turn.active }?.id?.value
                .takeIf { s.combat?.let { c -> !c.blocksDecided && c.attackers.isNotEmpty() } == true }
            else -> null
        }
    }

    private val board get() = atw.board
    /** Tags name card definitions by reference (D33, D60: small, and no hidden card's identity). */
    private val codec = TagCodec.of(call.state, call.definitions)

    // Lookups by id, rebuilt when the board changes (planning looks up cards many times per member)
    private var indexed: org.asyncttwiz.protocol.BoardState? = null
    private var cardIndex: Map<String, PlayCard> = emptyMap()
    private var containerIndex: Map<String, PlayContainer> = emptyMap()
    private fun index() {
        if (indexed === board) return
        cardIndex = board.cards.associateBy { it.id }
        containerIndex = board.containers.associateBy { it.id }
        indexed = board
    }
    private fun card(id: String): PlayCard? { index(); return cardIndex[id] }
    private fun containerOf(card: PlayCard): PlayContainer { index(); return containerIndex.getValue(card.containerId) }
    private val facesByDigest = mutableMapOf<String, List<org.asyncttwiz.protocol.DefinitionFace>>()
    /** [Derived.faces], by digest (a digest names the same card data for good). */
    private fun faces(card: PlayCard) = facesByDigest[card.definition]
        ?: Derived.faces(atw, card).also { if (it.isNotEmpty()) facesByDigest[card.definition] = it }
    private val owners = mutableMapOf<String, String>()
    private fun owner(card: PlayCard) = if (card.token) Derived.owner(atw, card) else owners.getOrPut(card.id) { Derived.owner(atw, card) }
    private fun atwId(card: CardId): String = call.hidden[card] ?: ids.atw(card)
    private fun atwCard(o: ObjectId): PlayCard = (o as? ObjectId.Card)?.let { card(atwId(it.card)) } ?: throw Unmappable("no ATW card for $o")
    private fun container(zone: PlayZone, player: String): PlayContainer =
        board.zoneContainer(player, zone) ?: throw Unmappable("no $zone of $player")
    private fun digest() = GameplayRules.stackDigest(atw)
    private fun source(card: PlayCard) = MoveSource(card.containerId, card.placement)
    /** ATW cards that are spells on the engine's Stack: their card stays in its ATW container (STK-001). */
    private fun spellCards(): List<String> = entries.keys.filterIsInstance<ObjectId.Card>().map { atwId(it.card) }

    // ---- Structure

    private fun primary(change: Change, first: Int) {
        when (change) {
            is Change.ZoneChange -> {
                change.moves.forEach { m ->
                    val at = actions.size
                    move(m)
                    if (actions.size > at) tags[at] = tag(m.meanings.map(::name), cause(change), "", tags[at]?.folded == true)
                }
                // Several cards into one library or hand: each went next to the cards already there; now the engine's order
                change.moves.map { it.after }.filter { it.zone == Zone.Library || it.zone == Zone.Hand }
                    .map { it.zone to it.owner }.distinct().forEach { (zone, owner) ->
                        if (zone == Zone.Library) arrange(container(PlayZone.LIBRARY, owner.value), engine.player(owner).library)
                        else arrange(container(PlayZone.HAND, owner.value), engine.player(owner).hand)
                    }
            }
            is Change.Created -> change.objects.forEach { create(it.obj, change.by) }
            is Change.Removed -> change.objects.forEach { placed ->
                card(atwId(placed.obj.card))?.takeIf { it.token }?.let { play(PlayAction.RemoveToken(it.id)) }
            }
            is Change.Shuffle -> shuffle(change)
            is Change.Reorder -> reorder(change)
            is Change.Step -> step(change.before, change.after)
            is Change.Combat -> combat(change.after)
            is Change.StackItemAdded -> if (change.index != null) addItem(change.item)
            is Change.StackItemRemoved -> if (change.item.id is ObjectId.StackItem) entries.remove(change.item.id)?.let { entry ->
                if (board.stackEffects.any { it.id == entry }) play(PlayAction.Stack(StackAction.RemoveEffect(entry)))
            }
            is Change.Reverted -> primary(change.change.inverse(), first)
            else -> Unit
        }
    }

    private fun move(m: Move) {
        val card = atwCard(m.before.id)
        when {
            m.to == Zone.Stack -> {
                // D35 in ATW's terms (STK-001, STK-003): a Stack entry points at the spell's card, which stays put; a spell
                // cast face down (CR 702.37c, 708.4) has a face-down entry (STK-005), so nobody else sees what it is
                val before = board.stackEffects.map { it.id }.toSet()
                play(PlayAction.Stack(StackAction.AddEffect(card.id, digest(), faceDown = true.takeIf { m.after.faceDown })))
                entries[m.after.id] = board.stackEffects.single { it.id !in before }.id
            }
            m.from == Zone.Stack -> {
                val entry = entries.remove(m.before.id) ?: throw Unmappable("no Stack entry for ${m.before.id}")
                val destination = destination(m.after, card)
                // A face-down spell becomes a face-down permanent (CR 708.4)
                val faceDown = true.takeIf { m.after.zone == Zone.Battlefield && m.after.faceDown }
                if (board.stackEffects.any { it.id == entry }) play(PlayAction.MoveCard(card.id, source(card), destination,
                    removeStackEntryId = entry, faceDown = faceDown))
                else play(PlayAction.MoveCard(card.id, source(card), destination, faceDown = faceDown))
            }
            else -> play(PlayAction.MoveCard(card.id, source(card), destination(m.after, card),
                faceDown = true.takeIf { m.after.zone == Zone.Battlefield && m.after.faceDown }))
        }
    }

    /**
     * Where [to] is in ATW: its owner's zone (its controller's Battlefield), at the place the engine put it. In a library
     * or hand it goes right after the nearest card above it in the engine's order that ATW already has there (on top
     * when there is none): when one change moves several cards there, the others may not have arrived yet, and the
     * change then puts them all in the engine's order ([arrange]).
     */
    private fun destination(to: GameObject, card: PlayCard): MoveDestination {
        val owner = to.owner.value
        /** The card to go after (null: the top) and the card now after that one (null: the bottom). */
        fun place(container: PlayContainer, order: List<ObjectId>): Pair<String?, String?> {
            val spells = spellCards().toSet()
            val present = board.containers.single { it.id == container.id }.cards.filter { it != card.id && it !in spells }
            val here = present.toSet()
            val i = order.indexOf(to.id)
            val after = order.subList(0, i.coerceAtLeast(0)).asReversed().map { atwCard(it).id }.firstOrNull { it in here }
            val before = if (after == null) present.firstOrNull() else present.getOrNull(present.indexOf(after) + 1)
            return after to before
        }
        return when (to.zone) {
            Zone.Library -> {
                val library = container(PlayZone.LIBRARY, owner)
                val (after, before) = place(library, engine.player(to.owner).library)
                when {
                    after == null -> MoveDestination(library.id, MoveInsertion.TOP)
                    before == null -> MoveDestination(library.id, MoveInsertion.BOTTOM)
                    else -> MoveDestination(library.id, MoveInsertion.GAP, afterId = after, beforeId = before, pendingIds = spellCards() - card.id)
                }
            }
            Zone.Hand -> {
                val hand = container(PlayZone.HAND, owner)
                val (after, before) = place(hand, engine.player(to.owner).hand)
                if (before == null) MoveDestination(hand.id, MoveInsertion.UNORDERED)
                else MoveDestination(hand.id, MoveInsertion.GAP, afterId = after, beforeId = before, pendingIds = spellCards() - card.id)
            }
            Zone.Graveyard -> MoveDestination(container(PlayZone.GRAVEYARD, owner).id, MoveInsertion.UNORDERED)
            Zone.Exile -> MoveDestination(container(PlayZone.EXILE, owner).id, MoveInsertion.UNORDERED)
            Zone.Battlefield -> {
                val field = container(PlayZone.BATTLEFIELD, (to.controller ?: to.owner).value).id
                MoveDestination(field, MoveInsertion.EMPTY, BattlefieldPlacement.cell(atw, field, card, faceDown = to.faceDown))
            }
            else -> throw Unmappable("ATW has no ${to.zone} zone")
        }
    }

    private fun create(o: GameObject, by: arcanum.engine.change.CausedBy?) {
        // An emblem is only its record (I-D25), which the values bring in
        if (o.zone == Zone.Command && !o.token) { emblemTexts[o.id] = emblemText(o, by); return }
        if (o.copyOfCard) { createCopy(o); return }
        if (o.zone != Zone.Battlefield) throw Unmappable("a token created in ${o.zone}")
        val definition = o.definition ?: throw Unmappable("a token without a definition")
        val before = board.cards.map { it.id }.toSet()
        play(PlayAction.CreateToken(AtwTokens.submitted(definition)))
        val token = board.cards.single { it.id !in before }
        ids.alias(token.id, o.card)
        val controller = (o.controller ?: o.owner).value
        val field = container(PlayZone.BATTLEFIELD, controller)
        if (token.containerId != field.id) play(PlayAction.MoveCard(token.id, source(token),
            MoveDestination(field.id, MoveInsertion.EMPTY, BattlefieldPlacement.cell(atw, field.id, token))))
    }

    /**
     * A copy of a card (CR 707.12), made in its owner's Exile where the card is: an ATW token card (it ceases to exist
     * where ATW's tokens do, CR 704.5e, TBL-103) showing the copied card's own card data, as ATW's manual copies do
     * (`CloneToken`); the engine's id, definition and copy flag are in its ledger. Copies made elsewhere can't be said.
     */
    private fun createCopy(o: GameObject) {
        if (o.zone != Zone.Exile) throw Unmappable("a copy of a card made in ${o.zone}")
        val definition = o.definition ?: throw Unmappable("a copy of a card without a definition")
        val before = board.cards.map { it.id }.toSet()
        play(PlayAction.CreateToken(copyData(definition), containerId = container(PlayZone.EXILE, o.owner.value).id))
        ids.alias(board.cards.single { it.id !in before }.id, o.card)
    }

    /** The copied card's own ATW card data as a token's ([definition] is a card of the game's), else what ATW shows of a
     * token with [definition] ([AtwTokens]). */
    private fun copyData(definition: arcanum.api.CardDefinition): org.asyncttwiz.protocol.SubmittedDefinition {
        val digest = call.definitions.entries.firstOrNull { it.value == definition }?.key
        val card = atw.definitions.firstOrNull { it.digest == digest }?.definition?.copy(token = true)
            ?: return AtwTokens.submitted(definition)
        return org.asyncttwiz.protocol.SubmittedDefinition(org.asyncttwiz.protocol.DeckJson.digest(card), card)
    }

    private fun shuffle(change: Change.Shuffle) {
        val answer = shuffles.firstOrNull { it.player == change.player } ?: throw Unmappable("a shuffle the host didn't answer")
        shuffles.remove(answer)
        val library = container(PlayZone.LIBRARY, change.player.value)
        play(PlayAction.ShuffleLibrary(library.id, answer.seed))
        // CR 701.24b: cards the shuffle left out go back on top as they were
        val kept = change.after.filter { a -> change.before.any { it.id == a.id } }
        for (k in kept.reversed()) {
            val card = atwCard(k.id)
            play(PlayAction.MoveCard(card.id, source(card), MoveDestination(library.id, MoveInsertion.TOP)))
        }
        if (board.zoneContainer(change.player.value, PlayZone.LIBRARY)!!.cards.filter { it !in spellCards() } !=
            engine.player(change.player).library.map { atwCard(it).id }) throw Unmappable("the shuffle's order differs from ATW's")
    }

    private fun reorder(change: Change.Reorder) {
        val owner = change.owner?.value ?: return
        when (change.zone) {
            Zone.Library -> arrange(container(PlayZone.LIBRARY, owner), engine.player(change.owner!!).library)
            Zone.Hand -> arrange(container(PlayZone.HAND, owner), engine.player(change.owner!!).hand)
            Zone.Graveyard -> throw Unmappable("ATW keeps a graveyard in arrival order")
            else -> Unit // the Battlefield's and Exile's order are by timestamp (EngineState.from)
        }
    }

    /** Puts [container]'s cards in [order] by moving each misplaced card into its gap. */
    private fun arrange(container: PlayContainer, order: List<ObjectId>) {
        val desired = order.map { atwCard(it).id }
        val spells = spellCards().toSet()
        for (i in desired.indices) {
            val current = board.containers.single { it.id == container.id }.cards.filter { it !in spells }
            if (current[i] == desired[i]) continue
            val card = card(desired[i])!!
            play(PlayAction.MoveCard(card.id, source(card), MoveDestination(container.id, MoveInsertion.GAP,
                afterId = desired.getOrNull(i - 1), beforeId = current[i], pendingIds = (spells - card.id).toList())))
        }
    }

    private fun step(before: TurnState, after: TurnState) {
        if (after.number != before.number || after.active != before.active) {
            if (after.number != before.number + 1) throw Unmappable("turn ${after.number} of ${after.active}")
            // I-D12: the turn change names whose turn begins (CR 500.7: an extra turn is the same player's again).
            val other = board.players.singleOrNull { it.accountId != before.active.value }?.accountId
            play(if (after.active.value == other) PlayAction.EndTurn else PlayAction.BeginTurn(after.active.value))
        }
        val target = EngineCodec.position(after.step, firstStrike = after.step == TurnStep.CombatDamage && firstStrikeBegins())
        // I-D22, StepRun: a step the call goes on from (nobody could act in it: the host passed for everyone, or it has no
        // priority) is a passed step; the last step change of the call is the step players act in and gets the move
        if (TurnPosition.of(board.turn) != target) play(PlayAction.ChooseStep(target, through = laterStep()))
    }

    /** A step's beginning (CR 500): a turn-state change that only counts a land or an extra turn begins none. */
    private fun begins(change: Change): Boolean = change is Change.Step &&
        (Meaning.StepBegan in change.meanings || change.after.step != change.before.step || change.after.number != change.before.number)

    /** Whether a later change of the call begins another step or turn (the second combat damage step too, CR 510.4). */
    private fun laterStep(): Boolean = call.changes.subList(current + 1, call.changes.size).any(::begins)

    /** Whether the combat damage step beginning now is the first-strike one (I-D31, CR 510.4): the engine marks its combat
     * so right after the step begins, before any other step does. */
    private fun firstStrikeBegins(): Boolean {
        for (change in call.changes.subList(current + 1, call.changes.size)) {
            if (begins(change)) return false
            if (change is Change.Combat && change.after?.firstStrikeStep == true && change.before?.firstStrikeStep != true) return true
        }
        return false
    }

    /** The combat lanes show the engine's combat (CMB-*): a pair per attacker with its target, blockers in the pair of
     * the first attacker they block, also blocking the pairs of the others (CMB-031). The engine reads the combat from
     * its record, the lanes only show it. */
    private fun combat(after: CombatState?) {
        if (after == null) {
            board.combat?.let { play(PlayAction.Combat(CombatAction.Close(it.id))) }
            return
        }
        if (board.combat == null) play(PlayAction.Combat(CombatAction.Open))
        val areaId = board.combat!!.id
        val wanted = (after.attackers.map { it.id } + after.blockers.map { it.id }).map { atwCard(it).id }.toSet()
        CombatStackRules.occurrences(board.combat).filter { it.sourceId !in wanted }
            .forEach { play(PlayAction.Combat(CombatAction.Remove(areaId, it.id))) }
        val placed = mutableSetOf<String>()
        for (attacker in after.attackers) {
            val card = atwCard(attacker.id)
            val target = (attacker.target as? TargetRef.Object)?.let { atwCard(it.id).id }
            val occurrence = CombatStackRules.occurrences(board.combat).firstOrNull { it.sourceId == card.id }
            if (occurrence != null) {
                // #2 (CR 509.1a, CMB-028): every declared attacker has a pair of its own, so each is blocked on its own; one
                // drafted beside another (CMB-027) moves into a new pair after it
                val pair = board.combat!!.pairs.first { p -> p.attackers.any { it.id == occurrence.id } }
                if (pair.attackers.none { it.sourceId in placed } || pair.blockers.isNotEmpty()) { placed += card.id; continue }
                val pairs = board.combat!!.pairs
                val next = pairs.getOrNull(pairs.indexOf(pair) + 1)?.id
                play(PlayAction.Combat(CombatAction.Place(card.id, CombatDestination(areaId, CombatRole.ATTACKER,
                    pairGap = IdentityGap(pair.id, next), attackTarget = target),
                    expectedOccurrence = CombatStackRules.location(board.combat!!, occurrence.id))))
                placed += card.id
                continue
            }
            placed += card.id
            play(PlayAction.Combat(CombatAction.Place(card.id, CombatDestination(areaId, CombatRole.ATTACKER,
                pairGap = IdentityGap(board.combat!!.pairs.lastOrNull()?.id), attackTarget = target))))
        }
        for (blocker in after.blockers) {
            val card = atwCard(blocker.id)
            val pairs = blocker.attackers.mapNotNull { a -> atwCard(a).id.let { id ->
                board.combat!!.pairs.firstOrNull { p -> p.attackers.any { it.sourceId == id } }?.id } }.distinct()
            if (pairs.isEmpty()) continue
            val existing = CombatStackRules.occurrences(board.combat).firstOrNull { it.sourceId == card.id }
            val now = existing?.let { CombatStackRules.blockedPairs(board.combat, it.id) }.orEmpty()
            // A drafted block stays where it was drafted while it blocks the same attackers
            if (existing != null && now.toSet() == pairs.toSet()) continue
            if (existing != null && now.isEmpty()) continue
            val ordered = if (now.firstOrNull() in pairs) listOf(now.first()) + (pairs - now.first()) else pairs
            play(PlayAction.Combat(CombatAction.SetBlocks(areaId, card.id, ordered, now)))
        }
    }

    private fun addItem(item: StackItem) {
        if (engine.stack.lastOrNull() != item.id) throw Unmappable("an ability put on the Stack below its top")
        val before = board.stackEffects.map { it.id }.toSet()
        when (val kind = item.kind) {
            is StackItemKind.SpellCopy -> {
                val copied = entries[kind.copied] ?: throw Unmappable("a copy of ${kind.copied}, which has no Stack entry")
                play(PlayAction.Stack(StackAction.CopyEffect(copied, digest())))
            }
            else -> {
                val source = item.source?.let { s -> (s as? ObjectId.Card)?.let { card(atwId(it.card)) } }
                if (source != null) play(PlayAction.Stack(StackAction.AddEffect(source.id, digest())))
                // I-D13: an ability whose source isn't a card on the table is an entry of its own, showing its text.
                else play(PlayAction.Stack(StackAction.AddAbility(AtwTokens.ability(item.snapshots.firstOrNull { it.id == item.source }
                    ?.characteristics?.name, item.ability), item.controller.value, digest())))
            }
        }
        entries[item.id] = board.stackEffects.single { it.id !in before }.id
    }

    // ---- Values: bring every engine value ATW keeps to the engine's

    /**
     * Brings ATW's values to the engine's: those of the cards in [dirty] (ATW card ids; null: every card) and of the
     * players, priority, Stack items and records. A card's values depend only on its engine object, its ATW card and its
     * Stack item, so a card neither side touched needs no look; each further pass looks at what the last one changed.
     */
    private fun settle(dirty: Set<String>?) {
        var cards = if (registerHidden()) null else dirty?.let { it + engine.stack.filterIsInstance<ObjectId.Card>().map { o -> atwId(o.card) } }
        repeat(4) {
            val before = board.cards
            if (!values(cards)) return
            cards = changedCards(before)
        }
        throw Unmappable("ATW's values don't settle on the engine's")
    }

    /** The ATW cards whose engine objects entered, left or changed since [before]. */
    private fun changedObjects(before: Map<ObjectId, GameObject>): Set<String> {
        val changed = mutableSetOf<String>()
        for ((id, o) in engine.objects) {
            val old = before[id]
            if (old == null || old !== o && old != o) changed += atwId((id as? ObjectId.Card ?: continue).card)
        }
        for (id in before.keys) if (id !in engine.objects) changed += atwId((id as? ObjectId.Card ?: continue).card)
        return changed
    }

    /** The ATW cards that differ from [before] (new ones included). */
    private fun changedCards(before: List<PlayCard>): Set<String> {
        if (before === board.cards) return emptySet()
        val old = before.associateBy { it.id }
        return board.cards.filter { c -> old[c.id].let { it == null || it !== c && it != c } }.mapTo(mutableSetOf()) { it.id }
    }

    /** A viewer's new card ids (D60) name ATW's cards from the moment they appear, counting zone changes from there.
     * True when it named a card anew (then every card's values get a look). */
    private fun registerHidden(): Boolean {
        if (call.hidden.isEmpty()) return false
        var named = false
        val objects = engine.objects.values.associateBy { it.card }
        for ((hidden, real) in call.hidden) {
            if (ids.atw(hidden) == real) continue
            val o = objects[hidden] ?: continue
            val card = card(real) ?: continue
            ids.alias(real, hidden, card.zoneChanges - o.id.zoneChanges)
            named = true
        }
        return named
    }

    /** One pass over the values of [cards] (null: every card) and the game's; true when it changed something. */
    private fun values(cards: Set<String>?): Boolean {
        val start = actions.size
        val objects = engine.objects.values.associateBy { it.card }
        for (id in cards ?: board.cards.map { it.id }) {
            if (card(id) == null) continue
            val o = objects[ids.engine(id)] ?: continue
            cardValues(id, o)
        }
        for (p in board.players) playerValues(p.accountId)
        val priority = engine.priority.takeUnless { it.holder == null && it.passes == 0 }?.let { MtgPriority(it.holder?.value, it.passes) }
        if (board.priority != priority && !waitsForInput()) play(PlayAction.Mtg(MtgAction.SetPriority(board.priority, priority)))
        for (entry in EngineState.entries(board)) {
            val key = entries.entries.firstOrNull { it.value == entry.id }?.key ?: continue
            val wanted = engine.stackItems[key]?.let { EngineCodec.stackItem(it, ids::ref) }
                ?: engine.obj(key)?.takeIf { it.zone == Zone.Stack }?.let { EngineCodec.casting(ids.ref(it.id), (it.controller ?: it.owner).value, it.timestamp) }
            if (entry.item != wanted) play(PlayAction.Mtg(MtgAction.SetStackItem(entry.id, entry.item, wanted)))
        }
        // I-D33 (#7): early answers stay while their decision can still open; used ones and stale ones go
        val wanted = (records() + PreAnswers.kept(board, engine, ids, call.preAnswered)).associateBy { it.id }
        val current = board.engineRecords.associateBy { it.id }
        for (id in (current.keys + wanted.keys).sorted()) if (current[id] != wanted[id])
            play(PlayAction.Mtg(MtgAction.SetRecord(id, current[id], wanted[id])))
        return actions.size > start
    }

    private fun cardValues(id: String, o: GameObject) {
        fun now() = card(id)!!
        val zone = containerOf(now()).zone
        val onField = zone == PlayZone.BATTLEFIELD
        if (onField && now().tapped != o.tapped) play(PlayAction.SetTapped(id, o.tapped))
        if (onField && (now().damage.toIntOrNull() ?: 0) != o.damage) play(PlayAction.SetDamage(id, if (o.damage == 0) "" else "${o.damage}"))
        if ((onField || zone == PlayZone.EXILE) && now().isFaceDown != o.faceDown) play(PlayAction.SetCovered(id, o.faceDown))
        val faces = faces(now())
        if (faces.size > 1 && o.face < faces.size && now().faceId != faces[o.face].faceId) play(PlayAction.SetFace(id, faces[o.face].faceId))
        fun ref() = MtgRef.Card(id, now().zoneChanges)
        val attached = o.attachedTo?.let(ids::ref)
        if (now().attachedTo != attached) play(PlayAction.Mtg(MtgAction.Attach(ref(), now().attachedTo, attached)))
        val stamp = MtgStamp(ids.zoneChanges(o.id), o.timestamp)
        if (MtgStamp(now().zoneChanges, now().timestamp) != stamp)
            play(PlayAction.Mtg(MtgAction.Restamp(id, MtgStamp(now().zoneChanges, now().timestamp), stamp)))
        val counters = EngineCodec.counters(o.counters).associateBy { it.kind }
        val held = now().typedCounters.associateBy { it.kind }
        for (kind in (held.keys + counters.keys).sorted()) if (held[kind] != counters[kind])
            play(PlayAction.Mtg(MtgAction.SetCounter(ref(), kind, held[kind], counters[kind])))
        val ledger = ledger(now(), o).text()
        if (now().ledger != ledger) play(PlayAction.Mtg(MtgAction.SetLedger(ref(), now().ledger, ledger)))
    }

    /** The ledger that makes [EngineState.from] give back [o] for [card]. */
    private fun ledger(card: PlayCard, o: GameObject): CardLedger {
        val container = containerOf(card)
        val derivedController = Derived.controller(o.zone, container.accountId, engine.stackItems[o.id]?.controller ?: (o.controller ?: o.owner))
        val faces = faces(card)
        val derivedFace = if (faces.size > 1 && o.face < faces.size) o.face else faces.indexOfFirst { it.faceId == card.faceId }.coerceAtLeast(0)
        return CardLedger(
            card = o.card.value.takeIf { (o.token || o.copyOfCard) && it != card.id && !ids.transient(o.card) },
            definition = o.definition.takeIf { o.token || o.copyOfCard },
            copyOfCard = o.copyOfCard,
            faceOverride = o.face.takeIf { it != derivedFace },
            controllerOverride = (o.controller?.value ?: "").takeIf { o.controller != derivedController },
            ownerOverride = o.owner.value.takeIf { it != owner(card) },
            phasedOut = o.phasedOut, marks = o.marks, deathtouchDamage = o.deathtouchDamage,
            controlSince = o.controlSince.takeIf { it != Derived.controlSince(o.zone, container.accountId, card) },
            noControlSince = o.controlSince == null && Derived.controlSince(o.zone, container.accountId, card) != null,
            memory = o.memory, facts = o.facts, goadedBy = o.goadedBy, counterOrder = EngineCodec.counterOrder(o.counters),
        )
    }

    private fun playerValues(account: String) {
        fun now() = board.players.single { it.accountId == account }
        val p = engine.player(arcanum.engine.state.PlayerId(account))
        val life = now().life.toIntOrNull()
        if (life != p.life) play(if (life == null) PlayAction.SetLife(account, "${p.life}")
            else PlayAction.AdjustLife(account, (p.life - life).let { if (it > 0) "+$it" else "$it" }))
        val ref = MtgRef.Player(account)
        val counters = EngineCodec.counters(p.counters).associateBy { it.kind }
        val held = now().typedCounters.associateBy { it.kind }
        for (kind in (held.keys + counters.keys).sorted()) if (held[kind] != counters[kind])
            play(PlayAction.Mtg(MtgAction.SetCounter(ref, kind, held[kind], counters[kind])))
        val pool = p.manaPool.map(EngineCodec::mana)
        if (now().manaPool != pool) play(PlayAction.Mtg(MtgAction.SetManaPool(account, now().manaPool, pool)))
        val ledger = PlayerLedger(p.startingLife, p.lost, p.designations, p.ledger, EngineCodec.counterOrder(p.counters)).text()
        if (now().ledger != ledger) play(PlayAction.Mtg(MtgAction.SetLedger(ref, now().ledger, ledger)))
        // I-D18: only its own player changes the setting.
        if (now().skipWhenNoChoice != p.skipWhenNoChoice) {
            if (account != actor) throw Unmappable("$actor can't change $account's Skip when I have no choice")
            play(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(p.skipWhenNoChoice)))
        }
    }

    /** [records] of the engine now, encoding again only the effect objects and delayed triggers that changed. */
    private val recordMemo = mutableMapOf<Any, Pair<Any, EngineRecord>>()
    private fun records(): List<EngineRecord> {
        fun memo(key: Any, value: Any, encode: () -> EngineRecord): EngineRecord =
            recordMemo[key]?.takeIf { it.first === value }?.second ?: encode().also { recordMemo[key] = value to it }
        return records(engine, board, codec,
            effect = { memo(it.id, it) { EngineCodec.effectRecord(it) } },
            delayed = { memo(it.id, it) { EngineCodec.delayedRecord(it) } },
            emblemText = { o -> board.engineRecords.firstOrNull { it.id == EngineCodec.emblemId(o) }?.text
                ?: emblemTexts[o.id] ?: EngineCodec.emblemText(o) })
    }

    /** The texts of emblems this call made (I-D25), when their abilities carry none: the quoted ability of what made them. */
    private val emblemTexts = mutableMapOf<ObjectId, String>()

    private fun emblemText(o: GameObject, by: arcanum.engine.change.CausedBy?): String {
        EngineCodec.emblemText(o).takeIf { it.isNotEmpty() }?.let { return it }
        val ability = by?.item?.let { engine.stackItems[it] ?: call.working.stackItems[it] }?.ability
        val made = ability?.let(AtwTokens::text)
        // "You get an emblem with "…"." (CR 114.2): the emblem's ability is the quoted text
        return made?.let { Regex("emblem with \"(.+)\"", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1) ?: it }
            ?: by?.name?.let { "Emblem of $it" } ?: "Emblem"
    }

    // ---- Tags

    private fun tag(meanings: List<String>, cause: MtgCause?, data: String, folded: Boolean = false): MtgTag? =
        MtgTag(meanings.distinct().take(16), cause, data, folded = folded)
            .takeIf { it.meanings.isNotEmpty() || cause != null || data.isNotEmpty() || folded }

    private fun name(meaning: Meaning): String =
        EngineCodec.json.encodeToJsonElement(Meaning.serializer(), meaning).jsonObject["type"]!!.jsonPrimitive.content

    private fun meanings(change: Change): List<String> = when (change) {
        is Change.ZoneChange -> change.moves.flatMap { it.meanings }
        is Change.Created -> change.meanings
        is Change.Removed -> change.meanings
        is Change.Status -> change.meanings
        is Change.Face -> change.meanings
        is Change.Counters -> change.meanings
        is Change.Life -> change.meanings
        is Change.ManaPool -> change.meanings
        is Change.StackItemAdded -> change.meanings
        is Change.StackItemRemoved -> change.meanings
        is Change.StackItemUpdated -> change.meanings
        is Change.EffectRemoved -> change.meanings
        is Change.DelayedTriggerRemoved -> change.meanings
        is Change.ObjectUpdated -> change.meanings
        is Change.Step -> change.meanings
        is Change.Priority -> change.meanings
        is Change.Combat -> change.meanings
        else -> emptyList()
    }.map(::name)

    private fun cause(change: Change): MtgCause? {
        val by = when (change) {
            is Change.ZoneChange -> change.by
            is Change.Created -> change.by
            is Change.Status -> change.by
            is Change.Counters -> change.by
            is Change.Life -> change.by
            is Change.Happened -> change.by
            is Change.StackItemRemoved -> change.by
            is Change.Attached -> change.by
            else -> null
        } ?: return null
        return MtgCause(by.controller.value, by.source?.let(ids::ref), by.name?.takeIf(Automatum::name), by.item?.let(ids::ref))
    }
}

/** The engine values ATW keeps as records (§4.3, [EngineCodec]): one per effect object and delayed trigger, the game
 * ledger, turn and combat facts, waiting triggers and the open flow. */
internal fun records(state: GameState, board: org.asyncttwiz.protocol.BoardState, codec: TagCodec,
    effect: (arcanum.engine.state.EffectObject) -> EngineRecord = EngineCodec::effectRecord,
    delayed: (arcanum.engine.state.DelayedTrigger) -> EngineRecord = EngineCodec::delayedRecord,
    emblemText: (GameObject) -> String = EngineCodec::emblemText): List<EngineRecord> = buildList {
    state.effects.values.forEach { add(effect(it)) }
    state.delayedTriggers.values.forEach { add(delayed(it)) }
    state.command.mapNotNull { state.objects[it] }.forEach { add(EngineCodec.emblemRecord(it, emblemText(it))) }
    if (state.ledger.isNotEmpty()) add(EngineRecord(EngineCodec.LEDGER, EngineRecordKind.Ledger, data = EngineCodec.encode(ledgerSerializer, state.ledger)))
    val turn = TurnExtras(state.turn.landsPlayed, state.turn.turnStarted.takeIf { it != Derived.turnStarted(board) },
        state.turn.extraTurns, state.skipFirstDraw, state.turn.number.takeIf { state.turn.landsPlayed.isNotEmpty() },
        TurnExtras.at(state.turn.number, EngineCodec.position(state.turn.step, state.combat?.firstStrikeStep == true))
            .takeIf { state.priority.passes > 0 })
    if (turn != TurnExtras()) add(EngineRecord(EngineCodec.TURN, EngineRecordKind.Turn, data = EngineCodec.encode(TurnExtras.serializer(), turn)))
    state.combat?.let { add(EngineCodec.combatRecord(it, state.turn.number)) }
    if (state.triggered.isNotEmpty())
        add(EngineRecord(EngineCodec.TRIGGERED, EngineRecordKind.Triggered, data = EngineCodec.encode(triggeredSerializer, state.triggered)))
    state.pending?.let { add(EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation,
        data = codec.encode(FlowRecord.serializer(), FlowRecord(state.viewer?.value, it)))) }
}.sortedBy { it.id }
