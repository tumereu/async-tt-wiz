package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * INR chunk 03: Flashback (CR 702.34a: cast from the graveyard for the flashback cost, then exile it, CR 118.9). The flashback cost is a
 * keyword with its own mana cost; the spell part reads as it does without flashback.
 */
class InrChunk03FlashbackTest {
    private val creatureYouControlSlot = slot("creature", and(creature, you))
    private val starValue = Value.Sum(listOf(count(inYourGraveyard(or(instant, sorcery))),
        count(and(cardFilter, kw(Keyword.Flashback.Kind), inZone(Zone.Exile), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)))))
    private val flashbackLine = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    // CR 702.34a, 509.1c (must be blocked if able), 611.2c (affects only this turn), 702.2 (deathtouch)
    @Test
    fun deadlyLure() = card(
        name = "Deadly Lure",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target creature gains deathtouch until end of turn and must be blocked this turn if able.
            Flashback {G} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Deathtouch)), Mechanic.MustBeBlocked),
            arcanum.api.Affected.Objects(target("creature")), arcanum.api.Duration.EndOfTurn)), targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{G}")),
    )

    // CR 107.3 (X from "where X is"), 601.2h (mana spent), 702.34a: flashback is cast with its own cost, so X is what that cost paid
    @Test
    fun memoryFlood() = card(
        name = "Memory Flood",
        cost = "{2}{U}{U}",
        type = "Instant",
        text = """
            Look at the top X cards of your library, where X is the amount of mana spent to cast this spell. Put two of them into your hand and the rest on the bottom of your library in a random order.
            Flashback {5}{U}{U} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.ManaSpent(of = Objects.Self), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(2), n(2), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        Keyword.Flashback(manaCost("{5}{U}{U}")),
    )

    // CR 111.4 (token's characteristic-defining ability), 604.3, 702.34a (a card with flashback in exile is one you cast with flashback), 702.19 trample
    @Test
    fun seizeTheGale() = card(
        name = "Seize the Gale",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Create a red Elemental creature token with trample and "This token's power and toughness are each equal to the number of instant and sorcery cards in your graveyard plus the number of cards with flashback you own in exile."
            Flashback {6}{R} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(create(TokenSpec.Defined(FaceDefinition("Elemental Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Elemental")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(),
            abilities = listOf(Keyword.Trample, static(Mechanic.SetPowerToughness(starValue, starValue), characteristicDefining = true)))))),
        Keyword.Flashback(manaCost("{6}{R}")),
    )

    // CR 122.1, 702.34a, 613.1f (ability-adding effects until end of turn)
    @Test
    fun angelflameIgnition() = card(
        name = "Angelflame Ignition",
        cost = "{1}{R}{W}",
        type = "Sorcery",
        text = """
            Put two +1/+1 counters on target creature. It gains vigilance, trample, lifelink, indestructible, and haste until end of turn.
            Flashback {2}{R}{W} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(counters(target("creature"), 2),
            gains(target("creature"), Keyword.Vigilance, Keyword.Trample, Keyword.Lifelink, Keyword.Indestructible, Keyword.Haste),
            targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{2}{R}{W}")),
    )

    // CR 701.18 (return), 122.1 ("up to one" target: zero allowed, CR 115.2), 702.34a
    @Test
    fun creepFromTheCellar() = card(
        name = "Creep from the Cellar",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to your hand. Put a +1/+1 counter on up to one target Zombie you control.
            Flashback {3}{B} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Hand), counters(target("Zombie")),
            targets = listOf(slot("card", inYourGraveyard(creature)), slot("Zombie", and(sub("Zombie"), you), min = 0))),
        Keyword.Flashback(manaCost("{3}{B}")),
    )

    // CR 111.1, 702.34a
    @Test
    fun joinTheRevel() = card(
        name = "Join the Revel",
        cost = "{G}{W}",
        type = "Sorcery",
        text = """
            Create two 1/1 white Human creature tokens.
            Flashback {3}{G}{W} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Human", listOf(Color.White), 1, 1), n(2))),
        Keyword.Flashback(manaCost("{3}{G}{W}")),
    )

    // CR 611.2c (affects only creatures you control when it resolves), 702.34a
    @Test
    fun rallyTheVillagers() = card(
        name = "Rally the Villagers",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Creatures you control get +2/+0 until end of turn.
            Flashback {2}{R} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(pump(Objects.Each(and(creature, you)), 2, 0)),
        Keyword.Flashback(manaCost("{2}{R}")),
    )

    // CR 701.8 (exile), 201.2 (same name), 702.34a
    @Test
    fun severTheBloodline2() = card(
        name = "Cut the Bloodline",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Exile target creature and all other creatures with the same name as that creature.
            Flashback {5}{B}{B} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(move(Objects.Union(listOf(target("creature"),
            Objects.Each(and(not(ObjectFilter.Is(target("creature"))), creature, ObjectFilter.SameNameAs(target("creature")))))), Zone.Exile),
            targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{5}{B}{B}")),
    )

    // CR 111.1, 702.34a: one token per creature card in your graveyard when it resolves
    @Test
    fun spiderSpinning() = card(
        name = "Spider Spinning",
        cost = "{4}{G}",
        type = "Sorcery",
        text = """
            Create a 1/2 green Spider creature token with reach for each creature card in your graveyard.
            Flashback {6}{B} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Spider", listOf(Color.Green), 1, 2, listOf(Keyword.Reach)), count(inYourGraveyard(creature)))),
        Keyword.Flashback(manaCost("{6}{B}")),
    )

    // CR 122.1, 115.2 (each of up to two targets), 702.34a
    @Test
    fun travelProvisions() = card(
        name = "Travel Provisions",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on each of up to two target creatures.
            Flashback {1}{W} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        Keyword.Flashback(manaCost("{1}{W}")),
    )

    // CR 613.4c, 613.1f, 702.19 (trample), 702.34a
    @Test
    fun wildCraving() = card(
        name = "Wild Craving",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Target creature gets +3/+1 and gains trample until end of turn.
            Flashback {3}{R} $flashbackLine
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 1, Mechanic.GainAbilities(listOf(Keyword.Trample))), targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{3}{R}")),
    )
}
