package webspike.bench

import arcanum.api.Layout
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.InitialShuffle
import org.asyncttwiz.protocol.InitialStarter
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayPlayer
import org.asyncttwiz.protocol.PlayRoot
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.RandomV1
import org.asyncttwiz.protocol.StarterChoice
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.TableState

/**
 * Web spike: the heavy board of androidApp's EndTurnBenchmark (about 44 permanents a side: anthems, a lord, equipment and
 * auras attached, upkeep and end step triggers, mana creatures and pumpers) as a game a device loads from the server's
 * snapshot, at Alice's first main phase, in common code so it runs on the JVM and in Kotlin/Wasm alike. About 180 cards.
 */
class BenchBoard(board: List<Pair<BenchCard, Int>> = HEAVY) {
    val a = id("alice")
    val b = id("bob")
    private val filler = listOf(BenchCard.MOUNTAIN to 20, BenchCard.SPARK to 12, BenchCard.CUB to 8)
    private val cards = (board + filler).flatMap { (card, n) -> List(n) { card } }
    private val decks = listOf(a, b).associateWith { p -> cards.mapIndexed { i, card -> id("$p/card/$i") to card } }
    val permanents = board.sumOf { it.second } * 2

    val base: PlayState = run {
        val containers = listOf(a, b).flatMap { p ->
            PlayZone.entries.filter { it != PlayZone.STACK }.map { zone -> PlayContainer(id("$p/$zone"), zone, p) }
        } + PlayContainer(id("stack"), PlayZone.STACK)
        val playCards = decks.flatMap { (player, deck) -> deck.map { (cardId, card) ->
            PlayCard(cardId, card.submitted.digest, player, id("$player/${PlayZone.LIBRARY}"), card.submitted.definition.faces.single().faceId)
        } }
        PlayState(gameId = id("game"), id = id("play"), preparationRound = id("round"), startRequestId = id("start"),
            root = PlayRoot(id("root"), id("branch")),
            board = BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), playCards,
                containers.map { c -> if (c.zone == PlayZone.LIBRARY) c.copy(cards = playCards.filter { it.containerId == c.id }.map { it.id }) else c },
                org.asyncttwiz.protocol.TurnState(a)),
            definitions = cards.map { it.submitted }.distinctBy { it.digest }.sortedBy { it.digest },
            shuffles = listOf(a, b).map { InitialShuffle(it, SEED, emptyList()) },
            starter = InitialStarter(StarterChoice(a), a))
    }

    val snapshot: EngineSnapshot = run {
        val model = GameModel(Engine.newGame(listOf(a, b).map { p ->
            Seat(PlayerId(p), decks.getValue(p).map { (cardId, card) -> DeckCard(CardId(cardId), card.definition) })
        }).also { s -> s.players.toList().forEach { p -> s.apply(arcanum.engine.change.Changes.skipWhenNoChoice(s, p.id, false)) } },
            TableState(secret = LineRandom.secret(ByteArray(32) { 5 })))
        fun event(eventId: String, parent: String, author: String, input: (GameState) -> Input) = EventBuilder(model, id(eventId), id(parent), author).run {
            perform(input(model.engine)); answerRandoms(); build()
        }
        fun keep(p: String): (GameState) -> Input = { s ->
            Engine.options(s, PlayerId(p)).first { i ->
                (i is Input.SetupAnswer && i.player == PlayerId(p) && i.answer == arcanum.engine.decision.Answer.Option("Keep")) ||
                    (i is Input.Answer && i.answer == arcanum.engine.decision.Answer.Option("Keep"))
            }
        }
        event("start", "root", "server") {
            Input.StartGame(StartOptions(startingPlayer = PlayerId(a), simultaneousMulligans = true, freeFirstMulligan = true))
        }
        event("keep/b", "start", b, keep(b))
        val head = event("keep/a", "keep/b", a, keep(a))
        check(model.engine.turn.number == 1) { "turn ${model.engine.turn}" }
        for (p in listOf(a, b)) put(model.engine, PlayerId(p), board)
        val codec = EngineJson(GameDefinitions.of(model.engine))
        EngineSnapshot(codec.encodeDefinitions(), id("root"), head.id, codec.encodeModel(model),
            localHeads = mapOf(a to head.id, b to head.id), settings = PlaySettings(alwaysAssumeResolves = true))
    }

    private fun put(s: GameState, player: PlayerId, board: List<Pair<BenchCard, Int>>) {
        val moved = mutableListOf<ObjectId>()
        for ((card, n) in board) repeat(n) {
            val p = s.player(player)
            val objectId = (p.library + p.hand).first { o -> o !in moved && s.obj(o)?.definition == card.definition }
            moved += objectId
        }
        val index = s.players.indexOfFirst { it.id == player }
        val p = s.players[index]
        s.players[index] = p.copy(library = p.library - moved.toSet(), hand = p.hand - moved.toSet(),
            libraryPlaces = p.libraryPlaces - moved.toSet())
        val creatures = moved.filter { s.obj(it)!!.definition!!.faces[0].types.contains(arcanum.api.CardType.Creature) }
        var next = 0
        for (o in moved) {
            val obj = s.obj(o)!!
            val face = obj.definition!!.faces[0]
            val attaches = face.subtypes.any { it.name == "Equipment" || it.name == "Aura" }
            s.objects[o] = obj.copy(zone = Zone.Battlefield, controller = player, controlSince = ControlSince(player, -1),
                timestamp = ++s.timestampCounter,
                attachedTo = if (attaches && creatures.isNotEmpty()) TargetRef.Object(creatures[next++ % creatures.size]) else null)
            s.battlefield.add(o)
        }
    }

    /** The game loaded afresh from [snapshot] (and [json], the snapshot as text, when given: what crosses to a worker), at
     * Alice's first main phase with priority. */
    fun device(json: EngineSnapshot = snapshot): GameDevice {
        var n = 0
        val (device, _) = GameDevice.load(json, PlayerId(a), takenBack = true, ids = { id("bench-${++n}") })
        device.advance(1_000)
        check(device.status == GameDevice.Status.YourMove) { "status ${device.status}" }
        val s = device.model.engine
        check(s.turn.step == arcanum.api.TurnStep.PrecombatMain && s.priority.holder == PlayerId(a)) { "at ${s.turn} ${s.priority}" }
        return device
    }

    companion object {
        private const val NS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        fun id(value: String): String = CommandIds.uuidV5(NS, value)
        val SEED: String = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })

        val HEAVY = listOf(
            BenchCard.MOUNTAIN to 7, BenchCard.FOREST to 7, BenchCard.BANNER to 2, BenchCard.MARSHAL to 1, BenchCard.ELF_LORD to 1,
            BenchCard.CLERIC to 3, BenchCard.IMP to 2, BenchCard.SENTINEL to 1, BenchCard.BRUTE to 3, BenchCard.KNIGHT to 3,
            BenchCard.TENDER to 2, BenchCard.HOUND to 2, BenchCard.ELF to 3, BenchCard.PLATE to 4, BenchCard.WINGS to 3,
        )
    }
}

/** An invented card (our names, oracle wordings of real templates), as the server and a device parse it. */
class BenchCard(val name: String, cost: String?, type: String, text: String = "", power: String? = null, toughness: String? = null) {
    val submitted = CardDefinition(name, listOf(DefinitionFace(BenchBoard.id("face/$name"), name, typeLine = type,
        manaCost = cost.orEmpty(), rulesText = text, power = power.orEmpty(), toughness = toughness.orEmpty())))
        .let { org.asyncttwiz.protocol.SubmittedDefinition(DeckJson.digest(it), it) }
    val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard(name, cost, type, text, power, toughness))))

    companion object {
        val MOUNTAIN = BenchCard("Mountain", null, "Basic Land — Mountain")
        val FOREST = BenchCard("Forest", null, "Basic Land — Forest")
        val SPARK = BenchCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
        val CUB = BenchCard("Ember Cub", "{R}", "Creature — Elemental Cat", "", "2", "1")
        val BRUTE = BenchCard("Thornback Brute", "{2}{G}", "Creature — Beast", "Trample", "3", "3")
        val KNIGHT = BenchCard("Vigil Knight", "{1}{W}", "Creature — Human Knight", "Vigilance, lifelink", "2", "2")
        val BANNER = BenchCard("Banner of the Host", "{2}{W}", "Enchantment", "Creatures you control get +1/+1.")
        val MARSHAL = BenchCard("Warband Marshal", "{1}{W}{W}", "Creature — Human Soldier", "Other creatures you control get +1/+1.", "2", "2")
        val ELF_LORD = BenchCard("Grovecall Elder", "{1}{G}{G}", "Creature — Elf Druid", "Other Elf creatures you control get +1/+1.", "2", "2")
        val ELF = BenchCard("Grove Skirmisher", "{G}", "Creature — Elf Warrior", "", "1", "1")
        val TENDER = BenchCard("Grove Tender", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1", "1")
        val HOUND = BenchCard("Cinder Hound", "{R}", "Creature — Elemental Dog", "{R}: This creature gets +1/+0 until end of turn.", "2", "1")
        val CLERIC = BenchCard("Dawnward Cleric", "{1}{W}", "Creature — Human Cleric", "At the beginning of your upkeep, you gain 1 life.", "1", "3")
        val IMP = BenchCard("Duskward Imp", "{1}{B}", "Creature — Imp", "Flying\nAt the beginning of your end step, each opponent loses 1 life.", "1", "1")
        val SENTINEL = BenchCard("Watchful Sentinel", "{2}{W}", "Creature — Human Soldier", "Whenever another creature you control enters, you gain 1 life.", "2", "3")
        val PLATE = BenchCard("Ironhide Plate", "{2}", "Artifact — Equipment", "Equipped creature gets +2/+2.\nEquip {2}")
        val WINGS = BenchCard("Wings of the Gale", "{1}{U}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1 and has flying.")
    }
}
