package org.asyncttwiz.server.decks

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** LOB-244..LOB-246 (owner 2026-11-19): an account's deck categories, a tree, and the category of each saved deck. */
class DeckCategoryTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private class Fixture(runtime: ServiceRuntime) {
        private val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "synthetic password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
        }
        val a = account("CategoryA"); val b = account("CategoryB")
        val decks = DeckService(runtime); val categories = DeckCategoryService(runtime)
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

    private fun Fixture.list(result: DeckService.Result): List<DeckCategory> {
        assertEquals(200, result.status, result.body)
        return json.decodeFromString<DeckCategoryList>(result.body).categories
    }
    private fun Fixture.create(actor: AccountService.Principal, name: String, parent: String? = null): DeckCategory =
        list(categories.create(actor, CreateDeckCategory(id(), name, parent))).single { it.name == name && it.parent == parent }
    private fun Fixture.deck(actor: AccountService.Principal, name: String, category: String? = null): SavedDeck =
        json.decodeFromString(decks.create(actor, CreateDeck(id(), name, listOf(TestPrintings.entry("Forest", 5)), category = category)).body)
    private fun Fixture.read(actor: AccountService.Principal, deck: SavedDeck): DeckSummary =
        json.decodeFromString<SavedDeck>(decks.read(actor, deck.deck.id).body).deck
    private fun code(result: DeckService.Result) = json.decodeFromString<DeckError>(result.body).code

    // LOB-244, LOB-258, LOB-259: categories nest; a deck is in at most one, set as it is saved or later from its editor; the decks' list
    // carries the account's categories
    @Test fun `categories nest and a deck is in at most one`() = fixture { f ->
        val box = f.create(f.a, "Battle box")
        val red = f.create(f.a, "Red", box.id)
        assertEquals(listOf(box.id), DeckCategories.path(f.list(f.categories.read(f.a)), red.id).dropLast(1).map { it.id })
        val burn = f.deck(f.a, "Burn", red.id)
        assertEquals(red.id, f.read(f.a, burn).category)
        val loose = f.deck(f.a, "Loose")
        assertNull(f.read(f.a, loose).category)
        // Set from the editor, without changing what the deck holds (its revision stays)
        val revision = f.read(f.a, loose).revision
        assertEquals(200, f.decks.update(f.a, loose.deck.id, UpdateDeck(id(), category = CategoryChoice(box.id))).status)
        assertEquals(box.id, f.read(f.a, loose).category)
        assertEquals(revision, f.read(f.a, loose).revision)
        assertEquals(200, f.decks.update(f.a, loose.deck.id, UpdateDeck(id(), category = CategoryChoice(null))).status)
        assertNull(f.read(f.a, loose).category)
        val page = json.decodeFromString<DeckList>(f.decks.list(f.a, null).body)
        assertEquals(setOf(box.id, red.id), page.categories.map { it.id }.toSet())
        assertEquals(red.id, page.decks.single { it.id == burn.deck.id }.category)
        // Selecting a category always includes its subcategories
        assertEquals(setOf(box.id, red.id), DeckCategories.within(page.categories, listOf(box.id)))
    }

    // LOB-245: categories are renamed and moved; never into themselves or below themselves
    @Test fun `categories are renamed and moved but never into their own subtree`() = fixture { f ->
        val box = f.create(f.a, "Battle box")
        val red = f.create(f.a, "Red", box.id)
        val deep = f.create(f.a, "Mono", red.id)
        val renamed = f.list(f.categories.update(f.a, red.id, UpdateDeckCategory(id(), name = "Red decks")))
        assertEquals("Red decks", renamed.single { it.id == red.id }.name)
        assertEquals("invalid_move", code(f.categories.update(f.a, box.id, UpdateDeckCategory(id(), parent = CategoryChoice(deep.id)))))
        assertEquals("invalid_move", code(f.categories.update(f.a, box.id, UpdateDeckCategory(id(), parent = CategoryChoice(box.id)))))
        val moved = f.list(f.categories.update(f.a, deep.id, UpdateDeckCategory(id(), parent = CategoryChoice(null))))
        assertNull(moved.single { it.id == deep.id }.parent)
        assertEquals(400, f.categories.create(f.a, CreateDeckCategory(id(), "  ")).status)
    }

    // LOB-246: deleting a category moves its decks and subcategories up one level, into its parent
    @Test fun `deleting a category moves its decks and subcategories up one level`() = fixture { f ->
        val box = f.create(f.a, "Battle box")
        val red = f.create(f.a, "Red", box.id)
        val mono = f.create(f.a, "Mono", red.id)
        val burn = f.deck(f.a, "Burn", red.id)
        val after = f.list(f.categories.delete(f.a, red.id, DeleteDeckCategory(id())))
        assertEquals(setOf(box.id, mono.id), after.map { it.id }.toSet())
        assertEquals(box.id, after.single { it.id == mono.id }.parent)
        assertEquals(box.id, f.read(f.a, burn).category)
        // A top-level category's decks become uncategorised
        f.list(f.categories.delete(f.a, box.id, DeleteDeckCategory(id())))
        assertNull(f.read(f.a, burn).category)
        assertNull(f.list(f.categories.read(f.a)).single().parent)
    }

    // LOB-244: an account's categories are its own: nobody else lists, uses, changes or deletes them
    @Test fun `another account's categories are out of reach`() = fixture { f ->
        val box = f.create(f.a, "Battle box")
        assertTrue(f.list(f.categories.read(f.b)).isEmpty())
        assertEquals("invalid_category", code(f.decks.create(f.b, CreateDeck(id(), "Theirs", listOf(TestPrintings.entry("Forest", 5)), category = box.id))))
        val own = f.deck(f.b, "Own")
        assertEquals("invalid_category", code(f.decks.update(f.b, own.deck.id, UpdateDeck(id(), category = CategoryChoice(box.id)))))
        assertEquals("invalid_category", code(f.categories.create(f.b, CreateDeckCategory(id(), "Inside", box.id))))
        assertEquals(404, f.categories.update(f.b, box.id, UpdateDeckCategory(id(), name = "Mine")).status)
        assertEquals(404, f.categories.delete(f.b, box.id, DeleteDeckCategory(id())).status)
        assertEquals(listOf(box), f.list(f.categories.read(f.a)))
    }
}
