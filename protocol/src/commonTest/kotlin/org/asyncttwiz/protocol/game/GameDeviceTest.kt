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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    /** Bob starts and keeps; Alice keeps and is away, so his device assumes her passes up to his main phase ([alwaysAssume]:
     * "Always assume resolves" for both). */
    private fun DeviceSim.bobToMain(alwaysAssume: Boolean = true) {
        start(bob, alwaysAssume = alwaysAssume)
        keepWhileAway(bob)
        assertTrue(atMain(bob), "Bob is at his main phase: ${device(bob).model.engine.turn}")
        assertEquals(GameDevice.Status.YourMove, device(bob).status)
    }

    /** Bob plays an Island and casts [spell] (at Alice when it targets); his device passes for him after the cast. */
    private fun DeviceSim.bobCasts(spell: String, land: Boolean = true, landName: String = "Island") {
        val bob = bob
        if (land) assertIs<GameDevice.Acted.Sent>(device(bob).act(Input.PlayLand(bob, hand(device(bob).model, bob, landName))))
        val cast = device(bob).act(Input.Cast(bob, hand(device(bob).model, bob, spell))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().let { answers ->
                if (d is Decision.Targets) answers.first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(alice)) } else answers.first()
            }
        }
        assertIs<GameDevice.Acted.Sent>(cast)
    }

    // I-D56 §3, §4, §7, HIS-096, TRN-027: async: the starting player plays on the other player's own keep, assuming only
    // their passes (an opening keep is never assumed); the other player's catch-up starts paused past the opening, and
    // their Auto-skip, which doesn't stop at passes they couldn't respond to, confirms them; the indicator empties
    @Test
    fun anAwayPlayersPassesAreAssumedAndConfirmedOnCatchUp() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val bob = sim.bob; val alice = sim.alice
        val assumed = sim.server.line().filter { it.assumed?.assumedBy == bob }
        assertTrue(assumed.isNotEmpty())
        assertTrue(assumed.all { it.assumed!!.input is Input.PassPriority }, "only Alice's passes in his turn")
        assertTrue(sim.server.line().any { e -> e.author == alice.value && e.assumed == null }, "Alice's own keep")
        assertEquals(GameDevice.Indicator(assumed.size, sim.server.line().size - sim.server.line().indexOf(assumed.first()) - 1),
            sim.device(bob).indicator())
        val device = sim.load(alice)
        assertEquals(GameDevice.Status.CatchUp, device.status, "past the opening: paused until Play")
        device.play()
        device.playToStop()
        assertEquals(sim.server.head, device.session.localHead)
        sim.settle()
        assertEquals(sim.server.head, sim.server.heads[alice])
        assertNull(sim.device(bob).indicator())
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.TheirTurn), device.status)
        assertTrue(sim.leaks.isEmpty())
    }

    /** What [device] reports now, past the throttle, each answered [answer] (null: not answered). */
    private fun reports(sim: DeviceSim, device: GameDevice, answer: String? = null): List<String> {
        sim.now += 3_000
        return device.take(sim.now).filterIsInstance<org.asyncttwiz.protocol.GameAction.EngineSeen>().onEach {
            if (answer != null) device.refused(it, answer)
        }.map { it.eventId }
    }

    // HIS-032, SYS-014 (e2e MainPhaseUndoTest, 2026-11-08): the server's word that the player's seat is on their own event still
    // pending, the shown head behind it (the revisions confirming it are late): the server can only refuse a report of the
    // shown head, so none goes out. It went out at every take before, without end
    @Test
    fun noReportOfAShownHeadBehindTheOwnPendingEventTheServerSaysTheSeatIsOn() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        sim.settle()
        val me = sim.bob
        val bob = sim.device(me)
        assertIs<GameDevice.Acted.Sent>(bob.act(Input.PlayLand(me, hand(bob.model, me, "Island"))))
        assertIs<GameDevice.Acted.Sent>(bob.act(Input.PassPriority(me)))
        bob.take(sim.now)
        val first = bob.session.pending.first()
        val last = bob.session.pending.last()
        assertTrue(first.id != last.id)
        val others = bob.localHeads.mapKeys { it.key.value }
        bob.revision(listOf(first), org.asyncttwiz.protocol.EngineUpdate(localHeads = others + (me.value to first.id)))
        bob.revision(emptyList(), org.asyncttwiz.protocol.EngineUpdate(localHeads = others + (me.value to last.id)))
        assertEquals(first.id, bob.session.localHead)
        repeat(3) { assertEquals(emptyList(), reports(sim, bob, "stale"), "take ${it + 1}") }
    }

    // HIS-032: a report refused with the server's head somewhere this device doesn't know goes out once: no revision since, so
    // nothing has changed for the server's answer, whatever it was
    @Test
    fun aRefusedReportIsNotRepeatedWithoutARevisionSince() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.load(sim.alice)
        alice.play()
        alice.playToStop()
        alice.revision(emptyList(), org.asyncttwiz.protocol.EngineUpdate(localHeads = alice.localHeads.mapKeys { it.key.value } +
            (sim.alice.value to "not-on-this-line")))
        val local = alice.session.localHead
        assertEquals(listOf(local), reports(sim, alice, "stale"))
        repeat(3) { assertEquals(emptyList(), reports(sim, alice, "stale"), "take ${it + 1}") }
    }

    // HIS-032: a report refused while its event hadn't reached the server's line goes again once a revision says the seat is
    // still before it (the other player's view of what this player has seen would stay behind), once per such revision, and
    // not after the server took it
    @Test
    fun aRefusedReportGoesAgainOnceARevisionSaysTheSeatIsStillBeforeIt() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val alice = sim.load(sim.alice)
        val before = alice.localHeads.mapKeys { it.key.value }
        alice.play()
        alice.playToStop()
        val local = alice.session.localHead
        assertTrue(local != before[sim.alice.value], "she was shown more than the server has of her")
        assertEquals(listOf(local), reports(sim, alice, "stale"))
        assertEquals(emptyList(), reports(sim, alice, "stale"), "nothing new from the server yet")
        alice.revision(emptyList(), org.asyncttwiz.protocol.EngineUpdate(localHeads = before))
        assertEquals(listOf(local), reports(sim, alice, "stale"), "the seat still before it: again")
        assertEquals(emptyList(), reports(sim, alice), "again only on the next revision")
        alice.revision(emptyList(), org.asyncttwiz.protocol.EngineUpdate(localHeads = before))
        assertEquals(listOf(local), reports(sim, alice))
        alice.revision(emptyList(), org.asyncttwiz.protocol.EngineUpdate(localHeads = before + (sim.alice.value to local)))
        assertEquals(emptyList(), reports(sim, alice), "the server took it")
    }

    // TRN-027, HIS-096 (owner, 2026-11-05): the starting player's device never assumes the other player's opening keep,
    // with "Always assume resolves" on or off: it waits for it with no Assume button, and turn 1 begins on their own keep
    @Test
    fun theOpeningKeepIsNeverAssumed() {
        for (always in listOf(false, true)) {
            val sim = spells("Storm Spark")
            val alice = sim.alice; val bob = sim.bob
            sim.start(bob, alwaysAssume = always)
            assertIs<GameDevice.Acted.Sent>(sim.device(bob).act(sim.keep(bob)))
            sim.settle(only = listOf(bob))
            sim.now += 600_000
            sim.settle(only = listOf(bob))
            assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.Keep), sim.device(bob).status, "always assume: $always")
            assertEquals(GameDevice.Acted.Refused("noOffer"), sim.device(bob).assume())
            assertEquals(0, sim.server.state().engine.turn.number)
            assertTrue(sim.server.line().none { it.assumed != null })
            sim.deliver(alice)
            sim.device(alice).playToStop()
            assertIs<GameDevice.Acted.Sent>(sim.device(alice).act(sim.keep(alice)))
            sim.settle()
            assertTrue(sim.atMain(bob), "turn 1 on her own keep")
        }
    }

    // HIS-095, STK-036 (owner, 2026-11-06): playback stops by the player's current response mode; set to Control
    // (from the quick actions, live or in playback), which stops it at every assumed pass; the value is kept on the server for the game
    @Test
    fun fullControlStopsAtEveryAssumedPass() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val device = sim.load(sim.alice)
        assertEquals(ResponseMode.AutoSkip, device.settings.mode)
        device.settings(device.settings.copy(mode = ResponseMode.FullControl))
        device.play()
        device.playToStop()
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

    // HIS-095 (I-D56 §27): pausing playback stops it at the next point where Full control would stop, even where the
    // player's own mode (Auto-skip) wouldn't; their answer there (playing on) ends the pause and playback goes on by
    // their mode
    @Test
    fun pausingStopsAtTheNextFullControlPoint() {
        val sim = spells("Storm Spark")
        sim.bobToMain()
        val device = sim.load(sim.alice)
        device.play()
        assertEquals(ResponseMode.AutoSkip, device.settings.mode)
        device.pausing = true
        while (device.step() != null) Unit
        val stop = assertIs<GameDevice.Status.Stopped>(device.status, "paused at an assumed pass Auto-skip goes past")
        assertTrue(stop.paused)
        assertIs<Input.PassPriority>(stop.assumed)
        assertEquals(GameDevice.Acted.Confirmed, device.act(stop.assumed))
        assertTrue(!device.pausing, "playing on ends the pause")
        // Her Auto-skip goes on to the head without stopping
        while (device.step() != null) Unit
        assertTrue(!device.session.behind)
    }

    // I-D56 §8, §9: another answer at the stop branches with a new line; the assumer's device goes back to the branch
    // point with a note, and plays the opponent's event back
    @Test
    fun anotherAnswerBranchesAndTheAssumerGoesBack() {
        // Alice holds a free instant, so her Auto-skip stops at the assumed pass of hers at Bob's spell (she could respond)
        val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to (List(20) { "Mountain" } + List(20) { "Storm Spark" })))
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        sim.bobCasts("Storm Spark", landName = "Mountain")
        sim.settle(only = listOf(bob))
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(device.status)
        assertIs<Input.PassPriority>(stop.assumed)
        val cast = device.act(Input.Cast(alice, hand(device.model, alice, glimmer))) { m ->
            m.engine.pending?.decision?.takeIf { it.player == alice && it !is Decision.Random }?.let { open ->
                Engine.options(m.engine, alice).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
        }
        assertIs<GameDevice.Acted.Sent>(cast)
        assertTrue(!device.session.behind, "the discarded line is gone from her device")
        sim.flush(alice)
        assertEquals(1, sim.branches)
        assertEquals(1, sim.server.lines)
        sim.deliver(bob)
        val note = sim.device(bob).notes().filterIsInstance<GameDevice.Note.Branched>().single()
        assertEquals(alice, note.by)
        assertEquals(stop.event.id, note.discarded.id)
        assertTrue(note.takenBack.isNotEmpty())
        // HIS-097 (problem report DY6VCM): only Bob's device's own passes and assumptions went: no action of his to name
        assertEquals(emptyList(), note.actions)
        // Bob's table is back at the branch point; Alice's cast is buffered and played back
        assertEquals(stop.event.parentId, sim.device(bob).session.localHead)
        assertIs<GameDevice.Status.Playing>(sim.device(bob).status)
        sim.device(bob).playToStop()
        sim.settle()
        assertEquals(sim.server.head, sim.device(bob).session.head)
        assertTrue(sim.device(bob).model.sameAs(sim.server.stateAt(sim.device(bob).session.localHead)))
        assertTrue(sim.leaks.isEmpty())
    }

    // HIS-094, HIS-095, HIS-035, HIS-033 (owner playtest 2026-11-06): at a stop for the player's own decision there is
    // nothing to watch before it: leaving the app and coming back, and loading the game again, show that same stop live,
    // never the catch-up's paused start ("has made moves"), though the other player's events follow it
    @Test
    fun aStopIsLiveAgainAfterLeavingAndLoading() {
        val glimmer = arcanum.engine.cards.Cards.invent("Idle Glimmer", "{0}", "Instant", "Draw a card.")
        val sim = DeviceSim(EngineGames(3, (List(10) { "Forest" } + List(30) { glimmer }) to (List(20) { "Mountain" } + List(20) { "Storm Spark" })))
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        sim.bobCasts("Storm Spark", landName = "Mountain")
        sim.settle(only = listOf(bob))
        // He plays on past it: his End turn
        sim.device(bob).endTurn(sim.now)
        sim.settle(only = listOf(bob))
        val device = sim.load(alice)
        device.play()
        device.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(device.status)
        assertTrue(device.session.buffered.size > 1, "Bob's events after the stop")
        device.away()
        assertEquals(stop, device.status, "away")
        device.returned()
        assertEquals(stop, device.status, "back")
        sim.now += 600_000
        sim.flush(alice)
        assertEquals(stop.event.parentId, sim.server.heads[alice], "her local head reported at the stop")
        assertEquals(stop, sim.load(alice).status, "loaded again")
    }

    /** The engine searches (layer computations, [Engine.layersWorkedOut]) and points worked out while [ticks] run. */
    private fun searches(device: GameDevice, ticks: () -> Unit): Pair<Int, Int> {
        var layers = 0
        val points = device.pointsWorkedOut
        Engine.layersWorkedOut = { layers++ }
        try { ticks() } finally { Engine.layersWorkedOut = null }
        return layers to device.pointsWorkedOut - points
    }

    // Engine-responsiveness R3 (I-D56 §4): the device's decision at a point is worked out once: ticks at an unchanged point
    // (the player's own move, a wait at the other player's pause) search nothing; a changed setting is a new point
    @Test
    fun aTickAtAnUnchangedPointSearchesNothing() {
        val sim = spells("Glance Ahead")
        sim.bobToMain(alwaysAssume = false)
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        fun tick() { bobs.advance(sim.now); bobs.status }
        // Bob's own main phase: his move
        tick()
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        assertEquals(0 to 0, searches(bobs) { repeat(5) { sim.now += 1_000; tick() } }, "ticks at his own move")
        // Alice catches up (Normal keeps her live device at Bob's spell); his spell is her pause: a wait with "Assume resolves"
        val device = sim.load(alice)
        device.settings(PlaySettings(mode = ResponseMode.Normal))
        device.play()
        device.playToStop()
        sim.settle()
        sim.bobCasts("Glance Ahead")
        sim.settle()
        tick()
        assertIs<GameDevice.Status.Waiting>(bobs.status)
        assertEquals(0 to 0, searches(bobs) { repeat(5) { sim.now += 60_000; tick() } }, "ticks at the wait")
        assertIs<GameDevice.Status.Waiting>(bobs.status, "a wait has no timeout")
        // "Always assume resolves" turned on: a new point, assumed at once
        val before = sim.server.line().size
        bobs.settings(PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always))
        sim.settle()
        assertTrue(sim.server.line().size > before, "assumed once the setting is on")
        assertEquals(bob, sim.server.line()[before].assumed?.assumedBy)
    }

    // HIS-096 (owner, 2026-11-05): the other player's pause is a wait with "Assume resolves" (a tap assumes it) unless
    // "Always assume resolves" is on, which assumes it at once; their presence never decides it
    @Test
    fun aPauseWaitsWithAssumeUnlessAlwaysAssumeResolves() {
        val sim = spells("Glance Ahead")
        sim.bobToMain(alwaysAssume = false)
        val alice = sim.alice; val bob = sim.bob
        // Alice in Normal (owner 2026-11-06: in Quick with nothing to respond with her own mode would pass, and Bob's device
        // would assume that at once), then away (her device gets nothing); Bob's spell is her pause
        sim.device(alice).settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(alice)
        sim.deliver(bob)
        assertEquals(ResponseMode.Normal, sim.device(bob).opponentMode)
        sim.bobCasts("Glance Ahead")
        sim.settle(only = listOf(bob))
        val waiting = assertIs<GameDevice.Status.Waiting>(sim.device(bob).status)
        assertIs<GameDevice.Wait.Respond>(waiting.reason)
        assertEquals(GameDevice.Offer.AssumeResolves, waiting.offer)
        sim.now += 3_600_000
        sim.settle(only = listOf(bob))
        assertIs<GameDevice.Status.Waiting>(sim.device(bob).status, "away for an hour: still a wait")
        val before = sim.server.line().size
        assertIs<GameDevice.Acted.Sent>(sim.device(bob).assume())
        sim.settle(only = listOf(bob))
        assertEquals(bob, sim.server.line()[before].assumed?.assumedBy, "Assume assumed her pass")
        // With "Always assume resolves" her pause is assumed at once
        val sim2 = spells("Glance Ahead")
        sim2.bobToMain(alwaysAssume = true)
        sim2.bobCasts("Glance Ahead")
        sim2.settle(only = listOf(bob))
        assertTrue(sim2.server.line().any { it.assumed?.assumedBy == bob && sim2.server.stateAt(it.parentId).engine.stack.isNotEmpty() },
            "the pass at Alice's pause was assumed at once")
    }

    // HIS-096, TRN-059 (owner playtest 2026-11-05 evening): with "When they have no mana" the other player's pause is
    // assumed at once while the table shows them no mana: nothing in their pool, nothing they could activate (Alice has
    // no permanent yet)
    @Test
    fun aPauseIsAssumedAtOnceWhenTheyHaveNoMana() {
        val sim = spells("Glance Ahead")
        sim.bobToMain(alwaysAssume = false)
        val bob = sim.bob
        sim.device(bob).settings(PlaySettings().assuming(AssumeResolves.WhenNoMana))
        sim.settle(only = listOf(bob))
        assertTrue(sim.device(bob).model.engine.battlefield.none { sim.device(bob).model.engine.obj(it)?.controller == sim.alice })
        sim.bobCasts("Glance Ahead")
        sim.settle(only = listOf(bob))
        assertTrue(sim.server.line().any { it.assumed?.assumedBy == bob && sim.server.stateAt(it.parentId).engine.stack.isNotEmpty() },
            "the pass at Alice's pause was assumed at once")
    }

    // LOB-113, HIS-096 (owner, 2026-11-06): Normal is the default; each choice is stored so; a tap goes Never, Normal, No
    // mana, Always
    @Test
    fun assumeResolvesSettingsMapFromTheirStoredForm() {
        val json = org.asyncttwiz.protocol.GameWire.json
        assertEquals(AssumeResolves.Normal, PlaySettings().assume)
        for (choice in AssumeResolves.all) assertEquals(choice,
            json.decodeFromString(PlaySettings.serializer(), json.encodeToString(PlaySettings.serializer(), PlaySettings().assuming(choice))).assume)
        assertEquals(listOf(AssumeResolves.Normal, AssumeResolves.WhenNoMana, AssumeResolves.Always, AssumeResolves.Never),
            AssumeResolves.all.map { it.next })
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
        assertEquals(GameDevice.Status.YourMove, device.status)
        assertIs<GameDevice.Acted.Sent>(device.act(Input.PassPriority(alice)))
        sim.settle()
        assertTrue(sim.device(bob).model.engine.stack.isEmpty())
        assertTrue(sim.leaks.isEmpty())
    }

    // SYS-057 (problem report N7FU6Y): the server makes an event again from its inputs, so an answer must name its
    // decision exactly as the server's engine does. A shrunk Android build named the cast's payment "cast:Mana:
    // Question$Pay@…" (a nested class's runtime name) where the server says "cast:Mana:Pay@…": every cast the phone
    // showed was refused and snapped back. The names are written out now; the early-answer key is the same on both.
    @Test
    fun aPaymentAnswerNamesItsDecisionAsTheServerDoes() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val bob = sim.bob
        val bobs = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
        sim.settle()
        val head = sim.server.head
        val model = sim.server.stateAt(head)
        val cast = Input.Cast(bob, hand(model, bob, "Glance Ahead"))
        val probe = model.copy()
        EventBuilder(probe, "probe", head, bob.value).perform(cast)
        val payment = assertIs<Decision.ManaPayment>(probe.engine.pending?.decision)
        assertEquals("ManaPayment:${bob.value}:${payment.source ?: "-"}", EarlyAnswer.keyOf(payment))
        assertTrue(payment.id.value.startsWith("cast:Mana:Pay@"), payment.id.value)
        fun submission(id: String) = Submission("cast-$id", head, listOf(Act.Perform(cast),
            Act.Perform(Input.Answer(arcanum.engine.decision.DecisionId(id), Answer.ManaPayment(payment.suggestion)))))
        Authority.make(model.copy(), submission(payment.id.value), bob)
        val phone = payment.id.value.replace(":Pay@", ":Question\$Pay@")
        val refused = assertFailsWith<Refusal> { Authority.make(model.copy(), submission(phone), bob) }
        assertTrue("no such decision" in refused.reason, refused.reason)
    }

    // TRN-067, SYS-025 (problem report N7FU6Y): an own action shows early, at its performed point, also on a tentative
    // line; the player's own passes that follow stop before one the information rule holds back (taken back exactly to
    // a mark), so that rule never refuses an action that already showed
    @Test
    fun anOwnActionShowsEarlyAndItsPassesStopBeforeAReveal() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val bob = sim.bob
        val bobs = sim.device(bob)
        assertTrue(bobs.tentative(), "Bob plays on Alice's assumed passes")
        var early = 0
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island")), performed = { early++ }))
        assertEquals(1, early, "shown at its performed point")
        sim.settle()
        // Taking back to a mark leaves exactly the state and the event made up to it
        val head = sim.server.head
        val model = sim.server.stateAt(head)
        val cast = Input.Cast(bob, hand(model, bob, "Glance Ahead"))
        val payment = assertIs<Decision.ManaPayment>(model.copy().also { m -> EventBuilder(m, "probe", head, bob.value).perform(cast) }
            .engine.pending?.decision)
        val answer = Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion))
        val whole = model.copy()
        val builder = EventBuilder(whole, "e", head, bob.value)
        builder.perform(cast); builder.perform(answer)
        val mark = builder.mark()
        builder.perform(Input.PassPriority(bob)); builder.answerRandoms()
        builder.takeBack(mark)
        val event = builder.build()
        val part = model.copy()
        val expected = EventBuilder(part, "e", head, bob.value).apply { perform(cast); perform(answer) }.build()
        assertEquals(expected, event)
        assertTrue(whole.sameAs(part))
    }

    // I-D56 §10: undo is free while nothing new was shown; otherwise a request: the game is frozen until it is answered,
    // the other player sees the board only up to their local head; accepting rewinds both devices
    // HIS-089: an undo past what the other player saw needs their approval
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

    // HIS-084, HIS-085, HIS-086 (I-D56 §10, report C9PVLM): Undo goes back to just before the player's latest own action,
    // past the passes their device made by itself after it; it only pops events (none of its own is added); it lands where
    // they hold priority and their device waits for them there; a second Undo takes back the action before; the next
    // event made there starts the new line (HIS-042)
    @Test
    fun undoTakesBackTheLatestOwnActionAndStaysThere() {
        // Bob's Mountains and Storm Sparks (red) this time
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Mountain" } + List(20) { "Storm Spark" })))
        sim.bobToMain()
        val alice = sim.alice; val bob = sim.bob
        val bobs = sim.device(bob)
        val beforeLand = sim.server.head
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Mountain"))))
        sim.bobCasts("Storm Spark", land = false)
        sim.settle()
        val beforeCast = sim.server.line().first { e -> e.engine?.steps?.firstOrNull()?.input is Input.Cast }.parentId
        // His main phase waits for him after the spark resolved (STK-053): his End turn is his own action there
        val beforeEnd = sim.server.head
        assertTrue(sim.atMain(bob))
        bobs.endTurn(sim.now)
        sim.settle()
        val after = MainLine.after(sim.server.history, sim.server.head, beforeEnd)!!
        assertTrue(after.size > 2, "his device passed on by itself after his End turn: ${after.size}")
        assertEquals(GameDevice.Undo.Free(beforeEnd), bobs.undo())
        sim.settle()
        assertEquals(beforeEnd, sim.server.head)
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        assertEquals(GameDevice.Undo.Free(beforeCast), bobs.undo())
        sim.settle()
        fun inHand(name: String) = bobs.model.engine.player(bob).hand.count { bobs.model.engine.obj(it)?.definition?.faces?.first()?.name == name }
        val sparks = inHand("Storm Spark")
        assertTrue(sim.atMain(bob), "back at his main phase, holding priority")
        assertEquals(20, bobs.model.engine.player(alice).life, "the spark's damage is gone")
        assertTrue(bobs.model.engine.battlefield.none { bobs.model.engine.obj(it)?.tapped == true }, "his Mountain is untapped again")
        assertTrue(bobs.model.sameAs(sim.server.stateAt(sim.server.head)), "his board is the server's")
        assertEquals(beforeCast, sim.server.head, "the events are popped, nothing added: his device made nothing more")
        assertEquals(beforeCast, bobs.session.head)
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        // Again: the land goes back too
        assertEquals(GameDevice.Undo.Free(beforeLand), bobs.undo())
        sim.settle()
        assertEquals(beforeLand, sim.server.head)
        assertEquals(sparks, inHand("Storm Spark"))
        assertTrue(inHand("Mountain") > 0 && bobs.model.engine.battlefield.isEmpty(), "the Mountain is back in his hand")
        assertEquals(3, sim.rewinds)
        // Played again: that event starts the new line
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Mountain"))))
        sim.settle()
        assertEquals(3, sim.server.line().single { it.parentId == beforeLand }.startsLine)
        assertTrue(bobs.model.sameAs(sim.server.stateAt(bobs.session.localHead)))
        // Alice comes back: her board is the server's, and her strip has no rewind event to show
        val alices = sim.load(alice)
        alices.play(); alices.playToStop()
        assertTrue(alices.model.sameAs(sim.server.stateAt(alices.session.localHead)))
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

    // SYS-012 (owner, 2026-11-08): a refused own action names what it was made on, the card played here, so the table
    // shakes that card; a pass names nothing (the main button shakes)
    @Test
    fun aRefusedActionNamesWhatItWasMadeOn() {
        val sim = spells("Storm Spark")
        val alice = sim.alice
        sim.start(alice)
        val alices = sim.device(alice)
        sim.keepWhileAway(alice)
        val forest = hand(alices.model, alice, "Forest")
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PlayLand(alice, forest)))
        val submit = alices.take(sim.now).filterIsInstance<org.asyncttwiz.protocol.GameAction.Submit>().last()
        alices.refused(submit, "refusedByEngine")
        assertEquals(GameDevice.Note.Refused("refusedByEngine", forest), alices.notes().filterIsInstance<GameDevice.Note.Refused>().single())
        assertEquals(null, GameDevice.madeOn(listOf(Input.PassPriority(alice))))
    }

    // I-D56 §6: a table-only event (a result suggestion) made while behind goes at the head and doesn't move the
    // author's local head; staging and early answers behind the head stay on the device; one that crosses an engine input
    // on the wire is refused as stale and made again at the new head; an engine input that crosses the other player's
    // table-only event is made on it (abreview #1: it decides nothing, so it never makes an input stale)
    @Test
    fun tableOnlyEventsGoAtTheHead() {
        val sim = spells("Storm Spark")
        val alice = sim.alice; val bob = sim.bob
        sim.start(alice)
        val alices = sim.device(alice)
        sim.keepWhileAway(alice)
        assertIs<GameDevice.Acted.Sent>(alices.act(Input.PlayLand(alice, hand(alices.model, alice, "Forest"))))
        alices.endTurn(sim.now)
        sim.settle()
        // Bob catches up, plays an Island and ends his turn while Alice is away (nothing reaches her device)
        sim.catchUp(bob)
        val bobs = sim.device(bob)
        assertTrue(sim.atMain(bob))
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Island"))))
        bobs.endTurn(sim.now)
        sim.settle(only = listOf(bob))
        // Alice comes back behind Bob's turn: her result suggestion goes at the head
        val back = sim.load(alice)
        val aliceHead = sim.server.heads[alice]
        assertTrue(back.session.behind)
        assertEquals(GameDevice.Acted.Refused("behind"),
            back.table(listOf(TableChange.EarlyAnswered("x", null, EarlyAnswer(alice, "x", Answer.YesNo(true))))))
        assertEquals(GameDevice.Acted.Kept, back.table(listOf(TableChange.BlockDraft(null, emptyList()))))
        val draw = ResultSuggestion(alice, arcanum.engine.setup.GameResult.Draw)
        val sent = assertIs<GameDevice.Acted.Sent>(back.table(listOf(TableChange.Suggestion(null, draw))))
        assertEquals(sim.server.head, sent.event.parentId)
        sim.flush(alice)
        assertEquals(sent.event.id, sim.server.head)
        assertEquals(aliceHead, sim.server.heads[alice], "it confirms nothing: her local head stays")
        assertEquals(draw, sim.server.state().table.suggestion)
        // I-D56 §6: nobody makes the other player's table changes (abreview #11)
        assertEquals("Only bob makes this change", sim.server.submit(Submission("x", sim.server.head,
            table = listOf(TableChange.Suggestion(draw, ResultSuggestion(bob, arcanum.engine.setup.GameResult.Draw)))), alice)
            .exceptionOrNull()?.message)
        sim.deliver(alice)
        assertIs<GameDevice.Acted.Sent>(back.table(listOf(TableChange.Suggestion(draw, null))))
        sim.flush(alice)
        // Normal: her device keeps her main phase for her after her land (Auto-skip would pass it: she can do nothing more)
        back.settings(PlaySettings(mode = ResponseMode.Normal))
        sim.catchUp(alice)
        sim.settle()
        assertTrue(sim.atMain(alice) && !back.session.behind && !bobs.session.behind)
        // Alice plays a land while Bob answers early: her land reaches the server first; his answer is made again on it
        val early = EarlyAnswer(bob, "early:bob", Answer.YesNo(true))
        bobs.table(listOf(TableChange.EarlyAnswered(early.key, null, early)))
        assertIs<GameDevice.Acted.Sent>(back.act(Input.PlayLand(alice, hand(back.model, alice, "Forest"))))
        sim.flush(alice); sim.flush(bob)
        sim.settle()
        assertEquals(early, sim.server.state().table.earlyAnswers[early.key])
        assertTrue(bobs.model.sameAs(sim.server.state()) && back.model.sameAs(sim.server.state()))
        // Bob takes it back while Alice passes: his change first; her pass is made on it, not refused
        bobs.table(listOf(TableChange.EarlyAnswered(early.key, early, null)))
        val pass = assertIs<GameDevice.Acted.Sent>(back.act(Input.PassPriority(alice)))
        sim.flush(bob); sim.flush(alice)
        sim.settle()
        assertEquals(emptyList(), back.notes())
        val taken = sim.server.line().single { it.id == pass.event.id }
        assertEquals(sim.server.line().single { it.table.any { c -> c is TableChange.EarlyAnswered && c.before == early && c.after == null } }.id, taken.parentId)
        assertTrue(bobs.model.sameAs(sim.server.state()) && back.model.sameAs(sim.server.stateAt(back.session.localHead)))
    }

    /** Forests and Elvish Seers (1/1) for both, at 1 life: Alice's turn-3 attack is lethal unless Bob blocks. */
    private fun seers() = DeviceSim(EngineGames(5, List(40) { if (it % 2 == 0) "Forest" else "Elvish Seer" }.let { it to it }, life = 1))

    /** Both play a Forest and an Elvish Seer in their first turns; in Alice's third, with Bob away (nothing delivered to
     * him), she attacks with her Seer: her Seer. */
    private fun DeviceSim.seersToCombat(alwaysAssume: Boolean = true): ObjectId {
        val seer = seersToThirdTurn()
        // "Assume resolves" Never from her third turn on (her earlier turns assume Bob's pauses as both play on)
        if (!alwaysAssume) device(alice).settings(PlaySettings(mode = ResponseMode.Normal))
        device(alice).act(Input.PassPriority(alice))
        settle(only = listOf(alice))
        assertEquals(GameDevice.Status.YourMove, device(alice).status)
        device(alice).act(Input.DeclareAttackers(alice, listOf(Attack(seer, TargetRef.Player(bob)))))
        settle(only = listOf(alice))
        return seer
    }

    // HIS-109, SYS-012 (a full e2e run, 2026-11-08): Alice's attack shows at once with her priority (act's performed point),
    // then her pass by her mode folds into its event. A tap of the main button on that board is that very press, also once
    // the server has taken the event; on the board before the attack the same input is another move, and so is Bob's pass
    @Test fun aPassFoldedIntoHerActionIsThePressOnItsOwnBoard() {
        val sim = seers()
        val seer = sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        alice.act(Input.PassPriority(sim.alice))
        sim.settle(only = listOf(sim.alice))
        val parent = alice.session.tip
        val pass = Input.PassPriority(sim.alice)
        var holder: PlayerId? = null
        val attack = assertIs<GameDevice.Acted.Sent>(alice.act(Input.DeclareAttackers(sim.alice, listOf(Attack(seer, TargetRef.Player(sim.bob)))),
            performed = { holder = alice.model.engine.priority.holder })).event
        assertEquals(sim.alice, holder, "her priority on the attack's own board")
        assertEquals(pass, attack.engine?.steps.orEmpty().mapNotNull { it.input }.getOrNull(1), "her pass folded into the attack's event")

        assertTrue(alice.pressedInAction(parent, pass), "the press the attack's own board offered")
        assertFalse(alice.pressedInAction(parent, Input.PassPriority(sim.bob)), "another input")
        assertFalse(alice.pressedByItself(parent, pass), "on the board before the attack it is no press of her phone's")
        sim.settle(only = listOf(sim.alice))
        assertTrue(alice.pressedInAction(parent, pass), "still that press on the main line")
    }

    // HIS-109, SYS-011: in Control nothing folds into her attack's event: she holds priority, and her tap is her own pass
    @Test fun nothingFoldedIntoHerActionLeavesHerPressHers() {
        val sim = seers()
        val seer = sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        alice.settings(PlaySettings(mode = ResponseMode.FullControl))
        repeat(5) {
            if (alice.model.engine.turn.step != TurnStep.DeclareAttackers) {
                alice.act(Input.PassPriority(sim.alice))
                sim.settle(only = listOf(sim.alice))
            }
        }
        assertEquals(TurnStep.DeclareAttackers, alice.model.engine.turn.step)
        val parent = alice.session.tip
        assertIs<GameDevice.Acted.Sent>(alice.act(Input.DeclareAttackers(sim.alice, listOf(Attack(seer, TargetRef.Player(sim.bob))))))
        assertFalse(alice.pressedInAction(parent, Input.PassPriority(sim.alice)))
    }

    // HIS-113 (owner, 2026-11-08): the attackers hold Alice's view, her phone making none of its own steps, until done; then
    // the damage holds it too; Off there are no holds; the game goes on for the other player (their events are never held)
    @Test fun aCombatHoldsTheViewOnTheAttackersAndOnTheDamage() {
        val sim = seers()
        val seer = sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        alice.showPlays = true
        alice.act(Input.PassPriority(sim.alice))
        sim.settle(only = listOf(sim.alice))
        assertNull(alice.combatHold)
        alice.act(Input.DeclareAttackers(sim.alice, listOf(Attack(seer, TargetRef.Player(sim.bob)))))
        val hold = assertNotNull(alice.combatHold, "the attackers show")
        assertEquals(GameDevice.CombatMoment.Attackers, hold.kind)
        val tip = alice.session.tip
        sim.settle(only = listOf(sim.alice))
        alice.advance(sim.now)
        assertEquals(tip, alice.session.tip, "her phone makes nothing of its own meanwhile")
        assertNull(alice.show, "no play shown for it")
        assertTrue(alice.dismissCombatHold(hold))
        assertEquals(hold.event, alice.showDone)
        sim.settle(only = listOf(sim.alice))
        assertNull(alice.combatHold, "on to Bob's blocks")
        // Bob's blocks assumed, her own passes and his on to the damage
        repeat(8) {
            if (alice.combatHold == null) {
                if (alice.status == GameDevice.Status.YourMove) alice.act(Input.PassPriority(sim.alice)) else alice.assume(emptyList())
                sim.settle(only = listOf(sim.alice))
            }
        }
        val damage = assertNotNull(alice.combatHold, "the damage holds too")
        assertEquals(GameDevice.CombatMoment.Damage, damage.kind)
        alice.showPlays = false
        assertNull(alice.combatHold, "Off: none")
    }

    // HIS-084, HIS-101, HIS-099 (owner, 2026-11-08): her press leaving her own main phase is an undo point, unlike the passes
    // after it: one Undo is back there, free, and her phone passes nothing there; her End turn's first pass records End turn
    @Test fun aPressLeavingTheMainPhaseIsAnUndoPoint() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        val pass = assertIs<GameDevice.Acted.Sent>(alice.act(Input.PassPriority(sim.alice))).event
        assertEquals(MainPress.ToCombat, pass.press)
        sim.settle(only = listOf(sim.alice))
        assertTrue(alice.model.engine.turn.step != arcanum.api.TurnStep.PrecombatMain, "past her main phase")
        assertEquals(pass.parentId, alice.undoTarget())
        assertIs<GameDevice.Undo.Free>(alice.undo())
        sim.settle(only = listOf(sim.alice))
        assertEquals(pass.parentId, alice.session.tip, "back in her main phase, her phone passing nothing")
        assertEquals(arcanum.api.TurnStep.PrecombatMain, alice.model.engine.turn.step)
        val made = alice.session.pending.size
        alice.endTurn(sim.now)
        val ending = alice.session.pending.drop(made)
        assertEquals(MainPress.EndTurn, ending.first().press, "End turn is its own kind of point")
        assertTrue(ending.drop(1).all { it.press == null }, "its later passes stay plain: ${ending.map { it.press }}")
    }

    // HIS-101 (owner, 2026-11-08): the mark says what was pressed: To end step from her second main phase in Control; no mark on
    // her passes between them, nor on an assumed pass
    @Test fun onlyThePressIsMarked() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        val bob = sim.device(sim.bob)
        alice.settings(PlaySettings(mode = ResponseMode.FullControl))
        assertEquals(MainPress.ToCombat, assertIs<GameDevice.Acted.Sent>(alice.act(Input.PassPriority(sim.alice))).event.press)
        sim.settle(only = listOf(sim.alice))
        // On to her second main phase: her declaration of no attackers and her later passes, none of them presses out of a main phase
        var guard = 0
        while (alice.model.engine.turn.step != arcanum.api.TurnStep.PostcombatMain && guard++ < 40) {
            val s = alice.model.engine
            val acted = when {
                s.pending == null && s.priority.holder == sim.alice -> alice.act(Input.PassPriority(sim.alice))
                s.pending == null && s.priority.holder == null && s.turn.active == sim.alice -> alice.act(Input.DeclareAttackers(sim.alice, emptyList()))
                else -> alice.assume(emptyList())
            }
            if (alice.model.engine.turn.step != arcanum.api.TurnStep.PostcombatMain) assertNull((acted as? GameDevice.Acted.Sent)?.event?.press, "$s")
            sim.settle(only = listOf(sim.alice))
        }
        assertEquals(arcanum.api.TurnStep.PostcombatMain, alice.model.engine.turn.step)
        assertEquals(MainPress.ToEndStep, assertIs<GameDevice.Acted.Sent>(alice.act(Input.PassPriority(sim.alice))).event.press)
        sim.settle()
        val line = alice.session.events + alice.session.pending
        assertTrue(line.any { it.assumed != null } && line.filter { it.assumed != null }.all { it.press == null }, "an assumed pass never has one")
        assertEquals(listOf(MainPress.ToCombat, MainPress.ToEndStep), line.mapNotNull { it.press }.takeLast(2))
        assertEquals(line.last { it.press != null }.id, bob.session.events.last { it.press != null }.id, "the other phone holds the same record")
    }

    // HIS-101, SYS-077 (owner, 2026-11-08): a press lost in a race, made again on the other player's event, keeps its mark
    @Test fun aPressMadeAgainAfterALostRaceKeepsItsMark() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        val bob = sim.device(sim.bob)
        sim.settle()
        // Her press on the wire, refused as out of date; the event the server took first (Bob's early answer) reaches her after it
        val lost = assertIs<GameDevice.Acted.Sent>(alice.act(Input.PassPriority(sim.alice))).event
        assertEquals(MainPress.ToCombat, lost.press)
        alice.take(sim.now)
        alice.refused(org.asyncttwiz.protocol.GameAction.Submit(Submission.of(lost)), "stale")
        val early = EarlyAnswer(sim.bob, "early:bob", Answer.YesNo(true))
        val first = assertIs<GameDevice.Acted.Sent>(bob.table(listOf(TableChange.EarlyAnswered(early.key, null, early)))).event
        sim.flush(sim.bob)
        sim.deliver(sim.alice)
        alice.playToStop()
        alice.advance(sim.now)
        val again = alice.session.pending.first()
        assertEquals(first.id, again.parentId, "made again on his event")
        assertTrue(again.id != lost.id)
        assertEquals(MainPress.ToCombat, again.press)
        // ... and the server keeps it: her phone's event is the one it stored (a difference would reload her game)
        sim.settle()
        assertEquals(false, alice.needsReload)
        assertEquals(MainPress.ToCombat, alice.session.events.single { it.id == again.id }.press)
        assertEquals(MainPress.ToCombat, bob.session.events.single { it.id == again.id }.press)
    }

    // HIS-101 (owner, 2026-11-08): the event and its submission carry the mark: the server makes the same event from what the
    // device sends (Authority.make), through the stored form; an old event without the field decodes with no mark
    @Test fun theMarkTravelsAndOldEventsDecode() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        val event = assertIs<GameDevice.Acted.Sent>(alice.act(Input.PassPriority(sim.alice))).event
        assertEquals(MainPress.ToCombat, Submission.of(event).press)
        val codec = EngineJson(GameDefinitions.of(alice.model.engine))
        val text = codec.encodeEvent(event)
        assertTrue("\"press\":{\"type\":\"combat\"}" in text, text)
        assertEquals(event, codec.decodeEvent(text))
        val old = codec.decodeEvent(text.replace(",\"press\":{\"type\":\"combat\"}", ""))
        assertNull(old.press)
        assertEquals(event.copy(press = null), old)
        for (press in listOf(MainPress.ToCombat, MainPress.ToEndStep, MainPress.EndTurn))
            assertEquals(press, codec.decodeEvent(codec.encodeEvent(event.copy(press = press))).press)
        assertTrue("press" !in codec.encodeEvent(event.copy(press = null)), "no mark, no field")
    }

    // HIS-113: declaring no attackers holds nothing
    @Test fun noAttackersHoldNothing() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.device(sim.alice)
        alice.showPlays = true
        alice.act(Input.PassPriority(sim.alice))
        sim.settle(only = listOf(sim.alice))
        alice.act(Input.DeclareAttackers(sim.alice, emptyList()))
        assertNull(alice.combatHold)
    }

    /** As [seersToCombat], up to Alice's third turn's first main phase, holding priority there: her Seer. */
    private fun DeviceSim.seersToThirdTurn(mode: ResponseMode = ResponseMode.Normal): ObjectId {
        val alice = alice
        start(alice, mode)
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
        return m.battlefield.first { m.obj(it)?.controller == alice && m.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }
    }

    // HIS-096, TRN-070 (owner, 2026-11-06): with "Assume resolves" Normal (the default), the other player's pause right after your
    // attack is assumed at once (no wait with Assume before their blocks); the priority after their blocks keeps the
    // setting (a wait with Assume); their catch-up playback still stops at the pass assumed after the attack (HIS-095)
    @Test
    fun theirPauseAfterYourAttackIsAssumedAtOnce() {
        val sim = seers()
        sim.seersToCombat(alwaysAssume = false)
        val alice = sim.alice; val bob = sim.bob
        assertEquals(AssumeResolves.Normal, sim.device(alice).settings.assume)
        val afterAttack = sim.server.line().single { e ->
            val before = sim.server.stateAt(e.parentId).engine
            e.assumed?.assumedBy == alice && before.turn.step == TurnStep.DeclareAttackers && before.turn.number == 3
        }
        assertEquals(Input.PassPriority(bob), afterAttack.assumed?.input)
        val blocks = assertIs<GameDevice.Status.Waiting>(sim.device(alice).status)
        assertEquals(GameDevice.Wait.Blockers, blocks.reason, "straight on to his blocks")
        assertIs<GameDevice.Acted.Sent>(sim.device(alice).assume())
        sim.settle(only = listOf(alice))
        sim.passToDamage()
        val afterBlocks = assertIs<GameDevice.Status.Waiting>(sim.device(alice).status, "his priority after the blocks waits")
        assertEquals(GameDevice.Wait.MayAct(TurnStep.DeclareBlockers), afterBlocks.reason)
        assertEquals(GameDevice.Offer.AssumeResolves, afterBlocks.offer)
        // Bob comes back in Control: his playback stops at the pass assumed after her attack
        val bobs = sim.load(bob)
        bobs.settings(PlaySettings(mode = ResponseMode.FullControl))
        bobs.play()
        val stops = mutableListOf<GameDevice.Status.Stopped>()
        while (true) {
            bobs.playToStop()
            val stop = bobs.status as? GameDevice.Status.Stopped ?: break
            stops += stop
            if (stop.assumed is Input.DeclareBlockers) break
            bobs.act(stop.assumed)
        }
        assertTrue(stops.any { it.event.id == afterAttack.id }, "playback stopped at the assumed pass after the attack: $stops")
        assertTrue(sim.leaks.isEmpty())
    }

    // HIS-096, TRN-070 (owner, 2026-11-06): with "Assume resolves" Normal (the default), the other player's pause at your end step is
    // assumed at once: Alice's End turn goes on into Bob's turn while he is away
    @Test
    fun theirPauseAtYourEndStepIsAssumedAtOnce() {
        val sim = seers()
        sim.seersToThirdTurn()
        val alice = sim.alice; val bob = sim.bob
        val alices = sim.device(alice)
        alices.settings(PlaySettings(mode = ResponseMode.Normal))
        assertEquals(AssumeResolves.Normal, alices.settings.assume)
        alices.endTurn(sim.now)
        sim.settle(only = listOf(alice))
        assertEquals(4, alices.model.engine.turn.number, "into Bob's turn: ${alices.model.engine.turn}")
        assertEquals(bob, alices.model.engine.turn.active)
        assertTrue(sim.server.line().any { e ->
            val before = sim.server.stateAt(e.parentId).engine
            e.assumed?.assumedBy == alice && e.assumed?.input == Input.PassPriority(bob) && before.turn.step == TurnStep.End && before.turn.number == 3
        }, "his pass at her end step assumed")
        assertEquals(GameDevice.Wait.TheirTurn, assertIs<GameDevice.Status.Waiting>(alices.status).reason)
    }

    // TRN-057, HIS-058 (smoke 2026-11-04): End turn passes the player's priority until the turn ends; their own main
    // phases (which Auto-skip never passes, STK-053) don't stop it: one End turn from the first main phase or from combat
    // (attackers awaited) reaches the other player's turn
    @Test
    fun endTurnPassesYourOwnMainPhases() {
        for (fromCombat in listOf(false, true)) {
            val sim = seers()
            sim.seersToThirdTurn(ResponseMode.AutoSkip)
            val alice = sim.alice; val bob = sim.bob
            val alices = sim.device(alice)
            assertEquals(3, alices.model.engine.turn.number)
            if (fromCombat) {
                alices.act(Input.PassPriority(alice))
                sim.settle(only = listOf(alice))
                assertEquals(arcanum.api.TurnStep.DeclareAttackers, alices.model.engine.turn.step, "attackers awaited")
            }
            alices.endTurn(sim.now)
            sim.settle(only = listOf(alice))
            val s = alices.model.engine
            assertEquals(4, s.turn.number, "from ${if (fromCombat) "combat" else "Main 1"}: ${s.turn}")
            assertEquals(bob, s.turn.active)
            assertTrue(s.combat?.attackers.isNullOrEmpty())
        }
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

    // I-D56 §3, §4, §11, §12, CMB-040: declaring attackers leaves a wait with Assume blockers (the screen it opens, with
    // "No blocks", assumes them); the lethal damage's result waits for Bob to catch up (OK greyed); his playback always stops at
    // assumed blocks, and the same blocks confirm; OK once both local heads reached the result
    @Test
    fun assumedBlockersAndAResultOnATentativeLine() {
        val sim = seers()
        sim.seersToCombat()
        val alice = sim.alice; val bob = sim.bob
        val waiting = assertIs<GameDevice.Status.Waiting>(sim.device(alice).status)
        assertEquals(GameDevice.Wait.Blockers, waiting.reason)
        assertEquals(GameDevice.Offer.AssumeBlockers, waiting.offer)
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
        // HIS-097: her assumption and her passes to the damage are no action of hers
        assertEquals(emptyList(), note.actions)
        assertNull(Engine.result(sim.device(alice).model.engine))
        assertNull(Engine.result(sim.server.state().engine))
        assertTrue(sim.leaks.isEmpty())
    }

    // STK-053, STK-026 (owner, smoke 2026-11-04): Auto-skip never passes the player's own main phase, even with nothing
    // left to do: a land played, or a creature cast with all their mana (resolved on Alice's assumed pass), leaves Bob in
    // his first main phase with priority; his turn goes on only by his own pass
    // SYS-063: Auto-skip runs on the player's own device and never passes their own main phase
    @Test
    fun autoSkipStaysInYourOwnMainPhase() {
        val sim = DeviceSim(EngineGames(3, List(40) { "Island" } to List(40) { if (it % 2 == 0) "Forest" else "Elvish Seer" }))
        sim.bobToMain()
        val bob = sim.bob
        val bobs = sim.device(bob)
        assertEquals(ResponseMode.AutoSkip, bobs.settings.mode)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Forest"))))
        sim.settle()
        assertTrue(sim.atMain(bob), "after the land: ${bobs.model.engine.turn}")
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, "Elvish Seer")), follow = { m ->
            m.engine.pending?.decision?.takeIf { it.player == bob && it !is Decision.Random }?.let { open ->
                Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first { it.decision == open.id } }
        }))
        sim.settle()
        val m = bobs.model.engine
        assertTrue(m.battlefield.any { m.obj(it)?.definition?.faces?.first()?.name == "Elvish Seer" }, "the Seer resolved")
        assertTrue(sim.atMain(bob), "after the cast: ${m.turn}")
        assertEquals(1, m.turn.number)
        assertEquals(GameDevice.Status.YourMove, bobs.status)
        assertEquals(sim.server.head, bobs.session.tip, "the server's head is there too")
    }

    // STK-036, STK-030, SYS-063, CR 603.3b (owner 2026-11-06): two different triggers of Bob's that go on the Stack at the
    // same time are his to order in every response mode: his Auto-skip device asks him rather than ordering them itself
    @Test
    fun autoSkipAsksTheOrderOfTwoTriggers() {
        val herald = arcanum.engine.cards.Cards.invent("Twinbloom Herald", "{G}", "Creature — Elf Druid",
            "When this creature enters, you gain 1 life.\nWhen this creature enters, draw a card.", "1/1")
        val sim = DeviceSim(EngineGames(3, List(40) { "Island" } to List(40) { if (it % 2 == 0) "Forest" else herald }))
        sim.bobToMain()
        val bob = sim.bob
        val bobs = sim.device(bob)
        assertEquals(ResponseMode.AutoSkip, bobs.settings.mode)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.PlayLand(bob, hand(bobs.model, bob, "Forest"))))
        sim.settle()
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(bob, hand(bobs.model, bob, herald))))
        sim.settle()
        val order = assertIs<Decision.OrderTriggers>(bobs.model.engine.pending?.decision, "the order is asked: ${bobs.model.engine.turn}")
        assertEquals(bob, order.player)
        assertEquals(2, order.triggers.size)
        assertEquals(GameDevice.Status.YourMove, bobs.status)
    }

    // TRN-027 (smoke 2026-11-04): both players keep at the same moment and the server takes Bob's first, refusing
    // Alice's as stale: her device shows his keep and makes hers again on it at once, without telling her of a refusal
    // (the refusal or his keep reaching her first alike); each keep is on the main line once
    @Test
    fun simultaneousKeepsAreBothTaken() {
        for (refusalFirst in listOf(false, true)) {
            val sim = spells("Storm Spark")
            val alice = sim.alice; val bob = sim.bob
            sim.start(alice)
            val alices = sim.device(alice); val bobs = sim.device(bob)
            assertIs<GameDevice.Acted.Sent>(alices.act(sim.keep(alice)))
            assertIs<GameDevice.Acted.Sent>(bobs.act(assertNotNull(bobs.openingInput(Answer.Option("Keep")))))
            sim.flush(bob)
            sim.flush(alice)
            val bobsKeep = sim.server.head
            if (refusalFirst) sim.deliverRefusalsFirst(alice) else sim.deliver(alice)
            alices.advance(sim.now)
            assertEquals(false, alices.session.behind, "his keep is shown")
            assertEquals(bobsKeep, alices.session.pending.first().parentId, "hers is made again on his")
            assertTrue(alices.notes().none { it is GameDevice.Note.Refused }, "no refusal told")
            sim.settle()
            fun keeps(p: PlayerId) = sim.server.line().count { e -> e.author == p.value && e.assumed == null &&
                e.engine?.steps?.firstNotNullOfOrNull { it.input }.let { input ->
                    (input is Input.Answer && input.answer == Answer.Option("Keep")) ||
                        (input is Input.SetupAnswer && input.answer == Answer.Option("Keep")) } }
            assertEquals(1, keeps(alice), "refusal first: $refusalFirst")
            assertEquals(1, keeps(bob))
            assertTrue(sim.atMain(alice), "Alice is in her first main phase: ${alices.model.engine.turn}")
            assertEquals(emptyList(), sim.equivalence())
        }
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

    // LOB-089: the record keeps its newest entries up to its bound, and the bundle carries them all
    @Test
    fun theRecordKeepsItsNewestEntries() {
        val sim = spells("Glance Ahead")
        sim.bobToMain()
        val device = sim.device(sim.bob)
        repeat(GameDevice.MAX_RECORD + 100) { device.answerRequest(it % 2 == 0) }
        val log = device.repro(sim.server.history.codec).log
        assertEquals(GameDevice.MAX_RECORD, log.size)
        assertEquals(device.recentRecord(1).single(), log.last())
        assertTrue(log.last().startsWith("out EngineAnswerRewind"))
    }

    // TRN-060 (CR 104.4b): Alice's Restless Wisp enters and is sacrificed, dies and returns, with only passes nobody can
    // avoid in between (her one card cast, Bob holding none): her device stops at the position met again with the
    // engine's loop choice for the player holding priority; Draw makes the game a draw, Continue goes on
    @Test
    fun aLoopOfForcedPassesAsksTheLoopChoice() {
        val wisp = arcanum.engine.cards.Cards.invent("Restless Wisp", "{0}", "Creature — Spirit",
            "When this creature enters, sacrifice a creature.\nWhen this creature dies, return it to the battlefield under its owner's control.",
            "1/1")
        for (answer in listOf(arcanum.engine.decision.LoopStop.DRAW, arcanum.engine.decision.LoopStop.CONTINUE)) {
            val sim = DeviceSim(EngineGames(3, List(40) { wisp } to List(40) { "Island" }))
            val alice = sim.alice
            sim.start(alice, handSizes = mapOf(alice to 1, sim.bob to 0))
            assertIs<GameDevice.Acted.Sent>(sim.device(alice).act(sim.keep(alice)))
            sim.settle()
            assertTrue(sim.atMain(alice), "Alice is at her main phase: ${sim.device(alice).model.engine.turn}")
            val alices = sim.device(alice)
            assertIs<GameDevice.Acted.Sent>(alices.act(Input.Cast(alice, hand(alices.model, alice, wisp))))
            sim.settle(rounds = 5)
            val state = sim.server.state().engine
            val choice = assertIs<Decision.ChooseOption>(state.pending?.decision, "the loop choice")
            assertEquals(state.priority.holder, choice.player, "asked of the player holding priority")
            assertEquals(listOf("Continue", "Draw"), choice.options)
            assertEquals("The game is in a loop of actions nobody can stop, which makes it a draw (CR 104.4b). End it as a draw, " +
                "or continue?", choice.text)
            assertTrue(sim.server.line().any { e -> e.engine?.steps.orEmpty().any { it.input == Input.MandatoryLoop } })
            val decider = sim.device(choice.player)
            val before = sim.server.line().size
            assertIs<GameDevice.Acted.Sent>(decider.act(Input.Answer(choice.id, Answer.Option(answer))))
            sim.settle(rounds = 5)
            if (answer == arcanum.engine.decision.LoopStop.DRAW) assertEquals(GameResult.Draw, Engine.result(sim.server.state().engine))
            else {
                assertNull(Engine.result(sim.server.state().engine), "the game goes on")
                assertTrue(sim.server.line().size > before)
            }
            assertTrue(sim.leaks.isEmpty())
        }
    }
}

private fun DeviceSim.take(p: PlayerId) = device(p).take(now)
