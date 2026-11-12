package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import kotlin.random.Random

/**
 * Seeded games of invented cube cards played into [GameEvent]s on the public engine API, as a device would make them:
 * one acting point per event (a chosen input, then the random results the device answers), and now and then a
 * table-only event (an early answer given or taken back).
 */
internal class EngineGames(seed: Long, decks: Pair<List<String>, List<String>> = CREATURES to SPELLS, life: Int = 20,
    /** Alice's cards outside the game besides a sideboard (dungeon cards, CR 309.2). */
    outside: List<arcanum.api.CardDefinition> = emptyList()) {
    val alice = PlayerId("alice")
    val bob = PlayerId("bob")
    val rnd = Random(seed)
    val initial: GameState = Engine.newGame(listOf(alice to decks.first, bob to decks.second).map { (p, names) ->
        Seat(p, names.mapIndexed { i, name -> DeckCard(CardId("${p.value}.$i"), Cards.definition(name)) },
            if (p == alice) outside.mapIndexed { i, d -> DeckCard(CardId("alice.outside.$i"), d) } else emptyList())
    }, startingLife = life)
    val codec = EngineJson(GameDefinitions.of(initial))
    private var inputsThisTurn = 0
    private var turn = 0
    private var ids = 0

    /** The game's random secret (I-D56 §9). */
    val secret: String = LineRandom.secret(ByteArray(32) { (seed * 31 + it).toByte() })

    fun model(): GameModel = GameModel(initial.deepCopy(), TableState(secret = secret))

    fun over(model: GameModel): Boolean = Engine.result(model.engine) != null

    /** The next event on [model] (applied to it), made on [parentId]. */
    fun next(model: GameModel, parentId: String, id: String = "e${++ids}"): GameEvent {
        val author = if (rnd.nextBoolean()) alice.value else bob.value
        val builder = EventBuilder(model, id, parentId, author)
        if (model.engine.turn.number > 0 && rnd.nextInt(8) == 0) {
            // A table-only event: an early answer given or taken back
            val player = if (rnd.nextBoolean()) alice else bob
            val key = "early:${player.value}"
            val before = model.table.earlyAnswers[key]
            builder.table(TableChange.EarlyAnswered(key, before, if (before == null) EarlyAnswer(player, key, Answer.YesNo(true)) else null))
            return builder.build()
        }
        builder.perform(choose(model.engine))
        // The random answers from the line's stream (I-D56 §9)
        builder.answerRandoms()
        if (model.engine.turn.number != turn) { turn = model.engine.turn.number; inputsThisTurn = 0 } else inputsThisTurn++
        return builder.build()
    }

    private fun choose(state: GameState): Input {
        val d = state.pending?.decision
        check(d !is Decision.NamedCard) { "no card is named here: $d" }
        if (state.turn.number == 0 && d == null) return Input.StartGame()
        val listed = state.players.flatMap { Engine.options(state, it.id) }.distinct().filter { it !is Input.Concede }
        check(listed.isNotEmpty()) { "stuck at ${d ?: state.turn}" }
        if (d != null) return listed.random(rnd)
        val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
            (it is Input.DeclareBlockers && it.blocks.isEmpty()) }
        return if (inputsThisTurn < 40 && doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd)
        else listed.filter { it !in doing }.ifEmpty { listed }.random(rnd)
    }

    /** Plays up to [events] events (or the game's end) from [model] at [parentId]: the events and each one's state. */
    fun play(model: GameModel, parentId: String, events: Int, prefix: String = "e"): List<Pair<GameEvent, GameModel>> {
        val out = mutableListOf<Pair<GameEvent, GameModel>>()
        var parent = parentId
        while (out.size < events && !over(model)) {
            val event = next(model, parent, "$prefix${out.size + 1}")
            out += event to model.copy()
            parent = event.id
        }
        return out
    }

    companion object {
        private fun lands(a: String, b: String) = List(8) { a } + List(8) { b }
        val SPELLS = lands("Island", "Mountain") + listOf(
            "Storm Spark", "Cinder Bolt", "Magma Needle", "Flash Surge", "Grindstone Rasp", "Stormforge Charm",
            "Faithless Rummage", "Mull It Over", "Glance Ahead", "Foretell the Way", "Thought Siphon", "Mind Chill",
            "Linked Lightning", "Flash of Mirroring", "Mounting Retribution", "Novice Flamecaller", "Wyrm's Fury Conduit",
            "Ysolde, the Unbound Scholar", "Grubb, the Fingerless", "Expanse // Eruption", "Denial", "Spell Prick", "Click Back",
            "Twincast Adept")
        val CREATURES = lands("Forest", "Plains") + listOf(
            "Elvish Seer", "Thicketwood Elves", "Canopy Visionary", "Elvish Hierophant", "Bough-Crowned Oracle",
            "Elvish Drillmaster", "Haven Warden", "Larder Ouphes", "Loyal Hearthhound", "Hollowell Investigator",
            "Wall of Portents", "Golem Grafter", "Torvanna, Elder-Tree Spirit", "Radiant Acolyte", "Brisa, Bounding Shepherd",
            "Aldric, Fist of the Seraphs", "Seasoned Pathfinder", "Sunning Mudlizard", "Bestower of Sigils", "Commanding Orator",
            "Ranger-Marshal of Dawn", "Elenwyn, Spinner of Dawn", "Knight of the Relic Vault", "Podrick Burrowfoot")
        val GRAVEYARD = lands("Swamp", "Forest") + listOf(
            "Festering Gremlin", "Stitchwork Porter", "Undying Valet", "Wreckage Analyst", "Satyr Pathseeker", "Gorehaunt",
            "Bloodsworn Duelist", "Tombscuttler", "Netherbound Turncoat", "Kettle Kitten", "Stairway from the Depths",
            "Fiend Sculptor", "Wight of the Relic Vault", "Corpse Gnawer", "Plague Shamblers", "Unyielding Dead",
            "Rebirth from the Mire", "Shadowsting", "Neckless Horseman", "Undead Soothsayer", "Burrowed Shambler",
            "Shuffling Wight", "Sinister Rootwork", "Undying Chronicler")
    }
}
