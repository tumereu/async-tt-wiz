package org.asyncttwiz.e2e

import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-10 (TRN-006, TRN-013, TRN-058, HIS-096, TRN-070, STK-036): the move colouring ("Your move" or
 * the other player's, the bars and the field holding the move) never flips back and forth. It comes only from the settled
 * board: the confirmed board with the device's own pending moves and its assumptions, never a board on the way to them.
 * Both phones' every published board's move and every move pill drawn are recorded through live back-and-forth play
 * (casting, passing, the other player in Control, Normal and Quick, each "Assume resolves" setting, the server's answers
 * held back so they arrive in a burst mid-animation); a move that goes A → B → A with nobody pressing anything in
 * between is a flip.
 *
 * The caster's "When they have no mana" or "Always" assuming the other player's pass where the other player's own mode
 * stops has a test of its own: the other phone knows that setting (TRN-059, owner 2026-11-10), so between the cast and the
 * assumption, which arrive as two events, it doesn't show its own player's move.
 */
class MoveColourTest : E2e() {
    @After fun noProbe() { EngineShownProbe.listener = null }

    /** The players' presses so far (every tap, hold or setting change the test makes on either phone). */
    private var presses = 0

    /** One change of the move on a phone: whose move it became, the presses made by then and what showed it; [asked]:
     * while it lasted the phone stood live at a decision of its player's (Your move, the main button theirs to press,
     * nothing of theirs on its way): a real stop on what the phone knew, not a board on the way to one. */
    private data class Mark(val move: String?, val presses: Int, val what: String, var asked: Boolean = false)

    /** Whether [shown] stands live at its player's own decision ([Mark.asked]). */
    private fun asked(shown: EngineGame.Shown) = shown.status == GameDevice.Status.YourMove && !shown.behind && shown.line.size == shown.held.size

    /** Each phone's published boards' moves (changes only), by account id. */
    private val published = mutableMapOf<String, MutableList<Mark>>()
    /** Each phone's move pill as drawn ("Your move" or not), changes only, by account id. */
    private val drawn = mutableMapOf<String, MutableList<Mark>>()

    private fun name(id: String?) = when (id) { alice.id -> "Alice"; bob.id -> "Bob"; null -> "nobody"; else -> id.take(8) }

    private fun describe(shown: EngineGame.Shown): String {
        val s = shown.engine
        val top = s.stack.lastOrNull()?.let { cardName(s, it) }
        return "v${shown.version} ${shown.status::class.simpleName} turn ${s.turn.number} ${s.turn.step} priority ${name(s.priority.holder?.value)}" +
            " stack ${s.stack.size}${top?.let { " ($it)" } ?: ""} tip ${shown.tip.take(8)} local ${shown.localHead.take(8)}" +
            (if (shown.behind) " behind" else "") + (if (shown.line.size > shown.held.size) " pending ${shown.line.size - shown.held.size}" else "")
    }

    /** From now on every board each phone publishes, and every move pill each draws, is recorded. */
    private fun recordMoves() {
        EngineShownProbe.listener = { me, shown ->
            val list = published.getOrPut(me) { mutableListOf() }
            val move = shown.board.board.moveHolder
            if (list.lastOrNull()?.move != move) list += Mark(move, presses, describe(shown))
            if (asked(shown)) list.last().asked = true
        }
        everyRun += {
            for (player in listOf(alice, bob)) {
                val shown = player.game?.shown ?: continue
                val own = player.showsText("Your move")
                val pill = if (own) player.id else if (player.showsText("Waiting · ", substring = true)) player.other.id else null
                val list = drawn.getOrPut(player.id) { mutableListOf() }
                if (pill != null && list.lastOrNull()?.move != pill) list += Mark(pill, presses, describe(shown))
                if (pill != null && asked(shown)) list.last().asked = true
            }
        }
    }

    private val E2ePlayer.other: E2ePlayer get() = if (this === alice) bob else alice

    /**
     * The flips in [marks]: a move B between two of A's with no press while B lasted, on boards that never stood live at
     * a decision of the phone's player ([Mark.asked]: there the phone really stopped for them on what it knew, the move
     * following its main button; the other phone's events already on their way may then take it on, a race of the two
     * phones, not a board on the way).
     */
    private fun flips(marks: List<Mark>): List<String> = marks.windowed(3).filter { (a, b, c) ->
        a.move == c.move && b.move != a.move && c.presses == b.presses && !b.asked
    }.map { (a, b, c) -> "${name(a.move)} → ${name(b.move)} → ${name(c.move)}: [${a.what}] → [${b.what}] → [${c.what}]" }

    private fun assertNoFlips() {
        val found = listOf(alice, bob).flatMap { p ->
            flips(published[p.id].orEmpty()).map { "${p.name}'s board: $it" } + flips(drawn[p.id].orEmpty()).map { "${p.name}'s pill: $it" }
        }
        val history = listOf(alice, bob).joinToString("\n") { p ->
            "${p.name}'s boards: " + published[p.id].orEmpty().joinToString(" | ") { "${name(it.move)}@${it.presses}" } +
                "\n${p.name}'s pill: " + drawn[p.id].orEmpty().joinToString(" | ") { "${name(it.move)}@${it.presses}" }
        }
        assertTrue(found.isEmpty(), "the move flipped with nothing pressed:\n${found.joinToString("\n")}\n$history")
    }

    // ---- Presses (each counted)

    private fun E2ePlayer.press(label: String) { presses++; tap(label) }
    private fun E2ePlayer.pressHold(label: String) { presses++; hold(label) }
    private fun E2ePlayer.cast(card: String) { presses++; playFromHand(card, this@MoveColourTest) }
    private fun E2ePlayer.choose(settings: PlaySettings) { presses++; game!!.settings(settings) }

    /** [player]'s response mode set to [mode], and the other phone knowing it. */
    private fun mode(player: E2ePlayer, mode: ResponseMode) {
        if (player.shown.settings.mode == mode) return
        player.choose(player.shown.settings.copy(mode = mode))
        waitUntil("${player.other.name}'s phone knowing ${player.name}'s ${mode::class.simpleName}") {
            player.other.engine { device.opponentMode } == mode && player.shown.settings.mode == mode
        }
    }

    /** [player]'s "Assume resolves" set to [assume]. */
    private fun assume(player: E2ePlayer, assume: AssumeResolves) {
        if (player.shown.settings.assume == assume) return
        player.choose(player.shown.settings.assuming(assume))
        waitUntil("${player.name}'s Assume ${assume::class.simpleName}") { player.shown.settings.assume == assume }
    }

    /** The main button's taps that go on one point (never End turn's). */
    private val taps = STEP_LABELS - "End turn" - "To combat" - "End step"

    /**
     * Both players answer what their tables ask until [done]: a stop of playback or a priority with something on the Stack
     * is answered with the main button (Resolve, Pass, Next), a catch-up's centre text is tapped; [respond] (once) casts
     * instead, the first time that player holds priority over the other's spell. Nobody presses Assume: a wait goes on
     * when the player waited for answers.
     */
    private fun answerUntil(what: String, respond: Pair<E2ePlayer, String>? = null, done: () -> Boolean) {
        var responded = respond == null
        waitUntil(what) {
            if (!done()) for (player in listOf(alice, bob)) {
                if (!player.foreground || player.engine { device.session.pending.isNotEmpty() }) continue
                when (player.status) {
                    GameDevice.Status.CatchUp -> player.once { presses++; player.tapToWatch() }
                    is GameDevice.Status.Stopped -> player.stepArrow()?.takeIf { it in taps }?.let { arrow -> player.once { player.press(arrow) } }
                    GameDevice.Status.YourMove -> if (quiet() && player.state.stack.isNotEmpty()) {
                        val s = player.state
                        val theirs = s.stack.lastOrNull()?.let { s.obj(it)?.controller ?: s.stackItems[it]?.controller } == player.other.me
                        if (!responded && respond?.first === player && theirs && player.hand(respond.second).isNotEmpty())
                            player.once { responded = true; player.cast(respond.second) }
                        else player.stepArrow()?.takeIf { it in taps }?.let { arrow -> player.once { player.press(arrow) } }
                    }
                    else -> {}
                }
            }
            done()
        }
    }

    /** The Idle Glimmers in [player]'s graveyard, as their table shows it. */
    private fun graveGlimmers(player: E2ePlayer) = player.state.let { s -> player.named(s.player(player.me).graveyard, "Idle Glimmer", s).size }

    private fun inMain(player: E2ePlayer, turn: Int = 1) = player.inOwnMain() && player.state.turn.number >= turn && quiet() &&
        player.current() && player.other.current()

    // TRN-006, TRN-013, TRN-058, HIS-096, TRN-070, STK-036: Alice casts Idle Glimmers in her main phase against Bob in
    // Control (he responds with one of his own once), Normal and Quick, with her "Assume resolves" on Never and Normal; then
    // her Ember Herald: Bob's pass held on its way while she presses Assume over it (her board goes on to her trigger before
    // her phone's own steps), her phone's server answers held back meanwhile and let through in a burst as the next board
    // animates; Bob answers the trigger. Neither phone's move flips.
    @Test fun theMoveNeverFlipsAtAlicesSpells() {
        startGame(TestDeck("Glimmers and Heralds", listOf(TestCards.mountain to 14, TestCards.glimmer to 16, TestCards.herald to 10)),
            TestDeck.glimmers(), deal = { a, b ->
                "Mountain" in hand(a) && "Ember Herald" in hand(a) && hand(a).count { it == "Idle Glimmer" } >= 4 &&
                    hand(b).count { it == "Idle Glimmer" } >= 2
            })
        bothOpen()
        recordMoves()
        alice.press("Keep")
        bob.press("Keep")
        answerUntil("Alice's Main 1") { inMain(alice) }
        alice.cast("Mountain")
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && inMain(alice) }
        val rounds = listOf(
            Triple(ResponseMode.FullControl, AssumeResolves.Normal, true),
            Triple(ResponseMode.FullControl, AssumeResolves.Never, false),
            Triple(ResponseMode.Normal, AssumeResolves.Never, false),
            Triple(ResponseMode.Normal, AssumeResolves.Normal, false),
            Triple(ResponseMode.AutoSkip, AssumeResolves.Never, false),
            Triple(ResponseMode.AutoSkip, AssumeResolves.Normal, false))
        for ((bobMode, aliceAssume, bobResponds) in rounds) {
            mode(bob, bobMode)
            assume(alice, aliceAssume)
            val resolved = graveGlimmers(alice)
            alice.cast("Idle Glimmer")
            answerUntil("Alice's Glimmer resolved (Bob ${bobMode::class.simpleName}, Alice Assume ${aliceAssume::class.simpleName})",
                respond = if (bobResponds) bob to "Idle Glimmer" else null) {
                alice.state.stack.isEmpty() && graveGlimmers(alice) > resolved && inMain(alice)
            }
        }
        // The Herald with Bob in Control: her phone waits for him (her Normal)
        mode(bob, ResponseMode.FullControl)
        assume(alice, AssumeResolves.Normal)
        alice.cast("Ember Herald")
        waitUntil("Bob holding priority over the Herald, Alice waiting for him") {
            bob.status == GameDevice.Status.YourMove && bob.state.stack.size == 1 && bob.stepArrow() == "Resolve" && bob.current() &&
                alice.status is GameDevice.Status.Waiting && alice.showsControl("Assume resolves") && alice.current()
        }
        // His pass on its way as she presses Assume; her phone hears nothing back meanwhile
        bob.link.holdOutgoing()
        alice.link.holdAnswers("/v1/socket")
        bob.press("Resolve")
        alice.press("Assume resolves")
        frameByFrame { repeat(20) { frame() } }
        bob.link.releaseOutgoing()
        waitUntil("Bob's pass on the server") { bob.engine { device.session.pending.isEmpty() } }
        // Alice's phone gets it all at once, the board animating meanwhile
        frameByFrame {
            alice.link.releaseAnswers()
            repeat(40) { frame() }
        }
        answerUntil("the Herald's token, Alice in her Main 1") {
            alice.state.battlefield.any { alice.state.obj(it)?.token == true } && inMain(alice)
        }
        repeat(5) { run(skip = java.time.Duration.ofMillis(300)) }
        assertNoFlips()
    }

    // TRN-006, TRN-013, TRN-058, HIS-096, STK-036: Alice (only Mountains: nothing to respond with) ends her turn; in Bob's
    // turn he casts Idle Glimmers against Alice in Control and Normal with his "Assume resolves" on Never and Normal, his
    // phone's answers held back during one and Alice's pass held on its way during another, and against her Quick (it
    // passes for her) with When they have no mana and Always. Neither phone's move flips.
    @Test fun theMoveNeverFlipsAtBobsSpells() {
        startGame(TestDeck("Mountains", listOf(TestCards.mountain to 40)), TestDeck.glimmers(), deal = { _, b ->
            hand(b).count { it == "Idle Glimmer" } >= 6
        })
        bothOpen()
        recordMoves()
        alice.press("Keep")
        bob.press("Keep")
        answerUntil("Alice's Main 1") { inMain(alice) }
        alice.pressHold(alice.stepArrow()!!)
        answerUntil("Bob's Main 1") { inMain(bob, turn = 2) }
        val rounds = listOf(
            Triple(ResponseMode.FullControl, AssumeResolves.Normal, "held answers"),
            Triple(ResponseMode.FullControl, AssumeResolves.Never, ""),
            Triple(ResponseMode.Normal, AssumeResolves.Never, "held pass"),
            Triple(ResponseMode.Normal, AssumeResolves.Normal, ""),
            Triple(ResponseMode.AutoSkip, AssumeResolves.WhenNoMana, ""),
            Triple(ResponseMode.AutoSkip, AssumeResolves.Always, ""))
        for ((aliceMode, bobAssume, race) in rounds) {
            mode(alice, aliceMode)
            assume(bob, bobAssume)
            if (race == "held answers") bob.link.holdAnswers("/v1/socket")
            if (race == "held pass") alice.link.holdOutgoing()
            val resolved = graveGlimmers(bob)
            bob.cast("Idle Glimmer")
            if (race == "held answers") {
                // Alice (Control) answers on her phone while Bob's phone hears nothing back
                answerUntil("Alice's answer out") { alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() } }
                frameByFrame { bob.link.releaseAnswers(); repeat(40) { frame() } }
            }
            if (race == "held pass") {
                waitUntil("Alice holding priority over Bob's Glimmer") { alice.state.stack.size == 1 && alice.status == GameDevice.Status.YourMove && alice.current() }
                alice.press(alice.stepArrow()!!)
                frameByFrame { repeat(20) { frame() } }
                alice.link.releaseOutgoing()
            }
            answerUntil("Bob's Glimmer resolved (Alice ${aliceMode::class.simpleName}, Bob Assume ${bobAssume::class.simpleName})") {
                bob.state.stack.isEmpty() && graveGlimmers(bob) > resolved && inMain(bob, turn = 2)
            }
        }
        repeat(5) { run(skip = java.time.Duration.ofMillis(300)) }
        assertNoFlips()
    }

    // TRN-006, TRN-013, TRN-058, TRN-059, HIS-096 (owner 2026-11-10: the Assume setting is shared): Alice casts Idle
    // Glimmers with her "Assume resolves" on When they have no mana (Bob has no mana source) and Always against Bob in
    // Normal, Control and Quick (his Glimmers let him respond, so each of his modes stops at her spell): her phone assumes
    // his pass at once, and his phone, knowing her setting, never shows his move between her cast and that assumption
    @Test fun theMoveNeverFlipsAtAnAssumptionByTheCastersSetting() {
        startGame(TestDeck("Glimmers only", listOf(TestCards.glimmer to 40)), TestDeck.glimmers(), deal = { _, b ->
            hand(b).count { it == "Idle Glimmer" } >= 1
        })
        bothOpen()
        recordMoves()
        alice.press("Keep")
        bob.press("Keep")
        answerUntil("Alice's Main 1") { inMain(alice) }
        val rounds = listOf(
            ResponseMode.Normal to AssumeResolves.WhenNoMana,
            ResponseMode.Normal to AssumeResolves.Always,
            ResponseMode.FullControl to AssumeResolves.WhenNoMana,
            ResponseMode.FullControl to AssumeResolves.Always,
            ResponseMode.AutoSkip to AssumeResolves.Always)
        for ((bobMode, aliceAssume) in rounds) {
            mode(bob, bobMode)
            assume(alice, aliceAssume)
            val resolved = graveGlimmers(alice)
            alice.cast("Idle Glimmer")
            answerUntil("Alice's Glimmer resolved (Bob ${bobMode::class.simpleName}, Alice Assume ${aliceAssume::class.simpleName})") {
                alice.state.stack.isEmpty() && graveGlimmers(alice) > resolved && inMain(alice)
            }
        }
        repeat(5) { run(skip = java.time.Duration.ofMillis(300)) }
        assertNoFlips()
    }
}
