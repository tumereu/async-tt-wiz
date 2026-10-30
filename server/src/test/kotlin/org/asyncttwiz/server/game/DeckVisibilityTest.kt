package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.accounts.AccountSettingsService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The account's deck visibility default sets each new deck selection's "Show deck name & art" flag. */
class DeckVisibilityTest {
    private fun id() = UUID.randomUUID().toString()

    private class Stream(val games: GameService, val actor: AccountService.Principal, val gameId: String) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, gameId, UUID.randomUUID().toString())
        fun execute(action: GameAction): GameResult {
            val summary = games.head(actor, gameId).summary
            val scope = if (action is GameAction.Play || action is GameAction.AcceptResult) GameScope(gameId, playId = summary.playId)
                else GameScope(gameId, summary.preparationRound, if (action == GameAction.AcceptInvitation) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(actor, hello, connection, command) is GameReceipt); assertTrue(games.executeOne())
            return games.results(actor, gameId, listOf(command.requestId)).statuses.single().result!!
        }
    }

    private class Fixture(val runtime: ServiceRuntime, val db: OwnedPostgres) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "synthetic password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
        }
        val a = account("VisibleA"); val b = account("VisibleB"); val c = account("VisibleC")
        val games = GameService(runtime); val lobby = LobbyService(runtime); val decks = DeckService(runtime)
        val settings = AccountSettingsService(runtime)
        fun deck(actor: AccountService.Principal, name: String): SavedDeck {
            val definition = CardDefinition("Card of $name", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card of $name")))
            val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
            assertEquals(200, decks.submit(actor, SubmitDefinitions(UUID.randomUUID().toString(), listOf(submitted))).status)
            return GameWire.json.decodeFromString(decks.create(actor, CreateDeck(UUID.randomUUID().toString(), name,
                listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 5)))).body)
        }
        fun game(inviter: AccountService.Principal, recipient: AccountService.Principal): Pair<Stream, Stream> {
            lobby.add(inviter, AddContact(UUID.randomUUID().toString(), recipient.account.accountId))
            val game = GameWire.json.decodeFromString<GameSummary>(lobby.create(inviter,
                CreateInvitation(UUID.randomUUID().toString(), recipient.account.accountId, "Visibility")).body)
            return Stream(games, inviter, game.id) to Stream(games, recipient, game.id)
        }
        fun select(stream: Stream, deck: SavedDeck) =
            assertEquals("accepted", stream.execute(GameAction.SelectDeck(deck.deck.id, current(stream.actor, deck).deck.revision)).outcome)
        fun current(owner: AccountService.Principal, deck: SavedDeck): SavedDeck =
            GameWire.json.decodeFromString(decks.read(owner, deck.deck.id).body)
        fun shown(stream: Stream, of: AccountService.Principal = stream.actor) =
            games.head(stream.actor, stream.gameId).preparation!!.players.single { it.accountId == of.account.accountId }.deckShown
        /** Both ready, start, and end the play with an accepted result. */
        fun finish(owner: Stream, other: Stream) {
            owner.execute(GameAction.Ready); other.execute(GameAction.Ready)
            assertEquals(GameLifecycle.ACTIVE, games.head(owner.actor, owner.gameId).summary.lifecycle)
            owner.execute(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.WIN)))
            val suggestion = games.head(owner.actor, owner.gameId).play!!.board.resultSuggestion!!
            assertEquals("accepted", other.execute(GameAction.AcceptResult(suggestion.id)).outcome)
        }
    }

    private fun fixture(test: (Fixture) -> Unit) = OwnedPostgres().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(runtime, db))
        }
    }

    @Test fun `show after first game reveals a deck only once it finished a game against the same opponent`() = fixture { f ->
        assertEquals(DeckVisibility.AFTER_FIRST_GAME, f.settings.read(f.a.account.accountId).deckVisibility)
        val x = f.deck(f.a, "Deck X"); val y = f.deck(f.a, "Deck Y"); val bDeck = f.deck(f.b, "Deck B")
        val (a1, b1) = f.game(f.a, f.b)
        f.select(a1, x); b1.execute(GameAction.AcceptInvitation); f.select(b1, bDeck)
        assertFalse(f.shown(a1)); assertFalse(f.shown(b1))
        // Other games of the pair already holding a deck before the first game finishes.
        val (waiting, _) = f.game(f.a, f.b); f.select(waiting, x)
        val (chosen, _) = f.game(f.a, f.b); f.select(chosen, x)
        assertEquals("accepted", chosen.execute(GameAction.ShowDeck(true)).outcome)
        assertEquals("accepted", chosen.execute(GameAction.ShowDeck(false)).outcome)
        val (other, _) = f.game(f.a, f.b); f.select(other, y)
        val (elsewhere, _) = f.game(f.a, f.c); f.select(elsewhere, x)
        val waitingRevision = f.games.head(f.a, waiting.gameId).summary.revision
        f.finish(a1, b1)
        // The finished game's next play shows both decks, to the opponent too.
        assertTrue(f.shown(a1)); assertTrue(f.shown(b1)); assertTrue(f.shown(b1, f.a)); assertTrue(f.shown(a1, f.b))
        // Another lobby game already holding the deck shows it too, through a revision of its own.
        assertTrue(f.shown(waiting))
        val revision = f.games.revisions(f.a, waiting.gameId, waitingRevision).revisions.single()
        assertTrue(revision.preparation!!.players.single { it.accountId == f.a.account.accountId }.deckShown)
        assertFalse(f.shown(chosen), "The player's own choice in a game stays")
        assertFalse(f.shown(other), "Another deck has not finished a game against B")
        assertFalse(f.shown(elsewhere), "The deck has not finished a game against C")
        // Hidden again by hand in the finished game: later finishes keep that choice.
        assertEquals("accepted", a1.execute(GameAction.ShowDeck(false)).outcome)
        f.finish(a1, b1)
        assertFalse(f.shown(a1)); assertTrue(f.shown(b1))

        // A later game against the same opponent: the same saved deck, even after editing it, starts shown.
        assertEquals(200, f.decks.update(f.a, x.deck.id, UpdateDeck(id(), "Deck X renamed")).status)
        val (a2, b2) = f.game(f.a, f.b)
        f.select(a2, x)
        assertTrue(f.shown(a2))
        b2.execute(GameAction.AcceptInvitation); assertTrue(f.shown(b2, f.a), "The opponent sees the flag too")
        f.select(b2, bDeck); assertTrue(f.shown(b2))
        // The per-game toggle overrides the default, and re-selecting the same deck keeps that choice.
        assertEquals("accepted", a2.execute(GameAction.ShowDeck(false)).outcome)
        f.select(a2, x); assertFalse(f.shown(a2))
        // Another deck has not finished a game against B yet; switching back applies the default again.
        f.select(a2, y); assertFalse(f.shown(a2))
        f.select(a2, x); assertTrue(f.shown(a2))

        // The same deck against a different opponent is a first game again.
        val (a3, _) = f.game(f.a, f.c)
        f.select(a3, x); assertFalse(f.shown(a3))
    }

    @Test fun `visible and hidden apply to every new deck selection and the per game toggle still overrides`() = fixture { f ->
        val x = f.deck(f.a, "Deck X"); val y = f.deck(f.a, "Deck Y"); val bDeck = f.deck(f.b, "Deck B")
        assertEquals(AccountSettings(DeckVisibility.VISIBLE), f.settings.set(f.a.account.accountId, AccountSettings(DeckVisibility.VISIBLE)))
        val (a1, b1) = f.game(f.a, f.b)
        f.select(a1, x); assertTrue(f.shown(a1))
        b1.execute(GameAction.AcceptInvitation); f.select(b1, bDeck)
        assertFalse(f.shown(b1), "Each player's own setting decides only their own deck")
        assertEquals("accepted", a1.execute(GameAction.ShowDeck(false)).outcome); assertFalse(f.shown(a1))
        f.finish(a1, b1)

        f.settings.set(f.a.account.accountId, AccountSettings(DeckVisibility.HIDDEN))
        val (a2, _) = f.game(f.a, f.b)
        f.select(a2, x); assertFalse(f.shown(a2), "Hidden even after a finished game")
        assertEquals("accepted", a2.execute(GameAction.ShowDeck(true)).outcome); assertTrue(f.shown(a2))
        f.select(a2, y); assertFalse(f.shown(a2))
        // A changed setting affects later selections only.
        f.settings.set(f.a.account.accountId, AccountSettings(DeckVisibility.VISIBLE))
        assertFalse(f.shown(a2))
        f.select(a2, x); assertTrue(f.shown(a2))
    }

    @Test fun `the migration shows lobby decks that already finished a game against the same opponent`() = fixture { f ->
        val x = f.deck(f.a, "Deck X"); val y = f.deck(f.a, "Deck Y"); val bDeck = f.deck(f.b, "Deck B")
        val (a1, b1) = f.game(f.a, f.b)
        f.select(a1, x); b1.execute(GameAction.AcceptInvitation); f.select(b1, bDeck)
        val (other, _) = f.game(f.a, f.b); f.select(other, y)
        f.finish(a1, b1)
        // The state the earlier server left: the finished game back in its lobby with both decks still hidden.
        fun sql(statement: String) = f.db.connection().use { it.createStatement().execute(statement) }
        sql("UPDATE player_preparation SET deck_shown = false")
        f.settings.set(f.b.account.accountId, AccountSettings(DeckVisibility.HIDDEN))
        sql("ALTER TABLE player_preparation DROP COLUMN deck_shown_chosen; DELETE FROM flyway_schema_history WHERE version = '46'")
        assertEquals(1, FoundationMigrations.migrate(f.db.settings))
        assertTrue(f.shown(a1)); assertTrue(f.shown(b1, f.a))
        assertFalse(f.shown(b1), "An account that hides its decks stays hidden")
        assertFalse(f.shown(other), "Another deck has not finished a game against B")
    }
}
