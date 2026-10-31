package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardMenuLayoutTest {
    private val f = TableStatesFixture

    /** Cells CardActions builds itself; every other cell must come from the context's tools. */
    private val builtIn = setOf("inspect", "note", "otherSide", "flip", "switchFace", "owner", "corner", "resetCorner", "damage",
        "eotCorner", "eotNote", "resetCounters", "keywords", "eotKeywords") +
        listOf("LEFT", "RIGHT").flatMap { part -> listOf(1, -1).flatMap { listOf("corner/$part/$it", "eotCorner/$part/$it") } } +
        CounterColor.entries.map { "counter/${it.name}" }

    private fun ids(layout: CardMenuLayout, token: Boolean = false, attacker: Boolean = false) =
        CardRingPages.pages(layout, token, attacker).map { it.actions }

    // SYS-040, TBL-070, TBL-071, TBL-073, TBL-074, TBL-075, TBL-078, TBL-082
    @Test fun battlefieldLayoutHasExactCellsOnThreePagesWithTwins() {
        assertEquals(listOf("Everyday", "Counters", "More"), CardRingPages.pages(CardMenuLayout.CARD).map { it.name })
        val (everyday, counters, more) = ids(CardMenuLayout.CARD)
        assertEquals(listOf("inspect", "note", "corner", "Stack", "Declare without tapping", "flip", "switchFace",
            "Graveyard", "Exile", "Clone", "Ping / Unping", "Hand", "Library top", "Library bottom", "Shuffle into library",
            "eotCorner", "Battlefield"), everyday)
        // Row 4: Shuffle into library, the until-end-of-turn corner widget, Battlefield.
        assertEquals(listOf(0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18), CardRingPages.layout(everyday).map { it.cell })
        assertEquals(listOf("counter/RED", "counter/BLUE", "note", "counter/GREEN", "counter/YELLOW", "eotNote",
            "counter/PURPLE", "counter/GRAY", "resetCounters"), counters)
        assertEquals(listOf("owner", "otherSide", "Link", "Unlink",
            "corner/LEFT/1", "corner/LEFT/-1", "corner/RIGHT/1", "corner/RIGHT/-1", "resetCorner",
            "eotCorner/LEFT/1", "eotCorner/LEFT/-1", "eotCorner/RIGHT/1", "eotCorner/RIGHT/-1", "keywords", "eotKeywords",
            "Temp 1", "Temp 2", "Temp 3", "Temp 4", "Deal to target", "Deal to target or creatures", "damage"), more)
        assertEquals(21, CardRingPages.layout(more).last().cell)
        assertEquals(listOf("Counters"), CardRingPages.pages(CardMenuLayout.CARD).filter { "eotNote" in it.actions }.map { it.name })
        val twins = ids(CardMenuLayout.CARD, token = true, attacker = true)[0]
        assertEquals(everyday.map { when (it) { "Declare without tapping" -> "Attack →"; "Hand" -> "Remove token"; else -> it } }, twins)
        assertEquals(ids(CardMenuLayout.CARD).drop(1), ids(CardMenuLayout.CARD, token = true, attacker = true).drop(1))
    }

    // TBL-071, TBL-089, TBL-091, ZON-007
    @Test fun singlePageLayoutsHaveExactCells() {
        assertEquals(listOf(listOf("inspect", "note", "Ping / Unping", "Attack →", "Remove from combat", "Graveyard", "Exile", "Hand",
            "Deal to target", "Deal to target or creatures", "Link", "Links")), ids(CardMenuLayout.COMBAT))
        assertEquals(listOf(listOf("inspect", "Play", "Face down", "Reveal", "Battlefield", "Discard", "Exile", "Library top",
            "Library bottom", "Shuffle into library", "Temp 1", "Temp 2", "Temp 3", "Temp 4",
            "Discard random", "Reveal random", "Exile random", "Random to library bottom")), ids(CardMenuLayout.HAND))
        assertEquals(listOf(listOf("inspect", "otherSide", "Ping / Unping", "Hand", "Battlefield", "Move to Stack", "Graveyard", "Exile",
            "Library top", "Library bottom", "Shuffle into library", "Temp 1", "Temp 2", "Temp 3", "Temp 4")), ids(CardMenuLayout.PANEL))
        // Twins belong to the battlefield layout only.
        for (layout in CardMenuLayout.entries - CardMenuLayout.CARD)
            assertEquals(ids(layout), ids(layout, token = true, attacker = true))
    }

    // SYS-040, TBL-064, TBL-071
    @Test fun everyLayoutFitsItsPagesWithoutHolesOrDuplicates() {
        for (layout in CardMenuLayout.entries) for (token in listOf(false, true)) for (attacker in listOf(false, true)) {
            val pages = CardRingPages.pages(layout, token, attacker)
            assertTrue(pages.size == if (layout == CardMenuLayout.CARD || layout == CardMenuLayout.PANEL && !CardRingPages.panelLayout) 3 else 1)
            for (page in pages) {
                assertTrue(CardRingPages.fits(page.actions), "$layout ${page.name} overflows")
                assertEquals(page.actions.distinct(), page.actions)
                assertTrue(page.actions.none { it.startsWith("empty/") })
                val slots = CardRingPages.layout(page.actions)
                // Cells are contiguous: no blank cell inside a page.
                slots.zipWithNext().forEach { (a, b) -> assertEquals(a.cell + a.span, b.cell, "$layout ${page.name} hole before ${b.id}") }
                assertTrue(slots.all { it.cell % CardRingPages.columns + it.span <= CardRingPages.columns })
            }
        }
    }

    // TBL-071
    @Test fun contextPicksTheLayoutFromTheTappedView() {
        val play = f.combat()
        val board = play.board
        val sentinel = f.source(f.a, "1/3")
        assertEquals(CardMenuLayout.CARD, CardRingPages.context(board, sentinel.id))
        assertEquals(CardMenuLayout.COMBAT, CardRingPages.context(board, f.id("attacker1")))
        assertEquals(CardMenuLayout.COMBAT, CardRingPages.context(board, f.id("blocker")))
        // Stack entries have no card menu: Manage Stack holds their actions (STK-019).
        assertNull(CardRingPages.context(board, f.id("effect")))
        val stackCard = CombatStackRules.stack(board).cards.single { it != f.id("effect") }
        assertEquals(CardMenuLayout.CARD, CardRingPages.context(board, stackCard))
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a }
        assertEquals(CardMenuLayout.HAND, CardRingPages.context(board, hand.cards.first()))
        val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        assertEquals(CardMenuLayout.PANEL, CardRingPages.context(board, library.cards.first()))
        assertNull(CardRingPages.context(board, f.id("missing")))
        assertTrue(CardRingPages.declaredAttacker(board, listOf(sentinel.id)))
        assertFalse(CardRingPages.declaredAttacker(board, listOf(f.source(f.b, "2/3").id)))
    }

    private fun toolkit(play: PlayState, sent: MutableList<PlayAction> = mutableListOf()) =
        TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true }, playBatch = { actions, _ -> sent += actions; true })

    private fun tools(toolkit: TableToolkitController, play: PlayState, view: String): List<MenuControl> {
        toolkit.openCard(TableScope(view, listOf(view)))
        return cardContextTools(toolkit, play, assertNotNull(toolkit.capture), TableSelection(), emptyMap(), true, null)
    }

    @Test fun everyLayoutCellIsSuppliedAndNoRemovedEntryRemains() {
        val play = f.combat()
        val board = play.board
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a }
        val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        val views = listOf(f.source(f.a, "1/3").id, f.id("attacker1"), f.id("blocker"), hand.cards.first(), library.cards.first(),
            CombatStackRules.stack(board).cards.single { it != f.id("effect") })
        val removed = listOf("Select / deselect", "Tap selected scope", "Untap selected scope", "Source actions", "Temp pile")
        val dropZones = CombatStackPresentation.combat(board, 48f, 68f).destinations.map { it.label }
        assertTrue(dropZones.isNotEmpty())
        for (view in views) {
            val layout = assertNotNull(CardRingPages.context(board, view))
            val card = CombatStackRules.liveSource(board, view)!!
            val names = tools(toolkit(play), play, view).map { it.name }.toSet()
            val pages = CardRingPages.pages(layout, card.token, CardRingPages.declaredAttacker(board, listOf(card.id)))
            val missing = pages.flatMap { it.actions }.filter { it !in builtIn && it !in names }
            assertEquals(emptyList(), missing, "$layout misses cells")
            assertTrue(names.none { it in removed || it in dropZones }, "$layout still offers ${names.filter { it in removed || it in dropZones }}")
        }
    }

    // CMB-016, TBL-070, TBL-074
    @Test fun declaredAttackerRetargetsItsCopiesAndBlockerCopiesCannotAttack() {
        val play = f.combat()
        val toolkit = toolkit(play)
        val sentinel = f.source(f.a, "1/3")
        val real = tools(toolkit, play, sentinel.id)
        assertTrue(real.single { it.name == "Attack →" }.enabled)
        assertTrue(real.single { it.name == "Deal to target" }.enabled)
        real.single { it.name == "Attack →" }.click()
        assertEquals("attackTarget", toolkit.panel)
        assertEquals(listOf(f.id("attacker1")), toolkit.capture?.viewIds)
        val blocker = tools(toolkit(play), play, f.id("blocker"))
        assertFalse(blocker.single { it.name == "Attack →" }.enabled)
        assertTrue(blocker.single { it.name == "Remove from combat" }.enabled)
        val fighting = CombatStackRules.occurrences(play.board.combat).map { it.sourceId }
        val idle = play.board.cards.first { card -> card.owner == f.a && card.id !in fighting &&
            play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } }
        val notInCombat = tools(toolkit(play), play, idle.id)
        assertFalse(notInCombat.single { it.name == "Attack →" }.enabled)
        assertFalse(notInCombat.single { it.name == "Deal to target" }.enabled)
        assertTrue(notInCombat.single { it.name == "Temp 2" }.enabled)
    }

    // TBL-075
    @Test fun tokenLibraryCellsRemoveTheToken() {
        val base = f.combat()
        val sentinel = f.source(f.a, "1/3")
        val play = base.copy(board = base.board.copy(combat = null,
            cards = base.board.cards.map { if (it.id == sentinel.id) it.copy(token = true) else it }))
        for (name in listOf("Library top", "Library bottom", "Shuffle into library", "Remove token")) {
            val sent = mutableListOf<PlayAction>()
            val controls = tools(toolkit(play, sent), play, sentinel.id)
            val control = controls.single { it.name == name }
            assertTrue(control.enabled, name)
            control.click()
            assertEquals(listOf<PlayAction>(PlayAction.RemoveToken(sentinel.id)), sent, name)
        }
        assertEquals("Remove token", CardRingPages.pages(CardMenuLayout.CARD, token = true)[0].actions[11])
    }

    // SYS-040
    @Test fun projectionWhoseSourceLeftPlayKeepsTheLayoutWithSourceCellsDisabled() {
        val base = f.combat()
        val sentinel = f.source(f.a, "1/3")
        val play = base.copy(board = base.board.copy(cards = base.board.cards.filterNot { it.id == sentinel.id },
            containers = base.board.containers.map { it.copy(cards = it.cards - sentinel.id) }))
        val toolkit = toolkit(play)
        toolkit.openCard(TableScope(f.id("attacker1"), listOf(f.id("attacker1"))))
        assertEquals("occurrence", toolkit.panel)
        val controls = projectionMenuTools(toolkit, play, assertNotNull(toolkit.capture), emptyMap(), true, null).associateBy { it.name }
        val combat = CardRingPages.pages(CardMenuLayout.COMBAT).single().actions - setOf("inspect", "note")
        assertEquals(emptyList(), combat.filter { it !in controls })
        assertFalse(controls.getValue("Graveyard").enabled)
        assertFalse(controls.getValue("Exile").enabled)
        assertFalse(controls.getValue("Hand").enabled)
        assertTrue(controls.getValue("Remove from combat").enabled)
        assertTrue(controls.getValue("Attack →").enabled)
    }

    // TBL-048, ZON-003
    @Test fun holdingTheHandBackgroundPreviewsNothingButAHandCardStillDoes() {
        val play = f.initial
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val hand = scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.a }
        assertTrue(hand.cards.isNotEmpty())
        val background = (0..40).flatMap { x -> (0..10).map { y ->
            TablePoint(hand.bounds.x + hand.bounds.width * x / 40f, hand.bounds.y + hand.bounds.height * y / 10f) } }
            .first { scene.hit(it)?.id == hand.id }
        val events = mutableListOf<TableIntent>()
        val gesture = TableGestureCoordinator(events::add)
        gesture.update(scene, TableSelection(), true)
        gesture.down(background, 0); gesture.tick(700); gesture.up(background, 750)
        assertTrue(events.none { it is TableIntent.Peek }, "$events")
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND && it.owner == f.a && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        events.clear()
        gesture.down(point, 1000); gesture.tick(1700); gesture.up(point, 1750)
        assertEquals(listOf<TableIntent>(TableIntent.Peek(card.cardId), TableIntent.Peek(null)), events)
    }
}
