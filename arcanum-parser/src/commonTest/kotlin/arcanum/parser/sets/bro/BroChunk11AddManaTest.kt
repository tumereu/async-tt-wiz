package arcanum.parser.sets.bro

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 11: "Add" abilities: painlands, any-color and counted mana, restricted mana, and extra costs on mana abilities. */
class BroChunk11AddManaTest {
    private fun fixed(mana: String) = ManaKind.Fixed(ManaCost.parse(mana)!!)
    private fun add(kind: ManaKind, restriction: ObjectFilter? = null) =
        doing(Effect.AddMana(ManaProduction(kind, restriction = restriction), Players.You))
    private val addColorless = activated(cost(tap), add(fixed("{C}")))
    private val landController = Players.ControllerOf(target("land"))

    // CR 605.1a (mana ability), 120.3 (damage to a player): the damage is part of the one ability
    @Test
    fun battlefieldForge() = painlandOf("Ember Forge Heath", Color.Red, Color.White, "{R} or {W}")

    @Test
    fun brushland() = painlandOf("Mossy Brushwood", Color.Green, Color.White, "{G} or {W}")

    @Test
    fun llanowarWastes() = painlandOf("Fenwood Wastes", Color.Black, Color.Green, "{B} or {G}")

    @Test
    fun undergroundRiver() = painlandOf("Murky River Run", Color.Blue, Color.Black, "{U} or {B}")

    private fun painlandOf(name: String, a: Color, b: Color, kinds: String) = card(
        name = name, cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add $kinds. This land deals 1 damage to you.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), add(ManaKind.Choose(n(1), colors = listOf(ColorRef.Literal(a), ColorRef.Literal(b)))), damage(1, Recipients(players = Players.You))),
    )

    @Test
    fun citanulStalwart() = card(
        name = "Citadel Stalwart Mage", cost = "{G}", type = "Creature — Elf Druid Soldier", pt = "1/1",
        text = "{T}, Tap an untapped artifact or creature you control: Add one mana of any color.",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.TapObjects(CostObjects.Choose(or(artifact, creature), count = n(1)))), add(anyOneColor(1))),
    )

    // CR 106.1b (any one color), 202.? n/a: X counts the creature cards in your graveyard when the ability resolves
    @Test
    fun deathbloomRitualist() = card(
        name = "Graveblossom Ritualist", cost = "{3}{B}{G}", type = "Creature — Elf Warlock", pt = "3/5",
        text = "{T}: Add X mana of any one color, where X is the number of creature cards in your graveyard.",
    ) parsesTo abilities(
        activated(cost(tap), add(ManaKind.Choose(count(inYourGraveyard(creature))))),
    )

    // CR 701.23a, 701.24: same wording as the tested land-destruction land
    @Test
    fun demolitionField() = card(
        name = "Quarry Breaker Field", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Sacrifice this land: Destroy target nonbasic land an opponent controls. That land's controller may search their library for a basic land card, put it onto the battlefield, then shuffle. You may search your library for a basic land card, put it onto the battlefield, then shuffle.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{2}"), tap, sacrificeSelf),
            destroy(target("land")),
            Step.May(player = landController, steps = listOf(
                Step.Do(Effect.Search(landController, basicLand(), max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = landController)),
                move(Objects.Bound("found"), Zone.Battlefield, controller = landController),
                Step.Do(Effect.Shuffle(landController)))),
            Step.May(player = Players.You, steps = listOf(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield), shuffle)),
            targets = listOf(slot("land", and(not(ObjectFilter.Super(arcanum.api.Supertype.Basic)), land, opponents)))),
    )

    // CR 106.6 (spending restriction): creature spells, or abilities of creature sources
    @Test
    fun gwennaEyesOfGaea() = card(
        name = "Gwendal, Eyes of Gaia", cost = "{2}{G}", type = "Legendary Creature — Elf Druid Scout", pt = "2/3",
        text = """
            {T}: Add two mana in any combination of colors. Spend this mana only to cast creature spells or activate abilities of creature sources.
            Whenever you cast a creature spell with power 5 or greater, put a +1/+1 counter on Gwendal and untap it.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = true),
            restriction = or(and(spellFilter, creature), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = creature))), Players.You))),
        triggered(youCast(and(creature, power(arcanum.api.Comparison.AtLeast, 5))),
            counters(Objects.Self), untapIt(Objects.Self)),
    )

    // CR 701.? n/a; "This land" is still a land (CR 205.1b); the target is an attacking Assembly-Worker
    @Test
    fun mishrasFoundry() = card(
        name = "Hearth Foundry", cost = null, type = "Land",
        text = """
            {T}: Add {C}.
            {2}: This land becomes a 2/2 Assembly-Worker artifact creature until end of turn. It's still a land.
            {1}, {T}: Target attacking Assembly-Worker gets +2/+2 until end of turn.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(mana("{2}")), untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature),
            addSubtypes = listOf(SubtypeRef.Literal(Subtype("Assembly-Worker")))), Mechanic.SetPowerToughness(n(2), n(2)))),
        activated(cost(mana("{1}"), tap), pump(target("Assembly-Worker"), 2, 2),
            targets = listOf(slot("Assembly-Worker", and(attacking, sub("Assembly-Worker"))))),
    )

    // CR 605.1a: a mana ability may have a sacrifice cost; the second ability is not a mana ability
    @Test
    fun transmograntAltar() = card(
        name = "Reanimant Altar", cost = "{3}", type = "Artifact",
        text = """
            {B}, {T}, Sacrifice a creature: Add {C}{C}{C}.
            {2}, {T}, Sacrifice a creature: Create a 3/3 colorless Zombie artifact creature token. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{B}"), tap, sacrificeA(creature)), add(fixed("{C}{C}{C}"))),
        activated(cost(mana("{2}"), tap, sacrificeA(creature)),
            create(creatureToken("Zombie", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature))), rules = asSorcery),
    )
}
