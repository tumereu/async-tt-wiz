package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 13: artifacts and lands. Exiling a card from a graveyard when attacking; a modal enters trigger; a
 * legendary artifact exiling itself to destroy all creatures; an upkeep surveil with a threshold payoff; shuffling a permanent
 * into its owner's library (CR 701.24); an Equipment granting flying and a triggered ability; lands entering tapped unless you
 * control a planeswalker or two other lands (CR 614.1c), becoming a copy of a creature with the "legend rule" off for the turn
 * (CR 707.2, 704.5j), triggering when a Forest enters with five other Forests, and choosing a colour as it enters.
 */
class FraChunk13ArtifactsAndLandsTest {
    @Test
    fun hindsightSentinel() = card(
        name = "Hindsight Sentinel",
        cost = "{2}",
        type = "Artifact Creature — Gargoyle",
        pt = "2/2",
        text = """
            {2}: This creature gains flying until end of turn.
            Whenever this creature attacks, exile up to one target card from a graveyard.
        """,
    ) parsesTo abilities(
            activated(cost(mana("{2}")), gains(Objects.Self, Keyword.Flying)),
            triggered(attacksSelf, Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")), targets = listOf(slot("card", and(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard)), min = 0))),
        )

    @Test
    fun libraryAdjudicator() = card(
        name = "Library Adjudicator",
        cost = "{6}",
        type = "Artifact Creature — Sphinx",
        pt = "4/4",
        text = """
            Flying
            When this creature enters, choose one —
            • Destroy target noncreature, nonland permanent.
            • You gain 4 life.
        """,
    ) parsesTo abilities(Keyword.Flying, Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("permanent", and(not(creature), nonland, permanentFilter))), steps = listOf(Step.Do(Effect.Destroy(target("permanent")))))),
            Mode(Instructions(steps = listOf(gainLife(4)))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun theResonancePivot() = card(
        name = "The Resonance Pivot",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            When The Resonance Pivot enters, draw a card, then discard a card.
            {5}, {T}, Exile The Resonance Pivot: Destroy all creatures. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, draw(1), Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any))),
            activated(cost(mana("{5}"), tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), Step.Do(Effect.Destroy(Objects.Each(creature))), rules = asSorcery),
        )

    // CR 701.25a
    @Test
    fun gazeOfTheTheorist() = card(
        name = "Gaze of the Theorist",
        cost = "{1}",
        type = "Artifact",
        text = "At the beginning of your upkeep, surveil 1. Then if there are seven or more cards in your graveyard, sacrifice this artifact, it deals 2 damage to each opponent, and you gain 2 life. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), surveil(1),
            Step.If(threshold, then = listOf(Step.Do(Effect.Sacrifice(Objects.Self)), damage(2, eachOpponent), gainLife(2)))))

    // CR 701.24a
    @Test
    fun breathingArchive() = card(
        name = "Breathing Archive",
        cost = "{2}",
        type = "Artifact Creature — Book Illusion",
        pt = "0/4",
        text = "{6}, Sacrifice this creature: Choose target creature or planeswalker an opponent controls. Its owner shuffles it into their library.",
    ) parsesTo abilities(activated(cost(mana("{6}"), sacrificeSelf),
            move(target("permanent"), Zone.Library),
            Step.Do(Effect.Shuffle(Players.OwnerOf(target("permanent")))),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, controlledBy(PlayerFilter.Opponent))))))

    // CR 613.1f
    @Test
    fun healerSGlidewing() = card(
        name = "Healer's Glidewing",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0 and has flying and "Whenever this creature attacks, you gain 1 life."
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
            static(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Flying, triggered(attacksSelf, gainLife(1)))),
                affects = Objects.AttachedTo(Objects.Self)),
            Keyword.Equip(cost(mana("{2}"))),
        )

    @Test
    fun whisperingTome() = card(
        name = "Whispering Tome",
        cost = "{3}",
        type = "Artifact — Book",
        text = """
            {T}: Add one mana of any color.
            {2}, {T}, Discard a card: Draw a card.
        """,
    ) parsesTo abilities(
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
            activated(cost(mana("{2}"), tap, discardACard), draw(1)),
        )

    // CR 614.1c: checked as it enters
    @Test
    fun devotedQuadrangle() = card(
        name = "Devoted Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.White)))), Players.You))),
        )

    @Test
    fun fatekeepWing() = card(
        name = "Fatekeep Wing",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)))), Players.You))),
        )

    @Test
    fun fearsomeQuadrangle() = card(
        name = "Fearsome Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Green)))), Players.You))),
        )

    @Test
    fun inventiveQuadrangle() = card(
        name = "Inventive Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Red)))), Players.You))),
        )

    @Test
    fun tinkerforgeWing() = card(
        name = "Tinkerforge Wing",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))), Players.You))),
        )

    @Test
    fun scrupulousQuadrangle() = card(
        name = "Scrupulous Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {W} or {B}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Black)))), Players.You))),
        )

    @Test
    fun barbquillWing() = card(
        name = "Barbquill Wing",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)))), Players.You))),
        )

    @Test
    fun hypothesisWing() = card(
        name = "Hypothesis Wing",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black)))), Players.You))),
        )

    @Test
    fun shiftingQuadrangle() = card(
        name = "Shifting Quadrangle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {G} or {U}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.Blue)))), Players.You))),
        )

    @Test
    fun vigorleafWing() = card(
        name = "Vigorleaf Wing",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a planeswalker.
            {T}: Add {G} or {W}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(planeswalker, you), 1))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White)))), Players.You))),
        )

    @Test
    fun abandonedSeashore() = card(
        name = "Abandoned Seashore",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(anotherObject, land, you), 2))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)))), Players.You))),
        )

    @Test
    fun spectralCrest() = card(
        name = "Spectral Crest",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(anotherObject, land, you), 2))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)))), Players.You))),
        )

    @Test
    fun wildAcreage() = card(
        name = "Wild Acreage",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {G} or {W}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(anotherObject, land, you), 2))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White)))), Players.You))),
        )

    @Test
    fun boulderslideGlen() = card(
        name = "Boulderslide Glen",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(anotherObject, land, you), 2))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))), Players.You))),
        )

    @Test
    fun wreckwaterBog() = card(
        name = "Wreckwater Bog",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
                condition = Condition.Not(Condition.Exists(and(anotherObject, land, you), 2))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black)))), Players.You))),
        )

    // CR 707.2, 704.5j
    @Test
    fun hallOfReverberations() = card(
        name = "Hall of Reverberations",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {5}: This land becomes a copy of target creature you control until end of turn. The "legend rule" doesn't apply to permanents you control this turn.
        """,
    ) parsesTo abilities(
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
            activated(cost(mana("{5}")), untilEndOfTurn(Objects.Self, Mechanic.CopyOf(target("creature"))),
                untilEndOfTurn(Objects.Each(and(permanentFilter, you)), Mechanic.LegendRuleDoesntApply), targets = listOf(slot("creature", creatureYouControl))),
        )

    // CR 603.4
    @Test
    fun churningBoughs() = card(
        name = "Churning Boughs",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            Whenever a Forest you control enters, if you control at least five other Forests, target creature you control gets +3/+3 until end of turn.
            {T}: Add {G}.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
            triggered(on(EventPattern.ZoneChange(and(sub("Forest"), you), to = Zone.Battlefield)), pump(target("creature"), 3, 3),
                targets = listOf(slot("creature", creatureYouControl)),
                interveningIf = Condition.Exists(and(not(ObjectFilter.Is(Objects.EventObject(EventRole.After))), sub("Forest"), you), 5)),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
        )

    // CR 614.12
    @Test
    fun chamberOfSanctuary() = card(
        name = "Chamber of Sanctuary",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
            {5}, {T}, Sacrifice this land: Put two +1/+1 counters on target creature. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
                Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.You))),
            activated(cost(mana("{5}"), tap, sacrificeSelf), counters(target("creature"), 2), targets = listOf(slot("creature", creature)), rules = asSorcery),
        )
}
