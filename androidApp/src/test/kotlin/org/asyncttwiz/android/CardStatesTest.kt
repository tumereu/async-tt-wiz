@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Duration
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectMark
import arcanum.api.RulesGlossary
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.state.CardId
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.PlayerStates
import org.asyncttwiz.automatum.StateGlyph
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the rules keep and the picture doesn't show: card states (TBL-133, ZON-094), restrictions from effects (TBL-134) and a
 * player's and the game's states (TRN-072, TRN-073). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardStatesTest {
    @get:Rule val compose = createComposeRule()
    @After fun forget() = AutomatumBoards.reset()

    private val f = TableStatesFixture
    private val names = mapOf(f.b to "Bob", f.a to "Alice")
    private val sentinel = f.source(f.a, "1/3")
    private val river = f.source(f.a, "1/2")
    private val blocker = f.source(f.b, "2/3")

    private fun id(card: PlayCard) = ObjectId.Card(CardId(card.id), card.zoneChanges)
    private fun GameState.mark(card: PlayCard, edit: (GameObject) -> GameObject) { setup.objects[id(card)] = edit(objects.getValue(id(card))) }
    private fun GameState.player(account: String, edit: (arcanum.engine.state.PlayerState) -> arcanum.engine.state.PlayerState) {
        val at = players.indexOfFirst { it.id.value == account }
        setup.players[at] = edit(players[at])
    }

    /** The table [viewer] draws of [play] with [edit] made to its engine state, as [EngineFixture] makes it. */
    private fun board(play: PlayState = f.initial, viewer: String = f.a, edit: GameState.() -> Unit): TableBoard {
        // The emblems' records are what a board shows of the engine's emblems: the test's state doesn't hold them
        val state = TestEngineState.of(play.copy(board = play.board.copy(engineRecords = emptyList())))
        StepFlow.settledPriority(state)?.let { state.setup.priority = it }
        state.edit()
        val model = GameModel(state, TableState(secret = LineRandom.secret(ByteArray(32) { 7 })))
        val me = PlayerId(viewer)
        val visible = model.visible(me)
        val engine = EngineView(model.engine, model.table, me, visible)
        val values = EngineBoardValues.make(EngineProjection.Board(play.board, play.definitions), play.gameId, engine.state, viewer,
            Engine.view(engine.state), visible, EngineWorker.Direct)
        return TableBoard(play.gameId, play.id, play.root.id, play.board, play.definitions, engine, values)
    }

    private fun scene(board: TableBoard, viewer: String = f.a, width: Float = 412f, height: Float = 892f, landscape: Boolean = false) =
        TablePresentation.scene(f.game, board, viewer, width, height, landscape, computed = board.values!!.computed)

    private fun changes(board: TableBoard, card: PlayCard, viewer: String = f.a) =
        InspectChanges.of(board, viewer, listOf(card), names)[card.id].orEmpty()

    // TBL-133: a card's states come first on its chip, which is cut short (TBL-113), whatever else changed on it
    @Test fun aStateComesFirstOnTheChip() {
        val board = board {
            mark(sentinel) { it.copy(marks = listOf(ObjectMark.RingBearer, ObjectMark.Monstrous), goadedBy = listOf(PlayerId(f.b)),
                counters = listOf(CounterEntry(CounterKind.PowerToughness(1, 1), 2, 1))) }
        }
        val item = scene(board).items.first { it.sourceId == sentinel.id && it.zone == PlayZone.BATTLEFIELD }
        assertEquals(listOf("Ring-bearer", "Goaded", "Monstrous"), item.automatumMarks.take(3))
        assertTrue(item.automatumMarks.joinToString(", ").take(32).startsWith("Ring-bearer, Goaded, Monstrous"), "the states survive the cut")
        assertTrue(item.label.contains(", Ring-bearer, Goaded, Monstrous"), "read aloud too: ${item.label}")
        assertEquals(emptyList(), scene(board).items.first { it.sourceId == river.id && it.zone == PlayZone.BATTLEFIELD }.automatumMarks)
    }

    // TBL-133: every state with its own word; the words a face-down permanent keeps to itself aren't among them
    @Test fun eachStatesWordAndWhatStaysHidden() {
        val marks = listOf(ObjectMark.Renowned to "Renowned", ObjectMark.Suspected to "Suspected", ObjectMark.Saddled to "Saddled",
            ObjectMark.Solved to "Solved", ObjectMark.Prepared to "Prepared", ObjectMark.Harnessed to "Harnessed",
            ObjectMark.Unlocked(arcanum.api.Door.Left) to "Left unlocked", ObjectMark.Named("tribute paid") to "Tribute paid")
        for ((mark, word) in marks) {
            val computed = board { mark(sentinel) { it.copy(marks = listOf(mark)) } }.values!!.computed
            assertEquals(listOf(word), computed.cards.getValue(sentinel.id).states)
        }
        val board = board(viewer = f.b) { mark(sentinel) { it.copy(marks = listOf(ObjectMark.Manifested, ObjectMark.Cloaked)) } }
        assertEquals(emptyList(), board.values!!.computed.cards.getValue(sentinel.id).states)
        assertEquals(emptyList(), changes(board, sentinel, f.b).map { it.text }, "nothing tells the opponent how it came face down")
    }

    // TBL-133: Inspect says what a Ring-bearer gets at the Ring's current level, with the Ring as its source; TBL-132 explains it
    @Test fun aRingBearersInspectLineFollowsTheRingsLevel() {
        fun line(level: Int) = changes(board {
            mark(sentinel) { it.copy(marks = listOf(ObjectMark.RingBearer)) }
            player(f.a) { it.copy(ledger = mapOf("ring-tempted" to level)) }
        }, sentinel).first()
        assertEquals("Ring-bearer: legendary, can't be blocked by creatures with greater power", line(1).text)
        assertEquals("The Ring", line(1).source)
        assertTrue(line(2).text.endsWith("; when it attacks, you draw a card, then discard a card"), line(2).text)
        assertEquals(4, line(9).text.split("; ").size, "the Ring has four levels")
        assertEquals("Ring-bearer", RulesGlossary.first(line(1).text)?.name)
    }

    // TBL-133: goaded names who goaded it in the viewer's words; a battle's protector and what was chosen for a permanent show too
    @Test fun goadedProtectorAndChosenValuesInInspect() {
        val edit: GameState.() -> Unit = {
            mark(sentinel) { it.copy(goadedBy = listOf(PlayerId(f.b)), protector = PlayerId(f.a),
                memory = it.memory.with("chosen", NamedValue.Colors(listOf(arcanum.api.Color.Blue))).with("\$secret", NamedValue.Name("Hidden"))) }
        }
        val board = board(edit = edit)
        assertEquals(listOf("Goaded by Bob: attacks each combat if able, and not Bob if it can", "Protector: you", "Chosen: blue"),
            changes(board, sentinel).map { it.text }.take(3))
        assertEquals(listOf("Goaded by you: attacks each combat if able, and not you if it can", "Protector: Alice", "Chosen: blue"),
            changes(board(viewer = f.b, edit = edit), sentinel, f.b).map { it.text }.take(3))
        assertTrue(changes(board, sentinel).none { "Hidden" in it.text }, "the engine's own memory stays its own")
    }

    // TBL-134: what an effect tells a creature it can't do is a line in Inspect with its source, like a change line; not on the chip
    @Test fun anEffectsRestrictionIsAnInspectLineWithItsSource() {
        val board = board {
            setup.effects[EffectId("E1")] = EffectObject(EffectId("E1"), listOf(Mechanic.CantBlock(arcanum.api.ObjectFilter.Any)), affects = null,
                locked = listOf(id(sentinel)), source = id(blocker), controller = PlayerId(f.b), timestamp = 900, createdTurn = 1, duration = Duration.EndOfTurn)
        }
        val line = changes(board, sentinel).single { it.text.startsWith("Can't block") }
        assertEquals("Can't block until end of turn", line.text)
        assertEquals("Lantern Guide", line.source)
        assertEquals(emptyList(), board.values!!.computed.cards.getValue(sentinel.id).states, "not on the chip")
        assertEquals(emptyList(), scene(board).items.first { it.sourceId == sentinel.id && it.zone == PlayZone.BATTLEFIELD }.automatumMarks)
    }

    // TBL-134: a Ring-bearer's rule from the Ring is its own line, not a line of its own again
    @Test fun theRingsRestrictionIsNotListedTwice() {
        val board = board {
            mark(sentinel) { it.copy(marks = listOf(ObjectMark.RingBearer)) }
            val emblem = ObjectId.Card(CardId("ring-emblem"), 0)
            setup.objects[emblem] = GameObject(emblem, PlayerId(f.a), PlayerId(f.a), Zone.Command, CardDefinition(Layout.Normal, listOf(FaceDefinition("The Ring",
                abilities = listOf(arcanum.api.Ability.Static(listOf(Mechanic.CantBeBlockedBy(arcanum.api.ObjectFilter.Any)),
                    arcanum.api.Affected.Objects(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.HasStatus(arcanum.api.Status.Marked(ObjectMark.RingBearer))))))))), timestamp = 900)
            setup.command.add(emblem)
        }
        assertEquals(1, changes(board, sentinel).count { it.text.contains("blocked") }, "${changes(board, sentinel)}")
    }

    /** Both players with states, the chips' whole set for the opponent: designations, speed, the Ring, counters. */
    private fun GameState.playerStates() {
        player(f.a) { it.copy(designations = listOf(Designation.Monarch), ledger = mapOf("speed" to 2, "ring-tempted" to 2),
            counters = listOf(CounterEntry(CounterKind.Poison, 3, 1))) }
        player(f.b) { it.copy(designations = listOf(Designation.Initiative, Designation.CitysBlessing),
            ledger = mapOf("ring-tempted" to 6), counters = listOf(CounterEntry(CounterKind.Energy, 5, 1))) }
        setup.ledger["\$day-night"] = 2
    }

    /** Emblems for both, as in TRN-053. */
    private fun withEmblems(play: PlayState) = play.copy(board = play.board.copy(engineRecords = listOf(
        EngineRecord("emblem:1", EngineRecordKind.Emblem, 1, "{}", f.a, "Creatures you control get +1/+1."),
        EngineRecord("emblem:2", EngineRecordKind.Emblem, 2, "{}", f.a, "Spells you cast cost {1} less to cast."),
        EngineRecord("emblem:3", EngineRecordKind.Emblem, 3, "{}", f.b, "Your opponents can't gain life."))))

    /** A dungeon on the opponent's side with its marker on a room (TRN-069). */
    private fun GameState.dungeon() {
        val crypt = ObjectId.Card(CardId("00000000-0000-5000-8000-0000000000d1"), 0)
        setup.objects[crypt] = GameObject(crypt, PlayerId(f.b), PlayerId(f.b), Zone.Command, CardDefinition(Layout.Normal, listOf(FaceDefinition("Lantern Crypt",
            types = listOf(CardType.Dungeon), rooms = listOf(arcanum.api.DungeonRoom("Hall of Moths", arcanum.api.Instructions(), emptyList()))))),
            timestamp = 901, memory = arcanum.engine.state.Bindings.EMPTY.with("\$venture-room", NamedValue.Number(0)))
        setup.command.add(crypt)
    }

    // TRN-072: a player's states as chips, in their order, by the rules' words; each round chip shows a glyph (owner gallery
    // review 2026-11-10, gallery 157): its first letter with its number, those that would share a letter told apart
    @Test fun aPlayersStatesInOrder() {
        val state = board { playerStates() }.engine!!.state
        assertEquals(listOf("Monarch", "Speed 2", "Ring 2/4", "Poison 3"), PlayerStates.of(state, f.a).map { it.label })
        assertEquals(listOf("Initiative", "City's blessing", "Ring 4/4", "Energy 5"), PlayerStates.of(state, f.b).map { it.label })
        assertTrue(PlayerStates.of(state, f.a).all { it.explanation != null }, "each explains itself")
        assertEquals("Night", PlayerStates.dayOrNight(state)?.label)
        fun glyphs(account: String) = PlayerStates.of(state, account).map { (it.glyph as StateGlyph.Letters).text }
        assertEquals(listOf("M", "S2", "R2", "P3"), glyphs(f.a))
        assertEquals(listOf("I", "C", "R4", "E5"), glyphs(f.b))
        assertEquals(StateGlyph.Night, PlayerStates.dayOrNight(state)?.glyph)
        val shared = board { player(f.a) { it.copy(designations = listOf(Designation.EnduringStory), ledger = mapOf("speed" to 1),
            counters = listOf(CounterEntry(CounterKind.Energy, 2, 1), CounterEntry(CounterKind.Named("shield"), 1, 2))) } }.engine!!.state
        assertEquals(listOf("St", "S1", "E2", "Sh1"), PlayerStates.of(shared, f.a).map { (it.glyph as StateGlyph.Letters).text },
            "an enduring story beside energy, a shield counter beside speed")
    }

    private fun assertClear(scene: TableScene, width: Float, height: Float, hands: Boolean = true) {
        for (life in scene.items.filter { it.kind == TableItemKind.LIFE }) {
            val chips = life.stateChips
            assertTrue(chips.isNotEmpty(), "${life.id} has chips")
            val round = chips.map { it.bounds } + life.emblemChips
            for ((n, a) in round.withIndex()) for (b in round.drop(n + 1)) assertEquals(null, a.intersection(b), "${life.id}: apart: $a, $b")
            assertTrue(round.all { it.width == it.height && it.width >= EmblemChips.MIN_SIZE }, "round, one size: $round")
            val others = scene.items.filter { hands && it.kind == TableItemKind.HAND || it.kind == TableItemKind.PILE || it.kind == TableItemKind.LIFE }
                .flatMap { it.visible } + life.emblemChips
            for (chip in chips) {
                assertTrue(chip.bounds.x >= 0 && chip.bounds.right <= width && chip.bounds.y >= 0 && chip.bounds.bottom <= height, "on the screen: $chip")
                others.forEach { other -> assertEquals(null, other.intersection(chip.bounds), "${life.id}: ${chip.label} clear of $other") }
            }
        }
    }

    // TRN-072: in portrait the round chips sit right of the piles, before the emblems, clear of the hands, on a narrow screen too
    @Test fun portraitChipsKeepClear() {
        val play = withEmblems(f.initial)
        for (width in listOf(412f, 360f)) assertClear(scene(board(play) { playerStates() }, width = width), width, 892f)
        // TRN-069: with a dungeon, the fourth pile's room still holds them
        assertClear(scene(board(play) { playerStates(); dungeon() }, width = 360f), 360f, 892f)
    }

    // TRN-072, TRN-053, TRN-069 (owner gallery review 2026-11-09, gallery 157): on any width, a dungeon's fourth pile or not, each
    // player's chips, emblems and room stay in their own row, clear of the hands and of the piles as drawn: what doesn't fit folds
    // into "+N", which stands for the rest
    @Test fun chipsStayInTheirRowOnAnyWidth() {
        val play = withEmblems(f.initial)
        for (width in listOf(412f, 360f, 320f, 300f, 280f, 240f)) for (withDungeon in listOf(false, true)) {
            val all = scene(board(play) { playerStates(); if (withDungeon) dungeon() }, width = width)
            assertClear(all, width, 892f)
            for (life in all.items.filter { it.kind == TableItemKind.LIFE }) {
                val row = all.viewports.single { it.id == "dock/${life.owner}" }.bounds.let { TableBounds(0f, it.y, width, it.height) }
                val where = "$width, dungeon $withDungeon, ${life.id}"
                val parts = life.stateChips.map { it.bounds } + life.emblemChips + listOfNotNull(life.chipsArea)
                parts.forEach { assertEquals(it, it.intersection(row), "$where: $it in the row $row") }
                val room = if (withDungeon && life.owner == f.b) 1 else 0
                assertEquals(PlayerStates.of(board { playerStates() }.engine!!.state, life.owner!!).size + room,
                    life.stateChips.count { it.chip != null } + life.stateChips.sumOf { it.hidden.size }, "$where: all of them, shown or under +N")
            }
        }
    }

    // TRN-072, TRN-069, TRN-053 (owner gallery review 2026-11-10, gallery 157): on the 320 dp phone, where the opponent's fourth
    // pile leaves the least room, each player's room, states and emblems are round chips over two lines, all of them shown
    @Test fun roundChipsFitTwoLinesWhereTheDungeonLeavesTheLeastRoom() {
        val all = scene(board(withEmblems(f.initial)) { playerStates(); dungeon() }, width = 320f)
        val opponent = all.items.single { it.id == "life/${f.b}" }
        assertEquals(listOf("Room: Hall of Moths", "Initiative", "City's blessing", "Ring 4/4", "Energy 5"), opponent.stateChips.map { it.label })
        assertEquals(1, opponent.emblemChips.size)
        assertEquals(2, (opponent.stateChips.map { it.bounds } + opponent.emblemChips).map { it.y }.distinct().size, "two lines")
        val own = all.items.single { it.id == "life/${f.a}" }
        assertEquals(listOf("Monarch", "Speed 2", "Ring 2/4", "Poison 3"), own.stateChips.map { it.label })
        assertEquals(2, own.emblemChips.size)
    }

    // TRN-072, TRN-053 (owner gallery review 2026-11-10, gallery 157): many states at once on a narrow phone: the round chips shrink,
    // then the states' last chip reads "+N" for those left out, never overlapping; the emblems keep their chips
    @Test fun manyStatesFoldIntoPlusN() {
        val many = board(withEmblems(f.initial)) {
            playerStates()
            player(f.a) { it.copy(designations = listOf(Designation.Monarch, Designation.Initiative, Designation.CitysBlessing,
                Designation.EnduringStory), counters = listOf(CounterEntry(CounterKind.Poison, 3, 1), CounterEntry(CounterKind.Energy, 5, 2),
                CounterEntry(CounterKind.Named("shield"), 1, 3), CounterEntry(CounterKind.Named("rad"), 4, 4))) }
        }
        val states = PlayerStates.of(many.engine!!.state, f.a)
        assertTrue(states.size >= 9, "$states")
        for ((width, height, landscape) in listOf(Triple(320f, 640f, false), Triple(412f, 892f, false), Triple(892f, 412f, true))) {
            val scene = scene(many, width = width, height = height, landscape = landscape)
            assertClear(scene, width, height)
            val own = scene.items.single { it.id == "life/${f.a}" }
            assertEquals(states.size, own.stateChips.count { it.chip != null } + own.stateChips.sumOf { it.hidden.size }, "$width: all, shown or under +N")
            if (width == 320f) {
                assertTrue(own.stateChips.last().label.startsWith("+"), "folded: ${own.stateChips.map { it.label }}")
                assertEquals(2, own.emblemChips.size, "both emblems keep their chips")
            }
        }
    }

    // TRN-072: in landscape right after the piles in the bar row; what doesn't fit is a "+N" chip that stands for the rest
    @Test fun landscapeChipsKeepClearAndFold() {
        val play = withEmblems(f.initial)
        for ((width, height) in listOf(892f to 412f, 800f to 360f, 640f to 360f)) {
            val scene = scene(board(play) { playerStates() }, width = width, height = height, landscape = true)
            assertClear(scene, width, height)
            for (life in scene.items.filter { it.kind == TableItemKind.LIFE }) {
                val chips = life.stateChips
                assertEquals(PlayerStates.of(board { playerStates() }.engine!!.state, life.owner!!).size,
                    chips.count { it.chip != null } + chips.sumOf { it.hidden.size }, "all of them, shown or under +N: $chips")
                val row = scene.viewports.single { it.id == "dock/${life.owner}" }.bounds
                chips.forEach { assertEquals(it.bounds, it.bounds.intersection(row), "in the row: $it") }
            }
        }
        val narrow = scene(board(play) { playerStates() }, width = 640f, height = 360f, landscape = true)
        assertTrue(narrow.items.single { it.id == "life/${f.a}" }.stateChips.last().label.startsWith("+"), "folded on the narrow screen")
    }

    // TRN-073: the game's day or night sits above the Stack, whoever looks
    @Test fun dayOrNightSitsAboveTheStack() {
        val scene = scene(board { playerStates() })
        val chip = scene.dayNightChip!!
        val stack = scene.stackRegion!!
        assertEquals("Night", chip.label)
        assertTrue(chip.bounds.bottom <= stack.y - 8.5f && chip.bounds.right <= stack.right, "$chip above $stack")
        assertEquals(null, scene(board { }).dayNightChip, "neither day nor night yet")
    }

    private fun render(play: PlayState, table: TableBoard, landscape: Boolean = false, runtime: TableRuntimeHooks = TableRuntimeHooks()) {
        val toolkit = TableToolkitController(f.a, { table }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            EngineFixtureTable(play, f.a) {
                TableSurface(f.game, table, f.a, landscape, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = runtime)
            }
        } }
        compose.waitForIdle()
    }

    private val shotPlay = withEmblems(f.initial)
    private fun allStates() = board(shotPlay) { playerStates(); dungeon() }

    // TRN-072, TRN-073, TRN-053, TRN-069: with chips, emblems and a dungeon together
    @Test fun playerChipsPortrait() {
        render(shotPlay, allStates())
        compose.onRoot().captureShot("$SCREENSHOTS/states/player-chips-portrait.png",
            "Portrait table where each player has states, shown as round chips with a letter before their emblems: M, S2, R2 and P3 (Monarch, " +
                "Speed 2, Ring 2/4, Poison 3) for you; a door for the dungeon room, then I, C, R4 and E5 (Initiative, City's blessing, Ring 4/4, " +
                "Energy 5) for the opponent. A moon chip for Night sits above the Stack.")
    }

    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun playerChipsLandscape() {
        render(shotPlay, allStates(), landscape = true)
        compose.onRoot().captureShot("$SCREENSHOTS/states/player-chips-landscape.png",
            "Landscape table with the same states: each player's round chips in one line after their piles in their bar row, the opponent's " +
                "starting with the door of their dungeon room, the emblems last; the moon chip for Night sits above the Stack.")
    }

    // LOB-136, TRN-072 (owner gallery review 2026-11-09): on a narrow phone too the other player's chips stay in their row (they
    // used to lie under the life square): the bubble under the square lies clear of them; a tap on it reads it, holding opens the chat
    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    @Test fun aChatBubbleLiesClearOfStateChipsOnANarrowPhone() {
        var read: String? = null
        var opened: String? = null
        render(shotPlay, allStates(), runtime = TableRuntimeHooks(chatBubbles = TableChatBubbles(
            IncomingChatBubble("m1", "Thinking… give me a moment, I have a lot of triggers to order here", 1, 1), null, true, "Bob",
            read = { read = it }, open = { opened = it }, sentShown = {})))
        val bubble = compose.onNode(hasTestTag("chat-bubble"))
        val at = bubble.fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onAllNodes(hasTestTag(STATE_CHIP_TAG)).fetchSemanticsNodes().none { it.boundsInRoot.overlaps(at) }, "clear of the chips: $at")
        compose.onRoot().captureShot("$SCREENSHOTS/states/bubble-clear-of-chips.png",
            "A narrow 320 dp phone with Bob's unread message: its bubble lies right under his life square, over the top of his battlefield, " +
                "while his round chips (room, states and emblem) stay above it in his row right of his piles and his hand's fan.")
        bubble.performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("m1", opened, "holding it opens the chat, no chip's explanation")
        bubble.performClick()
        assertEquals("m1", read, "a tap reads it")
    }

    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    @Test fun playerChipsNarrowScreen() {
        render(shotPlay, allStates())
        compose.onRoot().captureShot("$SCREENSHOTS/states/player-chips-narrow.png",
            "A narrow 320 dp phone with the same states, emblems and a dungeon: each player's room, states and emblems are round chips of one size, " +
                "over two lines right of their piles (which scroll under them), none overlapping and all shown, clear of the hands.")
    }

    // TRN-072, TBL-132: holding a chip explains it until the finger lifts; a tap does nothing
    @Test fun holdingAChipExplainsIt() {
        render(shotPlay, allStates())
        compose.mainClock.autoAdvance = false
        val chip = compose.onAllNodesWithTag(STATE_CHIP_TAG).filter(hasContentDescription("Monarch")).onFirst()
        val text = RulesGlossary.state("monarch")!!.text
        compose.onNodeWithText(text).assertDoesNotExist()
        chip.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(text).assertExists()
        captureShot("$SCREENSHOTS/states/player-chip-explained.png",
            "Holding the round M chip: a dark popup above it says what being the monarch means, in plain words, until the finger lifts.")
        chip.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    /** Alice's creatures with states and a restriction, for the pictures. */
    private fun stated() = board {
        mark(sentinel) { it.copy(marks = listOf(ObjectMark.RingBearer)) }
        mark(river) { it.copy(goadedBy = listOf(PlayerId(f.b)), marks = listOf(ObjectMark.Monstrous)) }
        mark(blocker) { it.copy(marks = listOf(ObjectMark.Suspected)) }
        player(f.a) { it.copy(ledger = mapOf("ring-tempted" to 2)) }
        setup.effects[EffectId("E1")] = EffectObject(EffectId("E1"), listOf(Mechanic.DoesntUntap), affects = null, locked = listOf(id(sentinel)),
            source = id(blocker), controller = PlayerId(f.b), timestamp = 900, createdTurn = 1, duration = Duration.UntilYourNextTurn)
    }

    // TBL-133: the state's word on the card's chip on the table
    @Test fun cardStateChips() {
        render(f.initial, stated())
        compose.onRoot().captureShot("$SCREENSHOTS/states/card-state-chips.png",
            "Portrait table where your Meadow Sentinel is your Ring-bearer, your River Adept is goaded and monstrous, and the opponent's Lantern Guide is " +
                "suspected: each chip under the title starts with the state's word, a little smaller where the word is long for the card.")
    }

    // TBL-133, TBL-134: Inspect lists the states and what effects tell it, with the change lines
    @Test fun cardStateInspect() {
        val table = stated()
        val groups = gameInspectGroups(table, f.a, ManualInspection(f.game.id, f.initial.id, sentinel.id, listOf(sentinel.faceId)), sentinel, names)
        compose.setContent { MaterialTheme { NoRipples { InspectContent(groups, ScrollState(0)) } } }
        compose.waitForIdle()
        assertEquals(listOf("Ring-bearer: legendary, can't be blocked by creatures with greater power; when it attacks, you draw a card, then discard a card",
            "Doesn't untap until Bob's next turn"), groups.first().changes.map { it.text }.take(2))
        captureShot("$SCREENSHOTS/states/card-state-inspect.png",
            "Holding your Ring-bearer: under the card the panel lists what it is (what a Ring-bearer gets at the Ring's level 2) and that it doesn't " +
                "untap until Bob's next turn.")
    }

    // TBL-132: pinned, a state's line explains the state on a tap, with its source
    @Test fun pinnedAStatesLineExplainsIt() {
        val groups = listOf(InspectGroup("card", emptyList(), listOf(InspectChange("Goaded by Bob: attacks each combat if able, and not Bob if it can"),
            InspectChange("Ring-bearer: legendary, can't be blocked by creatures with greater power", "The Ring"))))
        val swipe = InspectSwipe()
        compose.setContent { MaterialTheme { NoRipples { InspectContent(groups, ScrollState(0), swipe) } } }
        compose.runOnIdle { swipe.moved(-200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        compose.onNodeWithText("Ring-bearer: legendary, can't be blocked by creatures with greater power").performClick()
        compose.onNodeWithText("From The Ring").assertExists()
        compose.onNodeWithText(RulesGlossary.state("ring-bearer")!!.text).assertExists()
    }

    // ZON-094: a plotted card in exile says so on its chip and in Inspect; a card without one stays as it was
    @Test fun aStateInExile() {
        val exile = f.id("a/EXILE")
        val play = f.initial.copy(board = f.initial.board.copy(
            cards = f.initial.board.cards.map { if (it.id == sentinel.id) it.copy(containerId = exile) else it },
            containers = f.initial.board.containers.map { when {
                it.id == exile -> it.copy(cards = it.cards + sentinel.id)
                sentinel.id in it.cards -> it.copy(cards = it.cards - sentinel.id)
                else -> it
            } }))
        val card = play.board.cards.first { it.id == sentinel.id }
        val table = board(play) { mark(card) { it.copy(marks = listOf(ObjectMark.Plotted)) } }
        assertEquals(listOf("Plotted"), table.values!!.computed.exileStates[card.id])
        assertEquals("Plotted", changes(table, card).first().text)
        assertEquals(emptyMap(), board(play) { }.values!!.computed.exileStates)
    }
}
