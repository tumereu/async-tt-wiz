package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
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
import kotlin.test.assertTrue

/**
 * TBL-233 to TBL-237 (owner, 2026-11-21): battlefield cards show their card types as small icons, and a tag for each subtype a
 * card the viewer can see refers to, on either player's permanents. Boards made by hand of invented cards (wording from real oracle
 * templates; the Lizards and Goblins as the e2e test cards are), with the engine state of the same cards ([EngineFixture]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardTypeTagsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** The cards' rules parsed from their text, as on a device (the test boards' engine state otherwise has printed values only);
     * the Trail Marshal's "for each saddled Mount you control", which the parser doesn't read yet, written in the definition model. */
    @Before fun parsed() {
        AutomatumBoards.reset()
        AutomatumBoards.definitionSource = { defs -> defs.associate { s ->
            if (s.definition.name == marshal.name) return@associate s.digest to org.asyncttwiz.storage.ParsedCard.Read(marshalDefinition, 0)
            s.digest to org.asyncttwiz.protocol.game.GameCards.parse(s.definition, arcanum.parser.OracleParser::parseCard).let { p ->
                p.definition?.let { org.asyncttwiz.storage.ParsedCard.Read(it, p.manualAbilities) }
                    ?: org.asyncttwiz.storage.ParsedCard.Unreadable(p.problem.orEmpty())
            }
        } }
    }

    @After fun forget() { AutomatumBoards.definitionSource = null; AutomatumBoards.reset() }

    private val marshalDefinition = arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Trail Marshal",
        arcanum.api.ManaCost.parse("{2}{R}"), types = listOf(arcanum.api.CardType.Creature),
        subtypes = listOf(arcanum.api.Subtype("Human"), arcanum.api.Subtype("Knight")), power = arcanum.api.PrintedNumber.Fixed(2),
        toughness = arcanum.api.PrintedNumber.Fixed(2), abilities = listOf(arcanum.api.Ability.Static(listOf(
            arcanum.api.Value.Count(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.And(listOf(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype("Mount")),
                arcanum.api.ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Saddled)),
                arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You))))).let { arcanum.api.Mechanic.ModifyPowerToughness(it, it) }),
            arcanum.api.Affected.Objects(arcanum.api.Objects.Self), text = "This creature gets +1/+1 for each saddled Mount you control.")))))

    private val a = TableStatesFixture.a
    private val b = TableStatesFixture.b
    private val game = TableStatesFixture.game

    private fun face(name: String, type: String, cost: String = "", text: String = "", pt: String = "", loyalty: String = "",
        defense: String = ""): DefinitionFace {
        val (power, toughness) = if (pt.isEmpty()) "" to "" else pt.substringBefore('/') to pt.substringAfter('/')
        return DefinitionFace(name.lowercase().replace(' ', '-'), name, typeLine = type, manaCost = cost, rulesText = text,
            power = power, toughness = toughness, loyalty = loyalty, defense = defense, corner = pt.ifEmpty { loyalty.ifEmpty { defense } },
            scryfallId = placeholderPrinting(name))
    }
    private val forest = face("Forest", "Basic Land — Forest")
    private val mountain = face("Mountain", "Basic Land — Mountain")
    private val chief = face("Dunescale Chief", "Creature — Lizard Warrior", "{0}", "Other Lizards you control get +1/+1.", "2/2")
    private val basker = face("Sunbaked Basker", "Creature — Lizard", "{0}", pt = "1/1")
    private val goblin = face("Cinder Goblin", "Creature — Goblin", "{0}", pt = "1/1")
    private val warcaller = face("Goblin Warcaller", "Creature — Goblin", "{0}", "Other Goblins you control get +1/+1.", "2/2")
    private val charger = face("Dustmane Charger", "Creature — Horse Mount", "{1}{R}",
        "Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)", "2/2")
    private val marshal = face("Trail Marshal", "Creature — Human Knight", "{2}{R}", "This creature gets +1/+1 for each saddled Mount you control.", "2/2")
    private val plow = face("Brass Plow", "Artifact Creature — Ox", "{2}", pt = "2/2")
    private val lantern = face("Copper Lantern", "Artifact", "{1}")
    private val ward = face("Aegis Ward", "Enchantment", "{G}")
    private val oracle = face("Ash Oracle", "Legendary Planeswalker — Chandra", "{R}", "[+1]: You gain 1 life.", loyalty = "3")
    private val wagon = face("Ironclad Wagon", "Artifact — Vehicle", "{2}", "Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)", "3/3")
    private val wanderer = face("Grove Wanderer", "Land Creature — Forest Dryad", pt = "1/1")
    private val mimic = face("Mirror Mimic", "Creature — Shapeshifter", "{1}{U}", "Changeling (This card is every creature type.)", "1/1")
    private val ranger = face("Thornfield Ranger", "Creature — Elf Scout", "{1}{G}", "This creature gets +1/+1 for each Forest you control.", "1/1")
    private val blade = face("Scalebound Blade", "Artifact — Equipment", "{1}", "Equipped creature gets +1/+1.\nEquip {1}")
    private val armorer = face("Armory Scout", "Creature — Human Soldier", "{1}{W}", "This creature gets +1/+0 for each Equipment you control.", "1/2")
    private val mechanic = face("Pit Mechanic", "Creature — Human Artificer", "{1}{R}", "This creature gets +1/+1 for each Vehicle you control.", "1/1")

    /** One card of a test board: [face] in [owner]'s [zone]. */
    private class Put(val owner: String, val zone: PlayZone, val face: DefinitionFace, val tapped: Boolean = false, val damage: String = "",
        val counters: List<TypedCounter> = emptyList(), val faceDown: Boolean = false, val token: Boolean = false)

    /** A board of [cards] (each library ten Mountains), Alice's precombat main phase of turn 4. */
    private fun board(cards: List<Put>): PlayState {
        fun id(value: String) = TableStatesFixture.id("tags/$value")
        val all = cards + listOf(a, b).flatMap { owner -> List(10) { Put(owner, PlayZone.LIBRARY, mountain) } }
        val definitions = all.map { it.face }.distinctBy { it.name }.associate { f ->
            f.name to CardDefinition(f.name, listOf(f)).let { SubmittedDefinition(DeckJson.digest(it), it) } }
        val containers = listOf(a, b).flatMap { owner -> PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
            PlayContainer(id("$owner/$zone"), zone, owner) } } + PlayContainer(id("stack"), PlayZone.STACK)
        val placed = mutableMapOf<String, Int>()
        val playCards = all.mapIndexed { n, put ->
            val container = containers.first { it.accountId == put.owner && it.zone == put.zone }
            val slot = placed.getOrPut(container.id) { 0 }.also { placed[container.id] = it + 1 }
            val battlefield = put.zone == PlayZone.BATTLEFIELD
            PlayCard(id("card/$n"), definitions.getValue(put.face.name).digest, put.owner, container.id, put.face.faceId,
                covered = !battlefield || put.faceDown, explicitlyCovered = put.faceDown, tapped = put.tapped, corner = put.face.corner,
                token = put.token, damage = put.damage, typedCounters = put.counters, timestamp = n + 1L,
                placement = if (battlefield) PlayPlacement(BoardPosition(slot, 0), id("card/$n")) else null)
        }
        return TableStatesFixture.initial.copy(definitions = definitions.values.toList(), board = BoardState(
            listOf(PlayPlayer(a, "20"), PlayPlayer(b, "20")), playCards,
            containers.map { c -> c.copy(cards = playCards.filter { it.containerId == c.id }.map { it.id }) },
            TurnState(a, "4", "PRECOMBAT_MAIN", "MAIN")))
    }

    private fun bf(owner: String, face: DefinitionFace, count: Int = 1, tapped: Boolean = false, damage: String = "",
        counters: List<TypedCounter> = emptyList(), faceDown: Boolean = false) =
        List(count) { Put(owner, PlayZone.BATTLEFIELD, face, tapped, damage, counters, faceDown) }
    private fun hand(owner: String, vararg faces: DefinitionFace) = faces.map { Put(owner, PlayZone.HAND, it) }

    /** TBL-233: a battlefield of every permanent type but battle, nothing referring to a subtype. */
    private val typesBoard by lazy { board(
        bf(a, forest, 3) + bf(a, wanderer) + bf(a, plow) + bf(a, basker, tapped = true) + bf(a, lantern) + bf(a, ward) + bf(a, oracle) + bf(a, wagon) +
        hand(a, forest, basker) +
        bf(b, mountain, 3) + bf(b, goblin, 2) + bf(b, charger) + bf(b, plow, damage = "1") + bf(b, ward) + hand(b, mountain, goblin)) }

    /** TBL-234, TBL-237: Alice holds a Lizard lord, Bob a Goblin lord; Lizards and Goblins on both battlefields. */
    private val tagsBoard by lazy { board(
        bf(a, forest, 3) + bf(a, basker, 2) + bf(a, goblin) + bf(a, plow) + hand(a, chief, forest) +
        bf(b, mountain, 3) + bf(b, basker) + bf(b, goblin, 2) + hand(b, warcaller, mountain)) }

    /** TBL-234: Alice's Trail Marshal counts saddled Mounts; Mounts on both sides, none saddled. */
    private val mountBoard by lazy { board(
        bf(a, mountain, 3) + bf(a, marshal) + bf(a, charger) + bf(a, basker) + hand(a, mountain) +
        bf(b, mountain, 3) + bf(b, charger) + bf(b, goblin) + hand(b, mountain)) }

    /** TBL-233, TBL-235: a crowded battlefield: many permanents of several types, tags on many, a changeling with more than two, damage
     * and counters. */
    private val crowdedBoard by lazy { board(
        bf(a, forest, 4) + bf(a, mountain, 2) + bf(a, wanderer) + bf(a, chief) + bf(a, basker, 2) + bf(a, mimic) + bf(a, marshal) +
        bf(a, charger, damage = "1") + bf(a, plow, counters = listOf(TypedCounter("+1/+1", 2, 1))) + bf(a, wagon) + bf(a, blade) +
        bf(a, oracle) + bf(a, ward) + bf(a, ranger) + bf(a, armorer) + bf(a, mechanic) + hand(a, warcaller, forest) +
        bf(b, mountain, 4) + bf(b, goblin, 2) + bf(b, basker) + bf(b, charger, tapped = true) + bf(b, lantern) + bf(b, wagon) +
        bf(b, mimic) + bf(b, face("Hidden Shape", "Creature — Lizard", "{R}", pt = "1/1"), faceDown = true) + hand(b, mountain)) }

    /** [viewer]'s table of [play], in the phone's portrait layout or ([desktop]) the desktop layout's, as the app draws it. */
    private fun render(play: PlayState, viewer: String = a, desktop: Boolean = false, closeOn: String? = null) {
        val toolkit = TableToolkitController(viewer, { engineTableBoard(play, viewer) }, { true }, { _, _ -> true })
        // Zoomed in as far as a pinch goes (TBL-010), centred on the card named [closeOn]
        if (closeOn != null) TablePresentation.scene(game, engineTableBoard(play, viewer), viewer, 412f, 892f, false).let { scene ->
            val card = scene.items.first { it.zone == PlayZone.BATTLEFIELD && it.label.startsWith("$closeOn, ") }.bounds.center
            val port = assertNotNull(scene.world).viewport.center
            toolkit.layoutState.value = TableLayoutState(TableCamera(card.x - port.x, card.y - port.y, zoom = TablePresentation.MAX_ZOOM))
        }
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    EngineFixtureTable(play, viewer) {
                        val board = engineTableBoard(play, viewer)
                        val hooks = TableRuntimeHooks(navigation = {}, chat = {}, history = {}, undo = { true }, controlsConnected = true)
                        if (desktop) CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop, LocalDesktopFrame provides DesktopFrame(false)) {
                            Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
                                DesktopTopRow(game, board, viewer, toolkit, reviewing = false, navigation = {}, returnCurrent = null,
                                    changes = null, dockOpen = false, toggleDock = {}, chat = null, chatUnread = 0)
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    TableSurface(game, board, viewer, true, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle,
                                        toolkit = toolkit, runtime = hooks)
                                }
                            }
                        } else Box(Modifier.fillMaxSize()) {
                            TableSurface(game, board, viewer, false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle,
                                toolkit = toolkit, runtime = hooks)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** [viewer]'s battlefield items of [play] (not combat or Stack copies), by card name. */
    private fun field(play: PlayState, viewer: String = a, appearance: CardAppearance = CardAppearance()): List<TableItem> {
        val board = engineTableBoard(play, viewer)
        val scene = TablePresentation.scene(game, board, viewer, 412f, 892f, false, appearance = appearance,
            computed = assertNotNull(board.values).computed)
        return scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && !it.projection }
    }
    private fun List<TableItem>.named(name: String, owner: String) = filter { it.label.startsWith("$name, ") && it.owner == owner }

    // TBL-233, TBL-236: each permanent's card types, as icons and read aloud
    @Test fun battlefieldCardsShowTheirTypes() {
        val items = field(typesBoard)
        assertEquals(listOf("Artifact", "Creature"), items.named("Brass Plow", a).single().cardTypes)
        assertEquals(listOf("Land", "Creature"), items.named("Grove Wanderer", a).single().cardTypes)
        assertEquals(listOf("Planeswalker"), items.named("Ash Oracle", a).single().cardTypes)
        assertEquals(listOf("Artifact"), items.named("Ironclad Wagon", a).single().cardTypes, "an uncrewed Vehicle is no creature")
        assertTrue(items.named("Brass Plow", a).single().label.contains(", Artifact Creature"), "read aloud")
        assertTrue(items.all { it.subtypeTags.isEmpty() }, "no card refers to a subtype")
        render(typesBoard)
        captureShot("$SCREENSHOTS/battlefield/types-icons.png",
            "Alice's table with lands, a land creature, an artifact creature, a creature, an artifact, an enchantment, a planeswalker and an uncrewed Vehicle, and Bob's lands, creatures and enchantment: each battlefield card shows small dark icons of its card types at the left of its bottom row, and no subtype tags since no card refers to a subtype.")
    }

    // TBL-233 (WEB-015): the same on the desktop table, its cards larger
    @Test @Config(qualifiers = "w1440dp-h900dp-land-mdpi") fun battlefieldCardsShowTheirTypesOnTheDesktopTable() {
        render(typesBoard, desktop = true)
        captureShot("$SCREENSHOTS/desktop/types-icons.png",
            "The same board on Alice's desktop table: each battlefield card shows its card type icons at the left of its bottom row, larger with the desktop's cards.")
    }

    // TBL-234, TBL-235, TBL-237: Alice's Lizard lord in her hand tags the Lizards on both battlefields; Bob's Goblin lord in his hidden
    // hand tags nothing on her table
    @Test fun aCardInYourHandTagsItsSubtypeOnBothBattlefields() {
        val items = field(tagsBoard)
        assertTrue(items.named("Sunbaked Basker", a).all { it.subtypeTags == listOf("Lizard") }, "Alice's Lizards")
        assertEquals(listOf("Lizard"), items.named("Sunbaked Basker", b).single().subtypeTags, "Bob's Lizard too")
        assertTrue(items.named("Cinder Goblin", a).plus(items.named("Cinder Goblin", b)).all { it.subtypeTags.isEmpty() },
            "Bob's hand is hidden from Alice")
        assertTrue(items.named("Sunbaked Basker", b).single().label.endsWith(", Creature, Lizard tag"), items.named("Sunbaked Basker", b).single().label)
        // TBL-236: read before its counters
        val plow = items.named("Brass Plow", a).single().label
        assertTrue(plow.endsWith(", Artifact Creature"), plow)
        render(tagsBoard)
        captureShot("$SCREENSHOTS/battlefield/subtype-tags-alice.png",
            "Alice holds Dunescale Chief (\"Other Lizards you control get +1/+1\"): the Lizards on both battlefields show a small dark \"Lizard\" tag at their art's top right, while the Goblins show none, though Bob holds a Goblin lord in his hidden hand.")
    }

    // TBL-237: the same board on Bob's table: his Goblin lord tags the Goblins on both sides, Alice's hidden Lizard lord nothing
    @Test fun theOtherPlayersHiddenHandTagsNothing() {
        val items = field(tagsBoard, viewer = b)
        assertTrue(items.named("Cinder Goblin", a).plus(items.named("Cinder Goblin", b)).all { it.subtypeTags == listOf("Goblin") })
        assertTrue(items.named("Sunbaked Basker", a).plus(items.named("Sunbaked Basker", b)).all { it.subtypeTags.isEmpty() })
        render(tagsBoard, viewer = b)
        captureShot("$SCREENSHOTS/battlefield/subtype-tags-bob.png",
            "The same game on Bob's table: his Goblin Warcaller in hand tags the Goblins on both battlefields \"Goblin\", and the Lizards show no tag, as Alice's Lizard lord is in her hidden hand.")
    }

    // TBL-234: "for each saddled Mount" tags the Mounts on both sides while none is saddled
    @Test fun aSaddledMountCardTagsMountsBeforeAnyIsSaddled() {
        val items = field(mountBoard)
        assertEquals(listOf("Mount"), items.named("Dustmane Charger", a).single().subtypeTags)
        assertEquals(listOf("Mount"), items.named("Dustmane Charger", b).single().subtypeTags)
        render(mountBoard)
        captureShot("$SCREENSHOTS/battlefield/saddled-mount-tags.png",
            "Alice's Trail Marshal gets +1/+1 for each saddled Mount she controls: though no Mount is saddled, the Dustmane Chargers on both battlefields show a \"Mount\" tag.")
    }

    // TBL-233, TBL-235: a crowded battlefield; the changeling with three relevant creature types shows its first and "+2"
    @Test fun aCrowdedBattlefieldStaysReadable() {
        val items = field(crowdedBoard)
        val mimic = items.named("Mirror Mimic", a).single()
        assertEquals(listOf("Goblin", "Lizard", "Mount"), mimic.subtypeTags)
        assertEquals(listOf("Goblin", "+2"), SubtypeTags.shown(mimic.subtypeTags))
        assertEquals(listOf("Vehicle"), items.named("Ironclad Wagon", b).single().subtypeTags)
        assertEquals(listOf("Equipment"), items.named("Scalebound Blade", a).single().subtypeTags)
        assertTrue(items.named("Forest", a).all { it.subtypeTags == listOf("Forest") })
        assertEquals(listOf("Forest"), items.named("Grove Wanderer", a).single().subtypeTags)
        render(crowdedBoard)
        captureShot("$SCREENSHOTS/battlefield/types-tags-crowded.png",
            "A crowded table: Alice's many permanents (lands, creatures, an artifact creature with counters, a damaged Mount, a Vehicle, an Equipment, a planeswalker, an enchantment) and Bob's, each with its type icons; the subtypes her cards refer to (Lizard, Mount, Goblin, Forest, Equipment, Vehicle) tagged on both sides, her changeling, its changed type line on the chip under its title, showing one tag \"Goblin +2\" where only one fits.")
    }

    // TBL-233, TBL-235: the crowded battlefield zoomed in as far as a pinch goes
    @Test fun aCrowdedBattlefieldZoomedIn() {
        render(crowdedBoard, closeOn = "Trail Marshal")
        captureShot("$SCREENSHOTS/battlefield/types-tags-close.png",
            "The crowded table zoomed in as far as a pinch goes, around Alice's Trail Marshal: the type icons at the left of each card's bottom row beside its P/T and damage, and the subtype tags at the art's top right beside counters and the effects sparkle.")
    }

    // TBL-233, TBL-235 (WEB-015): the crowded battlefield on the desktop table
    @Test @Config(qualifiers = "w1440dp-h900dp-land-mdpi") fun aCrowdedBattlefieldOnTheDesktopTable() {
        render(crowdedBoard, desktop = true)
        captureShot("$SCREENSHOTS/desktop/types-tags-crowded.png",
            "The crowded board on Alice's desktop table: type icons and subtype tags on both battlefields, larger with the desktop's cards.")
    }

    // TBL-236: with full pictures on, and on a face-down card, the type icons sit on a small white chip
    @Test fun fullPicturesAndFaceDownCardsShowTheirTypesOnAChip() {
        val preferences = CardAppearancePreferences(org.asyncttwiz.appui.AppPlatform.current, AppLayout.Phone)
        preferences.fullPicture(hand = false, enabled = true)
        try {
        val appearance = preferences.read()
        val items = field(crowdedBoard, appearance = appearance)
        assertTrue(items.named("Brass Plow", a).single().fullPicture)
        val hidden = items.single { it.covered && it.owner == b }
        assertEquals(listOf("Creature"), hidden.cardTypes, "a face-down permanent is a creature (CR 708.2a)")
        assertEquals(emptyList(), hidden.subtypeTags, "it has no subtypes face down")
        render(crowdedBoard, closeOn = "Trail Marshal")
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/battlefield/types-full-picture.png",
            "With \"Battlefield cards: full picture\" on, zoomed in around Alice's Trail Marshal, the crowded table's cards show their printed pictures with their type icons on a small white chip at the bottom left and their subtype tags at the art's top right; Bob's face-down creature shows its creature icon on the same chip.")
        } finally {
            preferences.fullPicture(hand = false, enabled = false)
        }
    }
}
