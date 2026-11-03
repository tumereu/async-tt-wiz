package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I-D56 step B: the device core ([GameDevice]) through two devices and the in-memory server ([DeviceSim]): playback
 * stops by the private response mode, confirmations by the device's real answers, branches and the assumer's device
 * going back, the assume/wait logic (live, grace, Always assume resolves, blockers, keep), the information-rule stop,
 * undo free vs request and the freeze, table-only events at the head, results, the reproduction bundle.
 */
class GameDeviceTest {
    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob's deck of Islands and [spell], Alice's of Forests: Alice has nothing she could cast. */
    private fun spells(spell: String) = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { spell })))

    private fun DeviceSim.atMain(p: PlayerId) = device(p).model.engine.let {
        it.turn.active == p && it.turn.step == TurnStep.PrecombatMain && it.stack.isEmpty() && it.priority.holder == p
    }

    /** Bob starts and keeps; Alice is away, so his device assumes her keep and her passes up to his main phase. */
    private fun DeviceSim.bobToMain() {
        start(bob)
        assertIs<GameDevice.Acted.Sent>(device(bob).act(keep(bob)))
        settle()
        assertTrue(atMain(bob), "Bob is at his main phase: ${device(bob).model.engine.turn}")
        assertEquals(GameDevice.Status.YourMove, device(bob).status)
    }

    /** Bob plays an Island and casts [spell] (at Alice when it targets); his device passes for him after the cast. */
    private fun DeviceSim.bobCasts(spell: String, land: Boolean = true) {
        val bob = bob
        if (land) assertIs<GameDevice.Acted.Sent>(device(bob).act(Input.PlayLand(bob, hand(device(bob).model, bob, "Island"))))
        val cast = device(bob).act(Input.Cast(bob, hand(device(bob).model, bob, spell))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        }
        assertIs<GameDevice.Acted.Sent>(cast)
    }

    // I-D56 §3, §4, §7, §8: async: the starting player plays on an assumed keep; the other player's catch-up playback
    // stops at it whatever their mode, and their same answer confirms it; their Auto-skip confirms the passes it doesn't
    // stop at, and the indicator empties
    @Test
    fun anAsyncKeepIsAssumedAndConfirmedOnCatchUp() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val bob = sim.bob; val alice = sim.alice
        val assumed = sim.server.line().filter { it.assumed?.assumedBy == bob }
        assertTrue(assumed.first().assumed!!.input.let { it is Input.Answer || it is Input.SetupAnswer }, "the keep first")
        assertTrue(assumed.drop(1).all { it.assumed!!.input is Input.PassPriority }, "then Alice's passes in his turn")
        assertEquals(GameDevice.Indicator(assumed.size, sim.server.line().size - sim.server.line().indexOf(assumed.first()) - 1),
            sim.device(bob).indicator())
        // Alice comes back: the catch-up screen, then playback that stops at the assumed keep
        val device = sim.load(alice)
        assertEquals(GameDevice.Status.CatchUp, device.status)
        device.play()
        device.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(device.status)
        assertEquals(assumed.first().id, stop.event.id)
        assertNull(device.step(), "playback never shows past a decision stop")
        assertEquals(GameDevice.Acted.Confirmed, device.act(stop.assumed))
        // Her Auto-skip doesn't stop at Bob's upkeep passes she can't respond to: playback runs to the head
        device.playToStop()
        assertEquals(sim.server.head, device.session.localHead)
        sim.settle()
        assertEquals(1, sim.confirmations)
        assertEquals(sim.server.head, sim.server.heads[alice])
        assertNull(sim.device(bob).indicator())
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.TheirTurn), device.status)
        assertTrue(sim.leaks.isEmpty())
    }

    // I-D56 §7: playback stops follow the private response mode: Full control at every assumed pass
    @Test
    fun fullControlStopsAtEveryAssumedPass() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val device = sim.load(sim.alice)
        device.settings(PlaySettings(mode = ResponseMode.FullControl))
        device.play()
        device.playToStop()
        device.act((device.status as GameDevice.Status.Stopped).assumed)
        var stops = 0
        while (device.session.behind) {
            device.playToStop()
            if (!device.session.behind) break
            val stop = assertIs<GameDevice.Status.Stopped>(device.status, "Full control stops before each assumed pass")
            assertIs<Input.PassPriority>(stop.assumed)
            device.act(stop.assumed)
            stops++
        }
        assertTrue(stops >= 2, "upkeep and draw step: $stops")
        assertTrue(sim.take(sim.alice).any { it is org.asyncttwiz.protocol.GameAction.EngineSettings })
    }

    // I-D56 §8, §9: another answer at the stop branches with a new line; the assumer's device goes back to the branch
    // point with a note, and plays the opponent's event back
    @Test
    fun anotherAnswerBranchesAndTheAssumerGoesBack() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(device.status)
        val mulligan = Engine.options(device.model.engine, alice).first {
            (it as? Input.Answer)?.answer == Answer.Option("Mulligan") || (it as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan")
        }
        assertIs<GameDevice.Acted.Sent>(device.act(mulligan))
        assertTrue(!device.session.behind, "the discarded line is gone from her device")
        sim.flush(alice)
        assertEquals(1, sim.branches)
        assertEquals(1, sim.server.lines)
        sim.deliver(bob)
        val note = sim.device(bob).notes().filterIsInstance<GameDevice.Note.Branched>().single()
        assertEquals(alice, note.by)
        assertEquals(stop.event.id, note.discarded.id)
        assertTrue(note.takenBack.isNotEmpty())
        // Bob's table is back at the branch point; Alice's mulligan is buffered and played back
        assertEquals(stop.event.parentId, sim.device(bob).session.localHead)
        assertIs<GameDevice.Status.Playing>(sim.device(bob).status)
        sim.device(bob).playToStop()
        sim.settle()
        assertEquals(sim.server.head, sim.device(bob).session.head)
        assertTrue(sim.device(bob).model.sameAs(sim.server.stateAt(sim.device(bob).session.localHead)))
        assertTrue(sim.leaks.isEmpty())
    }

    // I-D56 §4: a pause of the live opponent is a wait with "Assume resolves"; once they are not live for 60 s the
    // device goes on as for async; "Always assume resolves" assumes at once
    @Test
    fun aLivePauseWaitsWithGraceAndAlwaysAssumeResolves() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        // Alice catches up and is live; Normal stops her at Bob's spell (Auto-skip would pass: she holds only Forests)
        val device = sim.load(alice)
        device.settings(PlaySettings(mode = ResponseMode.Normal))
        device.play()
        device.playToStop()
        device.act((device.status as GameDevice.Status.Stopped).assumed)
        device.playToStop()
        sim.settle()
        sim.device(bob).presence(live = true, now = sim.now)
        sim.bobCasts("Glance Ahead")
        sim.settle()
        val waiting = assertIs<GameDevice.Status.Waiting>(sim.device(bob).status)
        assertIs<GameDevice.Wait.Respond>(waiting.reason)
        assertEquals(GameDevice.Offer.AssumeResolves, waiting.offer)
        // She leaves: still a wait for 60 s, then assumed
        sim.now += 1_000
        sim.device(bob).presence(live = false, now = sim.now)
        sim.now += GameDevice.GRACE_MS - 1
        sim.device(bob).advance(sim.now)
        assertIs<GameDevice.Status.Waiting>(sim.device(bob).status)
        val before = sim.server.line().size
        sim.now += 1
        sim.settle()
        assertTrue(sim.server.line().size > before, "assumed after the grace")
        assertEquals(bob, sim.server.line()[before].assumed?.assumedBy)
        // With "Always assume resolves" a live pause is assumed at once
        val sim2 = spells("Glance Ahead")
        sim2.bobToMain()
        sim2.load(alice).let { d -> d.play(); d.playToStop(); d.act((d.status as GameDevice.Status.Stopped).assumed); d.playToStop() }
        sim2.settle()
        sim2.device(bob).presence(live = true, now = 0)
        sim2.device(bob).settings(PlaySettings(alwaysAssumeResolves = true))
        sim2.bobCasts("Glance Ahead")
        sim2.settle()
        assertTrue(sim2.server.line().any { it.assumed?.assumedBy == bob && sim2.server.stateAt(it.parentId).engine.stack.isNotEmpty() },
            "the pass at Alice's pause was assumed at once")
    }

    // I-D56 §5: an assumption whose outcome would show the assumer the other player's hand isn't sent: a wait with no
    // button; the other player's real pass goes on
    @Test
    fun theInformationRuleStopIsAWaitWithoutAButton() {
        val sim = spells("Cerebral Scan")
        sim.bobToMain()
        sim.bobCasts("Cerebral Scan")
        sim.settle()
        val bob = sim.bob; val alice = sim.alice
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.HiddenCards), sim.device(bob).status)
        assertEquals(GameDevice.Acted.Refused("noOffer"), sim.device(bob).assume())
        // Alice catches up and passes for real: the spell resolves, Bob looks at her hand as the rules allow
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        device.act((device.status as GameDevice.Status.Stopped).assumed)
        device.playToStop()
        assertEquals(GameDevice.Status.YourMove, device.status)
        assertIs<GameDevice.Acted.Sent>(device.act(Input.PassPriority(alice)))
        sim.settle()
        assertTrue(sim.device(bob).model.engine.stack.isEmpty())
        assertTrue(sim.leaks.isEmpty())
    }

    // I-D56 §10: undo is free while nothing new was shown; otherwise a request: the game is frozen until it is answered,
    // the other player sees the board only up to their local head; accepting rewinds both devices
    @Test
    fun undoIsFreeOrARequestThatFreezesTheGame() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        val beforeLand = sim.server.head
        bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")))
        sim.settle()
        assertEquals(GameDevice.Undo.Free(beforeLand), bobs.undo())
        sim.settle()
        assertEquals(1, sim.rewinds)
        assertTrue(bobs.model.engine.player(bob).hand.any { bobs.model.engine.obj(it)?.definition?.faces?.first()?.name == "Island" })
        // Casting Glance Ahead looks at and draws Bob's own cards: undoing it needs Alice's approval
        bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")))
        sim.settle()
        val beforeCast = sim.server.head
        sim.bobCasts("Glance Ahead", land = false)
        sim.settle()
        while (bobs.status == GameDevice.Status.YourMove && bobs.model.engine.pending != null) {
            val d = bobs.model.engine.pending!!.decision
            bobs.act(Engine.options(bobs.model.engine, bob).filterIsInstance<Input.Answer>().first { it.decision == d.id })
            sim.settle()
        }
        val undo = bobs.undo(beforeCast)
        val request = assertIs<GameDevice.Undo.Request>(undo)
        bobs.requestUndo(request.target, "misclick")
        sim.settle()
        // Frozen for both: no inputs, no automatic steps; Alice's preview is her local head's board (she hasn't seen it)
        assertEquals(GameDevice.Acted.Refused(GameDevice.REWIND_REQUESTED), bobs.act(Input.PassPriority(bob)))
        val alices = sim.load(alice)
        val preview = assertNotNull(alices.requestPreview())
        assertTrue(preview.unseen)
        assertEquals("misclick", preview.message)
        alices.answerRequest(accept = true)
        sim.flush(alice)
        sim.settle()
        assertEquals(2, sim.rewinds)
        assertNull(bobs.request)
        assertEquals(sim.server.head, bobs.session.head)
        assertTrue(bobs.model.sameAs(sim.server.stateAt(bobs.session.localHead)))
    }

    /** [p] plays back to the head, confirming each assumption their playback stops at. */
    private fun DeviceSim.catchUp(p: PlayerId) {
        val d = device(p)
        d.play()
        while (d.session.behind) {
            d.playToStop()
            (d.status as? GameDevice.Status.Stopped)?.let { d.act(it.assumed) }
        }
        settle()
    }

    // I-D56 §6: a table-only event made while behind goes at the head and doesn't move the author's local head; staging
    // and early answers behind the head stay on the device; one refused as stale is made again at the new head
    @Test
    fun tableOnlyEventsGoAtTheHead() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")))
        sim.settle()
        val island = bobs.model.engine.battlefield.first { bobs.model.engine.obj(it)?.controller == bob }
        val alices = sim.load(alice)
        val aliceHead = sim.server.heads[alice]
        assertTrue(alices.session.behind)
        assertEquals(GameDevice.Acted.Refused("behind"),
            alices.table(listOf(TableChange.EarlyAnswered("x", null, EarlyAnswer(alice, "x", Answer.YesNo(true))))))
        assertEquals(GameDevice.Acted.Kept, alices.table(listOf(TableChange.BlockDraft(null, emptyList()))))
        val placed = sim.server.state().table.placements[island]
        val sent = assertIs<GameDevice.Acted.Sent>(alices.table(listOf(TableChange.Placed(island, placed, Placement(7, 1)))))
        assertEquals(sim.server.head, sent.event.parentId)
        sim.flush(alice)
        assertEquals(sent.event.id, sim.server.head)
        assertEquals(aliceHead, sim.server.heads[alice], "it confirms nothing: her local head stays")
        assertEquals(Placement(7, 1), sim.server.state().table.placements[island])
        // Both at the head: two table changes cross on the wire; Bob's is taken first, Alice's is made again on it
        sim.catchUp(alice)
        assertTrue(!alices.session.behind && !bobs.session.behind)
        val now = sim.server.state().table.placements[island]
        bobs.table(listOf(TableChange.Placed(island, now, Placement(5, 0))))
        alices.table(listOf(TableChange.Placed(island, now, Placement(2, 2))))
        sim.flush(bob); sim.flush(alice)
        sim.settle()
        assertEquals(Placement(2, 2), sim.server.state().table.placements[island])
        assertTrue(alices.model.sameAs(sim.server.state()))
    }

    /** Forests and Elvish Seers (1/1) for both, at 1 life: Alice's turn-3 attack is lethal unless Bob blocks. */
    private fun seers() = DeviceSim(EngineGames(5, List(40) { if (it % 2 == 0) "Forest" else "Elvish Seer" }.let { it to it }, life = 1))

    /** Both play a Forest and an Elvish Seer in their first turns; in Alice's third, with Bob away (nothing delivered to
     * him), she attacks with her Seer: her Seer. */
    private fun DeviceSim.seersToCombat(): ObjectId {
        val alice = alice; val bob = bob
        start(alice, ResponseMode.Normal)
        for (p in listOf(alice, bob)) { device(p).act(keep(p)); settle() }
        for (p in listOf(alice, bob)) {
            catchUp(p)
            assertTrue(atMain(p), "${p.value} at their main phase: ${device(p).status}")
            val d = device(p)
            assertIs<GameDevice.Acted.Sent>(d.act(Input.PlayLand(p, hand(d.model, p, "Forest"))))
            assertIs<GameDevice.Acted.Sent>(d.act(Input.Cast(p, hand(d.model, p, "Elvish Seer")), follow = { m ->
                m.engine.pending?.decision?.takeIf { it.player == p && it !is Decision.Random }?.let { open ->
                    Engine.options(m.engine, p).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
            }))
            settle()
            d.endTurn(now)
            settle()
        }
        catchUp(alice)
        assertTrue(atMain(alice))
        val m = device(alice).model.engine
        val seer = m.battlefield.first { m.obj(it)?.controller == alice && m.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }
        device(alice).act(Input.PassPriority(alice))
        settle(only = listOf(alice))
        assertEquals(GameDevice.Status.YourMove, device(alice).status)
        device(alice).act(Input.DeclareAttackers(alice, listOf(Attack(seer, TargetRef.Player(bob)))))
        settle(only = listOf(alice))
        return seer
    }

    /** Alice passes at her own pause in the blockers step (Normal stops there): Bob's pass is assumed, damage is dealt. */
    private fun DeviceSim.passToDamage() {
        while (device(alice).status == GameDevice.Status.YourMove && device(alice).model.engine.priority.holder == alice) {
            device(alice).act(Input.PassPriority(alice))
            settle(only = listOf(alice))
        }
    }

    /** [d] plays back to the assumed blocks, confirming the assumptions it stops at before them. */
    private fun toAssumedBlocks(d: GameDevice): GameDevice.Status.Stopped {
        d.play()
        while (true) {
            d.playToStop()
            val stop = assertIs<GameDevice.Status.Stopped>(d.status, "playback stops before the assumed blocks")
            if (stop.assumed is Input.DeclareBlockers) return stop
            d.act(stop.assumed)
        }
    }

    // I-D56 §3, §4, §11, §12: opponent async: declaring attackers opens the Assume blockers screen with "No blocks";
    // OK assumes it; the lethal damage's result waits for Bob to catch up (OK greyed); his playback always stops at
    // assumed blocks, and the same blocks confirm; OK once both local heads reached the result
    @Test
    fun assumedBlockersAndAResultOnATentativeLine() {
        val sim = seers()
        sim.seersToCombat()
        val alice = sim.alice; val bob = sim.bob
        val waiting = assertIs<GameDevice.Status.Waiting>(sim.device(alice).status)
        assertEquals(GameDevice.Wait.Blockers, waiting.reason)
        assertEquals(GameDevice.Offer.AssumeBlockers, waiting.offer)
        assertTrue(waiting.blockersScreen, "async: the screen opens with No blocks")
        assertIs<GameDevice.Acted.Sent>(sim.device(alice).assume())
        sim.settle(only = listOf(alice))
        sim.passToDamage()
        val over = assertIs<GameDevice.Status.Over>(sim.device(alice).status)
        assertEquals(GameResult.Won(alice), over.result)
        assertTrue(!over.confirmable && !over.final, "You won · waiting for Bob to catch up")
        assertEquals(GameDevice.Acted.Refused("waitForCatchUp"), sim.device(alice).confirmResult())
        // Bob comes back: playback stops at the assumed blocks whatever his mode (Auto-skip here)
        val bobs = sim.load(bob)
        bobs.settings(PlaySettings(mode = ResponseMode.AutoSkip))
        val stop = toAssumedBlocks(bobs)
        assertEquals(Input.DeclareBlockers(bob, emptyList()), stop.assumed)
        assertEquals(GameDevice.Acted.Confirmed, bobs.act(stop.assumed))
        bobs.playToStop()
        sim.settle()
        assertEquals(GameDevice.Status.Over(GameResult.Won(alice), confirmable = true, final = false), sim.device(alice).status)
        assertIs<GameDevice.Acted.Sent>(sim.device(alice).confirmResult())
        sim.settle()
        assertEquals(true, (bobs.status as GameDevice.Status.Over).final)
        assertTrue(sim.leaks.isEmpty())
    }

    // I-D56 §8, §12: Bob blocks instead of the assumed "No blocks": a branch; Alice's result is taken back with a note
    @Test
    fun differentBlocksBranchAndTakeTheResultBack() {
        val sim = seers()
        val aliceSeer = sim.seersToCombat()
        val alice = sim.alice; val bob = sim.bob
        sim.device(alice).assume()
        sim.settle(only = listOf(alice))
        sim.passToDamage()
        assertIs<GameDevice.Status.Over>(sim.device(alice).status)
        val bobs = sim.load(bob)
        toAssumedBlocks(bobs)
        val bobSeer = bobs.model.engine.battlefield.first { bobs.model.engine.obj(it)?.controller == bob &&
            bobs.model.engine.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.DeclareBlockers(bob, listOf(Block(bobSeer, aliceSeer)))))
        sim.settle()
        assertEquals(1, sim.branches)
        val note = sim.device(alice).notes().filterIsInstance<GameDevice.Note.Branched>().single()
        assertEquals(Input.DeclareBlockers(bob, emptyList()), note.discarded.assumed?.input)
        assertNull(Engine.result(sim.device(alice).model.engine))
        assertNull(Engine.result(sim.server.state().engine))
        assertTrue(sim.leaks.isEmpty())
    }

    // I-D43: the reproduction bundle replays every event of the device's line exactly
    @Test
    fun theReproductionBundleReplays() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        sim.bobCasts("Glance Ahead")
        sim.settle()
        val bundle = sim.device(sim.bob).repro(sim.server.history.codec)
        val decoded = GameRepro.decode(bundle.encode())
        assertEquals(bundle, decoded)
        assertEquals(emptyList(), decoded.check())
        assertTrue(decoded.log.any { it.startsWith("out Submit") })
    }
}

private fun DeviceSim.take(p: PlayerId) = device(p).take(now)
