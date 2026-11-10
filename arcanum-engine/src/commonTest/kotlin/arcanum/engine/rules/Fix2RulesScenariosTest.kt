package arcanum.engine.rules

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rules fixes (fix2): permanents put onto the battlefield attached to something, and Aura tokens created by effects. */
class Fix2RulesScenariosTest {
    private val wisp = Cards.invent("Wandering Wisp", "{1}{W}", "Creature — Spirit", """
        {3}{W}: Return this card from your graveyard to the battlefield attached to target creature. Activate only as a sorcery.
    """, "2/2")
    private val blade = Cards.invent("Hollow Blade", "{2}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        {3}: Return this card from your graveyard to the battlefield attached to target land you control. Activate only as a sorcery.
    """)
    private val lance = Cards.invent("Hollow Lance", "{2}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        {3}: Return this card from your graveyard to the battlefield attached to target creature you control. Activate only as a sorcery.
    """)
    private val mantle = Cards.invent("Gleaming Mantle", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
    """)
    private val lonely = Cards.invent("Solitary Mantle", "{1}{W}", "Enchantment — Aura", """
        Enchant creature you control
        Enchanted creature gets +2/+2.
    """)
    private val hex = Cards.invent("Watchful Hex", "{1}{B}", "Enchantment — Aura", "Enchant player")
    private val mirror = Cards.invent("Echoing Mirror", "{2}{U}", "Sorcery", "Create a token that's a copy of target enchantment.")
    private val glade = Cards.invent("Quiet Glade", null, "Land", "")
    private val dune = Cards.invent("Dune Grazer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val ridge = Cards.invent("Ridge Grazer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val marsh = Cards.invent("Marsh Grazer", "{1}{G}", "Creature — Ox", "", "2/2")

    private fun Scenario.attachedChanges() = applied.filterIsInstance<Change.Attached>()
    private fun Scenario.tokens() = state.battlefield.filter { state.obj(it)?.token == true }

    // CR 303.4h: a permanent that isn't an Aura, Equipment or Fortification enters unattached (it is never attached, not
    // attached and then unattached by a state-based action)
    @Test
    fun creatureReturnedAttachedEntersUnattached() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        bob.battlefield(ridge)
        alice.graveyard(wisp)
        alice.activates(wisp, ability = 0, target = ridge)
        resolveAll()
        expect { wisp.zone shouldBe Zone.Battlefield }
        assertNull(state.objOrThrow(card(wisp, zone = Zone.Battlefield)).attachedTo)
        assertTrue(attachedChanges().isEmpty(), "nothing was ever attached: ${attachedChanges()}")
    }

    // CR 301.5e: an Equipment put onto the battlefield attached to an object it can't legally equip (a land) enters unattached
    @Test
    fun equipmentReturnedOntoALandEntersUnattached() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", glade)
        alice.graveyard(blade)
        alice.activates(blade, ability = 0, target = glade)
        resolveAll()
        expect { blade.zone shouldBe Zone.Battlefield }
        assertNull(state.objOrThrow(card(blade, zone = Zone.Battlefield)).attachedTo)
        assertTrue(attachedChanges().isEmpty(), "never attached: ${attachedChanges()}")
    }

    // CR 301.5e, 301.5b: an Equipment put onto the battlefield attached to a creature it can equip enters attached
    @Test
    fun equipmentReturnedOntoACreatureEntersAttached() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", ridge)
        alice.graveyard(lance)
        alice.activates(lance, ability = 0, target = ridge)
        resolveAll()
        assertEquals(TargetRef.Object(card(ridge)), state.objOrThrow(card(lance, zone = Zone.Battlefield)).attachedTo)
        expect { ridge.power shouldBe 4 }
    }

    // CR 303.4f, 111.1: an Aura token created without a named host is attached to what its controller chooses as it enters,
    // among other players' permanents too (not targeting)
    @Test
    fun auraTokenChoosesWhatItEnchants() = scenario(players = 3) {
        alice.battlefield(mantle)
        alice.hand(mirror)
        alice.battlefield(dune)
        bob.battlefield(ridge)
        carol.battlefield(marsh)
        attach(mantle, dune)
        alice.castsWithoutCosts(mirror, target = mantle)
        resolve()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, choice.player)
        assertEquals(setOf(card(dune), card(ridge), card(marsh)), choice.options.toSet())
        answer(Answer.Objects(listOf(card(marsh))))
        val token = tokens().single()
        assertEquals(TargetRef.Object(card(marsh)), state.objOrThrow(token).attachedTo)
        expect { marsh.power shouldBe 4 }
    }

    // CR 303.4f: an Aura token that enchants a player chooses among the players in the game
    @Test
    fun playerAuraTokenChoosesAPlayer() = scenario(players = 3) {
        alice.battlefield(hex)
        alice.hand(mirror)
        attach(hex, "alice")
        alice.castsWithoutCosts(mirror, target = hex)
        resolve()
        val choice = decision<Decision.ChoosePlayer>()
        assertEquals(players.map { it.id }.toSet(), choice.options.toSet())
        answer(Answer.Player(bob.id))
        assertEquals(TargetRef.Player(bob.id), state.objOrThrow(tokens().single()).attachedTo)
    }

    // CR 303.4g: with nothing legal to enchant ("you control" and no creature of the controller), the Aura token isn't created
    @Test
    fun auraTokenWithNothingToEnchantIsNotCreated() = scenario(players = 3) {
        alice.battlefield(lonely, dune)
        attach(lonely, dune)
        bob.hand(mirror)
        carol.battlefield(marsh)
        bob.castsWithoutCosts(mirror, target = lonely)
        resolve()
        assertNull(pending)
        assertTrue(tokens().isEmpty(), "no token: ${tokens()}")
        assertTrue(applied.none { it is Change.Created }, "no Created event")
    }
}
