package arcanum.engine.cards

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.Value

/**
 * Invented cards written by hand (spec §4.4: definitions can be written by hand) for layer interactions the cube lacks:
 * land-type setting, ability loss for every creature, and an anthem that also changes colour. Names and costs are our
 * own; each KDoc gives the oracle-style text it stands for.
 */
internal object InventedCards {
    private val creatures = ObjectFilter.Type(CardType.Creature)
    private val lands = ObjectFilter.Type(CardType.Land)

    private fun card(face: FaceDefinition) = face.name to CardDefinition(Layout.Normal, listOf(face))

    private fun static(vararg mechanics: Mechanic, affects: Objects) =
        Ability.Static(mechanics.toList(), affects = Affected.Objects(affects))

    val all: Map<String, CardDefinition> = mapOf(
        /** "Nonbasic lands are Mountains." (CR 305.7) */
        card(FaceDefinition(
            name = "Crimson Eclipse",
            manaCost = ManaCost.parse("{2}{R}"),
            types = listOf(CardType.Enchantment),
            abilities = listOf(static(
                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mountain"))), replaceSubtypes = true),
                affects = Objects.Each(ObjectFilter.And(listOf(lands, ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic))))),
            )),
        )),
        /** "Each land is a Swamp in addition to its other land types." */
        card(FaceDefinition(
            name = "Mirefen Spire",
            supertypes = listOf(Supertype.Legendary),
            types = listOf(CardType.Land),
            abilities = listOf(static(
                Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Swamp")))),
                affects = Objects.Each(lands),
            )),
        )),
        /** "All creatures lose all abilities and have base power and toughness 1/1." */
        card(FaceDefinition(
            name = "Hush of the Meek",
            manaCost = ManaCost.parse("{2}{W}{W}"),
            types = listOf(CardType.Enchantment),
            abilities = listOf(static(
                Mechanic.LoseAbilities(),
                Mechanic.SetPowerToughness(Value.Fixed(1), Value.Fixed(1)),
                affects = Objects.Each(creatures),
            )),
        )),
        /** "Other creatures you control are black and get +1/+1." */
        card(FaceDefinition(
            name = "Gloomcrest Herald",
            manaCost = ManaCost.parse("{2}{B}"),
            types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Zombie"), Subtype("Cleric")),
            power = PrintedNumber.Fixed(2),
            toughness = PrintedNumber.Fixed(2),
            abilities = listOf(static(
                Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black))),
                Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)),
                affects = Objects.Each(ObjectFilter.And(listOf(
                    creatures, ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                ))),
            )),
        )),
    )
}
