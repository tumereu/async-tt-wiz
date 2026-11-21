package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** [SubtypeReferences]: the subtypes invented cards' abilities pick objects by (wording from real oracle templates). */
class SubtypeReferencesTest {
    private fun n(value: Int) = Value.Fixed(value)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private fun sub(name: String) = ObjectFilter.Sub(Subtype(name))
    private fun all(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList())
    private fun literal(vararg names: String): Set<SubtypeRef> = names.mapTo(LinkedHashSet()) { SubtypeRef.Literal(Subtype(it)) }
    private fun lizard(vararg abilities: Ability) = FaceDefinition("Dunescale Chief", ManaCost.parse("{1}{R}"),
        types = listOf(CardType.Creature), subtypes = listOf(Subtype("Lizard"), Subtype("Warrior")),
        power = PrintedNumber.Fixed(2), toughness = PrintedNumber.Fixed(2), abilities = abilities.toList())

    /** "This creature gets +1/+1 for each [filter] you control." */
    private fun forEach(filter: ObjectFilter) = Ability.Static(listOf(Mechanic.ModifyPowerToughness(
        Value.Count(Objects.Each(all(filter, youControl))), Value.Count(Objects.Each(all(filter, youControl))))),
        Affected.Objects(Objects.Self))

    @Test fun aCountedSubtypeIsReferred() {
        assertEquals(literal("Lizard"), SubtypeReferences.of(lizard(forEach(sub("Lizard")))))
    }

    @Test fun aSaddledMountIsAMountReference() {
        // "This creature gets +1/+1 for each saddled Mount you control.": the Mount is referred to whether any is saddled or not
        val saddled = all(sub("Mount"), ObjectFilter.HasStatus(Status.Marked(ObjectMark.Saddled)))
        assertEquals(literal("Mount"), SubtypeReferences.of(lizard(forEach(saddled))))
    }

    @Test fun theCardsOwnTypeLineAndTheTokensItMakesAreNoReferences() {
        // "When this creature enters, create a 1/1 red Lizard creature token."
        val token = TokenSpec.Defined(FaceDefinition("Lizard Token", colorIndicator = listOf(Color.Red), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Lizard")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1)))
        val makes = Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            Instructions(steps = listOf(Step.Do(Effect.CreateToken(token, n(1), Players.You)))))
        assertEquals(emptySet(), SubtypeReferences.of(lizard()))
        assertEquals(emptySet(), SubtypeReferences.of(lizard(makes)))
    }

    @Test fun nestedPartsAndEveryFaceCount() {
        // "Other Lizards and Equipment you control get +1/+1." on one face, "Enchant Forest" on the other
        val anthem = Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(1))), Affected.Objects(Objects.Each(all(
            ObjectFilter.Or(listOf(sub("Lizard"), sub("Equipment"))), youControl, ObjectFilter.Not(ObjectFilter.Is(Objects.Self))))))
        val enchant = Keyword.Enchant(TargetKind.Objects(sub("Forest")))
        val back = FaceDefinition("Grove Ward", types = listOf(CardType.Enchantment), subtypes = listOf(Subtype("Aura")), abilities = listOf(enchant))
        val definition = CardDefinition(Layout.Transform, listOf(lizard(anthem), back))
        assertEquals(literal("Lizard", "Equipment", "Forest"), SubtypeReferences.of(definition))
    }

    @Test fun aKeywordsExpansionCounts() {
        // CR 702.173a: freerunning reads "was an Assassin or a commander" only in its expansion
        val freerunning = Keyword.Freerunning(Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{B}")!!))))
        assertTrue(SubtypeRef.Literal(Subtype("Assassin")) in SubtypeReferences.of(freerunning))
    }

    @Test fun theChosenTypeIsARememberedReference() {
        // "Creatures you control of the chosen type get +1/+1."
        val chosen = Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(1), n(1))), Affected.Objects(Objects.Each(all(
            ObjectFilter.Type(CardType.Creature), youControl, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))))
        assertEquals(setOf<SubtypeRef>(SubtypeRef.Remembered("type")), SubtypeReferences.of(chosen))
    }

    @Test fun theReferencesAreNoPartOfTheDefinitionsJson() {
        val face = lizard(forEach(sub("Lizard")))
        face.referencedSubtypes
        val text = Json.encodeToString(FaceDefinition.serializer(), face)
        assertFalse("referencedSubtypes" in text, text)
        assertEquals(face, Json.decodeFromString(FaceDefinition.serializer(), text))
    }
}
