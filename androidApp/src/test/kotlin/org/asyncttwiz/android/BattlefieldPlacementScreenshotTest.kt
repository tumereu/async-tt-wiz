package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A board in Alice's main phase, both battlefields laid out by themselves (Arena style): Alice's four Mountains (two
 * tapped) fanned as one stack, a Cinder Hound wearing an Ember Blade and an Ember Ward, two more Cinder Hounds in a stack,
 * three Elemental tokens (×3) from the left of her upper band, and a Spark Totem and a Quiet Banner from its right; Bob's
 * three Mountains (one tapped), two Ember Cubs, two Elemental tokens and a Quiet Banner, his rows mirrored. With `bands`
 * Alice also has a Vault Ruins (an artifact land), a Grove Warden (a land creature), nine more different creatures and three
 * more non-creature permanents (TBL-004).
 */
internal object BattlefieldPlacementFixture {
    private fun invented(id: String, name: String, typeLine: String, manaCost: String = "", text: String = "", power: String = "",
        toughness: String = "", token: Boolean = false): SubmittedDefinition {
        val definition = CardDefinition(name, listOf(DefinitionFace(id, name, typeLine = typeLine, manaCost = manaCost,
            rulesText = text, power = power, toughness = toughness, scryfallId = placeholderPrinting(name))), token = token)
        return SubmittedDefinition(DeckJson.digest(definition), definition)
    }
    val mountain = invented("mountain", "Mountain", "Basic Land — Mountain")
    val hound = invented("hound", "Cinder Hound", "Creature — Elemental Dog", "{R}", "{R}: This creature gets +1/+0 until end of turn.", "2", "1")
    val cub = invented("cub", "Ember Cub", "Creature — Elemental Cat", "{R}", power = "2", toughness = "1")
    val blade = invented("blade", "Ember Blade", "Artifact — Equipment", "{1}", "Equipped creature gets +1/+0.\nEquip {0}")
    val ward = invented("ward", "Ember Ward", "Enchantment — Aura", "{W}", "Enchant creature\nEnchanted creature gets +0/+2.")
    val elemental = invented("elemental", "Elemental", "Token Creature — Elemental", power = "1", toughness = "1", token = true)
    val totem = invented("totem", "Spark Totem", "Artifact", "{2}", "{T}: This artifact deals 1 damage to any target.")
    val banner = invented("banner", "Quiet Banner", "Enchantment", "{1}")
    /** A free two-target spell, in Alice's hand only with [play]'s `snare` (TBL-130: targets in a group). */
    val snare = invented("snare", "Frost Snare", "Instant", "{0}", "Tap up to two target creatures.")
    val ruins = invented("ruins", "Vault Ruins", "Artifact Land", text = "{T}: Add {C}.")
    val warden = invented("warden", "Grove Warden", "Land Creature — Forest Dryad", "{G}", "{T}: Add {G}.", "1", "1")
    val drake = invented("drake", "Cinder Drake", "Creature — Drake", "{2}{R}", "Flying", "2", "2")
    val imp = invented("imp", "Ash Imp", "Creature — Imp", "{1}{R}", power = "1", toughness = "1")
    val golem = invented("golem", "Brass Golem", "Artifact Creature — Golem", "{3}", power = "3", toughness = "3")
    val wolf = invented("wolf", "Ember Wolf", "Creature — Elemental Wolf", "{1}{R}", power = "2", toughness = "2")
    val ogre = invented("ogre", "Kiln Ogre", "Creature — Ogre", "{3}{R}", power = "4", toughness = "3")
    val sprite = invented("sprite", "Flame Sprite", "Creature — Faerie", "{R}", "Flying", "1", "1")
    val crab = invented("crab", "Lava Crab", "Creature — Crab", "{1}{R}", power = "1", toughness = "4")
    val beetle = invented("beetle", "Coal Beetle", "Creature — Insect", "{2}", power = "2", toughness = "3")
    val rat = invented("rat", "Spark Rat", "Creature — Rat", "{B}", power = "1", toughness = "2")
    val lantern = invented("lantern", "Glow Lantern", "Artifact", "{1}")
    val sigil = invented("sigil", "Red Sigil", "Enchantment", "{R}")
    val vess = invented("vess", "Vess, Ember Sage", "Legendary Planeswalker — Vess", "{2}{R}{R}")

    const val HOST = "a-hound-1"
    const val SNARE = "a-snare"

    /** The cards' rules parsed from their text, as on a device (the test boards' engine state otherwise has printed values
     * only); undone by [AutomatumBoards.reset] and [unparsed]. */
    fun parsed() {
        AutomatumBoards.reset()
        AutomatumBoards.definitionSource = { defs -> defs.associate { s ->
            s.digest to org.asyncttwiz.protocol.game.GameCards.parse(s.definition, arcanum.parser.OracleParser::parseCard).let { p ->
                p.definition?.let { org.asyncttwiz.storage.ParsedCard.Read(it, p.manualAbilities) }
                    ?: org.asyncttwiz.storage.ParsedCard.Unreadable(p.problem.orEmpty())
            }
        } }
    }
    fun unparsed() { AutomatumBoards.definitionSource = null; AutomatumBoards.reset() }

    /** The board; with [snare] Alice holds a Frost Snare ([SNARE]). Her stack of identical Cinder Hounds holds [hounds]
     * (`a-hound-2`, `a-hound-3`, …), the one [tappedHound] (by id) tapped. */
    fun play(snare: Boolean = false, hounds: Int = 2, tappedHound: String? = null, bands: Boolean = false): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        var stamp = 0L
        fun field(owner: String, vararg members: Triple<String, SubmittedDefinition, PlayCard.() -> PlayCard>): List<PlayCard> =
            members.map { (id, d, change) -> PlayCard(id, d.digest, owner, "$owner/BATTLEFIELD", d.definition.faces.single().faceId,
                covered = false, token = d.definition.token, timestamp = ++stamp).change() }
        val plain: PlayCard.() -> PlayCard = { this }
        val tapped: PlayCard.() -> PlayCard = { copy(tapped = true) }
        val attached: PlayCard.() -> PlayCard = { copy(attachedTo = MtgRef.Card(HOST, 0)) }
        val alice = field("a",
            Triple("a-mountain-1", mountain, tapped), Triple("a-mountain-2", mountain, plain),
            Triple(HOST, hound, plain), Triple("a-blade", blade, attached), Triple("a-ward", ward, attached),
            Triple("a-mountain-3", mountain, plain), Triple("a-hound-2", hound, plain), Triple("a-totem", totem, plain),
            *(3..hounds + 1).map { n -> Triple("a-hound-$n", hound, if ("a-hound-$n" == tappedHound) tapped else plain) }.toTypedArray(),
            Triple("a-mountain-4", mountain, tapped), Triple("a-banner", banner, plain),
            Triple("a-elemental-1", elemental, plain), Triple("a-elemental-2", elemental, plain), Triple("a-elemental-3", elemental, plain),
            *(if (!bands) emptyArray() else arrayOf(Triple("a-ruins", ruins, plain), Triple("a-drake", drake, plain),
                Triple("a-lantern", lantern, plain), Triple("a-warden", warden, plain), Triple("a-imp", imp, plain),
                Triple("a-golem", golem, plain), Triple("a-sigil", sigil, plain), Triple("a-wolf", wolf, plain),
                Triple("a-vess", vess, plain), Triple("a-ogre", ogre, plain), Triple("a-sprite", sprite, plain),
                Triple("a-crab", crab, plain), Triple("a-beetle", beetle, plain), Triple("a-rat", rat, plain))))
        val bob = field("b",
            Triple("b-mountain-1", mountain, plain), Triple("b-cub-1", cub, plain), Triple("b-mountain-2", mountain, tapped),
            Triple("b-banner", banner, plain), Triple("b-cub-2", cub, plain), Triple("b-mountain-3", mountain, plain),
            Triple("b-elemental-1", elemental, plain), Triple("b-elemental-2", elemental, plain))
        cards += alice + bob
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val id = "$owner/$zone"
            val members = when (zone) {
                PlayZone.BATTLEFIELD -> (if (owner == "a") alice else bob).map { it.id }
                PlayZone.LIBRARY -> (0 until 20).map { n -> "$owner-library-$n" }.onEach { cards += PlayCard(it, mountain.digest, owner, id, "mountain") }
                PlayZone.HAND -> if (snare && owner == "a") listOf(SNARE).onEach { cards += PlayCard(it, this.snare.digest, owner, id, "snare") }
                    else emptyList()
                else -> emptyList()
            }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return PlayState(TableFixture.gameId, TableFixture.playId, "round", "request", PlayRoot("root", "branch"),
            BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), cards, containers,
                TurnState("a", "4", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)),
            listOf(mountain, hound, cub, blade, ward, elemental, totem, banner) + listOfNotNull(this.snare.takeIf { snare }) +
                (if (bands) listOf(ruins, warden, drake, imp, golem, wolf, ogre, sprite, crab, beetle, rat, lantern, sigil, vess)
                    else emptyList()),
            emptyList(), InitialStarter(StarterChoice("a"), "a"))
    }
}

/** TBL-004, TBL-053, TBL-117, TBL-122 (owner 2026-11-05): the battlefields laid out by themselves, Arena style, for the
 * owner's review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class BattlefieldPlacementScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = BattlefieldPlacementFixture.also { it.parsed() }
    /** The board drawn (set before [render]). */
    private var play = f.play()

    @After fun forget() = f.unparsed()

    private fun render(camera: TableCamera = TableCamera()): TableToolkitController {
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { engineTableBoard(play, "a") }, { true }, { _, _ -> true })
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = camera)
        compose.setContent { MaterialTheme { NoRipples {
            EngineFixtureTable(play, "a") {
                TableSurface(TableFixture.game, board, "a", landscape, Modifier.fillMaxSize(), connected = true,
                    onIntent = toolkit::handle, toolkit = toolkit, runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}))
            }
        } } }
        compose.waitForIdle()
        return toolkit
    }

    /** The screen's size in dp (set before [render] for another orientation). */
    private var screen = 412f to 892f
    private val landscape get() = screen.first > screen.second

    private fun scene(toolkit: TableToolkitController) = TablePresentation.scene(TableFixture.game, engineTableBoard(play, "a"), "a",
        screen.first, screen.second, landscape, toolkit.layoutState.value, landscapeHome = true)

    /** Taps the screen where [cardId] shows on the table. */
    private fun tap(toolkit: TableToolkitController, cardId: String) {
        val scene = scene(toolkit)
        val item = scene.items.single { it.cardId == cardId && it.battlefieldCard }
        val point = item.visible.flatMap { b -> listOf(b.center, TablePoint(b.x + 2f, b.y + 2f)) }.first { scene.hit(it)?.id == item.id }
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(Offset(point.x, point.y)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    // TBL-053, TBL-054, TBL-027 (owner, 2026-11-08): the Mountain played this turn joined its stack at once, under the tapped
    // ones, which lie on top
    @Test fun aNewLandJoinsItsStackUnderTheTappedOnes() {
        play = f.play().let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map {
            if (it.id == "a-mountain-3") it.copy(enteredTurn = p.board.turn.number) else it })) }
        val toolkit = render()
        val scene = scene(toolkit)
        assertEquals(listOf("a-mountain-2", "a-mountain-3", "a-mountain-1", "a-mountain-4"),
            scene.battlefieldGroups.single { it.key == "stack:a-mountain-1" }.members)
        val own = scene.fields.last()
        val center = scene.world!!.viewport.center
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(own.originX + 80f - center.x,
            own.gridBounds.bottom - 24f - center.y, 3f))
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/pile-tapped-on-top.png",
            "Alice's land row zoomed in: she played the third Mountain this turn (its name in blue) and it joined the stack at once; of the fan from the left " +
                "the two untapped Mountains lie under the two tapped ones, which are turned and on top.")
    }

    // TBL-004, TBL-053, TBL-117, TBL-122: lands from the left in the land row by each player's edge; in the upper band
    // creatures from the left and the other permanents from the right, two-slot columns filled nearest the middle first;
    // identical permanents in one stack (lands fanned with their tapped members turned, tokens tight with ×3); the Blade
    // and the Ward peek out above the Hound they're on; the Hounds' stack glows cyan (a tap acts at once), the equipped
    // Hound white (a tap opens it)
    @Test fun bothBattlefieldsLayThemselvesOut() {
        val toolkit = render()
        val scene = scene(toolkit)
        val groups = scene.battlefieldGroups.associateBy { it.key }
        assertEquals(listOf("a-mountain-2", "a-mountain-3", "a-mountain-1", "a-mountain-4"), groups.getValue("stack:a-mountain-1").members)
        assertEquals(listOf(f.HOST, "a-blade", "a-ward"), groups.getValue("host:${f.HOST}").members)
        assertEquals(3, scene.items.single { it.cardId == "a-elemental-3" }.copies)
        val board = engineTableBoard(play, "a")
        assertEquals(GroupTap.Expand, toolkit.groupTap(board, groups.getValue("host:${f.HOST}")))
        // TBL-130: the topmost member able to (the Mountains' top one is tapped)
        assertEquals(GroupTap.Act("a-hound-3"), toolkit.groupTap(board, groups.getValue("stack:a-hound-2")))
        assertEquals(GroupTap.Act("a-mountain-3"), toolkit.groupTap(board, groups.getValue("stack:a-mountain-1")))
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/both-sides.png",
            "Alice's main phase, both battlefields laid out by themselves: by each player's edge the land row, the Mountains " +
                "fanned as one stack from the left (tapped ones turned inside it); above it the two-row upper band with the " +
                "creatures from the left in columns of two, nearest the middle first (Alice's Cinder Hound with an Ember Blade " +
                "and an Ember Ward peeking out above it, behind it two Cinder Hounds in one stack, then three Elemental tokens " +
                "in a tight stack marked ×3; Bob's two Ember Cubs and two Elementals, ×2 each) and the Spark Totem and Quiet " +
                "Banner from the right. The Hounds' stack and the Totem glow cyan (a tap acts at once), the equipped Hound white " +
                "(a tap opens it: the Hound and its Blade can both be used).")
        // Zoomed in on the left half of Alice's battlefield
        val own = scene.fields.last()
        val center = scene.world!!.viewport.center
        toolkit.layoutState.value = toolkit.layoutState.value.copy(camera = TableCamera(own.originX + 110f - center.x,
            own.gridBounds.center.y - center.y, 2f))
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/own-side-zoomed.png",
            "The same board zoomed in on Alice's battlefield: the four Mountains fanned in one stack with the two tapped ones " +
                "turned inside it, the Cinder Hound glowing white with its Ember Blade and Ember Ward peeking out above it, the " +
                "two-Hound stack glowing cyan, and the ×3 Elemental tokens.")
    }

    // TBL-004, TBL-003, TBL-120, SYS-041 (owner playtest 2026-11-07): every land in the land row, the artifact land and the
    // land creature too; in the upper band the creatures fill two-slot columns from the left (the row nearest the middle
    // first) and the other permanents from the right; the creatures need more room than the screen's width, so the others
    // lie further right, a small margin after the last creature column
    @Test fun theUpperBandFillsColumnsFromBothEnds() {
        play = f.play(bands = true)
        val toolkit = render()
        val scene = scene(toolkit)
        val own = scene.fields.last()
        fun item(id: String) = scene.items.single { it.cardId == id && it.battlefieldCard }
        fun row(id: String) = ((own.gridBounds.bottom - item(id).footprint.center.y) / own.rowHeight).toInt()
        for (land in listOf("a-mountain-1", "a-ruins", "a-warden")) assertEquals(BattlefieldRow.LAND_ROW, row(land), land)
        assertEquals(BattlefieldRow.BAND_FRONT, row(f.HOST)); assertEquals(BattlefieldRow.BAND_BACK, row("a-hound-2"))
        assertEquals(BattlefieldRow.BAND_FRONT, row("a-totem")); assertEquals(BattlefieldRow.BAND_BACK, row("a-banner"))
        assertEquals(BattlefieldRow.BAND_FRONT, row("a-lantern"))
        val lastCreature = listOf("a-beetle", "a-rat").maxOf { item(it).footprint.right }
        val firstOther = listOf("a-vess", "a-sigil").minOf { item(it).footprint.x }
        assertTrue(firstOther > lastCreature, "the others after the creatures, a margin between")
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/bands.png",
            "Alice's battlefield with many permanents: her land row by her edge holds the Mountains, the Vault Ruins (an " +
                "artifact land) and the Grove Warden (a land creature); above it the upper band's two rows: her creatures fill " +
                "columns of two from the left, nearest the middle first (the equipped Cinder Hound, the Hound stack behind it, " +
                "the Elementals, Cinder Drake, Ash Imp, Brass Golem, Ember Wolf, Kiln Ogre, Flame Sprite, Lava Crab, Coal " +
                "Beetle, Spark Rat), and her other permanents from the " +
                "right (Spark Totem, Quiet Banner behind it, Glow Lantern, Red Sigil, Vess); the creatures fill the screen's " +
                "width, so the others lie past its right edge, a small gap after the creatures.")
    }

    // TBL-122: the first tap on the equipped Hound opens its group in place, the Hound, the Blade and the Ward side by side
    // (the Hound and the Blade glowing cyan); the rest of the row moves along. A tap on the empty table closes it
    @Test fun aTapOpensAGroupThatCouldMeanSeveralThings() {
        val toolkit = render()
        val before = scene(toolkit).items.single { it.cardId == "a-elemental-3" }.bounds.x
        tap(toolkit, f.HOST)
        compose.runOnIdle {
            assertEquals("host:${f.HOST}", toolkit.expandedGroup)
            assertTrue(toolkit.abilityCard == null && toolkit.capture == null && toolkit.cast.castCard == null, "nothing used yet")
        }
        val opened = scene(toolkit)
        val members = listOf(f.HOST, "a-blade", "a-ward").map { id -> opened.items.single { it.cardId == id }.bounds }
        assertTrue(members.zipWithNext().all { (a, b) -> b.x > a.right }, "side by side: $members")
        assertTrue(opened.items.single { it.cardId == "a-elemental-3" }.bounds.x > before, "the row moved along")
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened.png",
            "After one tap on Alice's equipped Cinder Hound: its group lies open in place, the Hound, the Ember Blade and the " +
                "Ember Ward side by side, the Hound (its pump) and the Blade (its Equip) glowing cyan; the Hound stack behind it " +
                "stays, the tokens moved along to the right. The next tap on a member uses it.")
        val own = opened.fields.last()
        val empty = TablePoint(300f, own.gridBounds.bottom - 1.5f * own.rowHeight)
        assertEquals(null, opened.hit(empty), "empty table")
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(Offset(empty.x, empty.y)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(null, toolkit.expandedGroup, "a tap on the empty table closes it") }
    }

    /** The cards picked so far as targets of the open cast, by card id. */
    private fun picked(toolkit: TableToolkitController): List<String> = toolkit.cast.picked.map {
        (((it as arcanum.engine.state.TargetRef.Object).id) as arcanum.engine.state.ObjectId.Card).card.value }

    // TBL-130, STK-047 (owner 2026-11-07): while Alice picks the Frost Snare's two targets, a tap on the stack of two identical
    // Cinder Hounds opens it in place (the identical cards no longer count as one), each Hound glowing as a target; a tap
    // on a Hound picks it and the group stays open, a second tap unpicks it; the two Hounds are picked one tap each, and
    // once the targets are chosen the group closes
    @Test fun whileTargetsArePickedAGroupOpensAndEachMemberIsPickedByItsOwnTap() {
        play = f.play(snare = true)
        val toolkit = render()
        val stack = "stack:a-hound-2"
        compose.runOnIdle { assertTrue(toolkit.cast.start(f.SNARE), "${toolkit.error}") }
        compose.waitForIdle()
        assertTrue(toolkit.cast.targeting, "the Snare's targets are picked on the table")
        val groups = scene(toolkit).battlefieldGroups.associateBy { it.key }
        assertEquals(GroupTap.Expand, toolkit.groupTap(engineTableBoard(play, "a"), groups.getValue(stack)), "a tap opens it")
        assertEquals(GroupTap.Top, toolkit.groupTap(engineTableBoard(play, "a"), groups.getValue("stack:a-mountain-1")),
            "no target in it: its top card's tap (a shake)")
        tap(toolkit, "a-hound-3")
        compose.runOnIdle {
            assertEquals(stack, toolkit.expandedGroup)
            assertEquals(emptyList(), picked(toolkit), "the first tap only opens it")
        }
        val opened = scene(toolkit)
        val hounds = listOf("a-hound-2", "a-hound-3").map { id -> opened.items.single { it.cardId == id && it.battlefieldCard }.bounds }
        assertTrue(hounds[1].x > hounds[0].right, "side by side: $hounds")
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened-targets.png",
            "Alice casts Frost Snare (tap up to two target creatures) and taps her stack of two identical Cinder Hounds: while " +
                "targets are picked a tap always opens a group, so the two Hounds lie side by side, each framed in violet as " +
                "a target, for each to be picked by a tap of its own; the target bar waits above her hand.")
        tap(toolkit, "a-hound-2")
        compose.runOnIdle {
            assertEquals(listOf("a-hound-2"), picked(toolkit))
            assertEquals(stack, toolkit.expandedGroup, "it stays open while targets are picked from it")
        }
        tap(toolkit, "a-hound-2")
        compose.runOnIdle {
            assertEquals(emptyList(), picked(toolkit), "a tap on a picked Hound unpicks it")
            assertEquals(stack, toolkit.expandedGroup)
        }
        tap(toolkit, "a-hound-2")
        compose.runOnIdle { assertEquals(listOf("a-hound-2"), picked(toolkit)) }
        tap(toolkit, "a-hound-3")
        compose.runOnIdle {
            assertTrue(!toolkit.cast.targeting, "both targets chosen: ${toolkit.error}")
            assertEquals(null, toolkit.expandedGroup, "the group closes once the targets are chosen")
        }
    }

    /** Starts the Frost Snare, taps [tapOn] to open the group [group] and checks it lies open: [members] side by side in
     * that order, wholly on the screen, no other battlefield card overlapping them, and the rest of its row moved along
     * ([moved] further from the group than before; null: nothing after it in its row). */
    private fun openForTargets(toolkit: TableToolkitController, group: String, tapOn: String, members: List<String>, moved: String?) {
        compose.runOnIdle { assertTrue(toolkit.cast.start(f.SNARE), "${toolkit.error}") }
        compose.waitForIdle()
        assertTrue(toolkit.cast.targeting, "the Snare's targets are picked on the table")
        fun spot(id: String) = scene(toolkit).items.single { it.cardId == id && it.battlefieldCard }.footprint
        fun gap() = moved?.let { Math.abs(spot(it).center.x - spot(members.first()).center.x) } ?: 0f
        val gapBefore = gap()
        tap(toolkit, tapOn)
        compose.runOnIdle {
            assertEquals(group, toolkit.expandedGroup)
            assertEquals(emptyList(), picked(toolkit), "the first tap only opens it")
        }
        val spots = members.map(::spot)
        assertTrue(spots.zipWithNext().all { (a, b) -> b.x >= a.right - 0.01f }, "side by side: $spots")
        assertTrue(spots.all { it.x >= 0f && it.right <= screen.first && it.y >= 0f && it.bottom <= screen.second }, "on screen: $spots")
        val others = scene(toolkit).items.filter { it.battlefieldCard && it.cardId !in members }
        assertTrue(others.none { o -> spots.any { s -> s.intersection(o.footprint).let { it != null && it.width > 0.01f } } }, "nothing overlaps the opened group")
        if (moved != null) assertTrue(gap() > gapBefore, "the row moved along")
    }

    // TBL-130, TBL-002: while Alice picks the Frost Snare's targets, a tap on Bob's stack of two Ember Cubs opens it in
    // place on his side, the two Cubs side by side, each glowing as a target; his Elementals stay behind them
    @Test fun whileTargetsArePickedAnOpenedGroupOfTwo() {
        play = f.play(snare = true)
        val toolkit = render()
        openForTargets(toolkit, "stack:b-cub-1", "b-cub-2", listOf("b-cub-1", "b-cub-2"), moved = null)
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened-targets-2.png",
            "Alice casts Frost Snare (tap up to two target creatures) and taps Bob's stack of two Ember Cubs: it opens in place " +
                "on his side, the two Cubs side by side, each framed in violet as a target, his Elementals behind them.")
    }

    // TBL-130, TBL-002: while targets are picked, a tap on Alice's stack of three identical Cinder Hounds opens it in place,
    // the three side by side, each glowing as a target; her Elemental tokens move along
    @Test fun whileTargetsArePickedAnOpenedGroupOfThree() {
        play = f.play(snare = true, hounds = 3)
        val toolkit = render()
        openForTargets(toolkit, "stack:a-hound-2", "a-hound-3", listOf("a-hound-2", "a-hound-3", "a-hound-4"),
            moved = "a-elemental-3")
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened-targets-3.png",
            "Alice casts Frost Snare (tap up to two target creatures) and taps her stack of three identical Cinder Hounds: it " +
                "opens in place, the three Hounds side by side, each framed in violet as a target, her Elemental tokens moved " +
                "along to the right.")
    }

    // TBL-130: while targets are picked, a tap on Alice's equipped Cinder Hound opens its group though it holds one target:
    // the Hound glows as a target, its Ember Blade and Ember Ward (no creatures) are dimmed
    @Test fun whileTargetsArePickedAnOpenedGroupDimsWhatIsNoTarget() {
        play = f.play(snare = true)
        val toolkit = render()
        openForTargets(toolkit, "host:${f.HOST}", f.HOST, listOf(f.HOST, "a-blade", "a-ward"), moved = "a-elemental-3")
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened-targets-dimmed.png",
            "Alice casts Frost Snare (tap up to two target creatures) and taps her Cinder Hound wearing an Ember Blade and an " +
                "Ember Ward: its group opens in place, the Hound framed in violet as a target and the Blade and the Ward, no " +
                "creatures, dimmed; the Hound stack behind it stays, the tokens moved along to the right.")
    }

    // TBL-130, TBL-002, STK-047: while targets are picked, a tap on Alice's stack of six identical Cinder Hounds (one of
    // them tapped) opens it in place, the six side by side, the tapped one turned in its own slot; a tap on one picks it
    // and the group stays open
    @Test fun whileTargetsArePickedAnOpenedGroupOfSix() = groupOfSix(landscape = false)

    // TBL-130, TBL-002, STK-047: the same in landscape
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun whileTargetsArePickedAnOpenedGroupOfSixLandscape() =
        groupOfSix(landscape = true)

    private fun groupOfSix(landscape: Boolean) {
        if (landscape) screen = 892f to 412f
        play = f.play(snare = true, hounds = 6, tappedHound = "a-hound-5")
        val toolkit = render()
        openForTargets(toolkit, "stack:a-hound-2", "a-hound-3", listOf(2, 3, 4, 6, 7, 5).map { "a-hound-$it" }, moved = "a-elemental-3")
        tap(toolkit, "a-hound-4")
        compose.runOnIdle {
            assertEquals(listOf("a-hound-4"), picked(toolkit))
            assertEquals("stack:a-hound-2", toolkit.expandedGroup, "it stays open while targets are picked from it")
            // STK-047: the picked member is drawn picked, every member framed, as cards alone
            assertEquals(setOf("a-hound-4"), toolkit.cast.pickedGlow(engineTableBoard(play, "a")))
            assertTrue((2..7).all { "a-hound-$it" in toolkit.cast.glow(engineTableBoard(play, "a")) }, "every Hound framed")
        }
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/group-opened-targets-6${if (landscape) "-landscape" else ""}.png",
            "${if (landscape) "In landscape, " else ""}Alice casts Frost Snare (tap up to two target creatures), taps her stack " +
                "of six identical Cinder Hounds (one tapped) and then the third Hound: the stack lies open in place, the six " +
                "Hounds side by side (the tapped one turned in its own slot at the right end, TBL-053), each framed in violet as a target, the third " +
                "one picked (filled violet with a white check, the main button's None turned Done); her Elemental tokens moved along to " +
                "the right." + if (landscape) " The target bar covers the left of her land row (her Mountains)." else "")
    }

    // STK-047 (owner 2026-11-07): a card alone shows the same marks as an opened group's member: framed in violet as a
    // target, and once picked also filled violet with a white check
    @Test fun whileTargetsArePickedAPickedCardAloneIsMarked() {
        play = f.play(snare = true, hounds = 1)
        val toolkit = render()
        compose.runOnIdle { assertTrue(toolkit.cast.start(f.SNARE), "${toolkit.error}") }
        compose.waitForIdle()
        assertEquals(listOf("a-hound-2"), scene(toolkit).battlefieldGroups.single { "a-hound-2" in it.members }.members, "a card alone")
        tap(toolkit, "a-hound-2")
        compose.runOnIdle {
            assertEquals(listOf("a-hound-2"), picked(toolkit), "a tap on a card alone picks it")
            assertEquals(setOf("a-hound-2"), toolkit.cast.pickedGlow(engineTableBoard(play, "a")))
        }
        compose.onRoot().captureShot("$SCREENSHOTS/battlefield/target-picked-alone.png",
            "Alice casts Frost Snare (tap up to two target creatures) and taps her Cinder Hound standing alone: it is picked, " +
                "framed in violet and filled violet with a white check at its top right; the groups holding other targets " +
                "(her equipped Hound, her Elemental tokens, Bob's Cubs and Elementals) are each framed in violet as a whole, a tap opening them.")
    }

    // TBL-130: while targets are picked a tap elsewhere closes the opened group: a pick from it stays picked, a tap on
    // another group (Bob's two Ember Cubs) opens that one instead, and a tap on the empty table closes it, the target
    // choice going on
    @Test fun whileTargetsArePickedATapElsewhereClosesTheGroup() {
        play = f.play(snare = true)
        val toolkit = render()
        compose.runOnIdle { assertTrue(toolkit.cast.start(f.SNARE), "${toolkit.error}") }
        compose.waitForIdle()
        tap(toolkit, "a-hound-3")
        tap(toolkit, "a-hound-2")
        compose.runOnIdle { assertEquals("stack:a-hound-2", toolkit.expandedGroup) }
        tap(toolkit, "b-cub-2")
        compose.runOnIdle {
            assertEquals("stack:b-cub-1", toolkit.expandedGroup, "Bob's Cubs opened, the Hounds closed")
            assertEquals(listOf("a-hound-2"), picked(toolkit), "the Hound stays picked")
        }
        val own = scene(toolkit).fields.last()
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            click(Offset(300f, own.gridBounds.bottom - 1.5f * own.rowHeight)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(null, toolkit.expandedGroup, "a tap on the empty table closes it")
            assertTrue(toolkit.cast.targeting, "one more target may be picked")
        }
    }
}
