package arcanum.parser.sets.tmt

import arcanum.api.AttackTarget
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 03: instants and sorceries with sneak (CR 702.190a: an alternative cost cast in the declare blockers
 * step), the "Technique" cycle, and the other big spells: tokens entering tapped and attacking only if the sneak cost was paid (CR
 * 508.4), "one or two target cards", milling half a library rounded up (CR 107.1a), "if an enchantment was destroyed this way", a wheel
 * each player may choose (CR 101.4), looking at cards with a total mana value limit, searching the library and outside the game for cards
 * with different names (CR 400.11a) with an opponent choosing among them, and returning all creatures then shuffling hands and
 * graveyards away.
 */
class TmtChunk03TechniquesTest {
    private val spirit = creatureToken("Ninja Turtle Spirit", listOf(Color.White), 1, 1)

    // CR 508.4: the tokens are put onto the battlefield attacking only when the sneak cost was paid
    @Test
    fun loneWanderersTechnique() = card(
        name = "Lone Wanderer's Technique",
        cost = "{3}{W}",
        type = "Instant",
        text = """
            Sneak {1}{W} (You may cast this spell for {1}{W} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Create three 1/1 white Ninja Turtle Spirit creature tokens. If this spell's sneak cost was paid, they enter tapped and attacking.
        """,
    ) parsesTo abilities(
        sneak("{1}{W}"),
        spell(Step.If(sneakPaid,
            then = listOf(doing(Effect.CreateToken(spirit, n(3), Players.You, tapped = true, attacking = AttackTarget.Chosen))),
            otherwise = listOf(create(spirit, n(3))))),
    )

    @Test
    fun captainsTechnique() = card(
        name = "Captain's Technique",
        cost = "{3}{W}",
        type = "Sorcery",
        text = """
            Sneak {1}{W} (You may cast this spell for {1}{W} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Return one or two target creature cards each with mana value 3 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        sneak("{1}{W}"),
        spell(move(target("cards"), Zone.Battlefield), targets = listOf(slot("cards", inYourGraveyard(creature, manaValue(Comparison.AtMost, 3)), min = 1, max = 2))),
    )

    @Test
    fun inventorsTechnique() = card(
        name = "Inventor's Technique",
        cost = "{2}{U}",
        type = "Sorcery",
        text = """
            Sneak {U} (You may cast this spell for {U} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Draw two cards.
        """,
    ) parsesTo abilities(
        sneak("{U}"),
        spell(draw(2)),
    )

    // CR 107.1a: rounded up
    @Test
    fun vixensTechnique() = card(
        name = "Vixen's Technique",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = """
            Sneak {1}{U} (You may cast this spell for {1}{U} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Target opponent mills half their library, rounded up.
        """,
    ) parsesTo abilities(
        sneak("{1}{U}"),
        spell(doing(Effect.Mill(Players.Target("opponent"), Value.Half(count(and(inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))),
            roundUp = true))), targets = listOf(targetOpponent)),
    )

    @Test
    fun warlordsTechnique() = card(
        name = "Warlord's Technique",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Sneak {B} (You may cast this spell for {B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Destroy target creature or enchantment. If an enchantment was destroyed this way, you lose 2 life.
        """,
    ) parsesTo abilities(
        sneak("{B}"),
        spell(doing(Effect.Destroy(target("permanent"), postBind = "destroyed")),
            Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("destroyed"), enchantment)), Comparison.AtLeast, n(1)), then = listOf(loseLife(2))),
            targets = listOf(slot("permanent", or(creature, enchantment)))),
    )

    @Test
    fun senseisTechnique() = card(
        name = "Sensei's Technique",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Sneak {1}{B} (You may cast this spell for {1}{B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Search your library for a card, put that card into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        sneak("{1}{B}"),
        spell(search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle),
    )

    @Test
    fun bigSistersTechnique() = card(
        name = "Big Sister's Technique",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Sneak {R} (You may cast this spell for {R} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Big Sister's Technique deals 2 damage to each creature.
        """,
    ) parsesTo abilities(
        sneak("{R}"),
        spell(damage(2, toObjects(Objects.Each(creature)))),
    )

    // CR 101.4: each player chooses in turn order, then the choices happen at once
    @Test
    fun hotheadsTechnique() = card(
        name = "Hothead's Technique",
        cost = "{4}{R}{R}",
        type = "Instant",
        text = """
            Sneak {2}{R} (You may cast this spell for {2}{R} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Each player may discard their hand and draw seven cards.
        """,
    ) parsesTo abilities(
        sneak("{2}{R}"),
        spell(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.May(listOf(
                doing(Effect.Discard(Players.Bound("player"), n(1), ObjectFilter.Any,
                    objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))))))),
                draw(7, Players.Bound("player"))), player = Players.Bound("player"))))),
    )

    // the chosen cards' total mana value is at most 6 (CR 202.3)
    @Test
    fun pranksterTechnique() = card(
        name = "Prankster's Technique",
        cost = "{4}{G}",
        type = "Sorcery",
        text = """
            Sneak {3}{G} (You may cast this spell for {3}{G} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Look at the top eight cards of your library. Put up to two creature cards with total mana value 6 or less from among them onto the battlefield and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        sneak("{3}{G}"),
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(8), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(creature, n(0), n(2), from = Objects.Bound("seen"), totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, n(6))),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun youngGuardsTechnique() = card(
        name = "Young Guard's Technique",
        cost = "{3}{G}",
        type = "Sorcery",
        text = """
            Sneak {2}{G} (You may cast this spell for {2}{G} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        sneak("{2}{G}"),
        spell(search(basicLand(), max = 2), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun heiresssTechnique() = card(
        name = "Heiress's Technique",
        cost = "{1}{W}{B}",
        type = "Sorcery",
        text = """
            Sneak {W}{B} (You may cast this spell for {W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
            Choose one or both —
            • Target creature gets +3/+3 until end of turn.
            • Target creature gets -3/-3 until end of turn.
        """,
    ) parsesTo abilities(
        sneak("{W}{B}"),
        arcanum.api.Ability.Spell(arcanum.api.Instructions(modes = modes(
            mode(pump(target("creature"), 3, 3), targets = listOf(slot("creature", creature))),
            mode(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))),
            min = 1, max = 2))),
    )

    // CR 400.11a: cards outside the game; CR 701.23: a search for cards with a stated quality may find fewer
    @Test
    fun shellsEternal() = card(
        name = "Shells Eternal",
        cost = "{3}{W}",
        type = "Instant",
        text = "Search your library and/or outside the game for exactly four legendary creature cards you own with different names, then reveal those cards. An opponent chooses two of them. Put the chosen cards into your hand and shuffle the rest into your library.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Search(Players.You, and(legendary, creature, ObjectFilter.OwnedBy(PlayerFilter.You)), max = n(4), reveal = true,
                zones = listOf(Zone.Library, Zone.Outside), differentNames = true, bind = "found", searcher = Players.You)),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("found")), bind = "chosen", chooser = Players.Each(PlayerFilter.Opponent)),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Library),
            shuffle),
    )

    // CR 101.4: each player decides in turn order; then each who did draws
    @Test
    fun shellsThroughTime() = card(
        name = "Shells Through Time",
        cost = "{5}{U}{U}",
        type = "Sorcery",
        text = """
            Return all creatures to their owners' hands. Each player may shuffle their hand and graveyard into their library, then each player who does draws seven cards.
            Exile Shells Through Time.
        """,
    ) parsesTo abilities(
        spell(
            move(Objects.Each(creature), Zone.Hand),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Pay(steps = listOf(
                    move(Objects.Each(and(ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))), or(inZone(Zone.Hand), inZone(Zone.Graveyard)))), Zone.Library),
                    doing(Effect.Shuffle(Players.Bound("player")))), payer = Players.Bound("player"), bind = "shuffled"),
                Step.If(Condition.Did("shuffled"), then = listOf(draw(7, Players.Bound("player")))))),
            move(Objects.Self, Zone.Exile)),
    )
}
