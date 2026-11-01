package arcanum.engine.setup

import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.action.Act
import arcanum.engine.action.Actions
import arcanum.engine.action.Asker
import arcanum.engine.action.NeedDecision
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.query.Evaluator
import arcanum.engine.state.CardId
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TurnState
import arcanum.engine.turn.Steps
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One card of a deck: the host's own card id and the card's definition, null for a card the caller can't see (D60). */
@Serializable
data class DeckCard(val card: CardId, val definition: CardDefinition?)

/**
 * A player at the table, in seat order (clockwise, CR 103.1): their [deck], which becomes their library (CR 103.3), and
 * their [sideboard], the cards they own outside the game, a companion among them (CR 100.4, 103.2a, 400.11).
 */
@Serializable
data class Seat(val player: PlayerId, val deck: List<DeckCard>, val sideboard: List<DeckCard> = emptyList())

/**
 * How a game starts ([Input.StartGame], CR 103).
 *
 * - [startingPlayer]: given by the host (a variant or the players agreed): nobody chooses.
 * - [chooser]: who chooses the starting player, the loser of the previous game of a match (CR 103.1). Null: a random
 *   result the host answers (D25) picks the chooser, the "mutually agreeable method" of a match's first game.
 * - [handSize]: the starting hand size (CR 103.5).
 * - [freeFirstMulligan]: the first mulligan doesn't count (CR 103.5c); null: only in a multiplayer game.
 */
@Serializable
data class StartOptions(
    val startingPlayer: PlayerId? = null,
    val chooser: PlayerId? = null,
    val handSize: Int = 7,
    val freeFirstMulligan: Boolean? = null,
)

/** Where starting the game is ([Continuation.Setup]): each stage is done at once or waits for a decision. */
@Serializable
sealed interface SetupStage {
    /** CR 103.1: who takes the first turn. */
    @Serializable @SerialName("starting-player") data object StartingPlayer : SetupStage

    /** CR 103.2b: the next player in turn order not [asked] yet may reveal a companion. */
    @Serializable @SerialName("companion") data class RevealCompanion(val asked: List<PlayerId> = emptyList()) : SetupStage

    /** CR 103.3: every deck is shuffled and becomes a library. */
    @Serializable @SerialName("shuffle") data object Shuffle : SetupStage

    /** CR 103.5: every player draws their starting hand. */
    @Serializable @SerialName("draw") data object Draw : SetupStage

    /** CR 103.5: the next player in turn order who hasn't kept declares keep or mulligan. */
    @Serializable @SerialName("declare") data class Declare(val mulligans: Mulligans) : SetupStage

    /** CR 103.5: everyone who declared a mulligan takes it at the same time: hands shuffled back, new hands drawn. */
    @Serializable @SerialName("mulligan") data class Mulligan(val mulligans: Mulligans) : SetupStage

    /** CR 103.5: then each of them, in turn order, puts cards on the bottom; [done] already did. */
    @Serializable @SerialName("bottom") data class Bottom(val mulligans: Mulligans, val done: List<PlayerId> = emptyList()) : SetupStage

    /** CR 103.8: the starting player's first turn begins. */
    @Serializable @SerialName("first-turn") data object FirstTurn : SetupStage
}

/** The mulligan process so far (CR 103.5): mulligans [taken] by each player, who [kept], and who declared a mulligan in
 * this round ([taking]). */
@Serializable
data class Mulligans(
    val taken: Map<PlayerId, Int> = emptyMap(),
    val kept: List<PlayerId> = emptyList(),
    val taking: List<PlayerId> = emptyList(),
)

/** The game has begun: the first turn started (CR 103.8). Before that, [Input.StartGame] starts it. */
val GameState.started: Boolean get() = turn.number > 0

/**
 * Starting a game (CR 103): [newGame] builds the state before the game; [Input.StartGame] then runs CR 103.1–103.8 as
 * stages ([SetupStage]), each committed as it is done (D58), a stage waiting for a decision redone from its start with
 * the answers so far (D29):
 *
 * 1. The starting player (CR 103.1): given, or chosen by the [StartOptions.chooser], or by a player a die roll the host
 *    answers picks (D25). Turn order is seat order from the starting player (CR 103.1, 101.4).
 * 2. Companions (CR 103.2b, 702.139a): in turn order, each player with a companion outside the game whose condition
 *    their starting deck meets may reveal one; the engine checks conditions it can read (`each … card in your starting
 *    deck`, unknown deck cards filled in by the host, D60) and offers the rest on trust. Sideboards are set aside from
 *    the start (CR 103.2a).
 * 3. Libraries are shuffled by the host (CR 103.3, D25); each player draws their starting hand (CR 103.5).
 * 4. Mulligans (CR 103.5): the starting player, then each other player in turn order, declares keep or mulligan; then
 *    everyone who chose a mulligan takes it at once: hand shuffled back, a new hand drawn, then as many cards as
 *    mulligans taken (the first one free in multiplayer, CR 103.5c) put on the bottom of the library, in the order chosen
 *    (the last chosen lowest). This repeats until all have kept; a player whose hand is down to zero cards keeps.
 * 5. Opening-hand actions (CR 103.6, "you may begin the game with it on the battlefield") would come here, starting
 *    player first; no card has one yet. Actions "any time you could mulligan" (CR 103.5b) are not offered either.
 * 6. The first turn begins (CR 103.8): the starting player skips its draw when [GameState.skipFirstDraw] (CR 103.8a).
 */
internal object Setup {
    const val KEEP = "Keep"
    const val MULLIGAN = "Mulligan"

    fun newGame(seats: List<Seat>, startingLife: Int, skipFirstDraw: Boolean): GameState {
        require(seats.isNotEmpty()) { "A game needs players" }
        require(seats.map { it.player }.distinct().size == seats.size) { "Every player sits once" }
        val cards = seats.flatMap { it.deck + it.sideboard }.map { it.card }
        require(cards.distinct().size == cards.size) { "Every card has its own id" }
        var stamp = 0L
        val objects = LinkedHashMap<ObjectId, GameObject>()
        fun place(owner: PlayerId, deck: List<DeckCard>, zone: Zone): List<ObjectId> = deck.map { c ->
            val id = ObjectId.Card(c.card, 0)
            objects[id] = GameObject(id = id, owner = owner, zone = zone, definition = c.definition, timestamp = ++stamp)
            id
        }
        val players = seats.map { seat ->
            PlayerState(
                id = seat.player, life = startingLife, startingLife = startingLife,
                library = place(seat.player, seat.deck, Zone.Library),
                outside = place(seat.player, seat.sideboard, Zone.Outside),
            )
        }
        return GameState(
            players = players.toMutableList(),
            objects = objects,
            turn = TurnState(0, seats.first().player, TurnStep.Untap),
            priority = PriorityState(null),
            timestampCounter = stamp,
            skipFirstDraw = skipFirstDraw,
        )
    }

    fun start(recorder: Recorder, input: Input.StartGame): Progress {
        val s = recorder.state
        require(!s.started) { "The game has started already" }
        val o = input.options
        val ids = s.players.map { it.id }
        require(o.startingPlayer == null || o.startingPlayer in ids) { "${o.startingPlayer} isn't in the game" }
        require(o.chooser == null || o.chooser in ids) { "${o.chooser} isn't in the game" }
        require(o.handSize >= 0) { "A starting hand size can't be negative" }
        return run(recorder, SetupStage.StartingPlayer, o, emptyList())
    }

    fun resume(recorder: Recorder, continuation: Continuation.Setup, answer: Answer): Progress =
        run(recorder, continuation.stage, continuation.options, continuation.answers + answer)

    /** Runs the stages from [from] until one waits for a decision or the first turn has begun. */
    fun run(recorder: Recorder, from: SetupStage, options: StartOptions, given: List<Answer>): Progress {
        var stage: SetupStage? = from
        var answers = given
        while (stage != null) {
            val mark = recorder.changes.size
            val act = Act(recorder, Asker(answers, "setup:${key(stage)}@${recorder.state.log.size}"))
            val next = try {
                step(act, stage, options)
            } catch (n: NeedDecision) {
                recorder.rollback(mark)
                return Progress.Waiting(Pending(n.decision, Continuation.Setup(stage, options, answers)))
            }
            stage = next
            answers = emptyList()
        }
        // CR 103.8: the untap step of the first turn has nothing to do; the game goes on to its upkeep
        return Steps.endStep(recorder)
    }

    private fun key(stage: SetupStage): String = when (stage) {
        SetupStage.StartingPlayer -> "starting-player"
        is SetupStage.RevealCompanion -> "companion:${stage.asked.size}"
        SetupStage.Shuffle -> "shuffle"
        SetupStage.Draw -> "draw"
        is SetupStage.Declare -> stage.mulligans.let { "declare:${it.taken.values.sum()}:${it.kept.size}:${it.taking.size}" }
        is SetupStage.Mulligan -> "mulligan:${stage.mulligans.taken.values.sum()}"
        is SetupStage.Bottom -> "bottom:${stage.mulligans.taken.values.sum()}:${stage.done.size}"
        SetupStage.FirstTurn -> "first-turn"
    }

    /** Does [stage] and returns the next one; null once the first turn has begun. */
    private fun step(act: Act, stage: SetupStage, options: StartOptions): SetupStage? = when (stage) {
        SetupStage.StartingPlayer -> startingPlayer(act, options)
        is SetupStage.RevealCompanion -> companion(act, stage.asked)
        SetupStage.Shuffle -> {
            order(act.state).forEach { Actions.shuffle(act, it) }
            SetupStage.Draw
        }
        SetupStage.Draw -> {
            order(act.state).forEach { Actions.draw(act, null, it, options.handSize) }
            SetupStage.Declare(Mulligans())
        }
        is SetupStage.Declare -> declare(act, stage.mulligans, options)
        is SetupStage.Mulligan -> mulligan(act, stage.mulligans, options)
        is SetupStage.Bottom -> bottom(act, stage, options)
        SetupStage.FirstTurn -> {
            // CR 103.8, 800.4k: the starting player (or, if they left, the next player) takes the first turn
            val s = act.state
            val first = order(s).first()
            act.record(Changes.step(s, TurnState(1, first, TurnStep.Untap, turnStarted = mapOf(first to 1)),
                listOf(Meaning.TurnBegan, Meaning.StepBegan)))
            null
        }
    }

    /** Players still in the game in turn order, from the starting player (CR 103.1). */
    private fun order(s: GameState): List<PlayerId> = s.playersFrom(s.turn.active)

    // ---- CR 103.1

    private fun startingPlayer(act: Act, options: StartOptions): SetupStage {
        val s = act.state
        val seats = s.players.filterNot { it.lost }.map { it.id }
        var chooser: PlayerId? = null
        val starter = options.startingPlayer ?: run {
            val given = options.chooser?.let { c -> if (s.player(c).lost) arcanum.engine.turn.Priority.nextPlayer(s, c) else c }
            // CR 800.4h: a chooser who left is replaced by the next player in turn order
            val by = given ?: seats[
                (act.random(seats.first(), null, RandomKind.Die(seats.size, 1)) as Answer.DieRolls).results.single() - 1]
            chooser = by
            act.choosePlayer(by, null, seats) ?: by
        }
        act.record(Change.Happened(GameEvent.StartingPlayer(starter, chooser)))
        if (s.turn.active != starter) act.record(Changes.step(s, s.turn.copy(active = starter)))
        return SetupStage.RevealCompanion()
    }

    // ---- CR 103.2b, 702.139a

    private fun companion(act: Act, asked: List<PlayerId>): SetupStage {
        val player = order(act.state).firstOrNull { it !in asked } ?: return SetupStage.Shuffle
        val candidates = companions(act, player)
        val chosen = act.chooseObjects(player, null, candidates, 0, 1, "Reveal a companion (CR 103.2b)")
        chosen.singleOrNull()?.let { id ->
            act.record(Change.Memory(id, Evaluator.REVEALED_COMPANION, null, NamedValue.Number(1)))
            act.record(Change.Happened(GameEvent.Revealed(player, listOf(id))))
        }
        return SetupStage.RevealCompanion(asked + player)
    }

    /** The cards [player] owns outside the game with a companion ability whose condition their starting deck meets
     * (CR 702.139a, b); a condition the engine can't read is left to the player. */
    private fun companions(act: Act, player: PlayerId): List<ObjectId> {
        val outside = act.state.player(player).outside.filter { companionAbility(act.state.obj(it)) != null }
        if (outside.isEmpty()) return emptyList()
        if (outside.any { companionAbility(act.state.obj(it))?.eachCard != null }) {
            act.fill(player, null, act.state.player(player).library, "Check companion conditions against the starting deck (CR 702.139a)")
        }
        val snap = Snapshot(act.state)
        val deck = act.state.player(player).library
        return outside.filter { id ->
            val filter = companionAbility(act.state.obj(id))?.eachCard ?: return@filter true
            val e = snap.evaluator(player, self = id)
            deck.all { e.matches(filter, it) }
        }
    }

    private fun companionAbility(o: GameObject?): Keyword.CompanionAbility? =
        o?.definition?.faces?.flatMap { it.abilities }?.filterIsInstance<Keyword.CompanionAbility>()?.firstOrNull()

    // ---- CR 103.5

    /** Mulligans that count towards cards put on the bottom and the limit (CR 103.5c: the first is free in multiplayer). */
    private fun counted(taken: Int, free: Boolean): Int = if (free) (taken - 1).coerceAtLeast(0) else taken

    private fun freeFirst(s: GameState, options: StartOptions): Boolean = options.freeFirstMulligan ?: (s.players.size > 2)

    private fun declare(act: Act, m: Mulligans, options: StartOptions): SetupStage {
        val s = act.state
        val player = order(s).firstOrNull { it !in m.kept && it !in m.taking }
            // CR 103.6: opening-hand actions would be taken here, once nobody takes a mulligan any more
            ?: return if (order(s).none { it in m.taking }) SetupStage.FirstTurn else SetupStage.Mulligan(m)
        // A hand already down to zero cards can't be mulliganed again (CR 103.5)
        val canMulligan = counted(m.taken[player] ?: 0, freeFirst(s, options)) < options.handSize
        val choice = if (canMulligan) act.chooseOption(player, null, listOf(KEEP, MULLIGAN)) else KEEP
        require(choice == KEEP || choice == MULLIGAN) { "Answer $KEEP or $MULLIGAN" }
        if (choice == MULLIGAN) return SetupStage.Declare(m.copy(taking = m.taking + player))
        act.record(Change.Happened(GameEvent.Kept(player, s.player(player).hand.size)))
        return SetupStage.Declare(m.copy(kept = m.kept + player))
    }

    private fun mulligan(act: Act, m: Mulligans, options: StartOptions): SetupStage {
        val s = act.state
        val players = order(s).filter { it in m.taking }
        val taken = m.taken + players.associateWith { (m.taken[it] ?: 0) + 1 }
        // All at the same time (CR 103.5): hands shuffled back and new hands drawn, committed before anyone chooses
        players.forEach { p ->
            act.record(Change.Happened(GameEvent.Mulliganed(p, taken.getValue(p))))
            val hand = s.player(p).hand
            if (hand.isNotEmpty()) act.record(Changes.move(s, hand, Zone.Library, meanings = listOf(Meaning.PutIntoLibrary)))
            Actions.shuffle(act, p)
            Actions.draw(act, null, p, options.handSize)
        }
        return SetupStage.Bottom(m.copy(taken = taken))
    }

    private fun bottom(act: Act, stage: SetupStage.Bottom, options: StartOptions): SetupStage {
        val s = act.state
        val m = stage.mulligans
        val p = order(s).firstOrNull { it in m.taking && it !in stage.done }
            ?: return SetupStage.Declare(m.copy(taking = emptyList()))
        val hand = s.player(p).hand
        val n = counted(m.taken[p] ?: 0, freeFirst(s, options)).coerceAtMost(hand.size)
        val chosen = act.chooseObjects(p, null, hand, n, n, "Put $n ${if (n == 1) "card" else "cards"} on the bottom of your library (CR 103.5)")
        if (chosen.isNotEmpty()) {
            act.record(Changes.move(s, chosen, Zone.Library, Changes.Position.Bottom, meanings = listOf(Meaning.PutIntoLibrary)))
        }
        return stage.copy(done = stage.done + p)
    }
}
