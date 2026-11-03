package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.p1p1
import arcanum.engine.sets.ecl.Ecl.redBear
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lorwyn Eclipsed cards that copy triggered abilities and spells (conspire given to spells, a copy for each single-target spell, a
 * delayed copy), cast cards for free from among those they exiled, and cast exiled creature cards by removing counters. */
class EclCopiesAndFreeCastsScenariosTest {
    private val dovin = Cards.invent("Dovin, Watchful Freshling", "{1}{R/W}{R/W}", "Legendary Creature — Vampire Cleric", "Tap two untapped creatures you control: Copy target triggered ability you control. You may choose new targets for the copy. Activate only once each turn.", "3/3")
    private val eldestGranny = Cards.invent("Eldest Granny", "{2}{R}", "Creature — Goblin Warlock", "When this creature enters, create a 1/1 black and red Goblin creature token.", "2/2")
    private val sylvaine = Cards.invent("Sylvaine, Fae Ascendant", "{2}{B}{G}{U}", "Legendary Creature — Elf Faerie Noble", """
        Flying
        Whenever Sylvaine or another Elf or Faerie you control enters, exile the top two cards of target opponent's library.
        Once each turn, you may cast a spell with mana value less than or equal to the number of Elves and Faeries you control from among cards exiled with Sylvaine this turn without paying its mana cost.
    """, "4/5")
    private val duskpalmDissenter = Cards.invent("Duskpalm Dissenter", "{B}", "Creature — Elf Warlock", """
        {T}, Blight 1: Surveil 1.
        {T}, Blight 2: Exile target card from a graveyard.
        During your turn, you may cast creature spells from among cards you own exiled with this creature by removing three counters from among creatures you control in addition to paying their other costs.
    """, "1/2")
    private val plunderingPlots = Cards.invent("Plundering Plots", "{3}{R}{G}", "Enchantment", "Each noncreature spell you cast has conspire. (As you cast a noncreature spell, you may tap two untapped creatures you control that share a color with it. When you do, copy it and you may choose new targets for the copy. A copy of a permanent spell becomes a token.)")
    private val spikestoneDespot = Cards.invent("Spikestone Despot", "{3}{R}{R}", "Creature — Dragon", """
        Flying
        Wither (This deals damage to creatures in the form of -1/-1 counters.)
        Whenever you cast an instant or sorcery spell with a single target, you may copy it. If you do, those spells gain wither. You may choose new targets for the copy.
    """, "6/6")
    private val frostflameCollar = Cards.invent("Frostflame Collar", "{1}{U}", "Artifact", """
        As this artifact enters, choose a creature type.
        Whenever a permanent you control of the chosen type enters, put a charge counter on this artifact.
        {T}, Remove three charge counters from this artifact: When you next cast an instant or sorcery spell this turn, copy it. You may choose new targets for the copy.
    """)

    // CR 707.10, 603.2: a copy of the enters trigger resolves first; both make a Goblin
    @Test
    fun dovinCopiesAnEntersTrigger() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", dovin, bear, redBear)
        alice.hand(eldestGranny)
        alice.casts(eldestGranny)
        allPass()
        expect { stack.names shouldBe listOf("ability of Eldest Granny") }
        alice.activates(dovin, choosing = listOf(bear, redBear))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(state.stack.first().let { arcanum.engine.state.TargetRef.Object(it) })))
        if (pending is Decision.ChooseObjects) alice.chooses(bear, redBear)
        expect {
            bear.tapped shouldBe true
            redBear.tapped shouldBe true
            stack.size shouldBe 2
        }
        resolveAll()
        expect { alice.battlefield.names.count { it == "Goblin Token" } shouldBe 2 }
    }

    // CR 602.5b: "Activate only once each turn"
    @Test
    fun dovinOnlyOnceEachTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", dovin, bear, redBear, ogre, elf)
        alice.hand(eldestGranny, eldestGranny)
        alice.casts(eldestGranny, paying = listOf("Mountain", "Mountain", "Mountain"))
        allPass()
        alice.activates(dovin, choosing = listOf(bear, redBear))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(topOfStack())))
        if (pending is Decision.ChooseObjects) alice.chooses(bear, redBear)
        resolveAll()
        alice.casts(eldestGranny)
        allPass()
        assertFailsWith<IllegalArgumentException> {
            alice.activates(dovin, choosing = listOf(ogre, elf))
            if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(topOfStack())))
        }
    }

    // CR 601.2, 118.9: cast for free from among the exiled cards, if the mana value is low enough; once each turn
    @Test
    fun sylvaineCastsAnExiledCardForFree() = scenario {
        alice.battlefield("Forest", sylvaine)
        alice.hand(elf)
        bob.library(jolt, bear, ogre)
        alice.casts(elf)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            bob.exile has jolt
            bob.exile has bear
            bob.library has ogre
        }
        // two Elves and Faeries: Spark Jolt (mana value 1) is cast without mana
        alice.casts(jolt, target = "bob")
        resolveAll()
        assertEquals(18, bob.life)
        // once each turn: the bear (mana value 2) can't be cast too
        assertFailsWith<IllegalArgumentException> { alice.casts(bear) }
    }

    // the mana value must be at most the number of Elves and Faeries you control
    @Test
    fun sylvaineCantCastACostlierCard() = scenario {
        alice.battlefield("Forest", sylvaine)
        alice.hand(elf)
        bob.library(ogre, jolt)
        alice.casts(elf)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.casts(ogre) }
    }

    // CR 601.2f, 601.2h: exiled by its ability; cast during your turn by removing three counters from among your creatures as well as
    // paying the mana cost
    @Test
    fun duskpalmDissenterCastsByRemovingCounters() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", "Mountain", duskpalmDissenter, ogre)
        alice.battlefield(elf, counters = listOf(p1p1 to 1))
        alice.graveyard(redBear)
        alice.activates(duskpalmDissenter, ability = 1, target = redBear, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.exile has redBear
            ogre.counters(m1m1) shouldBe 2
        }
        alice.casts(redBear, paying = listOf("Mountain", "Mountain"), choosing = listOf(ogre, elf))
        resolveAll()
        expect {
            alice.battlefield has redBear
            ogre.counters(m1m1) shouldBe 0
            elf.counters(p1p1) shouldBe 0
        }
    }

    // only during your turn; and only with three counters to remove
    @Test
    fun duskpalmDissenterNeedsYourTurnAndThreeCounters() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", duskpalmDissenter, ogre)
        alice.graveyard(redBear)
        alice.activates(duskpalmDissenter, ability = 1, target = redBear, choosing = listOf(ogre))
        resolveAll()
        // two counters only
        assertFailsWith<IllegalArgumentException> { alice.casts(redBear, paying = listOf("Mountain", "Mountain"), choosing = listOf(ogre)) }
    }

    @Test
    fun duskpalmDissenterOnlyDuringYourTurn() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", duskpalmDissenter, ogre)
        alice.battlefield(elf, counters = listOf(p1p1 to 1))
        alice.graveyard(redBear)
        alice.activates(duskpalmDissenter, ability = 1, target = redBear, choosing = listOf(ogre))
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertFailsWith<IllegalArgumentException> { alice.casts(redBear, paying = listOf("Mountain", "Mountain"), choosing = listOf(ogre, elf)) }
    }

    // CR 702.78a: conspire given by a static ability: tap two creatures sharing a colour, and the spell is copied
    @Test
    fun plunderingPlotsGivesConspire() = scenario {
        alice.battlefield("Mountain", plunderingPlots, redBear, ogre)
        alice.hand(jolt)
        alice.casts(jolt, target = "bob", kicked = 1, choosing = listOf(redBear, ogre))
        resolveAll()
        say(false)
        resolveAll()
        expect {
            redBear.tapped shouldBe true
            ogre.tapped shouldBe true
        }
        assertEquals(16, bob.life)
    }

    // CR 702.78a: the creatures tapped must each share a colour with the spell
    @Test
    fun conspireNeedsCreaturesOfTheSpellsColor() = scenario {
        alice.battlefield("Mountain", plunderingPlots, redBear, bear)
        alice.hand(jolt)
        assertFailsWith<IllegalArgumentException> { alice.casts(jolt, target = "bob", kicked = 1, choosing = listOf(redBear, bear)) }
    }

    // creature spells don't get conspire
    @Test
    fun conspireOnlyForNoncreatureSpells() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", plunderingPlots, redBear, ogre)
        alice.hand(eldestGranny)
        alice.casts(eldestGranny, kicked = 1, choosing = listOf(redBear, ogre))
        assertTrue(pending == null, "nothing more is asked: $pending")
        resolveAll()
        expect {
            redBear.tapped shouldBe false
            alice.battlefield.names.count { it == "Eldest Granny" } shouldBe 1
        }
    }

    // CR 707.10, 702.80a: the single-target spell is copied, and both deal damage as wither
    @Test
    fun spikestoneDespotCopiesWithWither() = scenario {
        alice.battlefield("Mountain", spikestoneDespot)
        alice.hand(jolt)
        bob.battlefield(ogre)
        alice.casts(jolt, target = ogre)
        resolveAll()
        say(true)
        resolveAll()
        say(false)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun spikestoneDespotWitherCounters() = scenario {
        alice.battlefield("Mountain", spikestoneDespot)
        alice.hand(jolt)
        bob.battlefield(Ecl.bigElemental)
        alice.casts(jolt, target = Ecl.bigElemental)
        resolveAll()
        say(true)
        resolveAll()
        say(false)
        resolveAll()
        expect {
            Ecl.bigElemental.counters(m1m1) shouldBe 4
            Ecl.bigElemental.view.obj?.damage shouldBe 0
        }
    }

    // CR 603.7: a delayed trigger copies the next instant or sorcery cast this turn
    @Test
    fun frostflameCollarCopiesTheNextSpell() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield(frostflameCollar, counters = listOf(CounterKind.Named("charge") to 3))
        alice.hand(jolt)
        alice.activates(frostflameCollar)
        resolveAll()
        alice.casts(jolt, target = "bob")
        resolveAll()
        say(false)
        resolveAll()
        assertEquals(16, bob.life)
        expect { frostflameCollar.counters(CounterKind.Named("charge")) shouldBe 0 }
    }

    // CR 614.12: the creature type is chosen as it enters; permanents of that type that enter add charge counters
    @Test
    fun frostflameCollarCountsTheChosenType() = scenario {
        alice.battlefield("Island", "Island", "Forest")
        alice.hand(frostflameCollar, elf)
        alice.casts(frostflameCollar)
        resolveAll()
        if (pending is Decision.ChooseSubtype) alice.answers(Answer.SubtypeChoice(arcanum.api.Subtype("Elf")))
        resolveAll()
        alice.casts(elf)
        resolveAll()
        expect { frostflameCollar.counters(CounterKind.Named("charge")) shouldBe 1 }
    }
}
