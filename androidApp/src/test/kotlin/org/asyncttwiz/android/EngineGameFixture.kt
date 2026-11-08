package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.engine.Engine
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState

/**
 * I-D56 step C: a game on engine state as the server starts it, for the app's engine path: Alice's and Bob's decks of
 * invented Mountains and Storm Sparks (parsed from their oracle text, as on a device), the play's own base board
 * ([base], [TableStatesFixture]'s containers) and the server's first event ([start]: the game started, simultaneous
 * mulligans) with its [snapshot]. With [dungeons] (LOB-117) Alice's spells are Torch Maps ({0}: venture into the dungeon) and she
 * owns two invented dungeons outside the game, a plain one and an Undercity, as her device brings them. With [extraTurns]
 * (TRN-052) a third of each deck are Ember Strides ({R}, sorcery: take an extra turn after this one). With [declaring]
 * (CMB-028) a third of Alice's deck are Cinder Hounds (2/1 for {R} with "{R}: +1/+0") and of Bob's Ash Oracles (a
 * planeswalker for {R}, loyalty 3).
 */
internal class EngineGameFixture(val startingPlayer: String = TableStatesFixture.a, creatures: Boolean = false, dungeons: Boolean = false,
    heralds: Boolean = false, extraTurns: Boolean = false, declaring: Boolean = false,
    /** Alice's deck made of these cards in turn instead (by name: [Choices]), each free, for Automatum's choice panels. */
    aliceCards: List<String> = emptyList(),
    /** Bob's deck made of these cards in turn instead (by name: [Choices], "Mountain" or "Storm Spark"). */
    bobCards: List<String> = emptyList()) {
    private val f = TableStatesFixture
    private class Invented(face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private val spark = Invented(DefinitionFace("spark", "Storm Spark", typeLine = "Instant", manaCost = "{R}",
        rulesText = "Storm Spark deals 3 damage to any target."),
        OracleCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target."))
    private val mountain = Invented(DefinitionFace("mountain", "Mountain", typeLine = "Basic Land — Mountain"),
        OracleCard("Mountain", null, "Basic Land — Mountain"))
    private val cub = Invented(DefinitionFace("cub", "Ember Cub", typeLine = "Creature — Elemental Cat", manaCost = "{R}",
        power = "2", toughness = "1"), OracleCard("Ember Cub", "{R}", "Creature — Elemental Cat", "", "2", "1"))
    private val herald = Invented(DefinitionFace("herald", "Ember Herald", typeLine = "Creature — Elemental", manaCost = "{R}",
        rulesText = "When this creature enters, create a 1/1 red Elemental creature token.", power = "1", toughness = "1"),
        OracleCard("Ember Herald", "{R}", "Creature — Elemental", "When this creature enters, create a 1/1 red Elemental creature token.", "1", "1"))
    private val hound = Invented(DefinitionFace("hound", "Cinder Hound", typeLine = "Creature — Elemental Dog", manaCost = "{R}",
        rulesText = "{R}: This creature gets +1/+0 until end of turn.", power = "2", toughness = "1"),
        OracleCard("Cinder Hound", "{R}", "Creature — Elemental Dog", "{R}: This creature gets +1/+0 until end of turn.", "2", "1"))
    private val oracle = Invented(DefinitionFace("oracle", "Ash Oracle", typeLine = "Legendary Planeswalker — Chandra", manaCost = "{R}",
        rulesText = "[+1]: You gain 1 life.", loyalty = "3"),
        OracleCard("Ash Oracle", "{R}", "Legendary Planeswalker — Chandra", "[+1]: You gain 1 life.", loyalty = "3"))
    private val stride = Invented(DefinitionFace("stride", "Ember Stride", typeLine = "Sorcery", manaCost = "{R}",
        rulesText = "Take an extra turn after this one."), OracleCard("Ember Stride", "{R}", "Sorcery", "Take an extra turn after this one."))

    /** Free cards whose casting, resolving or triggers ask Automatum's choices (STK-030, STK-044), by name. */
    object Choices {
        const val DIVINATION = "Ember Divination"
        const val CHARM = "Cinder Charm"
        const val SEER = "Spark Seer"
        const val QUENCH = "Quench Spark"
        const val TOTEM = "Spark Totem"
        /** {X}: X damage to any target (STK-023: the X stepper, bounded by the mana). */
        const val BURST = "Ember Burst"
        /** A fetch-style land: its ability searches its controller's library for a basic land card. */
        const val TRAIL = "Cinder Trail"
        /** Offspring {R} and "When this creature enters, you may forage. If you do, put two +1/+1 counters on it." (owner
         * playtest 2026-11-06: worded as a real offspring-and-forage creature). */
        const val SENTRY = "Hollowroot Sentry"
        /** A free Food. */
        const val PIE = "Hedge Pie"
        /** A free "Draw a card." sorcery: casting it fills the graveyard. */
        const val STUDY = "Hedge Study"
        /** A free instant without targets: its holder can always respond. */
        const val GLINT = "Ember Glint"
        /** A free "Look at target player's hand." sorcery: its resolving shows its caster the target's hidden hand (TRN-067). */
        const val GLANCE = "Ember Glance"
        /** A free "Target player reveals their hand." sorcery: its resolving reveals the target's hand (LOB-135). */
        const val EXPOSURE = "Ember Exposure"
        /** A free "Target player reveals the top two cards of their library." sorcery (LOB-135). */
        const val AUGURY = "Ember Augury"
        /** A free instant milling fourteen with cycling {R} and "When you cycle this card, each opponent mills four cards."
         * (owner playtest 2026-11-07: worded as a real cycling card with a cycle trigger). */
        const val REASON = "Shattered Reason"
        /** A free sorcery that reveals its caster's top card (HIS-110: the other player's reveal panel). */
        const val INSIGHT = "Ember Insight"
        /** The Insight as a free instant: the other player's reveal in the viewer's own turn (HIS-110). */
        const val FORESIGHT = "Ember Foresight"
        /** A free creature with offspring {R} (ZON-080: "Cast" and "Cast with offspring {R}" chips). */
        const val KIT = "Ember Kit"
        /** A free mana rock: "{T}: Add {R}." (a second source beside a Mountain on turn one). */
        const val SHARD = "Ember Shard"
        /** {2}{R} with kicker {1}, offspring {R} and dash {R}: eight complete ways, one "Cast…" chip (ZON-080). */
        const val BROODRAIDER = "Ember Broodraider"
        /** A free artifact whose ability's cost sacrifices another creature (D1: the creature picked on the table). */
        const val ALTAR = "Ember Altar"
        /** A free 1/1 creature with nothing to do: identical ones lie in one stack (TBL-053). */
        const val PUP = "Ash Pup"
    }
    private val KIT_TEXT = "Offspring {R} (You may pay an additional {R} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)"
    private val BROODRAIDER_TEXT = "Kicker {1} (You may pay an additional {1} as you cast this spell.)\nOffspring {R}\nDash {R}"
    private val REASON_TEXT = "Each opponent mills fourteen cards.\nCycling {R} ({R}, Discard this card: Draw a card.)\n" +
        "When you cycle this card, each opponent mills four cards."
    private val SENTRY_TEXT = "Offspring {R} (You may pay an additional {R} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)\n" +
        "When this creature enters, you may forage. If you do, put two +1/+1 counters on it. (To forage, exile three cards from your graveyard or sacrifice a Food.)"
    private val TRAIL_TEXT = "{T}, Pay 1 life, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield, then shuffle."
    private fun free(id: String, name: String, typeLine: String, text: String, power: String = "", toughness: String = "") =
        Invented(DefinitionFace(id, name, typeLine = typeLine, manaCost = "{0}", rulesText = text, power = power, toughness = toughness),
            OracleCard(name, "{0}", typeLine, text, power.ifEmpty { null }, toughness.ifEmpty { null }))
    private val choiceCards = listOf(
        free("divination", Choices.DIVINATION, "Sorcery", "Scry 2."),
        free("charm", Choices.CHARM, "Instant", "Choose one —\n• Cinder Charm deals 1 damage to any target.\n• You gain 2 life."),
        free("seer", Choices.SEER, "Creature — Elemental", "When this creature enters, it deals 1 damage to any target.", "1", "1"),
        free("quench", Choices.QUENCH, "Instant", "Counter target spell."),
        free("totem", Choices.TOTEM, "Artifact", "{T}: This artifact deals 1 damage to any target."),
        Invented(DefinitionFace("burst", Choices.BURST, typeLine = "Sorcery", manaCost = "{X}", rulesText = "Ember Burst deals X damage to any target."),
            OracleCard(Choices.BURST, "{X}", "Sorcery", "Ember Burst deals X damage to any target.")),
        Invented(DefinitionFace("trail", Choices.TRAIL, typeLine = "Land", rulesText = TRAIL_TEXT),
            OracleCard(Choices.TRAIL, null, "Land", TRAIL_TEXT)),
        free("sentry", Choices.SENTRY, "Creature — Squirrel Warrior", SENTRY_TEXT, "2", "2"),
        free("pie", Choices.PIE, "Artifact — Food", "{2}, {T}, Sacrifice this artifact: You gain 3 life."),
        free("study", Choices.STUDY, "Sorcery", "Draw a card."),
        free("glint", Choices.GLINT, "Instant", "You gain 2 life."),
        free("glance", Choices.GLANCE, "Sorcery", "Look at target player's hand."),
        free("exposure", Choices.EXPOSURE, "Sorcery", "Target player reveals their hand."),
        free("augury", Choices.AUGURY, "Sorcery", "Target player reveals the top two cards of their library."),
        free("reason", Choices.REASON, "Instant", REASON_TEXT),
        free("insight", Choices.INSIGHT, "Sorcery", "Reveal the top card of your library and put that card into your hand."),
        free("foresight", Choices.FORESIGHT, "Instant", "Reveal the top card of your library and put that card into your hand."),
        free("kit", Choices.KIT, "Creature — Elemental Fox", KIT_TEXT, "1", "1"),
        free("shard", Choices.SHARD, "Artifact", "{T}: Add {R}."),
        free("altar", Choices.ALTAR, "Artifact", "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life."),
        free("pup", Choices.PUP, "Creature — Elemental Dog", "", "1", "1"),
        Invented(DefinitionFace("broodraider", Choices.BROODRAIDER, typeLine = "Creature — Elemental Warrior", manaCost = "{2}{R}",
            rulesText = BROODRAIDER_TEXT, power = "2", toughness = "2"),
            OracleCard(Choices.BROODRAIDER, "{2}{R}", "Creature — Elemental Warrior", BROODRAIDER_TEXT, "2", "2")),
    ).associateBy { it.submitted.definition.name }
    private val aliceChoices = aliceCards.map { choiceCards[it] ?: if (it == "Mountain") mountain else error("no card $it") }
    private val bobChoices = bobCards.map { choiceCards[it] ?: when (it) { "Mountain" -> mountain; "Storm Spark" -> spark; else -> error("no card $it") } }

    private val map = Invented(DefinitionFace("map", "Torch Map", typeLine = "Sorcery", manaCost = "{0}",
        rulesText = "Venture into the dungeon."), OracleCard("Torch Map", "{0}", "Sorcery", "Venture into the dungeon."))
    /** The dungeons as Alice's device brings them (OutsideCards), parsed as the server parses them. */
    val outside: List<SubmittedDefinition> = if (!dungeons) emptyList() else listOf(
        CardDefinition("Lantern Crypt", listOf(DefinitionFace("00000000-0000-5000-8000-0000000000d1", "Lantern Crypt", typeLine = "Dungeon",
            rulesText = """
                Crypt Door — You gain 1 life. (Leads to: Bone Hall, Dark Well)
                Bone Hall — Each opponent loses 1 life. (Leads to: Final Vault)
                Dark Well — You gain 2 life. (Leads to: Final Vault)
                Final Vault — You gain 3 life.
            """.trimIndent())), provider = "test"),
        CardDefinition("Sunken City", listOf(DefinitionFace("00000000-0000-5000-8000-0000000000d2", "Sunken City", typeLine = "Dungeon — Undercity",
            rulesText = """
                You can't enter this dungeon unless you "venture into Undercity."
                Flooded Stair — You gain 4 life. (Leads to: Abyss Throne)
                Abyss Throne — You gain 5 life.
            """.trimIndent())), provider = "test"),
    ).map { requireNotNull(org.asyncttwiz.protocol.game.OutsideCards.dungeon(it)) }

    val a = f.a
    val b = f.b
    val game: GameSummary = f.game
    /** Mountains and Storm Sparks; with [creatures] also Ember Cubs (2/1 for {R}), a third each (with [heralds] Ember
     * Heralds instead: 1/1 for {R} whose enters trigger makes a 1/1 token); with [dungeons] Alice's are two
     * thirds Torch Maps. */
    private val decks = listOf(a, b).associateWith { p -> List(30) { i -> f.id("$p/card/$i") to when {
        aliceChoices.isNotEmpty() && p == a -> aliceChoices[i % aliceChoices.size]
        bobChoices.isNotEmpty() && p == b -> bobChoices[i % bobChoices.size]
        dungeons && p == a -> if (i % 3 == 0) mountain else map
        i % 3 == 2 -> spark
        heralds && i % 3 == 1 -> herald
        extraTurns && i % 3 == 1 -> stride
        declaring && i % 3 == 1 -> if (p == a) hound else oracle
        creatures && i % 3 == 1 -> cub
        else -> mountain
    } } }

    /** The play as it started: the fixture's containers holding the decks' cards in the libraries. */
    val base: PlayState = f.initial.let { p ->
        val cards = decks.flatMap { (player, deck) -> deck.map { (id, card) ->
            PlayCard(id, card.submitted.digest, player, f.id("${if (player == a) "a" else "b"}/LIBRARY"), card.submitted.definition.faces.single().faceId)
        } }
        p.copy(definitions = (listOf(spark.submitted, mountain.submitted) + listOfNotNull(cub.submitted.takeIf { creatures }) +
            listOfNotNull(herald.submitted.takeIf { heralds }) + listOfNotNull(stride.submitted.takeIf { extraTurns }) +
            listOfNotNull(hound.submitted.takeIf { declaring }, oracle.submitted.takeIf { declaring }) +
            listOfNotNull(map.submitted.takeIf { dungeons }) + aliceChoices.map { it.submitted } + bobChoices.map { it.submitted } + outside)
            .distinctBy { it.digest }.sortedBy { it.digest },
            board = p.board.copy(cards = cards, stackEffects = emptyList(), combat = null, engineRecords = emptyList(),
                containers = p.board.containers.map { c ->
                    if (c.zone == PlayZone.LIBRARY && !c.temp) c.copy(cards = cards.filter { it.containerId == c.id }.map { it.id }, layers = emptyList())
                    else c.copy(cards = emptyList(), layers = emptyList())
                }))
    }

    /** As the server starts it: every player's Skip when I have no choice off in the shared state (I-D56 §13). */
    val model: GameModel = GameModel(Engine.newGame(listOf(a, b).map { p ->
        Seat(PlayerId(p), decks.getValue(p).map { (id, card) -> DeckCard(CardId(id), card.definition) },
            if (p != a) emptyList() else outside.mapIndexed { i, d ->
                DeckCard(CardId(f.id("$p/outside/$i")), org.asyncttwiz.protocol.game.GameCards.parse(d.definition).definition) })
    }).also { s -> s.players.toList().forEach { p -> s.apply(arcanum.engine.change.Changes.skipWhenNoChoice(s, p.id, false)) } },
        TableState(secret = LineRandom.secret(ByteArray(32) { 3 })))
    val codec = EngineJson(GameDefinitions.of(model.engine))
    /** The state before the game's start (the root's checkpoint, as the server stores it). */
    val rootState: String = codec.encodeModel(model)

    /** The server's first event: the game started (Alice first), simultaneous mulligans. */
    val start: GameEvent = EventBuilder(model, f.id("start"), f.id("root"), "server").run {
        perform(Input.StartGame(StartOptions(startingPlayer = PlayerId(startingPlayer), simultaneousMulligans = true, freeFirstMulligan = true)))
        answerRandoms()
        build()
    }

    /** The player who doesn't start. */
    val other: String = if (startingPlayer == a) b else a

    /** The other player's keep, the server's next event after [start] (TRN-027: the starting player's device waits for
     * it; an opening keep is never assumed). */
    val keep: GameEvent = EventBuilder(model.copy(), f.id("keep/$other"), start.id, other).run {
        perform(Input.SetupAnswer(PlayerId(other), arcanum.engine.decision.Answer.Option("Keep")))
        answerRandoms()
        build()
    }

    /** The state after [keep]. */
    val kept: GameModel get() = model.copy().also { it.apply(keep) }

    /** The snapshot a device loads at the start (both local heads at the start event); with [otherKept], after the other
     * player's [keep] (both local heads there): the starting player's keep then begins the first turn. By default the
     * player plays on while the other is away: "Always assume resolves" on (HIS-096). */
    fun snapshot(settings: org.asyncttwiz.protocol.game.PlaySettings? = PLAYS_ON, otherKept: Boolean = false): EngineSnapshot =
        if (otherKept) EngineSnapshot(codec.encodeDefinitions(), f.id("root"), keep.id, codec.encodeModel(kept),
            localHeads = mapOf(a to keep.id, b to keep.id), settings = settings)
        else EngineSnapshot(codec.encodeDefinitions(), f.id("root"), start.id, codec.encodeModel(model),
            localHeads = mapOf(a to start.id, b to start.id), settings = settings)

    /** The snapshot the other player's device loads after [events] made on [snapshot] (otherKept) up to [state]: their
     * local head at [keep], the events past it buffered. */
    fun behind(events: List<GameEvent>, state: GameModel): EngineSnapshot =
        EngineSnapshot(codec.encodeDefinitions(), f.id("root"), events.last().id, codec.encodeModel(state),
            localHeads = mapOf(startingPlayer to events.last().id, other to keep.id),
            baseId = keep.id, base = codec.encodeModel(kept), events = events.map(codec::encodeEvent))

    /** The revision the server sends for [event] (accepted), with the local heads after it. */
    fun revision(event: GameEvent, n: Int, heads: Map<String, String>): GameRevision = GameRevision(game.id, "$n", "${n + 1}",
        DeviceRandom.uuid(), event.author, GameScope(game.id, playId = base.id), game, engineEvent = codec.encodeEvent(event),
        engine = EngineUpdate(localHeads = heads))
}

/** A player who plays on while the other is away (HIS-096: the other player's pauses assumed at once). */
internal val PLAYS_ON = org.asyncttwiz.protocol.game.PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always)
