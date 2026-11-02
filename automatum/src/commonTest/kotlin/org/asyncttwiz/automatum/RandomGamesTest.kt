package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Seeded random games over `Engine.options` driven through ATW (the I0 acceptance check, integration spec §3): every
 * input is one engine call whose changes become one ATW batch, checked by [RoundTrip] after every call. With full
 * information, and on players' own boards (each player acts in their own view, D60; a shared flow goes on in the view it
 * was made in, and a step only the other player may see is handed to them, D31).
 */
class RandomGamesTest {
    private val aliceDeck = List(8) { "Mountain" } + List(4) { "Swamp" } + listOf("Storm Spark", "Storm Spark", "Cinder Bolt",
        "Curse Word", "Festering Gremlin", "Festering Gremlin", "Tinkered Mite", "Tinkered Mite", "Fiendish Mentor",
        "Garnet Bauble", "Grindstone Rasp", "Crypt Flitter", "Magma Needle")
    private val bobDeck = List(8) { "Island" } + List(4) { "Forest" } + listOf("Canopy Visionary", "Canopy Visionary",
        "Glance Ahead", "Glance Ahead", "Thought Siphon", "Rescind", "Gearwing Glider", "Gearwing Glider", "Mull It Over",
        "Chromed Locket", "Tinkered Mite", "Haven Warden", "Muse Awhile")

    private class Game(val rt: RoundTrip, val handOvers: Int, val inputs: Int, val turns: Int, val stop: String)

    private fun play(seed: Long, views: Boolean, inputs: Int = 160): Game {
        val random = Random(seed)
        val table = AtwTable("game-$seed")
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
        val rt = RoundTrip(table.state(), table.definitions, seed)
        val players = listOf(table.alice, table.bob)
        var done = 0
        var handOvers = 0
        var stop = "inputs"
        while (done < inputs) {
            val full = rt.engine()
            if (Engine.result(full) != null) { stop = "result ${Engine.result(full)}"; break }
            val decision = full.pending?.decision
            // The view a call is made in: the open flow's, else the acting player's own
            val flowView = if (views) EngineState.flowViewer(rt.state.board) else null
            if (decision is Decision.OtherView) {
                // D31: handed to its player, who decides in their own view; the call goes on in the flow's view
                val call = EngineCall(rt.state, rt.definitions, flowView, EngineLog.from(rt.tags), rt::seed)
                val filled = call.otherView(decision)
                val asked = Engine.otherViewDecision(call.working, filled) ?: run { stop = "other view without a decision"; null } ?: break
                val answer = simpleAnswer(asked) ?: run { stop = "no simple answer to $asked"; null } ?: break
                call.perform(Input.Answer(decision.id, Answer.OtherView(filled, listOf(answer))))
                rt.apply(call, decision.player.value)
                handOvers++
            } else if (decision != null) {
                // Answers, or cancelling a cast or activation that can't go on (D58)
                val options = Engine.options(rt.engine(flowView), decision.player).ifEmpty {
                    players.flatMap { p -> Engine.options(rt.engine(flowView), PlayerId(p)).filterIsInstance<Input.Cancel>() }
                }
                if (options.isEmpty()) { stop = "nothing listed for $decision"; break }
                val input = options.random(random)
                rt.perform(input, (input as? Input.Cancel)?.player?.value ?: decision.player.value, flowView)
            } else {
                val listed = players.flatMap { p -> Engine.options(rt.engine(if (views) p else null), PlayerId(p)).map { p to it } }
                if (listed.isEmpty()) { stop = "no options"; break }
                val doing = listed.filterNot { (_, i) -> i is Input.PassPriority || (i is Input.DeclareAttackers && i.attacks.isEmpty()) ||
                    (i is Input.DeclareBlockers && i.blocks.isEmpty()) }
                val (player, input) = if (doing.isNotEmpty() && random.nextInt(3) > 0) doing.random(random) else listed.random(random)
                rt.perform(input, player, if (views) player else null)
            }
            done++
        }
        return Game(rt, handOvers, done, rt.engine().turn.number, stop)
    }

    /** An answer a player would give in their own view to a step handed to them (D31): the first legal one. */
    private fun simpleAnswer(d: Decision): Answer? = when (d) {
        is Decision.ChooseObjects -> Answer.Objects(d.options.take(d.min))
        is Decision.May -> Answer.YesNo(false)
        is Decision.Targets -> Answer.Targets(d.options.take(d.min))
        is Decision.OrderTriggers -> Answer.Order(d.triggers.map { it.index })
        is Decision.ChooseOption -> d.options.firstOrNull()?.let { Answer.Option(it) }
        is Decision.ChooseNumber -> Answer.Number(d.min)
        is Decision.Manual -> Answer.Done
        else -> null
    }

    @Test fun fullInformationGames() {
        for (seed in listOf(11L, 12L)) {
            val game = play(seed, views = false)
            assertTrue(game.inputs > 50 || game.stop.startsWith("result"), "seed $seed: ${game.inputs} inputs, stopped: ${game.stop}")
            println("seed $seed full information: ${game.inputs} inputs, turn ${game.turns}, largest batch ${game.rt.largestBatch} actions, largest tag ${game.rt.largestTag} bytes, ${game.stop}")
        }
    }

    @Test fun gamesOnPlayersOwnBoards() {
        for (seed in listOf(21L, 22L)) {
            val game = play(seed, views = true)
            assertTrue(game.inputs > 50 || game.stop.startsWith("result"), "seed $seed: ${game.inputs} inputs, stopped: ${game.stop}")
            println("seed $seed views: ${game.inputs} inputs, turn ${game.turns}, hand-overs ${game.handOvers}, largest batch ${game.rt.largestBatch} actions, largest tag ${game.rt.largestTag} bytes, ${game.stop}")
        }
    }
}
