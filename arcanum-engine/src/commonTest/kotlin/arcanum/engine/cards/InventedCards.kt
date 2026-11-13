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
        // Keyword actions, written in the model (CR 701; the parser reads them since the lane wordings round)
        /** "Manifest the top two cards of your library." (CR 701.40a, c) */
        sorcery("Twofold Manifestation", "{2}{G}", action(arcanum.api.KeywordAction.Manifest(Value.Fixed(2)))),
        /** "Cloak the top card of your library." (CR 701.58a) */
        sorcery("Shrouded Arrival", "{1}{W}", action(arcanum.api.KeywordAction.Cloak(Value.Fixed(1)))),
        /** "Support 2. (Put a +1/+1 counter on each of up to two target creatures.)" (CR 701.39b) */
        sorcery("Rallying Support", "{1}{W}", arcanum.api.Instructions(
            targets = listOf(arcanum.api.TargetSlot("supported", arcanum.api.TargetKind.Objects(creatures), min = Value.Fixed(0), max = Value.Fixed(2))),
            steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.Action(arcanum.api.KeywordAction.Support(Value.Fixed(2), "supported")))))),
        /** "Double the mana in your mana pool." (CR 701.10e) */
        instant("Mana Doubling", "{G}", action(arcanum.api.KeywordAction.Double(arcanum.api.Doubled.Mana(arcanum.api.Players.You)))),
        /** "Discover 3." (CR 701.57a) */
        sorcery("Hidden Discovery", "{2}{U}", action(arcanum.api.KeywordAction.Discover(Value.Fixed(3)))),
        /** "Learn." (CR 701.48a) */
        sorcery("Curious Study", "{1}{U}", action(arcanum.api.KeywordAction.Learn)),
        /** "Time travel." (CR 701.56a) */
        instant("Temporal Nudge", "{U}", action(arcanum.api.KeywordAction.TimeTravel)),
        /** "When this creature enters, it endures 2." (CR 701.63a) */
        card(FaceDefinition(name = "Enduring Sentinel", manaCost = ManaCost.parse("{1}{W}"), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Spirit")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1), abilities = listOf(
                Ability.Triggered(arcanum.api.Trigger.On(arcanum.api.EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = arcanum.api.Zone.Battlefield)),
                    action(arcanum.api.KeywordAction.Endure(Objects.Self, Value.Fixed(2))))))),
        /** "Prototype {1}{R} — 2/2" on a 6/6 artifact creature for {7} (CR 702.160a, 718) */
        card(FaceDefinition(name = "Smelter Colossus", manaCost = ManaCost.parse("{7}"), types = listOf(CardType.Artifact, CardType.Creature),
            subtypes = listOf(Subtype("Construct")), power = PrintedNumber.Fixed(6), toughness = PrintedNumber.Fixed(6),
            abilities = listOf(arcanum.api.Keyword.Prototype(arcanum.api.Cost.mana(ManaCost.parse("{1}{R}")!!), 2, 2)))),
        /** "Partner with Bonded Finder" (CR 702.124j; the parser doesn't read partner with yet) */
        partner("Bonded Seeker", "Bonded Finder"),
        /** "Partner with Bonded Seeker" */
        partner("Bonded Finder", "Bonded Seeker"),
    )

    private fun action(action: arcanum.api.KeywordAction) = arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.Action(action))))
    private fun sorcery(name: String, cost: String, instructions: arcanum.api.Instructions) = spell(name, cost, CardType.Sorcery, instructions)
    private fun instant(name: String, cost: String, instructions: arcanum.api.Instructions) = spell(name, cost, CardType.Instant, instructions)
    private fun spell(name: String, cost: String, type: CardType, instructions: arcanum.api.Instructions) = card(FaceDefinition(name = name,
        manaCost = ManaCost.parse(cost), types = listOf(type), abilities = listOf(Ability.Spell(instructions))))

    private fun partner(name: String, with: String) = card(FaceDefinition(name = name, manaCost = ManaCost.parse("{1}{G}"),
        colorIndicator = null, types = listOf(CardType.Creature), subtypes = listOf(Subtype("Elf")), power = PrintedNumber.Fixed(2),
        toughness = PrintedNumber.Fixed(2), abilities = listOf(arcanum.api.Keyword.PartnerWith(with))))
}
