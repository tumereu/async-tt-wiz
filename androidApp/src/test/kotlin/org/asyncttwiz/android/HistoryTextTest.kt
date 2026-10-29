package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryTextTest {
    private val f = DownloadedTreeFixture
    private val names = mapOf(f.a to "alice", f.b to "bob")
    private fun id(n: Int) = f.id(n)
    private val state: PlayState get() {
        val initial = f.root(PlayZone.LIBRARY).initial
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
        before: PlayState = state, author: String = f.a, viewer: String = f.a) =
        historyText(event(action, transition, author), before, viewer, names)

    private fun placed(holder: String, zone: PlayZone, faceDown: Boolean = false): PlayState {
        val base = state
        val containers = base.board.containers + listOf(PlayContainer(id(25), PlayZone.GRAVEYARD, f.a),
            PlayContainer(id(26), PlayZone.GRAVEYARD, f.b))
        val target = containers.single { it.accountId == holder && it.zone == zone }
        return base.copy(board = base.board.copy(cards = base.board.cards.map {
            if (it.id != id(9)) it else it.copy(containerId = target.id, owner = holder,
                covered = faceDown || zone in listOf(PlayZone.LIBRARY, PlayZone.HAND), explicitlyCovered = faceDown)
        }, containers = containers.map { it.copy(cards = (if (it.id == target.id) listOf(id(9)) else emptyList()) + (it.cards - id(9))) }))
    }

    private fun apply(before: PlayState, author: String, action: PlayAction, request: Int = 300): GameplayChange {
        val changed = GameplayRules.change(before, author, GameCommand(id(request), GameScope(before.gameId, playId = before.id),
            GameAction.Play(action), before.id, "0"))
        assertEquals("accepted", changed.outcome, "$action: ${changed.reason}")
        return changed
    }

    @Test fun statusChangesReadAsStatusSetOrCleared() {
        val set = format(PlayAction.SetStatus("Wait"), author = f.b, viewer = f.a)
        assertEquals("Status → Wait", set.description); assertEquals("Status", set.label)
        assertEquals("Status cleared", format(PlayAction.SetStatus("")).description)
    }

    @Test fun scalarEditsShowRecordedBeforeAndAfterValuesNotRequestedDeltas() {
        val before = placed(f.a, PlayZone.BATTLEFIELD).let { base -> base.copy(board = base.board.copy(
            cards = base.board.cards.map { if (it.id == id(9)) it.copy(corner = "2/2", counters = mapOf(CounterColor.RED to "2")) else it })) }
        val cases = listOf(
            Triple(PlayAction.SetCorner(id(9), "3/3"), GameplayFieldChange(id(9), GameplayField.CORNER, "2/2", "3/3"), "Velvet Prism Engine P/T 2/2 → 3/3"),
            Triple(PlayAction.AdjustCorner(id(9), CornerPart.BOTH, 1), GameplayFieldChange(id(9), GameplayField.CORNER, "2/2", "3/3"), "Velvet Prism Engine P/T 2/2 → 3/3"),
            Triple(PlayAction.ResetCorner(id(9)), GameplayFieldChange(id(9), GameplayField.CORNER, "3/3", "2/2"), "Velvet Prism Engine P/T 3/3 → 2/2"),
            Triple(PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.RED, "3"), GameplayFieldChange(id(9), GameplayField.COUNTER, "2", "3", CounterColor.RED), "Velvet Prism Engine red counter 2 → 3"),
            Triple(PlayAction.AdjustCounter(CounterTarget.CARD, id(9), CounterColor.RED, "1"), GameplayFieldChange(id(9), GameplayField.COUNTER, "2", "3", CounterColor.RED), "Velvet Prism Engine red counter 2 → 3"),
            Triple(PlayAction.ClearCardCounter(id(9), CounterColor.RED), GameplayFieldChange(id(9), GameplayField.COUNTER, "2", null, CounterColor.RED), "Velvet Prism Engine red counter 2 → 0"),
            Triple(PlayAction.AdjustLife(f.a, "-3"), GameplayFieldChange(f.a, GameplayField.LIFE, "30", "27"), "Life 30 → 27"))
        for ((action, field, expected) in cases) for (viewer in listOf(f.a, f.b))
            assertEquals(expected, format(action, GameplayTransition(fields = listOf(field)), before, viewer = viewer).description)
        assertEquals("Counter (purple) 0 → 2", format(PlayAction.SetPlayerCounter(f.a, CounterColor.PURPLE, "2")).description)
        for (field in listOf(GameplayField.COUNTER, GameplayField.PLAYER_COUNTER)) {
            val transition = GameplayTransition(fields = listOf(GameplayFieldChange(f.b, field, "5", "7", CounterColor.PURPLE)))
            assertEquals("bob's counter (purple) 5 → 7", format(PlayAction.AdjustPlayerCounter(f.b, CounterColor.PURPLE, "2"), transition).description)
            assertEquals("bob's counter (purple) 5 → 7", format(PlayAction.AdjustCounter(CounterTarget.PLAYER, f.b, CounterColor.PURPLE, "2"), transition).description)
        }
        assertEquals("Velvet Prism Engine note: ready to block", format(PlayAction.SetNote(id(9), "ready\nto block"), before = before).description)
        assertEquals("Velvet Prism Engine projection note: ready", format(PlayAction.SetProjectionNote(id(9), "ready"), before = before).description)
    }

    @Test fun turnDescriptionsDistinguishStepsFromNextTurnAndKeepLabels() {
        assertEquals(HistoryText("Moved to Declare attackers", "Choose step"), format(PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS)))
        val transition = GameplayTransition(fields = listOf(
            GameplayFieldChange(state.id, GameplayField.TURN_NUMBER, "4", "5"),
            GameplayFieldChange(state.id, GameplayField.TURN_PLAYER, f.a, f.b)))
        assertEquals(HistoryText("Turn 5 → bob", "End turn"), format(PlayAction.EndTurn, transition))
        assertEquals(HistoryText("Turn 5 → bob", "Next step"), format(PlayAction.NextStep, transition))
        assertEquals(HistoryText("Move passed → bob", "Pass move"), format(PlayAction.PassAttention,
            GameplayTransition(attention = AttentionChange(f.a, f.b))))
        assertEquals(HistoryText("Mulligan to 6", "Mulligan"), format(PlayAction.Mulligan(id(10), id(20), emptyList(), 6, "secret")))
    }

    @Test fun handIdentityFollowsHolderNotOwnerAndUnknownLibraryOrderNeverGrantsFaces() {
        for (holder in listOf(f.a, f.b)) for (zone in listOf(PlayZone.HAND, PlayZone.LIBRARY)) {
            val before = placed(holder, zone)
            for (viewer in listOf(f.a, f.b)) {
                val subject = if (zone == PlayZone.HAND && viewer == holder) "Velvet Prism Engine" else "A card"
                assertEquals("$subject tapped", format(PlayAction.SetTapped(id(9), true), before = before, author = holder, viewer = viewer).description,
                    "$zone / holder=$holder / viewer=$viewer")
                if (zone == PlayZone.LIBRARY) for (top in listOf(true, false)) {
                    val ordered = before.copy(board = before.board.copy(containers = before.board.containers.map {
                        if (it.id == before.board.cards.first().containerId) it.copy(
                            knownTop = if (top) listOf(id(9)) else emptyList(), knownBottom = if (top) emptyList() else listOf(id(9))) else it }))
                    assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = ordered, viewer = viewer).description)
                }
            }
        }
    }

    @Test fun continuousTopRevealNamesOnlyTheActualTopForBothViewers() {
        for (holder in listOf(f.a, f.b)) {
            val base = placed(holder, PlayZone.LIBRARY)
            val library = base.board.cards.first().containerId
            val before = base.copy(board = base.board.copy(cards = base.board.cards.map { it.copy(containerId = library) },
                containers = base.board.containers.map { it.copy(cards = if (it.id == library) listOf(id(9), id(12)) else emptyList(),
                    keepTopRevealed = it.id == library, knownTop = if (it.id == library) listOf(id(9), id(12)) else emptyList()) }))
            for (viewer in listOf(f.a, f.b)) {
                assertEquals("Velvet Prism Engine tapped", format(PlayAction.SetTapped(id(9), true), before = before, viewer = viewer).description)
                assertEquals("A card tapped", format(PlayAction.SetTapped(id(12), true), before = before, viewer = viewer).description)
            }
        }
    }

    @Test fun drawAndHandToLibraryMovesStayPrivateFromBothViewpoints() {
        for (holder in listOf(f.a, f.b)) {
            val before = placed(holder, PlayZone.LIBRARY)
            val library = before.board.cards.first().containerId
            val hand = before.board.containers.single { it.zone == PlayZone.HAND && it.accountId == holder }.id
            val draw = apply(before, holder, PlayAction.MoveCard(id(9), MoveSource(library, edge = MoveSourceEdge.TOP),
                MoveDestination(hand, MoveInsertion.UNORDERED)))
            val drawn = assertNotNull(draw.state)
            for (viewer in listOf(f.a, f.b)) {
                assertEquals("${if (viewer == holder) "Velvet Prism Engine" else "A card"}: Library → Hand",
                    historyText(draw.mutation, before, viewer, names).description)
                assertEquals("${if (viewer == holder) "Velvet Prism Engine" else "A card"} tapped",
                    format(PlayAction.SetTapped(id(9), true), before = drawn, author = holder, viewer = viewer).description)
            }
            for (insertion in listOf(MoveInsertion.TOP, MoveInsertion.BOTTOM)) {
                val returned = apply(drawn, holder, PlayAction.MoveCard(id(9), MoveSource(hand), MoveDestination(library, insertion)), 301)
                for (viewer in listOf(f.a, f.b)) {
                    val subject = if (viewer == holder) "Velvet Prism Engine" else "A card"
                    assertEquals("$subject: Hand → Library ${insertion.name.lowercase()}", historyText(returned.mutation, drawn, viewer, names).description)
                    assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = returned.state!!, viewer = viewer).description)
                }
            }
        }
    }

    @Test fun handAndMillMovesNameOnlyCardsExposedByTheirDestination() {
        for (holder in listOf(f.a, f.b)) for (zone in listOf(PlayZone.HAND, PlayZone.LIBRARY)) for (faceDown in listOf(false, true)) {
            val before = placed(holder, zone)
            val source = before.board.cards.first().containerId
            val graveyard = before.board.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == holder }.id
            val moved = apply(before, holder, PlayAction.MoveCard(id(9), MoveSource(source,
                edge = if (zone == PlayZone.LIBRARY) MoveSourceEdge.TOP else MoveSourceEdge.MEMBER),
                MoveDestination(graveyard, MoveInsertion.UNORDERED), faceDown = faceDown))
            for (viewer in listOf(f.a, f.b)) {
                val subject = if (!faceDown || zone == PlayZone.HAND && viewer == holder) "Velvet Prism Engine" else "A card"
                assertEquals("$subject: ${zone.name.lowercase().replaceFirstChar { it.uppercase() }} → Graveyard", historyText(moved.mutation, before, viewer, names).description,
                    "$zone / faceDown=$faceDown / holder=$holder / viewer=$viewer")
            }
        }
    }

    @Test fun faceDownBattlefieldAndPrivateInspectionsDoNotGrantOtherViewersNames() {
        for (holder in listOf(f.a, f.b)) {
            val before = placed(holder, PlayZone.BATTLEFIELD, faceDown = true)
            val container = before.board.cards.first().containerId
            val inspected = apply(before, holder, PlayAction.InspectFace(id(9), id(11), container))
            val uncovered = apply(before, holder, PlayAction.SetCovered(id(9), false), 301)
            for (viewer in listOf(f.a, f.b)) {
                assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = before, author = holder, viewer = viewer).description)
                assertEquals("${if (viewer == holder) "Velvet Prism Engine" else "A card"} inspected",
                    historyText(inspected.mutation, before, viewer, names).description)
                assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = inspected.state!!, viewer = viewer).description)
                assertEquals("Velvet Prism Engine face up", historyText(uncovered.mutation, before, viewer, names).description)
            }
        }
    }

    @Test fun hiddenCardEditsDoNotLeakFaceStatisticsNotesOrCounters() {
        for (holder in listOf(f.a, f.b)) for (viewer in listOf(f.a, f.b)) {
            val before = placed(holder, PlayZone.BATTLEFIELD, faceDown = true)
            assertEquals("A card P/T changed", format(PlayAction.ResetCorner(id(9)), GameplayTransition(fields = listOf(
                GameplayFieldChange(id(9), GameplayField.CORNER, "3/3", "2/2"))), before, holder, viewer = viewer).description)
            assertEquals("A card red counter changed", format(PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.RED, "3"),
                before = before, author = holder, viewer = viewer).description)
            assertEquals("A card note edited", format(PlayAction.SetNote(id(9), "Velvet Prism Engine"),
                before = before, author = holder, viewer = viewer).description)
            assertEquals("A card projection note edited", format(PlayAction.SetProjectionNote(id(9), "Velvet Prism Engine"),
                before = before, author = holder, viewer = viewer).description)
        }
    }

    @Test fun handCardProjectedOnStackIsNamedToBothPlayers() {
        val ref = EventReference(id(2), id(5))
        for (holder in listOf(f.a, f.b)) {
            val placedHand = placed(holder, PlayZone.HAND)
            val hand = placedHand.copy(board = placedHand.board.copy(cards = placedHand.board.cards.map { if (it.id == id(9)) it.copy(owner = holder) else it }))
            val before = hand.copy(board = hand.board.copy(stackEffects = listOf(StackEffect(id(80), id(9))),
                containers = hand.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = it.cards + id(80)) else it }))
            for (viewer in listOf(f.a, f.b)) {
                assertEquals("Velvet Prism Engine pinged", format(PlayAction.Ping(ref, PingTarget(PingTargetKind.CARD, id(9))),
                    before = before, viewer = viewer).description)
                assertEquals(if (viewer == holder) "Velvet Prism Engine pinged" else "A card pinged",
                    format(PlayAction.Ping(ref, PingTarget(PingTargetKind.CARD, id(9))), before = hand, viewer = viewer).description)
            }
        }
    }

    @Test fun lookAndSearchCardsAreNamedOnlyToPanelAuthorUntilThisEventRevealsThem() {
        for (holder in listOf(f.a, f.b)) for (panelAuthor in listOf(f.a, f.b)) for (selection in listOf(ContentsSelection.TOP, ContentsSelection.ALL)) {
            val before = placed(holder, PlayZone.LIBRARY)
            val library = before.board.cards.first().containerId
            val opened = apply(before, panelAuthor, PlayAction.ShowContents(library, selection))
            val looking = opened.state!!
            val display = looking.board.contentsDisplays.single()
            val revealed = apply(looking, panelAuthor, PlayAction.RevealContentsCard(display.id, id(9)), 301)
            val dismissed = apply(looking, panelAuthor, PlayAction.DismissContents(display.id), 302)
            for (viewer in listOf(f.a, f.b)) {
                assertFalse(historyText(opened.mutation, before, viewer, names).description.contains("Velvet Prism Engine"))
                val subject = if (viewer == panelAuthor) "Velvet Prism Engine" else "A card"
                assertEquals("$subject tapped", format(PlayAction.SetTapped(id(9), true), before = looking, viewer = viewer).description,
                    "$selection / holder=$holder / panelAuthor=$panelAuthor / viewer=$viewer")
                assertEquals("Velvet Prism Engine revealed in panel", historyText(revealed.mutation, looking, viewer, names).description)
                assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = dismissed.state!!, viewer = viewer).description)
                // Rendering a later reveal cannot alter an earlier private-panel event.
                assertEquals("$subject tapped", format(PlayAction.SetTapped(id(9), true), before = looking, viewer = viewer).description)
            }
        }
    }

    @Test fun stackCopyAndControllerTextDoNotDescribeChangingTheSourceOwner() {
        assertEquals("A card copied → Stack top",
            format(PlayAction.Stack(StackAction.CopyEffect(id(9), GameplayRules.stackDigest(state)))).description)
        assertEquals("A card Stack controller → bob",
            format(PlayAction.Stack(StackAction.SetController(id(99), f.b))).description)
    }

    @Test fun shuffleLifeStepsRandomAndCommentArePlainEnglish() {
        assertEquals(HistoryText("Library shuffled", "Shuffle"), format(PlayAction.ShuffleLibrary(id(10), "secret seed")))
        assertEquals("Library shuffled", format(PlayAction.ShuffleLibrary(id(10), "secret"), author = f.b).description)
        assertEquals("Life 20 → 17", format(PlayAction.SetLife(f.a, "17")).description)
        assertEquals("bob's life 20 → 17", format(PlayAction.SetLife(f.b, "17")).description)
        assertEquals("Life 20 → 17", format(PlayAction.AdjustLife(f.a, "-3"), GameplayTransition(
            fields = listOf(GameplayFieldChange(f.a, GameplayField.LIFE, "20", "17")))).description)
        assertEquals("Moved to Draw", format(PlayAction.ChooseStep(TurnPosition.DRAW)).description)
        assertEquals("Moved to Draw", format(PlayAction.NextStep, GameplayTransition(fields = listOf(
            GameplayFieldChange(id(2), GameplayField.PHASE, "BEGINNING", "BEGINNING"),
            GameplayFieldChange(id(2), GameplayField.STEP, "UPKEEP", "DRAW")))).description)
        assertEquals("d6 → 4", format(PlayAction.RollDie(6, 4)).description)
        assertEquals("Heads", format(PlayAction.FlipCoin(CoinFace.HEADS)).description)
        assertEquals("Comment: hello there", format(PlayAction.EventComment(EventReference(id(2), id(5)), "hello\nthere")).description)
    }

    @Test fun drawNeverNamesAnOpponentsCardAndGroupsHaveCompactCounts() {
        val action = PlayAction.MoveCard(id(12), MoveSource(id(13), edge = MoveSourceEdge.TOP),
            MoveDestination(id(21), MoveInsertion.UNORDERED))
        val single = format(action, author = f.b)
        assertEquals("A card: Library → Hand", single.description)
        assertEquals("Draw", single.label)
        val texts = (1..7).associate { id(100 + it) to format(action, author = f.b) }
        val range = HistoryRange(id(100), id(100), texts.keys.toList(), texts.keys.first(), HistoryGlyph.DRAW,
            f.b, null, HistoryPoint(0f, 0f))
        assertEquals(HistoryText("7 cards: Library → Hand", "Draw ×7"), historyRangeText(range, texts))
        // Your own draws list every name; unseen cards collapse into a count after them.
        val seen = HistoryMove("Shock", "Library → Hand")
        val mixed = mapOf(id(201) to HistoryText("", "Draw", seen), id(202) to HistoryText("", "Draw", seen.copy(subject = "Opt")),
            id(203) to single, id(204) to single)
        val mixedRange = range.copy(nodeIds = mixed.keys.toList(), representativeId = id(201))
        assertEquals(HistoryText("Shock, Opt, 2 cards: Library → Hand", "Draw ×4"), historyRangeText(mixedRange, mixed))
        assertEquals("A card", historyMoveSubjects(listOf(null)))
        assertEquals("Shock, 1 card", historyMoveSubjects(listOf("Shock", null)))
    }

    @Test fun visibleCardsAreNamedButCoveredCardsAndOpponentsHandsAreNot() {
        val before = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == id(9)) it.copy(containerId = id(20), covered = false) else it
        }))
        val move = PlayAction.MoveCard(id(9), MoveSource(id(20)), MoveDestination(id(22), MoveInsertion.EMPTY))
        assertEquals("Velvet Prism Engine: Hand → Battlefield", format(move, before = before).description)
        assertEquals(HistoryText("Velvet Prism Engine tapped", "Tap"), format(PlayAction.SetTapped(id(9), true), before = before))
        val covered = before.copy(board = before.board.copy(cards = before.board.cards.map {
            it.copy(containerId = id(22), covered = true, explicitlyCovered = true)
        }))
        assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = covered).description)
        // Metadata owner is alice, but the card is in bob's hand. It must not grant visibility.
        val opponent = before.copy(board = before.board.copy(cards = before.board.cards.map { it.copy(containerId = id(21)) }))
        assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = opponent).description)
        assertEquals("A card inspected", format(PlayAction.InspectFace(id(9), id(11), id(21)), before = opponent).description)
    }

    @Test fun publicZonesUseTheSameFaceUpRulesAsTheTableIncludingLegacyCoveredFlags() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) {
            for (holder in listOf(f.a, f.b)) {
                val before = state.copy(board = state.board.copy(
                    containers = state.board.containers + PlayContainer(id(80), zone, holder),
                    cards = state.board.cards.map { it.copy(containerId = id(80), covered = true, explicitlyCovered = false) }))
                assertEquals("Velvet Prism Engine tapped", format(PlayAction.SetTapped(id(9), true),
                    before = before, author = f.b).description, "$zone / $holder")
                val hidden = before.copy(board = before.board.copy(cards = before.board.cards.map { it.copy(explicitlyCovered = true) }))
                assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true),
                    before = hidden, author = f.b).description, "$zone face down")
            }
        }
        // Default covered flags in one's own hand do not hide a face that the table displays.
        val ownHand = state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(containerId = id(20)) }))
        assertEquals("Velvet Prism Engine tapped", format(PlayAction.SetTapped(id(9), true), before = ownHand).description)
        for (container in listOf(id(10), id(13), id(21))) {
            val hidden = state.copy(board = state.board.copy(cards = state.board.cards.map {
                it.copy(containerId = container, covered = false, explicitlyCovered = false)
            }))
            assertEquals("A card tapped", format(PlayAction.SetTapped(id(9), true), before = hidden, author = f.b).description)
        }
    }

    @Test fun namesUseVisibilityBeforeOrAfterTheMutationButNotAHiddenFace() {
        val hidden = state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(containerId = id(21)) }))
        val move = PlayAction.MoveCard(id(9), MoveSource(id(21)), MoveDestination(id(23), MoveInsertion.EMPTY))
        val movement = GameplayTransition(movement = GameplayMoveChange(id(9), CardLocation(id(21)), CardLocation(id(23))))
        // The fixture's alice-owned card moves onto bob's battlefield, so only that zone is named as bob's.
        assertEquals("Velvet Prism Engine: Hand → bob's Battlefield", format(move, movement, hidden, f.b).description)
        val faceDown = movement.copy(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "false", "true")))
        assertEquals("A card: Hand → bob's Battlefield", format(move, faceDown, hidden, f.b).description)

        val public = hidden.copy(board = hidden.board.copy(cards = hidden.board.cards.map { it.copy(containerId = id(23)) }))
        val cover = GameplayTransition(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "false", "true")))
        assertEquals("Velvet Prism Engine face down", format(PlayAction.SetCovered(id(9), true), cover, public, f.b).description)
        val covered = public.copy(board = public.board.copy(cards = public.board.cards.map { it.copy(explicitlyCovered = true) }))
        val uncover = GameplayTransition(fields = listOf(GameplayFieldChange(id(9), GameplayField.EXPLICITLY_COVERED, "true", "false")))
        assertEquals("Velvet Prism Engine face up", format(PlayAction.SetCovered(id(9), false), uncover, covered, f.b).description)
        val toHand = PlayAction.MoveCard(id(9), MoveSource(id(23)), MoveDestination(id(21), MoveInsertion.UNORDERED))
        val returning = GameplayTransition(movement = GameplayMoveChange(id(9), CardLocation(id(23)), CardLocation(id(21))))
        assertEquals("Velvet Prism Engine: bob's Battlefield → Hand", format(toHand, returning, public, f.b).description)
        assertEquals("A card: bob's Battlefield → Hand", format(toHand, returning, covered, f.b).description)
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
        append(206, PlayAction.MoveCard(id(12), MoveSource(id(21)),
            MoveDestination(id(23), MoveInsertion.EMPTY, position = BoardPosition(1, 0)), faceDown = true))
        append(204, PlayAction.SetTapped(id(12), true))
        val hiddenTap = tree.currentNodeId
        for (viewer in listOf(f.a, f.b)) {
            val texts = historyTexts(tree, viewer, names)
            assertEquals("${if (viewer == f.b) "Velvet Prism Engine" else "A card"}: Library → Hand", texts.getValue(draw).description)
            assertEquals("Velvet Prism Engine tapped", texts.getValue(tap).description)
            assertEquals("A card tapped", texts.getValue(hiddenTap).description)
            assertEquals("Comment: ready", texts.getValue(comment).description)
            val timeline = HistoryTimelineLayout.events(HistoryTree(tree.initial.root.id, tree.nodes.values.toList(),
                tree.currentNodeId, "v1", texts = texts), tap)
            assertEquals(texts.getValue(tap).description, timeline.last().description)
        }
    }

    @Test fun tokensCombatAndCountersUseVisibleNamesWithoutInspectingCoveredFaces() {
        val token = PlayCard(id(60), TokenCatalog.blank.digest, f.a, id(22),
            TokenCatalog.blank.definition.faces.first().faceId, covered = false, token = true)
        val presence = TokenPresence(token, CardLocation(id(22)))
        assertEquals("Blank token created", format(PlayAction.CreateToken(TokenCatalog.blank),
            GameplayTransition(tokens = listOf(TokenChange(null, presence)))).description)
        val before = state.copy(definitions = state.definitions + TokenCatalog.blank, board = state.board.copy(
            cards = state.board.cards + token, combat = CombatArea(id(61), listOf(CombatPair(id(62),
                attackers = listOf(CombatOccurrence(id(63), token.id)))))))
        assertEquals("Blank removed from combat", format(PlayAction.Combat(CombatAction.Remove(id(61), id(63))), before = before).description)
        assertEquals("bob's counter (red) 0 → 3", format(PlayAction.SetCounter(CounterTarget.PLAYER, f.b,
            CounterColor.RED, "3")).description)
        assertEquals("Blank blue counter 0 → 0", format(PlayAction.ClearCardCounter(token.id, CounterColor.BLUE), before = before).description)
        assertEquals("Blank removed", format(PlayAction.RemoveToken(token.id), before = before).description)
        assertEquals("Result suggested → Draw", format(PlayAction.SuggestResult(SuggestedResult.DRAW)).description)
        assertEquals("Result suggested → Loss: “Infinite combo”",
            format(PlayAction.SuggestResult(SuggestedResult.LOSS, "Infinite combo")).description)
    }

    @Test fun everyActionFamilyHasShortLabelsWithoutWireIdsOrMutationDumps() {
        val ref = EventReference(id(2), id(5))
        val actions = listOf(
            PlayAction.SuggestResult(SuggestedResult.WIN), PlayAction.SuggestResult(SuggestedResult.LOSS), PlayAction.SuggestResult(SuggestedResult.DRAW),
            PlayAction.SetTapped(id(9), false), PlayAction.SetLife(f.a, "17"), PlayAction.AdjustLife(f.a, "1"),
            PlayAction.SetPlayerCounter(f.a, CounterColor.BLUE, "2"), PlayAction.AdjustPlayerCounter(f.a, CounterColor.BLUE, "1"),
            PlayAction.PassAttention, PlayAction.NextStep, PlayAction.EndTurn, PlayAction.ChooseStep(TurnPosition.PRECOMBAT_MAIN),
            PlayAction.RollDie(6, 4), PlayAction.FlipCoin(CoinFace.TAILS),
            PlayAction.MoveCard(id(9), MoveSource(id(10)), MoveDestination(id(20), MoveInsertion.UNORDERED)),
            PlayAction.ShuffleLibrary(id(10), "secret"), PlayAction.ShowContents(id(10), ContentsSelection.ALL),
            PlayAction.SetKeepTopRevealed(id(10), true), PlayAction.Mulligan(id(10), id(20), emptyList(), 6, "secret"),
            PlayAction.DismissContents(id(30)), PlayAction.RevealContentsCard(id(30), id(9)),
            PlayAction.ReorderContents(id(30), id(9)), PlayAction.EventComment(ref, "hello"),
            PlayAction.Ping(ref, PingTarget(PingTargetKind.PLAYER, f.b)), PlayAction.Unping(ref, PingTarget(PingTargetKind.CARD, id(9))),
            PlayAction.SetCorner(id(9), "2/3"), PlayAction.AdjustCorner(id(9), CornerPart.BOTH, 1), PlayAction.ResetCorner(id(9)),
            PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.RED, "3"),
            PlayAction.AdjustCounter(CounterTarget.PLAYER, f.a, CounterColor.RED, "1"),
            PlayAction.SetNote(id(9), "ready"), PlayAction.SetProjectionNote(id(30), "ready"),
            PlayAction.SetOwner(id(9), f.b), PlayAction.SetFace(id(9), id(11)),
            PlayAction.SetCovered(id(9), true), PlayAction.InspectFace(id(9), id(11), id(10)),
            PlayAction.ClearCardCounter(id(9), CounterColor.GRAY), PlayAction.CreateToken(TokenCatalog.blank),
            PlayAction.CloneToken(id(9)), PlayAction.RemoveToken(id(9)),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(9)), LinkEndpoint(LinkEndpointKind.PLAYER, f.b)),
            PlayAction.RemoveLink(id(30)), PlayAction.LinkProjections(ProjectionEndpoint(id(30)), ProjectionEndpoint(id(31))),
            PlayAction.RemoveProjectionLink(id(30)), PlayAction.Combat(CombatAction.Open), PlayAction.Combat(CombatAction.Close(id(30))),
            PlayAction.Combat(CombatAction.Declare(id(30), id(9))),
            PlayAction.Combat(CombatAction.Place(id(9), CombatDestination(id(30), CombatRole.BLOCKER))),
            PlayAction.Combat(CombatAction.Remove(id(30), id(31))), PlayAction.Stack(StackAction.AddEffect(id(9), "secret digest")),
            PlayAction.Stack(StackAction.RemoveEffect(id(30))), PlayAction.Stack(StackAction.ReorderEffect(id(30), IdentityGap(), IdentityGap())),
            PlayAction.Stack(StackAction.CopyEffect(id(30), "secret")), PlayAction.Stack(StackAction.SetController(id(30), f.b)),
            PlayAction.Stack(StackAction.MarkText(id(30), null)), PlayAction.Stack(StackAction.ToggleTextMark(id(30), 0)))
        for (action in actions) {
            val result = format(action)
            assertFalse(result.description.startsWith("alice "), action::class.simpleName)
            assertTrue(result.description.isNotBlank() && result.description.length <= 180, result.description)
            assertTrue(result.label.split(' ').size in 1..3, result.label)
            assertFalse(result.description.contains("00000000-"), result.description)
            assertFalse(result.description.contains("GameplayTransition"), result.description)
            assertFalse(result.description.contains("secret"), result.description)
        }
    }

    @Test fun missingContextUnsupportedSchemaAndUserTextNeverExposeIdentifiers() {
        assertEquals(HistoryText("Initial state", "Start"), historyText(null))
        assertEquals("A card tapped", historyText(event(PlayAction.SetTapped(id(999), true))).description)
        for (version in 1..2) assertEquals("A card tapped",
            historyText(event(PlayAction.SetTapped(id(999), true)).copy(schemaVersion = version), state, f.a, names).description)
        assertEquals("Table changed", historyText(event(PlayAction.SetTapped(id(9), true)).copy(schemaVersion = 3),
            state, f.a, names).description)
        assertEquals("Comment: see [reference]", format(PlayAction.EventComment(EventReference(id(2), id(5)), "see ${id(99)}")).description)
        assertTrue(format(PlayAction.EventComment(EventReference(id(2), id(5)), "x".repeat(1000))).description.length <= 180)
    }

    /** bob draws four named cards in a row, then alice taps: a real tree, indexed and laid out by the explorer. */
    private fun drawTree(): Pair<GameplayTree, List<String>> {
        val names = listOf("Shock", "Opt", "Ponder", "Brainstorm")
        val definitions = names.mapIndexed { n, name -> CardDefinition(name, listOf(DefinitionFace(id(600 + n), name))) }
        val cards = definitions.mapIndexed { n, definition -> PlayCard(id(610 + n), DeckJson.digest(definition), f.b, id(13), id(600 + n)) }
        val base = state
        val initial = base.copy(definitions = base.definitions + definitions.map { SubmittedDefinition(DeckJson.digest(it), it) },
            board = base.board.copy(cards = base.board.cards.filter { it.containerId != id(13) } + cards,
                containers = base.board.containers.map { if (it.id == id(13)) it.copy(cards = cards.map(PlayCard::id)) else it }))
        var tree = GameplayTree(initial)
        val draws = cards.mapIndexed { n, card ->
            tree = assertNotNull(tree.play(f.b, GameCommand(id(700 + n), GameScope(initial.gameId, playId = initial.id),
                GameAction.Play(PlayAction.MoveCard(card.id, MoveSource(id(13), edge = MoveSourceEdge.TOP),
                    MoveDestination(id(21), MoveInsertion.UNORDERED))), id(14), "1")).tree)
            tree.currentNodeId
        }
        return tree to draws
    }

    @Test fun treeDialogChipsAndSelectedLineShowTheWholeGroupOfMoves() {
        val (tree, draws) = drawTree()
        val explorer = HistoryExplorerController(java.util.concurrent.Executor { it.run() }, { it() })
        try {
            explorer.update(tree)
            val graph = assertNotNull(explorer.state.graph)
            val chip = assertNotNull(graph.rangeOf[draws.last()])
            assertEquals(draws, graph.rangesById.getValue(chip).nodeIds, "the four draws form one chip")
            // The drawing player sees every card; the other player sees only how many.
            assertEquals(HistoryText("Shock, Opt, Ponder, Brainstorm: Library → Hand", "Draw ×4"),
                graph.chipText(chip, historyTexts(tree, f.b, names)))
            val opponent = historyTexts(tree, f.a, names)
            assertEquals(HistoryText("4 cards: Library → Hand", "Draw ×4"), graph.chipText(chip, opponent))
            // Without board context nothing is invented: no names and no zones.
            assertEquals("4 cards moved", graph.chipText(chip, emptyMap())?.description)
            // The selected line describes the chip's group, or one event when a point inside the group is selected.
            assertEquals("4 cards: Library → Hand", graph.selectedText(draws.last(), opponent))
            assertEquals("A card: Library → Hand", graph.selectedText(draws.first(), opponent))
            assertEquals("Shock: Library → Hand", graph.selectedText(draws.first(), historyTexts(tree, f.b, names)))
        } finally { explorer.close() }
    }

    @Test fun groupTextIsCappedWithAnEllipsis() {
        val texts = (1..40).associate { n -> id(800 + n) to HistoryText("", "Draw", HistoryMove("Card number $n with a long name", "Library → Hand")) }
        val range = HistoryRange(id(800), id(800), texts.keys.toList(), texts.keys.last(), HistoryGlyph.DRAW, f.b, null, HistoryPoint(0f, 0f))
        val text = historyRangeText(range, texts)
        assertTrue(text.description.length in HISTORY_RANGE_TEXT_MAX - 2..HISTORY_RANGE_TEXT_MAX, text.description)
        assertTrue(text.description.startsWith("Card number 1 with a long name, Card number 2"))
        assertTrue(text.description.endsWith("…"))
        assertEquals("Draw ×40", text.label)
        val short = range.copy(nodeIds = texts.keys.take(2))
        assertEquals("Card number 1 with a long name, Card number 2 with a long name: Library → Hand",
            historyRangeText(short, texts).description)
    }

    @Test fun movesWithoutBoardContextSayOnlyThatACardMoved() {
        val move = PlayAction.MoveCard(id(9), MoveSource(id(20)), MoveDestination(id(13), MoveInsertion.TOP))
        assertEquals(HistoryText("A card moved", "Move", HistoryMove(null, null)), historyText(event(move)))
        // With the board known, zones (and the library edge) are shown as before.
        assertEquals("A card: Hand → Library top", format(move, author = f.b).description)
        // The timeline strip and the tree dialog fall back to the same text when no board-aware text exists.
        val (tree, draws) = drawTree()
        val strip = HistoryTimelineLayout.events(HistoryTree(tree.initial.root.id, tree.nodes.values.toList(), tree.currentNodeId, "v1"),
            tree.currentNodeId)
        assertEquals(listOf("Initial state") + List(4) { "A card moved" }, strip.map { it.description })
        assertEquals("A card moved", HistoryGraphLayout.build(tree).eventsById.getValue(draws.first()).description)
    }
}
