package arcanum.parser.sets.fra

import arcanum.api.Comparison
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 01: preparation cards (CR 722) in white and blue. Each card has two faces: the creature, and the
 * prepare spell in its inset frame, which is never cast from the card (722.3) but by the copy a prepared permanent's
 * controller gets in exile (722.3c). "This creature enters prepared" is a replacement (CR 614.1c, 722.3a); "if this
 * creature isn't prepared, it becomes prepared" checks the designation as it triggers and resolves (CR 603.4). The
 * spells: +1/+1 counters and life, bounce and surveil, Cadet tokens, "each player may discard their hand and draw seven".
 */
class FraChunk01PrepareWhiteBlueTest {
    // CR 722.3a: it has the designation as it enters
    @Test
    fun bloomwingSeraph() = cardFaces(
        Layout.Prepare,
        card(
            name = "Bloomwing Seraph",
            cost = "{3}{W}",
            type = "Creature — Angel Cleric",
            pt = "2/4",
            text = """
                Flying, vigilance
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(Keyword.Flying, Keyword.Vigilance, entersPrepared),
        card(
            name = "Graft Mending",
            cost = "{G/W}",
            type = "Sorcery",
            text = "Put a +1/+1 counter on target creature. You gain 1 life.",
        ) to abilities(spell(counters(target("creature")), gainLife(1), targets = listOf(slot("creature", creature)))),
    )

    @Test
    fun augurOfTriumphs() = cardFaces(
        Layout.Prepare,
        card(
            name = "Augur of Triumphs",
            cost = "{U}",
            type = "Creature — Dwarf Wizard",
            pt = "1/1",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                Whenever you scry or surveil, this creature gets +1/+1 until end of turn.
            """,
        ) to abilities(entersPrepared, triggered(youScryOrSurveil, pump(Objects.Self, 1, 1))),
        card(
            name = "Rewind the Annals",
            cost = "{1}{U}",
            type = "Sorcery",
            text = "Return target creature an opponent controls with mana value 3 or less to its owner's hand. Surveil 1.",
        ) to abilities(spell(move(target("creature"), Zone.Hand), surveil(1), targets = listOf(slot("creature", and(creature, controlledBy(PlayerFilter.Opponent), manaValue(Comparison.AtMost, 3)))))),
    )

    @Test
    fun termSeer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Term Seer",
            cost = "{3}{U}",
            type = "Creature — Human Wizard",
            pt = "3/4",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                When this creature enters, surveil 1.
            """,
        ) to abilities(entersPrepared, triggered(entersSelf, surveil(1))),
        card(
            name = "Faculty Review",
            cost = "{2}{W/U}",
            type = "Sorcery",
            text = "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.",
        ) to abilities(spell(createCadets(1), surveil(1))),
    )

    // CR 101.4: each player decides in turn order, then all at once
    @Test
    fun vectorPursuer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Vector Pursuer",
            cost = "{2}{U}",
            type = "Creature — Human Wizard",
            pt = "2/3",
            text = """
                Flying, prowess
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(Keyword.Flying, Keyword.Prowess, entersPrepared),
        card(
            name = "Curve of Chance",
            cost = "{2}{U}",
            type = "Sorcery",
            text = "Each player may discard their hand and draw seven cards.",
        ) to abilities(spell(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(Step.May(listOf(
                Step.Do(Effect.Discard(Players.Bound("player"), n(1), ObjectFilter.Any,
                    objects = Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))))))),
                draw(7, Players.Bound("player"))), player = Players.Bound("player"))), together = true))),
    )

    @Test
    fun fatekeepAnnalist() = cardFaces(
        Layout.Prepare,
        card(
            name = "Fatekeep Annalist",
            cost = "{1}{W/U}",
            type = "Creature — Bird Wizard",
            pt = "1/2",
            text = """
                Flying
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(Keyword.Flying, entersPrepared),
        card(
            name = "Faculty Review",
            cost = "{2}{W/U}",
            type = "Sorcery",
            text = "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.",
        ) to abilities(spell(createCadets(1), surveil(1))),
    )

    // CR 603.2h
    @Test
    fun carefulForeteller() = cardFaces(
        Layout.Prepare,
        card(
            name = "Careful Foreteller",
            cost = "{1}{W/U}{W/U}",
            type = "Creature — Dwarf Wizard",
            pt = "1/4",
            text = """
                This creature enters prepared.
                Whenever you scry or surveil, creatures you control get +1/+0 until end of turn. This ability triggers only once each turn.
            """,
        ) to abilities(entersPrepared, triggered(youScryOrSurveil, pump(Objects.Each(creatureYouControl), 1, 0), perTurn = 1)),
        card(
            name = "Faculty Review",
            cost = "{2}{W/U}",
            type = "Sorcery",
            text = "Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1.",
        ) to abilities(spell(createCadets(1), surveil(1))),
    )

    @Test
    fun urgentSapmender() = cardFaces(
        Layout.Prepare,
        card(
            name = "Urgent Sapmender",
            cost = "{G/W}",
            type = "Creature — Dryad Cleric",
            pt = "1/1",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(entersPrepared),
        card(
            name = "Graft Mending",
            cost = "{G/W}",
            type = "Sorcery",
            text = "Put a +1/+1 counter on target creature. You gain 1 life.",
        ) to abilities(spell(counters(target("creature")), gainLife(1), targets = listOf(slot("creature", creature)))),
    )

    @Test
    fun bloomvigorOutrider() = cardFaces(
        Layout.Prepare,
        card(
            name = "Bloomvigor Outrider",
            cost = "{1}{G/W}",
            type = "Creature — Troll Druid",
            pt = "2/2",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                Each creature you control with a +1/+1 counter on it has vigilance.
            """,
        ) to abilities(entersPrepared, static(Mechanic.GainAbilities(listOf(Keyword.Vigilance)), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, 1))))),
        card(
            name = "Graft Mending",
            cost = "{G/W}",
            type = "Sorcery",
            text = "Put a +1/+1 counter on target creature. You gain 1 life.",
        ) to abilities(spell(counters(target("creature")), gainLife(1), targets = listOf(slot("creature", creature)))),
    )

    // CR 603.4: checked as it triggers and as it resolves
    @Test
    fun riddleShaper() = cardFaces(
        Layout.Prepare,
        card(
            name = "Riddle Shaper",
            cost = "{1}{U/B}",
            type = "Creature — Octopus Wizard",
            pt = "1/3",
            text = """
                At the beginning of your upkeep, if this creature isn't prepared, it becomes prepared.
                {2}: Put target card from your graveyard on the bottom of your library.
            """,
        ) to abilities(
            preparesEachUpkeep,
            activated(cost(mana("{2}")), move(target("card"), Zone.Library, position = -1),
                targets = listOf(slot("card", and(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
        ),
        card(
            name = "Drop the Terms",
            cost = "{U/B}",
            type = "Sorcery",
            text = "Mill three cards. (Put the top three cards of your library into your graveyard.)",
        ) to abilities(spell(mill(3))),
    )

    @Test
    fun hollowProjector() = cardFaces(
        Layout.Prepare,
        card(
            name = "Hollow Projector",
            cost = "{1}{B}",
            type = "Creature — Aetherborn Warlock",
            pt = "2/2",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                Threshold — This creature gets +1/+1 as long as there are seven or more cards in your graveyard.
            """,
        ) to abilities(entersPrepared, static(Mechanic.ModifyPowerToughness(n(1), n(1)), condition = threshold, abilityWord = "Threshold")),
        card(
            name = "Drop the Terms",
            cost = "{U/B}",
            type = "Sorcery",
            text = "Mill three cards. (Put the top three cards of your library into your graveyard.)",
        ) to abilities(spell(mill(3))),
    )

    @Test
    fun theoremMetashade() = cardFaces(
        Layout.Prepare,
        card(
            name = "Theorem Metashade",
            cost = "{2}{U/B}",
            type = "Creature — Shade Wizard",
            pt = "2/3",
            text = """
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                Threshold — This creature gets +1/+0 and has flying as long as there are seven or more cards in your graveyard.
            """,
        ) to abilities(entersPrepared, static(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Flying)),
            condition = threshold, abilityWord = "Threshold")),
        card(
            name = "Drop the Terms",
            cost = "{U/B}",
            type = "Sorcery",
            text = "Mill three cards. (Put the top three cards of your library into your graveyard.)",
        ) to abilities(spell(mill(3))),
    )
}
