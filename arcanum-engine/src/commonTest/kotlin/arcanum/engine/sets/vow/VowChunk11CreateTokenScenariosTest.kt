package arcanum.engine.sets.vow

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.hits
import kotlin.test.Test
import kotlin.test.assertTrue

/** VOW chunk 11: the Blood and creature tokens. Covered here: a token copy of a nontoken Zombie only, X twice the Blood tokens counted
 * on resolution with a sorcery-speed timing, exiling three cards from your own graveyard only (the audit's "no owned-by" was a
 * false alarm), a token as big as the mana spent on a noncreature spell that is exiled at the next end step, an opponent's spell giving
 * that opponent a Blood token (three players), and "that many" Blood tokens for damage dealt. */
class VowChunk11CreateTokenScenariosTest {
    private val necroduality = Cards.invent("Reanimated Duality", "{3}{U}", "Enchantment",
        "Whenever a nontoken Zombie you control enters, create a token that's a copy of that creature.")
    private val shambler = Cards.invent("Mire Shambler", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val lurker = Cards.invent("Mire Lurker", "{1}{B}", "Creature — Zombie", "Flash", "2/2")
    private val squire = Cards.invent("Mire Squire", "{1}{B}", "Creature — Human", "", "2/2")
    private val font = Cards.invent("Crimson Font", "{B}", "Artifact", "When this artifact enters, create two Blood tokens.")
    private val harvester = Cards.invent("Sanguine Harvester", "{B}{R}", "Creature — Vampire",
        "{T}, Sacrifice this creature: Target creature gets -X/-X until end of turn, where X is twice the number of Blood tokens you control. Activate only as a sorcery.", "3/2")
    private val wolfrider = Cards.invent("Moorland Wolfrider", "{R}", "Creature — Human Knight",
        "{2}{R}, {T}, Exile three cards from your graveyard: Create a 3/2 red Wolf creature token.", "1/2")
    private val hellkite = Cards.invent("Spellform Hellkite", "{2}{R}{R}", "Creature — Dragon", """
        Flying
        Whenever you cast a noncreature spell, create an X/X red Dragon Illusion creature token with flying and haste, where X is the amount of mana spent to cast that spell. Exile that token at the beginning of the next end step.
    """.trimIndent(), "4/4")
    private val bolt = Cards.invent("Searing Gust", "{2}{R}", "Instant", "Searing Gust deals 1 damage to any target.")
    private val ogre = Cards.invent("Hollow Ogre", "{2}{R}", "Creature — Ogre", "", "2/3")
    private val purveyor = Cards.invent("Vial Purveyor", "{2}{B}{B}", "Creature — Vampire", """
        Flying, trample
        Whenever an opponent casts a spell, that player creates a Blood token.
        Whenever this creature attacks, it gets +1/+0 until end of turn for each Blood token defending player controls.
    """.trimIndent(), "5/6")
    private val attendants = Cards.invent("Olive's Attendants", "{4}{R}{R}", "Creature — Vampire", """
        Menace
        Whenever this creature deals damage, create that many Blood tokens.
        {2}{R}: This creature deals 1 damage to any target.
    """.trimIndent(), "6/6")
    private val boar = Cards.invent("Quillback Boar", "{2}{G}", "Creature — Boar", "", "4/4")
    private val whisper = Cards.invent("Idle Whisper", "{U}", "Instant", "Draw a card.")

    private fun Scenario.count(player: arcanum.engine.scenario.ScenarioPlayer, name: String) = player.battlefield.names.count { it == name }

    // CR 603.2, 707.2, 111.10: the copy is a token, so it doesn't trigger again; a non-Zombie and an opponent's Zombie don't trigger it
    @Test
    fun onlyYourNontokenZombiesAreCopied() = scenario(players = 3) {
        alice.battlefield(necroduality, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shambler, squire)
        bob.hand(lurker)
        bob.battlefield("Swamp", "Swamp")
        alice.casts(squire)
        resolveAll()
        expect { count(alice, squire) shouldBe 1 }
        alice.casts(shambler)
        resolveAll()
        expect {
            count(alice, shambler) shouldBe 2
        }
        alice.passes()
        bob.casts(lurker)
        resolveAll()
        expect {
            count(bob, lurker) shouldBe 1
            count(alice, shambler) shouldBe 2
            count(alice, lurker) shouldBe 0
        }
    }

    // CR 107.3, 202.1: X is twice the Blood tokens its controller has when the ability resolves; CR 701.21 sacrificing it is a cost
    @Test
    fun theHarvesterShrinksTwiceTheBloodTokens() = scenario(players = 3) {
        alice.battlefield(harvester, "Swamp")
        alice.hand(font)
        bob.battlefield(boar)
        alice.casts(font)
        resolveAll()
        expect { count(alice, "Blood Token") shouldBe 2 }
        alice.activates(harvester, target = boar)
        resolveAll()
        expect {
            alice.graveyard has harvester
            bob.graveyard has boar
        }
    }

    // CR 602.5d: only as a sorcery: no activation in the declare attackers step
    @Test
    fun theHarvesterIsOnlyActivatedAtSorcerySpeed() = scenario {
        alice.battlefield(harvester, "Swamp")
        bob.battlefield(boar)
        turn(1, alice, TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card(harvester) })
    }

    // CR 118.8, 602.2: the three cards exiled for the cost come from the activator's own graveyard; the audit's "no owned-by" was a false alarm
    @Test
    fun cardsInOtherGraveyardsDontPayTheCost() = scenario(players = 3) {
        alice.battlefield(wolfrider, "Mountain", "Mountain", "Mountain")
        alice.graveyard("Island", "Island")
        bob.graveyard("Island", "Island", "Island")
        carol.graveyard("Island", "Island")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card(wolfrider) })
    }

    // CR 118.8, 111.1: three cards from her own graveyard make a 3/2 red Wolf; the other graveyards are untouched
    @Test
    fun threeOwnCardsMakeAWolf() = scenario(players = 3) {
        alice.battlefield(wolfrider, "Mountain", "Mountain", "Mountain")
        alice.graveyard("Island", "Island", "Island", "Forest")
        bob.graveyard("Island", "Island")
        alice.activates(wolfrider, choosing = listOf("Island", "Island", "Island"))
        resolveAll()
        val wolf = "Wolf Token"
        expect {
            alice.graveyard.size shouldBe 1
            bob.graveyard.size shouldBe 2
            wolf.power shouldBe 3
            wolf.toughness shouldBe 2
            wolf.colors shouldBe listOf(Color.Red)
            wolf.subtypes shouldBe listOf(Subtype("Wolf"))
        }
    }

    // CR 202.1, 601.2h: X is the mana spent, three for the three-mana spell; the token has haste and flying; CR 603.7: gone at the next end step
    @Test
    fun theDragonIsAsBigAsTheManaSpentAndLeavesAtTheEnd() = scenario {
        alice.battlefield(hellkite, "Mountain", "Mountain", "Mountain")
        alice.hand(bolt)
        bob.battlefield(boar)
        alice.casts(bolt, target = boar)
        resolveAll()
        val dragon = "Dragon Illusion Token"
        expect {
            dragon.power shouldBe 3
            dragon.toughness shouldBe 3
            dragon.colors shouldBe listOf(Color.Red)
            dragon.subtypes shouldBe listOf(Subtype("Dragon"), Subtype("Illusion"))
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { count(alice, "Dragon Illusion Token") shouldBe 0 }
    }

    // CR 601.2: a creature spell doesn't trigger it (noncreature only)
    @Test
    fun aCreatureSpellMakesNoDragon() = scenario {
        alice.battlefield(hellkite, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        resolveAll()
        expect { count(alice, "Dragon Illusion Token") shouldBe 0 }
    }

    // CR 603.2, 111.2: "that player creates": the opponent who cast gets the token, nobody else, and not for Alice's own spell
    @Test
    fun theOpponentWhoCastGetsTheBloodToken() = scenario(players = 3) {
        alice.battlefield(purveyor, "Island")
        alice.hand(whisper)
        alice.library("Island", "Island")
        bob.battlefield("Island")
        bob.hand(whisper)
        bob.library("Island", "Island")
        alice.casts(whisper)
        resolveAll()
        expect {
            count(alice, "Blood Token") shouldBe 0
            count(bob, "Blood Token") shouldBe 0
        }
        alice.passes()
        bob.casts(whisper)
        resolveAll()
        expect {
            count(bob, "Blood Token") shouldBe 1
            count(alice, "Blood Token") shouldBe 0
            count(carol, "Blood Token") shouldBe 0
        }
    }

    // CR 120.3, 603.2: the ability triggers once for damage dealt at the same time, and "that many" is the damage: 6 combat damage, 6 tokens
    @Test
    fun damageDealtMakesThatManyBloodTokens() = scenario {
        alice.battlefield(attendants)
        hits(attendants)
        expect { count(alice, "Blood Token") shouldBe 6 }
    }

    // CR 120.3: one damage from the activated ability is one token, whatever the source of the damage is aimed at
    @Test
    fun theActivatedPingMakesOneBloodToken() = scenario {
        alice.battlefield(attendants, "Mountain", "Mountain", "Mountain")
        alice.activates(attendants, target = bob.id.value)
        resolveAll()
        expect { count(alice, "Blood Token") shouldBe 1 }
    }
}
