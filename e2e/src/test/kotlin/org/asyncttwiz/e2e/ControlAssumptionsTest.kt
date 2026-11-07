package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Control mode and assumptions (owner, 2026-11-06, 2026-11-07): each phone learns the other player's response mode. At a
 * point you began (the last real input since the step began was yours: a cast, an activation, an attack or block, an
 * answer; passes don't count), your phone assumes the other player's pass where your Assume setting does or their own
 * mode would pass (their Quick judged on their real hand); at a point they began (or one of their own steps nothing was
 * input in yet), only their mode decides, and where it stops your phone waits for them. An automatic assumption is a press
 * of Assume: a live move of the player it is about that arrives after it branches instead of being refused. A resolve by
 * hand after one's own cast looks to the other player exactly like the pass a lower mode makes by itself.
 */
class ControlAssumptionsTest : E2e() {
    /** A wait with Assume [player]'s table showed: its offer, whose turn and which step it was. */
    private data class Waited(val offer: GameDevice.Offer, val active: String, val step: TurnStep, val stack: Int)

    /** Every wait with an Assume button [player]'s table shows from now on. */
    private fun assumeWaits(player: () -> E2ePlayer): MutableList<Waited> {
        val waits = mutableListOf<Waited>()
        everyRun += {
            player().game?.shown?.let { shown ->
                (shown.status as? GameDevice.Status.Waiting)?.offer?.let { offer ->
                    val s = shown.engine
                    Waited(offer, s.turn.active.value, s.turn.step, s.stack.size).takeIf { it != waits.lastOrNull() }?.let(waits::add)
                }
            }
        }
        return waits
    }

    /** The events [by]'s phone assumed about [about]'s pass, on its line. */
    private fun assumedPasses(by: E2ePlayer, about: E2ePlayer) = by.engine { device.session.events + device.session.pending }
        .filter { it.assumed?.assumedBy == by.me && it.assumed?.input == Input.PassPriority(about.me) }

    /** Whether [player]'s table waits for the other player with "Assume resolves" (or "Assume no response"). */
    private fun waitsWithAssume(player: E2ePlayer) = (player.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves

    /** [player]'s phone, on a wait with Assume, still waiting a minute of its time later, having assumed nothing more. */
    private fun keepsWaiting(player: E2ePlayer, other: E2ePlayer) {
        val assumed = assumedPasses(player, other).size
        repeat(3) { run(skip = Duration.ofSeconds(20)) }
        assertEquals(assumed, assumedPasses(player, other).size, "${player.name}'s phone assumed nothing of ${other.name}'s")
        assertTrue(waitsWithAssume(player), "still waiting with Assume: ${player.status}")
    }

    /**
     * [player] goes on with taps of the main button (Pass, Next, Resolve, To combat, End step, No attack, No blocks;
     * also at a playback stop, and the centre text of a catch-up) until [done]; each of [along] plays along.
     */
    private fun stepsUntil(player: E2ePlayer, what: String, along: List<E2ePlayer> = emptyList(), done: () -> Boolean) =
        waitUntil(what, along = along) {
            if (!done() && player.foreground && player.engine { device.session.pending.isEmpty() }) when (player.status) {
                GameDevice.Status.CatchUp -> player.once { player.tapToWatch() }
                // Live, only on a settled game: a tap on a board still on its way passes the point [done] waits for
                GameDevice.Status.YourMove -> if (quiet())
                    player.stepArrow()?.takeIf { it in TAPS }?.let { arrow -> player.once { player.tap(arrow) } }
                is GameDevice.Status.Stopped ->
                    player.stepArrow()?.takeIf { it in TAPS }?.let { arrow -> player.once { player.tap(arrow) } }
                else -> {}
            }
            done()
        }

    /** The main button's taps that go on one point (End turn's tap ends the turn: never). */
    private val TAPS = STEP_LABELS - "End turn"

    /** [player]'s own Main 1 of turn [turn] (or later), with nothing of theirs on its way. */
    private fun inMain(player: E2ePlayer, turn: Int = 1) = player.inOwnMain() && player.state.turn.number >= turn &&
        player.engine { device.session.pending.isEmpty() }

    /** Both keep; Bob starts and plays on with the main button to his Main 1, Alice playing along. */
    private fun bobToHisMain() {
        alice.tap("Keep")
        bob.tap("Keep")
        stepsUntil(bob, "Bob's Main 1", along = listOf(alice)) { inMain(bob) && quiet() }
    }

    /** Every notice of the game [player]'s account got from now on (activity id, "your move"), read every 300 ms or so. */
    private fun notices(player: () -> E2ePlayer): MutableList<Pair<Long, Boolean>> {
        val seen = mutableListOf<Pair<Long, Boolean>>()
        var at = 0L
        everyRun += {
            val now = System.nanoTime()
            if (now - at > 300_000_000L) {
                at = now
                ServerSetup.gameNotice(player().account, game.id)?.takeIf { it != seen.lastOrNull() }?.let(seen::add)
            }
        }
        return seen
    }

    /** [player]'s timeline events (icon and text) not in [before] (event ids). */
    private fun timelineSince(player: E2ePlayer, before: Set<String>) = player.game!!.let { g ->
        g.timeline.events(g, names()).filter { it.id !in before }.map { it.icon to it.description }
    }

    private fun timelineIds(player: E2ePlayer) = player.game!!.let { g -> g.timeline.events(g, names()).map { it.id }.toSet() }

    // ---- At a point you began: your setting or their mode

    // HIS-096 (owner 2026-11-06, spec rule 5; regression): Bob is in Quick and holds only lands, so he couldn't respond to
    // anything; Alice's phone on Never assumes his passes at once (his own mode would pass them), also at her Herald on the
    // Stack, his pause: no wait with Assume while he is away
    @Test fun neverAssumesAtOnceWhatTheirQuickWouldPass() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck("Lands", listOf(TestCards.mountain to 40)),
            aliceSettings = PlaySettings().assuming(AssumeResolves.Never), deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        val waits = assumeWaits { alice }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald and its token on Alice's battlefield") {
            alice.field("Ember Herald").isNotEmpty() && alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain()
        }
        assertTrue(waits.isEmpty(), "no wait with Assume: $waits")
        assertTrue(assumedPasses(alice, bob).isNotEmpty(), "his passes assumed")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // HIS-096, TRN-070 (owner 2026-11-07, scenarios 2 and 4; regression): Bob is away in Control; Alice's points follow her
    // "Assume resolves" Normal: her Ember Dasher on the Stack is his pause (Normal and Control both stop: a wait); after it
    // resolved, his priority as she passes her main phase and at her beginning of combat is assumed (Normal passes there),
    // so is his pause right after her attack and at her end step (Normal's own); after the blocks (none: he has no creature) it waits
    @Test fun normalAssumesAtYourPointsAgainstControl() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)), TestDeck.thirds(),
            bobSettings = PlaySettings(mode = ResponseMode.FullControl), deal = { a, _ -> "Ember Dasher" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        assertEquals(AssumeResolves.Normal, alice.shown.settings.assume)
        val waits = assumeWaits { alice }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("Alice waiting for Bob at her Dasher") { waitsWithAssume(alice) && alice.state.stack.size == 1 }
        alice.tap("Assume resolves")
        waitUntil("the Dasher on the battlefield") { alice.field("Ember Dasher").isNotEmpty() && alice.inOwnMain() }
        waits.clear()
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.status == GameDevice.Status.YourMove }
        assertTrue(waits.isEmpty(), "his priority in her main phase and beginning of combat assumed: $waits")
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the Dasher staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        // He has no creature: no blocks to wait for
        waitUntil("Alice waiting for Bob after the blocks") { waitsWithAssume(alice) && alice.state.turn.step == TurnStep.DeclareBlockers }
        assertTrue(waits.none { it.step == TurnStep.DeclareAttackers }, "his pause right after her attack assumed: $waits")
        alice.tap("Assume no response")
        waitUntil("Alice in her second main phase") { alice.inOwnMain(TurnStep.PostcombatMain) }
        waits.clear()
        alice.hold(alice.stepArrow()!!)
        waitUntil("Bob's turn on Alice's table") { alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() } }
        assertTrue(waits.none { it.active == alice.id }, "her end step assumed: $waits")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // HIS-096, TRN-070, TRN-058 (owner 2026-11-07, scenarios 3 and 4; regression): the same with "Assume resolves" Never:
    // each of Bob's priorities in her turn is a wait (Never and his Control both stop), also as she passes her main phase
    // after her spell, right after her attack and at her end step; while it waits it is Bob's move
    @Test fun neverWaitsAtYourPointsAgainstControl() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)), TestDeck.thirds(),
            aliceSettings = PlaySettings().assuming(AssumeResolves.Never), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { a, _ -> "Ember Dasher" in hand(a) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's own keep on Alice's table") { alice.state.turn.number >= 1 && alice.shown.held.any { it.author == bob.id && it.assumed == null } }
        away(bob, alice)
        val waits = assumeWaits { alice }
        /** Alice presses each wait's Assume until [done]. */
        fun assumingUntil(what: String, done: () -> Boolean) = waitUntil(what) {
            if (!done() && waitsWithAssume(alice)) alice.once {
                alice.tap(if (alice.showsControl("Assume resolves")) "Assume resolves" else "Assume no response")
            }
            done()
        }
        assumingUntil("Alice's Main 1") { inMain(alice) }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("Alice waiting for Bob at her Dasher") { waitsWithAssume(alice) && alice.state.stack.size == 1 }
        waitUntil("the server naming Bob's move") { ServerSetup.head(alice.account, game.id).summary.attentionHolder == bob.id }
        assumingUntil("the Dasher on the battlefield") { alice.field("Ember Dasher").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        assumingUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.status == GameDevice.Status.YourMove }
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the Dasher staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        waitUntil("Alice waiting for Bob right after her attack") { waitsWithAssume(alice) && alice.state.turn.step == TurnStep.DeclareAttackers }
        assumingUntil("Alice in her second main phase") { alice.inOwnMain(TurnStep.PostcombatMain) }
        alice.hold(alice.stepArrow()!!)
        assumingUntil("Bob's turn on Alice's table") { alice.state.turn.active == bob.me }
        val mine = waits.filter { it.active == alice.id && it.offer == GameDevice.Offer.AssumeResolves }
        for ((step, stack) in listOf(TurnStep.PrecombatMain to 1, TurnStep.PrecombatMain to 0, TurnStep.BeginningOfCombat to 0,
                TurnStep.DeclareAttackers to 0, TurnStep.End to 0))
            assertTrue(mine.any { it.step == step && it.stack == stack }, "a wait at $step (Stack $stack): $mine")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // HIS-096, STK-036, TRN-058 (owner 2026-11-06, spec test 2; scenario 12; regression): Alice is in Control and has an
    // Ember Warden; Bob ("Assume resolves" Never) casts an Idle Glimmer and her Warden's trigger goes on top: with Alice
    // holding priority over it (a point Bob's cast began) his phone waits with Assume (Never respects her Control), and the
    // game is her move: the server names her, with a "your move" notification
    @Test fun neverWaitsAtYourOwnTriggerWhileYouHoldPriority() {
        startGame(TestDeck("Wardens", listOf(TestCards.mountain to 20, TestCards.warden to 20)), TestDeck.glimmers(),
            aliceSettings = PlaySettings(mode = ResponseMode.FullControl), bobSettings = PlaySettings().assuming(AssumeResolves.Never),
            deal = { a, b -> "Ember Warden" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        stepsUntil(alice, "Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Ember Warden", this)
        stepsUntil(alice, "the Warden on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Warden").isNotEmpty() && alice.inOwnMain() }
        alice.hold(alice.stepArrow()!!)
        // Bob's turn: his own main phase (Alice passing her priority in his upkeep, as Control stops there)
        waitUntil("Bob's Main 1") {
            if (alice.status == GameDevice.Status.YourMove && alice.engine { device.session.pending.isEmpty() })
                alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let { arrow -> alice.once { alice.tap(arrow) } }
            // Bob plays along until his own turn (in it his phone goes on by itself to his main phase)
            if (bob.state.turn.active != bob.me || bob.status is GameDevice.Status.Stopped || bob.status == GameDevice.Status.CatchUp) bob.playAlong()
            bob.inOwnMain() && quiet()
        }
        val before = ServerSetup.gameNotice(alice.account, game.id)?.first
        bob.playFromHand("Idle Glimmer", this)
        // Bob's own priority with Alice's trigger on top: he lets it be (his main button) when his phone stops there
        waitUntil("Alice holding priority over her Warden's trigger", along = emptyList()) {
            if (bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "Resolve" && bob.state.stack.size == 2) bob.once { bob.tap("Resolve") }
            alice.state.let { s -> s.stack.size == 2 && s.priority.holder == alice.me } && alice.status == GameDevice.Status.YourMove
        }
        waitUntil("Bob's phone waiting for Alice with Assume") { waitsWithAssume(bob) && bob.state.stack.size == 2 }
        keepsWaiting(bob, alice)
        // Her move, and she is told
        waitUntil("the server naming Alice's move") { ServerSetup.head(alice.account, game.id).summary.attentionHolder == alice.id }
        waitUntil("Alice's \"your move\" notification") {
            ServerSetup.gameNotice(alice.account, game.id)?.let { (id, move) -> id != before && move } == true
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096 (owner 2026-11-07, scenario 7b): Alice's Storm Spark kills Bob's Ember Martyr and its dies trigger, Bob's own,
    // goes on the Stack: still a point Alice began (her cast was the last real input), so her setting counts: on Always her
    // phone assumes his pass over his trigger at once although he is in Control; no wait with Assume in her turn
    @Test fun theirTriggerFromYourSpellFollowsYourSetting() {
        startGame(TestDeck.sparks(), TestDeck("Martyrs", listOf(TestCards.mountain to 20, TestCards.martyr to 20)), bobStarts = true,
            aliceSettings = PlaySettings().assuming(AssumeResolves.Always), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { a, b -> "Mountain" in hand(a) && "Storm Spark" in hand(a) && "Ember Martyr" in hand(b) })
        bothOpen()
        bobToHisMain()
        bob.playFromHand("Ember Martyr", this)
        stepsUntil(bob, "the Martyr on Bob's battlefield", along = listOf(alice)) { bob.field("Ember Martyr").isNotEmpty() && inMain(bob) }
        bob.hold(bob.stepArrow()!!)
        waitUntil("Alice's Main 1") { inMain(alice, turn = 2) }
        away(bob, alice)
        val waits = assumeWaits { alice }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && inMain(alice, turn = 2) }
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(androidx.compose.ui.test.hasContentDescription("Choose targets")) }
        alice.battlefieldCard("Ember Martyr", owner = bob).performClick()
        waitUntil("the Martyr dead and its trigger resolved") {
            alice.state.player(bob.me).life == 22 && alice.field("Ember Martyr", owner = bob.me).isEmpty() && inMain(alice, turn = 2)
        }
        assertTrue(waits.isEmpty(), "no wait with Assume: $waits")
        assertTrue(assumedPasses(alice, bob).any { e ->
            e.engine!!.changes.any { it is arcanum.engine.change.Change.StackItemRemoved && it.item.controller == bob.me }
        }, "his pass over his own trigger assumed")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // ---- At a point they began: only their mode

    // HIS-096, STK-036 (owner 2026-11-06, 2026-11-07): Alice is in Control and Bob's phone is on Never: after her own
    // cast, with her priority kept (a point she began), his phone waits for her instead of assuming her pass
    @Test fun neverWaitsForYourPassAfterYourOwnCastInControl() {
        startGame(TestDeck.glimmers(), TestDeck.thirds(), aliceSettings = PlaySettings(mode = ResponseMode.FullControl),
            bobSettings = PlaySettings().assuming(AssumeResolves.Never), deal = { a, _ -> "Idle Glimmer" in hand(a) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        stepsUntil(alice, "Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        val assumed = assumedPasses(bob, alice).size
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("Bob's phone waiting for Alice after her Glimmer") { waitsWithAssume(bob) && bob.state.stack.size == 1 }
        keepsWaiting(bob, alice)
        assertEquals(assumed, assumedPasses(bob, alice).size, "Bob's phone assumed nothing of hers")
        assertEquals(alice.me, alice.state.priority.holder, "Alice holds priority over her own spell")
        assertEquals(GameDevice.Status.YourMove, alice.status)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // STK-036, HIS-096, HIS-109 (owner 2026-11-07, scenario 5; replaces 2026-11-06's branch test here): Bob in Control casts
    // an Idle Glimmer and keeps priority: a point he began, so Alice's phone, though on Always, waits for him (only his
    // mode decides there); his second Glimmer is simply taken: no branch, nothing refused, nothing assumed
    @Test fun aSecondSpellWhileHoldingPriorityIsSimplyTaken() {
        startGame(TestDeck.thirds(), TestDeck.glimmers(), bobStarts = true,
            aliceSettings = PlaySettings().assuming(AssumeResolves.Always), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { _, b -> hand(b).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bobToHisMain()
        val assumed = assumedPasses(alice, bob).size
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Bob's Glimmer on Alice's table, Bob holding priority") {
            alice.state.stack.size == 1 && alice.state.priority.holder == bob.me && quiet()
        }
        waitUntil("Alice's phone waiting for Bob with Assume") { waitsWithAssume(alice) }
        keepsWaiting(alice, bob)
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("both Glimmers on Alice's table") { alice.state.stack.size == 2 && quiet() }
        assertEquals(assumed, assumedPasses(alice, bob).size, "Alice's phone assumed nothing of his")
        assertFalse(alice.told("responded"), "no branch: ${alice.shownTexts}")
        assertFalse(bob.refusalSeen, "nothing refused: ${bob.shownTexts}")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096, STK-026, TRN-058, LOB-073 (owner 2026-11-07, scenario 6): Bob in Normal casts an Idle Glimmer: his phone
    // passes after the cast, inside the cast's event; Alice (Normal, "Assume resolves" Never) then holds priority over his
    // spell: her move, the server names her and she gets a "your move" notification; her phone assumed nothing
    @Test fun theirPassAfterTheirCastMakesItYourMove() {
        startGame(TestDeck.thirds(), TestDeck.glimmers(), bobStarts = true,
            aliceSettings = PlaySettings(mode = ResponseMode.Normal).assuming(AssumeResolves.Never), bobSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { _, b -> "Idle Glimmer" in hand(b) })
        bothOpen()
        bobToHisMain()
        val before = ServerSetup.gameNotice(alice.account, game.id)?.first
        val assumed = assumedPasses(alice, bob).size
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Alice holding priority over Bob's Glimmer") {
            alice.state.stack.size == 1 && alice.state.priority.holder == alice.me && alice.status == GameDevice.Status.YourMove && quiet()
        }
        val cast = alice.shown.held.last { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.player == bob.me } }
        val inputs = cast.engine!!.steps.mapNotNull { it.input }
        assertEquals(Input.PassPriority(bob.me), inputs.last { it !is Input.Answer }, "his pass inside his cast's event: $inputs")
        assertEquals(assumed, assumedPasses(alice, bob).size, "Alice's phone assumed nothing of his")
        waitUntil("the server naming Alice's move") { ServerSetup.head(alice.account, game.id).summary.attentionHolder == alice.id }
        waitUntil("Alice's \"your move\" notification") {
            ServerSetup.gameNotice(alice.account, game.id)?.let { (id, move) -> id != before && move } == true
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096 (owner 2026-11-07, scenario 7a): Bob in Control plays a Mountain and casts an Ember Herald; once it resolved,
    // its enters trigger goes on the Stack with Bob holding priority: still a point he began (his cast was the last real
    // input; Alice's pass and the resolution aren't), so only his mode decides: Alice's phone, on Always, waits for him
    @Test fun theirCreaturesTriggerAfterTheirCastWaitsForThem() {
        startGame(TestDeck.thirds(), TestDeck.thirds(TestCards.herald), bobStarts = true,
            aliceSettings = PlaySettings().assuming(AssumeResolves.Always), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { _, b -> "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bothOpen()
        bobToHisMain()
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain") { bob.field("Mountain").isNotEmpty() && inMain(bob) && quiet() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob holding priority over his Herald") { alice.state.stack.size == 1 && alice.state.priority.holder == bob.me && quiet() }
        waitUntil("Alice's phone waiting for Bob at his Herald") { waitsWithAssume(alice) }
        bob.tap("Resolve")
        waitUntil("the Herald's trigger on the Stack, Bob holding priority") {
            alice.field("Ember Herald", owner = bob.me).isNotEmpty() && alice.state.stack.size == 1 &&
                alice.state.priority.holder == bob.me && quiet()
        }
        waitUntil("Alice's phone waiting for Bob at his trigger") { waitsWithAssume(alice) }
        keepsWaiting(alice, bob)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096, STK-036, TRN-063 (owner 2026-11-07, scenarios 7c and 8): Bob in Control, Alice on Always. At Bob's end step
    // with an empty Stack (his own step, nothing input in it yet: his point; his main 2 arrow taps on to it) Alice's phone
    // waits for him; so at his next upkeep with his Ember Chime's trigger on the Stack and him holding priority. Once he is
    // in Quick and holds nothing he could use there, his end step goes on by itself: no wait with Assume
    @Test fun theirOwnStepsWaitForTheirMode() {
        startGame(TestDeck.thirds(), TestDeck("Chimes", listOf(TestCards.mountain to 20, TestCards.chime to 20)), bobStarts = true,
            aliceSettings = PlaySettings().assuming(AssumeResolves.Always), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { a, b -> "Ember Chime" in hand(b) && "Mountain" in hand(a) })
        bothOpen()
        bobToHisMain()
        bob.playFromHand("Ember Chime", this)
        stepsUntil(bob, "the Chime on Bob's battlefield", along = listOf(alice)) { bob.field("Ember Chime").isNotEmpty() && inMain(bob) && quiet() }
        stepsUntil(bob, "Bob's end step, his priority", along = listOf(alice)) {
            bob.state.turn.step == TurnStep.End && bob.state.priority.holder == bob.me && bob.status == GameDevice.Status.YourMove && quiet()
        }
        waitUntil("Alice's phone waiting for Bob at his end step") {
            waitsWithAssume(alice) && alice.state.turn.step == TurnStep.End && alice.state.priority.holder == bob.me
        }
        keepsWaiting(alice, bob)
        // On to his next upkeep, Alice ending her turn and Bob confirming what her phone assumed of his on the way
        waitUntil("Bob's upkeep with his Chime's trigger on the Stack") {
            // Alice plays a Mountain (her hand stays at seven) and ends her turn
            if (alice.state.turn.active == alice.me) alice.playAlong()
            if (bob.state.turn.number < 3 && bob.engine { device.session.pending.isEmpty() } &&
                (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped))
                bob.stepArrow()?.takeIf { it in STEP_LABELS }?.let { arrow -> bob.once { bob.tap(arrow) } }
            bob.state.let { s -> s.turn.number == 3 && s.turn.step == TurnStep.Upkeep && s.stack.size == 1 && s.priority.holder == bob.me } && quiet()
        }
        waitUntil("Alice's phone waiting for Bob at his trigger") { waitsWithAssume(alice) && alice.state.turn.step == TurnStep.Upkeep }
        keepsWaiting(alice, bob)
        // Bob in Quick: his trigger goes on by itself, and so does his end step
        bob.game!!.settings(bob.shown.settings.copy(mode = ResponseMode.AutoSkip))
        waitUntil("Bob's Main 1 of turn 3", along = listOf(alice)) { inMain(bob, turn = 3) && quiet() }
        bob.tap("To combat")
        waitUntil("Bob's second main phase", along = listOf(alice)) { bob.inOwnMain(TurnStep.PostcombatMain) && quiet() }
        val waits = assumeWaits { alice }
        bob.tap(bob.stepArrow()!!)
        waitUntil("Bob's end step gone by") {
            alice.state.turn.number == 4 || alice.state.let { s -> s.turn.step == TurnStep.End && s.priority.holder == alice.me }
        }
        assertTrue(waits.none { it.step == TurnStep.End }, "his end step went on by itself: $waits")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096, SYS-063, TRN-067 (owner 2026-11-07, scenario 13; regression): Bob is in Quick and holds another Idle Glimmer;
    // his Glimmer puts Alice's Warden trigger on top with him holding priority: his point and his pause, where his Quick
    // stops (judged on his real hand, as his own phone does: it stops for him); Alice's phone waits with Assume, and
    // nothing of his hand shows on her table
    @Test fun theirQuickIsJudgedOnTheirOwnHand() {
        startGame(TestDeck("Wardens", listOf(TestCards.mountain to 20, TestCards.warden to 20)), TestDeck.glimmers(),
            deal = { a, b -> "Ember Warden" in hand(a) && hand(b).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Warden", this)
        waitUntil("the Warden on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Warden").isNotEmpty() && alice.inOwnMain() }
        alice.hold(alice.stepArrow()!!)
        stepsUntil(bob, "Bob's Main 1", along = listOf(alice)) { inMain(bob, turn = 2) && quiet() }
        val assumed = assumedPasses(alice, bob).size
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Bob holding priority over Alice's Warden trigger") {
            bob.state.stack.size == 2 && bob.state.priority.holder == bob.me && bob.status == GameDevice.Status.YourMove && quiet()
        }
        waitUntil("Alice's phone waiting for Bob with Assume") { waitsWithAssume(alice) && alice.state.stack.size == 2 }
        keepsWaiting(alice, bob)
        assertEquals(assumed, assumedPasses(alice, bob).size)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // ---- A resolve by hand looks like the pass a lower mode makes

    /**
     * One side of the comparison: Bob (at his main phase) casts an Idle Glimmer, in Control holding priority and then
     * tapping Resolve, else in Normal (his phone passes by itself), Alice in Normal holding priority over it then, her
     * move: her timeline events since, every notice of the game she got meanwhile, and the playback stops her table
     * showed (with [away], she is away meanwhile and catches up after). Alice lets the Glimmer resolve and Bob is back at
     * his main phase.
     */
    private fun glimmerByBob(control: Boolean, away: Boolean): Triple<List<Pair<Any, String>>, List<Pair<Long, Boolean>>, Int> {
        val mode = if (control) ResponseMode.FullControl else ResponseMode.Normal
        if (bob.shown.settings.mode != mode) {
            bob.game!!.settings(bob.shown.settings.copy(mode = mode))
            waitUntil("Bob's mode on Alice's phone") { alice.engine { device.opponentMode } == mode }
        }
        val before = timelineIds(alice)
        val base = ServerSetup.gameNotice(alice.account, game.id)
        val seen = notices { alice }
        val assumed = assumedPasses(alice, bob).size
        if (away) away(alice, bob)
        bob.playFromHand("Idle Glimmer", this)
        if (control) {
            waitUntil("Bob holding priority over his Glimmer") { bob.state.stack.size == 1 && bob.state.priority.holder == bob.me && bob.current() }
            if (!away) waitUntil("Alice's phone waiting for Bob") { waitsWithAssume(alice) && quiet() }
            bob.tap("Resolve")
        }
        waitUntil("Bob's phone waiting for Alice") { (bob.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves && quiet() }
        val stops = mutableListOf<GameDevice.Status>()
        if (away) {
            alice.back()
            waitUntil("Alice's catch-up") { alice.status == GameDevice.Status.CatchUp }
            everyRun += { (alice.game?.shown?.status as? GameDevice.Status.Stopped)?.let { if (stops.lastOrNull() != it) stops += it } }
            alice.tapToWatch()
        }
        waitUntil("Alice holding priority over Bob's Glimmer, her move") {
            alice.state.stack.size == 1 && alice.state.priority.holder == alice.me && alice.status == GameDevice.Status.YourMove && quiet()
        }
        waitUntil("Alice's \"your move\" notification") { seen.any { (id, move) -> id != base?.first && move } }
        assertEquals(assumed, assumedPasses(alice, bob).size, "Alice's phone assumed nothing of his")
        val timeline = timelineSince(alice, before)
        val notices = seen.filter { it != base }
        alice.tap("Resolve")
        waitUntil("Bob back at his main phase") { inMain(bob) && quiet() }
        return Triple(timeline.map { it.first as Any to it.second }, notices, stops.size)
    }

    // HIS-096, HIS-101, LOB-073 (owner 2026-11-07, scenario 9): Bob in Control casts an Idle Glimmer, keeps priority (Alice's
    // phone, in Normal, waits: his point, his Control) and taps Resolve; later, in Normal, he casts another and his phone
    // passes by itself: Alice's phone shows the same either way: the same timeline events and texts (no "passed" entry),
    // one "your move" notification each time, no playback stop
    @Test fun aResolveByHandLooksLikeAPassByItself() {
        startGame(TestDeck.thirds(), TestDeck.glimmers(), bobStarts = true,
            aliceSettings = PlaySettings(mode = ResponseMode.Normal), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { _, b -> hand(b).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bobToHisMain()
        val (timelineA, noticesA, _) = glimmerByBob(control = true, away = false)
        val (timelineB, noticesB, _) = glimmerByBob(control = false, away = false)
        assertEquals(timelineB, timelineA, "the same timeline")
        assertTrue(timelineA.none { "pass" in it.second }, "no pass entry: $timelineA")
        assertEquals(1, noticesA.count { it.second }, "one \"your move\" notification: $noticesA")
        assertEquals(noticesB.map { it.second }, noticesA.map { it.second }, "the same notifications")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-094, HIS-095, HIS-096, LOB-073 (owner 2026-11-07, scenario 10): as above with Alice away each time, catching up
    // when she comes back: the same playback (no stop), the same timeline, one "your move" notification each time
    @Test fun aResolveByHandCatchesUpLikeAPassByItself() {
        startGame(TestDeck.thirds(), TestDeck.glimmers(), bobStarts = true,
            aliceSettings = PlaySettings(mode = ResponseMode.Normal), bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { _, b -> hand(b).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bobToHisMain()
        val (timelineA, noticesA, stopsA) = glimmerByBob(control = true, away = true)
        val (timelineB, noticesB, stopsB) = glimmerByBob(control = false, away = true)
        assertEquals(timelineB, timelineA, "the same timeline")
        assertEquals(0, stopsA, "no playback stop")
        assertEquals(stopsB, stopsA)
        assertEquals(1, noticesA.count { it.second }, "one \"your move\" notification: $noticesA")
        assertEquals(noticesB.map { it.second }, noticesA.map { it.second }, "the same notifications")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // ---- Late moves branch

    // STK-036, SYS-077, HIS-097, HIS-109 (owner 2026-11-07, scenario 11): Bob casts an Idle Glimmer; Alice, live in Quick
    // with a Glimmer of her own, may respond (her phone stops; his, on Never, waits); his phone goes to Always and assumes
    // her pass at the moment she casts her Glimmer, before that assumption reached her: her response is taken as a branch
    // (his note), never "That wasn't taken"
    @Test fun aResponseAtTheMomentOfTheirAssumptionBranches() {
        startGame(TestDeck.glimmers(), TestDeck.glimmers(), bobStarts = true, bobSettings = PlaySettings().assuming(AssumeResolves.Never),
            deal = { a, b -> "Idle Glimmer" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { inMain(bob) && quiet() }
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Alice may respond to Bob's Glimmer; Bob's phone waits") {
            alice.state.stack.size == 1 && alice.status == GameDevice.Status.YourMove && waitsWithAssume(bob) && quiet()
        }
        // What the server sends Alice now waits on the way (her own sends go out)
        alice.link.holdIncoming()
        bob.game!!.settings(bob.shown.settings.assuming(AssumeResolves.Always))
        waitUntil("Bob's phone assuming her pass, on the server") {
            assumedPasses(bob, alice).isNotEmpty() && bob.engine { device.session.pending.isEmpty() }
        }
        alice.handCard("Idle Glimmer").performClick()
        waitUntil("the Glimmer raised in Alice's hand") { alice.raisedInHand() == "Idle Glimmer" && alice.current() }
        alice.handCard("Idle Glimmer").performClick()
        waitUntil("Alice's Glimmer sent") { alice.engine { device.session.pending.isNotEmpty() } }
        alice.link.releaseIncoming()
        waitUntil("Bob's line branched by Alice's answer") { bob.told("responded — your later actions were taken back") && quiet() }
        assertFalse(alice.refusalSeen, "Alice's Glimmer wasn't refused: ${alice.shownTexts}")
        assertEquals(2, alice.state.stack.size, "both Glimmers on the Stack")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // STK-036, HIS-097, HIS-109 (owner 2026-11-07, scenario 14): Bob in Control casts an Idle Glimmer and keeps priority;
    // Alice's phone waits (his point) and she presses Assume by hand there; Bob then casts a second Glimmer: his line
    // branches from her assumption (her note), nothing refused
    @Test fun aSecondSpellAfterAnAssumeByHandBranches() {
        startGame(TestDeck.thirds(), TestDeck.glimmers(), bobStarts = true, bobSettings = PlaySettings(mode = ResponseMode.FullControl),
            deal = { _, b -> hand(b).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bobToHisMain()
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Alice's phone waiting for Bob at his Glimmer") { waitsWithAssume(alice) && alice.state.stack.size == 1 && quiet() }
        alice.tap("Assume resolves")
        waitUntil("Alice's assumption of his pass on the server") {
            assumedPasses(alice, bob).isNotEmpty() && alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("Bob's phone stopping at it") { bob.status is GameDevice.Status.Stopped }
        bob.playFromHand("Idle Glimmer", this)
        waitUntil("Alice's line branched by Bob's second Glimmer") { alice.told("responded — your later actions were taken back") && quiet() }
        assertFalse(bob.refusalSeen, "nothing refused: ${bob.shownTexts}")
        assertEquals(2, alice.state.stack.size, "both Glimmers on the Stack")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
