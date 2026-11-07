package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.android.TableBoard
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Ways games go that several tests start from. */

/** Where card [id] is on [play]'s board ("STACK" while its spell is there), or null when the board hides it. */
internal fun where(play: TableBoard, id: String): String? {
    val board = play.board
    if (board.stackEffects.any { it.sourceId == id && it.item?.kind == MtgStackKind.Spell }) return "STACK"
    val card = board.cards.firstOrNull { it.id == id } ?: return null
    return board.containers.single { it.id == card.containerId }.zone.name
}

/**
 * [player] leaves the app ([E2ePlayer.away]), the other phone hears it, and the phone then sleeps: it hears nothing more
 * until [E2ePlayer.back] (its network cut; the server's events wait for it, as for a phone the system froze).
 */
internal fun E2e.away(player: E2ePlayer, other: E2ePlayer) {
    player.away()
    waitUntil("${other.name} seeing ${player.name} away") { other.lobby.state.opponentPresence?.presence?.state == PresenceState.OFFLINE }
    player.link.cut()
}

/** Both keep (each through their table's Keep); with [bobAway] Bob then leaves and Alice goes on alone to her Main 1. */
internal fun E2e.bothKeep(bobAway: Boolean) {
    alice.tap("Keep")
    bob.tap("Keep")
    waitUntil("Bob's own keep on Alice's table") {
        alice.state.turn.number >= 1 && alice.shown.held.any { it.author == bob.id && it.assumed == null }
    }
    if (bobAway) away(bob, alice)
    waitUntil("Alice's Main 1", along = if (bobAway) emptyList() else listOf(bob)) { alice.inOwnMain() }
}

/** Alice's Ember Herald cast: the Herald's id, its cast event and the event after it on the main line. */
internal class HeraldCast(val herald: String, val cast: GameEvent, val line: List<GameEvent>) {
    val next: GameEvent get() = line[line.indexOf(cast) + 1]
}

/**
 * The interim simulations' Herald game (EngineGameFixture(heralds = true): Alice's Mountains, Ember Heralds and Storm
 * Sparks): Bob holds an Idle Glimmer (so his playback, Auto-skip, stops at Alice's spell and her trigger: he could
 * respond, HIS-095), both keep, Bob goes away; Alice plays a Mountain and casts an Ember Herald, her phone assuming Bob
 * lets it resolve ("Always assume resolves" on, HIS-096); the Herald and its token reach her battlefield. With [endTurn]
 * she then ends her turn. With [bobCanRespond] false Bob's deck is Mountains, Ember Cubs and Storm Sparks instead (in
 * Alice's first turn he has no mana, so nothing he could respond with); [bobSettings]: his play defaults.
 */
internal fun E2e.aliceCastsAHeraldWhileBobIsAway(endTurn: Boolean = true, bobCanRespond: Boolean = true,
    bobSettings: PlaySettings? = null): HeraldCast {
    startGame(TestDeck.thirds(TestCards.herald), if (bobCanRespond) TestDeck.glimmers() else TestDeck.thirds(),
        aliceSettings = PlaySettings(alwaysAssumeResolves = true), bobSettings = bobSettings,
        deal = { a, b -> "Mountain" in hand(a) && "Ember Herald" in hand(a) && (!bobCanRespond || "Idle Glimmer" in hand(b)) })
    bothOpen()
    bothKeep(bobAway = true)
    alice.playFromHand("Mountain", this)
    waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
    val herald = atw(alice.hand("Ember Herald").first())
    alice.playFromHand("Ember Herald", this)
    waitUntil("the Herald and its token on Alice's battlefield") {
        herald in alice.field("Ember Herald").map(::atw) && alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain()
    }
    if (endTurn) {
        alice.hold("To combat")
        waitUntil("Alice's turn over") { alice.state.turn.active == bob.me }
    }
    waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
    val line = alice.engine { device.session.events }
    val cast = assertNotNull(line.firstOrNull { e -> e.engine?.steps?.any { (it.input as? Input.Cast)?.card?.let(::atw) == herald } == true },
        "the cast on the line: ${line.map { e -> e.engine?.steps?.mapNotNull { it.input } }}")
    return HeraldCast(herald, cast, line).also {
        assertEquals(alice.me, it.next.assumed?.assumedBy, "the next event is Alice's assumed pass for Bob")
        assertEquals(Input.PassPriority(bob.me), it.next.assumed?.input)
    }
}

/** From now on, every event [player]'s table shows (each transition's events, played back or live), in order. */
internal fun E2e.recordShown(player: () -> E2ePlayer): List<GameEvent> {
    val events = mutableListOf<GameEvent>()
    var serial = -1L
    everyRun += {
        player().game?.shown?.transition?.takeIf { it.serial != serial }?.let { t -> serial = t.serial; events += t.forward }
    }
    return events
}

/** The players' display names by account id (event texts). */
internal fun E2e.names(): Map<String, String> = mapOf(alice.id to alice.name, bob.id to bob.name)
