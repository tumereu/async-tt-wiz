package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 11: cheap card-flow spells. Looking at and arranging the top of the library (scry, surveil, "put them back in
 * any order", "you may shuffle", putting cards from the hand back on top), looking at a hand, milling, filtering mana,
 * investigating, a small -1/-1, and returning lands from the graveyard; dredge (CR 702.52) on two of them.
 */
class Chunk11LookScryAndMillTest {
    // ---- Shared shapes

    private val targetPlayer = TargetSlot(
        name = "player",
        what = TargetKind.Players(PlayerFilter.Any),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    private fun spell(vararg steps: Step) = Ability.Spell(Instructions(steps = steps.toList()))

    // ---- Scry and surveil (CR 701.22, 701.25)

    @Test
    fun mullItOver() = card(
        name = "Mull It Over",
        cost = "{U}",
        type = "Instant",
        text = """
            Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Surveil(Players.You, count = Value.Fixed(1))),
            drawACard,
        ),
    )

    @Test
    fun glanceAhead() = card(
        name = "Glance Ahead",
        cost = "{U}",
        type = "Instant",
        text = """
            Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Scry(Players.You, count = Value.Fixed(1))),
            drawACard,
        ),
    )

    @Test
    fun foretellTheWay() = card(
        name = "Foretell the Way",
        cost = "{U}",
        type = "Sorcery",
        text = "Scry 2, then draw a card. (To scry 2, look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Scry(Players.You, count = Value.Fixed(2))),
            drawACard,
        ),
    )

    @Test
    fun ichorDreams() = card(
        name = "Ichor Dreams",
        cost = "{U}",
        type = "Sorcery",
        text = "Draw a card. Scry 2.",
    ) parsesTo abilities(
        spell(
            drawACard,
            Step.Do(Effect.Scry(Players.You, count = Value.Fixed(2))),
        ),
    )

    // ---- Arranging the top of the library

    /** The cards looked at stay where they are and keep their identity; only their order changes. */
    @Test
    fun museAwhile() = card(
        name = "Muse Awhile",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Look at the top three cards of your library, then put them back in any order. You may shuffle.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(
                Objects.LibraryCards(Players.You, count = Value.Fixed(3), fromTop = true),
                bind = "seen",
                players = Players.You,
            )),
            Step.Do(Effect.Reorder(Objects.Bound("seen"), chooser = Players.You)),
            Step.May(listOf(Step.Do(Effect.Shuffle(Players.You))), player = Players.You),
            drawACard,
        ),
    )

    /** Cards put on top together are ordered by their owner (CR 401.4). */
    @Test
    fun mindSquall() = card(
        name = "Mind Squall",
        cost = "{U}",
        type = "Instant",
        text = "Draw three cards, then put two cards from your hand on top of your library in any order.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Draw(Players.You, count = Value.Fixed(3))),
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                    min = Value.Fixed(2),
                    max = Value.Fixed(2),
                ),
                bind = "put",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("put"), to = Destination(Zone.Library, libraryPosition = 0))),
        ),
    )

    // ---- Looking at a hand, milling

    @Test
    fun cerebralScan() = card(
        name = "Cerebral Scan",
        cost = "{U/P}",
        type = "Sorcery",
        text = """
            ({U/P} can be paid with either {U} or 2 life.)
            Look at target player's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.InZone(Zone.Hand),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))),
                ))), players = Players.You)),
                drawACard,
            ),
        )),
    )

    @Test
    fun thoughtSiphon() = card(
        name = "Thought Siphon",
        cost = "{U}",
        type = "Instant",
        text = """
            Target player mills two cards.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Do(Effect.Mill(Players.Target("player"), count = Value.Fixed(2))),
                drawACard,
            ),
        )),
    )

    // ---- Small spells that replace themselves

    /** "Two mana in any combination of colors": the colour of each mana is chosen separately. */
    @Test
    fun manashift() = card(
        name = "Manashift",
        cost = "{1}{R/G}",
        type = "Instant",
        text = """
            Add two mana in any combination of colors.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.AddMana(
                ManaProduction(ManaKind.Choose(amount = Value.Fixed(2), combination = true)),
                players = Players.You,
            )),
            drawACard,
        ),
    )

    @Test
    fun solidProof() = card(
        name = "Solid Proof",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Create a 0/3 blue Crab creature token.
            Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                name = "Crab Token",
                colorIndicator = listOf(Color.Blue),
                types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Crab")),
                power = PrintedNumber.Fixed(0),
                toughness = PrintedNumber.Fixed(3),
            )), count = Value.Fixed(1), controller = Players.You)),
            Step.Do(Effect.Action(KeywordAction.Investigate(count = Value.Fixed(1)))),
        ),
    )

    // ---- Dredge (CR 702.52)

    @Test
    fun shadowsting() = card(
        name = "Shadowsting",
        cost = "{B}",
        type = "Instant",
        text = """
            Target creature gets -1/-1 until end of turn.
            Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "creature",
                what = TargetKind.Objects(ObjectFilter.Type(CardType.Creature)),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1))),
                    affects = Affected.Objects(Objects.Target("creature")),
                    duration = Duration.EndOfTurn,
                )),
            ),
        )),
        Keyword.Dredge(3),
    )

    @Test
    fun rebirthFromTheMire() = card(
        name = "Rebirth from the Mire",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Return up to three target land cards from your graveyard to your hand.
            Dredge 3 (If you would draw a card, you may mill three cards instead. If you do, return this card from your graveyard to your hand.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(
                TargetSlot(
                    name = "cards",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        ObjectFilter.Type(CardType.Land),
                        ObjectFilter.InZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    ))),
                    min = Value.Fixed(0),
                    max = Value.Fixed(3),
                ),
            ),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("cards"), to = Destination(Zone.Hand)))),
        )),
        Keyword.Dredge(3),
    )
}
