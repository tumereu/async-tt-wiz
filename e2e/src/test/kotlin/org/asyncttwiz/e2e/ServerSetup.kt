package org.asyncttwiz.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.android.AccountApi
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.android.LobbyApi
import org.asyncttwiz.protocol.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** An account the setup made: its sign-in token and identity. */
class TestAccount(val name: String, val email: String, val token: String, val identity: AccountIdentity) {
    val id: String get() = identity.accountId
    override fun toString() = name
}

/**
 * The deterministic setup of an e2e game, done over the server's own API as the app's clients do (the apps' lobby
 * screens aren't what these tests look at): accounts, contacts, decks of [TestCards], the invitation, its acceptance,
 * the deck choices, the starting player and both Ready. The deal (opening hands, library orders) comes from the decks'
 * order and the game's random secret, which the server reads from [E2eStack.secretFile]: the setup works out deals
 * here with the same code the server starts games with ([Deal]) and gives the server the secret of one the test wants.
 */
object ServerSetup {
    private val origin get() = E2eStack.origin
    private const val PASSWORD = "e2e password 1"

    private var pair: Pair<TestAccount, TestAccount>? = null

    /**
     * Alice's and Bob's accounts, made once per test run (the server allows few sign-ups and sign-ins per address an
     * hour, and every e2e request comes from this machine); each test plays its own new game with them. Alice's account
     * id sorts first (the server seats players by account id, so the deal depends on it).
     */
    @Synchronized fun accounts(): Pair<TestAccount, TestAccount> = pair ?: run {
        val tag = UUID.randomUUID().toString().take(8)
        val (x, y) = account("e2e${tag}x") to account("e2e${tag}y")
        val (a, b) = if (x.identity.accountId < y.identity.accountId) x to y else y to x
        (rename(a, "Alice", "Alice$tag") to rename(b, "Bob", "Bob$tag")).also { pair = it }
    }

    private fun account(username: String): TestAccount = AccountApi(origin).use { api -> runBlocking {
        val email = "$username@e2e.invalid"
        try { api.register(username, email, PASSWORD) }
        catch (failure: org.asyncttwiz.android.AccountFailure) { error("register $username: ${failure.code} ${failure.field}") }
        val session = api.login(email, PASSWORD)
        TestAccount(username, email, session.token, session.account)
    } }

    private fun rename(account: TestAccount, name: String, username: String): TestAccount = AccountApi(origin).use { api ->
        runBlocking { TestAccount(name, account.email, account.token, api.changeUsername(account.token, username)) }
    }

    /** [account]'s play defaults for new games (LOB-113): how their device responds and stops. */
    fun playDefaults(account: TestAccount, settings: org.asyncttwiz.protocol.game.PlaySettings) = AccountApi(origin).use { api ->
        runBlocking { api.setAccountSettings(account.token, api.accountSettings(account.token).copy(playDefaults = settings)) }
    }

    fun deck(account: TestAccount, deck: TestDeck): SavedDeck = DeckApi(origin).use { api ->
        runBlocking {
            api.submit(account.token, SubmitDefinitions(UUID.randomUUID().toString(), deck.entries.map { it.first }.distinctBy { it.digest }))
            api.create(account.token, CreateDeck(UUID.randomUUID().toString(), deck.name,
                deck.entries.map { (card, n) -> SavedDeckEntry(SavedDeckSection.MAINBOARD, card.digest, n) }))
        }
    }

    /** A game set up up to both players' Ready ([ready] starts it): invited, accepted, decks chosen, starter set. */
    class Prepared(val a: TestAccount, val b: TestAccount, val invitation: GameSummary, private val decks: Map<TestAccount, TestDeck>,
        private val starter: TestAccount, private val wanted: (Deal) -> Boolean) {
        /** The deal the game will start with (worked out once [ready] chose its secret). */
        lateinit var deal: Deal
            private set

        /** Both players Ready: the server starts the game, dealt as [wanted]. Returns the started game. */
        fun ready(): GameSummary {
            val secret = generateSequence(0) { it + 1 }.take(2_000).map { n ->
                java.security.MessageDigest.getInstance("SHA-256").digest("e2e deal $n".encodeToByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.firstOrNull { secret -> Deal.of(listOf(a, b).map { it to decks.getValue(it) }, starter, secret).also { deal = it }.let(wanted) }
                ?: error("no deal of these decks satisfies the test's wish")
            E2eStack.secretFile.writeText(secret)
            Socket(a).use { owner -> Socket(b).use { recipient ->
                owner.execute(invitation, owner.open(invitation), GameAction.Ready)
                recipient.execute(invitation, recipient.open(invitation), GameAction.Ready)
            } }
            val started = LobbyApi(origin).use { api -> runBlocking { api.head(a.token, invitation.id) } }
            check(started.lifecycle == GameLifecycle.ACTIVE) { "the game didn't start: $started" }
            return started
        }
    }

    /**
     * A game between [a] (the inviter) and [b] with these decks, [starter] going first, set up to both players' Ready:
     * [Prepared.ready] starts it with a deal [deal] accepts (the opening hands and libraries the test needs).
     */
    fun prepare(a: TestAccount, b: TestAccount, aDeck: TestDeck, bDeck: TestDeck, starter: TestAccount = a,
        name: String = "E2E game", deal: (Deal) -> Boolean = { true }): Prepared {
        val invitation = LobbyApi(origin).use { api ->
            runBlocking {
                api.add(a.token, AddContact(UUID.randomUUID().toString(), b.id))
                api.create(a.token, CreateInvitation(UUID.randomUUID().toString(), b.id, name))
            }
        }
        val saved = mapOf(a to deck(a, aDeck), b to deck(b, bDeck))
        Socket(a).use { owner -> Socket(b).use { recipient ->
            val ownerStream = owner.open(invitation)
            val recipientStream = recipient.open(invitation)
            recipient.execute(invitation, recipientStream, GameAction.AcceptInvitation)
            for ((socket, stream, account) in listOf(Triple(owner, ownerStream, a), Triple(recipient, recipientStream, b))) {
                val deck = saved.getValue(account).deck
                socket.execute(invitation, stream, GameAction.SelectDeck(deck.id, deck.revision))
            }
            owner.execute(invitation, ownerStream, GameAction.SetStarter(StarterChoice(starter.id)))
        } }
        return Prepared(a, b, invitation, mapOf(a to aDeck, b to bDeck), starter, deal)
    }

    /** The game as the server holds it now, as [account] reads it (its engine snapshot: the main line's head, each
     * player's local head). */
    fun head(account: TestAccount, gameId: String): GameHead = Socket(account).use { it.head(gameId) }

    /** A bare game socket for the setup's commands. */
    private class Socket(account: TestAccount) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>()
        private val text = StringBuilder()
        private val socket: WebSocket = HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization", "Bearer ${account.token}")
            .connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI(origin.replace("http://", "ws://") + "/v1/socket"), this).get(20, TimeUnit.SECONDS)

        init {
            send(GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            next { it is GameWelcome }
        }

        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) { queue.add(GameWire.json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
            webSocket.request(1); return null
        }

        fun send(message: GameClientMessage) { socket.sendText(GameWire.json.encodeToString<GameClientMessage>(message), true).get(10, TimeUnit.SECONDS) }

        fun next(predicate: (GameServerMessage) -> Boolean): GameServerMessage {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < end) {
                val message = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (message is GameAdmissionError) error("setup refused: $message")
                if (predicate(message)) return message
            }
            error("no answer from the server")
        }

        fun head(gameId: String): GameHead {
            send(SubscribeGame(gameId))
            return next { it is GameHead && it.gameId == gameId } as GameHead
        }

        fun open(game: GameSummary): GameStreamReady {
            val id = UUID.randomUUID().toString()
            send(OpenGameStream(game.id, id))
            return next { it is GameStreamReady && it.streamId == id } as GameStreamReady
        }

        fun execute(game: GameSummary, stream: GameStreamReady, action: GameAction) {
            val scope = GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            send(command)
            val result = next { it is GameResult && it.requestId == command.requestId } as GameResult
            check(result.outcome == "accepted") { "setup ${action::class.simpleName}: ${result.outcome} ${result.reason}" }
        }

        override fun close() { socket.abort() }
    }
}
