package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.view.KeywordMeanings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Review 2 finding #10: keyword abilities without an expansion did nothing. The cheap ones now have expansions; every
 * other one stops with a manual step at its natural moment ([KeywordMeanings]), so none is silently left out (D61).
 */
class Review2KeywordsTest {
    private val hound = Cards.invent("Review Deathless Hound", "{1}{B}", "Creature — Zombie Dog", "Undying", "2/2")
    private val voidling = Cards.invent("Review Hollow Drifter", "{1}{U}", "Creature — Eldrazi Drone", "Devoid", "1/3")
    private val rider = Cards.invent("Review Steppe Rider", "{1}{R}", "Creature — Human Warrior", "Horsemanship", "2/2")
    private val skulker = Cards.invent("Review Alley Skulker", "{B}", "Creature — Rat", "Skulk", "1/1")
    private val fader = Cards.invent("Review Fading Wisp", "{1}{G}", "Creature — Spirit", "Fading 2", "3/3")
    private val soul = Cards.invent("Review Lingering Monk", "{1}{W}", "Creature — Human Monk", "Afterlife 2", "1/1")
    private val titan = Cards.invent("Review Hungering Colossus", "{7}", "Creature — Eldrazi", "Annihilator 1", "5/5")
    private val stinger = Cards.invent("Review Venom Stinger", "{1}{B}", "Creature — Insect", "Poisonous 2", "1/1")
    private val tormentor = Cards.invent("Review Grief Hound", "{2}{B}", "Creature — Dog", "Afflict 2", "3/3")
    private val samurai = Cards.invent("Review Patient Samurai", "{1}{W}", "Creature — Human Samurai", "Bushido 1", "2/2")
    private val knight = Cards.invent("Review Flanking Lancer", "{1}{W}", "Creature — Human Knight", "Flanking", "2/2")
    private val berserker = Cards.invent("Review Raging Berserker", "{1}{R}", "Creature — Human Berserker", "Frenzy 2", "1/1")
    private val mentor = Cards.invent("Review Drill Sergeant", "{2}{W}", "Creature — Human Soldier", "Mentor", "3/3")
    private val renowned = Cards.invent("Review Glory Seeker", "{1}{W}", "Creature — Human Soldier", "Renown 1", "2/2")
    private val sponge = Cards.invent("Review Absorbing Wall", "{1}{W}", "Creature — Wall", "Absorb 2", "1/2")
    private val sage = Cards.invent("Review Patient Sage", "{1}{G}", "Creature — Elf", "Outlast {1}", "1/1")
    private val reinforcements = Cards.invent("Review Reserve Squire", "{2}{W}", "Creature — Kithkin Soldier", "Reinforce 2—{1}{W}", "2/2")
    private val wall = Cards.invent("Review Rampart Plate", "{1}", "Artifact — Fortification", "Fortify {1}")
    private val rebelBlade = Cards.invent("Review Rebel Blade", "{1}{R}", "Artifact — Equipment", "For Mirrodin!\nEquipped creature gets +1/+0.\nEquip {2}")
    private val walker = Cards.invent("Review Living Walker", "{2}", "Artifact — Vehicle", "Living metal\nCrew 1", "3/3")
    private val leap = Cards.invent("Review Leaping Insight", "{U}", "Sorcery", "Draw a card.\nJump-start")
    private val exploiter = Cards.invent("Review Gnawing Exploiter", "{1}{B}", "Creature — Zombie", "Exploit", "2/2")
    private val tinkerer = Cards.invent("Review Scrap Tinkering", "{3}{U}", "Sorcery", "Improvise\nDraw two cards.")
    private val umbra = Cards.invent("Review Mossy Umbra", "{1}{G}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.\nUmbra armor")
    private val bear = Cards.invent("Review Big Bear", "{2}{G}", "Creature — Bear", "", "3/3")
    private val cub = Cards.invent("Review Bear Cub", "{G}", "Creature — Bear", "", "1/1")

    // ---- Implemented keywords

    /** Undying: it dies without a +1/+1 counter, returns with one; dying again with it, it stays in the graveyard. */
    // CR 702.93a
    @Test
    fun undyingReturnsOnceWithACounter() = scenario {
        alice.battlefield("Mountain", "Mountain", hound)
        alice.hand("Storm Spark", "Storm Spark")
        alice.casts("Storm Spark", target = hound)
        allPass()
        allPass()
        expect {
            alice.battlefield has hound
            hound.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
        alice.casts("Storm Spark", target = hound)
        allPass()
        expect { alice.graveyard has hound }
    }

    /** Devoid: colorless though its mana cost is blue, everywhere (here in the hand and on the battlefield). */
    // CR 702.114a, 604.3
    @Test
    fun devoidIsColorless() = scenario {
        alice.battlefield(voidling)
        expect { voidling.colors shouldBe emptyList<Color>() }
    }

    /** Horsemanship: only a creature with horsemanship can block it. */
    // CR 702.31b
    @Test
    fun horsemanshipCantBeBlockedWithoutIt() = scenario {
        alice.battlefield(rider)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rider)
        allPass()
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card(bear), card(rider))))) }
    }

    /** Skulk: a creature with greater power can't block it; one with equal power can. */
    // CR 702.118b
    @Test
    fun skulkCantBeBlockedByGreaterPower() = scenario {
        alice.battlefield(skulker)
        bob.battlefield(bear, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(skulker)
        allPass()
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card(bear), card(skulker))))) }
        bob.blocks(cub to skulker)
    }

    /** Fading 2: enters with two fade counters, loses one each upkeep, and is sacrificed at the upkeep it can't. */
    // CR 702.32a
    @Test
    fun fadingCountsDownThenSacrifices() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(fader)
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.casts(fader)
        allPass()
        expect { fader.counters(CounterKind.Named("fade")) shouldBe 2 }
        advanceTo(TurnStep.Draw, turn = 3)
        expect { fader.counters(CounterKind.Named("fade")) shouldBe 1 }
        advanceTo(TurnStep.Draw, turn = 5)
        expect { fader.counters(CounterKind.Named("fade")) shouldBe 0 }
        advanceTo(TurnStep.Draw, turn = 7)
        expect { alice.graveyard has fader }
    }

    /** Afterlife 2: dying, it leaves two 1/1 white and black flying Spirits. */
    // CR 702.135a
    @Test
    fun afterlifeMakesSpirits() = scenario {
        alice.battlefield("Mountain", soul)
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = soul)
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 2
            "Spirit Token".colors shouldBe listOf(Color.White, Color.Black)
            "Spirit Token" hasKeyword Keyword.Flying
        }
    }

    /** Annihilator 1: attacking, the defending player sacrifices a permanent of their choice. */
    // CR 702.86a
    @Test
    fun annihilatorMakesTheDefenderSacrifice() = scenario {
        alice.battlefield(titan)
        bob.battlefield("Island", bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(titan)
        allPass()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses("Island")
        expect {
            bob.graveyard has "Island"
            bob.battlefield has bear
        }
    }

    /** Annihilator with nothing to sacrifice: the defending player sacrifices nothing. */
    // CR 702.86a, 701.21a
    @Test
    fun annihilatorWithNoPermanents() = scenario {
        alice.battlefield(titan)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(titan)
        allPass()
        if (pending is Decision.ChooseObjects) bob.answers(Answer.Objects(emptyList()))
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    /** Poisonous 2: combat damage to a player gives two poison counters. */
    // CR 702.70a
    @Test
    fun poisonousGivesPoisonCounters() = scenario {
        alice.battlefield(stinger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stinger)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 19
            bob.counters(CounterKind.Poison) shouldBe 2
        }
    }

    /** Afflict 2: becoming blocked, the defending player loses 2 life. */
    // CR 702.130a
    @Test
    fun afflictOnBecomingBlocked() = scenario {
        alice.battlefield(tormentor)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tormentor)
        allPass()
        bob.blocks(bear to tormentor)
        allPass()
        expect { bob.life shouldBe 18 }
    }

    /** Bushido 1: blocking a 3/3, the 2/2 gets +1/+1, so both die. */
    // CR 702.45a
    @Test
    fun bushidoPumpsWhenBlocking() = scenario {
        alice.battlefield(bear)
        bob.battlefield(samurai)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        bob.blocks(samurai to bear)
        allPass()
        expect { samurai.power shouldBe 3 }
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.graveyard has bear
            bob.graveyard has samurai
        }
    }

    /** Flanking: a 1/1 without flanking blocking it gets -1/-1 and dies before damage. */
    // CR 702.25a
    @Test
    fun flankingShrinksTheBlocker() = scenario {
        alice.battlefield(knight)
        bob.battlefield(cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(knight)
        allPass()
        bob.blocks(cub to knight)
        allPass()
        expect { bob.graveyard has cub }
    }

    /** Frenzy 2: attacking unblocked, it gets +2/+0. */
    // CR 702.68a
    @Test
    fun frenzyWhenUnblocked() = scenario {
        alice.battlefield(berserker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(berserker)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    /** Mentor: the 3/3 puts a +1/+1 counter on the other attacker with less power. */
    // CR 702.134a
    @Test
    fun mentorCountersALesserAttacker() = scenario {
        alice.battlefield(mentor, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mentor, cub)
        allPass()
        expect { cub.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    /** Renown 1: the first combat damage to a player makes it renowned with a counter; the next doesn't add one. */
    // CR 702.112a
    @Test
    fun renownOnce() = scenario {
        alice.battlefield(renowned)
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(renowned)
        advanceTo(TurnStep.EndOfCombat)
        expect { renowned.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(renowned)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            renowned.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bob.life shouldBe 15
        }
    }

    /** Absorb 2: Storm Spark's 3 damage is 1, which a 1/2 survives. */
    // CR 702.64a
    @Test
    fun absorbPreventsSomeDamage() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield(sponge)
        alice.casts("Storm Spark", target = sponge)
        allPass()
        expect { bob.battlefield has sponge }
        assertEquals(1, state.objOrThrow(card(sponge)).damage)
    }

    /** Outlast {1}: tap and pay to put a +1/+1 counter on it, only as a sorcery. */
    // CR 702.107a
    @Test
    fun outlastAddsACounter() = scenario {
        alice.battlefield("Forest", sage)
        alice.activates(sage)
        allPass()
        expect {
            sage.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            sage.tapped shouldBe true
        }
    }

    /** Reinforce 2: from the hand, discard it to put two +1/+1 counters on a creature, at instant speed. */
    // CR 702.77a
    @Test
    fun reinforceFromTheHand() = scenario {
        alice.battlefield("Plains", "Plains", cub)
        alice.hand(reinforcements)
        turn(number = 1, active = bob, priority = alice)
        alice.activates(reinforcements, target = cub)
        allPass()
        expect {
            cub.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            alice.graveyard has reinforcements
        }
    }

    /** Fortify {1}: attaches the Fortification to a land you control. */
    // CR 702.67a
    @Test
    fun fortifyAttachesToALand() = scenario {
        alice.battlefield("Plains", "Island", wall)
        alice.activates(wall, target = "Island")
        allPass()
        assertEquals(TargetRef.Object(card("Island")), state.objOrThrow(card(wall)).attachedTo)
    }

    /** For Mirrodin!: entering, the Equipment makes a 2/2 red Rebel and is attached to it. */
    // CR 702.163a
    @Test
    fun forMirrodinMakesARebel() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(rebelBlade)
        alice.casts(rebelBlade)
        allPass()
        allPass()
        expect {
            "Rebel Token".power shouldBe 3
            "Rebel Token".colors shouldBe listOf(Color.Red)
        }
        assertEquals(TargetRef.Object(card("Rebel Token")), state.objOrThrow(card(rebelBlade)).attachedTo)
    }

    /** Living metal: a creature during its controller's turn only. */
    // CR 702.161a
    @Test
    fun livingMetalOnYourTurn() = scenario {
        alice.battlefield(walker)
        expect { (arcanum.api.CardType.Creature in walker.types) shouldBe true }
        turn(number = 2, active = bob)
        expect { (arcanum.api.CardType.Creature in walker.types) shouldBe false }
    }

    /** Jump-start: cast from the graveyard by discarding a card as well; it is exiled afterwards. */
    // CR 702.133a
    @Test
    fun jumpStartFromTheGraveyard() = scenario {
        alice.battlefield("Island")
        alice.graveyard(leap)
        alice.hand("Forest")
        alice.library("Swamp")
        alice.casts(leap, choosing = listOf("Forest"))
        allPass()
        expect {
            alice.exile has leap
            alice.graveyard has "Forest"
            alice.hand has "Swamp"
        }
    }

    /** Exploit: entering, you may sacrifice a creature (here the cub). */
    // CR 702.110a
    @Test
    fun exploitMaySacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", cub)
        alice.hand(exploiter)
        alice.casts(exploiter)
        allPass()
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        alice.chooses(cub)
        expect {
            alice.graveyard has cub
            alice.battlefield has exploiter
        }
    }

    /** Improvise: artifacts tapped pay generic mana. */
    // CR 702.126a
    @Test
    fun improvisePaysWithArtifacts() = scenario {
        alice.battlefield("Island", "Onyx Bauble", "Tinkered Mite", "Bonevise")
        alice.hand(tinkerer)
        alice.library("Swamp", "Plains")
        alice.casts(tinkerer, paying = listOf("Island", "Onyx Bauble", "Tinkered Mite", "Bonevise"))
        allPass()
        expect {
            "Tinkered Mite".tapped shouldBe true
            "Bonevise".tapped shouldBe true
            alice.hand has "Swamp"
        }
    }

    /** Umbra armor against "destroy": the Aura goes instead; "can't be regenerated" doesn't matter. */
    // CR 702.89a
    @Test
    fun umbraArmorAgainstDestroy() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Curse Word")
        bob.battlefield(bear, umbra)
        attach(umbra, bear)
        alice.casts("Curse Word", target = bear)
        allPass()
        expect {
            bob.battlefield has bear
            bob.graveyard has umbra
        }
    }

    // ---- Keywords done by hand

    /** A keyword the engine doesn't automate (enlist) stops with a manual step as the creature attacks; mobilize, by hand
     * before, now creates its tokens. */
    // CR 702.154a, 702.181a, D61
    @Test
    fun enlistIsDoneByHandAsItAttacks() = scenario {
        val enlister = Cards.invent("Review Drafting Sergeant", "{1}{W}", "Creature — Human Soldier", "Enlist", "2/2")
        val mobilizer = Cards.invent("Review Horde Caller", "{1}{R}", "Creature — Goblin", "Mobilize 2", "2/2")
        alice.battlefield(enlister, mobilizer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(enlister, mobilizer)
        allPass()
        if (pending is Decision.OrderTriggers) alice.answers(Answer.Order(listOf(0, 1)))
        while (pending !is Decision.Manual && state.stack.isNotEmpty()) passPriority()
        val manual = decision<Decision.Manual>()
        assertEquals("Enlist (CR 702.154): apply it by hand", manual.text)
        assertEquals(alice.id, manual.player)
        alice.answers(Answer.Done)
        allPass()
        expect { alice.battlefield.size shouldBe 4 }
    }

    /** Split second (automated since the Reality Fracture work, by hand before): while the spell is on the Stack, no
     * other spell can be cast; mana abilities can still be activated. */
    // CR 702.61a
    @Test
    fun splitSecondStopsOtherSpellsWhileOnTheStack() = scenario {
        val sudden = Cards.invent("Review Sudden Spark", "{R}", "Instant", "Split second\nReview Sudden Spark deals 2 damage to any target.")
        val shock = Cards.invent("Review Quick Shock", "{R}", "Instant", "Review Quick Shock deals 2 damage to any target.")
        alice.battlefield("Mountain")
        bob.battlefield("Mountain")
        alice.hand(sudden)
        bob.hand(shock)
        alice.casts(sudden, target = "bob")
        passPriority()
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
        assertTrue(Engine.options(state, bob.id).any { it is Input.Activate })
        assertFailsWith<IllegalArgumentException> { bob.casts(shock, target = "alice") }
        allPass()
        expect { bob.life shouldBe 18 }
    }

    /** Embalm: the engine pays its cost (mana, exiling the card from the graveyard, as a sorcery) and creates the token
     * (by hand before). */
    // CR 702.128a
    @Test
    fun embalmPaysItsCostAndCreatesTheToken() = scenario {
        val embalmed = Cards.invent("Review Gilded Mummy", "{1}{W}", "Creature — Human Cleric", "Embalm {W}", "2/2")
        alice.battlefield("Plains")
        alice.graveyard(embalmed)
        alice.activates(embalmed)
        expect { alice.exile has embalmed }
        allPass()
        expect { alice.battlefield has embalmed }
    }

    /** Harmonize (casting from the graveyard) comes up as the card is put into the graveyard. */
    // CR 702.180a, D61
    @Test
    fun harmonizeIsDoneByHandAsItReachesTheGraveyard() = scenario {
        val chorus = Cards.invent("Review Echoing Chorus", "{1}{G}", "Sorcery", "Draw a card.\nHarmonize {3}{G}")
        alice.battlefield("Forest", "Forest")
        alice.hand(chorus)
        alice.library("Island")
        alice.casts(chorus)
        allPass()
        expect { alice.graveyard has chorus }
        allPass()
        assertTrue(decision<Decision.Manual>().text.startsWith("Harmonize"))
    }

    /** Miracle comes up as the card is drawn: its controller may cast it for the miracle cost (sos: no longer by hand). */
    // CR 702.94a
    @Test
    fun miracleIsOfferedAsItIsDrawn() = scenario {
        val wonder = Cards.invent("Review Sudden Wonder", "{3}{W}", "Sorcery", "Draw two cards.\nMiracle {W}")
        players.forEach { it.library("Island", "Island") }
        alice.library(wonder)
        turn(number = 2, active = bob)
        advanceTo(TurnStep.Draw, turn = 3)
        allPass()
        expect { alice.hand has wonder }
        assertEquals(alice.id, decision<Decision.May>().player)
    }

    // ---- Splice, bestow, collect evidence

    /** Declining the splice: only the spell's own {R}{R}{R}, and the other card stays in the hand. */
    // CR 702.47a
    @Test
    fun spliceDeclined() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Frantic Invocation", "Frantic Invocation")
        perform(Input.Cast(alice.id, card("Frantic Invocation", 0)))
        alice.answers(Answer.YesNo(false))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        assertEquals(3, alice.pool.size)
        expect { alice.hand.names shouldBe listOf("Frantic Invocation") }
    }

    /** Splice onto Arcane isn't offered for a spell that isn't Arcane. */
    // CR 702.47a
    @Test
    fun spliceOnlyOntoItsQuality() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark", "Frantic Invocation")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertTrue(pending is Decision.Targets, "asked $pending")
    }

    /** A bestowed Firebird whose creature dies stays on the battlefield as a creature (it stops being bestowed). */
    // CR 702.103f
    @Test
    fun aBestowedAuraBecomesACreatureWhenUnattached() = scenario {
        alice.battlefield("Mountain", "Elvish Seer")
        alice.graveyard("Inspector's Firebird", "Duskflame Phoenix", "Smoldering Spite")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        castFirebirdBestowed()
        allPass()
        expect { "Elvish Seer".power shouldBe 3 }
        alice.passes()
        bob.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        expect {
            alice.graveyard has "Elvish Seer"
            alice.battlefield has "Inspector's Firebird"
            "Inspector's Firebird".power shouldBe 2
            (arcanum.api.CardType.Creature in "Inspector's Firebird".types) shouldBe true
        }
        assertEquals(null, state.objOrThrow(card("Inspector's Firebird")).attachedTo)
    }

    /** A bestowed Aura spell whose target is gone as it resolves enters as a creature instead of not resolving. */
    // CR 702.103e, 608.3b
    @Test
    fun aBestowedSpellWithoutItsTargetResolvesAsACreature() = scenario {
        alice.battlefield("Mountain", "Elvish Seer")
        alice.graveyard("Inspector's Firebird", "Duskflame Phoenix", "Smoldering Spite")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        castFirebirdBestowed()
        expect { ("Aura" in "Inspector's Firebird".subtypes.map { it.name }) shouldBe true }
        alice.passes()
        bob.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        allPass()
        expect {
            alice.battlefield has "Inspector's Firebird"
            "Inspector's Firebird".power shouldBe 2
        }
    }

    /** Collecting evidence 6 can't be paid with cards of total mana value 4. */
    // CR 701.59a
    @Test
    fun collectEvidenceNeedsTheTotal() = scenario {
        alice.battlefield("Mountain", "Elvish Seer")
        alice.graveyard("Inspector's Firebird", "Smoldering Spite")
        assertFailsWith<IllegalArgumentException> { castFirebirdBestowed(listOf("Smoldering Spite")) }
    }

    private fun arcanum.engine.scenario.Scenario.castFirebirdBestowed(evidence: List<String> = listOf("Duskflame Phoenix", "Smoldering Spite")) {
        val bestow = view()[card("Inspector's Firebird")]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost } == true
        }.origin
        alice.casts("Inspector's Firebird", way = arcanum.engine.input.CastWay(alternative = bestow), target = "Elvish Seer", choosing = evidence)
    }

    // ---- Paying life is losing life

    /** Paying 1 life for Sunbaked Mesa with a life-loss doubler loses 2 (the payment is still made). */
    // CR 119.4, 614.1a
    @Test
    fun payingLifeGoesThroughLifeLossReplacements() = scenario {
        // "If you would lose life, you lose twice that much life instead." (by hand: the parser doesn't read it yet)
        val doubler = Cards.define("Review Bloodtithe Idol", arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition(
            "Review Bloodtithe Idol", manaCost = arcanum.api.ManaCost.parse("{2}{B}"), types = listOf(arcanum.api.CardType.Artifact),
            abilities = listOf(arcanum.api.Ability.Static(listOf(arcanum.api.Mechanic.Replacement(
                arcanum.api.EventPattern.LoseLife(arcanum.api.PlayerFilter.You), modify = listOf(arcanum.api.Modification.MultiplyAmount(2)))),
                affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self)))))))
        alice.battlefield("Sunbaked Mesa", doubler)
        alice.library("Plains", "Island")
        alice.activates("Sunbaked Mesa")
        expect { alice.life shouldBe 18 }
    }

    /** Paying life is losing life: "whenever you lose life" triggers on a life payment. */
    // CR 119.4, 603.2
    @Test
    fun payingLifeTriggersOnLifeLoss() = scenario {
        // "Whenever you lose life, put a +1/+1 counter on this creature." (by hand: the parser doesn't read it yet)
        val ghoul = Cards.define("Review Pain-Fed Ghoul", arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition(
            "Review Pain-Fed Ghoul", manaCost = arcanum.api.ManaCost.parse("{1}{B}"), types = listOf(arcanum.api.CardType.Creature),
            power = arcanum.api.PrintedNumber.Fixed(1), toughness = arcanum.api.PrintedNumber.Fixed(1),
            abilities = listOf(arcanum.api.Ability.Triggered(arcanum.api.Trigger.On(arcanum.api.EventPattern.LoseLife(arcanum.api.PlayerFilter.You)),
                arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.PutCounters(arcanum.api.Objects.Self,
                    CounterKind.PowerToughness(1, 1), arcanum.api.Value.Fixed(1)))))))))))
        alice.battlefield("Sunbaked Mesa", ghoul)
        alice.library("Plains", "Island")
        alice.activates("Sunbaked Mesa")
        alice.passes()
        bob.passes()
        expect { ghoul.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    /** Every keyword means something to the engine: an expansion, engine code, or abilities that stop by hand. */
    // D61
    @Test
    fun everyKeywordHasAMeaning() {
        // Spree and tiered (CR 702.172a, 702.183a) mean what their modes carry (Mode.extraCost)
        val noMeaning = setOf<KeywordKind>(Keyword.Reach, Keyword.Partner, Keyword.Spree, Keyword.Tiered)
        KeywordKind.all.filterIsInstance<Keyword>().forEach { keyword ->
            if (keyword.kind in noMeaning || keyword.kind in KeywordMeanings.engineCode) return@forEach
            assertTrue(KeywordMeanings.expand(keyword).isNotEmpty(), "${keyword.text} means nothing")
        }
        // The table of natural moments only names keywords without an expansion
        KeywordMeanings.moments.keys.filterIsInstance<Keyword>().forEach { keyword ->
            assertTrue(KeywordMeanings.isManual(keyword), "${keyword.text} has an expansion and a manual moment")
        }
    }
}
