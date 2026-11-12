package arcanum.engine.turn

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.EventPattern
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Trigger
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.LoopStop
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Loops: the safety cap of one `perform` call, and loops of mandatory actions a host reports (CR 104.4b, 732.4). */
class LoopTest {
    /**
     * "Whenever this creature becomes the target of a spell or ability, tap another target creature." (written by hand:
     * the parser doesn't read "becomes the target" outside ward yet). Two of them target each other with their triggers
     * without end, all in one call: triggers go on the Stack without anyone getting priority.
     */
    private fun mirror(name: String): String {
        val base = Cards.definition(Cards.invent("Mirrorglass Base", "{U}", "Creature — Faerie",
            "Whenever this creature attacks, tap another target creature.", "1/1"))
        val face = base.faces.single()
        val attacks = face.abilities.single() as Ability.Triggered
        val targeted = attacks.copy(trigger = Trigger.On(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)))
        return Cards.define(name, CardDefinition(base.layout, listOf(face.copy(name = name, abilities = listOf(targeted)))))
    }
    private val sprite = mirror("Mirrorglass Sprite")
    private val twin = mirror("Mirrorglass Twin")
    private val glance = Cards.invent("Prying Glance", "{U}", "Instant", "Tap target creature.")

    /** Enters and is sacrificed, dies and returns: a loop of mandatory triggers through priority, each lap the same. */
    private val revenant = Cards.invent("Restless Revenant", "{1}{B}", "Creature — Spirit",
        "When this creature enters, sacrifice a creature.\nWhen this creature dies, return it to the battlefield under its owner's control.",
        "1/1")

    private fun Scenario.waitingOnLoop(): Pair<Decision.ChooseOption, LoopStop> {
        val manual = decision<Decision.ChooseOption>()
        assertEquals(listOf(LoopStop.CONTINUE, LoopStop.DRAW), manual.options)
        val continuation = assertIs<Continuation.Loop>(state.pending!!.continuation)
        return manual to continuation.stop
    }

    // CR 603.3, 704.3: triggered abilities without end before anyone gets priority; the call stops at its cap with the
    // loop choice instead of hanging
    @Test
    fun endlessTriggersInOneCallStopAtTheCap() = scenario {
        alice.battlefield(sprite, twin, "Island")
        alice.hand(glance)
        alice.casts(glance, target = sprite)
        val (manual, stop) = waitingOnLoop()
        assertEquals(LoopStop.Cap, stop)
        assertTrue(manual.text!!.startsWith("Possible loop"), manual.text)
        assertEquals(alice.id, manual.player, "the caster, who would get priority next (CR 117.3c)")
        val stacked = state.stack.size
        assertTrue(stacked > Loops.MAX_ROUNDS / 2, "the triggers went on the Stack round after round: $stacked")
        // Continue: the game goes on in a new call, up to the cap again
        answer(Answer.Option(LoopStop.CONTINUE))
        assertEquals(LoopStop.Cap, waitingOnLoop().second)
        assertTrue(state.stack.size > stacked)
    }

    // CR 104.4b: at the cap the players may end the game as a draw instead
    @Test
    fun aDrawAtTheCapEndsTheGame() = scenario {
        alice.battlefield(sprite, twin, "Island")
        alice.hand(glance)
        alice.casts(glance, target = sprite)
        assertEquals(LoopStop.Cap, waitingOnLoop().second)
        answer(Answer.Option(LoopStop.DRAW))
        assertEquals(GameResult.Draw, Engine.result(state))
    }

    /** The host's automatic passes (I-D3) with loop detection: while a player can only pass, it passes for them; meeting a
     * state again with only those passes since, it reports the loop. Returns how many passes it made. */
    private fun Scenario.hostPasses(limit: Int = 50): Int {
        val seen = mutableSetOf<LoopKey>()
        var passes = 0
        while (pending == null && Engine.result(state) == null && passes < limit) {
            val holder = state.priority.holder ?: break
            if (!Host.shouldPassFor(state, holder)) break
            if (state.stack.isNotEmpty() && !seen.add(Host.loopKey(state))) {
                perform(Input.MandatoryLoop)
                break
            }
            perform(Input.PassPriority(holder))
            passes++
        }
        return passes
    }

    // CR 104.4b, 732.4: a loop of mandatory actions (the Revenant enters, is sacrificed, dies and returns, each lap the
    // same state with new objects) is a draw
    @Test
    fun aMandatoryLoopIsADraw() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revenant)
        alice.casts(revenant)
        val passes = hostPasses()
        // Spell, enters, dies (the permanent cast first, CR 400.7d), then a whole lap: enters, dies, enters again
        assertTrue(passes in 6..12, "found within two laps: $passes passes")
        val (manual, stop) = waitingOnLoop()
        assertIs<LoopStop.Mandatory>(stop)
        assertTrue("draw" in manual.text!!, manual.text)
        assertNull(Engine.result(state), "not over before it is answered")
        answer(Answer.Option(LoopStop.DRAW))
        assertEquals(GameResult.Draw, Engine.result(state))
        assertTrue(state.log.any { (it.change as? Change.Happened)?.event == GameEvent.MandatoryLoop })
        assertTrue(Engine.options(state, alice.id).isEmpty())
    }

    // CR 104.4b: the loop reported, the players choose to continue: the game goes on where it was
    @Test
    fun aReportedLoopContinuedGoesOn() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revenant)
        alice.casts(revenant)
        hostPasses()
        assertIs<LoopStop.Mandatory>(waitingOnLoop().second)
        val holder = state.priority.holder
        answer(Answer.Option(LoopStop.CONTINUE))
        assertNull(Engine.result(state))
        assertNull(pending)
        assertEquals(holder, state.priority.holder)
    }

    // CR 104.4b: the key leaves out ids and timestamps, not what the game is
    @Test
    fun theKeyIgnoresIdsButNotTheGame() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(revenant)
        alice.casts(revenant)
        allPass()
        val cast = Host.loopKey(state)
        allPass()
        allPass()
        // The Revenant returned: its enters trigger is on the Stack again
        val entered = Host.loopKey(state)
        assertNotEquals(cast, entered, "the first time it was a permanent that had been cast (CR 400.7d)")
        assertEquals(entered, Host.loopKey(state.deepCopy()))
        allPass()
        assertNotEquals(entered, Host.loopKey(state), "it died: another position of the lap")
        allPass()
        assertEquals(entered, Host.loopKey(state), "it returned as a new object: the same position")
        val poorer = state.deepCopy().also { s -> s.players[0] = s.players[0].copy(life = 19) }
        assertNotEquals(entered, Host.loopKey(poorer), "a life total is part of the game")
    }
}
