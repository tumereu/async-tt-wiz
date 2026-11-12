package arcanum.engine.view

import arcanum.engine.state.fixedCopies
import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Copy effects of static abilities (CR 613.2a, 707.2): applied in layer 1 to what the ability affects, while it functions. */
class StaticCopyTest {
    /** "Enchant creature / Other creatures you control are copies of enchanted creature." (written by hand from the parsed
     * "Other creatures you control get +1/+1." wording; `casting/ReadLaneWordingsScenariosTest` plays the parsed wording) */
    private val charm = run {
        val base = Cards.definition(Cards.invent("Mirrorlink Charm Base", "{2}{U}", "Enchantment — Aura",
            "Enchant creature\nOther creatures you control get +1/+1."))
        val face = base.faces.single()
        val abilities = face.abilities.map { a ->
            if (a is Ability.Static) a.copy(mechanics = listOf(Mechanic.CopyOf(Objects.AttachedTo(Objects.Self)))) else a
        }
        Cards.define("Mirrorlink Charm", CardDefinition(base.layout, listOf(face.copy(name = "Mirrorlink Charm", abilities = abilities))))
    }
    private val bear = Cards.invent("Mirrorlink Bear", "{1}{G}", "Creature — Bear", "Vigilance", "2/2")
    private val ox = Cards.invent("Mirrorlink Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    // CR 613.2a, 707.2: the other creature has the enchanted creature's copiable values: name, P/T, abilities
    @Test
    fun othersAreCopies() = scenario {
        alice.battlefield(bear, ox, charm)
        val oxId = card(ox)
        attach(charm, bear)
        val c = view()[oxId]?.characteristics
        assertEquals(bear, c?.name)
        assertEquals(2, c?.power)
        assertTrue(c?.hasKeyword(arcanum.api.Keyword.Vigilance) == true)
        // the enchanted creature itself is a creature you control too, a copy of itself
        assertEquals(bear, view()[card(bear)]?.characteristics?.name)
    }

    // CR 611.3a: a static ability's effect ends once it stops functioning: the Aura gone, the Ox is itself again
    @Test
    fun endsWithTheAbility() = scenario {
        alice.battlefield(bear, ox, charm)
        attach(charm, bear)
        val oxId = card(ox)
        assertEquals(bear, view()[oxId]?.characteristics?.name)
        move(charm, Zone.Graveyard)
        assertEquals(ox, view()[oxId]?.characteristics?.name)
        assertEquals(3, view()[oxId]?.characteristics?.power)
    }

    /** A sorcery: "Turn target creature face down." */
    private val shrouding = Cards.define("Mirrorlink Shroud", CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Mirrorlink Shroud",
        types = listOf(arcanum.api.CardType.Sorcery), abilities = listOf(Ability.Spell(arcanum.api.Instructions(
            targets = listOf(arcanum.api.TargetSlot("creature", arcanum.api.TargetKind.Objects(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Creature)),
                arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(1))),
            steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.TurnFaceDown(Objects.Target("creature")))))))))))

    // CR 707.2c: a static copy effect's values are fixed as it first applies: the enchanted creature turned face down later, the Ox
    // stays a copy of the Bear as it was
    @Test
    fun theCopiedValuesAreFixedAsItFirstApplies() = scenario {
        alice.battlefield(bear, ox, charm)
        alice.hand(shrouding)
        attach(charm, bear)
        settle()
        val oxId = card(ox)
        assertEquals(1, state.objOrThrow(oxId).fixedCopies.size)
        alice.castsWithoutCosts(shrouding, target = bear)
        resolve()
        assertTrue(state.objOrThrow(card(bear)).faceDown)
        val c = view()[oxId]?.characteristics
        assertEquals(bear, c?.name)
        assertTrue(c?.hasKeyword(arcanum.api.Keyword.Vigilance) == true)
    }

    // CR 707.2c, 611.3a: once the effect no longer applies, what it fixed goes; applying again fixes the values anew
    @Test
    fun theFixedValuesGoWithTheEffect() = scenario {
        alice.battlefield(bear, ox, charm)
        attach(charm, bear)
        settle()
        val oxId = card(ox)
        move(charm, Zone.Graveyard)
        settle()
        assertTrue(state.objOrThrow(oxId).fixedCopies.isEmpty())
        assertEquals(ox, view()[oxId]?.characteristics?.name)
    }
}
