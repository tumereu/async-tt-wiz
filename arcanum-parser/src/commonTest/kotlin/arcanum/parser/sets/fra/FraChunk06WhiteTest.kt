package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Value
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
 * Reality Fracture chunk 06: white cards and the colourless Eldrazi. A cast trigger and an ability that works in the hand
 * (CR 603.6, 113.6b) granting a mana ability until the exiled card is cast and letting it be cast from exile; surveil keeping
 * the card put into the graveyard when it qualifies (CR 701.25); modes (CR 700.2); blinking (CR 400.7); returning with an
 * additional counter and "with X counters" (CR 122.6); X tokens counted by life gained this turn; moving a dead creature's
 * counters; a chosen creature type spared by a wrath (CR 205.3m); exile until it leaves (CR 610.3); "if it wasn't attacking"
 * as it last existed (CR 608.2h).
 */
class FraChunk06WhiteTest {
    // CR 113.6b, 611.2b
    @Test
    fun ulamorthTheExactingRuin() = card(
        name = "Ulamorth, the Exacting Ruin",
        cost = "{10}",
        type = "Legendary Creature — Eldrazi",
        pt = "12/12",
        text = """
            When you cast this spell, untap all lands you control.
            Flying, trample
            Ward—Sacrifice three permanents.
            {3}, Exile this card from your hand: Target land gains "{T}: Add {C}{C}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
        """,
    ) parsesTo abilities(
            triggered(youCast(ObjectFilter.Is(Objects.Self)), Step.Do(Effect.Untap(Objects.Each(and(land, you))))),
            Keyword.Flying,
            Keyword.Trample,
            Keyword.Ward(cost(CostPart.Sacrifice(CostObjects.Choose(permanentFilter, n(3))))),
            activated(cost(mana("{3}"), CostPart.Exile(CostObjects.Self, from = Zone.Hand, postBind = "exiled")),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(activated(cost(tap),
                    Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You)))))),
                    Affected.Objects(target("land")), Duration.UntilCast(Objects.Bound("exiled")))),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")),
                    Duration.Indefinitely)),
                targets = listOf(slot("land", land))),
        )

    // CR 701.25a, 603.4
    @Test
    fun illuminedConfessor() = card(
        name = "Illumined Confessor",
        cost = "{1}{W}",
        type = "Creature — Kor Cleric",
        pt = "2/1",
        text = """
            Lifelink
            At the beginning of your end step, if you gained life this turn, surveil 1. If you put a card with mana value less than or equal to the amount of life you gained this turn into your graveyard this way, put that card into your hand.
        """,
    ) parsesTo abilities(
            Keyword.Lifelink,
            triggered(stepBegins(TurnStep.End, PlayerFilter.You),
                Step.Do(Effect.Surveil(Players.You, n(1), bind = "surveilled")),
                Step.If(Condition.ObjectMatches(Objects.Bound("surveilled"), ObjectFilter.ManaValue(Comparison.AtMost, lifeGainedThisTurn)),
                    then = listOf(move(Objects.Bound("surveilled"), Zone.Hand))),
                interveningIf = thisTurn(EventPattern.GainLife(PlayerFilter.You))),
        )

    // CR 700.2
    @Test
    fun destinyforgeHopeful() = card(
        name = "Destinyforge Hopeful",
        cost = "{4}{W}",
        type = "Creature — Rhino Cleric",
        pt = "3/4",
        text = """
            When this creature enters, choose one —
            • Return target legendary card from your graveyard to your hand.
            • Put a +1/+1 counter on target creature. It gains vigilance and indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """,
    ) parsesTo abilities(Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("card", and(ObjectFilter.Super(Supertype.Legendary), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))), steps = listOf(move(target("card"), Zone.Hand)))),
            Mode(Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(counters(target("creature")), gains(target("creature"), Keyword.Vigilance, Keyword.Indestructible)))),
        ), min = 1, max = 1, chooser = Players.You))))

    // CR 400.7: the card returns as a new object
    @Test
    fun flickerfurHound() = card(
        name = "Flickerfur Hound",
        cost = "{3}{W}",
        type = "Creature — Dog",
        pt = "2/2",
        text = "Whenever you cast a creature spell, exile up to one other target creature you control, then return that card to the battlefield under its owner's control.",
    ) parsesTo abilities(triggered(youCast(creature),
            Step.Do(Effect.Move(target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))))),
            targets = listOf(slot("creature", and(anotherObject, creature, you), min = 0))))

    // CR 122.6
    @Test
    fun bountifulRevivification() = card(
        name = "Bountiful Revivification",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Return target creature card with mana value 3 or less from your graveyard to the battlefield with an additional +1/+1 counter on it.
            Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
            spell(Step.Do(Effect.Move(target("card"), Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, n(1)))))),
                targets = listOf(slot("card", and(creature, manaValue(Comparison.AtMost, 3), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            Keyword.Flashback(cost(mana("{4}{W}"))),
        )

    @Test
    fun sproutTheConscripts() = card(
        name = "Sprout the Conscripts",
        cost = "{2}{W}",
        type = "Instant",
        text = "Create X 2/2 colorless Wizard Soldier creature tokens named Cadet, where X is the amount of life you gained this turn.",
    ) parsesTo abilities(spell(Step.Do(Effect.CreateToken(cadet, lifeGainedThisTurn, Players.You, bind = "created"))))

    // CR 122.6, 608.2h: the counters it had as it last existed
    @Test
    fun spliceSurgeon() = card(
        name = "Splice Surgeon",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "2/2",
        text = """
            This creature enters with a +1/+1 counter on it.
            When this creature dies, put its counters on up to one target creature you control.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
            triggered(diesSelf, Step.Do(Effect.MoveCounters(Objects.Self, target("creature"), null, Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single))),
                targets = listOf(slot("creature", creatureYouControl, min = 0))),
        )

    // CR 107.3m, 118.12
    @Test
    fun pathfinderHydra() = card(
        name = "Pathfinder Hydra",
        cost = "{X}{W}",
        type = "Creature — Hydra Horror",
        pt = "1/0",
        text = """
            This creature enters with X +1/+1 counters on it.
            At the beginning of combat on your turn, you may remove a +1/+1 counter from this creature. If you do, put a +1/+1 counter on each other creature you control.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(Objects.Self))))),
            triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You),
                Step.Pay(cost(CostPart.RemoveCounters(p1p1, n(1), CostObjects.Self)), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(counters(Objects.Each(and(anotherObject, creature, you)))))),
        )

    // CR 205.3m
    @Test
    fun kinshipVerdict() = card(
        name = "Kinship Verdict",
        cost = "{5}{W}{W}",
        type = "Sorcery",
        text = "Choose a creature type. Destroy all creatures that aren't of the chosen type.",
    ) parsesTo abilities(spell(
            Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You),
            Step.Do(Effect.Destroy(Objects.Each(and(creature, not(ObjectFilter.Sub(SubtypeRef.Chosen("type"))))))),
        ))

    // CR 610.3
    @Test
    fun recollectionSnare() = card(
        name = "Recollection Snare",
        cost = "{2}{W}",
        type = "Enchantment",
        text = "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.",
    ) parsesTo abilities(triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, controlledBy(PlayerFilter.Opponent))))))

    // CR 115.1d
    @Test
    fun foresightedReadiness() = card(
        name = "Foresighted Readiness",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on each of one or two target creatures.
            Flashback {3}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
            spell(counters(target("creature")), targets = listOf(slot("creature", creature, min = 1, max = 2))),
            Keyword.Flashback(cost(mana("{3}{W}"))),
        )

    // CR 608.2h
    @Test
    fun foretoldDemise() = card(
        name = "Foretold Demise",
        cost = "{1}{W}",
        type = "Instant",
        text = "Destroy target creature. If it wasn't attacking, its controller draws a card.",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Destroy(target("creature"))),
            Step.If(Condition.Not(Condition.ObjectMatches(target("creature"), ObjectFilter.HasStatus(Status.Attacking))),
                then = listOf(draw(1, Players.ControllerOf(target("creature"))))),
            targets = listOf(slot("creature", creature)),
        ))

    @Test
    fun denyTheProphecy() = card(
        name = "Deny the Prophecy",
        cost = "{1}{W}",
        type = "Sorcery",
        text = "Exile target creature or planeswalker that's green or blue. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(spell(move(target("permanent"), Zone.Exile), surveil(1),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, or(ObjectFilter.OfColor(Color.Green), ObjectFilter.OfColor(Color.Blue)))))))

    @Test
    fun homecomingToTheBrightReaches() = card(
        name = "Homecoming to the Bright Reaches",
        cost = "{7}{W}{W}",
        type = "Sorcery",
        text = "Return all nonland permanent cards from your graveyard to the battlefield.",
    ) parsesTo abilities(spell(move(Objects.Each(and(nonland, or(artifact, ObjectFilter.Type(CardType.Battle), creature, ObjectFilter.Type(CardType.Enchantment), land,
            planeswalker), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), Zone.Battlefield)))

    @Test
    fun shardwingSteed() = card(
        name = "Shardwing Steed",
        cost = "{2}{W}",
        type = "Creature — Pegasus",
        pt = "2/3",
        text = """
            Flying
            {4}{W}: Creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(mana("{4}{W}")), pump(Objects.Each(creatureYouControl), 1, 1)))

    @Test
    fun exactingIncision() = card(
        name = "Exacting Incision",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Destroy target creature with toughness 4 or greater. You gain 1 life.
            • You draw a card and gain 2 life.
        """,
    ) parsesTo abilities(Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4))))),
                steps = listOf(Step.Do(Effect.Destroy(target("creature"))), gainLife(1)))),
            Mode(Instructions(steps = listOf(draw(1), gainLife(2)))),
        ), min = 1, max = 1, chooser = Players.You))))

    @Test
    fun undauntedHortiseer() = card(
        name = "Undaunted Hortiseer",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "2/1",
        text = """
            Ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            Whenever you gain life, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(Keyword.Ward(cost(mana("{1}"))), triggered(youGainLife, counters(Objects.Self)))

    @Test
    fun thyDestinyHalts() = card(
        name = "Thy Destiny Halts",
        cost = "{2}{W}",
        type = "Instant",
        text = "Destroy target creature or planeswalker with mana value 3 or greater. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(spell(Step.Do(Effect.Destroy(target("permanent"))), surveil(1),
            targets = listOf(slot("permanent", and(creatureOrPlaneswalker, manaValue(Comparison.AtLeast, 3))))))
}
