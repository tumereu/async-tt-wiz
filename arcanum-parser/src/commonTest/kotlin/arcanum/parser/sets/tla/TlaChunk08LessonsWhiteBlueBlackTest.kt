package arcanum.parser.sets.tla

import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 08: Lesson instants and sorceries of white, blue and black: a kicked search for two cards (CR 702.33d),
 * every basic land type (CR 305.7), a token for the exiled permanent's controller, a creature type and keywords until end of turn, "one or
 * two" targets, a bonus for an Ally, all cards looked at to hand with three Lessons in the graveyard, a bounce that draws if you controlled
 * it (CR 608.2h), a library position chosen by the owner (CR 401.4), "choose one or both".
 */
class TlaChunk08LessonsWhiteBlueBlackTest {
    // CR 702.33d, 701.23
    @Test
    fun wayfarersPilgrimage() = card(
        name = "Wayfarer's Pilgrimage",
        cost = "{2}",
        type = "Sorcery — Lesson",
        text = """
            Kicker {2} (You may pay an additional {2} as you cast this spell.)
            Search your library for a basic land card. If this spell was kicked, instead search your library for a basic land card and a Shrine card. Reveal those cards, put them into your hand, then shuffle.
            You gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{2}"))),
        spell(
            Step.If(kicked,
                then = listOf(search(basicLand, reveal = true), search(shrine, reveal = true, bind = "found2"),
                    move(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("found2"))), Zone.Hand)),
                otherwise = listOf(search(basicLand, reveal = true), move(Objects.Bound("found"), Zone.Hand))),
            shuffle,
            gainLife(2)),
    )

    // CR 305.7: every basic land type (layer 4)
    @Test
    fun vitalweaving() = card(
        name = "Vitalweaving",
        cost = "{2}",
        type = "Instant — Lesson",
        text = """
            Lands you control gain all basic land types until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(and(land, you)), Mechanic.SetTypes(addSubtypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
            .map { SubtypeRef.Literal(Subtype(it)) })), draw(1)),
    )

    // CR 111.2: the token is its controller's (as the permanent last existed, CR 608.2h)
    @Test
    fun banishedHeirsExile() = card(
        name = "Banished Heir's Exile",
        cost = "{5}",
        type = "Instant — Lesson",
        text = """
            Exile target artifact, creature, or enchantment. Its controller creates a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(move(target("permanent"), Zone.Exile),
            doing(Effect.CreateToken(clue, n(1), Players.ControllerOf(target("permanent")))),
            targets = listOf(slot("permanent", or(artifact, creature, enchantment)))),
    )

    // CR 205.3 (a creature type in addition), 613.1d, 613.1f
    @Test
    fun enterTheHeraldsTrance() = card(
        name = "Enter the Herald's Trance",
        cost = "{W}",
        type = "Instant — Lesson",
        text = "Until end of turn, target creature you control becomes an Avatar in addition to its other types and gains flying, first strike, lifelink, and hexproof. (A creature with hexproof can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Avatar")))),
            has(Keyword.Flying, Keyword.FirstStrike, Keyword.Lifelink, Keyword.Hexproof)), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 115.1 ("one or two target creatures")
    @Test
    fun nimbleSteps() = card(
        name = "Nimble Steps",
        cost = "{2}{W}",
        type = "Instant — Lesson",
        text = "Untap one or two target creatures. They each get +2/+2 until end of turn.",
    ) parsesTo abilities(
        spell(untapIt(target("creatures")), pump(target("creatures"), 2, 2), targets = listOf(slot("creatures", creature, min = 1, max = 2))),
    )

    @Test
    fun hupHup() = card(
        name = "Hup Hup!",
        cost = "{W}",
        type = "Instant — Lesson",
        text = "Target creature you control gets +2/+2 until end of turn. If that creature is an Ally, it also gains flying until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2),
            Step.If(Condition.ObjectMatches(target("creature"), ally), then = listOf(gains(target("creature"), Keyword.Flying))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun gatherInsight() = card(
        name = "Gather Insight",
        cost = "{1}{U}",
        type = "Instant — Lesson",
        text = "Look at the top three cards of your library. Put one of those cards into your hand and the rest on the bottom of your library in any order. Put each of those cards into your hand instead if there are three or more Lesson cards in your graveyard.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(3), fromTop = true), Players.You, bind = "looked")),
            Step.If(threeLessons, then = listOf(move(Objects.Bound("looked"), Zone.Hand)), otherwise = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
                move(Objects.Bound("picked"), Zone.Hand),
                doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Destination(Zone.Library, libraryPosition = -1)))))),
    )

    // CR 608.2h: whether you controlled it, as it last existed
    @Test
    fun returningBladeBasics() = card(
        name = "Returning Blade Basics",
        cost = "{U}",
        type = "Sorcery — Lesson",
        text = "Return target nonland permanent to its owner's hand. If you controlled that permanent, draw a card.",
    ) parsesTo abilities(
        spell(doing(Effect.Move(target("permanent"), Destination(Zone.Hand), preBind = "returned")),
            Step.If(Condition.ObjectMatches(Objects.Bound("returned"), you), then = listOf(draw(1))),
            targets = listOf(slot("permanent", nonlandPermanent))),
    )

    // CR 118.12a
    @Test
    fun thatllDouseYa() = card(
        name = "That'll Douse Ya!",
        cost = "{1}{U}",
        type = "Instant — Lesson",
        text = "Counter target spell unless its controller pays {2}.",
    ) parsesTo abilities(
        spell(
            Step.Pay(manaCost("{2}"), payer = Players.ControllerOf(target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Counter(target("spell"))))),
            targets = listOf(slot("spell", spellFilter))),
    )

    // CR 401.4: the owner chooses second from the top or the bottom
    @Test
    fun vanishedHours() = card(
        name = "Vanished Hours",
        cost = "{4}{U}",
        type = "Instant — Lesson",
        text = """
            The owner of target creature or enchantment puts it into their library second from the top or on the bottom. You create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("second from the top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            Step.If(Condition.Chose("position", "second from the top"),
                then = listOf(move(target("permanent"), Zone.Library, position = 1)),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            createClue,
            targets = listOf(slot("permanent", or(creature, enchantment)))),
    )

    @Test
    fun squidStance() = card(
        name = "Squid Stance",
        cost = "{U}",
        type = "Instant — Lesson",
        text = "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1, has(Keyword.Hexproof)), untapIt(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun wisecrackersVerse() = card(
        name = "Wisecracker's Verse",
        cost = "{3}{U}{U}",
        type = "Instant — Lesson",
        text = """
            Counter target spell.
            Draw a card, then mill three cards.
            Untap target land.
        """,
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), draw(1), mill(3), untapIt(target("land")),
            targets = listOf(slot("spell", spellFilter), slot("land", land))),
    )

    // CR 700.2 ("choose one or both")
    @Test
    fun thePrincessAlwaysLies() = card(
        name = "The Princess Always Lies",
        cost = "{1}{B}",
        type = "Instant — Lesson",
        text = """
            Choose one or both —
            • Target creature gets -1/-1 until end of turn.
            • Put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
        modal(
            mode(pump(target("creature"), -1, -1), targets = listOf(slot("creature", creature))),
            mode(counters(target("creature")), targets = listOf(slot("creature", creature))),
            max = 2,
        ),
    )

    @Test
    fun tyrantsCruelty() = card(
        name = "Tyrant's Cruelty",
        cost = "{2}{B}",
        type = "Sorcery — Lesson",
        text = "Tyrant's Cruelty deals 2 damage to target player. That player discards two cards.",
    ) parsesTo abilities(
        spell(damage(2, Recipients(players = Players.Target("player"))), discard(2, Players.Target("player")), targets = listOf(targetPlayer)),
    )
}
