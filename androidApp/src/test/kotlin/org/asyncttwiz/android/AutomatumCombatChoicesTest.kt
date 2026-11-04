package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.StepFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The attacker's choices and the costs of combat in the combat area (CMB-036–CMB-039), on two devices: Alice's and Bob's
 * toolkits act on one server history. Exert and enlist chips on staged attackers, a band formed by taps, a cost to attack
 * paid by Autotap, a cost to block, and a Siege attacked in its own lane with its protector blocking. Invented cards parsed
 * from their oracle text as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumCombatChoicesTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))
    private val raider = invented("raider", "Sunscorch Raider", "{2}{R}", "Creature — Human Warrior",
        "You may exert this creature as it attacks. When you do, it gets +2/+0 until end of turn. " +
            "(An exerted creature won't untap during your next untap step.)", "3/3")
    private val sergeant = invented("sergeant", "Drillmaster Vey", "{1}{W}", "Creature — Human Soldier",
        "Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. " +
            "When you do, add its power to this creature's until end of turn.)", "2/2")
    private val knight = invented("knight", "Shieldwall Knight", "{1}{W}", "Creature — Human Knight", "Banding", "2/2")
    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = invented("ogre", "Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val plains = invented("plains", "Plains", null, "Basic Land — Plains", "")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val prison = invented("prison", "Mistward Prison", "{2}{W}", "Enchantment",
        "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.")
    private val tollgate = invented("tollgate", "Thorned Tollgate", "{1}{B}", "Enchantment",
        "Creatures can't block unless their controller pays 2 life for each blocking creature they control.")
    private val siegeText = "(As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, " +
        "exile it, then cast it transformed.)"
    private val siege = Invented(DefinitionFace("siege", "Siege of Emberfall", typeLine = "Battle — Siege", manaCost = "{2}{R}",
        rulesText = siegeText, corner = "3"), OracleCard("Siege of Emberfall", "{2}{R}", "Battle — Siege", siegeText, defense = "3"))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val all = listOf(raider, sergeant, knight, bear, ogre, plains, mountain, prison, tollgate, siege, spark)

    private val aHand = f.id("a/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() {
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        AutomatumBoards.reset(); AutomatumBoards.definitionSource = null
    }

    /** Automatum on, turn 3 of Alice at [position]: the first cards of each Battlefield (and Alice's hand) become these
     * (creatures there since turn 1); every other Battlefield and hand card is out of the game. */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>,
        aliceHand: List<Invented> = emptyList(), bobHand: List<Invented> = emptyList()): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
            .also { assertEquals(n, it.size, "the fixture has $n cards in $container") }
        val bHand = f.id("b/HAND")
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField) +
            first(aHand, aliceHand.size).zip(aliceHand) + first(bHand, bobHand.size).zip(bobHand)).toMap()
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            // I-D41: Full control, so Automatum stops where a player could act and the tests see each step
            board = initial.board.copy(priority = priority, players = initial.board.players.map { it.copy(fullControl = true) },
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false,
                        covered = card.containerId == aHand || card.containerId == bHand, enteredTurn = if (card.containerId in setOf(aField, bField)) "1" else "")
                } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    private fun card(what: Invented, container: String? = null, n: Int = 0) =
        play.board.cards.filter { it.definition == what.submitted.digest && (container == null || it.containerId == container) }[n].id

    // ---- One history both devices act on, as the server keeps it

    private var tree: GameplayTree? = null
    private val live = mutableStateOf<PlayState?>(null)
    private var requests = 0
    private val play get() = live.value!!

    private fun start(initial: PlayState) { tree = GameplayTree(initial); live.value = initial }

    private fun accept(actor: String, action: GameAction, request: String = f.id("request/${requests++}")): Boolean {
        val history = tree!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1")
        val change = if (action is GameAction.Play) history.play(actor, command) else PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes $action")
        tree = change.tree
        live.value = tree!!.stateAt(tree!!.currentNodeId)
        return true
    }

    private fun device(viewer: String) = TableToolkitController(viewer, { live.value }, { true },
        { action, _ -> accept(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> accept(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> accept(viewer, batch, request) })

    private val shownDevice = mutableStateOf<TableToolkitController?>(null)

    /** Shows the table as [toolkit]'s device does (the content is set once; another device's view replaces it). */
    private fun render(toolkit: TableToolkitController) {
        AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val first = shownDevice.value == null
        shownDevice.value = toolkit
        if (first) compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        shownDevice.value?.let { device -> androidx.compose.runtime.key(device) {
                            TableSurface(f.game, live.value!!, device.viewer, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = device::handle, toolkit = device,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
                        } }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun scene(viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
    private fun tap(toolkit: TableToolkitController, item: TableItem) = toolkit.open(TableIntent.Actions(item.id, null), item)
    private fun tapCard(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.cardId == card && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD })
    private fun tapCopy(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.viewport == "combat" && it.sourceId == card })
    private fun shown() = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
    private fun arrow(toolkit: TableToolkitController, label: String) = tapArrow(play, toolkit, shown(), label)
    private fun position() = TurnPosition.of(play.board.turn)
    private fun choices() = CombatFlow.attackChoices(play.board)
    private fun blocks() = CombatStackRules.blocks(play.board.combat).map { (b, a) -> b.sourceId to a.sourceId }.toSet()
    private fun life(player: String) = play.board.players.single { it.accountId == player }.life.toInt()

    /** Each device acts in turn until [done]: the deciding player answers an open choice with its first listed answer,
     * else whoever holds priority passes with the step arrow. */
    private fun until(alice: TableToolkitController, bob: TableToolkitController, done: () -> Boolean) {
        val devices = mapOf(f.a to alice, f.b to bob)
        var guard = 20
        while (!done() && guard-- > 0) {
            val flow = AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))!!
            val decision = flow.decision
            if (decision != null) devices.getValue(decision.player.value).let { d -> d.flows.show(flow)
                assertTrue(d.flows.answer(flow.listedAnswers().first()), d.error) }
            else devices.getValue(flow.holder ?: flow.declaring!!).let { d -> assertTrue(d.flows.step(StepFlow.Until.Pass), d.error) }
        }
        assertTrue(done(), "got there: ${position()}")
    }

    /** After Alice's Attack: on to Bob's declare blockers step. */
    private fun toBlockers(alice: TableToolkitController, bob: TableToolkitController) =
        until(alice, bob) { position() == TurnPosition.DECLARE_BLOCKERS }

    // CMB-036, CMB-037, CR 508.1g, 701.43d, 702.154a: on Alice's device the staged Raider's Exert chip toggles its exert and
    // the Sergeant's Enlist chip picks the Bear on the Battlefield; Bob's device shows both choices without changing them;
    // Attack taps the Bear and the preview counts the exert and the enlisted power
    @Test fun exertAndEnlistOnTwoDevices() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(raider, sergeant, bear), bobField = listOf(ogre, mountain),
            bobHand = listOf(spark)))
        val alice = device(f.a)
        val bob = device(f.b)
        val (raiderCard, sergeantCard, bearCard) = listOf(card(raider), card(sergeant), card(bear))
        tapCard(alice, raiderCard)
        tapCard(alice, sergeantCard)
        render(alice)
        compose.onNodeWithContentDescription("Exert as it attacks").performClick()
        compose.waitForIdle()
        assertEquals(1, choices().getValue(raiderCard).paying.size, "exert chosen")
        compose.onNodeWithContentDescription("Enlist").performClick()
        compose.waitForIdle()
        assertEquals(CombatController.Picking.Enlist(sergeantCard), alice.combat.picked)
        tapCard(alice, bearCard)
        assertEquals(listOf(bearCard), choices().getValue(sergeantCard).enlisted)
        assertNull(alice.combat.picked, "one enlist ability: picking ends")
        assertEquals(listOf(raiderCard, sergeantCard), play.board.combat!!.pairs.flatMap { it.attackers }.map { it.sourceId }, "the Bear isn't staged")
        render(alice)
        compose.onNodeWithContentDescription("Enlists Stout Bear").assertExists()
        compose.onNodeWithContentDescription("Enlisted by Drillmaster Vey").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-exert-enlist.png",
            "Alice staged Sunscorch Raider and Drillmaster Vey, tapped the Raider's Exert chip (checked, filled violet) and the Vey's Enlist chip, then her Stout Bear on the Battlefield: Vey's chip reads +2 and the Bear carries a violet enlisted chip.")
        // Bob's device: the same choices, shown only
        render(bob)
        compose.onNodeWithContentDescription("Enlists Stout Bear").assertExists()
        compose.onNodeWithContentDescription("Exert as it attacks").assertExists().assertHasNoClickAction()
        captureShot("$SCREENSHOTS/automatum/combat-exert-enlist-defender.png",
            "Bob's device while Alice stages: her Raider's Exert and her Vey's +2 enlist chips show above their copies in his mirrored view, without being tappable, and her Stout Bear carries the enlisted chip.")
        assertTrue(arrow(alice, "Attack (2)"), alice.error)
        assertTrue(play.board.cards.single { it.id == bearCard }.tapped, "enlisting taps the Bear")
        assertTrue(choices().isEmpty(), "declared: the choices are gone")
        toBlockers(alice, bob)
        assertEquals(mapOf(f.b to -9), shown().preview!!.life.filterValues { it != 0 }, "5 exerted + 4 enlisted")
    }

    // CMB-038, CMB-037: unstaging an attacker drops its choices with it; Clear drops them all
    @Test fun unstagingDropsTheChoices() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(raider, sergeant, bear), bobField = listOf(ogre)))
        val alice = device(f.a)
        val (raiderCard, sergeantCard, bearCard) = listOf(card(raider), card(sergeant), card(bear))
        tapCard(alice, raiderCard)
        tapCard(alice, sergeantCard)
        val exert = assertNotNull(shown().choices.optional[raiderCard]).single()
        assertTrue(alice.combat.toggleOptional(raiderCard, exert.key))
        alice.combat.pickEnlisted(sergeantCard)
        tapCard(alice, bearCard)
        assertEquals(setOf(raiderCard, sergeantCard), choices().keys)
        tapCard(alice, raiderCard)
        assertEquals(setOf(sergeantCard), choices().keys, "the Raider left the staging with its exert")
        assertTrue(alice.clearCombatDraft(play), alice.error)
        assertTrue(choices().isEmpty())
        assertNull(play.board.engineRecords.firstOrNull { it.kind == EngineRecordKind.PreAnswer })
    }

    // old model (I-D54 §14): the history line of the attacker's choices
    @Test fun attackChoicesHistoryText() {
        val names = mapOf("v" to "Drillmaster Vey", "b" to "Stout Bear", "k" to "Shieldwall Knight", "r" to "Sunscorch Raider")
        assertEquals("Attack choices: Sunscorch Raider pays as it attacks · Drillmaster Vey enlists Stout Bear · band: Shieldwall Knight, Stout Bear",
            attackChoicesText(listOf(org.asyncttwiz.automatum.AttackChoice("r", paying = listOf("p0.0")),
                org.asyncttwiz.automatum.AttackChoice("v", enlisted = listOf("b")), org.asyncttwiz.automatum.AttackChoice("k", band = 1),
                org.asyncttwiz.automatum.AttackChoice("b", band = 1)), names::getValue))
        assertEquals("Attack choices cleared", attackChoicesText(emptyList(), names::getValue))
    }

    // CMB-038, CMB-031, CR 702.22c, 702.22h: Alice's Band chip on the Knight, then a tap on the staged Bear's copy, bands
    // them (the Bear stays staged); Bob's Ogre blocking the Bear blocks the whole band
    @Test fun aBandFormedByTaps() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(knight, bear), bobField = listOf(ogre, mountain),
            bobHand = listOf(spark)))
        val alice = device(f.a)
        val bob = device(f.b)
        val (knightCard, bearCard, ogreCard) = listOf(card(knight), card(bear), card(ogre))
        tapCard(alice, knightCard)
        tapCard(alice, bearCard)
        render(alice)
        compose.onNodeWithContentDescription("Band").performClick()
        compose.waitForIdle()
        tapCopy(alice, bearCard)
        assertEquals(2, play.board.combat!!.pairs.flatMap { it.attackers }.size, "the Bear joined the band, still staged")
        val band = choices().getValue(knightCard).band
        assertNotNull(band)
        assertEquals(band, choices().getValue(bearCard).band)
        alice.combat.clearSelection()
        render(alice)
        compose.onAllNodesWithContentDescription("In a band").assertCountEquals(2)
        captureShot("$SCREENSHOTS/automatum/combat-band.png",
            "Alice staged Shieldwall Knight (banding) and Stout Bear, tapped the Knight's Band chip and then the Bear's copy: both copies carry a filled Band chip and a violet bar over them joins them into one band.")
        assertTrue(arrow(alice, "Attack (2)"), alice.error)
        toBlockers(alice, bob)
        tapCard(bob, ogreCard)
        tapCopy(bob, bearCard)
        assertEquals(setOf(ogreCard to bearCard), blocks())
        assertTrue(arrow(bob, "Block (1)"), bob.error)
        assertEquals(setOf(ogreCard to knightCard, ogreCard to bearCard), blocks(), "blocking one blocks the band")
    }

    // CMB-037, CR 508.1h, LOB-113: Bob's Prison asks {2} of each attacker: the staged Bear shows it, and Attack pays it with
    // Alice's Plains by Autotap
    @Test fun aCostToAttackIsShownAndPaidByAutotap() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(bear, plains, plains), bobField = listOf(prison, ogre)))
        val alice = device(f.a)
        val bearCard = card(bear)
        tapCard(alice, bearCard)
        render(alice)
        compose.onNodeWithContentDescription("Pays {2}").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-attack-cost.png",
            "Bob's Mistward Prison asks {2} of each creature attacking him: Alice's staged Stout Bear shows an amber {2} chip under its copy, and the step arrow reads Attack (1).")
        assertTrue(arrow(alice, "Attack (1)"), alice.error)
        assertTrue(play.board.cards.filter { it.definition == plains.submitted.digest }.all { it.tapped }, "Autotap paid with both Plains")
        assertTrue(play.board.cards.single { it.id == bearCard }.tapped)
    }

    // CMB-037, CR 509.1d: Bob's own Tollgate asks 2 life of each blocker: his drafted Ogre shows it, and Block pays it
    @Test fun aCostToBlockIsShownAndPaid() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(bear), bobField = listOf(ogre, tollgate, mountain),
            bobHand = listOf(spark)))
        val alice = device(f.a)
        val bob = device(f.b)
        val (bearCard, ogreCard) = listOf(card(bear), card(ogre))
        tapCard(alice, bearCard)
        assertTrue(arrow(alice, "Attack (1)"), alice.error)
        toBlockers(alice, bob)
        tapCard(bob, ogreCard)
        tapCopy(bob, bearCard)
        render(bob)
        compose.onNodeWithContentDescription("Pays 2 life").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-block-cost.png",
            "Bob's Thorned Tollgate asks 2 life of each blocker: his Hill Ogre, drafted to block Alice's Stout Bear (arrow), shows an amber 2 life chip, and the step arrow reads Block (1).")
        val before = life(f.b)
        assertTrue(arrow(bob, "Block (1)"), bob.error)
        assertEquals(before - 2, life(f.b), "the Ogre's 2 life paid")
    }

    // CMB-039, CMB-012, CR 310.9b–d, 310.12a: Alice casts her Siege (Bob protects it); in combat its lane takes her Bear, and
    // Bob, its protector, blocks for it
    @Test fun aSiegeIsAttackedAndItsProtectorBlocks() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear, mountain, mountain, mountain),
            bobField = listOf(ogre, mountain), aliceHand = listOf(siege), bobHand = listOf(spark)))
        val alice = device(f.a)
        val bob = device(f.b)
        val (siegeCard, bearCard, ogreCard) = listOf(card(siege), card(bear), card(ogre))
        engine(f.a) { call ->
            val s = call.working
            val cast = Engine.options(s, PlayerId(f.a)).filterIsInstance<Input.Cast>()
                .first { s.obj(it.card)?.definition?.faces?.first()?.name == "Siege of Emberfall" }
            var open = call.perform(cast)
            while (open != null) {
                val d = open.decision
                open = call.perform(if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                    else Engine.options(call.working, d.player).filterIsInstance<Input.Answer>().first())
            }
            StepFlow.automatic(call)
        }
        until(alice, bob) { play.board.cards.single { it.id == siegeCard }.containerId == aField }
        assertEquals(3, play.board.cards.single { it.id == siegeCard }.typedCounters.single { it.kind == "defense" }.count)
        until(alice, bob) { position() == TurnPosition.DECLARE_ATTACKERS }
        alice.combat.chooseTarget(play, siegeCard)
        tapCard(alice, bearCard)
        assertEquals(listOf<String?>(siegeCard), play.board.combat!!.pairs.map { it.attackTarget })
        assertTrue(arrow(alice, "Attack (1)"), alice.error)
        toBlockers(alice, bob)
        assertEquals(f.b, shown().choices.declaring, "the protector declares blockers")
        tapCard(bob, ogreCard)
        tapCopy(bob, bearCard)
        assertEquals(setOf(ogreCard to bearCard), blocks())
        render(bob)
        captureShot("$SCREENSHOTS/automatum/combat-siege.png",
            "Alice cast her Siege of Emberfall (Bob protects it) and attacked it with Stout Bear in its own lane, headed by its 3 defense; Bob, its protector, blocks the Bear with his Hill Ogre (arrow) and the preview shows the Bear dying.")
        assertTrue(arrow(bob, "Block (1)"), bob.error)
    }

    /** One engine call made by [body] on the board, sent as [actor]'s automated batch. */
    private fun engine(actor: String, body: (EngineCall) -> Unit) {
        val definitions = AutomatumBoards.definitions(play)
        val call = EngineCall(play, definitions, null, AutomatumBoards.engineLog(play, definitions, engineTags(tree, play.currentNodeId))) { "seed" }
        body(call)
        val request = f.id("request/${requests++}")
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), GameAction.PlayBatch(listOf(PlayAction.PassAttention)), f.id("client"), "1")
        accept(actor, EngineBatch.plan(call, actor, command).action, request)
    }
}
