package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 14: green cards. Damage dealt by a creature to up to one other target, "can't be blocked by creatures with power 2
 * or less" (CR 509.1b), creatures that entered under your control this turn, an intervening "if you control three or more creatures",
 * fights on entering (CR 701.14), cards milled and one chosen from among them (CR 701.17), affinity for Forests (CR 702.41), an
 * enchantment exiled as a cost, a land of the chosen colour that makes more mana of it (CR 106.12a), two creatures chosen and their power
 * difference, and "lands and/or Treefolk".
 */
class EclChunk14GreenTest {
    private val treefolk = creatureToken("Treefolk", listOf(arcanum.api.Color.Green), 3, 4, abilities = listOf(Keyword.Reach))
    private val enteredThisTurn = Condition.HappenedThisTurn(EventPattern.ZoneChange(creatureYouControl, to = Zone.Battlefield), atLeast = 1,
        measure = Measure.Events)

    // the first target deals the damage (CR 120.3)
    @Test
    fun declareFlawlessness() = card(
        name = "Declare Flawlessness",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Target creature you control gets +1/+0 until end of turn. It deals damage equal to its power to up to one target creature an opponent controls.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 0), damage(power(target("creature")), toObjects(target("second creature")), source = target("creature")),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls, min = 0))),
    )

    @Test
    fun burgeoningShield() = card(
        name = "Burgeoning Shield",
        cost = "{G}",
        type = "Instant",
        text = "Target creature you control gets +2/+2 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.Hexproof)), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 509.1b
    @Test
    fun spinebaneRunner() = card(
        name = "Spinebane Runner",
        cost = "{3}{G}",
        type = "Creature — Kithkin Knight",
        text = """
            This creature can't be blocked by creatures with power 2 or less.
            As long as another creature entered the battlefield under your control this turn, this creature gets +2/+0.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
        static(pt(2, 0), condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(and(another, creature, you), to = Zone.Battlefield), atLeast = 1,
            measure = Measure.Events)),
    )

    @Test
    fun waymeetSentinel() = card(
        name = "Waymeet Sentinel",
        cost = "{2}{G}",
        type = "Creature — Kithkin Ranger",
        text = """
            Trample
            Whenever another creature you control enters, this creature gets +1/+0 until end of turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(anotherCreatureYouControlEnters, pump(Objects.Self, 1, 0)),
    )

    @Test
    fun morningsRayBowman() = card(
        name = "Morning's Ray Bowman",
        cost = "{2}{G}",
        type = "Creature — Elf Archer",
        text = """
            Flash
            Reach
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Reach,
    )

    // CR 603.4
    @Test
    fun hillmootWeaver() = card(
        name = "Hillmoot Weaver",
        cost = "{1}{G}",
        type = "Creature — Kithkin Druid",
        text = "When this creature enters, if you control three or more creatures, return target permanent card from your graveyard to your hand.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(permanentCard))),
            interveningIf = Condition.Exists(creatureYouControl, atLeast = 3)),
    )

    @Test
    fun imposingOrator() = card(
        name = "Imposing Orator",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        text = """
            When this creature enters, you may discard a card. If you do, search your library for a creature card, reveal it, put it into your hand, then shuffle.
            {1}, {T}: Untap another target permanent.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, *mayPay(discardACard, then = listOf(search(creature, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle)).toTypedArray()),
        activated(cost(mana("{1}"), tap), untapIt(target("permanent")), targets = listOf(slot("permanent", and(another, permanentFilter)))),
    )

    @Test
    fun goldLeafsClasp() = card(
        name = "Gold-Leaf's Clasp",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains trample and indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it. If its toughness is 0 or less, it still dies.)
            Enchanted creature gets +2/+0.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, gains(Objects.AttachedTo(Objects.Self), Keyword.Trample, Keyword.Indestructible)),
        static(pt(2, 0), affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun deepwoodDruid() = card(
        name = "Deepwood Druid",
        cost = "{1}{G}",
        type = "Creature — Treefolk Druid",
        text = "{T}: Add one mana of any color.",
        pt = "0/4",
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    @Test
    fun lysValenLookout() = card(
        name = "Lys Valen Lookout",
        cost = "{1}{G}",
        type = "Creature — Elf Scout",
        text = "When this creature enters or dies, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
        pt = "3/1",
    ) parsesTo abilities(
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))), doing(Effect.Surveil(Players.You, n(1)))),
    )

    // CR 701.17a: "from among them": the milled cards
    @Test
    fun moonlitFurrowing() = card(
        name = "Moonlit Furrowing",
        cost = "{1}{G}",
        type = "Instant",
        text = "Mill four cards, then you may return a permanent card from among them to your hand. (To mill four cards, put the top four cards of your library into your graveyard.)",
    ) parsesTo abilities(
        spell(mill(4, postBind = "milled"),
            Step.Choose(Choice.Objects(permanentCard, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
    )

    @Test
    fun fogvaleAssembly() = card(
        name = "Fogvale Assembly",
        cost = "{4}{G}",
        type = "Creature — Kithkin Advisor",
        text = """
            This spell costs {1} less to cast if you control a Kithkin.
            When this creature enters, draw a card.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        costsLess("{1}", n(1), condition = Condition.Exists(and(sub("Kithkin"), you), atLeast = 1)),
        triggered(entersSelf, draw(1)),
    )

    @Test
    fun moonWatchDevotees() = card(
        name = "Moon-Watch Devotees",
        cost = "{2}{G}{G}",
        type = "Creature — Elf Druid",
        text = """
            Trample
            This creature gets +1/+1 for each creature you control and each creature card in your graveyard.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Trample,
        run {
            val x = Value.Sum(listOf(count(creatureYouControl), count(inYourGraveyard(creature))))
            static(Mechanic.ModifyPowerToughness(x, x))
        },
    )

    @Test
    fun velsynsGaze() = card(
        name = "Velsyn's Gaze",
        cost = "{1}{G}",
        type = "Kindred Enchantment — Elf",
        text = """
            At the beginning of your upkeep, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {4}{G}{G}, Sacrifice this enchantment: Create X 2/2 black and green Elf creature tokens, where X is the number of Elf cards in your graveyard. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.You), doing(Effect.Surveil(Players.You, n(1)))),
        activated(cost(mana("{4}{G}{G}"), sacrificeSelf), create(elf, count(inYourGraveyard(sub("Elf")))), rules = asSorcery),
    )

    // CR 701.14a: the enchanted creature fights
    @Test
    fun mercilessKnuckles() = card(
        name = "Merciless Knuckles",
        cost = "{3}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            When this Aura enters, enchanted creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
            Enchanted creature gets +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureYouControl)),
        triggered(entersSelf, doing(Effect.Fight(Objects.AttachedTo(Objects.Self), target("creature"))), targets = listOf(slot("creature", creatureOpponentControls, min = 0))),
        static(pt(2, 2), affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun bruiserForCoin() = card(
        name = "Bruiser for Coin",
        cost = "{4}{G}",
        type = "Creature — Giant Mercenary",
        text = """
            Reach, vigilance
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            When this creature enters, you gain X life, where X is the greatest power among Giants you control.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        Keyword.Ward(manaCost("{2}")),
        triggered(entersSelf, doing(Effect.GainLife(Players.You, Value.Of(NumericProperty.Power, Objects.Each(and(sub("Giant"), you)), arcanum.api.Combine.Greatest)))),
    )

    // CR 509.1b: at most one blocker
    @Test
    fun keeperOutriders() = card(
        name = "Keeper Outriders",
        cost = "{3}{G}",
        type = "Creature — Elf Warrior",
        text = """
            This creature can't be blocked by more than one creature.
            {5}: Target Elf you control gets +2/+2 until end of turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.MaximumBlockers(1)),
        activated(manaCost("{5}"), pump(target("Elf"), 2, 2), targets = listOf(slot("Elf", and(sub("Elf"), you)))),
    )

    // CR 702.41a: affinity for Forests; CR 207.2c: landfall
    @Test
    fun seedlingGrove() = card(
        name = "Seedling Grove",
        cost = "{6}{G}{G}",
        type = "Enchantment",
        text = """
            Affinity for Forests (This spell costs {1} less to cast for each Forest you control.)
            Landfall — Whenever a land you control enters, create a 3/4 green Treefolk creature token with reach.
            {1}{G}, Exile this enchantment: Treefolk and Forests you control gain indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(sub("Forest"), "Forests"),
        triggered(landfall, create(treefolk), abilityWord = "Landfall"),
        activated(cost(mana("{1}{G}"), CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)),
            gains(Objects.Each(and(or(sub("Treefolk"), sub("Forest")), you)), Keyword.Indestructible)),
    )

    // CR 106.12a: a triggered mana ability adding mana of the chosen colour
    @Test
    fun glimmerwoodBloom() = card(
        name = "Glimmerwood Bloom",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            As this Aura enters, choose a color.
            Enchanted land is the chosen color.
            Whenever enchanted land is tapped for mana, its controller adds an additional one mana of the chosen color.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        static(Mechanic.SetColors(listOf(ColorRef.Remembered("color"))), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.Tap(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), forMana = true)),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.ControllerOf(Objects.EventObject(EventRole.Object))))),
    )

    // the greater power less the smaller
    @Test
    fun nimbleAndHale() = card(
        name = "Nimble and Hale",
        cost = "{4}{G}",
        type = "Sorcery",
        text = "Choose exactly two creatures you control. You draw X cards and the chosen creatures get +X/+X and gain trample until end of turn, where X is the difference between the chosen creatures' powers.",
    ) parsesTo abilities(
        run {
            val chosen = Objects.Bound("chosen")
            val x = Value.Difference(Value.Of(NumericProperty.Power, chosen, arcanum.api.Combine.Greatest), Value.Of(NumericProperty.Power, chosen, arcanum.api.Combine.Least))
            spell(Step.Choose(Choice.Objects(creatureYouControl, n(2), n(2)), bind = "chosen", chooser = Players.You),
                doing(Effect.Draw(Players.You, x)),
                untilEndOfTurn(chosen, Mechanic.ModifyPowerToughness(x, x), has(Keyword.Trample)))
        },
    )

    @Test
    fun gruffShoer() = card(
        name = "Gruff Shoer",
        cost = "{1}{G}",
        type = "Creature — Kithkin Citizen",
        text = "{T}: Target creature you control gets +1/+1 and gains vigilance until end of turn. Activate only as a sorcery.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(tap), pump(target("creature"), 1, 1, has(Keyword.Vigilance)), targets = listOf(slot("creature", creatureYouControl)), rules = asSorcery),
    )

    @Test
    fun nurtureTheShoots() = card(
        name = "Nurture the Shoots",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Then if you control seven or more lands and/or Treefolk, create a 3/4 green Treefolk creature token with reach. (It can block creatures with flying.)",
    ) parsesTo abilities(
        spell(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle,
            Step.If(Condition.Exists(and(or(land, sub("Treefolk")), you), atLeast = 7), then = listOf(create(treefolk)))),
    )

    @Test
    fun mindweaveRush() = card(
        name = "Mindweave Rush",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature gets +3/+3 until end of turn. If a creature entered the battlefield under your control this turn, draw a card.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 3), Step.If(enteredThisTurn, then = listOf(draw(1))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun relentlessShot() = card(
        name = "Relentless Shot",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target creature with flying.
            • Destroy target enchantment.
            • Create a 2/2 black and green Elf creature token.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(creature, kw(Keyword.Flying.kind))))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(create(elf))), min = 1, max = 1, chooser = Players.You))),
    )

    // CR 509.1c: a requirement on the defending player
    @Test
    fun vinebornScrapper() = card(
        name = "Vineborn Scrapper",
        cost = "{2}{G}",
        type = "Creature — Elf Berserker",
        text = """
            This creature must be blocked if able.
            Whenever this creature attacks, another target Elf you control gets +2/+1 until end of turn.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        static(Mechanic.MustBeBlocked),
        triggered(attacksSelf, pump(target("Elf"), 2, 1), targets = listOf(slot("Elf", and(another, sub("Elf"), you)))),
    )

    @Test
    fun venomousEnvoy() = card(
        name = "Venomous Envoy",
        cost = "{G}",
        type = "Creature — Elf Assassin",
        text = """
            Deathtouch
            Whenever another creature you control enters, you gain 1 life.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(anotherCreatureYouControlEnters, gainLife(1)),
    )
}
