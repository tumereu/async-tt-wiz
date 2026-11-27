package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckCategoryService
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.decks.Lending
import org.asyncttwiz.server.history.MatchRecords
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** LOB-247..LOB-260 (owner 2026-11-19): a game's deck settings, lent decks, battle boxes and random deals. */
class DeckLendingTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()

    private class Stream(val games: GameService, val actor: AccountService.Principal, val gameId: String) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, gameId, UUID.randomUUID().toString())
        fun execute(action: GameAction): GameResult {
            val summary = games.head(actor, gameId).summary
            val scope = if (action is GameAction.Submit) GameScope(gameId, playId = summary.playId)
                else if (action == GameAction.AcknowledgeResult || action is GameAction.SetAssumeReveals) GameScope(gameId)
                else GameScope(gameId, summary.preparationRound, if (action == GameAction.AcceptInvitation) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(actor, hello, connection, command) is GameReceipt); assertTrue(games.executeOne())
            return games.results(actor, gameId, listOf(command.requestId)).statuses.single().result!!
        }
        fun accepted(action: GameAction) = assertEquals("accepted", execute(action).let { it.reason ?: it.outcome })
        fun refused(action: GameAction) = execute(action).also { assertEquals("rejected", it.outcome) }.reason
    }

    private class Fixture(val runtime: ServiceRuntime) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "synthetic password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
        }
        val a = account("Lender"); val b = account("Borrower"); val c = account("Stranger")
        val games = GameService(runtime); val lobby = LobbyService(runtime); val decks = DeckService(runtime)
        val categories = DeckCategoryService(runtime); val lending = Lending(runtime)
        fun category(name: String, parent: String? = null): DeckCategory =
            GameWire.json.decodeFromString<DeckCategoryList>(categories.create(a, CreateDeckCategory(UUID.randomUUID().toString(), name, parent)).body)
                .categories.single { it.name == name && it.parent == parent }
        fun deck(owner: AccountService.Principal, name: String, category: String? = null, card: String = "Forest"): SavedDeck =
            GameWire.json.decodeFromString(decks.create(owner, CreateDeck(UUID.randomUUID().toString(), name,
                listOf(TestPrintings.entry(card, 5)), category = category)).body)
        fun game(): Pair<Stream, Stream> {
            lobby.add(a, AddContact(UUID.randomUUID().toString(), b.account.accountId))
            val game = GameWire.json.decodeFromString<GameSummary>(lobby.create(a,
                CreateInvitation(UUID.randomUUID().toString(), b.account.accountId, "Lending")).body)
            return Stream(games, a, game.id) to Stream(games, b, game.id)
        }
        fun select(stream: Stream, deck: SavedDeck) = stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
        fun summary(stream: Stream) = games.head(stream.actor, stream.gameId).summary
        fun player(stream: Stream, of: AccountService.Principal = stream.actor) =
            games.head(stream.actor, stream.gameId).preparation!!.players.single { it.accountId == of.account.accountId }
        fun lent(stream: Stream) = lending.list(stream.actor, stream.gameId).let {
            assertEquals(200, it.status, it.body); GameWire.json.decodeFromString<LentDecks>(it.body) }
        fun finish(owner: Stream, other: Stream) {
            assertEquals(GameLifecycle.ACTIVE, summary(owner).lifecycle)
            assertEquals("accepted", other.execute(games.concession(other.actor, other.gameId)).outcome)
            assertEquals("accepted", owner.execute(GameAction.AcknowledgeResult).outcome)
        }
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(Fixture(runtime))
        }
    }

    // LOB-247, LOB-154: only the game's creator sets its deck settings, in the lobby; both players see them; a change clears
    // both players' Ready
    @Test fun `only the creator sets the deck settings and a change clears both Readys`() = fixture { f ->
        val (a, b) = f.game()
        val own = f.deck(f.a, "Own"); val theirs = f.deck(f.b, "Theirs")
        b.accepted(GameAction.AcceptInvitation)
        f.select(a, own); f.select(b, theirs)
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready).also { assertEquals(GameLifecycle.ACTIVE, f.summary(a).lifecycle) }
        f.finish(a, b)
        a.accepted(GameAction.Ready)
        val settings = DeckSettings(DeckLending.All, sameDeck = false)
        assertEquals("notHost", b.refused(GameAction.SetDeckSettings(settings)))
        assertEquals(DeckSettings(), f.summary(b).deckSettings)
        a.accepted(GameAction.SetDeckSettings(settings))
        assertEquals(settings, f.summary(b).deckSettings)
        assertEquals(settings, f.summary(a).deckSettings)
        assertFalse(f.player(a).ready, "the creator's Ready cleared")
        // Own decks are still allowed: lending alone puts nothing back
        assertEquals(own.deck.id, f.player(a).deck?.sourceId); assertEquals(theirs.deck.id, f.player(b).deck?.sourceId)
        // A category of someone else's can't be lent
        val foreign = GameWire.json.decodeFromString<DeckCategoryList>(f.categories.create(f.c, CreateDeckCategory(id(), "Not hers")).body).categories.single()
        assertEquals("inapplicable", a.refused(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(foreign.id))))))
    }

    // LOB-282, LOB-247 (owner 2026-11-27): the creator changing "Reshuffle libraries on undo" or "on branch" in the lobby clears
    // both players' Ready, as the Decks settings do; the other game settings leave Ready as it is
    @Test fun `a reshuffle switch changed in the lobby clears both Readys`() = fixture { f ->
        val (a, b) = f.game()
        b.accepted(GameAction.AcceptInvitation)
        f.select(a, f.deck(f.a, "Own")); f.select(b, f.deck(f.b, "Theirs"))
        a.accepted(GameAction.Ready)
        a.accepted(GameAction.SetAssumeReveals(AssumeReveals(hands = true)))
        assertTrue(f.player(a).ready, "another game setting leaves her Ready")
        assertEquals("notHost", b.refused(GameAction.SetAssumeReveals(AssumeReveals(hands = true, reshuffleOnBranch = false))))
        assertTrue(f.player(a).ready)
        a.accepted(GameAction.SetAssumeReveals(AssumeReveals(hands = true, reshuffleOnBranch = false)))
        assertFalse(f.player(a).ready, "the creator's Ready cleared")
        assertFalse(f.player(b, f.a).ready, "as the other player sees it")
        assertEquals(AssumeReveals(hands = true, reshuffleOnBranch = false), f.summary(b).assumeReveals)
        b.accepted(GameAction.Ready)
        a.accepted(GameAction.SetAssumeReveals(AssumeReveals(hands = true, reshuffleOnUndo = false, reshuffleOnBranch = false)))
        assertFalse(f.player(b).ready, "the other player's Ready cleared")
        assertEquals(GameLifecycle.LOBBY, f.summary(a).lifecycle)
    }

    // LOB-248, LOB-259, LOB-255: the other player reads only the lent decks (what Choose deck lists first) (all of them, or the chosen categories with their subcategories,
    // never draft decks); nobody else reads them at all
    @Test fun `a borrower reads only the decks lent`() = fixture { f ->
        val (a, b) = f.game()
        val box = f.category("Battle box"); val red = f.category("Red", box.id); val other = f.category("Other")
        val burn = f.deck(f.a, "Burn", red.id); val elves = f.deck(f.a, "Elves", box.id); val secret = f.deck(f.a, "Secret", other.id)
        val loose = f.deck(f.a, "Loose")
        assertTrue(f.lent(b).decks.isEmpty(), "nothing is lent by default")
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)))))
        val lent = f.lent(b)
        assertEquals(f.a.account.accountId, lent.lender.accountId)
        assertEquals(setOf(burn.deck.id, elves.deck.id), lent.decks.map { it.id }.toSet())
        assertEquals(setOf(box.id, red.id), lent.categories.map { it.id }.toSet())
        assertEquals(200, f.lending.deck(f.b, b.gameId, burn.deck.id).status)
        assertEquals(404, f.lending.deck(f.b, b.gameId, secret.deck.id).status)
        assertEquals(404, f.lending.deck(f.b, b.gameId, loose.deck.id).status)
        // Lent categories whose parent isn't lent lie at the top level
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(red.id)))))
        assertEquals(listOf(DeckCategory(red.id, "Red")), f.lent(b).categories)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All)))
        assertEquals(setOf(burn.deck.id, elves.deck.id, secret.deck.id, loose.deck.id), f.lent(b).decks.map { it.id }.toSet())
        // Someone not in the game reads nothing
        assertEquals(404, f.lending.list(f.c, b.gameId).status)
        assertEquals(404, f.lending.deck(f.c, b.gameId, burn.deck.id).status)
    }

    // LOB-256, LOB-257, LOB-053: choosing a lent deck makes the lobby's own copy: it always shows to both players, the lender's saved deck
    // never changes and nothing is saved to the borrower's account; a deck not lent can't be chosen
    @Test fun `a borrowed deck is the lobby's copy and always shows`() = fixture { f ->
        val (a, b) = f.game()
        val box = f.category("Battle box")
        val burn = f.deck(f.a, "Burn", box.id); val secret = f.deck(f.a, "Secret")
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)))))
        b.accepted(GameAction.AcceptInvitation)
        assertEquals("sourceUnavailable", f.select(b, secret).reason)
        b.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        val chosen = f.player(b)
        assertEquals(f.a.account.accountId, chosen.deck?.lender)
        assertEquals("Burn", chosen.deck?.name)
        assertTrue(chosen.deckShown, "a borrowed deck shows whatever the borrower's deck visibility says")
        assertTrue(f.player(a, f.b).deckShown, "the lender sees it too")
        assertEquals("borrowedDeckShown", b.refused(GameAction.ShowDeck(false)))
        // Prepare deck moves the lobby's copy only
        val entry = chosen.deck!!.entries.first()
        b.accepted(GameAction.TransferPreparedCard(chosen.deck!!.id, chosen.deck!!.card(entry, 0).id, entry.firstIndex, SavedDeckSection.MAINBOARD))
        assertEquals(1L, f.player(b).deck!!.count(SavedDeckSection.SIDEBOARD))
        val saved = GameWire.json.decodeFromString<SavedDeck>(f.decks.read(f.a, burn.deck.id).body)
        assertEquals(burn, saved)
        assertTrue(GameWire.json.decodeFromString<DeckList>(f.decks.list(f.b, null).body).decks.isEmpty(), "nothing saved for the borrower")
        // The lender's own lent deck is their own: no lender, their visibility
        a.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        assertNull(f.player(a).deck?.lender)
        // Lending turned off puts the borrowed deck back
        a.accepted(GameAction.SetDeckSettings(DeckSettings()))
        assertNull(f.player(b).deck)
        assertEquals(burn.deck.id, f.player(a).deck?.sourceId)
    }

    // LOB-249, LOB-257: with Only lent decks every player plays a lent deck: the borrower's own and the creator's other decks are
    // refused, and turning it on puts such a chosen deck back
    @Test fun `only lent decks makes a battle box`() = fixture { f ->
        val (a, b) = f.game()
        val box = f.category("Battle box")
        val burn = f.deck(f.a, "Burn", box.id); val elves = f.deck(f.a, "Elves", box.id); val secret = f.deck(f.a, "Secret")
        val theirs = f.deck(f.b, "Theirs")
        b.accepted(GameAction.AcceptInvitation)
        f.select(a, secret); f.select(b, theirs)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true)))
        assertNull(f.player(a).deck, "the creator's deck that isn't lent is put back")
        assertNull(f.player(b).deck, "the borrower's own deck is put back")
        assertEquals("notLent", f.select(b, theirs).reason)
        assertEquals("notLent", f.select(a, secret).reason)
        b.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        a.accepted(GameAction.SelectDeck(elves.deck.id, elves.deck.revision))
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        assertEquals(GameLifecycle.ACTIVE, f.summary(a).lifecycle)
    }

    // LOB-254, LOB-257: without Same deck allowed both players never use the same deck; with it they may
    @Test fun `same deck allowed decides whether both may pick one deck`() = fixture { f ->
        val (a, b) = f.game()
        val burn = f.deck(f.a, "Burn")
        b.accepted(GameAction.AcceptInvitation)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All, sameDeck = false)))
        a.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        assertEquals("sameDeckTaken", f.select(b, burn).reason)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All, sameDeck = true)))
        b.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        // Turned off again: the borrower's copy of the same deck is put back
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All, sameDeck = false)))
        assertEquals(burn.deck.id, f.player(a).deck?.sourceId)
        assertNull(f.player(b).deck)
    }

    // LOB-250, LOB-260, LOB-115: with Random nobody chooses; both press Ready and the server deals each a different lent deck Automatum
    // can play as the game starts; too few such decks refuse the Ready
    @Test fun `random deals distinct playable lent decks as the game starts`() = fixture { f ->
        val (a, b) = f.game()
        val box = f.category("Battle box")
        val burn = f.deck(f.a, "Burn", box.id)
        f.deck(f.a, "Relics", box.id, card = "Odd Relic") // Automatum can't play it: never dealt
        b.accepted(GameAction.AcceptInvitation)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)), sameDeck = false, random = true)))
        assertEquals("decksDealt", f.select(b, burn).reason)
        assertEquals("noDecksToDeal", b.refused(GameAction.Ready))
        val elves = f.deck(f.a, "Elves", box.id)
        a.accepted(GameAction.Ready)
        assertNull(f.player(a).deck, "nothing is dealt before the game starts")
        b.accepted(GameAction.Ready)
        val play = f.games.head(f.a, a.gameId)
        assertEquals(GameLifecycle.ACTIVE, play.summary.lifecycle)
        val dealt = f.runtime.transaction { PreparationStore.read(org.jooq.impl.DSL.using(it, org.jooq.SQLDialect.POSTGRES), play.summary) }.players
        assertEquals(setOf(burn.deck.id, elves.deck.id), dealt.map { it.deck!!.sourceId }.toSet(), "different playable decks")
        assertEquals(f.a.account.accountId, dealt.single { it.accountId == f.b.account.accountId }.deck!!.lender)
        assertNull(dealt.single { it.accountId == f.a.account.accountId }.deck!!.lender)
    }

    // LOB-251: with New random decks on rematch the next game's decks are dealt again; without it the dealt decks stay
    @Test fun `a rematch deals new decks only when asked`() = fixture { f ->
        val (a, b) = f.game()
        val box = f.category("Battle box")
        f.deck(f.a, "Burn", box.id); f.deck(f.a, "Elves", box.id)
        b.accepted(GameAction.AcceptInvitation)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)), random = true, redeal = false)))
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        f.finish(a, b)
        val kept = f.player(a).deck
        assertNotNull(kept, "the dealt decks stay for the rematch")
        assertNotNull(f.player(b).deck)
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        f.finish(a, b)
        assertEquals(kept?.sourceId, f.player(a).deck?.sourceId)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id)), random = true, redeal = true)))
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        assertEquals(GameLifecycle.ACTIVE, f.summary(a).lifecycle)
        f.finish(a, b)
        assertNull(f.player(a).deck, "the rematch deals again as it starts")
        assertNull(f.player(b).deck)
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        assertEquals(GameLifecycle.ACTIVE, f.summary(a).lifecycle)
    }

    // LOB-253: a borrowed deck counts for the player who played it, named with its lender
    @Test fun `a borrowed deck's match record names its lender`() = fixture { f ->
        val (a, b) = f.game()
        val burn = f.deck(f.a, "Burn"); val own = f.deck(f.a, "Own")
        b.accepted(GameAction.AcceptInvitation)
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All)))
        a.accepted(GameAction.SelectDeck(own.deck.id, own.deck.revision))
        b.accepted(GameAction.SelectDeck(burn.deck.id, burn.deck.revision))
        a.accepted(GameAction.Ready); b.accepted(GameAction.Ready)
        f.finish(a, b)
        fun history(of: AccountService.Principal) = f.runtime.transaction {
            MatchRecords.forAccount(org.jooq.impl.DSL.using(it, org.jooq.SQLDialect.POSTGRES), UUID.fromString(of.account.accountId)) }
        val record = history(f.b).single()
        assertEquals("Burn (from Lender)", record.player(f.b.account.accountId)?.deck?.name)
        assertFalse(record.player(f.b.account.accountId)!!.deck!!.openable, "the lender's deck isn't the borrower's to open")
        val lenders = history(f.a).single()
        assertEquals("Own", lenders.player(f.a.account.accountId)?.deck?.name)
        assertEquals("Burn (from Lender)", lenders.opponent(f.a.account.accountId)?.deck?.name)
    }

    // LOB-252: turning lending on while the invitation waits tells the recipient again that decks are lent
    @Test fun `the invitation says decks are lent`() = fixture { f ->
        val (a, _) = f.game()
        a.accepted(GameAction.SetDeckSettings(DeckSettings(DeckLending.All)))
        val invitations = ActivityService(f.runtime).page(f.b, null).entries.filter { it.kind == ActivityKind.INVITATION }
        assertTrue(invitations.isNotEmpty())
        assertTrue(invitations.all { it.lendsDecks }, "$invitations")
        assertEquals("Lender lends you decks", ActivityTexts.notification(invitations.last()).second)
    }
}
