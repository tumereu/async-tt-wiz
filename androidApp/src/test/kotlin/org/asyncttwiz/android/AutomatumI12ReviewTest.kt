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
import org.junit.Ignore
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
 * problem found and is ignored until it is fixed. Invented cards parsed from their oracle text as on a device. */
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
        CastController(f.a, current, { batch, _ -> sent += batch; true }, { emptyList() }, { message = it })
    private fun manaGroups(decision: Decision.ManaPayment) =
        decision.options.filterIsInstance<ManaUse.Ability>().groupBy { it.source }.values.map { it.toList<ManaUse>() }

    // i12review #1 (I-D24, SYS-060, D32): the other player (or another device) switches Automatum off while Alice's cast
    // is open; the cast is worked out again on the new board and still goes out as an automated batch with Automatum off
    @Ignore("i12review #1: an open cast survives Automatum being switched off under it")
    @Test fun switchingOffUnderAnOpenCastDropsIt() {
        var live: PlayState? = board(listOf(mountain, spark), listOf(mountain))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        val off = live!!.copy(board = live!!.board.copy(automatum = false))
        live = off
        cast.follow(off)
        assertNull(cast.flow, "Automatum is off: the cast is dropped (with a message), never sent as an automated batch")
    }

    // i12review #2 (§2 "switching Automatum off is always allowed and is the way out of anything stuck", TRN-054):
    // with a cast open, the player's own switch is refused with "Finish or cancel the cast first."
    @Ignore("i12review #2: own Automatum switch refused while a cast is open")
    @Test fun ownSwitchOffDuringAnOpenCastDropsTheCastAndSwitches() {
        val play = board(listOf(mountain, spark), listOf(mountain))
        val gameplay = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> gameplay += action; true },
            automatedBatch = { batch, _ -> sent += batch; true })
        assertTrue(toolkit.cast.start(hand[1]))
        assertTrue(toolkit.setAutomatum(false, null), "switching off: ${toolkit.error}")
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetAutomatum(false))), gameplay)
        assertNull(toolkit.cast.flow)
    }

    // i12review #3 (I-D27, STK-005, CMB-025): Alice targets Bob's face-down creature; the cast panel's answered Target
    // section names it by its real card ("Hidden Basilisk"): CastPanel.objectName falls back to the game's card data when
    // Alice's engine state doesn't know the object
    @Ignore("i12review #3: the cast panel names a face-down target")
    @Test fun faceDownTargetIsNotNamedInTheCastPanel() {
        val hidden = f.source(f.b, "2/3")
        val play = board(listOf(mountain, spark), listOf(mountain)) { card ->
            if (card.id == hidden.id) card.copy(definition = basilisk.submitted.digest, faceId = basilisk.face.faceId,
                covered = true, explicitlyCovered = true, corner = "") else card
        }
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true }, automatedBatch = { batch, _ -> sent += batch; true })
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
        compose.runOnIdle { toolkit.open(TableIntent.Actions(item.id, null), item, TableSelection()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onAllNodesWithText("Hidden Basilisk", substring = true).assertCountEquals(0)
    }

    // i12review #5 (CMB-024 "using current … P/T", TBL-112): with Automatum on the attacker shows its computed 4/4 (printed
    // 2/2 with two +1/+1 counters), but "Deal combat damage" deals the hand-set corner's 2
    @Ignore("i12review #5: combat damage ignores the computed P/T")
    @Test fun combatDamageUsesTheComputedPowerToughness() {
        val fixture = CombatDamageFixture(attackers = listOf("2/2"))
        val face = DefinitionFace(TableToolFixture.id(25), "Combatant", typeLine = "Creature — Bear", power = "2", toughness = "2")
        val printed = CardDefinition("Combatant", listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val old = fixture.play.definitions.single { it.definition.name == "Combatant" }.digest
        // The fixture's minimal board has no graveyards or exiles; every real game has them
        val piles = listOf(fixture.actor, fixture.defender).flatMapIndexed { n, player -> listOf(
            PlayContainer(TableToolFixture.id(300 + 2 * n), PlayZone.GRAVEYARD, player),
            PlayContainer(TableToolFixture.id(301 + 2 * n), PlayZone.EXILE, player)) }
        val play = fixture.play.copy(definitions = fixture.play.definitions.map { if (it.digest == old) printed else it },
            board = fixture.play.board.copy(automatum = true, containers = fixture.play.board.containers + piles,
                cards = fixture.play.board.cards.map { card ->
                if (card.definition != old) card else card.copy(definition = printed.digest, timestamp = 1,
                    typedCounters = listOf(TypedCounter("+1/+1", 2, 2)))
            }))
        assertEquals("4/4", ComputedBoard.of(play, emptyMap(), fixture.actor).cards[fixture.attackerIds.single()]?.corner,
            "the table shows 4/4")
        val gameplay = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(fixture.actor, { play }, { true }, { action, _ -> gameplay += action; true })
        AutomatumBoards.compute(play, fixture.actor)
        assertTrue(toolkit.dealCombatDamage(fixture.attackerViews, areaId = play.board.combat!!.id))
        assertEquals(listOf<PlayAction>(PlayAction.AdjustLife(fixture.defender, "-4")), gameplay)
    }

    // i12review #7 (STK-024 "tapping a chip taps or untaps it", I-D6): re-tapping a two-colour land before the other
    // source picks its first colour, so Plains + Glacier Ford ({W} or {U}) end up as {W}{W} for {W}{U}: "Not paid yet"
    // although the two tapped sources pay; the player has to untap and re-tap in the other order
    @Ignore("i12review #7: toggling mana sources depends on tap order")
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

    // i12review #8 (SYS-060 "removes everything Automatum recorded", TRN-055's dialog "Removes every effect, ledger and
    // record"): Clear Automatum data leaves card and player ledgers; with only those (or typed counters) on the board the
    // button is enabled but its event changes nothing
    @Ignore("i12review #8: Clear Automatum data leaves ledgers")
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
    }

    // i12review #9 (I-D27, ZON-007 rules v30: the other player acts on a hand blind): with Automatum on, Bob's hidden hand
    // cards read "Play" (a land) or "Cast" in Alice's hand menu, and Alice's double-tap on one says "<its name> can't be
    // cast now." / "can't be played now."
    @Ignore("i12review #9: the opponent's hidden hand card is named and its land-ness shown")
    @Test fun opponentsHiddenHandCardIsNeitherNamedNorTyped() {
        assertTrue(bobHand.size >= 2)
        val play = board(emptyList(), listOf(mountain)) { card -> when (card.id) {
            bobHand[0] -> card.copy(definition = mountain.submitted.digest, faceId = mountain.face.faceId)
            bobHand[1] -> card.copy(definition = spark.submitted.digest, faceId = spark.face.faceId)
            else -> card
        } }
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true }, automatedBatch = { batch, _ -> sent += batch; true })
        fun doubleTap(card: String): String? {
            toolkit.dismissError()
            toolkit.handle(TableIntent.Move(TableScope(card, listOf(card)), TableDestination.Container(f.id("stack"), PlayZone.STACK),
                handDoubleTap = true))
            return toolkit.error
        }
        val land = doubleTap(bobHand[0])
        val instant = doubleTap(bobHand[1])
        assertFalse(land.orEmpty().contains("Mountain") || instant.orEmpty().contains("Storm Spark"), "$land / $instant")
        assertEquals(land, instant, "the refusal doesn't tell a hidden land from a hidden spell")
        fun label(card: String) = handMenuTools(toolkit, play, ToolkitCapture(listOf(card), card, listOf(card), emptyMap(), null),
            TableSelection(), emptyMap(), true).single { it.name == "Play" || it.name == "Cast" }.name
        assertEquals(label(bobHand[0]), label(bobHand[1]), "the menu doesn't tell a hidden land from a hidden spell")
    }

    // i12review #10 (SYS-025 gate, TableToolkitController.enabled): the cast goes out while the player can't act (the
    // personal timeline is open, the connection is down): Cast sends the batch planned on the board it last followed
    @Ignore("i12review #10: Cast sends while actions are disabled")
    @Test fun castSendsNothingWhileActionsAreDisabled() {
        var live: PlayState? = board(listOf(mountain, spark), listOf(mountain))
        val cast = controller { live }
        assertTrue(cast.start(hand[1]))
        assertTrue(cast.answer(Answer.Targets(listOf(TargetRef.Player(PlayerId(f.b))))))
        // Reviewing the timeline (or disconnected): the toolkit's current() gives no board to act on
        live = null
        cast.cast()
        assertTrue(sent.isEmpty(), "nothing is sent while actions are disabled")
    }

    // i12review #13 (STK-024, STK-025 "keeping the choices that still fit"): the player's own tapping in the mana section
    // is reset to the suggestion whenever anything on the board changes (Bob's life here)
    @Ignore("i12review #13: own mana tapping lost when the board changes")
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
        cast.follow(changed)
        assertEquals(chosen, cast.mana, "the player's own tapping is kept")
    }

    // i12review #14 (I-D6 "targets are chosen on the table", STK-023): a target card in a graveyard glows by its card id
    // but the table draws a pile, and while targets are chosen a tap on the pile is "That isn't a legal target." and can't
    // open it: Grave Recall's targets below the top card can't be picked at all
    @Ignore("i12review #14: graveyard targets can't be picked on the table")
    @Test fun everyLegalTargetCanBePickedOnTheTable() {
        assertTrue(graveyard.size >= 2)
        val play = board(listOf(mountain, recall), listOf(swamp)) { card ->
            if (card.id in graveyard) card.copy(definition = hound.submitted.digest, faceId = hound.face.faceId) else card
        }
        val cast = controller { play }
        assertTrue(cast.start(hand[1]))
        val targets = assertNotNull(cast.flow?.pending as? Decision.Targets)
        assertTrue(targets.options.size >= 2, "both Grave Hounds are legal targets")
        val items = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
        val unreachable = cast.glow(play).filter { id -> items.none { it.cardId == id || it.sourceId == id || it.id == id } }
        assertTrue(unreachable.isEmpty(), "targets without a table item to tap: $unreachable")
    }
}
