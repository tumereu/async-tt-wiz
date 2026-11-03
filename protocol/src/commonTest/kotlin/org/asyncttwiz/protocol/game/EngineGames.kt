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
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.random.Random

/**
 * Seeded games of invented cube cards played into [GameEvent]s on the public engine API, as a device would make them:
 * one acting point per event (a chosen input, then the random results the device answers), with the table part a
 * device adds (a place for each permanent that entered), and now and then a table-only event (a card moved on the
 * battlefield, a setting changed).
 */
internal class EngineGames(seed: Long, decks: Pair<List<String>, List<String>> = CREATURES to SPELLS) {
    val alice = PlayerId("alice")
    val bob = PlayerId("bob")
    val rnd = Random(seed)
    val initial: GameState = Engine.newGame(listOf(alice to decks.first, bob to decks.second).map { (p, names) ->
        Seat(p, names.mapIndexed { i, name -> DeckCard(CardId("${p.value}.$i"), Cards.definition(name)) })
    })
    val codec = EngineJson(GameDefinitions.of(initial))
    private var inputsThisTurn = 0
    private var turn = 0
    private var ids = 0

    fun model(): GameModel = GameModel(initial.deepCopy())

    fun over(model: GameModel): Boolean = Engine.result(model.engine) != null

    /** The next event on [model] (applied to it), made on [parentId]. */
    fun next(model: GameModel, parentId: String, id: String = "e${++ids}"): GameEvent {
        val author = if (rnd.nextBoolean()) alice.value else bob.value
        val builder = EventBuilder(model, id, parentId, author)
        val placed = model.table.placements.keys
        if (model.engine.turn.number > 0 && rnd.nextInt(8) == 0) {
            // A table-only event: a card moved on the battlefield, or a setting changed
            val obj = model.engine.battlefield.filter { it in placed }.randomOrNull(rnd)
            if (obj != null && rnd.nextBoolean()) {
                val before = model.table.placements.getValue(obj)
                builder.table(TableChange.Placed(obj, before, Placement(rnd.nextInt(12), rnd.nextInt(3))))
            } else {
                val player = if (rnd.nextBoolean()) alice else bob
                val before = model.table.settingsOf(player)
                builder.table(TableChange.Settings(player, before, before.copy(fullControl = !before.fullControl)))
            }
            return builder.build()
        }
        builder.perform(choose(model.engine))
        // The random results the device answers at once (I-D54 §3)
        while (true) {
            val d = model.engine.pending?.decision as? Decision.Random ?: break
            builder.perform(Input.Answer(d.id, random(d.what)))
        }
        // A place for each permanent that entered (the device's table part)
        val taken = model.table.placements.filterKeys { it in model.engine.battlefield }.values.toMutableSet()
        for (obj in model.engine.battlefield) if (obj !in model.table.placements && obj is ObjectId.Card) {
            val owner = model.engine.obj(obj)?.controller ?: continue
            val row = if (owner == alice) 0 else 3
            val cell = generateSequence(0) { it + 1 }.map { Placement(it, row) }.first { it !in taken }
            taken += cell
            builder.table(TableChange.Placed(obj, null, cell))
        }
        if (model.engine.turn.number != turn) { turn = model.engine.turn.number; inputsThisTurn = 0 } else inputsThisTurn++
        return builder.build()
    }

    private fun choose(state: GameState): Input {
        val d = state.pending?.decision
        check(d !is Decision.HostFill && d !is Decision.OtherView) { "full information has no host fills: $d" }
        if (state.turn.number == 0 && d == null) return Input.StartGame()
        val listed = state.players.flatMap { Engine.options(state, it.id) }.distinct().filter { it !is Input.Concede }
        check(listed.isNotEmpty()) { "stuck at ${d ?: state.turn}" }
        if (d != null) return listed.random(rnd)
        val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
            (it is Input.DeclareBlockers && it.blocks.isEmpty()) }
        return if (inputsThisTurn < 40 && doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd)
        else listed.filter { it !in doing }.ifEmpty { listed }.random(rnd)
    }

    private fun random(kind: RandomKind): Answer = when (kind) {
        RandomKind.CoinFlip -> Answer.CoinFlip(rnd.nextBoolean())
        is RandomKind.Die -> Answer.DieRolls(List(kind.count) { rnd.nextInt(1, kind.sides + 1) })
        is RandomKind.Objects -> {
            check(!kind.newIds) { "full information keeps ids" }
            Answer.RandomObjects(kind.among.shuffled(rnd).take(kind.count))
        }
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
