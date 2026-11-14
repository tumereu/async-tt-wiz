package arcanum.engine.sets.otj

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** OTJ chunk 18: the granted "you" of a dies trigger on an opponent's creature. */
class OtjChunk18AuditScenariosTest {
    private val funeral = Cards.invent("Fake Your Own Funeral", "{1}{B}", "Instant", """
        Until end of turn, target creature gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control and you create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val ox = Cards.invent("Dusty Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val zap = Cards.invent("Dust Zap", "{R}", "Instant", "Dust Zap deals 3 damage to target creature.")

    // CR 113.8 (the granted ability is controlled by the creature's controller), 111.10b, 400.7: the Treasure goes to the opponent whose creature died, not the caster
    @Test
    fun treasureGoesToTheCreaturesControllerNotTheCaster() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand(funeral, zap)
        bob.battlefield(ox)
        alice.casts(funeral, target = ox)
        resolveAll()
        alice.casts(zap, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Battlefield
            ox.tapped shouldBe true
            bob.battlefield.names.count { it == "Treasure Token" } shouldBe 1
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 0
        }
    }
}
