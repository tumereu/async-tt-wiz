package arcanum.engine.sets.fdn

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The FDN Auras, played: the invented names carry the oracle text of Eaten by Piranhas, Imprisoned in the Moon, Witness Protection, Angelic Destiny,
 * Blanchwood Armor, Confiscate, New Horizons, Ordeal of Nylea, Pacifism, Starlight Snare, Twinblade Blessing, Unflinching Courage and Untamed Hunger.
 */
class FdnChunk02AuraScenariosTest {
    private val swallowedByEels = Cards.invent("Swallowed by Eels", "{1}{U}", "Enchantment — Aura", """
        Flash (You may cast this spell any time you could cast an instant.)
        Enchant creature
        Enchanted creature loses all abilities and is a black Skeleton creature with base power and toughness 1/1. (It loses all other colors, card types, and creature types.)
    """)
    private val entombed = Cards.invent("Entombed in the Star", "{2}{U}", "Enchantment — Aura", """
        Enchant creature, land, or planeswalker
        Enchanted permanent is a colorless land with "{T}: Add {C}" and loses all other card types and abilities.
    """)
    private val newAlias = Cards.invent("New Alias", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature loses all abilities and is a green and white Citizen creature with base power and toughness 1/1 named Legitimate Businessperson. (It loses all other colors, card types, creature types, and names.)
    """)
    private val seraphicFate = Cards.invent("Seraphic Fate", "{2}{W}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +4/+4, has flying and first strike, and is an Angel in addition to its other types.
        When enchanted creature dies, return this card to its owner's hand.
    """)
    private val forestbarkMantle = Cards.invent("Forestbark Mantle", "{2}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 for each Forest you control.
    """)
    private val wrongfulSeizure = Cards.invent("Wrongful Seizure", "{4}{U}{U}", "Enchantment — Aura", """
        Enchant permanent
        You control enchanted permanent.
    """)
    private val freshVistas = Cards.invent("Fresh Vistas", "{2}{G}", "Enchantment — Aura", """
        Enchant land
        When this Aura enters, put a +1/+1 counter on target creature you control.
        Enchanted land has "{T}: Add two mana of any one color."
    """)
    private val trialOfTheHuntress = Cards.invent("Trial of the Huntress", "{1}{G}", "Enchantment — Aura", """
        Enchant creature
        Whenever enchanted creature attacks, put a +1/+1 counter on it. Then if it has three or more +1/+1 counters on it, sacrifice this Aura.
        When you sacrifice this Aura, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
    """)
    private val sereneOath = Cards.invent("Serene Oath", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block.
    """)
    private val moonlitShackle = Cards.invent("Moonlit Shackle", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val pairedEdgeGrace = Cards.invent("Paired Edge Grace", "{1}{W}{W}", "Enchantment — Aura", """
        Flash (You may cast this spell any time you could cast an instant.)
        Enchant creature
        Enchanted creature has double strike. (It deals both first-strike and regular combat damage.)
    """)
    private val steadfastVigor = Cards.invent("Steadfast Vigor", "{1}{G}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and has trample and lifelink. (Damage dealt by the creature also causes its controller to gain that much life.)
    """)
    private val feralCraving = Cards.invent("Feral Craving", "{2}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+1 and has menace. (It can't be blocked except by two or more creatures.)
    """)

    private val bear = Cards.invent("Reedmarsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Tallgrass Ox", "{G}", "Creature — Ox", "", "1/1")
    private val walkingOx = Cards.invent("Hillside Ox", "{G}", "Creature — Ox", "", "1/1")
    private val flyingGolem = Cards.invent("Stoneframe Golem", "{4}", "Artifact Creature — Golem", "Flying", "4/4")
    private val walker = Cards.invent("Verge Oracle", "{1}{U}", "Legendary Planeswalker — Oracle", "[+1]: You gain 1 life.", loyalty = "3")
    private val changeling = Cards.invent("Everyshape Mite", "{1}", "Creature — Shapeshifter", "Changeling", "1/1")
    private val anvil = Cards.invent("Plain Anvil", "{1}", "Artifact", "")
    private val slay = Cards.invent("Ruinous Decree", "{B}", "Instant", "Destroy target creature.")
    private val unweave = Cards.invent("Unweave the Spell", "{W}", "Instant", "Destroy target enchantment.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 613.1d-g: the card type, color and creature type are set, the abilities go, the base is 1/1; 205.1a: setting the type drops artifact and Golem
    @Test
    fun eelsMakeAnArtifactFlyerAPlainBlackSkeleton() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(swallowedByEels)
        bob.battlefield(flyingGolem, counters = listOf(p1p1 to 1))
        alice.casts(swallowedByEels, target = flyingGolem)
        allPass()
        expect {
            card(flyingGolem).types shouldBe listOf(CardType.Creature)
            card(flyingGolem).subtypes shouldBe listOf(Subtype("Skeleton"))
            card(flyingGolem).colors shouldBe listOf(Color.Black)
            card(flyingGolem) lacksKeyword Keyword.Flying.kind
            // CR 613.4c: the counter still counts over the new base
            card(flyingGolem).power shouldBe 2
            card(flyingGolem).toughness shouldBe 2
            card(swallowedByEels).zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.8a: on an opponent's turn
    @Test
    fun eelsHaveFlash() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Island", "Island")
        alice.hand(swallowedByEels)
        bob.battlefield(bear)
        bob.passes()
        alice.casts(swallowedByEels, target = bear)
        allPass()
        expect { card(bear).power shouldBe 1 }
    }

    // CR 205.1a: "land" is set and the others go, Bear with creature; the Aura stays as it can still enchant a land (CR 704.5m); the only mana is {C}
    @Test
    fun entombedCreatureIsAColorlessLand() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(entombed)
        bob.battlefield(bear)
        alice.casts(entombed, target = bear)
        allPass()
        expect {
            card(bear).types shouldBe listOf(CardType.Land)
            card(bear).subtypes shouldBe emptyList<Subtype>()
            card(bear).colors shouldBe emptyList<Color>()
            card(bear).power shouldBe null
            card(bear).zone shouldBe Zone.Battlefield
            card(entombed).zone shouldBe Zone.Battlefield
        }
        alice.passes()
        bob.activates(bear)
        expect { bob.pool.map { it.color } shouldBe listOf<Color?>(null) }
    }

    // CR 205.1a: no land type is set, so a Forest stays a Forest; CR 305.6 and 613.1f: its intrinsic ability is lost with the rest, so only {C} is left
    @Test
    fun entombedForestKeepsItsTypeButMakesOnlyColorless() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(entombed)
        bob.battlefield("Forest")
        alice.casts(entombed, target = "Forest")
        allPass()
        alice.passes()
        expect { card("Forest").subtypes shouldBe listOf(Subtype("Forest")) }
        bob.activates("Forest")
        expect { bob.pool.map { it.color } shouldBe listOf<Color?>(null) }
        assertFailsWith<Throwable> { bob.activates("Forest", ability = 1) }
    }

    // CR 205.1a: every creature type goes with the creature type too; CR 702.73a: changeling's come first (CR 613.3), so this set of types is what's left
    @Test
    fun entombedChangelingHasNoCreatureTypes() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(entombed)
        bob.battlefield(changeling)
        alice.casts(entombed, target = changeling)
        allPass()
        expect { card(changeling).view.characteristics?.allCreatureTypes shouldBe false }
    }

    // CR 306.9 is only for planeswalkers: the former one stays with no loyalty left to matter, and has no loyalty ability
    @Test
    fun entombedPlaneswalkerLosesItsAbilities() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(entombed)
        bob.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.casts(entombed, target = walker)
        allPass()
        expect {
            card(walker).types shouldBe listOf(CardType.Land)
            card(walker).subtypes shouldBe emptyList<Subtype>()
        }
    }

    // CR 702.5a, 601.2c: an artifact isn't in the list of what it can enchant
    @Test
    fun entombedCantEnchantAnArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(entombed)
        bob.battlefield(anvil)
        assertFailsWith<Throwable> { alice.casts(entombed, target = anvil) }
    }

    // CR 612.8: the name is set; 613.1d-g: two colors, the one creature type, base 1/1
    @Test
    fun newAliasRenamesAndRecolors() = scenario {
        alice.battlefield("Island")
        alice.hand(newAlias)
        bob.battlefield(flyingGolem)
        alice.casts(newAlias, target = flyingGolem)
        allPass()
        expect {
            card(flyingGolem).view.name shouldBe "Legitimate Businessperson"
            card(flyingGolem).colors.toSet() shouldBe setOf(Color.Green, Color.White)
            card(flyingGolem).types shouldBe listOf(CardType.Creature)
            card(flyingGolem).subtypes shouldBe listOf(Subtype("Citizen"))
            card(flyingGolem).power shouldBe 1
            card(flyingGolem).toughness shouldBe 1
            card(flyingGolem) lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 205.1b: "in addition to its other types" keeps Bear; 613.4c, 613.1f
    @Test
    fun seraphicFateAddsAngelAndKeepsBear() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(seraphicFate)
        alice.battlefield(bear)
        alice.casts(seraphicFate, target = bear)
        allPass()
        expect {
            card(bear).subtypes.toSet() shouldBe setOf(Subtype("Bear"), Subtype("Angel"))
            card(bear).power shouldBe 6
            card(bear).toughness shouldBe 6
            card(bear) hasKeyword Keyword.Flying.kind
            card(bear) hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 603.10a: the creature's death looks back; CR 704.5m: the Aura is in the graveyard by then, and returns to its owner's hand, not the creature's owner
    @Test
    fun seraphicFateReturnsWhenTheCreatureDies() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp")
        alice.hand(seraphicFate, slay)
        bob.battlefield(bear)
        alice.casts(seraphicFate, target = bear)
        allPass()
        alice.casts(slay, target = bear)
        allPass()
        expect {
            card(bear).zone shouldBe Zone.Graveyard
            card(seraphicFate).zone shouldBe Zone.Graveyard
            state.stack.size shouldBe 1
        }
        allPass()
        expect {
            alice.hand has seraphicFate
            bob.hand lacks seraphicFate
        }
    }

    // CR 603.6c: the Aura alone going is no death of the creature
    @Test
    fun seraphicFateDestroyedAloneStaysInTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(seraphicFate, unweave)
        alice.battlefield(bear)
        alice.casts(seraphicFate, target = bear)
        allPass()
        alice.casts(unweave, target = seraphicFate)
        allPass()
        expect {
            card(seraphicFate).zone shouldBe Zone.Graveyard
            card(bear).power shouldBe 2
            state.stack.size shouldBe 0
        }
    }

    // CR 613.4c: counts the Forests of the Aura's controller, changing as they change
    @Test
    fun mantleCountsYourForests() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(forestbarkMantle, "Forest")
        bob.battlefield(bear, "Forest", "Forest")
        alice.casts(forestbarkMantle, target = bear)
        allPass()
        expect {
            card(bear).power shouldBe 5
            card(bear).toughness shouldBe 5
        }
        alice.plays("Forest")
        expect { card(bear).power shouldBe 6 }
    }

    // CR 613.1b: control follows the Aura, and goes back when it's gone
    @Test
    fun seizureTakesControlUntilItsGone() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Plains")
        alice.hand(wrongfulSeizure, unweave)
        bob.battlefield(bear)
        alice.casts(wrongfulSeizure, target = bear)
        allPass()
        expect { card(bear).controller shouldBe alice.id }
        alice.casts(unweave, target = wrongfulSeizure)
        allPass()
        expect { card(bear).controller shouldBe bob.id }
    }

    // CR 702.5a: "permanent" is any of them
    @Test
    fun seizureEnchantsAnyPermanent() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(wrongfulSeizure)
        bob.battlefield(anvil)
        alice.casts(wrongfulSeizure, target = anvil)
        allPass()
        expect { card(anvil).controller shouldBe alice.id }
    }

    // CR 603.3d: the enters trigger targets one of your creatures, and not the opponent's
    @Test
    fun vistasCounterOnYourCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(freshVistas)
        bob.battlefield(ox)
        alice.casts(freshVistas, target = "Forest")
        allPass()
        allPass()
        expect {
            card(bear).counters(p1p1) shouldBe 1
            card(ox).counters(p1p1) shouldBe 0
        }
    }

    // CR 106.5: "two mana of any one color" is one choice, two mana of that color; the land keeps its own ability, this one granted second
    @Test
    fun vistasLandMakesTwoOfOneColor() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain", bear)
        alice.hand(freshVistas)
        alice.casts(freshVistas, target = "Mountain", paying = listOf("Forest", "Forest", "Forest"))
        allPass()
        allPass()
        alice.activates("Mountain", ability = 1, color = Color.Blue)
        expect { alice.pool.map { it.color } shouldBe listOf<Color?>(Color.Blue, Color.Blue) }
    }

    private fun arcanum.engine.scenario.Scenario.attackWithTrial() {
        alice.battlefield("Forest", "Forest")
        alice.hand(trialOfTheHuntress)
        alice.casts(trialOfTheHuntress, target = bear)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
    }

    // CR 508.1m, 608.2c: the counter goes on the creature, and with one on it the Aura stays
    @Test
    fun trialPutsACounterOnTheCreatureAndStays() = scenario {
        alice.battlefield(bear)
        attackWithTrial()
        allPass()
        expect {
            card(bear).counters(p1p1) shouldBe 1
            card(trialOfTheHuntress).counters(p1p1) shouldBe 0
            card(trialOfTheHuntress).zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2c: "it" is the enchanted creature, whose third counter makes the Aura go; CR 701.21a, 603.2: then it fetches two basics, tapped, and shuffles
    @Test
    fun trialSacrificesOnTheCreaturesThirdCounterAndFetchesTwoBasics() = scenario {
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.library("Plains", "Island", "Mountain")
        attackWithTrial()
        allPass()
        expect {
            card(bear).counters(p1p1) shouldBe 3
            card(trialOfTheHuntress).zone shouldBe Zone.Graveyard
        }
        allPass()
        answer(Answer.Objects(listOf(card("Plains"), card("Island"))))
        settle()
        expect {
            card("Plains").zone shouldBe Zone.Battlefield
            card("Plains").tapped shouldBe true
            card("Island").tapped shouldBe true
            card("Mountain").zone shouldBe Zone.Library
        }
    }

    // CR 508.1a
    @Test
    fun oathStopsAttacking() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(sereneOath)
        alice.casts(sereneOath, target = bear)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.attacks(bear) }
    }

    // CR 509.1a
    @Test
    fun oathStopsBlocking() = scenario {
        alice.battlefield("Plains", "Plains", ox)
        alice.hand(sereneOath)
        bob.battlefield(bear)
        alice.casts(sereneOath, target = bear)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bear to ox) }
    }

    // CR 502.3: tapped as it enters, and it stays so through its controller's untap step, while others untap
    @Test
    fun shackleTapsAndKeepsTapped() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(moonlitShackle)
        bob.battlefield(bear)
        bob.battlefield(ox, tapped = true)
        alice.casts(moonlitShackle, target = bear)
        allPass()
        allPass()
        expect { card(bear).tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            card(bear).tapped shouldBe true
            card(ox).tapped shouldBe false
        }
    }

    // CR 702.8a: in combat, after attackers; CR 702.4a: deals damage in both combat damage steps
    @Test
    fun graceGivesDoubleStrikeAtInstantSpeed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(pairedEdgeGrace)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.casts(pairedEdgeGrace, target = bear)
        allPass()
        advanceTo(TurnStep.End)
        expect { bob.life shouldBe 16 }
    }

    // CR 702.19b: lethal to the blocker, the rest to the player; CR 702.15b: the controller gains all the damage dealt
    @Test
    fun vigorTramplesAndLinksLife() = scenario {
        alice.battlefield("Forest", "Forest", "Plains", bear)
        alice.hand(steadfastVigor)
        bob.battlefield(ox)
        alice.casts(steadfastVigor, target = bear)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to bear)
        allPass()
        divideAsSuggested()
        allPass()
        expect {
            card(ox).zone shouldBe Zone.Graveyard
            bob.life shouldBe 17
            alice.life shouldBe 24
        }
    }

    // CR 702.111b: one blocker alone isn't legal, two are
    @Test
    fun cravingNeedsTwoBlockers() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(feralCraving)
        bob.battlefield(ox, walkingOx)
        alice.casts(feralCraving, target = bear)
        allPass()
        expect {
            card(bear).power shouldBe 4
            card(bear).toughness shouldBe 3
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(ox to bear) }
        bob.blocks(ox to bear, walkingOx to bear)
        settle()
        expect { bob.life shouldBe 20 }
    }
}
