package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.protocol.HistoryIcon
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.TableChange as GameTableChange
import java.math.BigInteger

/**
 * Short texts of engine events (I-D56 step D: the strip, the timeline, the event chip, the Undo dialog and the notes):
 * who did what, from the viewer's side (HIS-091), never naming what the viewer can't see in [state] (HIS-062: hidden
 * cards read "a card").
 */
internal object EngineEventText {
    fun of(event: GameEvent, state: GameState?, viewer: String, names: Map<String, String>): String {
        fun who(player: PlayerId) = if (player.value == viewer) "You" else names[player.value] ?: "The other player"
        fun author() = if (event.author == viewer) "You" else names[event.author] ?: "A player"
        fun card(id: ObjectId?): String = name(id, state, viewer) ?: "a card"
        val step = event.engine?.steps?.firstOrNull { it.input != null }
        // HIS-091 (owner playtest 2026-11-04 #1, #2): only the player who assumed an answer reads it as assumed; to the
        // player it was assumed about it reads as their own answer, the same as if they had made it (passes read as
        // what they led to, §29.2)
        val assumed = if (step?.assumedBy?.value == viewer) " (assumed)" else ""
        // An assumed input is the other player's (§14: "Bob passes (assumed)"); an answer names no player, its event's author made it
        fun actor(): String = step?.assumedBy?.let { by -> names.keys.firstOrNull { it != by.value }?.let { who(PlayerId(it)) } }
            ?: author()
        val input = step?.input
        return when (input) {
            null -> tableText(event, author())
            is Input.StartGame -> "The game began"
            is Input.Cast -> "${who(input.player)} cast ${card(input.card)}"
            is Input.PlayLand -> "${who(input.player)} played ${card(input.card)}"
            // STK-046: an ability is named as its source's ability, never as if the card were cast
            is Input.Activate -> "${who(input.player)} activated ${name(input.source, state, viewer)?.let { "$it's ability" } ?: "an ability"}"
            // §29.2 (owner 2026-11-04): passes are implied: a pass reads only as what it led to ("Storm Spark resolved",
            // "Turn 3 → Bob", the new step); one that led to nothing shows nowhere ([hidden])
            is Input.PassPriority -> passOutcome(event, state, viewer, names).orEmpty()
            is Input.DeclareAttackers -> if (input.attacks.isEmpty()) "${who(input.player)}: no attack" else "${who(input.player)} attacked with ${input.attacks.size}"
            is Input.DeclareBlockers -> if (input.blocks.isEmpty()) "${who(input.player)}: no blocks$assumed" else "${who(input.player)} blocked with ${input.blocks.size}$assumed"
            is Input.SetupAnswer -> when (input.answer) {
                Answer.Option("Keep") -> "${who(input.player)} kept$assumed"
                Answer.Option("Mulligan") -> "${who(input.player)} took a mulligan"
                else -> "${who(input.player)} chose"
            }
            is Input.Answer -> when (input.answer) {
                Answer.Option("Keep") -> "${actor()} kept$assumed"
                else -> "${actor()} chose$assumed"
            }
            is Input.Concede -> "${who(input.player)} conceded"
            is Input.AgreedResult -> "The result was agreed"
            else -> "${author()} acted"
        }
    }

    /** What the table part of a table-only event says. */
    private fun tableText(event: GameEvent, author: String): String = when {
        event.table.any { it is GameTableChange.AttackDraft } -> (event.table.firstNotNullOfOrNull { it as? GameTableChange.AttackDraft }!!.after
            ?.let { "$author staged ${it.size} ${if (it.size == 1) "attacker" else "attackers"}" } ?: "$author cleared the attack staging")
        event.table.any { it is GameTableChange.BlockDraft } -> (event.table.firstNotNullOfOrNull { it as? GameTableChange.BlockDraft }!!.after
            ?.let { "$author arranged ${it.size} ${if (it.size == 1) "block" else "blocks"}" } ?: "$author cleared the blocks")
        event.table.any { it is GameTableChange.EarlyAnswered } ->
            if (event.table.any { it is GameTableChange.EarlyAnswered && it.after != null }) "$author answered early" else "$author withdrew an early answer"
        event.table.any { it is GameTableChange.Placed } -> "$author moved a card"
        event.table.any { it is GameTableChange.Suggestion && it.after != null } -> "A result was suggested"
        event.table.any { it is GameTableChange.Suggestion } -> "The suggested result was answered"
        event.table.any { it is GameTableChange.ResultConfirmed } -> "The result was confirmed"
        else -> "$author acted"
    }

    /** Shown nowhere (the strip, the timeline, the event chip): HIS-086: a rewind's line start that older games stored as
     * an event of its own (an undo only pops events; the new line starts with the next event); §29.2: a pass that led to
     * no resolution, step or turn (passes are implied). */
    fun hidden(event: GameEvent): Boolean = (event.engine?.steps.orEmpty().none { it.input != null } &&
        event.table.isNotEmpty() && event.table.all { it is GameTableChange.LineMoved }) ||
        (pass(event) && passOutcome(event, null, "", emptyMap()) == null)

    /** Whether [event]'s acting input is a priority pass (§29.2: passes are implied). */
    fun pass(event: GameEvent): Boolean = event.engine?.steps?.firstOrNull { it.input != null }?.input is Input.PassPriority

    /**
     * §29.4: the fading top text of an undo: "Undid: cast Storm Spark" for the player who undid ([by] is [viewer]),
     * "Alice undid: cast Storm Spark" for the other player, naming [event], the first action taken back, as [viewer] saw it
     * on [state] (the board before the undo). An End turn's first pass reads "End turn"; with no event, just who undid.
     */
    fun undid(event: GameEvent?, state: GameState?, viewer: String, by: String, names: Map<String, String>): String {
        val subject = if (by == viewer) "You" else names[by] ?: "The other player"
        val head = if (by == viewer) "Undid" else "$subject undid"
        val e = event ?: return head
        val text = if (pass(e)) "End turn" else of(e, state, viewer, names)
        val action = when {
            text.startsWith("$subject: ") -> text.removePrefix("$subject: ")
            text.startsWith("$subject ") -> text.removePrefix("$subject ")
            text.startsWith("You") -> "you" + text.removePrefix("You")
            else -> text
        }
        return "$head: $action"
    }

    /** What a pass led to (the most telling of: a spell or ability resolved, a new turn, a new step). */
    private fun passOutcome(event: GameEvent, state: GameState?, viewer: String, names: Map<String, String>): String? {
        val changes = event.engine?.changes.orEmpty()
        // The spell or ability that resolved leaves the Stack last (CR 608.2n): what it countered left before it
        changes.lastOrNull { it is Change.StackItemRemoved }?.let { it as Change.StackItemRemoved }?.let { removed ->
            val item = removed.item
            // STK-046: an ability resolving reads as its source's ability ("Ember Herald's ability resolved")
            val ability = item.kind is StackItemKind.Activated || item.kind is StackItemKind.Triggered
            val shown = when (val id = item.id) {
                is ObjectId.Card -> name(id, state, viewer)
                else -> (item.snapshots.firstOrNull { it.id == item.source }?.characteristics?.name ?: name(item.source, state, viewer))
                    ?.let { if (ability) "$it's ability" else it }
            }
            return "${shown ?: if (ability) "an ability" else "a spell"} resolved"
        }
        val steps = changes.filterIsInstance<Change.Step>()
        val last = steps.lastOrNull()?.after ?: return null
        val first = steps.first().before
        if (last.number != first.number) return "Turn ${last.number} → ${if (last.active.value == viewer) "you" else names[last.active.value] ?: "the other player"}"
        return if (last.step != first.step) org.asyncttwiz.automatum.EngineProjection.position(last.step).label else null
    }

    /** The name of [id] as [viewer] may see it in [state] (the card's latest object), or null. */
    private fun name(id: ObjectId?, state: GameState?, viewer: String): String? {
        val card = (id as? ObjectId.Card)?.card ?: return null
        val s = state ?: return null
        val obj = s.objects[id] ?: s.objects.values.lastOrNull { it.card == card } ?: return null
        if (obj.faceDown || obj.id !in Engine.visible(s, PlayerId(viewer))) return null
        return obj.definition?.faces?.getOrNull(obj.face)?.name
    }

    /** HIS-070 on engine events: the event's icon. */
    fun icon(event: GameEvent): HistoryIcon {
        val input = event.engine?.steps?.firstOrNull { it.input != null }?.input
        return when (input) {
            null -> when {
                event.table.any { it is GameTableChange.AttackDraft || it is GameTableChange.BlockDraft } -> HistoryIcon.GPS_FIXED
                event.table.any { it is GameTableChange.EarlyAnswered } -> HistoryIcon.STICKY_NOTE_2
                event.table.any { it is GameTableChange.Placed } -> HistoryIcon.OPEN_WITH
                event.table.any { it is GameTableChange.Suggestion || it is GameTableChange.ResultConfirmed } -> HistoryIcon.EMOJI_EVENTS
                else -> HistoryIcon.SETTINGS_BACKUP_RESTORE
            }
            is Input.StartGame -> HistoryIcon.FLAG
            is Input.Cast -> HistoryIcon.LAYERS
            is Input.PlayLand -> HistoryIcon.SPACE_DASHBOARD
            is Input.Activate -> HistoryIcon.ADS_CLICK
            is Input.PassPriority -> {
                val steps = event.engine?.changes.orEmpty().filterIsInstance<Change.Step>()
                when {
                    event.engine?.changes.orEmpty().any { it is Change.StackItemRemoved } -> HistoryIcon.CHECK
                    steps.isNotEmpty() && steps.first().before.number != steps.last().after.number -> HistoryIcon.SKIP_NEXT
                    else -> HistoryIcon.ARROW_FORWARD
                }
            }
            is Input.DeclareAttackers, is Input.DeclareBlockers -> HistoryIcon.SWORDS
            is Input.SetupAnswer, is Input.Answer -> if ((input as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan") ||
                (input as? Input.Answer)?.answer == Answer.Option("Mulligan")) HistoryIcon.SHUFFLE else HistoryIcon.CHECK
            is Input.Concede -> HistoryIcon.OUTLINED_FLAG
            is Input.AgreedResult -> HistoryIcon.EMOJI_EVENTS
            else -> HistoryIcon.ADS_CLICK
        }
    }

    /** Whose colour an event shows in (HIS-005): an assumed input is the other player's (I-D56 §14); the server's own
     * events (the game's start) are the system's. */
    fun colourOf(event: GameEvent, players: List<String>): String? {
        val step = event.engine?.steps?.firstOrNull { it.input != null }
        if (step?.input is Input.StartGame) return null
        step?.assumedBy?.let { by -> return players.firstOrNull { it != by.value } }
        return event.author.takeIf { it in players }
    }
}

/**
 * The timeline of a game on engine state (HIS-001–HIS-005, HIS-012 on engine events): the main line the device holds,
 * up to the viewer's local head, and their own pending events after it; nothing past the local head (I-D56 §7). One event
 * per acting point (HIS-092); groups are turns (the faint line between them). Texts are worked out once per event, with
 * the state shown when they are first asked for.
 */
internal class EngineTimeline {
    private val texts = HashMap<String, String>()

    fun clear() = texts.clear()

    /** The events shown to the viewer, oldest first, with the turn each was made in. */
    fun shown(game: EngineGame): List<GameEvent> = game.shown?.line.orEmpty()

    fun events(game: EngineGame, names: Map<String, String>): List<HistoryTimelineEvent> {
        val published = game.shown ?: return emptyList()
        val shown = published.line
        val state = published.engine
        val players = state.players.map { it.id.value }
        var turn = 0
        return shown.mapNotNull { e ->
            e.engine?.changes?.lastOrNull { it is Change.Step }?.let { turn = (it as Change.Step).after.number }
            if (EngineEventText.hidden(e)) return@mapNotNull null
            val text = texts.getOrPut(e.id) { EngineEventText.of(e, state, game.me, names) }
            HistoryTimelineEvent(e.id, EngineEventText.icon(e), text, EngineEventText.colourOf(e, players), "turn:$turn")
        }
    }

    /** The text of [event] (cached). */
    fun text(game: EngineGame, event: GameEvent, names: Map<String, String>): String =
        texts.getOrPut(event.id) { EngineEventText.of(event, requireNotNull(game.shown).engine, game.me, names) }
}

/**
 * HIS-031 on engine state: the change flashes and delta tags between two boards shown one after the other by the events
 * that led from one to the other (live, playback, the player's own actions, take-backs): life, P/T, step and turn, face
 * and control; library shuffles for their animation. Never on a load (a jump).
 */
internal object EngineChanges {
    /** P/T of the battlefield's creatures by card id, as the table shows them: from [model]'s view (worked out once per
     * state, engine-responsiveness R2). */
    fun powerToughness(model: org.asyncttwiz.protocol.game.GameModel): Map<String, Pair<Int, Int>> {
        val state = model.engine
        val view = runCatching { model.view() }.getOrNull() ?: return emptyMap()
        return state.battlefield.mapNotNull { id ->
            val v = view[id] ?: return@mapNotNull null
            val p = v.power ?: return@mapNotNull null
            val t = v.toughness ?: return@mapNotNull null
            ((id as? ObjectId.Card)?.card?.value ?: return@mapNotNull null) to (p to t)
        }.toMap()
    }

    fun between(before: TableBoard, after: TableBoard, ptBefore: Map<String, Pair<Int, Int>>, ptAfter: Map<String, Pair<Int, Int>>,
        events: List<GameEvent>): List<TableChange> = buildList {
        for (p in after.board.players) {
            val old = before.board.players.firstOrNull { it.accountId == p.accountId }?.life?.toBigIntegerOrNull() ?: continue
            val new = p.life.toBigIntegerOrNull() ?: continue
            if (old != new) add(TableChange(TableChangeKey(p.accountId, TableChangeKind.LIFE), listOf(new - old)))
        }
        for ((card, pt) in ptAfter) {
            val old = ptBefore[card] ?: continue
            if (old != pt) add(TableChange(TableChangeKey(card, TableChangeKind.CORNER),
                listOf(BigInteger.valueOf((pt.first - old.first).toLong()), BigInteger.valueOf((pt.second - old.second).toLong()))))
        }
        val bt = before.board.turn; val at = after.board.turn
        if (bt.number != at.number || bt.phase != at.phase || bt.step != at.step) add(TableChange(TableChangeKey("turn", TableChangeKind.STEP)))
        val beforeCards = before.board.cards.associateBy { it.id }
        val battlefield = after.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.map { it.id }.toSet()
        for (card in after.board.cards) {
            val old = beforeCards[card.id] ?: continue
            if (card.containerId in battlefield && old.containerId in battlefield && card.containerId != old.containerId)
                add(TableChange(TableChangeKey(card.id, TableChangeKind.OWNER), owner = card.controllerOf(after)))
            if (old.containerId == card.containerId && (old.faceId != card.faceId || old.covered != card.covered))
                add(TableChange(TableChangeKey(card.id, TableChangeKind.FACE)))
        }
    }.distinctBy { it.key }

    /** The libraries the events shuffled (their containers), for the shuffle animation. */
    fun shuffled(play: TableBoard, events: List<GameEvent>): Set<String> {
        val players = events.flatMap { e -> e.engine?.changes.orEmpty().filterIsInstance<Change.Shuffle>().map { it.player.value } }.toSet()
        return play.board.containers.filter { it.zone == PlayZone.LIBRARY && !it.temp && it.accountId in players }.map { it.id }.toSet()
    }

    private fun org.asyncttwiz.protocol.PlayCard.controllerOf(play: TableBoard): String? =
        play.board.containers.firstOrNull { it.id == containerId }?.accountId
}
