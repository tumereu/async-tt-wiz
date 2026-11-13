package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.spm.loreChapter
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 09: an attack trigger countering the attacker and each other creature sharing a creature type with it, a sword that surveils
 * and casts a cheap spell from the graveyard for free (exiled afterwards), a modal enters trigger that fights and a discard ability that
 * shuffles the discarded card back from the graveyard, a Saga that puts milled creatures onto the battlefield and distributes seven counters
 * among any number of creatures, a Siege's two-target enters trigger and +2 damage from noncreature sources, and a reveal that draws a land
 * or double-faced card. */
class MomChunk09EquipFightProwessScryScenariosTest {
    // Holy Frazzle-Cannon's text on a single-faced card (a Siege's back face can't be set up face up, see notes)
    private val rig = Cards.invent("Hymnal Frazzle-Rig", "{2}", "Artifact — Equipment", """
        Whenever equipped creature attacks, put a +1/+1 counter on that creature and each other creature you control that shares a creature type with it.
        Equip {1}
    """.trimIndent())
    private val sword = Cards.invent("Sword of Ebb and Return", "{3}", "Artifact — Equipment", """
        Equipped creature gets +2/+2 and has protection from blue and from black.
        Whenever equipped creature deals combat damage to a player, surveil 2. Then you may cast an instant or sorcery spell with mana value 2 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
        Equip {2}
    """.trimIndent())
    private val brakka = Cards.invent("Brakka and Thulm", "{2}{R}{R}{G}{G}", "Legendary Creature — Ape Dinosaur Turtle", """
        When Brakka and Thulm enters, choose one —
        • It gains trample and haste until end of turn.
        • It fights target creature you don't control.
        {2}{R}{G}, Discard this card: Destroy up to one target artifact or enchantment. Shuffle this card into your library from your graveyard, then draw a card.
    """.trimIndent(), "7/7")
    // The Grand Evolution's text on a single-faced Saga
    private val ascent = Cards.invent("The Rampant Ascent", "{3}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter.)
        I — Mill ten cards. Put up to two creature cards from among the milled cards onto the battlefield.
        II — Distribute seven +1/+1 counters among any number of target creatures you control.
        III — Until end of turn, creatures you control gain "{1}: This creature fights target creature you don't control." Exile this Saga, then return it to the battlefield (front face up).
    """.trimIndent())
    private val corvanth = Cards.inventFaces("Transform",
        OracleCard("Invasion of Corvanth", "{2}{R}", "Battle — Siege",
            "When this Siege enters, it deals 4 damage to another target battle or opponent and 1 damage to up to one target creature.", defense = "5"),
        OracleCard("Acolytes of the Pyre", null, "Creature — Human Monk", "Prowess\nIf a noncreature source you control would deal damage to a creature, battle, or opponent, it deals that much damage plus 2 instead.",
            "4/4", colorIndicator = "R"))
    private val acolytes = Cards.invent("Pyrebound Acolytes", "{2}{R}", "Creature — Human Monk", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        If a noncreature source you control would deal damage to a creature, battle, or opponent, it deals that much damage plus 2 instead.
    """.trimIndent(), "4/4")
    private val mireholt = Cards.inventFaces("Transform",
        OracleCard("Invasion of Mireholt", "{G}{U}", "Battle — Siege",
            "When this Siege enters, scry 3, then reveal the top card of your library. If it's a land or double-faced card, draw a card.", defense = "4"),
        OracleCard("Towering Slabhide", null, "Creature — Beast", "Trample, ward {2}\nOther transformed permanents you control have trample and ward {2}.", "4/4",
            colorIndicator = "GU"))
    private val lancer = Cards.inventFaces("Transform",
        face("Sandjackal Lancer", "{1}{R}", "Creature — Jackal Warrior", """
            Trample
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {3}{U/P}: Transform this creature. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
        """.trimIndent(), "2/2", null, null),
        face("Glistening Lancestalker", null, "Creature — Phyrexian Jackal", "Trample, ward {2}, prowess, prowess (Each instance of prowess triggers separately.)",
            "3/3", null, "UR"))

    private val elf = Cards.invent("Plasmfield Elf", "{1}{G}", "Creature — Elf Warrior", "", "2/2")
    private val druid = Cards.invent("Plasmfield Druid", "{G}", "Creature — Elf Druid", "", "1/1")
    private val bear = Cards.invent("Plasmfield Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ranger = Cards.invent("Plasmfield Ranger", "{1}{G}", "Creature — Elf Ranger", "", "2/2")
    private val ogre = Cards.invent("Plasmfield Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val insight = Cards.invent("Plasmfield Insight", "{1}{U}", "Instant", "Draw a card.")
    private val study = Cards.invent("Plasmfield Study", "{2}{U}", "Sorcery", "Draw a card.")
    private val shock = Cards.invent("Plasmfield Shock", "{R}", "Instant", "Plasmfield Shock deals 2 damage to any target.")
    private val pinger = Cards.invent("Plasmfield Pinger", "{1}{R}", "Creature — Human Shaman", "{T}: This creature deals 1 damage to any target.", "1/1")
    private val relic = Cards.invent("Plasmfield Relic", "{1}", "Artifact", "")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.libraries(count: Int = 6) = players.forEach { p -> p.library(*Array(count) { "Island" }) }
    /** Resolves the scry keeping the card on top. */
    private fun Scenario.keepOnTop() {
        resolveAll()
        answer(Answer.Objects(emptyList()))
        resolveAll()
    }
    private fun Scenario.targetsOne(ref: TargetRef) = answer(Answer.Targets(listOf(ref)))

    // CR 508.1m, 603.2: "that creature" is the attacker; "each other creature you control that shares a creature type with it" (CR 205.3)
    // gets one too, not a creature of another type and not an opponent's Elf
    @Test
    fun theRigCountersTheAttackerAndEachOtherCreatureSharingAType() = scenario {
        alice.battlefield(rig, elf, druid, bear)
        bob.battlefield(ranger)
        attach(rig, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(elf)
        resolveAll()
        expect {
            elf.counters(p1p1) shouldBe 1
            druid.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
            ranger.counters(p1p1) shouldBe 0
        }
    }

    // CR 701.25a: surveil 2 first, so a spell surveilled into the graveyard can be cast; CR 608.2c: chosen as it resolves; CR 601.2, 118.9:
    // without paying its mana cost; CR 614.1a: it is exiled instead of going to the graveyard; a mana value 3 sorcery isn't offered
    @Test
    fun theSwordCastsASurveilledSpellFreeAndExilesIt() = scenario {
        alice.battlefield(sword, bear)
        attach(sword, bear)
        alice.graveyard(study)
        alice.library(insight, "Forest", "Island", "Island")
        bob.library("Island", "Island")
        unblocked(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses(insight)
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(1, choice.options.size)
        alice.chooses(insight)
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.exile has insight
            alice.graveyard has study
            alice.hand has "Forest"
        }
    }

    // CR 700.2, 701.14a: the chosen mode fights the opponent's creature (7 damage to it, 3 back)
    @Test
    fun brakkaFightsACreatureYouDontControl() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest", "Mountain", "Forest")
        alice.hand(brakka)
        bob.battlefield(ogre)
        alice.casts(brakka)
        resolveAll()
        answer(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            bob.graveyard has ogre
            brakka.view.obj?.damage shouldBe 3
        }
    }

    // CR 602.2b: discarded as a cost; CR 400.7: the ability finds the card in the graveyard and shuffles it into the library; then draws
    @Test
    fun brakkasDiscardAbilityDestroysAndShufflesItselfBack() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand(brakka)
        alice.library("Island")
        bob.battlefield(relic)
        alice.activates(brakka, target = relic)
        resolveAll()
        expect {
            bob.graveyard has relic
            alice.graveyard lacks brakka
            // shuffled in with the Island, then one of the two drawn
            (brakka in alice.library.names + alice.hand.names) shouldBe true
            alice.library.size shouldBe 1
            alice.hand.size shouldBe 1
        }
    }

    // CR 701.17a, 608.2c: up to two creature cards from among the milled ones onto the battlefield, under your control (CR 110.2a)
    @Test
    fun ascentPutsTwoMilledCreaturesOntoTheBattlefield() = scenario {
        loreChapter(ascent, 1, elf, druid, bear, "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        resolveAll()
        alice.chooses(elf, bear)
        resolveAll()
        expect {
            alice.battlefield has elf
            alice.battlefield has bear
            alice.graveyard has druid
            elf.controller shouldBe alice.id
        }
    }

    // CR 601.2d: seven counters divided among any number of target creatures you control, at least one each
    @Test
    fun ascentDistributesSevenCountersAmongTheChosenCreatures() = scenario {
        alice.battlefield(elf, druid, bear)
        bob.battlefield(ranger)
        loreChapter(ascent, 2)
        answer(Answer.Targets(listOf(target(elf), target(druid))))
        val divide = decision<Decision.Divide>()
        assertEquals(7, divide.amount)
        answer(Answer.Division(listOf(Share(target(elf), 5), Share(target(druid), 2))))
        resolveAll()
        expect {
            elf.counters(p1p1) shouldBe 5
            druid.counters(p1p1) shouldBe 2
            bear.counters(p1p1) shouldBe 0
        }
    }

    // CR 115.1, 310.6: 4 damage to the opponent and 1 to their creature; CR 115.4: "another target battle" (three players: one opponent)
    @Test
    fun corvanthDealsFourToAnOpponentAndOneToACreature() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(corvanth)
        bob.battlefield(ogre)
        libraries()
        alice.casts(corvanth)
        resolveAll()
        while (pending is Decision.ChoosePlayer) answer(Answer.Player(carol.id))
        resolveAll()
        targetsOne(TargetRef.Player(bob.id))
        if (pending is Decision.Targets) targetsOne(target(ogre))
        resolveAll()
        expect {
            bob.life shouldBe 16
            carol.life shouldBe 20
            ogre.view.obj?.damage shouldBe 1
        }
    }

    // CR 614.1a: a noncreature source of yours deals 2 more to a creature (anyone's) or an opponent; a creature source doesn't
    @Test
    fun acolytesAddTwoToNoncreatureDamageOnly() = scenario {
        alice.battlefield(acolytes, pinger, "Mountain", "Mountain")
        alice.hand(shock)
        bob.battlefield(ogre)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
        alice.activates(pinger, target = ogre)
        resolveAll()
        expect { ogre.view.obj?.damage shouldBe 1 }
    }

    // CR 701.22a, 712.1: the revealed top card is a double-faced card, so it is drawn
    @Test
    fun mireholtDrawsARevealedDoubleFacedCard() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(mireholt)
        alice.library(lancer)
        alice.casts(mireholt)
        keepOnTop()
        expect { alice.hand has lancer }
    }

    // CR 701.22a: a revealed single-faced nonland card isn't drawn; a land is
    @Test
    fun mireholtDrawsOnlyALandOrDoubleFacedCard() {
        scenario {
            alice.battlefield("Forest", "Island")
            alice.hand(mireholt)
            alice.library(bear)
            alice.casts(mireholt)
            keepOnTop()
            expect { alice.hand lacks bear }
        }
        scenario {
            alice.battlefield("Forest", "Island")
            alice.hand(mireholt)
            alice.library("Forest")
            alice.casts(mireholt)
            keepOnTop()
            expect { alice.hand has "Forest" }
        }
    }
}
