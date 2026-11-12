package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The cube's chunk 32 (enters: tutors, tokens and bounce) played in the engine. */
class Chunk32EntersTutorsTokensAndBounceScenariosTest {
    /** Casts [name], resolves it and its enters trigger (all players pass twice). */
    private fun Scenario.castAndResolve(name: String) {
        alice.casts(name)
        allPass()
        expect { stack.names shouldBe listOf("ability of $name") }
        allPass()
    }

    /** The way that casts [name] for its alternative cost (evoke, "you may pay … to cast this spell"). */
    private fun Scenario.alternative(name: String) = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first {
        (it.ability as? Ability.Static)?.mechanics?.any { m -> m is Mechanic.AlternativeCost } == true
    }.origin)

    /** Enters: search for a creature card with power 2 or less, reveal it, put it into your hand. */
    @Test
    fun royalEnlister() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Royal Enlister")
        alice.library("Bull of the Pyre", "Elvish Seer", "Island")

        castAndResolve("Royal Enlister")
        // CR 701.23a: only a card that matches may be found
        assertFailsWith<IllegalArgumentException> { alice.chooses("Bull of the Pyre") }
        alice.chooses("Elvish Seer")

        expect {
            alice.hand.names shouldBe listOf("Elvish Seer")
            alice.library.size shouldBe 2
        }
    }

    /** Enters: you may search for a creature card with toughness 2 or less. */
    @Test
    fun enlisterOfTheWatch() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Enlister of the Watch")
        alice.library("Wall of Portents", "Undying Valet", "Island")

        castAndResolve("Enlister of the Watch")
        answer(Answer.YesNo(true))
        alice.chooses("Undying Valet")

        expect { alice.hand.names shouldBe listOf("Undying Valet") }
    }

    /** Enters: you may search for a creature card with mana value 1 or less; sacrifice it: your opponents can't cast
     * noncreature spells this turn. */
    @Test
    fun rangerMarshalOfDawn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Ranger-Marshal of Dawn")
        alice.library("Elvish Seerling", "Elvish Seer", "Island")
        bob.battlefield("Mountain", "Forest")
        bob.hand("Storm Spark", "Elvish Seer")
        bob.library("Island")

        castAndResolve("Ranger-Marshal of Dawn")
        answer(Answer.YesNo(true))
        alice.chooses("Elvish Seer")
        expect { alice.hand.names shouldBe listOf("Elvish Seer") }
        alice.activates("Ranger-Marshal of Dawn")
        allPass()
        expect { alice.graveyard has "Ranger-Marshal of Dawn" }
        advanceTo(TurnStep.PrecombatMain, turn = 2)

        // It lasts only "this turn": on Bob's turn he may cast it again
        bob.casts("Storm Spark", target = "alice")
        expect { stack.names shouldBe listOf("Storm Spark") }
    }

    /** Opponents can't cast noncreature spells the turn it's sacrificed; creature spells still. */
    @Test
    fun rangerMarshalOfDawnStopsNoncreatureSpells() = scenario {
        alice.battlefield("Ranger-Marshal of Dawn")
        bob.battlefield("Mountain", "Forest")
        bob.hand("Storm Spark", "Elvish Seer")
        turn(number = 2, active = bob, priority = alice)

        alice.activates("Ranger-Marshal of Dawn")
        allPass()

        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = "alice") }
    }

    /** Enters: you may search for an instant or sorcery card with mana value 2 or less. */
    @Test
    fun arcaneSeeker() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Arcane Seeker")
        alice.library("Roiling Chant", "Denial", "Island")

        castAndResolve("Arcane Seeker")
        answer(Answer.YesNo(true))
        alice.chooses("Denial")

        expect { alice.hand.names shouldBe listOf("Denial") }
    }

    /** Enters: you may search for an Equipment card; {1}{W}, {T}: you may put an Equipment from your hand onto the
     * battlefield. */
    @Test
    fun anvilbornMystic() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Anvilborn Mystic")
        alice.library("Island", "Eden Cloak", "Swamp")

        castAndResolve("Anvilborn Mystic")
        answer(Answer.YesNo(true))
        alice.chooses("Eden Cloak")

        expect { alice.hand.names shouldBe listOf("Eden Cloak") }
    }

    /** {1}{W}, {T}: an Equipment card from the hand onto the battlefield. */
    @Test
    fun anvilbornMysticPutsEquipmentIntoPlay() = scenario {
        alice.battlefield("Plains", "Plains", "Anvilborn Mystic")
        alice.hand("Eden Cloak", "Skull Bracer")

        alice.activates("Anvilborn Mystic")
        allPass()
        alice.chooses("Skull Bracer")

        expect {
            alice.battlefield has "Skull Bracer"
            alice.hand.names shouldBe listOf("Eden Cloak")
            "Anvilborn Mystic".tapped shouldBe true
        }
    }

    /** Enters: you may discard a card; if you do, search for a creature card. {1}, {T}: untap another permanent. */
    @Test
    fun commandingOrator() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Commanding Orator", "Island", "Swamp")
        alice.library("Plains", "Elvish Seer")

        castAndResolve("Commanding Orator")
        answer(Answer.YesNo(true))
        alice.chooses("Island")
        alice.chooses("Elvish Seer")

        expect {
            alice.graveyard has "Island"
            alice.hand.names shouldBe listOf("Swamp", "Elvish Seer")
        }
    }

    /** {1}, {T}: untap another target permanent. */
    @Test
    fun commandingOratorUntaps() = scenario {
        alice.battlefield("Forest", "Commanding Orator")
        alice.battlefield("Island", tapped = true)

        alice.activates("Commanding Orator", target = "Island", paying = listOf("Forest"))
        allPass()

        expect {
            "Island".tapped shouldBe false
            "Commanding Orator".tapped shouldBe true
        }
    }

    /** Enters: with {R}{R} spent, 3 damage to any target. */
    @Test
    fun vivacityWithRedMana() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Vivacity")

        alice.casts("Vivacity")
        allPass()
        answer(Answer.Targets(listOf(target("bob"))))
        allPass()

        expect {
            bob.life shouldBe 17
            stack.isEmpty()
        }
    }

    /** With {G}{G} spent: search for a land card and gain 2 life. */
    @Test
    fun vivacityWithGreenMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Vivacity")
        alice.library("Storm Spark", "Island")

        alice.casts("Vivacity")
        allPass()
        expect { stack.names shouldBe listOf("ability of Vivacity") }
        allPass()
        alice.chooses("Island")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.life shouldBe 22
            bob.life shouldBe 20
        }
    }

    /** Evoke {R/G}{R/G} paid {R}{G}: sacrificed as it enters, and neither enters trigger happens. */
    @Test
    fun vivacityEvoked() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand("Vivacity")

        alice.casts("Vivacity", way = alternative("Vivacity"))
        allPass()
        allPass()

        expect {
            alice.graveyard has "Vivacity"
            stack.isEmpty()
            bob.life shouldBe 20
        }
    }

    /** "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell"; enters: you may search for
     * The Netherworld Recipe Book; sacrifice two Foods: target creature deals 6 damage to itself. */
    @Test
    fun bartholomewtheophilustrindleberry() = scenario {
        val book = Cards.invent("The Netherworld Recipe Book", "{2}", "Artifact")
        alice.battlefield("Swamp", "Swamp", "Ghoul Outbreak")
        alice.hand("Bartholomewtheophilustrindleberry", "Forest", "Island")
        alice.library("Plains", book)

        // No discard yet: it can't be cast
        assertFailsWith<IllegalArgumentException> { alice.casts("Bartholomewtheophilustrindleberry", way = alternative("Bartholomewtheophilustrindleberry")) }
        alice.activates("Ghoul Outbreak", choosing = listOf("Forest", "Island"))
        allPass()
        alice.casts("Bartholomewtheophilustrindleberry", way = alternative("Bartholomewtheophilustrindleberry"))
        allPass()
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses(book)

        expect {
            alice.battlefield has "Bartholomewtheophilustrindleberry"
            alice.hand.names shouldBe listOf(book)
            card("Swamp").tapped shouldBe true
            card("Swamp", 1).tapped shouldBe false
        }
    }

    /** Sacrifice two Foods: target creature deals 6 damage to itself. */
    @Test
    fun bartholomewSacrificesFoods() = scenario {
        alice.battlefield("Bartholomewtheophilustrindleberry", "Cookie Brawler", "Cookie Brawler")
        bob.battlefield("Wall of Portents")

        alice.activates("Bartholomewtheophilustrindleberry", target = "Wall of Portents", choosing = listOf("Cookie Brawler", "Cookie Brawler"))
        allPass()

        expect {
            alice.graveyard.names shouldBe listOf("Cookie Brawler", "Cookie Brawler")
            bob.graveyard has "Wall of Portents"
        }
    }

    /** Enters: a 3/3 colorless Phyrexian Golem artifact creature token; Golems you control have first strike. */
    @Test
    fun golemGrafter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Cookie Brawler")
        bob.battlefield("Cookie Brawler")
        alice.hand("Golem Grafter")

        castAndResolve("Golem Grafter")

        expect {
            card("Phyrexian Golem Token").power shouldBe 3
            card("Phyrexian Golem Token").types shouldBe listOf(CardType.Artifact, CardType.Creature)
            card("Phyrexian Golem Token") hasKeyword Keyword.FirstStrike
            card("Cookie Brawler", 0) hasKeyword Keyword.FirstStrike
            card("Cookie Brawler", 1) lacksKeyword Keyword.FirstStrike
            "Golem Grafter" lacksKeyword Keyword.FirstStrike
        }
    }

    /** Haste; enters: your creatures gain trample and get +X/+X, X the number of creatures you control. */
    @Test
    fun thunderhoofColossus() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Elvish Seer")
        bob.battlefield("Umbral Elves")
        alice.hand("Thunderhoof Colossus")

        castAndResolve("Thunderhoof Colossus")

        expect {
            "Elvish Seer".power shouldBe 3
            "Thunderhoof Colossus".power shouldBe 7
            "Thunderhoof Colossus" hasKeyword Keyword.Haste
            "Elvish Seer" hasKeyword Keyword.Trample
            "Umbral Elves".power shouldBe 1
        }
    }

    /** Fabricate 1: a +1/+1 counter or a Servo; another creature or artifact of yours going to a graveyard drains each
     * opponent for 1. */
    @Test
    fun puppeteersApprentice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Tinkered Mite")
        alice.hand("Puppeteer's Apprentice", "Storm Spark")

        alice.casts("Puppeteer's Apprentice")
        allPass()
        allPass()
        // CR 702.123a: the choice: a Servo
        answer(Answer.YesNo(false))
        expect {
            alice.battlefield has "Servo Token"
            "Puppeteer's Apprentice".counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
        alice.castsWithoutCosts("Storm Spark", target = "Tinkered Mite")
        resolve()
        resolve()

        expect { bob.life shouldBe 19 }
    }

    /** Fabricate's other choice: a +1/+1 counter on it. */
    @Test
    fun puppeteersApprenticeCounter() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Puppeteer's Apprentice")

        alice.casts("Puppeteer's Apprentice")
        allPass()
        allPass()
        answer(Answer.YesNo(true))

        expect {
            "Puppeteer's Apprentice".power shouldBe 2
            alice.battlefield lacks "Servo Token"
        }
    }

    /** Ninjutsu; flying; entering from the hand attacking: a token copy of another attacking creature, tapped and
     * attacking. */
    @Test
    fun myriadFacedShade() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Elvish Seer", "Umbral Elves")
        alice.hand("Myriad-Faced Shade")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer", "Umbral Elves")
        advanceTo(TurnStep.DeclareBlockers)
        // CR 702.49a: return an unblocked attacker, put the Shade in tapped and attacking
        alice.activates("Myriad-Faced Shade", choosing = listOf("Elvish Seer"))
        allPass()
        expect {
            "Myriad-Faced Shade".tapped shouldBe true
            alice.hand has "Elvish Seer"
            stack.names shouldBe listOf("ability of Myriad-Faced Shade")
        }
        allPass()
        advanceTo(TurnStep.EndOfCombat)

        expect {
            alice.battlefield.names.count { it == "Umbral Elves" } shouldBe 2
            bob.life shouldBe 17
        }
    }

    /** Cast normally (not attacking) it makes no copy. */
    @Test
    fun myriadFacedShadeCastNormally() = scenario {
        alice.battlefield("Island")
        alice.hand("Myriad-Faced Shade")
        alice.casts("Myriad-Faced Shade")
        allPass()
        expect {
            stack.isEmpty()
            "Myriad-Faced Shade" hasKeyword Keyword.Flying
        }
    }

    /** Flash; enters: copy target instant or sorcery spell; new targets may be chosen. */
    @Test
    fun twincastAdept() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.hand("Twincast Adept")
        turn(number = 2, active = bob)

        bob.casts("Storm Spark", target = "alice")
        bob.passes()
        alice.casts("Twincast Adept")
        allPass()
        expect { stack.names shouldBe listOf("Storm Spark", "ability of Twincast Adept") }
        allPass()
        // CR 707.10c: new targets for the copy
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("bob"))))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        allPass()
        allPass()

        expect {
            bob.life shouldBe 17
            alice.life shouldBe 17
            stack.isEmpty()
        }
    }

    /** Flying; enters: return a permanent you control to its owner's hand. */
    @Test
    fun korCloudangler() = scenario {
        alice.battlefield("Plains", "Plains", "Ghoul Outbreak")
        alice.hand("Kor Cloudangler")

        castAndResolve("Kor Cloudangler")
        alice.chooses("Ghoul Outbreak")

        expect {
            alice.hand.names shouldBe listOf("Ghoul Outbreak")
            "Kor Cloudangler" hasKeyword Keyword.Flying
        }
    }

    /** Flying; enters: return a creature you control (itself, if it's the only one). */
    @Test
    fun screechingDrakelet() = scenario {
        alice.battlefield("Island")
        alice.hand("Screeching Drakelet")

        castAndResolve("Screeching Drakelet")

        expect { alice.hand.names shouldBe listOf("Screeching Drakelet") }
    }

    /** Flash; enters: return a creature you control to its owner's hand. */
    @Test
    fun silvermaneLion() = scenario {
        alice.battlefield("Plains", "Plains", "Elvish Seer")
        alice.hand("Silvermane Lion")
        turn(number = 2, active = bob, priority = alice)

        castAndResolve("Silvermane Lion")
        alice.chooses("Elvish Seer")

        expect {
            alice.hand.names shouldBe listOf("Elvish Seer")
            alice.battlefield has "Silvermane Lion"
        }
    }

    /** Flash, flying; enters: return a creature you control; and exile target card from a graveyard. */
    @Test
    fun stoneshroudGargoyle() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Elvish Seer")
        bob.graveyard("Storm Spark", "Island")
        alice.hand("Stoneshroud Gargoyle")

        alice.casts("Stoneshroud Gargoyle")
        allPass()
        answer(Answer.Order(listOf(0, 1)))
        answer(Answer.Targets(listOf(target("Storm Spark"))))
        allPass()
        allPass()
        alice.chooses("Elvish Seer")

        expect {
            bob.exile has "Storm Spark"
            alice.hand.names shouldBe listOf("Elvish Seer")
        }
    }
}
