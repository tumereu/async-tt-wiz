package org.asyncttwiz.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private const val ACCOUNTS = "atw.e2e.accounts"

    /**
     * Alice's and Bob's accounts, made once per test run as the base dataset ([E2eStack.keepBase]; the server allows
     * few sign-ups and sign-ins per address an hour, and every e2e request comes from this machine); each test plays its
     * own new game with them. Alice's account id sorts first (the server seats players by account id, so the deal
     * depends on it). Kept in a system property, as [E2eStack] keeps the stack, so another Robolectric sandbox finds the
     * same pair.
     */
    @Synchronized fun accounts(): Pair<TestAccount, TestAccount> = pair ?: run {
        val stored = System.getProperty(ACCOUNTS)?.lines()?.map { line ->
            val (name, email, token, identity) = line.split('\t')
            TestAccount(name, email, token, Json.decodeFromString(AccountIdentity.serializer(), identity))
        }
        stored?.let { (a, b) -> a to b } ?: made().also { (a, b) ->
            E2eStack.keepBase()
            System.setProperty(ACCOUNTS, listOf(a, b).joinToString("\n") {
                listOf(it.name, it.email, it.token, Json.encodeToString(AccountIdentity.serializer(), it.identity)).joinToString("\t")
            })
        }
    }.also { pair = it }

    /**
     * The server as at the start of the run, with [accounts] (the base dataset): whatever earlier tests made on it
     * (games, pods, invitations, contacts, decks, notifications, chat, problem reports, changed settings) is gone.
     */
    fun fresh(): Pair<TestAccount, TestAccount> = accounts().also { E2eStack.reset() }

    private fun made(): Pair<TestAccount, TestAccount> {
        val tag = UUID.randomUUID().toString().take(8)
        val (x, y) = account("e2e${tag}x") to account("e2e${tag}y")
        val (a, b) = if (x.identity.accountId < y.identity.accountId) x to y else y to x
        return rename(a, "Alice", "Alice$tag") to rename(b, "Bob", "Bob$tag")
    }

    private fun account(username: String): TestAccount = AccountApi(origin).use { api -> runBlocking {
        val email = "$username@e2e.invalid"
        try { api.register(username, email, PASSWORD) }
        catch (failure: org.asyncttwiz.android.AccountFailure) { error("register $username: ${failure.code} ${failure.field}") }
        val session = api.login(email, PASSWORD)
        TestAccount(username, email, session.token, session.account)
    } }

    private fun rename(account: TestAccount, name: String, username: String): TestAccount = accountCall("rename ${account.name}") { api ->
        TestAccount(name, account.email, account.token, api.changeUsername(account.token, username))
    }

    /** [account]'s play defaults for new games (LOB-113): how their device responds and stops. */
    fun playDefaults(account: TestAccount, settings: org.asyncttwiz.protocol.game.PlaySettings) = accountCall("play defaults of ${account.name}") { api ->
        api.setAccountSettings(account.token, api.accountSettings(account.token).copy(playDefaults = settings))
    }

    /** [account]'s latest notification of game [gameId] as the server keeps it (its activity summary, what the phone's
     * notification shows): its activity id and whether it says "your move"; null when there is none. Read from the
     * database: the activity API's rate limit is spent by the phones' own requests. */
    fun gameNotice(account: TestAccount, gameId: String): Pair<Long, Boolean>? =
        E2eStack.query("SELECT activity_id, your_move FROM activity_summary WHERE account_id = '${account.id}' " +
            "AND game_id = '$gameId' AND kind = 'GAME'").singleOrNull()?.split('|')?.let { (id, move) -> id.toLong() to (move == "t") }

    /** A lobby request of the setup: again after a connection failure, as [accountCall], at most three times; a refusal fails
     * at once, naming its code. */
    private fun <T> lobbyCall(call: suspend (LobbyApi) -> T): T {
        repeat(3) { attempt ->
            try { return LobbyApi(origin).use { api -> runBlocking { call(api) } } }
            catch (failure: org.asyncttwiz.android.LobbyFailure) {
                if (failure.code != "network" || attempt == 2) error("Lobby request of the setup: ${failure.code}")
                Thread.sleep(200)
            }
        }
        error("unreachable")
    }

    /**
     * An account request of the setup ([what]): again after a connection failure (the app's AccountApi says only
     * "network"; a test's setup was seen failing once with "Account request failed"), at most three times; a refusal
     * fails at once, naming its code.
     */
    private fun <T> accountCall(what: String, call: suspend (AccountApi) -> T): T {
        repeat(3) { attempt ->
            try { return AccountApi(origin).use { api -> runBlocking { call(api) } } }
            catch (failure: org.asyncttwiz.android.AccountFailure) {
                if (failure.code != "network" || attempt == 2) error("$what: ${failure.code} ${failure.field.orEmpty()}")
                Thread.sleep(200)
            }
        }
        error("unreachable")
    }

    fun deck(account: TestAccount, deck: TestDeck): SavedDeck = DeckApi(origin).use { api ->
        runBlocking {
            val sections = listOf(SavedDeckSection.MAINBOARD to deck.entries, SavedDeckSection.SIDEBOARD to deck.side,
                SavedDeckSection.MAYBEBOARD to deck.maybe)
            api.submit(account.token, SubmitDefinitions(UUID.randomUUID().toString(),
                sections.flatMap { (_, cards) -> cards.map { it.first.submitted() } }.distinctBy { it.digest }))
            api.create(account.token, CreateDeck(UUID.randomUUID().toString(), deck.name,
                sections.flatMap { (section, cards) -> cards.map { (card, n) -> SavedDeckEntry(section, card.digest, n) } }))
        }
    }

    /**
     * A pod [name] of [a] (its creator) and [b] drafting: a cube of [TestCards] (a booster draft of three packs of two
     * cards each, or [settings]), [b] invited and accepted, the draft started. [cards]: the cube's cards, as many in all as the draft takes, evenly.
     * Returns the pod's id.
     */
    fun draftingPod(a: TestAccount, b: TestAccount, name: String,
        cards: List<TestCard> = listOf(TestCards.mountain, TestCards.cub, TestCards.spark),
        settings: DraftSettings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 2)): String = runBlocking {
        fun id() = UUID.randomUUID().toString()
        lobbyCall { it.add(a.token, AddContact(id(), b.id)) }
        val cube = DeckApi(origin).use { api ->
            api.submit(a.token, SubmitDefinitions(id(), cards.map { it.submitted() }))
            api.create(a.token, CreateCube(id(), "E2E cube", cards.map { SavedCubeEntry(it.digest, 2 * settings.packsPerPlayer * settings.cardsPerPack / cards.size) }))
        }
        org.asyncttwiz.android.PodApi(origin).use { api ->
            val pod = api.create(a.token, CreatePod(id(), name, cube.cube.id)).id
            api.invite(a.token, pod, InviteToPod(id(), b.id))
            api.accept(b.token, pod, PodRequest(id()))
            api.update(a.token, pod, UpdatePod(id(), settings = settings))
            api.start(a.token, pod, PodRequest(id())).also { check(it.phase == PodPhase.DRAFTING) { "the draft didn't start: $it" } }.id
        }
    }

    /** [b] joins [a]'s pod [pod] (invited as a contact, accepting) and [a] starts its draft. */
    fun joinAndStart(a: TestAccount, b: TestAccount, pod: String) = runBlocking {
        fun id() = UUID.randomUUID().toString()
        lobbyCall { it.add(a.token, AddContact(id(), b.id)) }
        org.asyncttwiz.android.PodApi(origin).use { api ->
            api.invite(a.token, pod, InviteToPod(id(), b.id))
            api.accept(b.token, pod, PodRequest(id()))
            api.start(a.token, pod, PodRequest(id())).also { check(it.phase == PodPhase.DRAFTING) { "the draft didn't start: $it" } }
        }
    }

    /** A pod [name] of [a] (its creator) still in setup, with a cube of [TestCards]. Returns the pod's id. */
    fun setupPod(a: TestAccount, name: String, cards: List<TestCard> = listOf(TestCards.mountain, TestCards.cub, TestCards.spark)): String = runBlocking {
        fun id() = UUID.randomUUID().toString()
        val cube = DeckApi(origin).use { api ->
            api.submit(a.token, SubmitDefinitions(id(), cards.map { it.submitted() }))
            api.create(a.token, CreateCube(id(), "E2E cube", cards.map { SavedCubeEntry(it.digest, 12 / cards.size) }))
        }
        org.asyncttwiz.android.PodApi(origin).use { it.create(a.token, CreatePod(id(), name, cube.cube.id)).id }
    }

    /** The name the server holds for pod [id]. */
    fun podName(a: TestAccount, id: String): String = runBlocking { org.asyncttwiz.android.PodApi(origin).use { it.detail(a.token, id).name } }

    /** A game [name] of [a] (the inviter) and [b], invited and accepted, in its lobby with no deck chosen yet. */
    fun invitation(a: TestAccount, b: TestAccount, name: String): GameSummary {
        val invitation = lobbyCall { api ->
            api.add(a.token, AddContact(UUID.randomUUID().toString(), b.id))
            api.create(a.token, CreateInvitation(UUID.randomUUID().toString(), b.id, name))
        }
        Socket(b).use { recipient -> recipient.execute(invitation, recipient.open(invitation), GameAction.AcceptInvitation) }
        return invitation
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
            val started = lobbyCall { api -> api.head(a.token, invitation.id) }
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
        val invitation = lobbyCall { api ->
            api.add(a.token, AddContact(UUID.randomUUID().toString(), b.id))
            api.create(a.token, CreateInvitation(UUID.randomUUID().toString(), b.id, name))
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

    /** The five basic lands a device supplies with a pod deck (LOB-052, DRF-104): the test cards' M21 basics. */
    val basics: List<SubmittedDefinition> get() = with(TestCards) { listOf(plains, island, swamp, mountain, forest) }.map { it.submitted() }

    /**
     * A pod game of [a] (the pod's creator, who challenges) and [b] from a finished draft of [draftingPod]: each picks the
     * first card of every pack, adds [mainPlains] Plains to Main (an unlimited sideboard: 17 of each basic in Side, DRF-104)
     * and submits; [a] challenges [b], who accepts. Returns the game, in its lobby (decks prepared from the pod decks).
     */
    fun podGame(a: TestAccount, b: TestAccount, name: String, mainPlains: Int = 40): GameSummary = runBlocking {
        val pod = draftedPod(a, b, "$name pod", mainPlains)
        for (who in listOf(a, b)) submitPodDeck(who, pod)
        val invitation = lobbyCall { it.challengeFromPod(a.token, pod, ChallengeFromPod(UUID.randomUUID().toString(), b.id, name, basics)) }
        Socket(b).use { recipient -> recipient.execute(invitation, recipient.open(invitation), GameAction.AcceptInvitation) }
        invitation
    }

    /**
     * LOB-141: a pod game of [a] and [b] with a cube of nothing but [card], so every hand holds it: each drafts 45 copies into
     * Main (no basics there; an unlimited sideboard's Side basics, DRF-104), and [a] moves one copy to Side and one to Maybe.
     * Returns the game, in its lobby.
     */
    fun podGameOfOneCard(a: TestAccount, b: TestAccount, name: String, card: TestCard): GameSummary = runBlocking {
        fun id() = UUID.randomUUID().toString()
        val pod = draftedPod(a, b, "$name pod", mainPlains = 0, cards = listOf(card), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 15))
        org.asyncttwiz.android.PodApi(origin).use { api ->
            val detail = api.detail(a.token, pod)
            val copies = detail.build!!.copies
            api.move(a.token, pod, MovePodCopy(id(), detail.draftAttemptId!!, copies[0].copyId, PodDeckCategory.SIDE))
            api.move(a.token, pod, MovePodCopy(id(), detail.draftAttemptId!!, copies[1].copyId, PodDeckCategory.MAYBE))
        }
        for (who in listOf(a, b)) submitPodDeck(who, pod)
        val invitation = lobbyCall { it.challengeFromPod(a.token, pod, ChallengeFromPod(id(), b.id, name, basics)) }
        Socket(b).use { recipient -> recipient.execute(invitation, recipient.open(invitation), GameAction.AcceptInvitation) }
        invitation
    }

    /** A pod [name] of [a] and [b] whose draft has finished (each picked the first card of every pack and has [mainPlains]
     * Plains in Main), neither deck submitted yet ([submitPodDeck]). [cards], [settings]: as [draftingPod]. */
    fun draftedPod(a: TestAccount, b: TestAccount, name: String, mainPlains: Int = 40,
        cards: List<TestCard> = listOf(TestCards.mountain, TestCards.cub, TestCards.spark),
        settings: DraftSettings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 2)): String = runBlocking {
        fun id() = UUID.randomUUID().toString()
        val pod = draftingPod(a, b, name, cards, settings)
        // As the app does, the basics' card data goes up before the pod deck and the challenge that name them
        DeckApi(origin).use { api -> for (who in listOf(a, b)) api.submit(who.token, SubmitDefinitions(id(), basics)) }
        org.asyncttwiz.android.PodApi(origin).use { api ->
            var picks = 0
            while (api.detail(a.token, pod).phase != PodPhase.DRAFTED) {
                check(picks++ < 2 * settings.packsPerPlayer * settings.cardsPerPack + 50) { "the draft didn't finish" }
                for (who in listOf(a, b)) {
                    val detail = api.detail(who.token, pod)
                    val pack = detail.draft?.head ?: continue
                    api.pick(who.token, pod, PickPod(id(), detail.draftAttemptId!!, pack.id, listOf(pack.cards.first().id)))
                }
            }
            for (who in listOf(a, b)) {
                val attempt = api.detail(who.token, pod).draftAttemptId!!
                api.basics(who.token, pod, SetPodBasics(id(), attempt, PodDeckBasics(plains = mainPlains)))
            }
        }
        pod
    }

    /** [account] submits their deck of [pod] ([draftedPod]). */
    fun submitPodDeck(account: TestAccount, pod: String) = runBlocking {
        org.asyncttwiz.android.PodApi(origin).use { api ->
            api.submit(account.token, pod, SubmitPodDeck(UUID.randomUUID().toString(), api.detail(account.token, pod).draftAttemptId!!, basics))
        }
    }

    /** [account] writes [text] in [pod]'s chat (DRF-170), as their phone would. */
    fun podChat(account: TestAccount, pod: String, text: String): PodChatMessage = runBlocking {
        org.asyncttwiz.android.PodApi(origin).use { it.sendChat(account.token, pod, SendPodChat(UUID.randomUUID().toString(), text)) }
    }

    /** [account] writes [text] in [game]'s own chat (LOB-136), from a socket of their own. */
    fun gameChat(account: TestAccount, game: GameSummary, text: String) = Socket(account).use { socket ->
        val stream = socket.open(game)
        val command = GameCommand(UUID.randomUUID().toString(), GameScope(game.id, playId = game.playId),
            GameAction.SendChat(text), stream.streamId, stream.generation)
        socket.send(command)
        val result = socket.next { it is GameResult && it.requestId == command.requestId } as GameResult
        check(result.outcome == "accepted") { "game chat: ${result.outcome} ${result.reason}" }
    }

    /** [a] challenges [b] to a game [name] of [pod] ([a]'s deck submitted). */
    fun challenge(a: TestAccount, b: TestAccount, pod: String, name: String): GameSummary =
        lobbyCall { it.challengeFromPod(a.token, pod, ChallengeFromPod(UUID.randomUUID().toString(), b.id, name, basics)) }

    /** [account]'s invitation notices of game [gameId] as the server recorded them (DRF-137): one per notification. Read
     * from the database (see [gameNotice]). */
    fun invitationNotices(account: TestAccount, gameId: String): Int =
        E2eStack.query("SELECT count(*) FROM account_activity WHERE account_id = '${account.id}' AND game_id = '$gameId' " +
            "AND kind = 'INVITATION'").single().toInt()

    /** [account]'s current invitation notice of game [gameId] as their phone's notification gets it (the activity feed). */
    fun invitationNotice(account: TestAccount, gameId: String): ActivityHint? = runBlocking {
        org.asyncttwiz.android.ActivityApi(origin).use { api -> api.page(account.token, "0").entries }
            .lastOrNull { it.kind == ActivityKind.INVITATION && it.gameId == gameId }
    }

    /** Both players of [game] (in its lobby, decks prepared) Ready: the server starts it. Returns the started game. */
    fun readyBoth(a: TestAccount, b: TestAccount, game: GameSummary): GameSummary {
        Socket(a).use { owner -> Socket(b).use { recipient ->
            owner.execute(game, owner.open(game), GameAction.Ready)
            recipient.execute(game, recipient.open(game), GameAction.Ready)
        } }
        val started = lobbyCall { api -> api.head(a.token, game.id) }
        check(started.lifecycle == GameLifecycle.ACTIVE) { "the game didn't start: $started" }
        return started
    }

    /** The game as the server holds it now, as [account] reads it (its engine snapshot: the main line's head, each
     * player's local head). */
    fun head(account: TestAccount, gameId: String): GameHead = Socket(account).use { it.head(gameId) }

    /** A bare game socket for the setup's commands. */
    private class Socket(account: TestAccount) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>()
        private val text = StringBuilder()
        private val socket: WebSocket = connect(account)

        /** Again after a connection failure, as [accountCall] and [lobbyCall], at most three times. */
        private fun connect(account: TestAccount): WebSocket {
            repeat(3) { attempt ->
                try {
                    return HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization", "Bearer ${account.token}")
                        .connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(URI(origin.replace("http://", "ws://") + "/v1/socket"), this).get(20, TimeUnit.SECONDS)
                } catch (failure: java.util.concurrent.ExecutionException) {
                    if (failure.cause !is java.io.IOException || attempt == 2) throw failure
                    Thread.sleep(200)
                }
            }
            error("unreachable")
        }

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

/** [this] card's card data as a device uploads it. */
fun TestCard.submitted() = SubmittedDefinition(digest, definition)
