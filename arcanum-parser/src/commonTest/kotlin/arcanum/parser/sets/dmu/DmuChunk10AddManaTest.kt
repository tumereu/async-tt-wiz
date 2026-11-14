package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.PlayKind
import arcanum.api.Zone
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 10: mana abilities. Painlands (CR 605.1a, 120.3), tapping untapped permanents as a cost (CR 602.2b, 605.1a),
 * a spending restriction (CR 106.6) and a land that becomes a creature (CR 613.1d, 205.1b).
 */
class DmuChunk10AddManaTest {
    private val addColorless = activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))

    private fun oneOf(vararg colors: Color) = ManaKind.Choose(amount = Value.Fixed(1), colors = colors.map { ColorRef.Literal(it) })
    private fun addKind(kind: ManaKind) = Step.Do(Effect.AddMana(ManaProduction(kind), Players.You))
    private val anyColor = ManaKind.Choose(amount = Value.Fixed(1))

    // CR 605.1a, 120.3: the damage is part of the mana ability's resolution
    private fun painland(name: String, a: Color, b: Color, ca: String, cb: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add {$ca} or {$cb}. This land deals 1 damage to you.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), addKind(oneOf(a, b)),
            Step.Do(Effect.DealDamage(Recipients(players = Players.You), amount = Value.Fixed(1), source = Objects.Self))),
    )

    @Test fun duskWastes() = painland("Dusk Wastes", Color.White, Color.Blue, "W", "U")
    @Test fun veilfenCaves() = painland("Veilfen Caves", Color.White, Color.Black, "W", "B")
    @Test fun thornfenForest() = painland("Thornfen Forest", Color.Red, Color.Green, "R", "G")
    @Test fun brineReef() = painland("Brine Reef", Color.Blue, Color.Red, "U", "R")
    @Test fun ruinmarshSprings() = painland("Ruinmarsh Springs", Color.Black, Color.Red, "B", "R")
    @Test fun greenhollowCoast() = painland("Greenhollow Coast", Color.Green, Color.Blue, "G", "U")

    // CR 602.2b: the tapped permanent is chosen among untapped nontoken artifacts; CR 605.1a: still a mana ability
    @Test
    fun meriaStand() = card(
        name = "Merrow, Keeper of Gears",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Elf Artificer",
        text = """
            Tap an untapped nontoken artifact you control: Add {G}.
            Tap two untapped nontoken artifacts you control: Exile the top card of your library. You may play it this turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        activated(cost(CostPart.TapObjects(CostObjects.Choose(and(not(token), artifact), count = Value.Fixed(1)))), addKind(ManaKind.Fixed(ManaCost.parse("{G}")!!))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(and(not(token), artifact), count = Value.Fixed(2)))),
            doing(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn))),
    )

    // CR 106.6: the mana can be spent only on a legendary spell; CR 106.1b: "of any color among" legendary permanents
    @Test
    fun plazaOfBanners() = card(
        name = "Plaza of Banners",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a legendary spell.
            {T}: Add one mana of any color among legendary permanents you control.
            {3}, {T}, Exile this land: Target legendary creature gains hexproof and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(anyColor, restriction = and(spellFilter, legendary)), Players.You))),
        activated(cost(tap), addKind(ManaKind.Choose(Value.Fixed(1), colors = listOf(ColorRef.Of(Objects.Each(and(legendary, permanentFilter, you)))))) ),
        activated(cost(mana("{3}"), tap, CostPart.Exile(CostObjects.Self, from = Zone.Battlefield)), gains(target("creature"), Keyword.Hexproof, Keyword.Indestructible),
            targets = listOf(slot("creature", and(legendary, creature)))),
    )

    // CR 602.2b, 605.1a
    @Test
    fun relicOfSagas() = card(
        name = "Relic of Sagas",
        cost = "{3}",
        type = "Artifact",
        text = """
            {T}: Add one mana of any color.
            Tap an untapped legendary creature you control: Add one mana of any color.
        """,
    ) parsesTo abilities(
        activated(cost(tap), addKind(anyColor)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(and(legendary, creature), count = Value.Fixed(1)))), addKind(anyColor)),
    )

    // CR 613.1d, 205.1b ("It's still a land" is a reminder), 307.5 (sorcery timing), 702.10 (haste)
    @Test
    fun loamweaverSprite() = card(
        name = "Loamweaver Sprite",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        text = """
            {T}: Add one mana of any color.
            {T}: Target land you control becomes a 3/3 Elemental creature with haste until end of turn. It's still a land. Activate only as a sorcery.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        activated(cost(tap), addKind(anyColor)),
        activated(cost(tap),
            untilEndOfTurn(target("land"), Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(3), n(3)), Mechanic.GainAbilities(listOf(Keyword.Haste))),
            targets = listOf(slot("land", and(land, you))), rules = asSorcery),
    )
}
