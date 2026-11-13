package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 10: fight, utility lands with a second ability, and additional costs (sacrifice a creature, exile creature cards or X cards from your graveyard).
 */
class IsdChunk10FightLandsAdditionalCostTest {
    private val addC: Ability = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))
    private fun additional(vararg parts: CostPart) = static(Mechanic.AdditionalCost(cost = Cost(parts.toList())))
    private fun exileFromYard(filter: ObjectFilter, count: Value) = CostPart.Exile(CostObjects.Choose(filter, count = count), from = Zone.Graveyard)
    private val x: Value = Value.Announced("X")

    // CR 701.14a (fight), 603.4, 701.27 (transform)
    @Test
    fun daylightWarden() = cardFaces(Layout.Transform,
        card(
            name = "Daylight Warden",
            cost = "{2}{G}",
            type = "Creature — Human Archer Ranger Werewolf",
            pt = "2/2",
            text = """
                {T}: This creature deals 2 damage to target creature with flying.
                At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
            """,
        ) to abilities(
            activated(cost(tap), damage(2, toObjects(target("creature"))), targets = listOf(slot("creature", and(creature, ObjectFilter.HasKeyword(Keyword.Flying))))),
            werewolfFront,
        ),
        card(
            name = "Nightfall Stalker",
            cost = null,
            type = "Creature — Werewolf",
            pt = "4/4",
            colorIndicator = "G",
            text = """
                {R}, {T}: This creature fights target creature. (Each deals damage equal to its power to the other.)
                At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
            """,
        ) to abilities(
            activated(cost(mana("{R}"), tap), doing(Effect.Fight(Objects.Self, target("creature"))), targets = listOf(slot("creature", creature))),
            werewolfBack,
        ),
    )

    // CR 701.14a, 701.14b: each deals damage equal to its power to the other
    @Test
    fun preyfall() = card(
        name = "Preyfall",
        cost = "{G}",
        type = "Sorcery",
        text = "Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)",
    ) parsesTo abilities(
        spell(doing(Effect.Fight(target("creature"), target("second creature"))),
            targets = listOf(slot("creature", creatureYouControl),
                slot("second creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 605.1a (mana ability), 602.2: the two abilities share the land's tapped state (the {T} cost, CR 602.5b)
    @Test
    fun gavonyBorough() = card(
        name = "Gavony Borough",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}{G}{W}, {T}: Put a +1/+1 counter on each creature you control.
        """,
    ) parsesTo abilities(
        addC,
        activated(cost(mana("{2}{G}{W}"), tap), counters(Objects.Each(creatureYouControl))),
    )

    // CR 701.8a (destroy), 608.2b: the land's controller searches, so it is read from the destroyed land (CR 608.2h)
    @Test
    fun ghostPlaza() = card(
        name = "Ghost Plaza",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}, Sacrifice this land: Destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        addC,
        activated(cost(tap, sacrificeSelf), destroy(target("land")),
            Step.May(listOf(
                Step.Do(Effect.Search(Players.ControllerOf(target("land")), basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found",
                    searcher = Players.ControllerOf(target("land")))),
                move(Objects.Bound("found"), Zone.Battlefield, controller = Players.ControllerOf(target("land"))),
                Step.Do(Effect.Shuffle(Players.ControllerOf(target("land"))))), player = Players.ControllerOf(target("land"))),
            targets = listOf(slot("land", land))),
    )

    // CR 107.3a, 608.2b: X is announced with the ability; +X/+0 and trample until end of turn
    @Test
    fun kessigHowlRun() = card(
        name = "Kessig Howl Run",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {X}{R}{G}, {T}: Target creature gets +X/+0 and gains trample until end of turn.
        """,
    ) parsesTo abilities(
        addC,
        Ability.Activated(cost(mana("{X}{R}{G}"), tap),
            Instructions(targets = listOf(slot("creature", creature)),
                steps = listOf(run {
                    untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, n(0)), Mechanic.GainAbilities(listOf(Keyword.Trample)))
                }), announced = listOf("X")),
            anyTime),
    )

    // CR 602.2, 118.8: exiling a creature card from your graveyard is a cost; the token is a 1/1 white Spirit with flying
    @Test
    fun moorlandHollow() = card(
        name = "Moorland Hollow",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {W}{U}, {T}, Exile a creature card from your graveyard: Create a 1/1 white Spirit creature token with flying.
        """,
    ) parsesTo abilities(
        addC,
        activated(cost(mana("{W}{U}"), tap, exileFromYard(creature, n(1))),
            create(creatureToken("Spirit", listOf(Color.White), 1, 1, listOf(Keyword.Flying)))),
    )

    // CR 106.1b: one mana of any one color
    @Test
    fun shimmeringCavern() = card(
        name = "Shimmering Cavern",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        addC,
        activated(cost(mana("{1}"), tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(amount = n(1))), Players.You))),
    )

    // CR 120.3, 115.1: any target limited to players and planeswalkers
    @Test
    fun stensiaBloodpit() = card(
        name = "Stensia Bloodpit",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}{B}{R}, {T}: This land deals 2 damage to target player or planeswalker.
        """,
    ) parsesTo abilities(
        addC,
        activated(cost(mana("{3}{B}{R}"), tap), damage(2, Recipients(target = "player or planeswalker")),
            targets = listOf(TargetSlot("player or planeswalker", TargetKind.Either(ObjectFilter.Type(CardType.Planeswalker), PlayerFilter.Any),
                min = n(1), max = n(1)))),
    )

    // CR 601.2b, 118.8: sacrificing a creature is an additional cost
    @Test
    fun altarsReaping() = card(
        name = "Altar's Reaping",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards.
        """,
    ) parsesTo abilities(additional(sacrificeA(creature)), spell(draw(2)))

    // CR 601.2b, 107.3a: X is the number of cards exiled as the additional cost
    @Test
    fun harvestBlaze() = card(
        name = "Harvest Blaze",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, exile X cards from your graveyard.
            Harvest Blaze deals X damage to target creature.
        """,
    ) parsesTo abilities(
        additional(exileFromYard(cardFilter, x)),
        spell(damage(x, toObjects(target("creature"))), targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    // CR 601.2b, 106.1 (adds mana as it resolves)
    @Test
    fun infernalLeap() = card(
        name = "Infernal Leap",
        cost = "{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Add {R}{R}{R}.
        """,
    ) parsesTo abilities(
        additional(sacrificeA(creature)),
        spell(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}{R}")!!)), Players.You))),
    )

    // CR 601.2b: exile a creature card from your graveyard as an additional cost
    @Test
    fun makeshiftBrute() = card(
        name = "Makeshift Brute",
        cost = "{3}{U}",
        type = "Creature — Zombie Horror",
        pt = "4/5",
        text = "As an additional cost to cast this spell, exile a creature card from your graveyard.",
    ) parsesTo abilities(additional(exileFromYard(creature, n(1))))

    // CR 601.2b: two creature cards
    @Test
    fun skaabColossus() = card(
        name = "Skaab Colossus",
        cost = "{5}{U}",
        type = "Creature — Zombie Giant",
        pt = "6/9",
        text = """
            As an additional cost to cast this spell, exile two creature cards from your graveyard.
            Trample
        """,
    ) parsesTo abilities(additional(exileFromYard(creature, n(2))), Keyword.Trample)

    // CR 601.2b, 601.3 (cast from the graveyard): three creature cards
    @Test
    fun skaabDestroyer() = card(
        name = "Skaab Destroyer",
        cost = "{1}{U}{U}",
        type = "Creature — Zombie Horror",
        pt = "5/6",
        text = """
            As an additional cost to cast this spell, exile three creature cards from your graveyard.
            Flying
            You may cast this card from your graveyard.
        """,
    ) parsesTo abilities(
        additional(exileFromYard(creature, n(3))),
        Keyword.Flying,
        Ability.Static(listOf(Mechanic.MayPlay(Zone.Graveyard, how = PlayKind.Cast, holder = PlayerFilter.You)), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun stitchedWyvern() = card(
        name = "Stitched Wyvern",
        cost = "{1}{U}{U}",
        type = "Creature — Zombie Drake",
        pt = "3/4",
        text = """
            As an additional cost to cast this spell, exile a creature card from your graveyard.
            Flying
        """,
    ) parsesTo abilities(additional(exileFromYard(creature, n(1))), Keyword.Flying)
}
