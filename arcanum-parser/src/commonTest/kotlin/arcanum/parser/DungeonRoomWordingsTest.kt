package arcanum.parser

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The room effects of the dungeon cards (CR 309.4c) that the general sentence rules didn't read, one test per wording, each on an
 * invented dungeon whose rooms use the printed templates.
 */
class DungeonRoomWordingsTest {
    /** The rooms of an invented dungeon whose rooms are [rooms] ("[name] — [effect]"), the topmost first, each leading to the next. */
    private fun rooms(name: String, vararg rooms: Pair<String, String>): List<Instructions> {
        val text = rooms.mapIndexed { n, (room, effect) ->
            "$room — $effect" + (rooms.getOrNull(n + 1)?.let { " (Leads to: ${it.first})" } ?: "")
        }.joinToString("\n")
        val face = OracleParser.parse(OracleCard(name, null, "Dungeon", text))
        assertEquals(rooms.map { it.first }, face.rooms.map { it.name })
        assertEquals(rooms.indices.map { if (it == rooms.lastIndex) emptyList() else listOf(it + 1) }, face.rooms.map { it.next })
        return face.rooms.map { it.instructions }
    }

    private fun type(type: CardType) = ObjectFilter.Type(type)
    private fun bound(name: String) = Objects.Bound(name)
    private val creature = type(CardType.Creature)

    // CR 309.4c, 508.1c, 611.2a: a room's "Target creature can't attack until your next turn." (the target chosen as the room ability
    // is put on the Stack)
    @Test
    fun cantAttackUntilYourNextTurn() = assertEquals(
        listOf(Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(creature), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.CantAttack()),
                Affected.Objects(Objects.Target("creature")),
                Duration.UntilYourNextTurn,
            ))),
        )),
        rooms("Gloomwater Warren", "Snaring Hollow" to "Target creature can't attack until your next turn."),
    )

    // CR 309.4c, 400.7: "Exile the top two cards of your library. You may play them." The permission has no end: once a card leaves
    // exile it is a new object
    @Test
    fun exileTopCardsAndPlayThem() = assertEquals(
        listOf(Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(2), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                Affected.Objects(bound("exiled")),
                Duration.Indefinitely,
            )),
        ))),
        rooms("Gloomwater Warren", "Glyph Vault" to "Exile the top two cards of your library. You may play them."),
    )

    // CR 309.4c, 601.2, 608.2g, 701.20a: "Draw three cards and reveal them. You may cast one of them without paying its mana cost."
    // (cast while the room ability resolves; a land among them can't be cast)
    @Test
    fun drawRevealAndCastOneFree() = assertEquals(
        listOf(Instructions(steps = listOf(
            Step.Do(Effect.Draw(Players.You, Value.Fixed(3), postBind = "drawn")),
            Step.Do(Effect.Reveal(bound("drawn"))),
            Step.Choose(Choice.Objects(ObjectFilter.Not(type(CardType.Land)), min = Value.Fixed(0), max = Value.Fixed(1), from = bound("drawn")),
                bind = "toCast", chooser = Players.You),
            Step.Do(Effect.Cast(bound("toCast"), PlayKind.Cast, withoutPaying = true, caster = Players.You)),
        ))),
        rooms("Gloomwater Warren", "Archmage's Study" to
            "Draw three cards and reveal them. You may cast one of them without paying its mana cost."),
    )

    // CR 101.4, 118.12a, 309.4c: "Each player loses 2 life unless they discard a card." Each player in turn order may discard; those who
    // don't lose the life
    @Test
    fun eachPlayerLosesLifeUnlessTheyDiscard() = assertEquals(
        listOf(Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = Value.Fixed(1))))),
                payer = Players.Bound("player"), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.LoseLife(Players.Bound("player"), Value.Fixed(2))))),
        ))))),
        rooms("Gloomwater Warren", "Weeping Veil" to "Each player loses 2 life unless they discard a card."),
    )

    // CR 101.4, 118.12a, 309.4c: "Each player loses 2 life unless they sacrifice a creature, artifact, or land of their choice." (one
    // permanent of any of the three types)
    @Test
    fun eachPlayerLosesLifeUnlessTheySacrifice() = assertEquals(
        listOf(Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.Or(listOf(creature, type(CardType.Artifact), type(CardType.Land))),
                count = Value.Fixed(1),
            )))), payer = Players.Bound("player"), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.LoseLife(Players.Bound("player"), Value.Fixed(2))))),
        ))))),
        rooms("Gloomwater Warren", "Sinking Sands" to
            "Each player loses 2 life unless they sacrifice a creature, artifact, or land of their choice."),
    )

    // CR 309.4c, 701.9a, 701.21a: "Discard a card and sacrifice a creature, an artifact, and a land." The discard, then three different
    // permanents chosen (each choice excludes the ones before it) and sacrificed at once
    @Test
    fun discardAndSacrificeOneOfEach() {
        fun yours(type: CardType, vararg earlier: String) = ObjectFilter.And(listOf(type(type), ObjectFilter.ControlledBy(PlayerFilter.You)) +
            earlier.map { ObjectFilter.Not(ObjectFilter.Is(bound(it))) })
        fun choose(bind: String, filter: ObjectFilter) =
            Step.Choose(Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1)), bind = bind, chooser = Players.You)
        assertEquals(
            listOf(Instructions(steps = listOf(
                Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any)),
                choose("sacrificed1", yours(CardType.Creature)),
                choose("sacrificed2", yours(CardType.Artifact, "sacrificed1")),
                choose("sacrificed3", yours(CardType.Land, "sacrificed1", "sacrificed2")),
                Step.Do(Effect.Sacrifice(Objects.Union(listOf(bound("sacrificed1"), bound("sacrificed2"), bound("sacrificed3"))))),
            ))),
            rooms("Gloomwater Warren", "Forgotten Pit" to "Discard a card and sacrifice a creature, an artifact, and a land."),
        )
    }

    // CR 122.6, 309.4c, 400.7, 701.20a, 701.24a: "Reveal the top ten cards of your library. Put a creature card from among them onto the
    // battlefield with three +1/+1 counters on it. It gains hexproof until your next turn. Then shuffle." (a creature card if there
    // is one; "it" is the permanent it became)
    @Test
    fun revealAndPutACreatureOntoTheBattlefield() = assertEquals(
        listOf(Instructions(steps = listOf(
            Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(10), fromTop = true), bind = "seen")),
            Step.Choose(Choice.Objects(creature, min = Value.Fixed(1), max = Value.Fixed(1), from = bound("seen")), bind = "chosen", chooser = Players.You),
            Step.Do(Effect.Move(bound("chosen"),
                Destination(Zone.Battlefield, counters = listOf(CountersToPut(CounterKind.PowerToughness(1, 1), Value.Fixed(3)))), postBind = "put")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Hexproof))), Affected.Objects(bound("put")),
                Duration.UntilYourNextTurn)),
            Step.Do(Effect.Shuffle(Players.You)),
        ))),
        rooms("Gloomwater Warren", "Bone Throne" to
            "Reveal the top ten cards of your library. Put a creature card from among them onto the battlefield with three +1/+1 " +
                "counters on it. It gains hexproof until your next turn. Then shuffle."),
    )

    // CR 309.4, 309.5a: a whole invented dungeon whose rooms use these wordings and the ones read before, every room read
    @Test
    fun aWholeDungeonIsRead() {
        val face = OracleParser.parse(OracleCard("Gloomwater Warren", null, "Dungeon", """
            Drowned Gate — Each player loses 1 life. (Leads to: Weeping Veil, Forgotten Pit)
            Weeping Veil — Each player loses 2 life unless they discard a card. (Leads to: Sinking Sands)
            Sinking Sands — Each player loses 2 life unless they sacrifice a creature, artifact, or land of their choice. (Leads to: Bone Throne)
            Forgotten Pit — Discard a card and sacrifice a creature, an artifact, and a land. (Leads to: Bone Throne)
            Bone Throne — Draw three cards and reveal them. You may cast one of them without paying its mana cost.
        """.trimIndent()))
        assertEquals(listOf(listOf(1, 3), listOf(2), listOf(4), listOf(4), emptyList()), face.rooms.map { it.next })
        val manual = face.rooms.filter { r -> r.instructions.steps.any { it is Step.Manual } }.map { it.name }
        assertEquals(emptyList(), manual)
    }

    // CR 309.4: a room whose effect isn't read leaves the whole dungeon unread, never a room done by hand
    @Test
    fun aDungeonWithAnUnreadRoomIsUnread() {
        val text = """
            Drowned Gate — Each player loses 1 life. (Leads to: Moonlit Hall)
            Moonlit Hall — Whenever the moon is full, dance a slow jig.
        """.trimIndent()
        val face = OracleParser.parse(OracleCard("Jigging Warren", null, "Dungeon", text))
        assertEquals(emptyList(), face.rooms)
        assertEquals(listOf<arcanum.api.Ability>(arcanum.api.Ability.Unparsed(text)), face.abilities)
    }
}
