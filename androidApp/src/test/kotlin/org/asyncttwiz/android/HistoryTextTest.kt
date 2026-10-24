package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryTextTest {
    private val f = DownloadedTreeFixture
    private val names = mapOf(f.a to "alice", f.b to "bob")
    private fun id(n: Int) = f.id(n)
    private val state: PlayState get() {
        val initial = f.root().initial
        val definition = CardDefinition("Velvet Prism Engine", listOf(DefinitionFace(id(11), "Velvet Prism Engine")))
        val digest = DeckJson.digest(definition)
        return initial.copy(definitions = listOf(SubmittedDefinition(digest, definition)), board = initial.board.copy(
            cards = initial.board.cards.map { it.copy(definition = digest) },
            containers = initial.board.containers + listOf(
                PlayContainer(id(20), PlayZone.HAND, f.a), PlayContainer(id(21), PlayZone.HAND, f.b),
                PlayContainer(id(22), PlayZone.BATTLEFIELD, f.a), PlayContainer(id(23), PlayZone.BATTLEFIELD, f.b),
                PlayContainer(id(24), PlayZone.STACK))))
    }
    private fun event(action: PlayAction, transition: GameplayTransition = GameplayTransition(), author: String = f.a) =
        GameplayMutation(id(100), id(5), id(6), id(101), author, null, action, transition)
    private fun format(action: PlayAction, transition: GameplayTransition = GameplayTransition(),
        before: PlayState = state, author: String = f.a, count: Int = 1) =
        historyText(event(action, transition, author), before, f.a, names, count)

    @Test fun shuffleLifeStepsRandomAndCommentArePlainEnglish() {
        assertEquals(HistoryText("alice shuffled their library", "Shuffle"), format(PlayAction.ShuffleLibrary(id(10), "secret seed")))
        assertEquals("bob shuffled alice's library", format(PlayAction.ShuffleLibrary(id(10), "secret"), author = f.b).description)
        assertEquals("alice set life to 17", format(PlayAction.SetLife(f.a, "17")).description)
        assertEquals("alice set bob's life to 17", format(PlayAction.SetLife(f.b, "17")).description)
        assertEquals("alice set their life to 17", format(PlayAction.AdjustLife(f.a, "-3"), GameplayTransition(
            fields = listOf(GameplayFieldChange(f.a, GameplayField.LIFE, "20", "17")))).description)
        assertEquals("alice advanced to Draw step", format(PlayAction.ChooseStep(TurnPosition.DRAW)).description)
        assertEquals("alice advanced to Draw step", format(PlayAction.NextStep, GameplayTransition(fields = listOf(
            GameplayFieldChange(id(2), GameplayField.PHASE, "BEGINNING", "BEGINNING"),
            GameplayFieldChange(id(2), GameplayField.STEP, "UPKEEP", "DRAW")))).description)
        assertEquals("alice rolled a d6: 4", format(PlayAction.RollDie(6, 4)).description)
        assertEquals("alice flipped a coin: heads", format(PlayAction.FlipCoin(CoinFace.HEADS)).description)
        assertEquals("alice commented: hello there", format(PlayAction.EventComment(EventReference(id(2), id(5)), "hello\nthere")).description)
    }

    @Test fun drawNeverNamesAnOpponentsCardAndGroupsHaveCompactCounts() {
        val action = PlayAction.MoveCard(id(12), MoveSource(id(13), edge = MoveSourceEdge.TOP),
            MoveDestination(id(21), MoveInsertion.UNORDERED))
        assertEquals(HistoryText("bob drew a card", "Draw"), format(action, author = f.b))
        assertEquals(HistoryText("bob drew 7 cards", "Draw ×7"), format(action, author = f.b, count = 7))
        val texts = (1..7).associate { id(100 + it) to format(action, author = f.b) }
        val range = HistoryRange(id(100), id(100), texts.keys.toList(), texts.keys.first(), HistoryGlyph.DRAW,
            f.b, null, HistoryPoint(0f, 0f))
        assertEquals(HistoryText("bob drew 7 cards", "Draw ×7"), historyRangeText(range, texts))
    }

    @Test fun visibleCardsAreNamedButCoveredCardsAndOpponentsHandsAreNot() {
        val before = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == id(9)) it.copy(containerId = id(20), covered = false) else it
        }))
        val move = PlayAction.MoveCard(id(9), MoveSource(id(20)), MoveDestination(id(22), MoveInsertion.EMPTY))
        assertEquals("alice moved Velvet Prism Engine from hand to battlefield", format(move, before = before).description)
        assertEquals(HistoryText("alice tapped Velvet Prism Engine", "Tap"), format(PlayAction.SetTapped(id(9), true), before = before))
        val covered = before.copy(board = before.board.copy(cards = before.board.cards.map {
            it.copy(containerId = id(22), covered = true, explicitlyCovered = true)
        }))
        assertEquals("alice tapped a card", format(PlayAction.SetTapped(id(9), true), before = covered).description)
        // Metadata owner is alice, but the card is in bob's hand. It must not grant visibility.
        val opponent = before.copy(board = before.board.copy(cards = before.board.cards.map { it.copy(containerId = id(21)) }))
        assertEquals("alice tapped a card", format(PlayAction.SetTapped(id(9), true), before = opponent).description)
        assertEquals("alice inspected a card face", format(PlayAction.InspectFace(id(9), id(11), id(21)), before = opponent).description)
    }

    @Test fun publicZonesUseTheSameFaceUpRulesAsTheTableIncludingLegacyCoveredFlags() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) {
            for (holder in listOf(f.a, f.b)) {
                val before = state.copy(board = state.board.copy(
                    containers = state.board.containers + PlayContainer(id(80), zone, holder),
                    cards = state.board.cards.map { it.copy(containerId = id(80), covered = true, explicitlyCovered = false) }))
                assertEquals("bob tapped Velvet Prism Engine", format(PlayAction.SetTapped(id(9), true),
                    before = before, author = f.b).description, "$zone / $holder")
                val hidden = before.copy(board = before.board.copy(cards = before.board.cards.map { it.copy(explicitlyCovered = true) }))
                assertEquals("bob tapped a card", format(PlayAction.SetTapped(id(9), true),
                    before = hidden, author = f.b).description, "$zone face down")
            }
        }
        // Default covered flags in one's own hand do not hide a face that the table displays.
        val ownHand = state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(containerId = id(20)) }))
        assertEquals("alice tapped Velvet Prism Engine", format(PlayAction.SetTapped(id(9), true), before = ownHand).description)
        for (container in listOf(id(10), id(13), id(21))) {
            val hidden = state.copy(board = state.board.copy(cards = state.board.cards.map {
                it.copy(containerId = container, covered = false, explicitlyCovered = false)
            }))
            assertEquals("bob tapped a card", format(PlayAction.SetTapped(id(9), true), before = hidden, author = f.b).description)
        }
    }

    @Test fun namesUseVisibilityBeforeOrAfterTheMutationButNotAHiddenFace() {
        val hidden = state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(containerId = id(21)) }))
        val move = PlayAction.MoveCard(id(9), MoveSource(id(21)), MoveDestination(id(23), MoveInsertion.EMPTY))
        val movement = GameplayTransition(movement = GameplayMoveChange(id(9), CardLocation(id(21)), CardLocation(id(23))))
        assertEquals("bob moved Velvet Prism Engine from hand to battlefield", format(move, movement, hidden, f.b).description)
        val faceDown = movement.copy(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "false", "true")))
        assertEquals("bob moved a card from hand to battlefield", format(move, faceDown, hidden, f.b).description)

        val public = hidden.copy(board = hidden.board.copy(cards = hidden.board.cards.map { it.copy(containerId = id(23)) }))
        val cover = GameplayTransition(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "false", "true")))
        assertEquals("bob turned Velvet Prism Engine face down", format(PlayAction.SetCovered(id(9), true), cover, public, f.b).description)
        val covered = public.copy(board = public.board.copy(cards = public.board.cards.map { it.copy(explicitlyCovered = true) }))
        val uncover = GameplayTransition(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "true", "false")))
        assertEquals("bob turned Velvet Prism Engine face up", format(PlayAction.SetCovered(id(9), false), uncover, covered, f.b).description)
        val toHand = PlayAction.MoveCard(id(9), MoveSource(id(23)), MoveDestination(id(21), MoveInsertion.UNORDERED))
        val returning = GameplayTransition(movement = GameplayMoveChange(id(9), CardLocation(id(23)), CardLocation(id(21))))
        assertEquals("bob moved Velvet Prism Engine from battlefield to hand", format(toHand, returning, public, f.b).description)
        assertEquals("bob moved a card from battlefield to hand", format(toHand, returning, covered, f.b).description)
    }

    @Test fun historicalTextUsesEventTimeBoardAndSurvivesLaterRevealsAndForks() {
        var tree = GameplayTree(state)
        fun append(request: Int, action: PlayAction, actor: String = f.b) {
            tree = assertNotNull(tree.play(actor, GameCommand(id(request), GameScope(state.gameId, playId = state.id),
                GameAction.Play(action), state.id, "0")).tree)
        }
        append(200, PlayAction.MoveCard(id(12), MoveSource(id(13), edge = MoveSourceEdge.TOP),
            MoveDestination(id(21), MoveInsertion.UNORDERED)))
        val draw = tree.currentNodeId
        // Public-zone entry is face-up by default. This fixture needs a later explicit reveal.
        append(201, PlayAction.MoveCard(id(12), MoveSource(id(21)),
            MoveDestination(id(23), MoveInsertion.EMPTY, position = BoardPosition(0, 0)), faceDown = true))
        append(202, PlayAction.SetCovered(id(12), false))
        append(203, PlayAction.SetTapped(id(12), true))
        val tap = tree.currentNodeId
        append(205, PlayAction.EventComment(EventReference(state.id, tap), "ready"))
        val comment = tree.currentNodeId
        tree = assertNotNull(tree.activate(draw).tree)
        append(204, PlayAction.SetTapped(id(12), true))
        val hiddenTap = tree.currentNodeId
        val texts = historyTexts(tree, f.a, names)
        assertEquals("bob drew a card", texts.getValue(draw).description)
        assertEquals("bob tapped Velvet Prism Engine", texts.getValue(tap).description)
        assertEquals("bob tapped a card", texts.getValue(hiddenTap).description)
        assertEquals("bob commented: ready", texts.getValue(comment).description)
        val timeline = HistoryTimelineLayout.events(HistoryTree(tree.initial.root.id, tree.nodes.values.toList(),
            tree.currentNodeId, "v1", texts = texts), tap)
        assertEquals(texts.getValue(tap).description, timeline.last().description)
    }

    @Test fun tokensCombatAndCountersUseVisibleNamesWithoutInspectingCoveredFaces() {
        val token = PlayCard(id(60), TokenCatalog.blank.digest, f.a, id(22),
            TokenCatalog.blank.definition.faces.first().faceId, covered = false, token = true)
        val presence = TokenPresence(token, CardLocation(id(22)))
        assertEquals("alice created a Blank token", format(PlayAction.CreateToken(TokenCatalog.blank),
            GameplayTransition(tokens = listOf(TokenChange(null, presence)))).description)
        val before = state.copy(definitions = state.definitions + TokenCatalog.blank, board = state.board.copy(
            cards = state.board.cards + token, combat = CombatArea(id(61), listOf(CombatPair(id(62),
                attackers = listOf(CombatOccurrence(id(63), token.id)))))))
        assertEquals("alice removed Blank from combat", format(PlayAction.Combat(CombatAction.Remove(id(61), id(63))), before = before).description)
        assertEquals("alice set the red counter on bob to 3", format(PlayAction.SetCounter(CounterTarget.PLAYER, f.b,
            CounterColor.RED, "3")).description)
        assertEquals("alice cleared the blue counter on Blank", format(PlayAction.ClearCardCounter(token.id, CounterColor.BLUE), before = before).description)
        assertEquals("alice removed Blank", format(PlayAction.RemoveToken(token.id), before = before).description)
        assertEquals("alice suggested a draw", format(PlayAction.SuggestResult(SuggestedResult.DRAW)).description)
    }

    @Test fun everyActionFamilyHasShortLabelsWithoutWireIdsOrMutationDumps() {
        val ref = EventReference(id(2), id(5))
        val actions = listOf(
            PlayAction.SuggestResult(SuggestedResult.WIN), PlayAction.SuggestResult(SuggestedResult.LOSS), PlayAction.SuggestResult(SuggestedResult.DRAW),
            PlayAction.SetTapped(id(9), false), PlayAction.SetLife(f.a, "17"), PlayAction.AdjustLife(f.a, "1"),
            PlayAction.SetPlayerCounter(f.a, CounterColor.BLUE, "2"), PlayAction.AdjustPlayerCounter(f.a, CounterColor.BLUE, "1"),
            PlayAction.NextStep, PlayAction.EndTurn, PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN),
            PlayAction.RollDie(6, 4), PlayAction.FlipCoin(CoinFace.TAILS),
            PlayAction.MoveCard(id(9), MoveSource(id(10)), MoveDestination(id(20), MoveInsertion.UNORDERED)),
            PlayAction.ShuffleLibrary(id(10), "secret"), PlayAction.ShowContents(id(10), ContentsSelection.ALL),
            PlayAction.DismissContents(id(30)), PlayAction.EventComment(ref, "hello"),
            PlayAction.Ping(ref, PingTarget(PingTargetKind.PLAYER, f.b)), PlayAction.Unping(ref, PingTarget(PingTargetKind.CARD, id(9))),
            PlayAction.SetCorner(id(9), "2/3"), PlayAction.AdjustCorner(id(9), CornerPart.BOTH, 1), PlayAction.ResetCorner(id(9)),
            PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.RED, "3"),
            PlayAction.AdjustCounter(CounterTarget.PLAYER, f.a, CounterColor.RED, "1"),
            PlayAction.SetNote(id(9), "secret"), PlayAction.SetOwner(id(9), f.b), PlayAction.SetFace(id(9), id(11)),
            PlayAction.SetCovered(id(9), true), PlayAction.InspectFace(id(9), id(11), id(10)),
            PlayAction.ClearCardCounter(id(9), CounterColor.GRAY), PlayAction.CreateToken(TokenCatalog.blank),
            PlayAction.CloneToken(id(9)), PlayAction.RemoveToken(id(9)),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(9)), LinkEndpoint(LinkEndpointKind.PLAYER, f.b)),
            PlayAction.RemoveLink(id(30)), PlayAction.LinkProjections(ProjectionEndpoint(id(30)), ProjectionEndpoint(id(31))),
            PlayAction.RemoveProjectionLink(id(30)), PlayAction.Combat(CombatAction.Open), PlayAction.Combat(CombatAction.Close(id(30))),
            PlayAction.Combat(CombatAction.Declare(id(30), id(9))),
            PlayAction.Combat(CombatAction.Place(id(9), CombatDestination(id(30), CombatRole.BLOCKER))),
            PlayAction.Combat(CombatAction.Remove(id(30), id(31))), PlayAction.Stack(StackAction.AddEffect(id(9), "secret digest")),
            PlayAction.Stack(StackAction.RemoveEffect(id(30))), PlayAction.Stack(StackAction.ReorderEffect(id(30), IdentityGap(), IdentityGap())))
        for (action in actions) {
            val result = format(action)
            assertTrue(result.description.startsWith("alice "), action::class.simpleName)
            assertTrue(result.label.split(' ').size in 1..3, result.label)
            assertFalse(result.description.contains("00000000-"), result.description)
            assertFalse(result.description.contains("GameplayTransition"), result.description)
            assertFalse(result.description.contains("secret"), result.description)
        }
    }

    @Test fun missingContextUnsupportedSchemaAndUserTextNeverExposeIdentifiers() {
        assertEquals(HistoryText("Initial state", "Start"), historyText(null))
        assertEquals("A player tapped a card", historyText(event(PlayAction.SetTapped(id(999), true))).description)
        for (version in 1..2) assertEquals("alice tapped a card",
            historyText(event(PlayAction.SetTapped(id(999), true)).copy(schemaVersion = version), state, f.a, names).description)
        assertEquals("alice changed the table", historyText(event(PlayAction.SetTapped(id(9), true)).copy(schemaVersion = 3),
            state, f.a, names).description)
        assertEquals("alice commented: see [reference]", format(PlayAction.EventComment(EventReference(id(2), id(5)), "see ${id(99)}")).description)
        assertTrue(format(PlayAction.EventComment(EventReference(id(2), id(5)), "x".repeat(1000))).description.length <= 180)
    }
}
