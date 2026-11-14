package arcanum.engine.sets.woe

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.hob.pickTargets
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * WOE chunk 09, one-off effects: the invented names carry the oracle text of Callous Sell-Sword, Elusive Otter, Tangled Colony, The
 * Apprentice's Folly, Graceful Takedown, Archon of the Wild Rose, Kellan, the Fae-Blooded, Specter of Mortality and Virtue of Courage.
 */
class WoeChunk09EffectsScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val bruin = Cards.invent("Thornwood Bruin", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Cragside Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val hulk = Cards.invent("Moorland Hulk", "{5}{G}", "Creature — Beast", "", "7/7")
    private val squire = Cards.invent("Field Squire", "{W}", "Creature — Human Soldier", "", "1/1")
    private val jolt = Cards.invent("Cinder Snap", "{R}", "Instant", "Cinder Snap deals 3 damage to target creature.")
    private val spit = Cards.invent("Ember Spit", "{R}", "Instant", "Ember Spit deals 3 damage to any target.")
    private val prick = Cards.invent("Needle Prick", "{R}", "Instant", "Needle Prick deals 1 damage to target creature.")
    private val ward = Cards.invent("Gilded Ward", "{W}", "Enchantment — Aura", "Enchant creature")
    private val towerWard = Cards.invent("Tower Ward", "{W}", "Enchantment — Aura", "Enchant creature")
    private val circlet = Cards.invent("Iron Circlet", "{1}", "Artifact — Equipment", "Equip {1}")
    private val brannoc = Cards.invent("Ser Brannoc", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")
    private val wisp = Cards.invent("Mirror Wisp", "{U}", "Creature — Reflection", "", "1/1")

    private val hireblade = Cards.inventFaces("Adventure",
        face("Callous Hireblade", "{1}{B}", "Creature — Human Soldier",
            "This creature enters with a +1/+1 counter on it for each creature that died under your control this turn.", "2/2", null, null),
        face("Fused Ruin", "{R}", "Sorcery — Adventure",
            "Target creature you control deals damage equal to its power to any other target. Then sacrifice it.", null, null, null))
    private val weasel = Cards.inventFaces("Adventure",
        face("Slippery Weasel", "{U}", "Creature — Weasel",
            "Prowess\nCreatures with power less than this creature's power can't block it.", "1/1", null, null),
        face("Orchard Gift", "{X}{G}", "Sorcery — Adventure",
            "Distribute X +1/+1 counters among any number of target creatures you control. (Then exile this card. You may cast the creature later from exile.)",
            null, null, null))
    private val nest = Cards.invent("Snarled Rat Nest", "{1}{B}", "Creature — Rat", """
        This creature can't block.
        When this creature dies, create X 1/1 black Rat creature tokens with "This token can't block," where X is the amount of damage dealt to it this turn.
    """, "3/2")
    private val blunder = Cards.invent("The Pupil's Blunder", "{2}{U}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I, II — Choose target nontoken creature you control that doesn't have the same name as a token you control. Create a token that's a copy of it, except it isn't legendary, is a Reflection in addition to its other types, and has haste.
        III — Sacrifice all Reflections you control.
    """)
    private val downfall = Cards.invent("Elegant Downfall", "{1}{G}", "Sorcery",
        "Any number of target enchanted creatures you control and up to one other target creature you control each deal damage equal to their power to target creature you don't control.")
    private val archon = Cards.invent("Archon of the Thorned Bloom", "{2}{W}{W}", "Creature — Archon", """
        Flying
        Other creatures you control that are enchanted by Auras you control have base power and toughness 4/4 and have flying.
    """, "4/4")
    private val arlen = Cards.invent("Arlen, the Fey-Touched", "{2}{R}", "Legendary Creature — Human Faerie", """
        Double strike
        Other creatures you control get +1/+0 for each Aura and Equipment attached to Arlen.
    """, "2/2")
    private val wraith = Cards.invent("Wraith of Endings", "{3}{B}{B}", "Creature — Specter", """
        Flying
        When this creature enters, you may exile one or more creature cards from your graveyard. When you do, each other creature gets -X/-X until end of turn, where X is the number of cards exiled this way.
    """, "3/3")
    private val valor = Cards.inventFaces("Adventure",
        face("Virtue of Valor", "{3}{R}{R}", "Enchantment",
            "Whenever a source you control deals noncombat damage to an opponent, you may exile that many cards from the top of your library. You may play those cards this turn.",
            null, null, null),
        face("Ember Flare", "{1}{R}", "Instant — Adventure",
            "Ember Flare deals 2 damage to any target. (Then exile this card. You may cast the enchantment later from exile.)", null, null, null))

    // CR 614.1c, 700.4: one creature died under Alice's control this turn (Bob's doesn't count): one counter
    @Test
    fun callousHirebladeCountsOnlyYourCreaturesThatDied() = scenario {
        alice.battlefield("Mountain", "Mountain", "Swamp", "Swamp", bruin)
        alice.hand(jolt, spit, hireblade)
        bob.battlefield(ogre)
        alice.casts(jolt, target = bruin)
        resolveAll()
        alice.casts(spit, target = ogre)
        resolveAll()
        alice.casts(hireblade)
        resolveAll()
        expect {
            bruin.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            hireblade.counters(p1p1) shouldBe 1
        }
    }

    // CR 608.2c, 701.21a: the Ogre deals 3 to Bob, then Alice sacrifices it
    @Test
    fun fusedRuinDealsPowerDamageThenSacrificesTheCreature() = scenario {
        alice.battlefield("Mountain", ogre)
        alice.hand(hireblade)
        alice.casts(hireblade, targets = listOf(ogre, "bob"), way = CastWay(face = 1))
        resolveAll()
        expect {
            bob.life shouldBe 17
            ogre.zone shouldBe Zone.Graveyard
            alice.exile has hireblade
        }
    }

    // CR 115.3: "any other target" can't be the creature dealing the damage
    @Test
    fun fusedRuinCantAimTheCreatureAtItself() = scenario {
        alice.battlefield("Mountain", ogre)
        alice.hand(hireblade)
        bob.battlefield(bruin)
        assertFailsWith<IllegalArgumentException> { alice.casts(hireblade, targets = listOf(ogre, ogre), way = CastWay(face = 1)) }
    }

    // CR 601.2d, 107.3a: X = 3 divided 2 and 1 among two of Alice's creatures
    @Test
    fun orchardGiftDistributesXCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bruin, ogre)
        alice.hand(weasel)
        alice.casts(weasel, x = 3, way = CastWay(face = 1))
        pickTargets(bruin, ogre)
        alice.answers(Answer.Division(listOf(Share(target(bruin), 2), Share(target(ogre), 1))))
        paySuggested()
        resolveAll()
        expect {
            bruin.counters(p1p1) shouldBe 2
            ogre.counters(p1p1) shouldBe 1
        }
    }

    // CR 603.10a, 120.3: 1 + 3 damage was dealt to it this turn, the second lethal: four Rats
    @Test
    fun snarledRatNestMakesARatForEachDamageDealtToIt() = scenario {
        alice.battlefield("Mountain", "Mountain", nest)
        alice.hand(prick, jolt)
        alice.casts(prick, target = nest)
        resolveAll()
        alice.casts(jolt, target = nest)
        resolveAll()
        expect {
            nest.zone shouldBe Zone.Graveyard
            tokens("Rat Token").size shouldBe 4
        }
    }

    // CR 707.9a, b: chapter I copies Ser Brannoc: a Reflection with haste, not legendary
    @Test
    fun pupilsBlunderCopiesAsAHastyReflection() = scenario {
        alice.battlefield(brannoc)
        loreChapter(blunder, 1)
        resolveAll()
        expect {
            val copy = CardRef(tokens(brannoc).single(), "copy")
            (Subtype("Reflection") in copy.subtypes) shouldBe true
            (Subtype("Knight") in copy.subtypes) shouldBe true
            (Supertype.Legendary in copy.view.characteristics!!.supertypes) shouldBe false
            copy hasKeyword Keyword.Haste.kind
        }
    }

    // CR 701.21a: chapter III sacrifices Alice's Reflections only
    @Test
    fun pupilsBlunderChapterThreeSacrificesReflections() = scenario {
        alice.battlefield(wisp, bruin)
        bob.battlefield(Cards.invent("Mirror Wisp Twin", "{U}", "Creature — Reflection", "", "1/1"))
        loreChapter(blunder, 3)
        resolveAll()
        expect {
            wisp.zone shouldBe Zone.Graveyard
            bruin.zone shouldBe Zone.Battlefield
            bob.battlefield has "Mirror Wisp Twin"
        }
    }

    // CR 120.3, 115.3: the two enchanted creatures and the Squire each deal their power: 2 + 3 + 1 to the Hulk
    @Test
    fun elegantDownfallEachChosenCreatureDealsItsPower() = scenario {
        alice.battlefield("Forest", "Forest", bruin, ogre, squire, ward, towerWard)
        attach(ward, bruin)
        attach(towerWard, ogre)
        alice.hand(downfall)
        bob.battlefield(hulk)
        alice.casts(downfall)
        pickTargets(bruin, ogre, squire, hulk)
        paySuggested()
        resolveAll()
        expect { card(hulk).view.obj?.damage shouldBe 6 }
    }

    // CR 115.3: "up to one other target creature" can't be one already chosen for the first targets (it was before this fix)
    @Test
    fun elegantDownfallOtherTargetExcludesTheFirstTargets() = scenario {
        alice.battlefield("Forest", "Forest", bruin, squire, ward)
        attach(ward, bruin)
        alice.hand(downfall)
        bob.battlefield(hulk)
        alice.casts(downfall)
        answer(Answer.Targets(listOf(target(bruin))))
        assertEquals(listOf(target(squire)), decision<Decision.Targets>().options)
    }

    // CR 613.4b, 613.1f: only Alice's other creature enchanted by her Aura becomes a 4/4 flier
    @Test
    fun archonMakesEnchantedCreaturesFourFourFliers() = scenario {
        alice.battlefield(archon, bruin, ogre, ward)
        attach(ward, bruin)
        bob.battlefield(squire, towerWard)
        attach(towerWard, squire)
        expect {
            bruin.power shouldBe 4
            bruin.toughness shouldBe 4
            bruin hasKeyword Keyword.Flying.kind
            ogre.power shouldBe 3
            ogre lacksKeyword Keyword.Flying.kind
            squire.power shouldBe 1
        }
    }

    // CR 613.4c: an Aura and an Equipment on Arlen: other creatures get +2/+0, Arlen doesn't
    @Test
    fun arlenPumpsOthersForEachAttachment() = scenario {
        alice.battlefield(arlen, bruin, ward, circlet)
        attach(ward, arlen)
        attach(circlet, arlen)
        expect {
            bruin.power shouldBe 4
            bruin.toughness shouldBe 2
            arlen.power shouldBe 2
        }
    }

    // CR 603.12, 611.2c: two cards exiled: every other creature gets -2/-2
    @Test
    fun wraithShrinksOtherCreaturesByTheCardsExiled() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ogre)
        alice.hand(wraith)
        alice.graveyard(bruin, squire)
        bob.battlefield(hulk)
        alice.casts(wraith)
        resolveAll()
        may(true)
        answer(Answer.Objects(listOf(card(bruin), card(squire))))
        resolveAll()
        expect {
            alice.exile has bruin
            alice.exile has squire
            ogre.power shouldBe 1
            hulk.power shouldBe 5
            wraith.power shouldBe 3
        }
    }

    // CR 120.2b, 603.2, 305.1: 3 noncombat damage to an opponent (Carol, of three players): Alice exiles three cards and may play them
    @Test
    fun virtueExilesThatManyCardsForNoncombatDamageToAnyOpponent() = scenario(players = 3) {
        alice.battlefield("Mountain", valor)
        alice.hand(spit)
        alice.library("Forest", "Island", "Swamp", "Plains")
        alice.casts(spit, target = "carol")
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.exile.size shouldBe 3
            alice.library.size shouldBe 1
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }
}
