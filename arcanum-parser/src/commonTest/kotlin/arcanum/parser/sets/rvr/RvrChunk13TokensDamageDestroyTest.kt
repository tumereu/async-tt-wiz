package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 13: creating tokens, dealing damage and destroying permanents. */
class RvrChunk13TokensDamageDestroyTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val playerOrPlaneswalker = TargetSlot("player or planeswalker", TargetKind.Either(planeswalker, PlayerFilter.Any),
        min = n(1), max = n(1))
    private val saproling = creatureToken("Saproling", listOf(Color.Green), 1, 1)
    private val power = { of: Objects -> Value.Of(NumericProperty.Power, of, Combine.Single) }

    // CR 700.4 (dies), 111.1 (a token's characteristics come from the effect), 702.20 (vigilance)
    @Test
    fun groveCavalier() = card(
        name = "Grove Cavalier", cost = "{G}{G}{W}{W}", type = "Creature — Centaur Knight", pt = "4/4",
        text = """
            Vigilance
            When this creature dies, create two 2/2 green and white Elf Knight creature tokens with vigilance.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(diesSelf, create(creatureToken("Elf Knight", listOf(Color.Green, Color.White), 2, 2, listOf(Keyword.Vigilance)), n(2))),
    )

    // CR 601.2i (a spell is cast), 111.1
    @Test
    fun whisperingMystic() = card(
        name = "Whispering Mystic", cost = "{3}{U}", type = "Creature — Human Wizard", pt = "1/5",
        text = "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token with flying.",
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)),
            create(creatureToken("Bird Illusion", listOf(Color.Blue), 1, 1, listOf(Keyword.Flying)))),
    )

    // CR 701.26a, 602.2b: a cost can only tap untapped permanents its payer controls, so the canonical form names just the kind
    // (the audit's "no untapped / you-control restriction" is a false alarm; same as crew)
    @Test
    fun sylvanEvangel() = card(
        name = "Sylvan Evangel", cost = "{G}{W}", type = "Creature — Elf Shaman", pt = "1/2",
        text = "{1}, {T}, Tap an untapped creature you control: Create a 1/1 green Saproling creature token.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, CostPart.TapObjects(CostObjects.Choose(creature, count = n(1)))),
            create(saproling)),
    )

    // CR 602.1 (activated abilities), 107.4 hybrid mana
    @Test
    fun brambleGuildmage() = card(
        name = "Bramble Guildmage", cost = "{G/W}{G/W}", type = "Creature — Elf Wizard", pt = "2/2",
        text = """
            {3}{G}: Create a 1/1 green Saproling creature token.
            {3}{W}: Creatures you control get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}{G}")), create(saproling)),
        activated(cost(mana("{3}{W}")), pump(Objects.Each(creatureYouControl), 1, 1)),
    )

    // CR 120.3 (damage dealt by the spell), 508.1 (attacking creatures)
    @Test
    fun heapfall() = card(
        name = "Heapfall", cost = "{3}{R}", type = "Instant",
        text = "Heapfall deals damage to any target equal to the number of attacking creatures you control.",
    ) parsesTo abilities(
        spell(damage(Value.Count(Objects.Each(and(attacking, creature, you))), toAny), targets = listOf(anyTarget)),
    )

    // CR 700.4, 120.3
    @Test
    fun lamplightFiend() = card(
        name = "Lamplight Fiend", cost = "{B/R}", type = "Creature — Devil", pt = "1/1",
        text = "When this creature dies, it deals 1 damage to any target.",
    ) parsesTo abilities(triggered(diesSelf, damage(1, toAny), targets = listOf(anyTarget)))

    // CR 602.2 (sacrifice as a cost), 115.1 (player or planeswalker target)
    @Test
    fun emberGuildmage() = card(
        name = "Ember Guildmage", cost = "{R/G}{R/G}", type = "Creature — Human Shaman", pt = "2/2",
        text = """
            ({R/G} can be paid with either {R} or {G}.)
            {3}{R}, Sacrifice a land: This creature deals 2 damage to target player or planeswalker.
            {3}{G}: Target creature gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{3}{R}"), sacrificeA(land)), damage(2, Recipients(target = "player or planeswalker")),
            targets = listOf(playerOrPlaneswalker)),
        activated(cost(mana("{3}{G}")), pump(target("creature"), 2, 2), targets = listOf(slot("creature", creature))),
    )

    // CR 120.3, 119.1 (life gain), 603.2 (upkeep trigger), 602.2
    @Test
    fun illEarnedLegacy() = card(
        name = "Ill-Earned Legacy", cost = "{3}{B}", type = "Enchantment",
        text = """
            At the beginning of your upkeep, this enchantment deals 1 damage to each opponent and you gain 1 life.
            {5}{B}, Sacrifice this enchantment: It deals 4 damage to target opponent and you gain 4 life.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep(), damage(1, eachOpponent), gainLife(1)),
        activated(cost(mana("{5}{B}"), sacrificeSelf), damage(4, Recipients(players = Players.Target("opponent"))), gainLife(4),
            targets = listOf(targetOpponent)),
    )

    // CR 121.1 (drawing), 603.2
    @Test
    fun zivMizzetTheSparkmind() = card(
        name = "Ziv-Mizzet, the Sparkmind", cost = "{2}{U}{U}{R}{R}", type = "Legendary Creature — Dragon Wizard", pt = "4/4",
        text = """
            Flying
            Whenever you draw a card, Ziv-Mizzet deals 1 damage to any target.
            {T}: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Draw(PlayerFilter.You)), damage(1, toAny), targets = listOf(anyTarget)),
        activated(cost(tap), draw(1)),
    )

    // CR 602.2, 701.21 (sacrifice), 115.1
    @Test
    fun cinderedRusalka() = card(
        name = "Cindered Rusalka", cost = "{R}", type = "Creature — Spirit", pt = "1/1",
        text = "{R}, Sacrifice a creature: This creature deals 1 damage to target player or planeswalker.",
    ) parsesTo abilities(
        activated(cost(mana("{R}"), sacrificeA(creature)), damage(1, Recipients(target = "player or planeswalker")),
            targets = listOf(playerOrPlaneswalker)),
    )

    // CR 608.2h (last known information: the dying creature's power and the damage source), 700.4
    @Test
    fun lurkingVengeance() = card(
        name = "Lurking Vengeance", cost = "{5}{R}{R}", type = "Creature — Avatar", pt = "5/5",
        text = """
            Haste
            Whenever another creature you control dies, it deals damage equal to its power to target player or planeswalker.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(dies(and(another, creature, you)),
            damage(power(Objects.EventObject(EventRole.Before)), Recipients(target = "player or planeswalker"), Objects.EventObject(EventRole.Before)),
            targets = listOf(playerOrPlaneswalker)),
    )

    // CR 120.3 (a creature deals damage equal to its power), 115.3, split card halves 709.3
    @Test
    fun grappleGoreCharge() = cardFaces(
        layout = Layout.Split,
        card(
            name = "Grapple", cost = "{R/G}{R/G}", type = "Instant",
            text = "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control.",
        ) to abilities(
            spell(damage(power(target("creature")), toObjects(target("permanent")), target("creature")),
                targets = listOf(slot("creature", creatureYouControl),
                    slot("permanent", and(or(creature, planeswalker), notYours)))),
        ),
        card(
            name = "Gore Charge", cost = "{2}{R}{G}", type = "Sorcery",
            text = "Create a 4/4 red and green Beast creature token with trample.",
        ) to abilities(
            spell(create(creatureToken("Beast", listOf(Color.Red, Color.Green), 4, 4, listOf(Keyword.Trample)))),
        ),
    )

    // CR 700.4, 102.2 (each opponent), 119.1
    @Test
    fun spitefulVampire() = card(
        name = "Spiteful Vampire", cost = "{3}{B}", type = "Creature — Vampire", pt = "2/3",
        text = "Whenever another creature you control dies, this creature deals 1 damage to each opponent and you gain 1 life.",
    ) parsesTo abilities(
        triggered(dies(and(another, creature, you)), damage(1, eachOpponent), gainLife(1)),
    )

    // CR 701.8a (destroy), 115.1
    @Test
    fun razeMachinery() = card(
        name = "Raze Machinery", cost = "{3}{R}", type = "Sorcery", text = "Destroy target artifact or land.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, land)))),
    )

    // CR 107.3 (X in a spell's cost is its announced value), 202.3 (mana value)
    @Test
    fun gutCheck() = card(
        name = "Gut Check", cost = "{X}{B}", type = "Instant", text = "Destroy target creature with mana value X.",
    ) parsesTo abilities(
        spell(destroy(target("creature")),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ManaValue(Comparison.Equal, Value.Announced("X"))))),
            announced = listOf("X")),
    )
}
