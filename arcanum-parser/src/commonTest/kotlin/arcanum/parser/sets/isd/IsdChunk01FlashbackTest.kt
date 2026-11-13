package arcanum.parser.sets.isd

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Choice
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 01: Flashback, part 1 (CR 702.34a: cast from the graveyard for the flashback cost, then exiled, CR 118.9). The flashback cost is
 * a keyword with its own mana cost; the spell part reads as it does without flashback.
 */
class IsdChunk01FlashbackTest {
    private val permanentTypes = listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker)

    // CR 111.1 (tokens created tapped), 702.34a
    @Test
    fun legionOfTheBuried() = card(
        name = "Legion of the Buried",
        cost = "{5}{B}{B}{B}",
        type = "Sorcery",
        text = """
            Create thirteen tapped 2/2 black Zombie creature tokens.
            Flashback {7}{B}{B}{B} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(doing(Effect.CreateToken(zombie, n(13), Players.You, tapped = true))),
        Keyword.Flashback(manaCost("{7}{B}{B}{B}")),
    )

    // CR 110.4 (the six permanent types), 205.2a, 608.2d (the type is chosen as it resolves), 702.34a
    @Test
    fun slowResurgence() = card(
        name = "Slow Resurgence",
        cost = "{3}{G}{G}",
        type = "Sorcery",
        text = """
            Choose a permanent type. Return all cards of the chosen type from your graveyard to your hand.
            Flashback {5}{G}{G} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.CardType(permanentTypes), bind = "type", chooser = Players.You),
            move(Objects.Each(inYourGraveyard(cardFilter, ObjectFilter.Type(CardTypeRef.Chosen("type")))), Zone.Hand),
        ),
        Keyword.Flashback(manaCost("{5}{G}{G}")),
    )

    // CR 701.9b (a random discard), 702.34a
    @Test
    fun franticBabbling() = card(
        name = "Frantic Babbling",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Draw two cards, then discard a card at random.
            Flashback {2}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(draw(2), doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, random = true))),
        Keyword.Flashback(manaCost("{2}{U}")),
    )

    // CR 101.4 (choices in turn order), 608.2e (then all the rest are destroyed at once), 702.34a
    @Test
    fun heavenlyCulling() = card(
        name = "Heavenly Culling",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = """
            Each player chooses a creature they control. Destroy the rest.
            Flashback {5}{W}{W} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)),
                    bind = "chosen", chooser = Players.Bound("player")),
                destroy(Objects.Except(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))), Objects.Bound("chosen"))),
            )),
        ),
        Keyword.Flashback(manaCost("{5}{W}{W}")),
    )

    // CR 115.1 (the cards are targets in that player's graveyard), 400.3 (into their owner's library), 701.24a, 702.34a
    @Test
    fun recollectionsPath() = card(
        name = "Recollection's Path",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Target player shuffles up to three target cards from their graveyard into their library.
            Flashback {G} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(
            move(target("cards"), Zone.Library),
            doing(Effect.Shuffle(Players.Target("player"))),
            targets = listOf(targetPlayer, slot("cards", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))),
                min = 0, max = 3)),
        ),
        Keyword.Flashback(manaCost("{G}")),
    )

    // CR 509.1a (can't block), 611.2a (this turn), 115.1a (up to two targets), 702.34a
    @Test
    fun owlwingGrasp() = card(
        name = "Owlwing Grasp",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Up to two target creatures can't block this turn.
            Flashback {3}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target("creatures")), Duration.EndOfTurn)),
            targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        Keyword.Flashback(manaCost("{3}{R}")),
    )

    // CR 701.8a, 702.34a
    @Test
    fun ancestralSpite() = card(
        name = "Ancestral Spite",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Destroy target artifact.
            Flashback {G} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        Keyword.Flashback(manaCost("{G}")),
    )

    // CR 119.1, 702.34a (the flashback cost is red though the card is black)
    @Test
    fun thumpAfterDark() = card(
        name = "Thump After Dark",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target opponent loses 3 life.
            Flashback {5}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(loseLife(3, Players.Target("opponent")), targets = listOf(targetOpponent)),
        Keyword.Flashback(manaCost("{5}{R}")),
    )

    // CR 707.2 (a token copy), 702.34a
    @Test
    fun cacklingMirrorling() = card(
        name = "Cackling Mirrorling",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = """
            Create a token that's a copy of target creature you control.
            Flashback {5}{U}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(create(TokenSpec.CopyOf(target("creature"), emptyList())), targets = listOf(slot("creature", and(creature, you)))),
        Keyword.Flashback(manaCost("{5}{U}{U}")),
    )

    // CR 107.3 (X announced), 702.34a, 118.9 (X in the flashback cost is chosen when it is cast that way)
    @Test
    fun fiendsGambit() = card(
        name = "Fiend's Gambit",
        cost = "{X}{R}",
        type = "Sorcery",
        text = """
            Fiend's Gambit deals X damage to any target.
            Flashback {X}{R}{R}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(damage(Value.Announced(name = "X"), toAny), targets = listOf(anyTarget), announced = listOf("X")),
        Keyword.Flashback(manaCost("{X}{R}{R}{R}")),
    )

    // CR 701.17a (mill), 702.34a
    @Test
    fun dreamWarp() = card(
        name = "Dream Warp",
        cost = "{U}",
        type = "Instant",
        text = """
            Target player mills three cards.
            Flashback {1}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(mill(3, Players.Target("player")), targets = listOf(targetPlayer)),
        Keyword.Flashback(manaCost("{1}{U}")),
    )

    // CR 701.26a (tap), 115.1a (up to two targets), 702.34a
    @Test
    fun senseOfForeboding() = card(
        name = "Sense of Foreboding",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Tap up to two target creatures.
            Flashback {1}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(tapIt(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        Keyword.Flashback(manaCost("{1}{U}")),
    )

    // CR 608.2c, 702.34a
    @Test
    fun bannedDistillation() = card(
        name = "Banned Distillation",
        cost = "{2}{U}",
        type = "Instant",
        text = """
            Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard.
            Flashback {6}{B} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "picked", chooser = Players.You),
            move(Objects.Bound("picked"), Zone.Hand), move(Objects.Except(Objects.Bound("looked"), Objects.Bound("picked")), Zone.Graveyard)),
        Keyword.Flashback(manaCost("{6}{B}")),
    )

    // CR 120.3a, 120.3e, 702.34a
    @Test
    fun ghostspark() = card(
        name = "Ghostspark",
        cost = "{R}",
        type = "Instant",
        text = """
            Ghostspark deals 1 damage to any target.
            Flashback {3}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(damage(1, toAny), targets = listOf(anyTarget)),
        Keyword.Flashback(manaCost("{3}{R}")),
    )
}
