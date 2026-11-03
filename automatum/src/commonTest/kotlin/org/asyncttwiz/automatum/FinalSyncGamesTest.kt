package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameplayHistory
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Final sync review: long seeded games of two players on one shared history tree (the server's), with Undo and Redo,
 * branches from earlier acting points, and the stored history replayed. Invariants after every step: the rebuilt engine
 * state equals the engine's own ([TreeGame.send]); Undo of the latest batch gives back its board, Redo the board after it;
 * a branch from an acting point starts from that point's engine state;
 * the history replays to the server's head; the incremental engine log ([EngineLog.Chain]) is the whole one; and the
 * engine can always take the board over. Invented cards only.
 */
class FinalSyncGamesTest {
    init { CombatCards.ensured }

    private val aliceDeck = List(7) { "Mountain" } + List(4) { "Swamp" } + List(3) { "Plains" } + listOf("Storm Spark",
        "Cinder Bolt", "Curse Word", "Festering Gremlin", "Tinkered Mite", "Tinkered Mite", "Viper Adept", "Grim Prowler",
        "Bloodbond Acolyte", "Hill Ogre", "Twinblade Duelist", "Dawnlance Squire", "Gloomblossom Carrier", "Hill Ogre")
    private val bobDeck = List(8) { "Island" } + List(6) { "Forest" } + listOf("Canopy Visionary", "Glance Ahead",
        "Gearwing Glider", "Gearwing Glider", "Ironhide Brute", "Stout Bear", "Stout Bear", "Haven Warden",
        "Sunning Mudlizard", "Storm Spark", "Tinkered Mite", "Mull It Over", "Stout Bear")

    private class Batch(val actor: String, val head: String, val before: BoardState, val after: BoardState)

    private class Counts(var calls: Int = 0, var undos: Int = 0, var redos: Int = 0,
        var branches: Int = 0, var known5: Int = 0, var refused: MutableMap<String, Int> = linkedMapOf(), var turn: Int = 0)

    private val problems = mutableListOf<String>()

    private fun play(seed: Long, steps: Int): Counts {
        val random = Random(seed)
        val table = AtwTable("final-sync-games-$seed")
        if (seed % 2 == 0L) table.fullControl += table.players
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
        val game = TreeGame(table.state(), table.definitions, seed, "games-$seed")
        val counts = Counts()
        val batches = mutableListOf<Batch>()
        // The board before the batch that opened the open flow (I-D8)
        var flowStart: BoardState? = null
        val ops = mutableListOf<String>()
        val chain = EngineLog.Chain(table.definitions)
        fun problem(text: String) { problems += "seed $seed step ${counts.calls}: $text" }
        fun refused(what: String, reason: String?) { counts.refused.merge("$what:$reason", 1, Int::plus) }
        fun players() = table.players.random(random)

        for (i in 0 until steps) {
            val play = game.state
            val s = try { game.engine() } catch (e: Exception) { problem("the engine can't take the board: $e"); return counts }
            if (Engine.result(s) != null) break
            counts.turn = s.turn.number
            val flow = Automatum.flowOpen(play.board)
            val r = random.nextInt(100)
            val last = batches.lastOrNull()?.takeIf { it.head == game.head && it.actor.isNotEmpty() }
            when {
                // Undo of the latest batch by its sender; then maybe Redo
                r < 8 && last != null -> {
                    val undone = game.control(last.actor, GameAction.Undo)
                    if (undone.tree == null) { refused("undo", undone.reason); continue }
                    counts.undos++; ops += "undo ${TurnPosition.of(game.state.board.turn)}"
                    if (game.state.board != last.before) problem("Undo didn't give back the board before the batch:\n" + boardDiff(last.before, game.state.board))
                    batches.removeAt(batches.lastIndex)
                    if (!Automatum.flowOpen(game.state.board)) flowStart = null
                    if (random.nextBoolean()) {
                        val redone = game.control(last.actor, GameAction.Redo)
                        // #4 (fixed, its own test): a redo right after its undo gives back exactly the batch
                        if (redone.tree == null) { refused("redo", redone.reason); problem("Redo right after Undo refused: ${redone.reason}"); continue }
                        counts.redos++; ops += "redo"
                        val differs = boardDiff(last.after, game.state.board, 40)
                        if (differs.isNotEmpty()) problem("Redo didn't give back the board after the batch:\n$differs")
                        batches += Batch(last.actor, game.head, last.before, game.state.board)
                    }
                    continue
                }
                // Undo by the other player of their own earlier event (selective, SYS-004): when allowed, the engine still
                // takes the board
                r < 10 -> {
                    val other = table.players.first { it != batches.lastOrNull()?.actor }
                    val undone = game.control(other, GameAction.Undo)
                    if (undone.tree == null) { refused("other-undo", undone.reason); continue }
                    counts.undos++; ops += "other undo"
                    batches.removeAll { b -> b.head !in game.tree.nodes }
                    flowStart = null
                    try { game.engine() } catch (e: Exception) { problem("after the other player's undo the engine can't take the board: $e") }
                    // An undone branch start leaves the live position at a branch point, whose next event reseeds the libraries
                    // (HIS-042; #5, fixed): the harness checks plans against the server's board exactly, so a status starts the
                    // branch first
                    if (game.tree.nodes.values.any { it.parentId == game.head }) {
                        counts.known5++
                        game.manual(other, PlayAction.SetStatus("Again ${counts.known5}"))
                    }
                    continue
                }
                // A branch from an earlier acting point (a batch's end, as Hold at a pause makes it, I-D41), by a shown-only event
                r < 28 && !flow && batches.size > 2 -> {
                    val point = batches.dropLast(1).takeLast(6).random(random)
                    if (point.head !in game.tree.nodes || Automatum.flowOpen(game.tree.stateAt(point.head)!!.board)) continue
                    val at = game.engine(point.head)
                    val branched = game.manual(players(), PlayAction.SetStatus("Branch ${counts.branches}"), from = point.head)
                    if (branched.tree == null) { refused("branch", branched.reason); continue }
                    counts.branches++; ops += "branch"
                    // A branch reseeds the libraries' unknown order (LibraryKnowledge.reseeds): only their order may differ
                    fun sorted(g: arcanum.engine.state.GameState) = g.also { it.players.replaceAll { p -> p.copy(library = p.library.sortedBy(Any::toString)) } }
                    val real = StateCompare.exact(sorted(at), sorted(game.engine()))
                    if (real.isNotEmpty()) problem("a branch's engine state isn't its point's: ${real.first().take(300)}")
                    batches += Batch("", game.head, point.after, game.state.board)
                    continue
                }
            }
            // An ordinary acting-point step
            val before = game.state.board
            var actor = ""
            try {
                step(s, play.board, random, { game.call() }) { who, body ->
                    actor = who
                    val call = game.call().also(body)
                    val sent = game.send(call, who)
                    if (call.changes.isNotEmpty() && sent.tree == null) problem("the server refused a batch planned on its own head: ${sent.reason} " +
                        "(${TurnPosition.of(game.state.board.turn)}, recent: ${ops.takeLast(4)})")
                }
            } catch (e: AssertionError) {
                problem("${e.message?.take(400)}"); return counts
            } catch (e: Exception) {
                problem("step failed: $e (recent: ${ops.takeLast(8)}, board combat ${game.state.board.combat})"); return counts
            }
            counts.calls++; ops += "call by ${actor.take(4)}"
            if (game.state.board !== before && game.state.board != before) {
                if (!Automatum.flowOpen(before) && Automatum.flowOpen(game.state.board)) flowStart = before
                if (!Automatum.flowOpen(game.state.board)) flowStart = null
                batches += Batch(actor, game.head, before, game.state.board)
            }
            // N3: the incremental log along the tree (the app's) is the log read from every tag, after undos and branches too
            if (counts.calls % 5 == 0) {
                val play = game.state
                val turn = play.board.turn.number.toIntOrNull()
                val whole = EngineLog.recent(EngineLog.from(game.tags(), play, table.definitions), turn)
                val incremental = chain.log(game.head, play, turn) { id ->
                    game.tree.nodes[id]?.let { node -> game.tree.materializationId(id)?.let { EngineLog.Event(it, node.parentId, node.tag) } }
                }
                if (incremental != whole) problem("the incremental engine log differs: ${incremental.size} vs ${whole.size} entries")
            }
            // The stored history replays to the server's head (server and clients agree; test-only replay from the start)
            if (counts.calls % 40 == 0) {
                val chain = generateSequence(game.tree.nodes[game.head]) { game.tree.nodes[it.parentId] }.toList().asReversed()
                var replayed = game.tree.initial
                for (m in chain) replayed = GameplayHistory.replay(replayed, m).state ?: run { problem("history doesn't replay at ${m.id}"); return counts }
                if (replayed.board != game.state.board) problem("the replayed history differs from the head")
            }
        }
        return counts
    }

    private fun step(s: arcanum.engine.state.GameState, board: BoardState, random: Random, call: () -> EngineCall,
        run: (String, (EngineCall) -> Unit) -> Unit) {
        val decision = s.pending?.decision
        when {
            decision is Decision.Divide -> run(decision.player.value) { CombatFlow.divide(it) }
            // A cast or ability sent while its own choices are open: the suggested mana (as the cast panel pays)
            decision is Decision.ManaPayment ->
                run(decision.player.value) { StepFlow.answer(it, arcanum.engine.decision.Answer.ManaPayment(decision.suggestion)) }
            decision != null -> {
                val answer = Engine.options(s, decision.player).filterIsInstance<Input.Answer>().randomOrNull(random)
                    ?: fail("no answer listed for $decision")
                run(decision.player.value) { StepFlow.answer(it, answer.answer) }
            }
            // The holder as every engine call settles it (HostPriority: "Clear Automatum data" leaves nobody holding it)
            (HostPriority.wanted(s, HostPriority.kept(board))?.holder ?: s.priority.holder) == null -> {
                val c = call()
                val choices = CombatFlow.choices(c)
                val player = choices.declaring ?: fail("nobody holds priority and nothing is awaited in ${s.turn}")
                if (choices.attackers) {
                    val chosen = choices.attacks.entries.filter { random.nextBoolean() }.map { (card, lanes) -> LaneAttack(card, lanes.random(random)) }
                    run(player) { CombatFlow.declareAttackers(it, if (CombatFlow.attackRefusal(c, chosen) == null) chosen else choices.requiredAttacks) }
                } else {
                    val chosen = choices.blocks.entries.filter { random.nextBoolean() }.map { (card, attackers) -> LaneBlock(card, attackers.random(random)) }
                    run(player) { CombatFlow.declareBlockers(it, if (CombatFlow.blockRefusal(c, chosen) == null) chosen else choices.requiredBlocks) }
                }
            }
            else -> {
                val holder = HostPriority.wanted(s, HostPriority.kept(board))?.holder ?: s.priority.holder!!
                val doing = Engine.options(s, holder).filter { it is Input.Cast || it is Input.PlayLand }
                if (doing.isNotEmpty() && random.nextInt(3) > 0) run(holder.value) { c -> HostPriority.settle(c)
                    if (c.perform(doing.random(random)) == null) StepFlow.automatic(c) }
                else run(holder.value) { StepFlow.pass(it, holder.value, if (random.nextInt(5) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass) }
            }
        }
    }

    @Test fun twoPlayersSwitchingUndoingRevertingAndBranching() {
        for (seed in listOf(61L, 62L, 63L, 64L, 65L, 66L)) {
            val counts = play(seed, steps = 220)
            println("finalreview-sync games seed $seed: ${counts.calls} calls, turn ${counts.turn}, undos ${counts.undos}, redos ${counts.redos}, " +
                "branches ${counts.branches}, branch points ${counts.known5}, refused ${counts.refused}")
        }
        problems.forEach { println("finalreview-sync PROBLEM $it") }
        assertTrue(problems.isEmpty(), "${problems.size} problems:\n" + problems.take(6).joinToString("\n"))
    }
}
