package arcanum.engine.view

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
     * "Other creatures you control get +1/+1." wording: the parser doesn't read "are copies of" yet) */
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
}
