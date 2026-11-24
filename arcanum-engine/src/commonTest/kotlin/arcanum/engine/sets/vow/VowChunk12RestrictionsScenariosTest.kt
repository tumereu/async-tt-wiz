package arcanum.engine.sets.vow

import arcanum.api.Color
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** VOW chunk 12: counters counted from other nontoken Humans of the controller only (three players), an activation only with a card
 * in your own exile, a spell that targets only attackers and blockers, one permanent's two abilities sharing its tapped state, mana
 * spendable only on Vampire spells with a cost reduced per Vampire, a creature returned from the graveyard by sacrificing two Blood
 * tokens, a once-each-turn Blood trigger, a cost reduction that depends on a Zombie, and the cost exiling only your own creature card
 * (the audit's unsure line was a false alarm). */
class VowChunk12RestrictionsScenariosTest {
    private val vanguard = Cards.invent("Thornhedge Vanguard", "{2}{G}", "Creature — Human Warrior", """
        Ward {2}
        This creature enters with two +1/+1 counters on it for each other nontoken Human you control.
    """.trimIndent(), "1/1")
    private val human = Cards.invent("Briar Villager", "{1}", "Creature — Human", "", "1/1")
    private val recruiter = Cards.invent("Briar Recruiter", "{1}{W}", "Creature — Human", "When this creature enters, create a 1/1 white Human creature token.", "1/1")
    private val monstrosity = Cards.invent("Gloomtide Monstrosity", "{4}{U}{U}", "Creature — Crab Horror", """
        Ward {2}
        {U}: This creature can't be blocked this turn. Activate only if you own a card in exile.
    """.trimIndent(), "5/5")
    private val needle = Cards.invent("Needle of Dawn", "{W}", "Instant",
        "Needle of Dawn deals 2 damage to target attacking or blocking creature. Scry 1.")
    private val keepsake = Cards.invent("Venerated Keepsake", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        {2}, {T}: Exile target card from a graveyard.
    """.trimIndent())
    private val manor = Cards.invent("Crimson Manor", null, "Land", """
        {T}: Add {C}.
        {T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast a Vampire spell.
        {5}, {T}: Create a Blood token. This ability costs {1} less to activate for each Vampire you control.
    """.trimIndent())
    private val bat = Cards.invent("Vesper Bat", "{R}", "Creature — Vampire", "", "1/1")
    private val ogre = Cards.invent("Mossback Ogre", "{R}", "Creature — Ogre", "", "2/3")
    private val forebear = Cards.invent("Ravenmoor Forebear", "{2}{B}", "Creature — Vampire", """
        Flying
        This creature can't block.
        {B}, Sacrifice two Blood tokens: Return this card from your graveyard to the battlefield.
    """.trimIndent(), "3/1")
    private val font = Cards.invent("Scarlet Font", "{B}", "Artifact", "When this artifact enters, create two Blood tokens.")
    private val mesmerist = Cards.invent("Gore Mesmerist", "{2}{R}", "Creature — Vampire", """
        This creature can't block.
        Whenever you sacrifice one or more Blood tokens, target creature can't block this turn. This ability triggers only once each turn.
    """.trimIndent(), "3/3")
    private val frost = Cards.invent("Frost of the Barrow", "{2}{U}", "Instant", """
        This spell costs {1} less to cast if you control a Zombie.
        Tap target creature. It doesn't untap during its controller's next untap step.
        Draw a card.
    """.trimIndent())
    private val zombie = Cards.invent("Cairn Zombie", "{B}", "Creature — Zombie", "", "2/2")
    private val boar = Cards.invent("Gorse Boar", "{2}{G}", "Creature — Boar", "", "4/4")
    private val stitched = Cards.invent("Stitched Lancer", "{U}", "Creature — Zombie Horse", """
        As an additional cost to cast this spell, exile a creature card from your graveyard.
        {3}{U}, Exile this card from your graveyard: Draw a card.
    """.trimIndent(), "3/3")

    private fun Scenario.count(player: arcanum.engine.scenario.ScenarioPlayer, name: String) = player.battlefield.names.count { it == name }
    private fun Scenario.canActivate(player: arcanum.engine.scenario.ScenarioPlayer, name: String, vararg more: String) =
        Engine.options(state, player.id).filterIsInstance<Input.Activate>().any { it.source == card(name) }
    private fun Scenario.canCast(player: arcanum.engine.scenario.ScenarioPlayer, name: String) =
        Engine.options(state, player.id).filterIsInstance<Input.Cast>().any { it.card == card(name) }

    // CR 614.1c, 122.1, 111.1: two counters per other nontoken Human its controller controls (a Human token and the opponents' Humans don't count)
    @Test
    fun countersForOtherNontokenHumansYouControlOnly() = scenario(players = 3) {
        alice.battlefield(human, human, "Plains", "Plains", "Forest", "Forest", "Forest")
        alice.hand(recruiter, vanguard)
        bob.battlefield(human, human, human)
        carol.battlefield(human)
        alice.casts(recruiter)
        resolveAll()
        expect { count(alice, "Human Token") shouldBe 1 }
        // three other nontoken Humans (two Villagers and the Recruiter) make six counters; the token and the opponents' Humans add none
        alice.casts(vanguard)
        resolveAll()
        expect {
            "Thornhedge Vanguard".power shouldBe 7
            "Thornhedge Vanguard".toughness shouldBe 7
        }
    }

    // CR 602.5b: only with a card in your own exile; a card in an opponent's exile doesn't count, a card in yours does
    @Test
    fun monstrosityNeedsACardInYourOwnExile() = scenario(players = 3) {
        alice.battlefield(monstrosity, "Island")
        bob.battlefield(boar)
        bob.exile("Island")
        assertTrue(!canActivate(alice, "Gloomtide Monstrosity"), "a card in an opponent's exile isn't enough")
        alice.exile("Island")
        assertTrue(canActivate(alice, "Gloomtide Monstrosity"))
        alice.activates(monstrosity)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(monstrosity)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(boar to monstrosity) }
    }

    // CR 506.4, 115.1: only an attacking or blocking creature is a legal target; the damage kills a 2/2 attacker and Scry 1 follows
    @Test
    fun needleHitsAnAttackerOnly() = scenario {
        alice.battlefield(ogre, bat)
        bob.battlefield("Plains")
        bob.hand(needle)
        bob.library("Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bat)
        resolveAll()
        alice.passes()
        bob.casts(needle, target = bat)
        resolveAll()
        playOut()
        expect {
            alice.graveyard has bat
            alice.battlefield has ogre
        }
    }

    @Test
    fun needleCantTargetACreatureThatIsNeitherAttackingNorBlocking() = scenario {
        alice.battlefield(ogre, bat)
        bob.battlefield("Plains")
        bob.hand(needle)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bat)
        resolveAll()
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(needle, target = ogre) }
    }

    // CR 602.2, 106.1b, 701.13: two abilities of one artifact both cost {T}: tapping for mana leaves no exile
    @Test
    fun keepsakeTapsForManaOrExilesNotBoth() = scenario {
        alice.battlefield(keepsake, "Island", "Island")
        bob.graveyard("Swamp")
        alice.activates(keepsake)
        assertTrue(!canActivate(alice, "Venerated Keepsake"), "it is tapped: neither ability can be activated")
    }

    @Test
    fun keepsakeExilesACardFromAnyGraveyard() = scenario(players = 3) {
        alice.battlefield(keepsake, "Island", "Island")
        carol.graveyard(ogre)
        alice.activates(keepsake, ability = 1, target = ogre)
        resolveAll()
        expect {
            carol.exile has ogre
            carol.graveyard lacks ogre
            "Venerated Keepsake".tapped shouldBe true
        }
    }

    // CR 106.6: mana from the Manor's second ability pays a Vampire spell but not another spell
    @Test
    fun manorsManaPaysOnlyForVampireSpells() = scenario {
        alice.battlefield(manor)
        alice.hand(bat, ogre)
        alice.activates(manor, ability = 0, color = Color.Red)
        assertTrue(!canCast(alice, "Mossback Ogre"), "a restricted mana can't pay for a non-Vampire")
        assertTrue(!canCast(alice, "Vesper Bat"), "the land is already tapped")
    }

    @Test
    fun manorsColoredManaCastsAVampireForOneLife() = scenario {
        alice.battlefield(manor)
        alice.hand(bat, ogre)
        alice.activates(manor, ability = 1, color = Color.Red)
        expect { alice.life shouldBe 19 }
        assertTrue(!canCast(alice, "Mossback Ogre"), "a restricted mana can't pay for a non-Vampire")
        alice.casts(bat)
        resolveAll()
        expect { alice.battlefield has bat }
    }

    // CR 602.2b, 601.2f: {5} minus one per Vampire: two Vampires leave {3}
    @Test
    fun manorsBloodAbilityCostsOneLessPerVampire() = scenario {
        alice.battlefield(manor, bat, bat, "Swamp", "Swamp", "Swamp")
        alice.activates(manor, ability = 2)
        resolveAll()
        expect { count(alice, "Blood Token") shouldBe 1 }
    }

    @Test
    fun manorsBloodAbilityNeedsTwoLessForTwoVampiresOnly() = scenario {
        alice.battlefield(manor, bat, "Swamp", "Swamp", "Swamp")
        alice.activates(manor, ability = 2)
        assertEquals("{4}", decision<Decision.ManaPayment>().cost.toString(), "one Vampire: {5} less 1")
    }

    // CR 113.6m, 400.7: the Forebear works from the graveyard; sacrificing two Blood tokens is a cost, and it returns to the battlefield
    @Test
    fun forebearReturnsFromTheGraveyardForTwoBloodTokens() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(font)
        alice.graveyard(forebear)
        alice.casts(font)
        resolveAll()
        expect { count(alice, "Blood Token") shouldBe 2 }
        alice.activates(forebear, choosing = listOf("Blood Token", "Blood Token"))
        resolveAll()
        expect {
            alice.battlefield has forebear
            alice.graveyard lacks forebear
            count(alice, "Blood Token") shouldBe 0
        }
    }

    @Test
    fun forebearNeedsTwoBloodTokens() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(forebear)
        assertTrue(!canActivate(alice, "Ravenmoor Forebear"))
    }

    // CR 509.1a: it can't block while a creature next to it can
    @Test
    fun forebearCantBlock() = scenario {
        alice.battlefield(boar)
        bob.battlefield(forebear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(boar)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(forebear to boar) }
        bob.blocks(ogre to boar)
    }

    // CR 603.2c, 603.4: sacrificing two Blood tokens in one turn triggers once; the target creature can't block this turn
    @Test
    fun mesmeristTriggersOncePerTurn() = scenario(players = 3) {
        alice.battlefield(mesmerist, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(font, "Island", "Island")
        alice.library("Forest", "Forest", "Forest")
        bob.battlefield(ogre)
        alice.casts(font)
        resolveAll()
        alice.activates("Blood Token", choosing = listOf("Island"))
        targets(ogre)
        resolveAll()
        assertEquals(0, state.stack.size)
        alice.activates("Blood Token", choosing = listOf("Island"))
        assertEquals(1, state.stack.size, "only the Blood token's own ability: no second trigger this turn")
        resolveAll()
        assertTrue(canBlock(), "bob's Ogre was told it can't block")
    }

    private fun Scenario.canBlock(): Boolean {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mesmerist)
        advanceTo(TurnStep.DeclareBlockers)
        return runCatching { bob.blocks(ogre to mesmerist) }.isFailure
    }

    // CR 601.2f: {1} less with a Zombie: {1}{U} is enough only then; CR 502.3: the tapped creature stays tapped through its controller's untap step
    @Test
    fun frostCostsOneLessWithAZombie() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(frost)
        assertTrue(!canCast(alice, "Frost of the Barrow"), "{2}{U} needs three mana")
        alice.battlefield(zombie)
        assertTrue(canCast(alice, "Frost of the Barrow"))
    }

    @Test
    fun frostKeepsItTappedThroughTheNextUntapStep() = scenario(players = 3) {
        alice.battlefield("Island", "Island", zombie)
        alice.hand(frost)
        alice.library("Island", "Island")
        bob.battlefield(boar)
        alice.casts(frost, target = boar)
        resolveAll()
        expect {
            "Gorse Boar".tapped shouldBe true
            alice.hand.size shouldBe 1
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Gorse Boar".tapped shouldBe true }
    }

    // CR 118.8, 601.2b: the creature card to exile comes from your own graveyard only
    @Test
    fun lancerExilesOnlyYourOwnCreatureCard() = scenario(players = 3) {
        alice.battlefield("Island")
        alice.hand(stitched)
        bob.graveyard(ogre)
        carol.graveyard(boar)
        assertTrue(!canCast(alice, "Stitched Lancer"), "other players' graveyards don't pay")
        alice.graveyard(zombie)
        alice.casts(stitched)
        resolveAll()
        expect {
            alice.battlefield has stitched
            alice.exile has zombie
            bob.graveyard has ogre
            carol.graveyard has boar
        }
    }
}
