package arcanum.engine.sets.dmu

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.eoe.pay
import arcanum.engine.sets.fin.choosePlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The DMU cards of chunk 07 that give the engine something new, played: the invented names carry the oracle text of Tyrannical Pitlord,
 * Thran Portal, Zur, Eternal Schemer, Serra Paragon, Phoenix Chick, Shanna, Purifying Blade, Baird, Argivian Recruiter, Danitha, Benalia's
 * Hope, Inscribed Tablet, Soul of Windgrace, Threats Undetected, Ivy, Gleeful Spellthief, Rivaz of the Claw and Vesuvan Duplimancy.
 */
class DmuChunk07GrantsAndPermissionsScenariosTest {
    private val bear = Cards.invent("Chunk Seven Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val elk = Cards.invent("Chunk Seven Test Elk", "{2}{G}", "Creature — Elk", "", "2/3")
    private val ogre = Cards.invent("Chunk Seven Test Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val wurm = Cards.invent("Chunk Seven Test Wurm", "{4}{G}", "Creature — Wurm", "", "5/5")
    private val wyrm = Cards.invent("Chunk Seven Test Wyrm", "{2}{R}", "Creature — Dragon", "", "3/3")
    private val banish = Cards.invent("Chunk Seven Test Banish", "{W}", "Instant", "Exile target creature.")
    private val blast = Cards.invent("Chunk Seven Test Blast", "{R}", "Instant", "Chunk Seven Test Blast deals 5 damage to target creature.")
    private val quake = Cards.invent("Chunk Seven Test Quake", "{R}", "Sorcery", "Destroy target land.")
    private val growth = Cards.invent("Chunk Seven Test Growth", "{G}", "Instant", "Target creature gets +2/+2 until end of turn.")
    private val ward = Cards.invent("Chunk Seven Test Ward", "{W}", "Enchantment", "")
    private val sigil = Cards.invent("Chunk Seven Test Sigil", "{2}{W}", "Enchantment", "")
    private val blade = Cards.invent("Chunk Seven Test Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +2/+0.\nEquip {4}")
    private val paladin = Cards.invent("Chunk Seven Test Paladin", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")

    private val pitfiend = Cards.invent("Despotic Pitfiend", "{4}{B}{B}", "Creature — Demon", """
        Flying, trample
        As this creature enters, choose another creature you control.
        The chosen creature gets +3/+3 and has flying.
        When this creature leaves the battlefield, sacrifice the chosen creature.
    """, "6/6")
    private val ruin = Cards.invent("Thran Gateway Ruin", null, "Land — Gate", """
        This land enters tapped unless you control two or fewer other lands.
        As this land enters, choose a basic land type.
        This land is the chosen type in addition to its other types.
        Mana abilities of this land cost an additional 1 life to activate.
    """)
    private val vorr = Cards.invent("Vorr, Eternal Plotter", "{W}{U}{B}", "Legendary Creature — Human Wizard", """
        Flying
        Enchantment creatures you control have deathtouch, lifelink, and hexproof.
        {1}{W}: Target non-Aura enchantment you control becomes a creature in addition to its other types and has base power and base toughness each equal to its mana value.
    """, "1/4")
    private val exemplar = Cards.invent("Serra Exemplar", "{2}{W}{W}", "Creature — Angel", """
        Flying
        Once during each of your turns, you may play a land from your graveyard or cast a permanent spell with mana value 3 or less from your graveyard. If you do, it gains "When this permanent is put into a graveyard from the battlefield, exile it and you gain 2 life."
    """, "3/4")
    private val fledgling = Cards.invent("Phoenix Fledgling", "{R}", "Creature — Phoenix", """
        Flying, haste
        This creature can't block.
        Whenever you attack with three or more creatures, you may pay {R}{R}. If you do, return this card from your graveyard to the battlefield tapped and attacking with a +1/+1 counter on it.
    """, "1/1")
    private val shalla = Cards.invent("Shalla, Cleansing Edge", "{G}{W}{U}", "Legendary Creature — Human Warrior", """
        Lifelink
        At the beginning of your end step, you may pay {X}. If you do, draw X cards. X can't be greater than the amount of life you gained this turn.
    """, "3/3")
    private val bardo = Cards.invent("Bardo, Argivian Drillmaster", "{R}{W}", "Legendary Creature — Human Soldier",
        "At the beginning of your end step, if you control a creature with power greater than its base power, create a 1/1 white Soldier creature token.", "2/2")
    private val danika = Cards.invent("Danika, Benalian Hope", "{4}{W}", "Legendary Creature — Human Knight", """
        First strike, vigilance, lifelink
        When Danika enters, you may put an Aura or Equipment card from your hand or graveyard onto the battlefield attached to Danika.
    """, "4/4")
    private val slate = Cards.invent("Etched Slate", "{1}", "Artifact",
        "{1}, {T}, Sacrifice this artifact: Reveal the top five cards of your library. Put a land card from among them into your hand and the rest on the bottom of your library in a random order. If you didn't put a card into your hand this way, draw a card.")
    private val windmere = Cards.invent("Spirit of Windmere", "{1}{B}{R}{G}", "Legendary Creature — Cat Avatar", """
        Whenever Spirit of Windmere enters or attacks, you may put a land card from a graveyard onto the battlefield tapped under your control.
        {G}, Discard a land card: You gain 3 life.
    """, "5/4")
    private val dangers = Cards.invent("Dangers Unseen", "{2}{G}", "Sorcery",
        "Search your library for up to four creature cards with different powers and reveal them. An opponent chooses two of those cards. Shuffle the chosen cards into your library and put the rest into your hand.")
    private val ivet = Cards.invent("Ivet, Merry Spellthief", "{G}{U}", "Legendary Creature — Faerie Rogue", """
        Flying
        Whenever a player casts a spell that targets only a single creature other than Ivet, you may copy that spell. The copy targets Ivet. (A copy of an Aura spell becomes a token.)
    """, "2/1")
    private val rizav = Cards.invent("Rizav of the Talon", "{1}{B}{R}", "Legendary Creature — Lizard Warlock", """
        Menace
        Once during each of your turns, you may cast a Dragon creature spell from your graveyard.
        Whenever you cast a Dragon creature spell from your graveyard, it gains "When this creature dies, exile it."
    """, "3/3")
    private val mimicry = Cards.invent("Vesuvan Mimicry", "{3}{U}", "Enchantment",
        "Whenever you cast a spell that targets only a single artifact or creature you control, create a token that's a copy of that artifact or creature, except it's not legendary.")

    // ---- Tyrannical Pitlord

    // CR 607.2a, 613.4c: the creature chosen as it entered gets +3/+3 and flying; CR 603.6c, 701.21a: when it leaves, that creature is sacrificed
    @Test
    fun pitfiendPumpsTheChosenAndSacrificesItWhenItLeaves() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Plains", bear)
        alice.hand(pitfiend, banish)
        alice.casts(pitfiend)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 5
            bear hasKeyword Keyword.Flying.kind
        }
        alice.casts(banish, target = pitfiend)
        resolveAll()
        expect {
            pitfiend.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Graveyard
        }
    }

    // ---- Thran Portal

    // CR 602.2b, 305.6: the chosen type's intrinsic mana ability costs 1 life more
    @Test
    fun ruinsManaAbilityCostsALife() = scenario {
        alice.hand(ruin, blast)
        bob.battlefield(elk)
        alice.plays(ruin)
        answer(Answer.SubtypeChoice(Subtype("Mountain")))
        expect { ruin.tapped shouldBe false }
        alice.casts(blast, target = elk)
        resolveAll()
        expect {
            elk.zone shouldBe Zone.Graveyard
            alice.life shouldBe 19
        }
    }

    // ---- Zur, Eternal Schemer

    // CR 613.1d, 613.4b, 608.2h: a creature with base power and toughness its mana value, for good; the granted abilities follow (layer 6)
    @Test
    fun vorrAnimatesAnEnchantmentByItsManaValue() = scenario {
        alice.battlefield(vorr, sigil, "Plains", "Plains")
        alice.activates(vorr, target = sigil)
        resolveAll()
        advanceTo(TurnStep.End)
        expect {
            sigil.types.contains(CardType.Creature) shouldBe true
            sigil.power shouldBe 3
            sigil.toughness shouldBe 3
            sigil hasKeyword Keyword.Lifelink.kind
        }
    }

    // ---- Serra Paragon

    // CR 305.1, 400.7d: a land played from the graveyard uses the once a turn and gains the ability: destroyed, it's exiled and Alice gains 2
    @Test
    fun exemplarPlaysALandOnceAndItGainsTheAbility() = scenario {
        alice.battlefield(exemplar, "Mountain")
        alice.graveyard("Forest", bear)
        alice.hand(quake)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertFailsWith<IllegalArgumentException> { alice.casts(bear) }
        alice.casts(quake, target = "Forest", paying = listOf("Mountain"))
        resolveAll()
        expect {
            alice.exile has "Forest"
            alice.life shouldBe 22
        }
    }

    // CR 601.2, 400.7d: a permanent spell of mana value 3 or less cast from the graveyard; when it dies it is exiled and Alice gains 2
    @Test
    fun exemplarCastsAPermanentSpellThatGainsTheAbility() = scenario {
        alice.battlefield(exemplar, "Forest", "Forest", "Mountain")
        alice.graveyard(bear, wurm)
        alice.hand(blast)
        assertFailsWith<IllegalArgumentException> { alice.casts(wurm) }
        alice.casts(bear)
        resolveAll()
        alice.casts(blast, target = bear)
        resolveAll()
        expect {
            alice.exile has bear
            alice.life shouldBe 22
        }
    }

    // ---- Phoenix Chick

    // CR 508.4, 122.6: three attackers; paid, it returns tapped and attacking with a +1/+1 counter, and deals its damage
    @Test
    fun fledglingReturnsAttackingWithACounter() = scenario {
        alice.battlefield(bear, elk, ogre, "Mountain", "Mountain")
        alice.graveyard(fledgling)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, elk, ogre, defender = "bob")
        resolveAll()
        may(true)
        pay()
        expect {
            fledgling.zone shouldBe Zone.Battlefield
            fledgling.tapped shouldBe true
            fledgling.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 - 2 - 2 - 3 - 2 }
    }

    // ---- Shanna, Purifying Blade

    // CR 107.1c: X is at most the 3 life gained this turn; paid {2}, Alice draws two
    @Test
    fun shallaDrawsAtMostTheLifeGained() = scenario {
        alice.battlefield(shalla, "Island", "Island", "Island", "Island")
        alice.library("Plains", "Plains", "Plains", "Plains")
        bob.battlefield(elk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shalla, defender = "bob")
        advanceTo(TurnStep.End)
        resolveAll()
        val d = decision<Decision.ChooseNumber>()
        assertEquals(3, d.max)
        answer(Answer.Number(2))
        may(true)
        pay()
        expect { alice.hand.size shouldBe 2 }
    }

    // ---- Baird, Argivian Recruiter

    // CR 208.4b: a +2/+2 pump raises power above base power; a creature only as big as its base power doesn't count (CR 603.4)
    @Test
    fun bardoCountsPowerAboveBasePower() = scenario {
        alice.battlefield(bardo, bear, "Forest")
        alice.hand(growth)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks "Soldier Token" }
    }

    @Test
    fun bardoMakesASoldierWhenACreatureIsPumped() = scenario {
        alice.battlefield(bardo, bear, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Soldier Token" }
    }

    // ---- Danitha, Benalia's Hope

    // CR 301.5c: an Equipment from the graveyard enters attached to it without paying equip
    @Test
    fun danikaAttachesAnEquipmentFromTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(danika)
        alice.graveyard(blade)
        alice.casts(danika)
        resolveAll()
        alice.chooses(blade)
        resolveAll()
        expect {
            blade.zone shouldBe Zone.Battlefield
            danika.power shouldBe 6
        }
    }

    // ---- Inscribed Tablet

    // CR 609.3: no land among the five, they all go to the bottom and Alice draws a card
    @Test
    fun slateDrawsWhenNoLandIsRevealed() = scenario {
        alice.battlefield(slate, "Plains")
        alice.library(bear, elk, ogre, wurm, wyrm, "Island")
        alice.activates(slate)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.hand has "Island"
        }
    }

    // CR 701.20a: the land revealed goes to the hand and nothing is drawn
    @Test
    fun slateTakesTheRevealedLand() = scenario {
        alice.battlefield(slate, "Plains")
        alice.library(bear, "Forest", ogre, wurm, wyrm, "Island")
        alice.activates(slate)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.hand has "Forest"
        }
    }

    // ---- Soul of Windgrace

    // CR 110.2a: a land card from an opponent's graveyard enters tapped under Alice's control
    @Test
    fun windmereTakesALandFromAnyGraveyard() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Forest")
        alice.hand(windmere)
        carol.graveyard("Island")
        alice.casts(windmere)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            "Island".zone shouldBe Zone.Battlefield
            "Island".controller shouldBe alice.id
            "Island".tapped shouldBe true
        }
    }

    // ---- Threats Undetected

    // CR 701.23a: two cards of the same power can't both be found; the opponent Alice picks chooses the two shuffled back (3 players)
    @Test
    fun dangersFindsDifferentPowersAndAnOpponentChooses() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(dangers)
        alice.library(bear, elk, ogre, wurm, wyrm)
        alice.casts(dangers)
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.chooses(bear, elk) }
        alice.chooses(bear, ogre, wurm)
        choosePlayer(carol)
        assertEquals(carol.id, decision<Decision.ChooseObjects>().player)
        carol.chooses(bear, wurm)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.hand has ogre
            alice.library has bear
            alice.library has wurm
        }
    }

    // ---- Ivy, Gleeful Spellthief

    // CR 707.10e: Bob's pump on his own creature is copied for Alice, the copy targets Ivet
    @Test
    fun ivetCopiesASingleTargetSpellOntoItself() = scenario(players = 3) {
        alice.battlefield(ivet)
        bob.battlefield(elk, "Forest")
        bob.hand(growth)
        turn(number = 2, active = bob)
        bob.casts(growth, target = elk)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            ivet.power shouldBe 4
            elk.power shouldBe 4
        }
    }

    // CR 115.7: a spell targeting Ivet itself doesn't trigger it
    @Test
    fun ivetIgnoresSpellsTargetingItself() = scenario {
        alice.battlefield(ivet, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = ivet)
        resolveAll()
        expect { ivet.power shouldBe 4 }
    }

    // ---- Rivaz of the Claw

    // CR 400.7d: the Dragon cast from the graveyard gains the ability as a creature: it dies and is exiled
    @Test
    fun rizavsDragonFromTheGraveyardIsExiledWhenItDies() = scenario {
        alice.battlefield(rizav, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(wyrm)
        alice.hand(blast)
        alice.casts(wyrm)
        resolveAll()
        expect { wyrm.zone shouldBe Zone.Battlefield }
        alice.casts(blast, target = wyrm)
        resolveAll()
        expect { alice.exile has wyrm }
    }

    // ---- Vesuvan Duplimancy

    // CR 707.9b: a token copy of the spell's single target, not legendary
    @Test
    fun mimicryCopiesTheTargetWithoutLegendary() = scenario {
        alice.battlefield(mimicry, paladin, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = paladin)
        resolveAll()
        val tokens = view().objects.values.filter { it.zone == Zone.Battlefield && it.obj?.isToken == true }
        assertEquals(1, tokens.size)
        assertTrue(arcanum.api.Supertype.Legendary !in tokens.single().characteristics!!.supertypes)
        assertEquals("Chunk Seven Test Paladin", tokens.single().characteristics!!.name)
    }
}
