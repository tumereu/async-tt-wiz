package org.asyncttwiz.server.decks

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * LOB-247, LOB-248 (owner 2026-11-19): the decks a game's creator lends the other player. Lent decks are the creator's
 * ordinary saved decks (never draft decks): all of them, or those in the chosen categories and their subcategories. The
 * game's players read them (GET /v1/games/{id}/lent-decks, and one by one); nobody reads a deck that isn't lent.
 */
class Lending(private val runtime: ServiceRuntime) {
    private val json = DeckJson.format
    data class Result(val status: Int, val body: String)
    private fun error(status: Int, code: String) = Result(status, json.encodeToString(DeckError(code)))

    /** The decks game [gameId]'s creator lends, for one of its players. */
    fun list(principal: AccountService.Principal, gameId: String): Result = read(principal, gameId) { sql, game ->
        val settings = settings(game)
        val lender = game.inviterId!!
        val categories = DeckCategoryService.all(sql, lender)
        val ids = lentIds(sql, lender, settings, categories)
        val rows = if (ids.isEmpty()) emptyList() else sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ID.`in`(ids))
            .orderBy(SAVED_DECK.ID).forShare().fetch()
        val lent = when (val lend = settings.lend) {
            DeckLending.Off -> emptySet()
            DeckLending.All -> categories.map { it.id }.toSet()
            is DeckLending.Categories -> DeckCategories.within(categories, lend.ids)
        }
        // A lent category whose parent isn't lent lies at the top level here: nothing of the lender's that isn't lent shows
        val shown = categories.filter { it.id in lent }.map { if (it.parent in lent) it else it.copy(parent = null) }
        val identity = sql.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(lender)).fetchSingle().let(AccountService::identity)
        Result(200, json.encodeToString(LentDecks(identity, DeckService(runtime).summaries(sql, rows), shown)))
    }

    /** Lent deck [deckId] of game [gameId]'s creator, whole, for one of its players; "not_found" when it isn't lent. */
    fun deck(principal: AccountService.Principal, gameId: String, deckId: String): Result = read(principal, gameId) { sql, game ->
        if (!DeckValidation.uuid(deckId) || !isLent(sql, game, UUID.fromString(deckId))) return@read error(404, "not_found")
        val deck = DeckService(runtime).lentSource(sql, game.inviterId!!, deckId) ?: return@read error(404, "not_found")
        Result(200, json.encodeToString(deck))
    }

    private fun read(principal: AccountService.Principal, gameId: String, work: (DSLContext, GameRecord) -> Result): Result {
        if (!DeckValidation.uuid(gameId)) return error(404, "not_found")
        return runtime.transaction { connection ->
            val sql = DSL.using(connection, SQLDialect.POSTGRES)
            val actor = UUID.fromString(principal.account.accountId)
            // Unknown games and other players' games have the same answer
            val game = sql.selectFrom(GAME).where(GAME.ID.eq(UUID.fromString(gameId))).and(GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE"))
                .and(GAME.INVITER_ID.eq(actor).or(GAME.RECIPIENT_ID.eq(actor))).forShare().fetchOne()
                ?: return@transaction error(404, "not_found")
            work(sql, game)
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(Lending::class.java)

        /** [game]'s deck settings, as its creator last set them; a stored value the code can't read counts as the defaults
         * (SYS-052: logged). */
        fun settings(game: GameRecord): DeckSettings = decode(game.deckSettings, game.id)

        /** Game [id]'s [stored] deck settings ([settings]). */
        fun decode(stored: String?, id: UUID?): DeckSettings = stored?.let {
            StoredData.orSkip("deck settings", id) { GameWire.json.decodeFromString<DeckSettings>(it) } } ?: DeckSettings()

        fun encode(settings: DeckSettings): String? = if (settings == DeckSettings()) null else GameWire.json.encodeToString(settings)

        /** The ids of [lender]'s decks lent under [settings] ([categories]: all of the lender's). */
        fun lentIds(sql: DSLContext, lender: UUID, settings: DeckSettings,
            categories: List<DeckCategory> = DeckCategoryService.all(sql, lender)): Set<UUID> {
            val ordinary = SAVED_DECK.ACCOUNT_ID.eq(lender).and(SAVED_DECK.DRAFT_POD_ID.isNull)
            val condition = when (val lend = settings.lend) {
                DeckLending.Off -> return emptySet()
                DeckLending.All -> ordinary
                is DeckLending.Categories -> DeckCategories.within(categories, lend.ids).takeIf { it.isNotEmpty() }
                    ?.let { ordinary.and(SAVED_DECK.CATEGORY_ID.`in`(it.map(UUID::fromString))) } ?: return emptySet()
            }
            return sql.select(SAVED_DECK.ID).from(SAVED_DECK).where(condition).fetchSet(SAVED_DECK.ID).filterNotNull().toSet()
        }

        /** Whether [game]'s creator lends their deck [deck] now. */
        fun isLent(sql: DSLContext, game: GameRecord, deck: UUID): Boolean = deck in lentIds(sql, game.inviterId!!, settings(game))

        /**
         * Whether [player]'s chosen [deck] is allowed in [game] under [settings] (LOB-256, LOB-249, LOB-257): a borrowed deck only while
         * it is lent; with only lent decks, nothing else (the creator's other decks included).
         */
        fun allowed(sql: DSLContext, game: GameRecord, settings: DeckSettings, player: UUID, deck: PreparedDeck,
            lent: Set<UUID> = lentIds(sql, game.inviterId!!, settings)): Boolean {
            val source = runCatching { UUID.fromString(deck.sourceId) }.getOrNull()
            val isLent = source != null && source in lent && (deck.lender == game.inviterId.toString() ||
                (deck.lender == null && player == game.inviterId))
            return if (deck.lender != null || settings.onlyLentDecks) isLent else true
        }

        /** LOB-250: a random deal's log line. */
        fun logDeal(game: UUID, dealt: Map<String, String>, from: Int) =
            log.info("Decks dealt game={} from {} playable lent decks: {}", game, from, dealt)
    }
}
