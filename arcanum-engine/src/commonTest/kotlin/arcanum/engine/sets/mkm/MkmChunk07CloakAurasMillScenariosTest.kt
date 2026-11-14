package arcanum.engine.sets.mkm

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.ltr.hits
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.tdm.Plain
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM chunk 07: cloak (CR 701.58) from the top of a library (yours and a damaged opponent's), from a hand, from looked-at cards and from exile,
 * an Equipment attached to what it cloaked, a cloaked instant that can't turn face up cast from exile; Auras that suspect, forbid suspicion,
 * depend on what they enchant, target another creature, add mana, grant a trigger and shuffle the creature away; a reflexive trigger on a
 * fifth counter; mill. */
class MkmChunk07CloakAurasMillScenariosTest {
    private val mantle = Cards.invent("Veiled Mantle", "{2}{U}", "Artifact — Equipment", """
        When this Equipment enters, cloak the top card of your library, then attach this Equipment to it. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
        Equipped creature gets +1/+0 and can't be blocked.
        {1}{U}: Return this Equipment to its owner's hand.
    """)
    private val ysolde = Cards.invent("Ysolde, Shrouded Fugitive", "{1}{U}{B}", "Legendary Creature — Vampire Assassin", """
        Deathtouch
        Face-down creatures you control have "{2}{U}{B}: Turn this creature face up. If you can't, exile it, then you may cast the exiled card without paying its mana cost."
        Whenever an Assassin you control deals combat damage to an opponent, cloak the top card of that player's library.
    """, "1/4")
    private val unmask = Cards.invent("Unmask the Schemer", "{1}{R}", "Instant", """
        Choose one or both —
        • Turn target face-down creature face up.
        • Exile any number of face-up creatures you control with disguise in a face-down pile, shuffle that pile, then cloak them. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
    """)
    private val blend = Cards.invent("Blend into the Crowd", "{3}{G}", "Sorcery",
        "Look at the top five cards of your library, cloak two of them, and put the rest on the bottom of your library in a random order. (To cloak a card, put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)")
    private val oriel = Cards.invent("Oriel, Shifting Enigma", "{2}{G}{U}", "Legendary Creature — Elf Ooze Wizard", """
        At the beginning of combat on your turn, choose one —
        • Cloak a card from your hand. (Put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card.)
        • Put a +1/+1 counter on each colorless creature you control.
    """, "3/4")
    private val excuse = Cards.invent("Ironclad Excuse", "{2}{G}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, untap enchanted creature. It gains hexproof until end of turn. If it's suspected, it's no longer suspected.
        Enchanted creature gets +2/+2 and can't become suspected.
    """)
    private val weight = Cards.invent("Weight of Evidence", "{1}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets +2/+2 as long as it's a Detective you control. Otherwise, it has base power and toughness 1/1 and can't block Detectives.
    """)
    private val compelled = Cards.invent("Compelled to Murder", "{3}{U}{B}", "Enchantment — Aura", """
        Enchant creature
        You control enchanted creature.
        Enchanted creature has base power and toughness 1/1, has deathtouch, and is an Assassin in addition to its other types.
    """)
    private val scapegoat = Cards.invent("Handy Scapegoat", "{R}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, suspect enchanted creature. (It has menace and can't block.)
        Enchanted creature gets +1/+1.
        {2}{R}: Return this card from your graveyard to your hand.
    """)
    private val scrutiny = Cards.invent("Careful Scrutiny", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, target creature you control other than enchanted creature gets +2/+2 and gains vigilance until end of turn.
        Enchanted creature gets +2/+2 and has vigilance.
    """)
    private val roses = Cards.invent("Interred Beneath Roses", "{2}{G}{W}", "Enchantment — Aura", """
        Enchant land
        When this Aura enters, exile target nonland permanent you don't control until this Aura leaves the battlefield.
        Whenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.
    """)
    private val nosy = Cards.invent("Nosy Questioning", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 and has "Whenever this creature deals combat damage to a player, investigate." (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
    """)
    private val charge = Cards.invent("Theatrical Charge", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
        {U}{U}: Shuffle enchanted creature into its owner's library.
    """)
    private val gore = Cards.invent("Gore Pattern Study", "{B}{R}", "Enchantment", """
        When this enchantment enters, it deals 3 damage to target creature an opponent controls.
        Whenever one or more creatures die, mill a card and put a bloodstain counter on this enchantment. Then sacrifice it if it has five or more bloodstain counters on it. When you do, return target creature card from your graveyard to your hand.
    """)
    private val examiner = Cards.invent("Wreckage Examiner", "{1}{G}", "Creature — Elf Detective", """
        When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
        {3}{G}, Sacrifice this creature: Return all land cards from your graveyard to the battlefield tapped.
    """, "1/3")
    private val sleuth = Cards.invent("Ledger Sleuth", "{1}{U}", "Creature — Human Detective", "", "2/2")
    private val lurker = Cards.invent("Masked Lurker", "{1}{G}", "Creature — Human Rogue",
        "Disguise {2}{G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)", "3/3")
    private val blame = Cards.invent("Pin the Blame", "{R}", "Instant", "Suspect target creature. (It has menace and can't block.)")

    private val bloodstain = CounterKind.Named("bloodstain")
    private fun Scenario.faceDown(): List<ObjectId> = state.battlefield.filter { state.objOrThrow(it).faceDown }
    private fun Scenario.cloaked(id: ObjectId): Boolean = ObjectMark.Cloaked in state.objOrThrow(id).marks
    private fun Scenario.hasWard(id: ObjectId): Boolean = view()[id]!!.characteristics!!.abilities.any { it.ability is Keyword.Ward }
    private fun Scenario.suspected(name: String): Boolean = state.obj(card(name))?.marks?.contains(ObjectMark.Suspected) == true

    // CR 701.58a: the top card enters face down as a cloaked 2/2 with ward {2}; CR 701.3a: the Equipment is attached to it as the ability
    // resolves, so it is a 3/2
    @Test
    fun veiledMantleCloaksAndEquipsIt() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(mantle)
        alice.library(Plain.ogre, "Island")
        alice.casts(mantle)
        playOut()
        val id = faceDown().single()
        assertTrue(cloaked(id))
        assertTrue(hasWard(id))
        assertEquals(TargetRef.Object(id), state.objOrThrow(card(mantle)).attachedTo)
        assertEquals(3, view()[id]!!.characteristics!!.power)
        expect { alice.library.size shouldBe 1 }
    }

    // CR 701.58a, 102.2: the damaged opponent's top card is cloaked under Ysolde's controller's control, not the other opponent's; CR 701.58g: a
    // cloaked instant can't be turned face up, so the granted ability exiles it and its controller may cast it without paying its mana cost
    // (CR 608.2g), though another player owns it
    @Test
    fun ysoldeCloaksTheDamagedOpponentsCardAndCastsAnInstantThatCantTurnUp() = scenario(players = 3) {
        alice.battlefield(ysolde, "Island", "Island", "Swamp", "Swamp")
        alice.library("Island", "Island")
        bob.library(Plain.shock, "Island")
        carol.library(Plain.bear, "Island")
        hits(ysolde)
        val id = faceDown().single()
        assertTrue(cloaked(id))
        assertEquals(alice.id, state.objOrThrow(id).controller)
        assertEquals(bob.id, state.objOrThrow(id).owner)
        expect {
            bob.life shouldBe 19
            carol.library.size shouldBe 2
        }
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(Plain.shock)
        playOut("carol")
        expect {
            carol.life shouldBe 18
            bob.graveyard has Plain.shock
        }
    }

    // CR 701.58b, 708.8: a cloaked creature card is turned face up by the granted ability; nothing is exiled
    @Test
    fun ysoldesGrantedAbilityTurnsACreatureFaceUp() = scenario {
        alice.battlefield(ysolde, "Island", "Island", "Swamp", "Swamp")
        alice.library("Island", "Island")
        bob.library(Plain.ogre, "Island")
        hits(ysolde)
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(Plain.ogre)
        playOut()
        assertTrue(faceDown().isEmpty())
        expect {
            Plain.ogre.controller shouldBe alice.id
            Plain.ogre.power shouldBe 3
            Plain.ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 708.8: the first mode turns a face-down creature face up; CR 701.58a, e: the second exiles the face-up creature with disguise and cloaks
    // it, leaving the other creature
    @Test
    fun unmaskTheSchemerTurnsOneUpAndCloaksTheDisguised() = scenario {
        alice.battlefield(lurker, Plain.bear, "Mountain", "Mountain")
        bob.battlefield(Plain.ogre, faceDown = true)
        alice.hand(unmask)
        alice.casts(unmask, target = Plain.ogre, modes = listOf(0, 1))
        playOut(lurker, Plain.bear)
        assertFalse(state.objOrThrow(card(Plain.ogre)).faceDown)
        val id = faceDown().single()
        assertTrue(cloaked(id))
        assertEquals(alice.id, state.objOrThrow(id).controller)
        expect {
            Plain.bear.zone shouldBe Zone.Battlefield
            alice.exile.size shouldBe 0
        }
    }

    // CR 701.58a, e: two of the five are cloaked; CR 401.4: the other three go to the bottom, under the card that wasn't looked at
    @Test
    fun blendIntoTheCrowdCloaksTwoOfFive() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(blend)
        alice.library(Plain.bear, Plain.ogre, Plain.giant, Plain.elf, Plain.turtle, "Island")
        alice.casts(blend)
        playOut(Plain.giant, Plain.turtle)
        val ids = faceDown()
        assertEquals(2, ids.size)
        assertTrue(ids.all { cloaked(it) })
        assertEquals(setOf(card(Plain.giant), card(Plain.turtle)), ids.toSet())
        expect {
            alice.library.size shouldBe 4
            alice.library.names.first() shouldBe "Island"
        }
    }

    // CR 701.58a: the first mode cloaks a card chosen from the hand as the trigger resolves
    @Test
    fun orielCloaksACardFromHand() = scenario {
        alice.battlefield(oriel)
        alice.hand(Plain.giant, Plain.shock)
        advanceTo(TurnStep.BeginningOfCombat)
        modes(0)
        playOut(Plain.shock)
        val id = faceDown().single()
        assertTrue(cloaked(id))
        assertEquals(card(Plain.shock), id)
        expect { alice.hand has Plain.giant }
    }

    // CR 701.60a: the Aura's trigger clears the mark; while enchanted, suspecting it again does nothing
    @Test
    fun ironcladExcuseClearsAndPreventsSuspicion() = scenario {
        alice.battlefield(Plain.bear, "Forest", "Forest", "Forest", "Mountain", "Mountain")
        alice.hand(blame, excuse)
        alice.casts(blame, target = Plain.bear)
        playOut()
        assertTrue(suspected(Plain.bear))
        alice.casts(excuse, target = Plain.bear)
        playOut()
        assertFalse(suspected(Plain.bear))
        expect {
            Plain.bear.power shouldBe 4
            Plain.bear hasKeyword Keyword.Hexproof.kind
        }
    }

    // CR 701.60a: an enchanted creature can't become suspected
    @Test
    fun ironcladExcuseKeepsItUnsuspected() = scenario {
        alice.battlefield(Plain.bear, "Forest", "Forest", "Forest", "Mountain")
        alice.hand(excuse, blame)
        alice.casts(excuse, target = Plain.bear)
        playOut()
        alice.casts(blame, target = Plain.bear)
        playOut()
        assertFalse(suspected(Plain.bear))
        expect { Plain.bear lacksKeyword Keyword.Menace.kind }
    }

    // CR 611.3a: on a Detective you control it is +2/+2
    @Test
    fun weightOfEvidencePumpsADetectiveYouControl() = scenario {
        alice.battlefield(sleuth, "Island", "Island")
        alice.hand(weight)
        alice.casts(weight, target = sleuth)
        playOut()
        expect {
            sleuth.power shouldBe 4
            sleuth.toughness shouldBe 4
        }
    }

    // CR 611.3a: on another player's creature it is a 1/1 (CR 613.4b) that can't block Detectives but may block others (CR 509.1b)
    @Test
    fun weightOfEvidenceShrinksAnotherCreature() = scenario {
        alice.battlefield(sleuth, Plain.bear, "Island", "Island")
        alice.hand(weight)
        bob.battlefield(Plain.giant)
        alice.casts(weight, target = Plain.giant)
        playOut()
        expect {
            Plain.giant.power shouldBe 1
            Plain.giant.toughness shouldBe 1
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sleuth, Plain.bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.giant to sleuth) }
        bob.blocks(Plain.giant to Plain.bear)
    }

    // CR 613.1b, 613.4b, 613.1d, 613.1f: control, base 1/1, an Assassin with deathtouch
    @Test
    fun compelledToMurderTakesAnAssassin() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(compelled)
        bob.battlefield(Plain.giant)
        alice.casts(compelled, target = Plain.giant)
        playOut()
        expect {
            Plain.giant.controller shouldBe alice.id
            Plain.giant.power shouldBe 1
            Plain.giant.toughness shouldBe 1
            Plain.giant hasKeyword Keyword.Deathtouch.kind
            Plain.giant.subtypes.contains(Subtype("Assassin")) shouldBe true
            Plain.giant.subtypes.contains(Subtype("Giant")) shouldBe true
        }
    }

    // CR 701.60a, c: the enchanted creature is suspected (menace, can't block) and gets +1/+1
    @Test
    fun handyScapegoatSuspectsWhatItEnchants() = scenario {
        alice.battlefield("Mountain")
        alice.hand(scapegoat)
        bob.battlefield(Plain.bear)
        alice.casts(scapegoat, target = Plain.bear)
        playOut()
        assertTrue(suspected(Plain.bear))
        expect {
            Plain.bear hasKeyword Keyword.Menace.kind
            Plain.bear.power shouldBe 3
        }
    }

    // CR 115.1, 601.2c: the enchanted creature isn't a legal target of its own trigger; the other gets +2/+2 and vigilance for the turn
    @Test
    fun carefulScrutinyPumpsAnotherCreature() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, Plain.ogre, "Plains", "Plains", "Plains")
        alice.hand(scrutiny)
        alice.casts(scrutiny, target = Plain.bear)
        allPass()
        val d = decision<Decision.Targets>()
        assertFalse(target(Plain.bear) in d.options)
        playOut(Plain.elf)
        expect {
            Plain.bear.power shouldBe 4
            Plain.bear hasKeyword Keyword.Vigilance.kind
            Plain.elf.power shouldBe 3
            Plain.elf hasKeyword Keyword.Vigilance.kind
        }
    }

    // CR 610.3: exiled until the Aura leaves; CR 605.1b: tapping the enchanted land adds one more mana of a chosen colour
    @Test
    fun interredBeneathRosesExilesAndAddsMana() = scenario {
        alice.battlefield("Forest", "Forest", "Plains", "Plains", "Swamp")
        alice.hand(roses)
        bob.battlefield(Plain.charm)
        alice.casts(roses, target = "Swamp", paying = listOf("Forest", "Forest#1", "Plains", "Plains#1"))
        playOut(Plain.charm)
        expect { bob.exile has Plain.charm }
        alice.activates("Swamp")
        decision<Decision.ChooseColor>()
        answer(Answer.ColorChoice(arcanum.api.Color.Green))
        assertEquals(setOf(arcanum.api.Color.Black, arcanum.api.Color.Green), alice.pool.map { it.color }.toSet())
        assertEquals(2, alice.pool.size)
    }

    // CR 603.2: the granted trigger investigates as the enchanted creature deals combat damage
    @Test
    fun nosyQuestioningInvestigatesOnCombatDamage() = scenario {
        alice.battlefield(Plain.bear, "Island")
        alice.hand(nosy)
        alice.casts(nosy, target = Plain.bear)
        playOut()
        hits(Plain.bear)
        expect {
            bob.life shouldBe 17
            alice.battlefield has "Clue Token"
        }
    }

    // CR 502.3: tapped and kept tapped; CR 701.24a: the creature goes into its owner's library, which is then shuffled (the audit: the owner is
    // that creature's, as it last was)
    @Test
    fun theatricalChargeShufflesTheCreatureIntoItsOwnersLibrary() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(charge)
        alice.library("Island", "Island")
        bob.battlefield(Plain.ogre)
        bob.library("Swamp", "Swamp", "Swamp")
        alice.casts(charge, target = Plain.ogre)
        playOut()
        expect { Plain.ogre.tapped shouldBe true }
        val before = state.player(bob.id).library.toSet()
        alice.activates(charge)
        playOut()
        // shuffled: every card of Bob's library is a new object (CR 401.4)
        assertTrue(state.player(bob.id).library.none { it in before })
        expect {
            bob.library.size shouldBe 4
            bob.library has Plain.ogre
            alice.library.size shouldBe 2
        }
    }

    // CR 603.10a, 701.17a: each batch of deaths mills a card and adds a counter; CR 603.12: at the fifth it is sacrificed and the reflexive trigger
    // returns a creature card
    @Test
    fun gorePatternStudyReturnsACardOnTheFifthCounter() = scenario {
        alice.battlefield(gore, counters = listOf(bloodstain to 3))
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard(Plain.giant)
        alice.library("Island", "Island", "Island")
        bob.battlefield(Plain.elf, Plain.bear)
        alice.hand(Plain.bolt, Plain.shock)
        alice.casts(Plain.bolt, target = Plain.elf)
        playOut()
        expect {
            gore.counters(bloodstain) shouldBe 4
            alice.library.size shouldBe 2
            gore.zone shouldBe Zone.Battlefield
        }
        alice.casts(Plain.shock, target = Plain.bear)
        playOut(Plain.giant)
        expect {
            alice.graveyard has gore
            alice.hand has Plain.giant
            alice.library.size shouldBe 1
        }
    }

    // CR 701.17a: three cards milled as it enters; CR 400.7: the land cards return tapped as new permanents
    @Test
    fun wreckageExaminerMillsThenReturnsLands() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(examiner)
        alice.library("Island", Plain.bear, "Swamp", "Plains")
        alice.casts(examiner, paying = listOf("Forest", "Forest#1"))
        playOut()
        expect {
            alice.graveyard has "Island"
            alice.graveyard has "Swamp"
            alice.graveyard has Plain.bear
        }
        alice.activates(examiner)
        playOut()
        expect {
            "Island".zone shouldBe Zone.Battlefield
            "Island".tapped shouldBe true
            "Swamp".zone shouldBe Zone.Battlefield
            alice.graveyard has Plain.bear
            alice.graveyard has examiner
        }
    }
}
