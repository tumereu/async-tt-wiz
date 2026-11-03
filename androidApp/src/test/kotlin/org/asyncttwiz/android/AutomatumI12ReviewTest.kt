package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adversarial review of Automatum phases I1 and I2a (report `arcanum-i12-review.md`, outside the repo): each test pins a
 * problem found (ignored while it isn't fixed). Invented cards parsed from their oracle text as on a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumI12ReviewTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String = "", pt: Pair<String, String>? = null) =
        Invented(DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text,
            power = pt?.first.orEmpty(), toughness = pt?.second.orEmpty()),
            OracleCard(name, cost, type, text, pt?.first, pt?.second))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val verdict = invented("verdict", "Azure Verdict", "{W}{U}", "Instant", "Azure Verdict deals 3 damage to any target.")
    private val recall = invented("recall", "Grave Recall", "{B}", "Sorcery", "Return target creature card from your graveyard to your hand.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain")
    private val plains = invented("plains", "Plains", null, "Basic Land — Plains")
    private val swamp = invented("swamp", "Swamp", null, "Basic Land — Swamp")
    private val ford = invented("ford", "Glacier Ford", null, "Land — Plains Island", "({T}: Add {W} or {U}.)")
    private val hound = invented("hound", "Grave Hound", "{1}{B}", "Creature — Dog", "", "2" to "2")
    private val basilisk = invented("basilisk", "Hidden Basilisk", "{3}{G}", "Creature — Basilisk", "", "4" to "4")
    private val invented = listOf(spark, verdict, recall, mountain, plains, swamp, ford, hound, basilisk)

    private val hand = f.initial.board.containers.single { it.id == f.id("a/HAND") }.cards
    private val bobHand = f.initial.board.containers.single { it.id == f.id("b/HAND") }.cards
    private val field = f.id("a/BATTLEFIELD")
    private val lands = f.initial.board.cards.filter { it.containerId == field }.take(2).map { it.id }
    private val graveyard = f.initial.board.containers.single { it.id == f.id("a/GRAVEYARD") }.cards

    @Before fun definitions() {
        // The face-down card's definition is the device's too (I-D27: clients may hold hidden identities)
        val byDigest = invented.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /** Alice's main phase with Automatum on: [inHand] as her first hand cards, [onField] as her first battlefield cards
     * (untapped, no corner), [cards] changing any others. */
    private fun board(inHand: List<Invented>, onField: List<Invented>, cards: (PlayCard) -> PlayCard = { it }): PlayState {
        val initial = f.initial
        val fieldCards = initial.board.cards.filter { it.containerId == field }.map { it.id }
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = MtgPriority(f.a), cards = initial.board.cards.map { card ->
                val h = hand.indexOf(card.id).takeIf { it in inHand.indices }?.let(inHand::get)
                val b = fieldCards.indexOf(card.id).takeIf { it in onField.indices }?.let(onField::get)
                when {
                    h != null -> card.copy(definition = h.submitted.digest, faceId = h.face.faceId)
                    b != null -> card.copy(definition = b.submitted.digest, faceId = b.face.faceId, tapped = false, corner = "")
                    else -> cards(card)
                }
            }))
    }

    private val sent = mutableListOf<GameAction.PlayBatch>()
    private var message: String? = null
    private fun controller(current: () -> PlayState?) =
        // These reviews check the open cast at its payment: the mana section, Autotap off (I-D47)
        CastController(f.a, current, { batch, _ -> sent += batch; true }, { emptyList() }, { message = it }).also { it.autotap = false }
    private fun manaGroups(decision: Decision.ManaPayment) =
        decision.options.filterIsInstance<ManaUse.Ability>().groupBy { it.source }.values.map { it.toList<ManaUse>() }

    // i12review #1 (I-D24, SYS-060, STK-025, D32): the other player (or another device) switches Automatum off while
    // Alice's cast is open: the cast is dropped with a message, never sent as an automated batch (the server refuses
    // those while Automatum is off, SYS-057)
    @Test fun switchingOffUnderAnOpenCastDropsIt() {
        var live: PlayState? = board(listOf(mountain, spark), listOf(mountain))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        val off = live!!.copy(board = live!!.board.copy(automatum = false))
        live = off
        cast.follow()
        assertNull(cast.flow, "Automatum is off: the cast is dropped")
        assertEquals(CastController.SWITCHED_OFF, message)
        assertFalse(cast.cast())
        assertTrue(sent.isEmpty())
    }

    // i12review #2 (§2 "switching Automatum off is always allowed and is the way out of anything stuck", TRN-054,
    // STK-025): with a cast open, the player's own switch drops the cast (nothing was sent of it) and switches off
    @Test fun ownSwitchOffDuringAnOpenCastDropsTheCastAndSwitches() {
        val play = board(listOf(mountain, spark), listOf(mountain))
        val gameplay = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> gameplay += action; true },
            automatedBatch = { batch, _ -> sent += batch; true }).also { it.cast.autotap = false }
        assertTrue(toolkit.cast.start(hand[1]))
        assertTrue(toolkit.setAutomatum(false), "switching off: ${toolkit.error}")
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(false))), gameplay)
        assertNull(toolkit.cast.flow)
    }

    // i12review #3 (I-D27, STK-005): Alice targets Bob's face-down creature; the cast panel's answered Target
    // section named it by its real card ("Hidden Basilisk") in I2a; fixed by I2b (CastController.shown), kept as a check
    @Test fun faceDownTargetIsNotNamedInTheCastPanel() {
        val hidden = f.source(f.b, "2/3")
        val play = board(listOf(mountain, spark), listOf(mountain)) { card ->
            if (card.id == hidden.id) card.copy(definition = basilisk.submitted.digest, faceId = basilisk.face.faceId,
                covered = true, explicitlyCovered = true, corner = "") else card
        }
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true }, automatedBatch = { batch, _ -> sent += batch; true }).also { it.cast.autotap = false }
        compose.setContent {
            MaterialTheme { NoRipples { Box(Modifier.fillMaxSize()) {
                TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
            } } }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(toolkit.cast.start(hand[1])) }
        compose.waitForIdle()
        assertTrue(hidden.id in toolkit.cast.glow(play), "the face-down 2/2 is a legal target")
        val item = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
            .single { it.cardId == hidden.id && it.zone == PlayZone.BATTLEFIELD }
        compose.runOnIdle { toolkit.open(TableIntent.Actions(item.id, null), item) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onAllNodesWithText("Hidden Basilisk", substring = true).assertCountEquals(0)
    }

    // i12review #7 (STK-024 "tapping a chip taps or untaps it", I-D6): the tapped sources are a set whose colours are
    // worked out together: Plains + Glacier Ford ({W} or {U}) re-tapped in either order pay {W}{U}
    @Test fun manaTogglingPaysWhateverOrderTheSourcesAreTapped() {
        var live: PlayState? = board(listOf(mountain, verdict), listOf(plains, ford))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        val payment = assertNotNull(cast.flow?.pending as? Decision.ManaPayment)
        assertTrue(cast.paid, "the suggestion pays")
        val groups = manaGroups(payment)
        assertEquals(2, groups.size)
        val (dual, single) = groups.partition { it.size > 1 }.let { it.first.single() to it.second.single() }
        cast.toggleMana(dual); cast.toggleMana(single)
        assertTrue(cast.mana.isEmpty(), "both untapped")
        cast.toggleMana(dual); cast.toggleMana(single)
        assertTrue(cast.paid, "both tapped again pay {W}{U}: ${cast.mana}")
    }

    // i12review #8 (SYS-060, TRN-055's dialog "Removes every effect, ledger and record"): Clear Automatum data removes
    // card and player ledgers too, and its button is enabled exactly when there is something to remove
    @Test fun clearAutomatumDataRemovesLedgers() {
        val play = board(emptyList(), listOf(mountain)) { it }.let { p ->
            p.copy(board = p.board.copy(priority = null, cards = p.board.cards.map { if (it.id == lands[0]) it.copy(ledger = "{\"memory\":{}}") else it },
                players = p.board.players.map { if (it.accountId == f.a) it.copy(ledger = "{\"ledger\":{}}") else it }))
        }
        val change = GameplayRules.change(play, f.a, GameCommand(f.id("clear"), GameScope(play.gameId, playId = play.id),
            GameAction.Play(PlayAction.Mtg(MtgAction.ClearRecords)), f.id("client"), "1"))
        val after = assertNotNull(change.state)
        assertEquals("", after.board.cards.single { it.id == lands[0] }.ledger)
        assertEquals("", after.board.players.single { it.accountId == f.a }.ledger)
        assertTrue(Automatum.recorded(play.board), "only ledgers: the button is enabled")
        assertFalse(Automatum.recorded(after.board), "nothing left: the button is greyed")
    }


    // i12review #10 (STK-025, TableToolkitController.enabled): while the player can't act (the personal timeline is
    // open, the connection is down) Cast sends nothing and says why; the cast stays open and follows no reviewed board
    @Test fun castSendsNothingWhileActionsAreDisabled() {
        var live: PlayState? = board(listOf(mountain, spark), listOf(mountain))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        // Reviewing the timeline (or disconnected): the toolkit's current() gives no board to act on
        live = null
        val paying = cast.flow
        assertFalse(cast.cast())
        assertTrue(sent.isEmpty(), "nothing is sent while actions are disabled")
        assertEquals(CastController.CANT_ACT, message)
        cast.follow()
        assertTrue(cast.flow === paying, "the cast stays as it was")
        // Back at the live board, Cast goes out
        live = board(listOf(mountain, spark), listOf(mountain))
        assertTrue(cast.cast())
        assertEquals(1, sent.size)
    }

    // i12review #13 (STK-024, STK-025 "keeping the choices that still fit"): the player's own tapping in the mana section
    // stays when the board changes (Bob's life here) and its sources are still there
    @Test fun ownManaTappingSurvivesAnUnrelatedBoardChange() {
        var live: PlayState? = board(listOf(mountain, spark), listOf(mountain, mountain))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        val payment = assertNotNull(cast.flow?.pending as? Decision.ManaPayment)
        val (suggested, other) = manaGroups(payment).partition { g -> g.any { it in cast.mana } }.let { it.first.single() to it.second.single() }
        cast.toggleMana(suggested); cast.toggleMana(other)
        val chosen = cast.mana
        assertTrue(cast.paid && chosen.single() in other)
        val changed = live!!.copy(board = live!!.board.copy(players = live!!.board.players.map {
            if (it.accountId == f.b) it.copy(life = "19") else it }))
        live = changed
        cast.follow()
        assertEquals(chosen, cast.mana, "the player's own tapping is kept")
        assertTrue(cast.paid)
        // A source gone (tapped by hand meanwhile): the suggestion again
        val source = (chosen.single() as ManaUse.Ability).source
        val tapped = changed.copy(board = changed.board.copy(cards = changed.board.cards.map {
            if (it.id == cast.atwCard(source)) it.copy(tapped = true) else it }))
        live = tapped
        cast.follow()
        assertTrue(cast.mana.none { it in chosen } && cast.paid, "${cast.mana}")
    }

    // i12review #14 (I-D6 "targets are chosen on the table", STK-023): a target card in a Graveyard has no table item of
    // its own; its pile glows, a tap opens it as its card panel (instead of "That isn't a legal target."), the legal
    // cards in it glow and a tap picks one; the panel closes again once the targets are chosen
    @Test fun everyLegalTargetCanBePickedOnTheTable() {
        assertTrue(graveyard.size >= 2)
        val play = board(listOf(mountain, recall), listOf(swamp)) { card ->
            if (card.id in graveyard) card.copy(definition = hound.submitted.digest, faceId = hound.face.faceId) else card
        }
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true }, automatedBatch = { batch, _ -> sent += batch; true }).also { it.cast.autotap = false }
        assertTrue(toolkit.cast.start(hand[1]))
        val targets = assertNotNull(toolkit.cast.flow?.pending as? Decision.Targets)
        assertTrue(targets.options.size >= 2, "both Grave Hounds are legal targets")
        val pile = f.id("a/GRAVEYARD")
        val glow = toolkit.cast.glow(play)
        assertTrue(pile in glow, "the Graveyard holding them glows")
        val items = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
        val pileItem = items.single { it.id == pile }
        toolkit.open(TableIntent.Actions(pileItem.id, null), pileItem)
        assertNull(toolkit.error)
        assertEquals(pile, toolkit.layoutState.value.localPileId, "the pile opens as its card panel")
        assertTrue(toolkit.cast.targeting, "still choosing targets")
        val panel = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false, ui = toolkit.layoutState.value).items
            .filter { it.viewport.startsWith("cardPanel/") }
        val unreachable = glow.filter { id -> id != pile && !id.startsWith("life/") &&
            items.none { it.cardId == id || it.sourceId == id || it.id == id } && panel.none { it.cardId == id } }
        assertTrue(unreachable.isEmpty(), "targets without a table item to tap: $unreachable")
        assertEquals(graveyard.toSet(), panel.mapNotNull { it.cardId }.toSet(), "each Grave Hound its own, not grouped as ×2")
        AutomatumBoards.compute(play, f.a)
        compose.setContent {
            MaterialTheme { NoRipples { Box(Modifier.fillMaxSize()) {
                TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
            } } }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose targets").assertExists()
        captureShot("$SCREENSHOTS/automatum/cast-graveyard-target.png",
            "Casting the invented Grave Recall, Alice tapped her glowing Graveyard: it opens as its card panel with each Grave Hound on its own, both glowing as legal targets, under the slim target bar.")
        val second = panel.single { it.cardId == graveyard[1] }
        compose.runOnIdle { toolkit.open(TableIntent.Actions(second.id, null), second) }
        compose.waitForIdle()
        val chosen = (toolkit.cast.flow?.steps?.single()?.second as Answer.Targets).chosen.single() as TargetRef.Object
        assertEquals(graveyard[1], toolkit.cast.atwCard(chosen.id))
        assertFalse(toolkit.cast.targeting)
        assertNull(toolkit.layoutState.value.localPileId, "the pile closes once the targets are chosen")
    }

    // i12review #6 (SYS-025): a cast's engine work runs on the worker it is given (off the main thread in the app), never
    // on the caller's thread; the panel is busy meanwhile and takes no input, and a step Cancel replaced is dropped
    @Test fun castStepsRunOnTheWorker() {
        val queued = mutableListOf<() -> Unit>()
        val worker = object : AutomatumWork {
            override fun <T> run(work: () -> T, done: (T) -> Unit) { queued += { done(work()) } }
        }
        val live = board(listOf(mountain, spark), listOf(mountain))
        val cast = CastController(f.a, { live }, { batch, _ -> sent += batch; true }, { emptyList() }, { message = it }, worker).also { it.autotap = false }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.open && cast.busy && cast.flow == null, "nothing worked out on the caller's thread")
        assertFalse(cast.start(hand[1]), "one cast at a time")
        queued.removeAt(0)()
        assertFalse(cast.busy)
        assertTrue(cast.targeting)
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        assertTrue(cast.busy)
        assertFalse(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.a))))), "no input while busy")
        queued.removeAt(0)()
        assertTrue(cast.flow?.pending is Decision.ManaPayment && cast.paid)
        assertTrue(cast.cast())
        cast.cancel()
        queued.toList().forEach { it() }
        assertNull(cast.flow, "Cancel drops the step still being worked out")
        assertTrue(sent.isEmpty())
    }

}
