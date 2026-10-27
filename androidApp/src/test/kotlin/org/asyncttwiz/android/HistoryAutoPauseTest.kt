package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.fixtures.ScaleGames as F
import org.junit.Test
import kotlin.test.*

class HistoryAutoPauseTest {
    @Test fun attentionLooksAheadOneEventEvenBeyondGroupAndIgnoresInactiveBranches() {
        val attention = node(PlayAction.PassAttention, GameplayTransition(attention = AttentionChange(opponent, me)))
        val own = node(PlayAction.RollDie(6, 1), author = me, id = "own", parent = attention.id)
        val theirs = node(PlayAction.RollDie(6, 2), id = "theirs", parent = attention.id)
        val enabled = setOf(PlaybackAutoPause.ATTENTION)
        for ((head, expected) in listOf(own.id to false, theirs.id to true, attention.id to true)) {
            val tree = HistoryTree("root", listOf(attention, own, theirs), head, "v")
            assertEquals(expected, tree.autoPauseBetween("root", attention.id, me, enabled, board), head)
        }
        // Look at the immediate event, not the next group, the group author, or a later own action.
        val laterOwn = own.copy(id = "later", parentId = theirs.id)
        val tree = HistoryTree("root", listOf(attention, theirs, laterOwn), laterOwn.id, "v")
        assertTrue(tree.autoPauseBetween("root", attention.id, me, enabled, board))
        val sameGroup = GameplayGroup("group", opponent, attention.id, theirs.id, "move", null, "2")
        val ownThenTheirs = theirs.copy(parentId = own.id)
        val grouped = HistoryTree("root", listOf(attention, own, ownThenTheirs).map {
            it.copy(bookkeeping = GameplayBookkeeping(null, "branch", sameGroup))
        }, theirs.id, "v")
        assertFalse(grouped.autoPauseBetween("root", theirs.id, me, enabled, board))
        // Suppression is specific to attention, not a blanket suppression of other triggers.
        val ping = attention.copy(action = PlayAction.Ping(reference, PingTarget(PingTargetKind.AREA, "mine")))
        assertTrue(HistoryTree("root", listOf(ping, own), own.id, "v").autoPauseBetween("root", ping.id,
            me, setOf(PlaybackAutoPause.PINGS_COMMENTS), board))
    }

    @Test fun alreadyActedAttentionDoesNotPausePlaybackAfterAnimation() {
        val attention = node(PlayAction.PassAttention, GameplayTransition(attention = AttentionChange(opponent, me)))
        for (nextAuthor in listOf(me, opponent)) {
            val next = node(PlayAction.RollDie(6, 1), author = nextAuthor, id = "next", parent = attention.id)
            val player = HistoryPlayback(HistoryTree("root", listOf(attention, next), next.id, "v"))
            player.openFrom("root")
            player.next(true, me, setOf(PlaybackAutoPause.ATTENTION), board)
            assertEquals(attention.id, player.position.cursorId)
            assertEquals(nextAuthor == opponent, player.pauseAfterAnimation())
            assertEquals(nextAuthor == me, player.position.playing)
        }
    }

    @Test fun toggleMayPauseUnderRevealButAutomaticallyExposedTopsNeverDo() {
        for (enabled in listOf(false, true)) assertEquals(setOf(PlaybackAutoPause.REVEAL),
            triggers(PlayAction.SetKeepTopRevealed("theirs", enabled)))
        val exposure = GameplayDisclosure(other.id, "face", "theirs", listOf(me, opponent), automatic = true)
        for (action in listOf(PlayAction.ShuffleLibrary("theirs", "seed"),
            PlayAction.MoveCard(other.id, MoveSource("theirs"), MoveDestination("hand", MoveInsertion.UNORDERED)),
            PlayAction.MoveCard(other.id, MoveSource("hand"), MoveDestination("theirs", MoveInsertion.TOP)))) {
            assertFalse(PlaybackAutoPause.REVEAL in triggers(action, GameplayTransition(disclosures = listOf(exposure))))
        }
        assertEquals(setOf(PlaybackAutoPause.REVEAL), triggers(PlayAction.InspectFace(other.id, "face", "theirs"),
            GameplayTransition(disclosures = listOf(exposure.copy(automatic = false)))))
    }

    private val me = "me"
    private val opponent = "opponent"
    private val own = PlayCard("own", "definition", me, "mine", "face")
    private val controlled = own.copy(id = "controlled", owner = opponent)
    private val other = own.copy(id = "other", owner = opponent, containerId = "theirs")
    private val board = BoardState(listOf(PlayPlayer(me), PlayPlayer(opponent)), listOf(own, controlled, other),
        listOf(PlayContainer("mine", PlayZone.BATTLEFIELD, me), PlayContainer("theirs", PlayZone.BATTLEFIELD, opponent),
            PlayContainer("stack", PlayZone.STACK)), TurnState(opponent))
    private val reference = EventReference("play", "root")
    private fun node(action: PlayAction, transition: GameplayTransition = GameplayTransition(), author: String = opponent,
        id: String = "one", parent: String = "root") = GameplayMutation(id, parent, "branch",
        java.util.UUID.nameUUIDFromBytes("request/$id".toByteArray()).toString(), author, null,
        action, transition)
    private fun triggers(action: PlayAction, transition: GameplayTransition = GameplayTransition(), author: String = opponent) =
        HistoryAutoPause(me, board).triggers(node(action, transition, author))

    @Test fun opponentOnlyAndSeparateDefaultOffAttackers() {
        val actions = listOf(
            node(PlayAction.RollDie(6, 4)),
            node(PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, own.id))),
            node(PlayAction.EventComment(reference, "hello")),
            node(PlayAction.ShowContents("theirs", ContentsSelection.TOP, reveal = true),
                GameplayTransition(disclosures = listOf(GameplayDisclosure(other.id, "face", "theirs", listOf(me))))),
            node(PlayAction.SetTapped(own.id, true), GameplayTransition(fields = listOf(
                GameplayFieldChange(own.id, GameplayField.TAPPED, "false", "true")))),
            node(PlayAction.PassAttention, GameplayTransition(attention = AttentionChange(opponent, me))),
            node(PlayAction.Stack(StackAction.RemoveEffect("effect")), GameplayTransition(combatStack = CombatStackTransition(
                effects = listOf(StackEffectChange("effect", StackEffect("effect", other.id), null))))),
            node(PlayAction.Combat(CombatAction.Declare("combat", other.id)))
        )
        for (event in actions) {
            assertTrue(HistoryAutoPause(me, board).triggers(event).isNotEmpty(), event.action.toString())
            assertTrue(HistoryAutoPause(me, board).triggers(event.copy(author = me)).isEmpty(), event.action.toString())
        }
        assertFalse(PlaybackAutoPause.ATTACKERS in PlaybackAutoPause.defaults)
        assertEquals(setOf(PlaybackAutoPause.ATTACKERS), triggers(actions.last().action))
        assertFalse(PlaybackAutoPause.RANDOM in PlaybackAutoPause.defaults)
        assertEquals(5, PlaybackAutoPause.defaults.size)
    }

    @Test fun revealMustShowCardsToViewerAndUnpingDoesNotStop() {
        val display = ContentsDisplay("display", opponent, "theirs", ContentsSelection.TOP, 1, false, listOf(other.id))
        val show = PlayAction.ShowContents("theirs", ContentsSelection.TOP)
        assertTrue(triggers(show, GameplayTransition(contents = ContentsDisplayChange(to = display))).isEmpty())
        assertEquals(setOf(PlaybackAutoPause.REVEAL), triggers(show,
            GameplayTransition(contents = ContentsDisplayChange(to = display.copy(reveal = true)))))
        assertTrue(triggers(show, GameplayTransition(contents = ContentsDisplayChange(to = display.copy(cardIds = emptyList(), reveal = true)))).isEmpty())
        val disclosure = GameplayDisclosure(other.id, "face", "theirs", listOf(me))
        assertEquals(setOf(PlaybackAutoPause.REVEAL), triggers(PlayAction.RevealContentsCard("display", other.id),
            GameplayTransition(disclosures = listOf(disclosure))))
        assertTrue(triggers(show, GameplayTransition(disclosures = listOf(disclosure.copy(viewers = listOf(opponent))))).isEmpty())
        assertTrue(triggers(PlayAction.Unping(reference, PingTarget(PingTargetKind.CARD, own.id))).isEmpty())
        assertEquals(setOf(PlaybackAutoPause.RANDOM), triggers(PlayAction.FlipCoin(CoinFace.HEADS)))
    }

    @Test fun stackEntryAndProjectionChangesIncludeMovesButNotTextMarks() {
        for ((from, to, expected) in listOf(Triple("theirs", "stack", setOf(PlaybackAutoPause.STACK_ADDED)),
                Triple("stack", "theirs", setOf(PlaybackAutoPause.STACK_REMOVED)), Triple("stack", "stack", emptySet()))) {
            val move = GameplayMoveChange(other.id, CardLocation(from), CardLocation(to))
            assertEquals(expected, triggers(PlayAction.MoveCard(other.id, MoveSource(from),
                MoveDestination(to, MoveInsertion.TOP)), GameplayTransition(movement = move)))
        }
        assertEquals(setOf(PlaybackAutoPause.STACK_ADDED), triggers(PlayAction.Stack(StackAction.AddEffect(other.id, "digest")),
            GameplayTransition(combatStack = CombatStackTransition(order = listOf(StackOrderChange("effect", null, IdentityGap()))))))
        assertEquals(setOf(PlaybackAutoPause.STACK_REMOVED), triggers(PlayAction.Stack(StackAction.RemoveEffect("effect")),
            GameplayTransition(combatStack = CombatStackTransition(order = listOf(StackOrderChange("effect", IdentityGap(), null))))))
        assertTrue(triggers(PlayAction.Stack(StackAction.MarkText(other.id, 0)),
            GameplayTransition(combatStack = CombatStackTransition(textMarks = listOf(StackTextMarkChange(other.id, null, null))))).isEmpty())
    }

    @Test fun opponentTurningFaceDownCardFaceUpRevealsOnlyToNewViewers() {
        val covered = F.play(GameplayTree(F.root()), PlayAction.SetCovered(F.id(12), true), 601, F.b)
        val board = assertNotNull(covered.preparedState(covered.currentNodeId)).board
        val revealed = F.play(covered, PlayAction.SetCovered(F.id(12), false), 602, F.b)
        val event = revealed.nodes.getValue(revealed.currentNodeId)
        assertTrue(event.transition.disclosures.any { F.a in it.viewers })
        assertEquals(setOf(PlaybackAutoPause.REVEAL), HistoryAutoPause(F.a, board).triggers(event))
        assertTrue(HistoryAutoPause(F.b, board).triggers(event).isEmpty(), "Own reveals do not pause")
        assertTrue(HistoryAutoPause(F.a, covered.initial.board).triggers(covered.nodes.getValue(covered.currentNodeId)).isEmpty(),
            "Turning a card face down does not reveal it")
        assertTrue(HistoryAutoPause(F.a, board).triggers(event.copy(transition = event.transition.copy(
            disclosures = event.transition.disclosures.map { it.copy(viewers = listOf(F.b)) }))).isEmpty(),
            "Uncovering must disclose the face to the viewer")
    }

    @Test fun switchingAlreadyPublicFaceAndMovingToPublicZoneDoNotReveal() {
        val base = F.root()
        val definition = base.definitions.single().definition.let { it.copy(faces = it.faces +
            DefinitionFace(F.id(603), "Other face", typeLine = "Creature")) }
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val initial = base.copy(definitions = base.definitions + submitted, board = base.board.copy(
            cards = base.board.cards.map { if (it.id == F.id(12)) it.copy(definition = submitted.digest) else it }))
        val switched = F.play(GameplayTree(initial), PlayAction.SetFace(F.id(12), F.id(603)), 604, F.b)
        val event = switched.nodes.getValue(switched.currentNodeId)
        assertTrue(event.transition.disclosures.any { F.a in it.viewers }, "Public face changes still carry disclosures")
        val classifier = HistoryAutoPause(F.a, initial.board)
        assertTrue(classifier.triggers(event).isEmpty())
        val moved = F.play(switched, PlayAction.MoveCard(F.id(12), MoveSource(F.id(10), initial.board.cards.single { it.id == F.id(12) }.placement),
            MoveDestination(F.id(13), MoveInsertion.UNORDERED)), 605, F.b)
        assertTrue(classifier.triggers(moved.nodes.getValue(moved.currentNodeId)).isEmpty())
    }

    @Test fun typicalOpponentTurnOnlyPausesOnCastAndAttentionWithDefaults() {
        val land = CardDefinition("Forest", listOf(DefinitionFace(F.id(401), "Forest", typeLine = "Basic Land — Forest")))
        val creature = CardDefinition("Bear", listOf(DefinitionFace(F.id(402), "Bear", typeLine = "Creature — Bear")))
        val definitions = listOf(land, creature).map { SubmittedDefinition(DeckJson.digest(it), it) }
        val hand = F.id(403)
        val cards = definitions.mapIndexed { index, d -> PlayCard(F.id(404 + index), d.digest, F.b, hand, d.definition.faces.single().faceId) }
        val base = F.root()
        var tree = GameplayTree(base.copy(definitions = base.definitions + definitions, board = base.board.copy(
            cards = base.board.cards + cards, containers = base.board.containers + PlayContainer(hand, PlayZone.HAND, F.b, cards.map { it.id }),
            turn = TurnState(F.b))))
        val classifier = HistoryAutoPause(F.a, tree.initial.board, definitions = tree.initial.definitions)
        val results = mutableListOf<Set<PlaybackAutoPause>>()
        fun step(action: PlayAction, n: Int) {
            tree = F.play(tree, action, n, F.b)
            results += classifier.triggers(tree.nodes.getValue(tree.currentNodeId))
        }
        step(PlayAction.MoveCard(cards[0].id, MoveSource(hand), MoveDestination(F.id(10), MoveInsertion.EMPTY, BoardPosition(0, 2))), 501)
        assertTrue(tree.nodes.getValue(tree.currentNodeId).transition.disclosures.isNotEmpty(), "Land becoming public reproduces the original reveal bug")
        step(PlayAction.MoveCard(cards[1].id, MoveSource(hand), MoveDestination(F.id(14), MoveInsertion.TOP),
            expectedStackDigest = GameplayRules.stackDigest(tree.preparedState(tree.currentNodeId)!!)), 502)
        step(PlayAction.MoveCard(cards[1].id, MoveSource(F.id(14)), MoveDestination(F.id(10), MoveInsertion.EMPTY, BoardPosition(2, 2))), 503)
        step(PlayAction.Combat(CombatAction.Open), 504)
        val combat = tree.preparedState(tree.currentNodeId)!!.board.combat!!.id
        step(PlayAction.Combat(CombatAction.Declare(combat, cards[1].id)), 505)
        step(PlayAction.PassAttention, 506)
        assertEquals(listOf(emptySet(), setOf(PlaybackAutoPause.STACK_ADDED), emptySet(), emptySet(), emptySet(),
            setOf(PlaybackAutoPause.ATTENTION)), results.map { it.intersect(PlaybackAutoPause.defaults) })
        assertEquals(setOf(PlaybackAutoPause.LAND_FROM_HAND), results.first())
        assertEquals(setOf(PlaybackAutoPause.STACK_REMOVED), results[2])
    }

    @Test fun handBattlefieldTogglesAreIndependentDefaultOffAndExplicitInspectStillReveals() {
        assertTrue(listOf(PlaybackAutoPause.LAND_FROM_HAND, PlaybackAutoPause.NONLAND_FROM_HAND, PlaybackAutoPause.STACK_REMOVED)
            .none { it in PlaybackAutoPause.defaults })
        for ((type, expected) in listOf("Land — Forest" to PlaybackAutoPause.LAND_FROM_HAND, "Creature — Landshark" to PlaybackAutoPause.NONLAND_FROM_HAND)) {
            val definitions = listOf(SubmittedDefinition(other.definition, CardDefinition("Card", listOf(DefinitionFace(other.faceId, "Card", typeLine = type)))))
            val fromHand = board.copy(containers = board.containers + PlayContainer("hand", PlayZone.HAND, opponent))
            val move = node(PlayAction.MoveCard(other.id, MoveSource("hand"), MoveDestination("theirs", MoveInsertion.TOP)),
                GameplayTransition(movement = GameplayMoveChange(other.id, CardLocation("hand"), CardLocation("theirs")),
                    disclosures = listOf(GameplayDisclosure(other.id, other.faceId, "theirs", listOf(me)))))
            assertEquals(setOf(expected), HistoryAutoPause(me, fromHand, definitions = definitions).triggers(move))
            assertTrue(HistoryAutoPause(me, fromHand, definitions = definitions).triggers(move.copy(author = me)).isEmpty())
        }
        assertEquals(setOf(PlaybackAutoPause.REVEAL), triggers(PlayAction.InspectFace(other.id, other.faceId, "theirs"),
            GameplayTransition(disclosures = listOf(GameplayDisclosure(other.id, other.faceId, "theirs", listOf(me))))))
    }

    @Test fun panelResolutionsAndLibraryReordersOfYourCardsStop() {
        val display = ContentsDisplay("look", opponent, "mine", ContentsSelection.TOP, 1, false, listOf(own.id))
        assertTrue(PlaybackAutoPause.YOURS in triggers(PlayAction.DismissContents("look"),
            GameplayTransition(contents = ContentsDisplayChange(display, null))))
        assertTrue(PlaybackAutoPause.YOURS in triggers(PlayAction.ShuffleLibrary("mine", "seed"),
            GameplayTransition(libraryOrder = LibraryOrderChange("mine", listOf(own.id), listOf(own.id)))))
        assertTrue(triggers(PlayAction.ShuffleLibrary("theirs", "seed"),
            GameplayTransition(libraryOrder = LibraryOrderChange("theirs", listOf(other.id), listOf(other.id)))).isEmpty())
    }

    @Test fun lifeCountersAndOwnedOrControlledCardEditsAndDestruction() {
        for (field in listOf(GameplayField.LIFE, GameplayField.COUNTER, GameplayField.PLAYER_COUNTER)) {
            assertEquals(setOf(PlaybackAutoPause.YOURS), triggers(PlayAction.AdjustLife(me, "-1"),
                GameplayTransition(fields = listOf(GameplayFieldChange(me, field, "20", "19")))))
            assertTrue(triggers(PlayAction.AdjustLife(opponent, "-1"),
                GameplayTransition(fields = listOf(GameplayFieldChange(opponent, field, "20", "19")))).isEmpty())
        }
        for (card in listOf(own, controlled)) {
            assertEquals(setOf(PlaybackAutoPause.YOURS), triggers(PlayAction.CloneToken(card.id)))
            assertEquals(setOf(PlaybackAutoPause.YOURS), triggers(PlayAction.Stack(StackAction.AddEffect(card.id, "digest"))))
            for (field in listOf(GameplayField.TAPPED, GameplayField.COUNTER, GameplayField.NOTE, GameplayField.OWNER, GameplayField.FACE)) {
                assertTrue(PlaybackAutoPause.YOURS in triggers(PlayAction.SetNote(card.id, "note"),
                    GameplayTransition(fields = listOf(GameplayFieldChange(card.id, field, "old", "new")))))
            }
            assertTrue(PlaybackAutoPause.YOURS in triggers(PlayAction.MoveCard(card.id, MoveSource("mine"),
                MoveDestination("theirs", MoveInsertion.TOP)), GameplayTransition(movement = GameplayMoveChange(
                card.id, CardLocation("mine"), CardLocation("theirs")))))
            assertTrue(PlaybackAutoPause.YOURS in triggers(PlayAction.RemoveToken(card.id), GameplayTransition(tokens = listOf(
                TokenChange(TokenPresence(card, CardLocation("mine")), null)))))
        }
        assertTrue(triggers(PlayAction.SetTapped(other.id, true), GameplayTransition(fields = listOf(
            GameplayFieldChange(other.id, GameplayField.TAPPED, "false", "true")))).isEmpty())
        val effectBoard = board.copy(stackEffects = listOf(StackEffect("effect", other.id, controller = me)))
        assertTrue(PlaybackAutoPause.YOURS in HistoryAutoPause(me, effectBoard).triggers(node(
            PlayAction.Stack(StackAction.RemoveEffect("effect")), GameplayTransition(combatStack = CombatStackTransition(
                effects = listOf(StackEffectChange("effect", effectBoard.stackEffects.single(), null)))))))
    }

    @Test fun attentionMustBecomeYoursAndAttackersCanBeEnabledIndependently() {
        assertEquals(setOf(PlaybackAutoPause.ATTENTION), triggers(PlayAction.NextStep,
            GameplayTransition(attention = AttentionChange(opponent, me))))
        assertTrue(triggers(PlayAction.NextStep, GameplayTransition(attention = AttentionChange(me, me))).isEmpty())
        assertTrue(triggers(PlayAction.PassAttention, GameplayTransition(attention = AttentionChange(me, opponent))).isEmpty())
        assertTrue(HistoryAutoPause(me, board.copy(turn = TurnState(me))).triggers(node(PlayAction.NextStep,
            GameplayTransition(attention = AttentionChange(null, me)))).isEmpty())
        assertEquals(setOf(PlaybackAutoPause.ATTACKERS), triggers(PlayAction.Combat(CombatAction.Place(other.id,
            CombatDestination("combat", CombatRole.ATTACKER)))))
        assertTrue(triggers(PlayAction.Combat(CombatAction.Place(other.id,
            CombatDestination("combat", CombatRole.BLOCKER)))).isEmpty())
    }

    @Test fun classificationFoldsOwnershipAndControlChangesInsideGroup() {
        val classifier = HistoryAutoPause(me, board)
        classifier.triggers(node(PlayAction.SetOwner(other.id, me), GameplayTransition(fields = listOf(
            GameplayFieldChange(other.id, GameplayField.OWNER, opponent, me))), author = me))
        assertEquals(setOf(PlaybackAutoPause.YOURS), classifier.triggers(node(PlayAction.SetTapped(other.id, true),
            GameplayTransition(fields = listOf(GameplayFieldChange(other.id, GameplayField.TAPPED, "false", "true"))))))
        val control = HistoryAutoPause(me, board)
        control.triggers(node(PlayAction.MoveCard(other.id, MoveSource("theirs"), MoveDestination("mine", MoveInsertion.TOP)),
            GameplayTransition(movement = GameplayMoveChange(other.id, CardLocation("theirs"), CardLocation("mine"))), author = me))
        assertTrue(PlaybackAutoPause.YOURS in control.triggers(node(PlayAction.SetNote(other.id, "note"),
            GameplayTransition(fields = listOf(GameplayFieldChange(other.id, GameplayField.NOTE, "", "note"))))))
    }

    @Test fun pauseIsAfterAnimationIncludesEarlierGroupMembersAndResumesWithoutRetriggering() {
        val ping = node(PlayAction.Ping(reference, PingTarget(PingTargetKind.AREA, "mine")))
        val tap = node(PlayAction.SetTapped(other.id, true), id = "two", parent = ping.id)
        val group = GameplayGroup("group", opponent, ping.id, tap.id, "tap", null, "2")
        val end = node(PlayAction.FlipCoin(CoinFace.HEADS), id = "three", parent = tap.id)
        val tree = HistoryTree("root", listOf(ping, tap).map { it.copy(bookkeeping = GameplayBookkeeping(null, "branch", group)) } + end,
            end.id, "version")
        val player = HistoryPlayback(tree)
        player.openFrom("root")
        player.next(true, me, board = board)
        assertEquals(tap.id, player.position.cursorId)
        assertTrue(player.position.playing) // Still animating.
        assertTrue(player.pauseAfterAnimation())
        assertFalse(player.position.playing)
        player.next(true, me, board = board)
        assertEquals(tap.id, player.position.cursorId)
        player.play()
        assertFalse(player.pauseAfterAnimation())
        player.next(true, me, board = board)
        assertEquals(end.id, player.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, player.position.mode) // Final event is still animating.
        assertFalse(player.pauseAfterAnimation()) // Terminal events never auto-pause.
        assertTrue(player.finishAnimation(end.id))
        assertEquals(HistoryMode.LIVE, player.position.mode)
    }

    @Test fun disabledTriggersAndManualStepsDoNotAutoPauseAndOnlyTraversedMembersAreInspected() {
        val first = node(PlayAction.RollDie(6, 1))
        val second = node(PlayAction.SetTapped(other.id, true), id = "two", parent = first.id)
        val group = GameplayGroup("group", opponent, first.id, second.id, "tap", null, "2")
        val tree = HistoryTree("root", listOf(first, second).map { it.copy(bookkeeping = GameplayBookkeeping(null, "branch", group)) }, second.id, "v")
        assertFalse(tree.autoPauseBetween("root", second.id, me, PlaybackAutoPause.defaults, board))
        assertTrue(tree.autoPauseBetween("root", second.id, me, setOf(PlaybackAutoPause.RANDOM), board))
        assertFalse(tree.autoPauseBetween(first.id, second.id, me, PlaybackAutoPause.defaults, board))
        val player = HistoryPlayback(tree)
        player.openFrom("root"); player.next(true, me, emptySet(), board)
        assertFalse(player.pauseAfterAnimation())
        player.finishAnimation(second.id)
        assertEquals(HistoryMode.LIVE, player.position.mode)
        player.scrub("root"); player.next(viewer = me, board = board)
        assertFalse(player.pauseAfterAnimation())
    }
}
