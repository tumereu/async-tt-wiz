package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Card moves read as neutral sentences named by where the card went, never by the acting player. */
class MoveTextTest {
    private val f = DownloadedTreeFixture
    private val a = f.a; private val b = f.b
    private val names = mapOf(a to "alice", b to "bob")
    private fun id(n: Int) = f.id(n)

    private val library = id(30); private val hand = id(31); private val battlefield = id(32); private val graveyard = id(33)
    private val exile = id(34); private val stack = id(35); private val handB = id(36); private val battlefieldB = id(37)
    private val libraryB = id(38); private val temp1 = id(39); private val temp3 = id(40); private val graveyardB = id(41)
    private val shock = id(50); private val opt = id(51); private val bear = id(52); private val ponder = id(53)
    private val banished = id(54); private val spell = id(55); private val hidden = id(56); private val thief = id(57); private val second = id(58)

    private val before: PlayState by lazy {
        val base = f.root().initial
        val cards = listOf(shock to "Shock", opt to "Opt", bear to "Grizzly Bears", ponder to "Ponder", banished to "Swords",
            spell to "Lightning Bolt", hidden to "Brainstorm", thief to "Thieving Magpie", second to "Preordain")
        val definitions = cards.mapIndexed { n, (_, name) -> CardDefinition(name, listOf(DefinitionFace(id(80 + n), name))) }
        val where = mapOf(shock to library, second to library, opt to hand, bear to battlefield, ponder to graveyard, banished to exile,
            spell to stack, hidden to temp1, thief to battlefieldB)
        val playCards = cards.mapIndexed { n, (card, _) -> PlayCard(card, DeckJson.digest(definitions[n]), if (card == thief) b else a,
            where.getValue(card), id(80 + n), covered = where.getValue(card) in setOf(library, hand, temp1)) }
        fun holding(container: String) = playCards.filter { it.containerId == container }.map { it.id }
        val containers = listOf(PlayContainer(library, PlayZone.LIBRARY, a, holding(library)), PlayContainer(hand, PlayZone.HAND, a, holding(hand)),
            PlayContainer(battlefield, PlayZone.BATTLEFIELD, a, holding(battlefield)), PlayContainer(graveyard, PlayZone.GRAVEYARD, a, holding(graveyard)),
            PlayContainer(exile, PlayZone.EXILE, a, holding(exile)), PlayContainer(stack, PlayZone.STACK, null, holding(stack)),
            PlayContainer(handB, PlayZone.HAND, b), PlayContainer(battlefieldB, PlayZone.BATTLEFIELD, b, holding(battlefieldB)),
            PlayContainer(libraryB, PlayZone.LIBRARY, b), PlayContainer(graveyardB, PlayZone.GRAVEYARD, b),
            PlayContainer(temp1, PlayZone.LIBRARY, a, holding(temp1), temp = true),
            PlayContainer(temp3, PlayZone.LIBRARY, a, temp = true, tempNumber = 3))
        base.copy(definitions = definitions.map { SubmittedDefinition(DeckJson.digest(it), it) },
            board = base.board.copy(cards = playCards, containers = containers))
    }

    private fun move(card: String, from: String, to: String, edge: MoveSourceEdge = MoveSourceEdge.MEMBER,
        insertion: MoveInsertion = MoveInsertion.TOP, faceDown: Boolean? = null) =
        PlayAction.MoveCard(card, MoveSource(from, edge = edge), MoveDestination(to, insertion), faceDown = faceDown)

    /** The recorded movement (and face-down entry) the text reads alongside the before-board. */
    private fun text(action: PlayAction, viewer: String = a, author: String = a): HistoryText {
        val transition = when (action) {
            is PlayAction.MoveCard -> GameplayTransition(movement = GameplayMoveChange(action.cardId,
                CardLocation(action.source.containerId), CardLocation(action.destination.containerId)),
                fields = if (action.faceDown == true) listOf(GameplayFieldChange(action.cardId, GameplayField.EXPLICITLY_COVERED, "false", "true"))
                    else emptyList())
            is PlayAction.RandomHand -> GameplayTransition(movement = when (action.result) {
                HandRandomResult.DISCARD -> graveyard; HandRandomResult.EXILE -> exile
                HandRandomResult.LIBRARY_BOTTOM -> library; HandRandomResult.REVEAL -> null
            }?.let { GameplayMoveChange(action.cardId, CardLocation(hand), CardLocation(it)) })
            else -> GameplayTransition()
        }
        return historyText(GameplayMutation(id(100), id(5), id(6), id(101), author, null, action, transition), before, viewer, names)
    }
    private fun says(expected: String, action: PlayAction, viewer: String = a) =
        assertEquals(expected, text(action, viewer).description, "viewer ${names[viewer]}: $action")

    // HIS-063, HIS-065
    @Test fun intoHandDrawTakeAndReturn() {
        says("Drew Shock", move(shock, library, hand, MoveSourceEdge.TOP))
        says("Drew a card", move(shock, library, hand, MoveSourceEdge.TOP), viewer = b)
        assertEquals("Draw", text(move(shock, library, hand, MoveSourceEdge.TOP)).label)
        says("Took Shock from library", move(shock, library, hand))
        says("Took a card from library", move(shock, library, hand), viewer = b)
        says("Took a card from library", move(shock, library, hand, MoveSourceEdge.BOTTOM), viewer = b)
        says("Returned Ponder to hand", move(ponder, graveyard, hand))
        says("Returned Grizzly Bears to hand", move(bear, battlefield, hand), viewer = b)
        says("Returned Lightning Bolt to hand", move(spell, stack, hand))
        says("Returned Brainstorm to hand from Temp 1", move(hidden, temp1, hand))
        says("Returned a card to hand from Temp 1", move(hidden, temp1, hand), viewer = b)
        // Only a hand that isn't the card owner's is named.
        says("Returned Grizzly Bears to bob's hand", move(bear, battlefield, handB))
        says("Drew a card into bob's hand", move(shock, library, handB, MoveSourceEdge.TOP))
        assertEquals("Return", text(move(ponder, graveyard, hand)).label)
    }

    // HIS-063, HIS-064, HIS-065
    @Test fun ontoTheBattlefieldAndGainingControl() {
        says("Put Opt onto the battlefield", move(opt, hand, battlefield), viewer = b)
        says("Put Ponder onto the battlefield from graveyard", move(ponder, graveyard, battlefield))
        says("Put Swords onto the battlefield from exile", move(banished, exile, battlefield))
        says("Put Lightning Bolt onto the battlefield from the stack", move(spell, stack, battlefield))
        says("Put Shock onto the battlefield from library", move(shock, library, battlefield, MoveSourceEdge.TOP), viewer = b)
        says("Put Opt onto bob's battlefield", move(opt, hand, battlefieldB))
        says("Put Ponder onto bob's battlefield from graveyard", move(ponder, graveyard, battlefieldB))
        // Face down onto the battlefield stays hidden from the other player.
        says("Put a card onto the battlefield", move(opt, hand, battlefield, faceDown = true), viewer = b)
        // The one player name: who receives a permanent from another battlefield, never who moved it.
        says("bob gains control of Grizzly Bears", move(bear, battlefield, battlefieldB))
        says("alice gains control of Thieving Magpie", move(thief, battlefieldB, battlefield), viewer = b)
        assertEquals("Gain control", text(move(bear, battlefield, battlefieldB)).label)
    }

    // HIS-063
    @Test fun ontoTheStackIsACast() {
        says("Cast Opt", move(opt, hand, stack), viewer = b)
        says("Cast Ponder from graveyard", move(ponder, graveyard, stack))
        says("Cast Swords from exile", move(banished, exile, stack))
        says("Cast Shock from library", move(shock, library, stack, MoveSourceEdge.TOP), viewer = b)
        says("Cast a card face down", move(opt, hand, stack, faceDown = true), viewer = b)
        says("Cast Opt face down", move(opt, hand, stack, faceDown = true))
        assertEquals("Cast", text(move(opt, hand, stack)).label)
    }

    // HIS-063, HIS-065
    @Test fun intoTheGraveyardNeverClaimsWhy() {
        says("Discarded Opt", move(opt, hand, graveyard), viewer = b)
        says("Milled Shock", move(shock, library, graveyard, MoveSourceEdge.TOP), viewer = b)
        says("Put Grizzly Bears into graveyard", move(bear, battlefield, graveyard))
        says("Put Lightning Bolt into graveyard from the stack", move(spell, stack, graveyard))
        says("Put Shock into graveyard from library", move(shock, library, graveyard))
        says("Put Swords into graveyard from exile", move(banished, exile, graveyard))
        says("Put Brainstorm into graveyard from Temp 1", move(hidden, temp1, graveyard), viewer = b)
        says("Put Grizzly Bears into bob's graveyard", move(bear, battlefield, graveyardB))
        for (viewer in listOf(a, b)) for (action in listOf(move(bear, battlefield, graveyard), move(spell, stack, graveyard))) {
            val description = text(action, viewer).description.lowercase()
            for (claim in listOf("died", "destroyed", "resolved", "countered", "alice", "bob")) assertFalse(claim in description, description)
        }
        assertEquals(listOf("Discard", "Mill", "Put into graveyard"), listOf(move(opt, hand, graveyard),
            move(shock, library, graveyard, MoveSourceEdge.TOP), move(bear, battlefield, graveyard)).map { text(it).label })
    }

    // HIS-063
    @Test fun intoExile() {
        says("Exiled Grizzly Bears", move(bear, battlefield, exile))
        says("Exiled Ponder from graveyard", move(ponder, graveyard, exile))
        says("Exiled Opt from hand", move(opt, hand, exile), viewer = b)
        says("Exiled Lightning Bolt from the stack", move(spell, stack, exile))
        says("Exiled Shock from library", move(shock, library, exile, MoveSourceEdge.TOP))
    }

    // HIS-063, HIS-065
    @Test fun intoALibrary() {
        says("Put Opt on top of library", move(opt, hand, library))
        says("Put a card on top of library", move(opt, hand, library), viewer = b)
        says("Put a card on the bottom of library", move(opt, hand, library, insertion = MoveInsertion.BOTTOM), viewer = b)
        says("Put Grizzly Bears into library", move(bear, battlefield, library, insertion = MoveInsertion.GAP))
        says("Put Grizzly Bears on top of bob's library", move(bear, battlefield, libraryB))
        says("Put a card on top of library from Temp 1", move(hidden, temp1, library), viewer = b)
        // Same library, a new edge: still a put, not a vague move.
        for (viewer in listOf(a, b))
            says("Put a card on the bottom of library", move(shock, library, library, MoveSourceEdge.TOP, MoveInsertion.BOTTOM), viewer)
        val seed = RandomV1.encodeSeed(ByteArray(32))
        says("Put Ponder, Opt on the bottom of library in random order",
            PlayAction.MoveToLibraryRandom(library, listOf(ponder, opt), true, seed))
        says("Put Ponder, 1 card on top of library in random order",
            PlayAction.MoveToLibraryRandom(library, listOf(ponder, opt), false, seed), viewer = b)
    }

    // HIS-063, HIS-064
    @Test fun tempPilesAndSamePlace() {
        says("Put Ponder into Temp 3", move(ponder, graveyard, temp3), viewer = b)
        says("Put a card into Temp 3", move(shock, library, temp3, MoveSourceEdge.TOP))
        says("Put a card into Temp 3 from Temp 1", move(hidden, temp1, temp3))
        says("Grizzly Bears moved on the battlefield", move(bear, battlefield, battlefield))
        says("Thieving Magpie moved on the battlefield", move(thief, battlefieldB, battlefieldB))
        says("Ponder moved within graveyard", move(ponder, graveyard, graveyard))
        says("A card moved within Temp 1", move(hidden, temp1, temp1))
        says("Lightning Bolt moved within the stack", move(spell, stack, stack))
        assertEquals(HistoryText("Hand reordered", "Reorder hand"), text(move(opt, hand, hand)))
    }

    // HIS-066
    @Test fun randomHandResultsSayAtRandom() {
        val seed = RandomV1.encodeSeed(ByteArray(32))
        fun random(result: HandRandomResult) = PlayAction.RandomHand(hand, listOf(opt), seed, result, opt)
        says("Discarded Opt at random", random(HandRandomResult.DISCARD), viewer = b)
        says("Exiled Opt from hand at random", random(HandRandomResult.EXILE), viewer = b)
        says("Put a card from hand on the bottom of library at random", random(HandRandomResult.LIBRARY_BOTTOM), viewer = b)
        says("Put Opt from hand on the bottom of library at random", random(HandRandomResult.LIBRARY_BOTTOM))
        says("Revealed Opt from hand at random", random(HandRandomResult.REVEAL))
    }

    // HIS-067
    @Test fun groupsMergeMovesByKindInOrderOfFirstAppearance() {
        val events = listOf(move(shock, library, hand, MoveSourceEdge.TOP), move(opt, hand, graveyard),
            move(second, library, hand, MoveSourceEdge.TOP), move(ponder, graveyard, exile), move(bear, battlefield, graveyard))
        fun group(viewer: String, picked: List<PlayAction> = events): HistoryText {
            val texts = picked.mapIndexed { n, action -> id(200 + n) to text(action, viewer) }.toMap()
            return historyGroupText(texts.keys.toList(), texts.keys.last(), texts)
        }
        assertEquals("Drew Shock, Preordain; Discarded Opt; Exiled Ponder from graveyard; Put Grizzly Bears into graveyard",
            group(a).description)
        assertEquals("Drew 2 cards; Discarded Opt; Exiled Ponder from graveyard; Put Grizzly Bears into graveyard", group(b).description)
        // A mixed group keeps its representative's label; one kind counts its moves.
        assertEquals("Put into graveyard", group(b).label)
        assertEquals(HistoryText("Drew 2 cards", "Draw ×2"), group(b, events.filterIndexed { n, _ -> n % 2 == 0 }.take(2)))
        val discards = listOf(move(opt, hand, graveyard), move(shock, library, graveyard))
        assertEquals(HistoryText("Discarded Opt; Put Shock into graveyard from library", "Put into graveyard"), group(a, discards))
        val hiddenDiscards = listOf(move(opt, hand, graveyard, faceDown = true), move(opt, hand, graveyard))
        assertEquals(HistoryText("Discarded Opt, 1 card", "Discard ×2"), group(b, hiddenDiscards))
    }
}
