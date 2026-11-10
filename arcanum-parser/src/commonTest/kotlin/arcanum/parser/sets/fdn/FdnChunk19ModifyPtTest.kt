package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Step
import arcanum.api.Modification
import arcanum.api.Destination
import arcanum.api.EventPattern
import arcanum.api.PlayerFilter
import arcanum.api.Zone
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 19 of FDN: lords, anthems and conditional or counted P/T changes. Invented names, the real oracle text. */
class FdnChunk19ModifyPtTest {
    private fun elf(): ObjectFilter = sub("Elf")
    private fun cat(): ObjectFilter = sub("Cat")
    private fun anthem(group: ObjectFilter, vararg also: Mechanic) =
        static(pt(1, 1), *also, affects = Objects.Each(group))
    private fun lord(vararg group: ObjectFilter, also: List<Mechanic> = emptyList()) =
        static(pt(1, 1), *also.toTypedArray(), affects = Objects.Each(and(another, *group)))
    private val lifelinkGrant = Mechanic.GainAbilities(listOf(Keyword.Lifelink))

    // CR 613.4c: all creatures you control, the source included
    @Test
    fun anthemOfHeroes() = card(
        name = "Anthem of Heroes", cost = "{G}{W}", type = "Enchantment",
        text = "Creatures you control get +1/+1.",
    ) parsesTo abilities(anthem(and(creature, you)))

    // CR 613.4c, 205.3m, 603.6a, 111.1
    @Test
    fun arahboTheSecondFang() = card(
        name = "Arahbo, the Second Fang", cost = "{2}{W}", type = "Legendary Creature — Cat Avatar", pt = "2/2",
        text = """
            Other Cats you control get +1/+1.
            Whenever Arahbo or another nontoken Cat you control enters, create a 1/1 white Cat creature token.
        """,
    ) parsesTo abilities(
        lord(cat(), you),
        triggered(
            enters(ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self), and(another, not(token), cat(), you)))),
            create(creatureToken("Cat", listOf(Color.White), 1, 1)),
        ),
    )

    // CR 613.4c, 702.17a (reach), 508.1, 120.1
    @Test
    fun dwynenOfTheOakDaen() = card(
        name = "Dwynen, Oak Daen", cost = "{2}{G}{G}", type = "Legendary Creature — Elf Warrior", pt = "3/4",
        text = """
            Reach (This creature can block creatures with flying.)
            Other Elf creatures you control get +1/+1.
            Whenever Dwynen attacks, you gain 1 life for each attacking Elf you control.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        lord(elf(), creature, you),
        triggered(
            on(arcanum.api.EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            Step.Do(arcanum.api.Effect.GainLife(arcanum.api.Players.You, count(and(attacking, elf(), you)))),
        ),
    )

    // CR 613.4c, 605.1a (mana ability), 106.1
    @Test
    fun elvishArchdruidOfTheGlade() = card(
        name = "Elvish Archdruid of the Glade", cost = "{1}{G}{G}", type = "Creature — Elf Druid", pt = "2/2",
        text = """
            Other Elf creatures you control get +1/+1.
            {T}: Add {G} for each Elf you control.
        """,
    ) parsesTo abilities(
        lord(elf(), creature, you),
        activated(cost(tap), Step.Do(arcanum.api.Effect.AddMana(arcanum.api.ManaProduction(
            arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{G}")!!, times = count(and(elf(), you)))), arcanum.api.Players.You))),
    )

    // CR 613.4c, 702.9a (flying): the group is picked by a keyword
    @Test
    fun skyEagleOfTheReach() = card(
        name = "Sky Eagle of the Reach", cost = "{1}{W}{U}", type = "Creature — Bird Spirit", pt = "2/3",
        text = """
            Flying
            Other creatures you control with flying get +1/+1.
        """,
    ) parsesTo abilities(Keyword.Flying, lord(creature, you, kw(Keyword.Flying.kind)))

    // CR 613.4c, 611.3a (static condition), 207.2c, 702.10a
    @Test
    fun ghituFireRunner() = card(
        name = "Ghitu Fire Runner", cost = "{R}", type = "Creature — Human Wizard", pt = "1/2",
        text = "As long as there are two or more instant and/or sorcery cards in your graveyard, this creature gets +1/+0 and has haste. (It can attack and {T} as soon as it comes under your control.)",
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.Haste), condition = Condition.Exists(inYourGraveyard(or(instant, sorcery)), 2)),
    )

    // CR 613.4c: only attacking creatures
    @Test
    fun goblinBanner() = card(
        name = "Goblin Banner of War", cost = "{1}{R}", type = "Enchantment",
        text = "Attacking creatures you control get +1/+0.",
    ) parsesTo abilities(static(pt(1, 0), affects = Objects.Each(and(attacking, creature, you))))

    // CR 613.4c, 602.2 (activated), 111.1
    @Test
    fun perfectOfTheWood() = card(
        name = "Perfect of the Wood", cost = "{2}{G}", type = "Creature — Elf Warrior", pt = "2/2",
        text = """
            Other Elves you control get +1/+1.
            {G}, {T}: Create a 1/1 green Elf Warrior creature token.
        """,
    ) parsesTo abilities(
        lord(elf(), you),
        activated(cost(mana("{G}"), tap), create(creatureToken("Elf Warrior", listOf(Color.Green), 1, 1))),
    )

    // CR 613.4c, 613.4f (abilities), 702.2/702.7/702.15
    @Test
    fun lyraSkybringer() = card(
        name = "Lyra Skybringer", cost = "{3}{W}{W}", type = "Legendary Creature — Angel", pt = "5/5",
        text = """
            Flying
            First strike (This creature deals combat damage before creatures without first strike.)
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            Other Angels you control get +1/+1 and have lifelink.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.FirstStrike, Keyword.Lifelink,
        lord(sub("Angel"), you, also = listOf(lifelinkGrant)),
    )

    // CR 613.4c, 702.15, 111.1
    @Test
    fun caracalOfTheDunes() = card(
        name = "Caracal of the Dunes", cost = "{3}{W}{W}", type = "Creature — Cat", pt = "3/3",
        text = """
            Other Cats you control get +1/+1 and have lifelink. (Damage dealt by those creatures also causes you to gain that much life.)
            When this creature enters, create two 1/1 white Cat creature tokens with lifelink.
        """,
    ) parsesTo abilities(
        lord(cat(), you, also = listOf(lifelinkGrant)),
        triggered(entersSelf, create(creatureToken("Cat", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Lifelink)), n(2))),
    )

    // CR 613.4c, 604.3, 205.3i: a count of basic Islands you control (a land type implies the land card type)
    @Test
    fun stormDjinn() = card(
        name = "Storm Djinn of the Isles", cost = "{U}{U}{U}", type = "Creature — Djinn", pt = "0/4",
        text = """
            Flying
            This creature gets +1/+0 for each basic Island you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.ModifyPowerToughness(count(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), sub("Island"), you)), n(0))),
    )

    // CR 613.4c, 105.1, 205.4: two colour lords on separate lines, plus a discard replacement
    @Test
    fun liegeOfTheGlade() = card(
        name = "Liege of the Glade", cost = "{1}{G/W}{G/W}{G/W}", type = "Creature — Elf Knight", pt = "4/4",
        text = """
            ({G/W} can be paid with either {G} or {W}.)
            Other green creatures you control get +1/+1.
            Other white creatures you control get +1/+1.
            If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
        """,
    ) parsesTo abilities(
        lord(ObjectFilter.OfColor(ColorRef.Literal(Color.Green)), creature, you),
        lord(ObjectFilter.OfColor(ColorRef.Literal(Color.White)), creature, you),
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You, causedBy = ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Battlefield))))),
            affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
    )
}
