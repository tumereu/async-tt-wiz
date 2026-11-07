package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.android.EngineTableProbe
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner 2026-11-07 (catch-up rules, replacing the rewind on return that played by itself): coming back to a game whose
 * events the player had seen were taken back while they were away, by a branch (HIS-097, HIS-106) or by the other
 * player's undo (HIS-108), opens the game exactly as they left it, and nothing moves until they tap: the centre text says
 * what waits ("Bob responded differently · Tap to watch", "Alice undid moves · Tap to watch"), and the tap plays the
 * take-back (a branch step by step with timeline mode's look and the note, then the other player's new events played
 * back; an undo in one backward animation with "<name> undid: <action>"). The same whether the phone heard it in the
 * background, slept, or the notification (or the Games list) starts the app anew. Live, with the game open and in sight,
 * a take-back plays at once.
 */
class RewindOnReturnTest : E2e() {
    @After fun noProbe() {
        EngineShownProbe.listener = null
        EngineTableProbe.applied = null
        EngineTableProbe.rewinding = null
    }

    /**
     * What the returning player's phone showed, from before they left: every board its game published (several may come
     * between two frames, [EngineShownProbe]) and what its table did with them ([EngineTableProbe]: the transitions it
     * applied, with whether it opened on them, and whether it drew a branch's take-back with timeline mode's look); from
     * their return on ([returned]: the index in [boards]) the first centre text the screen showed and whether the game
     * screen loaded anew (the table gone, then back); [tapped]: the index in [boards] when they tapped the centre text.
     */
    private class Seen {
        val boards = java.util.concurrent.CopyOnWriteArrayList<EngineGame.Shown>()
        var returned = -1
        var tapped = -1
        val applied = java.util.concurrent.CopyOnWriteArrayList<Pair<EngineGame.Transition, Boolean>>()
        var rewinding = false
        var watch: String? = null
        var reopened = false

        /** The boards the table showed from the return on: the one already on screen when they came back (the last
         * published before it, unless the game screen loaded anew), then every one published since. A phone that heard
         * the take-back in the background published its held board before the return, and nothing new after it while
         * the table waits. */
        fun sinceReturn(): List<EngineGame.Shown> {
            val onScreen = if (reopened) null else boards.getOrNull(returned - 1)
            return listOfNotNull(onScreen) + boards.drop(returned)
        }
    }

    /** Records what [player]'s game publishes from now on ([Seen.boards]). */
    private fun watching(player: E2ePlayer): Seen {
        val seen = Seen()
        val id = player.id
        EngineShownProbe.listener = { me, shown -> if (me == id) seen.boards += shown }
        EngineTableProbe.applied = { me, transition, opened -> if (me == id) seen.applied += transition to opened }
        EngineTableProbe.rewinding = { me -> if (me == id) seen.rewinding = true }
        return seen
    }

    /** [player] leaves the app. [connected]: the phone keeps its connection meanwhile (the process alive in the
     * background, as a phone a notification reaches often is): the other player's events reach it while it is away;
     * else it sleeps and hears them only when it is back. [other], when they are there, then sees them away. */
    private fun leaves(player: E2ePlayer, other: E2ePlayer?, connected: Boolean) {
        player.away()
        if (other != null) waitUntil("${other.name} seeing ${player.name} away") {
            other.lobby.state.opponentPresence?.presence?.state == PresenceState.OFFLINE
        }
        if (!connected) player.link.cut()
    }

    /** [player] taps the game's notification: the app comes back from the background, or ([coldStart]) starts anew,
     * and opens the game the notification names ([E2ePlayer.openFromNotification]). */
    private fun comesBackByNotification(seen: Seen, player: () -> E2ePlayer, set: (E2ePlayer) -> Unit, coldStart: Boolean) {
        returns(seen, player)
        if (coldStart) set(restart(player())) else player().back()
        player().openFromNotification(game.id, this)
    }

    /** [player] is back: from now on [seen] records what their screen does ([Seen]). */
    private fun returns(seen: Seen, player: () -> E2ePlayer) {
        seen.returned = seen.boards.size
        everyRun += {
            val p = player()
            if (seen.watch == null && p.showsWatch()) seen.watch = p.watchText()
            if (p.lobby.state.detail == null) seen.reopened = true
        }
    }

    /**
     * HIS-106, HIS-108, HIS-035 (owner 2026-11-07): back, [player]'s table stays exactly as they left it ([asLeft]) with
     * the centre text [text], and nothing moves while they wait (no take-back, no playback); then they tap it.
     */
    private fun waitsForTheTap(seen: Seen, player: E2ePlayer, text: String, asLeft: (EngineGame.Shown) -> Boolean) {
        waitUntil("${player.name}'s centre text") { player.showsWatch() && player.current() }
        assertEquals(text, player.watchText())
        repeat(5) { run(skip = java.time.Duration.ofMillis(600)) }
        val since = seen.sinceReturn()
        assertTrue(since.isNotEmpty(), "a board on ${player.name}'s table since the return")
        assertTrue(since.none { it.transition?.undone != null }, "nothing taken back before the tap")
        assertTrue(since.all(asLeft) && since.last().status == GameDevice.Status.CatchUp,
            "the table as left, waiting: ${since.map { it.status }}")
        assertEquals(text, seen.watch, "the first centre text says what waits")
        seen.tapped = seen.boards.size
        player.tapToWatch()
    }

    // ---- (a) A branch while away

    /**
     * Alice casts an Ember Herald while Bob is away, assumes his pass and ends her turn; she leaves the app; Bob comes
     * back and, at his playback's stop at her spell, casts an Idle Glimmer instead: Alice's later actions are taken back.
     * Alice's assumed pass (discarded), which events are Bob's Glimmer cast, and what Alice's phone showed from before
     * she left. [connected]: [leaves].
     */
    private fun bobRespondsWhileAliceIsAway(connected: Boolean): Triple<GameEvent, (GameEvent) -> Boolean, Seen> {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        val seen = watching(alice)
        leaves(alice, null, connected)
        bob.back()
        waitUntil("Bob's playback stopping at Alice's Herald") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && bob.state.stack.isNotEmpty()
        }
        val glimmer = bob.hand("Idle Glimmer").first()
        bob.playFromHand("Idle Glimmer", this)
        fun bobsCast(e: GameEvent) = e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.card == glimmer }
        waitUntil("Bob's Glimmer on the server's main line") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any(::bobsCast) }
        }
        // Heard in the background: the branch reached Alice's phone before she is back, and waits there
        if (connected) waitUntil("the branch held on Alice's phone in the background") { alice.game?.shown?.takeBack?.undo == false }
        return Triple(cast.next, ::bobsCast, seen)
    }

    /** HIS-097, HIS-106: back, Alice's table waited as she left it (her assumed pass shown) with "Bob responded
     * differently · Tap to watch"; her tap took it back to the branch point step by step with timeline mode's look and
     * the note, then played Bob's new events ([theirs]) back, and she is live at the new head. */
    private fun assertBranchShown(seen: Seen, discarded: GameEvent, theirs: (GameEvent) -> Boolean) {
        waitsForTheTap(seen, alice, "${bob.name} responded differently · Tap to watch") { s -> s.held.any { it.id == discarded.id } }
        fun takeBack(): Int = seen.boards.indexOfFirst { s -> s.transition?.let { it.undone?.branch == true && it.backward.isNotEmpty() } == true }
        waitUntil("Alice's table going back to the branch point") { takeBack() >= 0 }
        waitUntil("Alice told") { alice.told("${bob.name} responded") }
        waitUntil("Bob's answer played back on Alice's table, her table live at the new head") {
            !alice.shown.behind && alice.status !is GameDevice.Status.Playing && alice.shown.held.any(theirs)
        }
        val back = seen.boards[takeBack()]
        val t = assertNotNull(back.transition)
        assertTrue(takeBack() >= seen.tapped, "the take-back only after her tap")
        assertTrue(back.takingBack, "the take-back is shown as a branch's (HIS-106)")
        assertTrue(t.steps.isNotEmpty(), "the take-back goes back step by step")
        assertTrue(t.backward.any { it.id == discarded.id }, "her seen line is what goes back")
        assertTrue(seen.applied.any { (applied, opened) -> applied.serial == t.serial && !opened },
            "her table went back from the board she had seen: ${seen.applied.map { (a, o) -> "${a.serial}/${a.undone?.branch}/$o" }}")
        assertTrue(seen.rewinding, "her table drew the take-back's steps with timeline mode's look")
        assertTrue(seen.boards.take(takeBack()).none { s -> s.held.any(theirs) }, "the take-back first, then Bob's new events")
        assertTrue(seen.boards.drop(seen.tapped).none { it.status == GameDevice.Status.CatchUp && !it.takingBack },
            "after the tap never paused for a centre text again: ${seen.boards.map { "${it.status}/${it.takingBack}" }}")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-097, HIS-106, LOB-078 (owner 2026-11-07): Bob's response branched away from Alice's assumed pass while she was
    // away, her phone still connected (the branch reached it in the background); she taps the game's notification: her
    // table, kept as she left it, waits for her tap, then goes back to the branch point with the note and plays Bob's
    // Glimmer back
    @Test fun aBranchHeardInTheBackgroundWaitsForATapFromANotification() {
        val (discarded, theirs, seen) = bobRespondsWhileAliceIsAway(connected = true)
        comesBackByNotification(seen, { alice }, { alice = it }, coldStart = false)
        assertBranchShown(seen, discarded, theirs)
        assertFalse(seen.reopened, "the open game's table stays: the notification doesn't load its screen anew")
    }

    // HIS-097, HIS-106, LOB-078 (owner 2026-11-07): as above, but her phone slept (it hears the branch only when she is
    // back): the same
    @Test fun aBranchWhileAsleepWaitsForATapFromANotification() {
        val (discarded, theirs, seen) = bobRespondsWhileAliceIsAway(connected = false)
        comesBackByNotification(seen, { alice }, { alice = it }, coldStart = false)
        assertBranchShown(seen, discarded, theirs)
        assertFalse(seen.reopened, "the open game's table stays: the notification doesn't load its screen anew")
    }

    // HIS-097, HIS-106, LOB-078 (owner 2026-11-07): as above, but the notification starts the app anew (the process had
    // ended): the table opens where she left it and waits for her tap
    @Test fun aBranchWhileAwayWaitsForATapFromANotificationOnAColdStart() {
        val (discarded, theirs, seen) = bobRespondsWhileAliceIsAway(connected = false)
        comesBackByNotification(seen, { alice }, { alice = it }, coldStart = true)
        waitUntil("Alice's table") { seen.boards.size > seen.returned }
        assertTrue(seen.boards[seen.returned].held.any { it.id == discarded.id }, "opened where she left it")
        assertBranchShown(seen, discarded, theirs)
    }

    // ---- (b) The other player's undo while away

    /**
     * Alice casts an Ember Herald while Bob is away (her phone assuming he lets it resolve) and stays in her main phase;
     * Bob comes back and watches: his playback stops at her spell (he could respond), so he has seen the cast but not
     * answered; with [away] he leaves the app. Alice undoes the Herald: free (all after it is hers, her assumption of
     * Bob's pass not confirmed, HIS-087), it takes back her cast Bob had seen. The cast, and what Bob's phone showed from
     * before Alice's undo. [connected]: [leaves].
     */
    private fun aliceUndoesWhatBobSaw(connected: Boolean, away: Boolean = true): Pair<HeraldCast, Seen> {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        bob.back()
        waitUntil("Bob's playback stopping at Alice's Herald") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && bob.state.stack.isNotEmpty()
        }
        waitUntil("the server told Bob saw the cast") { serverGame()?.localHeads?.get(bob.id) == cast.cast.id }
        val seen = watching(bob)
        if (away) leaves(bob, alice, connected)
        val target = assertNotNull(alice.shown.undoTarget)
        assertEquals(cast.cast.parentId, target, "Undo's point: just before the cast")
        alice.tap("Undo")
        waitUntil("the server back before the cast (a free undo)") { serverGame()?.headId == target }
        waitUntil("the Herald back in Alice's hand") { where(alice.shown.board, cast.herald) == "HAND" }
        assertFalse(alice.refusalSeen)
        // Heard in the background: the undo reached Bob's phone before he is back, and waits there
        if (away && connected) waitUntil("the undo held on Bob's phone in the background") { bob.game?.shown?.takeBack?.undo == true }
        return cast to seen
    }

    /** HIS-108, HIS-076: Bob's table went back from the cast he had seen to before it in one backward animation (never
     * a jump to the board after), with "Alice undid: cast Ember Herald", and is live there. */
    private fun assertUndoShown(seen: Seen, cast: HeraldCast) {
        fun undo(): Int = seen.boards.indexOfFirst { s -> s.transition?.let { it.undone?.branch == false && it.backward.isNotEmpty() } == true }
        waitUntil("Bob's table going back from the cast") { undo() >= 0 }
        waitUntil("Bob's table live before the cast") {
            where(bob.shown.board, cast.herald) == "HAND" && !bob.shown.behind && bob.status !is GameDevice.Status.Playing
        }
        waitUntil("Bob told") { bob.told("${alice.name} undid: cast Ember Herald") }
        assertTrue(seen.boards.take(undo()).any { where(it.board, cast.herald) == "STACK" }, "the table first showed the cast Bob had seen: " +
            seen.boards.map { where(it.board, cast.herald) })
        val t = assertNotNull(seen.boards[undo()].transition)
        assertFalse(t.jump, "an undo animates, never jumps")
        assertTrue(seen.applied.any { (applied, opened) -> applied.serial == t.serial && !opened },
            "his table went back from the board he had seen: ${seen.applied.map { (a, o) -> "${a.serial}/${a.undone}/$o" }}")
        assertTrue(t.backward.any { it.id == cast.cast.id }, "the cast goes back")
        val undone = assertNotNull(t.undone)
        assertEquals("${alice.name} undid: cast Ember Herald", EngineEventText.undid(undone.event, undone.state, bob.id, undone.by, names()))
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    /** HIS-108, HIS-033 (owner 2026-11-07): back, Bob's table waited as he left it (the cast on the Stack: the stop of his
     * that the undo took back doesn't open live) with "Alice undid moves · Tap to watch"; his tap played the undo. */
    private fun assertUndoWaitedThenShown(seen: Seen, cast: HeraldCast) {
        waitsForTheTap(seen, bob, "${alice.name} undid moves · Tap to watch") { where(it.board, cast.herald) == "STACK" }
        assertUndoShown(seen, cast)
        val undo = seen.boards.indexOfFirst { s -> s.transition?.undone?.branch == false }
        assertTrue(undo >= seen.tapped, "the undo only after his tap")
    }

    // HIS-108, HIS-076, LOB-078 (owner 2026-11-07): Alice undid her cast Bob had seen while he was away, his phone still
    // connected (the undo reached it in the background); he taps the game's notification: his table, kept as he left it,
    // waits for his tap, then goes back from the cast with "Alice undid: cast Ember Herald"
    @Test fun anUndoHeardInTheBackgroundWaitsForATapFromANotification() {
        val (cast, seen) = aliceUndoesWhatBobSaw(connected = true)
        comesBackByNotification(seen, { bob }, { bob = it }, coldStart = false)
        assertUndoWaitedThenShown(seen, cast)
        assertFalse(seen.reopened, "the open game's table stays: the notification doesn't load its screen anew")
    }

    // HIS-108, HIS-076, LOB-078 (owner 2026-11-07): as above, but his phone slept (it hears the undo only when he is back)
    @Test fun anUndoWhileAsleepWaitsForATapFromANotification() {
        val (cast, seen) = aliceUndoesWhatBobSaw(connected = false)
        comesBackByNotification(seen, { bob }, { bob = it }, coldStart = false)
        assertUndoWaitedThenShown(seen, cast)
    }

    // HIS-108, HIS-076, LOB-078 (owner 2026-11-07): as above, but the notification starts the app anew: the table opens
    // with the cast where he left it and waits for his tap
    @Test fun anUndoWhileAwayWaitsForATapFromANotificationOnAColdStart() {
        val (cast, seen) = aliceUndoesWhatBobSaw(connected = false)
        comesBackByNotification(seen, { bob }, { bob = it }, coldStart = true)
        waitUntil("Bob's table") { seen.boards.size > seen.returned }
        assertEquals("STACK", where(seen.boards[seen.returned].board, cast.herald), "opened where he left it")
        assertUndoWaitedThenShown(seen, cast)
    }

    // HIS-108, HIS-033 (owner 2026-11-07): as above, but Bob opens the game from the Games list of an app started anew:
    // the same
    @Test fun anUndoWhileAwayWaitsForATapWhenTheGameOpensAgain() {
        val (cast, seen) = aliceUndoesWhatBobSaw(connected = false)
        returns(seen) { bob }
        bob = restart(bob)
        bob.openGame(game, this)
        waitUntil("Bob's table") { seen.boards.size > seen.returned }
        assertEquals("STACK", where(seen.boards[seen.returned].board, cast.herald), "opened where he left it")
        assertUndoWaitedThenShown(seen, cast)
    }

    // HIS-108, HIS-106 (owner 2026-11-07): live, Bob watching at his stop at her cast, Alice's undo plays at once on his
    // table, with no centre text
    @Test fun anUndoWhileWatchingPlaysAtOnce() {
        val (cast, seen) = aliceUndoesWhatBobSaw(connected = true, away = false)
        returns(seen) { bob }
        assertUndoShown(seen, cast)
        assertEquals(null, seen.watch, "no centre text live")
        assertTrue(seen.boards.none { it.takeBack != null }, "nothing held live")
    }
}
