package org.asyncttwiz.android

import org.asyncttwiz.appui.nameUuid
import org.asyncttwiz.protocol.*
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// The table tests' boards, shared by the common tests (app-ui's commonTest, on the JVM and in the browser) and the
// Android tests (androidApp's screenshot and Robolectric tests): this folder is a source folder of both.

/** The render model ([TableBoard]) of a test board: what the table draws of [play]'s board (no engine state, no worked-out
 * values), for the table's drawing, layout and gesture tests. */
internal fun tableBoard(play: PlayState): TableBoard =
    TableBoard(play.gameId, play.id, play.root.id, play.board, play.definitions)

internal object TableFixture {
    // Real games have UUID ids.
    val gameId = nameUuid("game".encodeToByteArray())
    val playId = nameUuid("play".encodeToByteArray())
    val game = GameSummary(gameId, "Canvas Spark", "2026-10-23T00:00:00Z", AccountIdentity("a", "Alice"),
        AccountIdentity("b", "Bob"), "round", "1", InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = playId)
    val definition = CardDefinition("Secret name", listOf(DefinitionFace("face", "Secret face", rulesText = "Secret rules",
        scryfallId = placeholderPrinting("Secret name"))))
    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    fun container(owner: String, zone: PlayZone) = "$owner/$zone"
    fun play(hand: Int = 0, board: Int = 0, library: Int = 4): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val count = when (zone) { PlayZone.HAND -> hand; PlayZone.BATTLEFIELD -> board; PlayZone.LIBRARY -> library; else -> 0 }
            val id = container(owner, zone)
            val members = (0 until count).map { n -> "$owner/$zone/$n" }
            cards += members.map { PlayCard(it, submitted.digest, owner, id, "face", covered = zone != PlayZone.BATTLEFIELD) }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return PlayState(gameId, playId, "round", "request", PlayRoot("root", "branch"),
            BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), cards, containers, TurnState("a")), listOf(submitted),
            emptyList(), InitialStarter(StarterChoice("a"), "a"))
    }
    /** [play] with its battlefield cards told apart (each its own P/T corner), so none stack (TBL-053). */
    fun apart(play: PlayState): PlayState = play.copy(board = play.board.copy(cards = play.board.cards.map { card ->
        if (card.containerId.endsWith("BATTLEFIELD")) card.copy(corner = "${card.id.substringAfterLast('/')}/1") else card }))
    fun scene(play: PlayState = play(board = 3, hand = 2), landscape: Boolean = false,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap()) =
        TablePresentation.scene(game, tableBoard(play), "a", if (landscape) 900f else 412f, if (landscape) 412f else 820f,
            landscape, ui, placements)
}

internal object LiveTableFixture {
    val game = TableFixture.game.copy(gameName = "Crackling Mist 4")
    // The spells cost {G}: one with no mana cost can't be cast (CR 118.6), and Alice's untapped Forest pays any one of
    // them, so her hand glows as castable now (TBL-117)
    private val definitions = listOf(
        DefinitionFace("land", "Forest", typeLine = "Basic Land — Forest", scryfallId = placeholderPrinting("Forest")),
        DefinitionFace("sentinel", "Meadow Sentinel", typeLine = "Creature — Soldier", power = "1", toughness = "3", corner = "1/3",
            manaCost = "{G}", scryfallId = placeholderPrinting("Meadow Sentinel")),
        DefinitionFace("river", "River Adept", typeLine = "Creature — Wizard", power = "1", toughness = "2", corner = "1/2",
            manaCost = "{G}", scryfallId = placeholderPrinting("River Adept")),
        DefinitionFace("guide", "Lantern Guide", typeLine = "Creature — Scout", power = "2", toughness = "3", corner = "2/3",
            manaCost = "{G}", scryfallId = placeholderPrinting("Lantern Guide")),
        DefinitionFace("aegis", "Aegis Ward", typeLine = "Enchantment", manaCost = "{G}",
            scryfallId = placeholderPrinting("Aegis Ward"))
    ).map { face -> CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) } }

    fun play(hand: Int = 5): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val count = when (zone) {
                PlayZone.HAND -> if (owner == "a" || hand > 5) hand else 3
                PlayZone.BATTLEFIELD -> 5
                PlayZone.LIBRARY -> 50
                PlayZone.GRAVEYARD -> 2
                else -> 0
            }
            val id = "$owner/$zone"
            val members = (0 until count).map { "$id/$it" }
            members.forEachIndexed { n, cardId ->
                val definition = definitions[when (zone) {
                    PlayZone.BATTLEFIELD -> if (n < 2) 0 else n - 1
                    else -> n % definitions.size
                }]
                val face = definition.definition.faces.single()
                cards += PlayCard(cardId, definition.digest, owner, id, face.faceId,
                    covered = zone == PlayZone.LIBRARY || zone == PlayZone.HAND,
                    tapped = zone == PlayZone.BATTLEFIELD && n == 1, corner = face.corner,
                    placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(
                        BoardPosition(if (n < 2) n else n - 2, if (n < 2) 0 else 2), cardId) else null)
            }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return TableFixture.play().copy(definitions = definitions, board = BoardState(
            listOf(PlayPlayer("a", "20", mapOf(CounterColor.BLUE to "3", CounterColor.GRAY to "0")), PlayPlayer("b", "17")),
            cards, containers, TurnState("a", "4", "PRECOMBAT_MAIN", "MAIN")))
    }

    fun menuPlay(showcase: Boolean = false): PlayState {
        val play = play()
        val ids = play.board.cards.associate { it.id to nameUuid(it.id.encodeToByteArray()) }
        val cards = play.board.cards.map { it.copy(id = ids.getValue(it.id),
            placement = it.placement?.copy(pileId = ids.getValue(it.id))) }
        val shown = cards.filter { it.containerId == "a/LIBRARY" }.take(2).map { it.id }
        val normalized = play.copy(board = play.board.copy(cards = cards,
            containers = play.board.containers.map { it.copy(cards = it.cards.map(ids::getValue)) }))
        if (!showcase) return normalized
        val definition = CardDefinition("Soldier", listOf(DefinitionFace("soldier", "Soldier", typeLine = "Token Creature — Soldier", corner = "1/1",
            scryfallId = placeholderPrinting("Soldier"))))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val token = PlayCard(nameUuid("soldier".encodeToByteArray()), submitted.digest,
            "a", "a/BATTLEFIELD", "soldier", covered = false, corner = "1/1", token = true,
            placement = PlayPlacement(BoardPosition(3, 2), "soldier-pile"))
        return normalized.copy(definitions = normalized.definitions + submitted, board = normalized.board.copy(cards = cards + token,
                containers = normalized.board.containers.map { if (it.id == token.containerId) it.copy(cards = it.cards + token.id) else it },
                contentsDisplays = listOf(ContentsDisplay("reveal", "a", "a/LIBRARY", ContentsSelection.TOP, 2, true, shown))))
    }

    /** Twelve life changes as the timeline strip shows them (the engine table's events), Bob's every third. */
    fun timeline(): List<HistoryTimelineEvent> {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        return (1..12).map { n -> HistoryTimelineEvent(id(n), HistoryIcon.FAVORITE, "Life set to ${20 - n}",
            if (n % 3 == 0) "b" else "a", null, label = "Life") }
    }

    /** Timeline mode's panel of the engine table at [events]' event [at] (the latest by default, where entering it opens). */
    @androidx.compose.runtime.Composable
    fun ReviewPanel(viewer: String, landscape: Boolean, events: List<HistoryTimelineEvent> = timeline(), at: Int = events.lastIndex) {
        val routes = androidx.compose.runtime.remember {
            EngineTableRoutes(viewer, { null }, {}, { null }).apply { review = events[at].id }
        }
        EngineReviewPanel(routes, events, viewer, landscape, 240L)
    }
}

/** A stable provider printing id for a fixture card, so it has an artwork source like every real card; its placeholder card
 * images (androidApp's PlaceholderArtwork) show [name] (then each of [otherFaces] for the later faces) in the name bar. */
internal fun placeholderPrinting(name: String, vararg otherFaces: String): String = PlaceholderPrintings.printing(name, *otherFaces)

/** The fixture cards' printing ids and the face names their placeholder pictures show. */
@OptIn(ExperimentalAtomicApi::class)
internal object PlaceholderPrintings {
    private val names = AtomicReference(emptyMap<String, String>())

    fun printing(name: String, vararg otherFaces: String): String = nameUuid("printing:$name".encodeToByteArray()).also { id ->
        val faces = (listOf(name) + otherFaces).withIndex().associate { (face, faceName) -> "$id|$face" to faceName }
        while (true) {
            val current = names.load()
            if (names.compareAndSet(current, current + faces)) break
        }
    }

    /** The face name the placeholder picture of [printing]'s [face] shows, null for a printing not made here. */
    fun faceName(printing: String?, face: Int): String? = names.load()["$printing|$face"]
}
