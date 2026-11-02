package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.ActivationRules
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.rtr.Rtr.activated
import arcanum.parser.sets.rtr.Rtr.all
import arcanum.parser.sets.rtr.Rtr.another
import arcanum.parser.sets.rtr.Rtr.cost
import arcanum.parser.sets.rtr.Rtr.counters
import arcanum.parser.sets.rtr.Rtr.create
import arcanum.parser.sets.rtr.Rtr.creature
import arcanum.parser.sets.rtr.Rtr.creatureToken
import arcanum.parser.sets.rtr.Rtr.enchant
import arcanum.parser.sets.rtr.Rtr.enchanted
import arcanum.parser.sets.rtr.Rtr.gainLife
import arcanum.parser.sets.rtr.Rtr.gains
import arcanum.parser.sets.rtr.Rtr.land
import arcanum.parser.sets.rtr.Rtr.manaPart
import arcanum.parser.sets.rtr.Rtr.n
import arcanum.parser.sets.rtr.Rtr.pump
import arcanum.parser.sets.rtr.Rtr.slot
import arcanum.parser.sets.rtr.Rtr.spell
import arcanum.parser.sets.rtr.Rtr.static
import arcanum.parser.sets.rtr.Rtr.sub
import arcanum.parser.sets.rtr.Rtr.target
import arcanum.parser.sets.rtr.Rtr.thisAttacks
import arcanum.parser.sets.rtr.Rtr.thisDies
import arcanum.parser.sets.rtr.Rtr.thisEnters
import arcanum.parser.sets.rtr.Rtr.triggered
import arcanum.parser.sets.rtr.Rtr.you
import arcanum.parser.sets.rtr.Rtr.yourUpkeep
import kotlin.test.Test

/**
 * Return to Ravnica chunk 10: the green cards. Counters by the power of the creature that died (as it last existed, CR
 * 603.10a), a pump "for each" with a keyword in front ("Until end of turn, …"), charge counters with X as it enters and an
 * intervening "if" on them (CR 603.4), hexproof only during other players' turns, a land untapping in other untap steps
 * (CR 502.3), "put into a graveyard from anywhere" (CR 603.6c), mana in any combination of colours by a count, an X/X token.
 */
class RtrChunk10GreenTest {
    private val creaturesYouControl = all(creature, you)
    private val charge = CounterKind.Named("charge")

    @Test
    fun skywardAmbush() = card(
        name = "Skyward Ambush",
        cost = "{2}{G}",
        type = "Instant",
        text = "Destroy target creature with flying. You gain 2 life.",
    ) parsesTo abilities(
        spell(Rtr.destroy(target("creature")), gainLife(2), targets = listOf(slot("creature", all(creature, ObjectFilter.HasKeyword(Keyword.Flying))))),
    )

    /** Mana in any combination of colours (CR 106.1b), as much as a count says; a mana ability (CR 605.1a). */
    @Test
    fun axewoodWarden() = card(
        name = "Axewood Warden",
        cost = "{2}{G}",
        type = "Creature — Human Druid",
        pt = "0/3",
        text = """
            Defender
            {T}: Add X mana in any combination of colors, where X is the number of creatures you control with defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(CostPart.TapSelf), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(
            Value.Count(Objects.Each(all(creature, you, ObjectFilter.HasKeyword(Keyword.Defender)))), combination = true)), Players.You))),
    )

    @Test
    fun centaurHarbinger() = card(
        name = "Centaur Harbinger",
        cost = "{G}",
        type = "Creature — Elf Scout",
        pt = "1/1",
        text = "{2}{G}, Sacrifice this creature: Create a 3/3 green Centaur creature token.",
    ) parsesTo abilities(
        activated(cost(manaPart("{2}{G}"), CostPart.Sacrifice(CostObjects.Self)), create(creatureToken("Centaur", listOf(Color.Green), 3, 3))),
    )

    /** The count is fixed as it resolves (CR 611.2c). */
    @Test
    fun anthemOfStrength() = card(
        name = "Anthem of Strength",
        cost = "{3}{G}",
        type = "Instant",
        text = "Until end of turn, target creature gets +1/+1 for each creature you control and gains trample.",
    ) parsesTo abilities(
        spell(pump(target("creature"), Value.Count(Objects.Each(creaturesYouControl)), Value.Count(Objects.Each(creaturesYouControl)),
            Mechanic.GainAbilities(listOf(Keyword.Trample))), targets = listOf(slot("creature", creature))),
    )

    /** "The power of the creature that died": as it last existed on the battlefield (CR 603.10a). */
    @Test
    fun mortalInheritance() = card(
        name = "Mortal Inheritance",
        cost = "{5}{G}",
        type = "Enchantment",
        text = "Whenever a creature you control dies, put X +1/+1 counters on target creature you control, where X is the power of the creature that died.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(creaturesYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
            counters(target("creature"), Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Before), Combine.Single)),
            targets = listOf(slot("creature", creaturesYouControl))),
    )

    @Test
    fun gateclimberVine() = card(
        name = "Gateclimber Vine",
        cost = "{1}{G}",
        type = "Creature — Plant",
        pt = "0/3",
        text = """
            Defender
            When this creature enters, you may search your library for a basic land card or a Gate card, reveal it, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(thisEnters, Step.May(listOf(
            Step.Do(Effect.Search(Players.You, ObjectFilter.Or(listOf(all(ObjectFilter.Super(Supertype.Basic), land), sub("Gate"))), max = n(1), reveal = true,
                zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            Rtr.move(Objects.Bound("found"), Zone.Hand),
            Step.Do(Effect.Shuffle(Players.You)),
        ), player = Players.You)),
    )

    @Test
    fun devouringOoze() = card(
        name = "Devouring Ooze",
        cost = "{4}{G}",
        type = "Creature — Ooze",
        pt = "3/3",
        text = "{G}, Sacrifice another creature: Put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        activated(cost(manaPart("{G}"), CostPart.Sacrifice(CostObjects.Choose(all(another, creature), count = n(1)))), counters(Objects.Self)),
    )

    /** X as it entered (CR 107.3m); "Activate only once each turn"; an intervening "if" (CR 603.4). */
    @Test
    fun manaBlossom() = card(
        name = "Mana Blossom",
        cost = "{X}{G}",
        type = "Enchantment",
        text = """
            This enchantment enters with X charge counters on it.
            Remove a charge counter from this enchantment: Add one mana of any color. Activate only once each turn.
            At the beginning of your upkeep, if this enchantment has no charge counters on it, return it to its owner's hand.
        """,
    ) parsesTo abilities(
        static(Objects.Self, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(charge, Value.CastX(Objects.Self))))),
        Ability.Activated(cost(CostPart.RemoveCounters(charge, n(1), from = CostObjects.Self)),
            Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)))),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
        triggered(yourUpkeep, Rtr.move(Objects.Self, Zone.Hand),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(charge, atLeast = 1)))),
    )

    /** Only while it isn't your turn. */
    @Test
    fun elmLaneInnkeeper() = card(
        name = "Elm Lane Innkeeper",
        cost = "{2}{G}",
        type = "Creature — Elf",
        pt = "2/2",
        text = "During turns other than yours, tapped creatures you control have hexproof.",
    ) parsesTo abilities(
        static(Objects.Each(all(ObjectFilter.HasStatus(arcanum.api.Status.Tapped), creature, you)), Mechanic.GainAbilities(listOf(Keyword.Hexproof)),
            condition = Condition.Not(Condition.YourTurn)),
    )

    @Test
    fun feralSurge() = card(
        name = "Feral Surge",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature gets +2/+2 until end of turn. Untap that creature.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2), Step.Do(Effect.Untap(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun scanTheSkyline() = card(
        name = "Scan the Skyline",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Search your library for up to three basic land cards, reveal them, put them into your hand, then shuffle.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Search(Players.You, all(ObjectFilter.Super(Supertype.Basic), land), max = n(3), reveal = true, zones = listOf(Zone.Library),
                bind = "found", searcher = Players.You)),
            Rtr.move(Objects.Bound("found"), Zone.Hand),
            Step.Do(Effect.Shuffle(Players.You)),
        ),
    )

    /** An X/X token: X fixed as it is created (CR 107.3a, 707.2). */
    @Test
    fun oozeSculpting() = card(
        name = "Ooze Sculpting",
        cost = "{X}{G}",
        type = "Sorcery",
        text = "Create an X/X green Ooze creature token.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(create(TokenSpec.Defined(FaceDefinition(
            name = "Ooze Token", colorIndicator = listOf(Color.Green), types = listOf(arcanum.api.CardType.Creature), subtypes = listOf(Subtype("Ooze")),
            power = PrintedNumber.X, toughness = PrintedNumber.X,
        ), power = Value.Announced("X"), toughness = Value.Announced("X")))), announced = listOf("X"))),
    )

    @Test
    fun stonetrailCrocodile() = card(
        name = "Stonetrail Crocodile",
        cost = "{2}{G}",
        type = "Creature — Crocodile",
        pt = "2/3",
        text = "{2}{B}: This creature gains lifelink until end of turn. (Damage dealt by this creature also causes you to gain that much life.)",
    ) parsesTo abilities(
        activated(cost(manaPart("{2}{B}")), gains(Objects.Self, Keyword.Lifelink)),
    )

    /** A granted static ability: the land untaps in each other player's untap step too (CR 502.3). */
    @Test
    fun civicOvergrowth() = card(
        name = "Civic Overgrowth",
        cost = "{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "Untap this land during each other player's untap step."
        """,
    ) parsesTo abilities(
        enchant(land),
        static(enchanted, Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.UntapsDuringOtherUntapSteps), affects = Affected.Objects(Objects.Self))))),
    )

    /** X is this creature's power as the ability resolves, fixed then (CR 608.2h). */
    @Test
    fun feralPackmaster() = card(
        name = "Feral Packmaster",
        cost = "{2}{G}",
        type = "Creature — Human Shaman",
        pt = "1/1",
        text = "Whenever this creature attacks, each other creature you control gets +X/+X until end of turn, where X is this creature's power.",
    ) parsesTo abilities(
        triggered(thisAttacks, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(
            Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))),
            Affected.Objects(Objects.Each(all(another, creature, you))), Duration.EndOfTurn))),
    )

    /** Dying triggers both; "from anywhere" also from the hand or library (CR 603.6c), the card shuffled back. */
    @Test
    fun worldrootWurm() = card(
        name = "Worldroot Wurm",
        cost = "{8}{G}{G}{G}",
        type = "Creature — Wurm",
        pt = "15/15",
        text = """
            Trample
            When this creature dies, create three 5/5 green Wurm creature tokens with trample.
            When Worldroot Wurm is put into a graveyard from anywhere, shuffle it into its owner's library.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(thisDies, create(creatureToken("Wurm", listOf(Color.Green), 5, 5, Keyword.Trample), count = 3)),
        triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Graveyard)),
            Rtr.move(Objects.EventObject(EventRole.After), Zone.Library),
            Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.EventObject(EventRole.After))))),
    )
}
