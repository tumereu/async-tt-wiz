package arcanum.engine.sets.rvr

import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered chunk 06 in play: a token-replacing enchantment that only changes its controller's creature tokens, and an
 * enchantment that enters as a copy of another enchantment. Guildgates, shock lands, Maze's End, Pithing Needle and Prime Speaker Zegana
 * have the same shapes as cards already played elsewhere. */
class RvrChunk06ReplacementScenariosTest {
    private val calling = Cards.invent("Heavenly Calling", "{3}{W}{W}", "Enchantment",
        "If one or more creature tokens would be created under your control, that many 4/4 white Angel creature tokens with flying and vigilance are created instead.")
    private val muster = Cards.invent("Sudden Muster", "{1}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val trinkets = Cards.invent("Quiet Trinkets", "{1}{R}", "Sorcery", "Create two Treasure tokens.")
    private val mimic = Cards.invent("Mimic Veil", "{2}{U}", "Enchantment", "You may have this enchantment enter as a copy of any enchantment on the battlefield.")

    private fun arcanum.engine.scenario.Scenario.tokenSubtypes() =
        state.battlefield.filter { state.obj(it)?.token == true }.map { view()[it]?.characteristics?.subtypes?.map { s -> s.name } }

    /** Two Soldier tokens would be created under your control: two 4/4 flying vigilance Angels instead. */
    // CR 614.1a, 111.1
    @Test
    fun creatureTokensBecomeAngels() = scenario {
        alice.battlefield(calling, "Plains", "Plains")
        alice.hand(muster)
        alice.casts(muster)
        playOut()
        assertEquals(listOf(listOf("Angel"), listOf("Angel")), tokenSubtypes())
        val angel = state.battlefield.first { state.obj(it)?.token == true }
        val c = view()[angel]!!.characteristics!!
        assertEquals(4, c.power)
        assertEquals(4, c.toughness)
        expect {
            card("Angel Token") hasKeyword Keyword.Flying
            card("Angel Token") hasKeyword Keyword.Vigilance
        }
    }

    /** Only creature tokens are replaced: Treasures are untouched. */
    // CR 614.1a, 111.1
    @Test
    fun treasuresAreNotReplaced() = scenario(players = 3) {
        alice.battlefield(calling, "Mountain", "Mountain")
        alice.hand(trinkets)
        carol.battlefield(calling)
        alice.casts(trinkets)
        playOut()
        val names = tokenSubtypes()
        assertEquals(2, names.count { it == listOf("Treasure") })
        assertEquals(0, names.count { it == listOf("Angel") })
    }

    /** It enters as a copy of an enchantment on the battlefield, so it has that enchantment's ability. */
    // CR 707.2, 614.1c
    @Test
    fun enchantmentEntersAsACopyOfAnotherEnchantment() = scenario {
        alice.hand(mimic)
        alice.battlefield("Island", "Island", "Island")
        bob.battlefield(calling)
        alice.casts(mimic)
        playOut(calling)
        expect { card(mimic).view.name shouldBe calling }
    }

    /** Another player's Divine Visitation only changes tokens under its controller's control: the caster's Soldiers stay Soldiers. */
    // CR 614.1a
    @Test
    fun anotherPlayersReplacementDoesNotApply() = scenario(players = 3) {
        bob.battlefield(calling)
        carol.battlefield(calling)
        alice.battlefield("Plains", "Plains")
        alice.hand(muster)
        alice.casts(muster)
        playOut()
        assertEquals(listOf(listOf("Soldier"), listOf("Soldier")), tokenSubtypes())
    }
}
