package arcanum.parser.sets.sos

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 07: preparation cards (CR 722) in white and blue. Each card has two faces: the creature, and the prepare
 * spell in its inset frame, cast only as the copy a prepared permanent's controller gets in exile (CR 722.3c). "Enters prepared" is a
 * replacement (CR 614.1c, 722.3a); "becomes prepared" by triggers (end step, attacking, tokens entering) and by an activated ability
 * tapping creatures; the spells: tap or untap, Swords-style exile, "you and target opponent each draw", Brainstorm-style drawing and
 * putting back, X Fractals with X counters each, a blink of a non-Pilot creature.
 */
class SosChunk07PrepareWhiteBlueTest {
    private val opponentControlsMoreCreatures = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
        Value.Count(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))))), Comparison.Greater,
        Value.Count(Objects.Each(and(creature, you))))))), atLeast = 1)
    private val opponentHasMoreCards = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
        Value.HandSize(Players.Tested), Comparison.Greater, Value.HandSize(Players.You))))), atLeast = 1)

    // CR 608.2d: the choice to tap or untap is made as it resolves
    @Test
    fun crackWaylayer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Crack Waylayer",
            cost = "{W}",
            type = "Creature — Human Wizard",
            pt = "1/2",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Retort in Kind",
            cost = "{1}{W}",
            type = "Sorcery",
            text = """
                You may tap or untap target creature.
                Draw a card.
            """,
        ) to abilities(
            spell(
                Step.May(listOf(Step.Choose(Choice.Option(listOf("tap", "untap")), bind = "tapOrUntap", chooser = Players.You),
                    ifThen(Condition.Chose("tapOrUntap", "tap"), tapIt(target("creature")), otherwise = listOf(untapIt(target("creature"))))), Players.You),
                draw(1),
                targets = listOf(slot("creature", creature)),
            ),
        ),
    )

    // CR 722.3a: it becomes prepared only if an opponent controls more creatures as the ability resolves
    @Test
    fun emeritusOfConcord() = cardFaces(
        Layout.Prepare,
        card(
            name = "Emeritus of Concord",
            cost = "{1}{W}{W}",
            type = "Creature — Cat Cleric",
            pt = "3/3",
            text = "When this creature enters, target player creates a 1/1 white and black Inkling creature token with flying. Then if an opponent controls more creatures than you, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(
                entersSelf,
                doing(Effect.CreateToken(inkling, n(1), Players.Target("player"))),
                ifThen(opponentControlsMoreCreatures, becomesPrepared()),
                targets = listOf(targetPlayer),
            ),
        ),
        card(
            name = "Blades into Ploughs",
            cost = "{W}",
            type = "Instant",
            text = "Exile target creature. Its controller gains life equal to its power.",
        ) to abilities(
            spell(
                move(target("creature"), Zone.Exile),
                doing(Effect.GainLife(Players.ControllerOf(target("creature")), power(target("creature")))),
                targets = listOf(slot("creature", creature)),
            ),
        ),
    )

    @Test
    fun oathswornSquire() = cardFaces(
        Layout.Prepare,
        card(
            name = "Oathsworn Squire",
            cost = "{3}{W}",
            type = "Creature — Cat Cleric",
            pt = "3/3",
            text = """
                First strike
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.FirstStrike, entersPrepared,
        ),
        card(
            name = "Agora's Boon",
            cost = "{W}",
            type = "Sorcery",
            text = "Target creature gets +1/+0 and gains flying until end of turn.",
        ) to abilities(
            spell(pump(target("creature"), 1, 0, has(Keyword.Flying)), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 603.4: checked as each end step begins and as it resolves
    @Test
    fun pairedScholars() = cardFaces(
        Layout.Prepare,
        card(
            name = "Paired Scholars",
            cost = "{1}{W}",
            type = "Creature — Human Cleric Wizard",
            pt = "2/2",
            text = """
                First strike
                At the beginning of each end step, if an opponent has more cards in hand than you, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.FirstStrike,
            triggered(stepBegins(TurnStep.End, PlayerFilter.Any), becomesPrepared(), interveningIf = opponentHasMoreCards),
        ),
        card(
            name = "Hidden Assignation",
            cost = "{1}{W}{W}",
            type = "Sorcery",
            text = "You and target opponent each draw three cards.",
        ) to abilities(
            // CR 101.4: both draw at the same time
            spell(draw(3, Players.Each(PlayerFilter.Or(listOf(PlayerFilter.You, PlayerFilter.Is(Players.Target("opponent")))))), targets = listOf(targetOpponent)),
        ),
    )

    @Test
    fun penknifeLaureate() = cardFaces(
        Layout.Prepare,
        card(
            name = "Penknife Laureate",
            cost = "{1}{W}",
            type = "Creature — Human Cleric",
            pt = "1/1",
            text = """
                Double strike
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.DoubleStrike, entersPrepared,
        ),
        card(
            name = "Double Purpose",
            cost = "{1}{W}",
            type = "Sorcery",
            text = "Target creature gets +1/+0 and gains double strike until end of turn.",
        ) to abilities(
            spell(pump(target("creature"), 1, 0, has(Keyword.DoubleStrike)), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 603.2c by its wording: one trigger for tokens entering together
    @Test
    fun ghostcallDevotee() = cardFaces(
        Layout.Prepare,
        card(
            name = "Ghostcall Devotee",
            cost = "{2}{W}",
            type = "Creature — Cat Cleric",
            pt = "3/3",
            text = "Whenever one or more tokens you control enter, this creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(Trigger.On(EventPattern.ZoneChange(and(token, you), to = Zone.Battlefield), oneOrMore = true), becomesPrepared()),
        ),
        card(
            name = "Scroll Surge",
            cost = "{1}{W}",
            type = "Sorcery",
            text = "One or two target creatures each get +2/+2 until end of turn.",
        ) to abilities(
            spell(pump(target("creature"), 2, 2), targets = listOf(slot("creature", creature, min = 1, max = 2))),
        ),
    )

    @Test
    fun quadComposer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Quad Composer",
            cost = "{3}{U}",
            type = "Creature — Merfolk Bard",
            pt = "3/4",
            text = """
                Ward {2}
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Ward(manaCost("{2}")), entersPrepared,
        ),
        card(
            name = "Watery Aria",
            cost = "{4}{U}",
            type = "Sorcery",
            text = "Create a 3/3 blue and red Elemental creature token with flying.",
        ) to abilities(
            spell(create(elemental)),
        ),
    )

    // CR 118.12: "you may exile eight cards. If you do" is a cost paid on resolution
    @Test
    fun emeritusOfNotions() = cardFaces(
        Layout.Prepare,
        card(
            name = "Emeritus of Notions",
            cost = "{3}{U}{U}",
            type = "Creature — Human Wizard",
            pt = "5/5",
            text = """
                Flying, ward {2}
                This creature enters prepared.
                Whenever this creature attacks, you may exile eight cards from your graveyard. If you do, this creature becomes prepared.
            """,
        ) to abilities(
            Keyword.Flying,
            Keyword.Ward(manaCost("{2}")),
            entersPrepared,
            triggered(attacksSelf, Step.Pay(cost(CostPart.Exile(CostObjects.Choose(cardFilter, n(8)), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
                ifThen(Condition.Did("paid"), becomesPrepared())),
        ),
        card(
            name = "Forebears' Recollection",
            cost = "{U}",
            type = "Instant",
            text = "Target player draws three cards.",
        ) to abilities(
            spell(draw(3, Players.Target("player")), targets = listOf(targetPlayer)),
        ),
    )

    @Test
    fun hearteningFlier() = cardFaces(
        Layout.Prepare,
        card(
            name = "Heartening Flier",
            cost = "{2}{U}",
            type = "Creature — Bird Wizard",
            pt = "2/3",
            text = """
                Flying
                Whenever this creature attacks, it becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flying, triggered(attacksSelf, becomesPrepared()),
        ),
        card(
            name = "Leap Aloft",
            cost = "{U}",
            type = "Instant",
            text = "Target creature gains flying until end of turn.",
        ) to abilities(
            spell(gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 602.2: the cost taps it and two other untapped creatures
    @Test
    fun attunedTrio() = cardFaces(
        Layout.Prepare,
        card(
            name = "Attuned Trio",
            cost = "{U}",
            type = "Creature — Merfolk Bard Wizard",
            pt = "1/1",
            text = "{T}, Tap two untapped creatures you control: This creature becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(creature, n(2)))), becomesPrepared()),
        ),
        card(
            name = "Thoughtsquall",
            cost = "{U}",
            type = "Instant",
            text = "Draw three cards, then put two cards from your hand on top of your library in any order.",
        ) to abilities(
            spell(
                draw(3),
                Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(2), n(2)), bind = "put", chooser = Players.You),
                move(Objects.Bound("put"), Zone.Library, position = 0),
            ),
        ),
    )

    // CR 107.3a: X announced; the counters go on every Fractal you control, the new ones too
    @Test
    fun ilsaWardenOfFortune() = cardFaces(
        Layout.Prepare,
        card(
            name = "Ilsa, Warden of Fortune",
            cost = "{2}{U}",
            type = "Legendary Creature — Human Wizard",
            pt = "2/4",
            text = """
                Ilsa enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                When Ilsa enters, draw two cards, then discard two cards.
            """,
        ) to abilities(
            entersPrepared, triggered(entersSelf, draw(2), discard(2)),
        ),
        card(
            name = "Seer's Bounty",
            cost = "{X}{X}{U}",
            type = "Sorcery",
            text = "Create X 0/0 green and blue Fractal creature tokens, then put X +1/+1 counters on each Fractal you control.",
        ) to abilities(
            spell(create(fractal, x), counters(Objects.Each(and(sub("Fractal"), you)), x), announced = listOf("X")),
        ),
    )

    @Test
    fun vistaPainter() = cardFaces(
        Layout.Prepare,
        card(
            name = "Vista Painter",
            cost = "{1}{U}",
            type = "Creature — Merfolk Wizard",
            pt = "2/1",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Vivid Notion",
            cost = "{4}{U}",
            type = "Sorcery",
            text = "Draw two cards.",
        ) to abilities(
            spell(draw(2)),
        ),
    )

    // CR 400.7: the card returned is a new object
    @Test
    fun aircoachDriver() = cardFaces(
        Layout.Prepare,
        card(
            name = "Aircoach Driver",
            cost = "{2}{U}",
            type = "Creature — Bird Pilot",
            pt = "2/3",
            text = """
                Flash
                Flying, vigilance
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flash, Keyword.Flying, Keyword.Vigilance, entersPrepared,
        ),
        card(
            name = "Board the Coach",
            cost = "{U}",
            type = "Instant",
            text = "Exile target non-Pilot creature you control, then return that card to the battlefield under its owner's control.",
        ) to abilities(
            spell(
                doing(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
                targets = listOf(slot("creature", and(not(sub("Pilot")), creature, you))),
            ),
        ),
    )

    @Test
    fun grimoireSearcher() = cardFaces(
        Layout.Prepare,
        card(
            name = "Grimoire Searcher",
            cost = "{3}{U}",
            type = "Creature — Bird Wizard",
            pt = "3/3",
            text = """
                Flying
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flying, entersPrepared,
        ),
        card(
            name = "Studious Review",
            cost = "{U}",
            type = "Sorcery",
            text = "Draw two cards, then discard two cards.",
        ) to abilities(
            spell(draw(2), discard(2)),
        ),
    )
}
