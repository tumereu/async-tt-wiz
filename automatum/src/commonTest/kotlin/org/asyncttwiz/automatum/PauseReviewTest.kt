package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.automatum.PausePoints.At
import org.asyncttwiz.automatum.PausePoints.Point
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Adversarial review of the pause points (integration spec I-D41, "pausereview"): where the host passes and where the
 * board says a player has a stop or a pause. Ignored tests are findings (~/projects/atw-cache/arcanum-pause-review.md).
 */
class PauseReviewTest {
    init { CombatCards.ensured }

    private val table = AtwTable("pause-review")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private fun at(position: TurnPosition, number: Int, player: String = alice) {
        table.turn = TurnState(player, "$number", position.phase, position.step)
        table.priority = MtgPriority(player)
    }
    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)

    // pausereview #8 (needs owner decision; CR 117.3a, 513.1): Bob holds Storm Spark and an untapped Mountain. Alice
    // passes in her second main phase: without Full control Bob gets no pause in her end step (nor in her upkeep, draw or
    // beginning of combat): Automatum passes for him up to his own main phase, so "at the end of your turn" instants and
    // beginning-of-combat taps need Full control, which then pauses everywhere. Proposed: the other player's end step is
    // a pause for a player who could act (as the opponent's attack is).
    @Ignore // pausereview #8: needs owner decision: the opponent's end step is no pause without Full control
    @Test fun theOpponentsEndStepIsAPause() {
        at(TurnPosition.POSTCOMBAT_MAIN, 3)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.END_STEP, position(rt), "the turn is at ${rt.state.board.turn}")
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
    }

    // SYS-067, pausereview #9 (fixed with final cast review #3) (CR 117.3a/117.3d, 605.3a; TBL-115 "a mana ability ... its player keeping priority"): Bob has Full
    // control and a pause in Alice's upkeep (empty Stack, Alice passed). He taps his Mountain for mana first (a mana
    // ability keeps priority) and then wants to pass (or cast). The mana ability made the passes in a row 0, and the host's
    // priority fix-up ([HostPriority], an empty Stack with no passes gives priority to the active player) hands priority
    // back to Alice on the next call: the board ([PausePoints.at], the pause bar) says Bob holds it, every engine call
    // says Alice does, so Bob's Pass acts for Alice and his cast is refused.
    @Test fun aManaAbilityAtAnEmptyStackPauseKeepsPriority() {
        at(TurnPosition.UPKEEP, 3)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.fullControl += bob
        val rt = RoundTrip(table.state(), table.definitions)
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.UPKEEP, position(rt))
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
        val mana = rt.options(bob).filterIsInstance<Input.Activate>().first()
        rt.perform(mana, bob)
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board), "Bob still holds priority on the board")
        assertEquals(bob, rt.call().also { HostPriority.settle(it) }.working.priority.holder?.value,
            "the host keeps Bob's priority after his mana ability")
    }

    private val aliceDeck =List(7) { "Mountain" } + List(4) { "Swamp" } + List(3) { "Plains" } + listOf("Storm Spark",
        "Cinder Bolt", "Storm Spark", "Festering Gremlin", "Tinkered Mite", "Tinkered Mite", "Viper Adept", "Grim Prowler",
        "Bloodbond Acolyte", "Hill Ogre", "Twinblade Duelist", "Dawnlance Squire", "Storm Spark", "Hill Ogre")
    private val bobDeck = List(8) { "Island" } + List(6) { "Forest" } + List(2) { "Mountain" } + listOf("Storm Spark",
        "Glance Ahead", "Gearwing Glider", "Gearwing Glider", "Ironhide Brute", "Stout Bear", "Stout Bear", "Haven Warden",
        "Sunning Mudlizard", "Storm Spark", "Tinkered Mite", "Mull It Over", "Stout Bear")

    /**
     * pausereview: seeded random games through the I3 API with each player's Full control and "Skip when I have no
     * choice" switched at random between calls. After every call the board's pause point ([PausePoints.at], what the app
     * reads) is the host's ([PausePoints.of] on the engine state), and a player the host left holding priority has a stop
     * or a pause (no point where neither device would show or pass anything).
     */
    @Test fun theBoardAndTheHostAgreeOnPausePoints() {
        val dead = mutableListOf<String>()
        for (seed in listOf(41L, 42L)) dead += play(seed, calls = 150)
        println("SIM pause points: ${dead.size} points with priority but neither stop nor pause: ${dead.take(8)}; " +
            "${settled.size} boards whose holder the host changes (#9): ${settled.take(4)}")
        assertTrue(dead.isEmpty(), "points where the host left priority with neither a stop nor a pause: ${dead.take(5)}")
    }
    private val settled = mutableListOf<String>()

    private fun play(seed: Long, calls: Int): List<String> {
        val random = Random(seed)
        val table = AtwTable("pause-review-$seed")
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
        val rt = RoundTrip(table.state(), table.definitions, seed)
        val dead = mutableListOf<String>()
        var done = 0
        while (done < calls) {
            // Settings change between calls, as players flip them
            if (random.nextInt(6) == 0) {
                val who = table.players.random(random)
                val fc = random.nextBoolean(); val skip = random.nextInt(4) != 0
                rt.state = rt.state.copy(board = rt.state.board.copy(players = rt.state.board.players.map {
                    if (it.accountId == who) it.copy(fullControl = fc, skipWhenNoChoice = skip) else it }))
            }
            val s = rt.engine()
            if (Engine.result(s) != null) break
            val decision = s.pending?.decision
            when {
                decision is Decision.Divide -> rt.run(decision.player.value) { CombatFlow.divide(it) }
                decision != null -> {
                    val answer = Engine.options(s, decision.player).filterIsInstance<Input.Answer>().randomOrNull(random)
                        ?: fail("seed $seed: no answer listed for $decision")
                    rt.run(decision.player.value) { StepFlow.answer(it, answer.answer) }
                }
                s.priority.holder == null -> {
                    val call = rt.call()
                    val choices = CombatFlow.choices(call)
                    val player = choices.declaring ?: fail("seed $seed: nobody holds priority and nothing is awaited in ${s.turn}")
                    if (choices.attackers) {
                        val chosen = choices.attacks.entries.filter { random.nextBoolean() }.map { (card, lanes) -> LaneAttack(card, lanes.random(random)) }
                        rt.run(player) { CombatFlow.declareAttackers(it, if (CombatFlow.attackRefusal(call, chosen) == null) chosen else choices.requiredAttacks) }
                    } else {
                        val chosen = choices.blocks.entries.filter { random.nextBoolean() }.map { (card, attackers) -> LaneBlock(card, attackers.random(random)) }
                        rt.run(player) { CombatFlow.declareBlockers(it, if (CombatFlow.blockRefusal(call, chosen) == null) chosen else choices.requiredBlocks) }
                    }
                }
                else -> {
                    // The holder as every engine call sees it (#9: the host's fix-up may differ from the board)
                    val holder = HostPriority.wanted(s, HostPriority.kept(rt.state.board))?.holder ?: s.priority.holder!!
                    val doing = Engine.options(s, holder).filter { it is Input.Cast || it is Input.PlayLand || it is Input.Activate }
                    if (doing.isNotEmpty() && random.nextInt(3) > 0) {
                        // As the app sends a cast or ability: then the host's passes ([CastFlow.sent], I-D41)
                        rt.run(holder.value) { call -> HostPriority.settle(call)
                            if (call.perform(doing.random(random)) == null) StepFlow.automatic(call) }
                    } else rt.run(holder.value) {
                        StepFlow.pass(it, holder.value, if (random.nextInt(4) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass)
                    }
                }
            }
            done++
            val after = rt.engine()
            val holder = after.priority.holder
            if (after.pending != null || holder == null || Engine.result(after) != null) continue
            HostPriority.wanted(after, HostPriority.kept(rt.state.board))?.takeIf { it.holder != holder }?.let {
                settled += "seed $seed call $done ${TurnPosition.of(rt.state.board.turn)}: board ${rt.state.board.priority}, host ${it.holder}"
                continue
            }
            val fc = rt.state.board.players.single { it.accountId == holder.value }.fullControl
            val host = PausePoints.of(after, holder, fc)?.let { At(holder.value, it) }
            val board = PausePoints.at(rt.state.board)
            assertEquals(host, board, "seed $seed call $done: the board's point is the host's (${rt.state.board.turn})")
            if (board == null) dead += "seed $seed call $done ${TurnPosition.of(rt.state.board.turn)} holder " +
                "${if (holder.value == table.alice) "alice" else "bob"} active ${if (after.turn.active.value == table.alice) "alice" else "bob"} " +
                "stack ${after.stack.size} forced ${Engine.shouldPassFor(after, holder)}"
        }
        return dead
    }
}
