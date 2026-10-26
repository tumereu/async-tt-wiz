package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryAutoPauseTest {
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
        assertEquals(6, PlaybackAutoPause.defaults.size)
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
        for ((from, to) in listOf("theirs" to "stack", "stack" to "theirs", "stack" to "stack")) {
            val move = GameplayMoveChange(other.id, CardLocation(from), CardLocation(to))
            assertTrue(PlaybackAutoPause.STACK in triggers(PlayAction.MoveCard(other.id, MoveSource(from),
                MoveDestination(to, MoveInsertion.TOP)), GameplayTransition(movement = move)))
        }
        assertEquals(setOf(PlaybackAutoPause.STACK), triggers(PlayAction.Stack(StackAction.AddEffect(other.id, "digest")),
            GameplayTransition(combatStack = CombatStackTransition(order = listOf(StackOrderChange("effect", null, IdentityGap()))))))
        assertFalse(PlaybackAutoPause.STACK in triggers(PlayAction.Stack(StackAction.MarkText(other.id, 0)),
            GameplayTransition(combatStack = CombatStackTransition(textMarks = listOf(StackTextMarkChange(other.id, null, null))))))
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
        assertEquals(HistoryMode.PLAYBACK, player.position.mode) // Matching head cannot escape to live.
        assertTrue(player.pauseAfterAnimation())
        player.play(); player.next(true, me, board = board)
        assertEquals(HistoryMode.LIVE, player.position.mode)
    }

    @Test fun disabledTriggersAndManualStepsDoNotAutoPauseAndOnlyTraversedMembersAreInspected() {
        val first = node(PlayAction.RollDie(6, 1))
        val second = node(PlayAction.SetTapped(other.id, true), id = "two", parent = first.id)
        val group = GameplayGroup("group", opponent, first.id, second.id, "tap", null, "2")
        val tree = HistoryTree("root", listOf(first, second).map { it.copy(bookkeeping = GameplayBookkeeping(null, "branch", group)) }, second.id, "v")
        assertTrue(tree.autoPauseBetween("root", second.id, me, PlaybackAutoPause.defaults, board))
        assertFalse(tree.autoPauseBetween(first.id, second.id, me, PlaybackAutoPause.defaults, board))
        val player = HistoryPlayback(tree)
        player.openFrom("root"); player.next(true, me, emptySet(), board)
        assertFalse(player.pauseAfterAnimation())
        assertEquals(HistoryMode.LIVE, player.position.mode)
        player.scrub("root"); player.next(viewer = me, board = board)
        assertFalse(player.pauseAfterAnimation())
    }
}
