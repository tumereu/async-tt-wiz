package arcanum.engine.sets.burn

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.mh2.playOut
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * Cross-set sweep of the burn run's parallel set work (ISD, ONE, RVR, MKM, WOE, LCI, OTJ, MOM, MH3, BRO, DMU, DMR): each set fixed a class
 * of gap in its own mechanics' paths; these probe the sibling paths another set's mechanics use, each with the rule it follows.
 */
class BurnSweepScenariosTest {
    // ---- Damage that becomes counters goes through counter placing (ONE, LCI: infect/toxic, CR 120.3b, 120.3d); sibling: wither onto a
    // creature that can't have -1/-1 counters put on it

    private val stalker = Cards.invent("Blightfang Stalker", "{2}{B}", "Creature — Horror",
        "Wither (This deals damage to creatures in the form of -1/-1 counters.)", "2/2")
    private val wardmoss = Cards.invent("Fernback Grazer", "{1}{G}", "Creature — Beast", "", "1/1")
    private val shackle = Cards.invent("Rimebound Shackle", "{1}{U}", "Enchantment — Aura",
        "Enchant creature\nEnchanted creature can't become untapped and can't have counters put on it.")

    // CR 120.3d, 614.1a: wither damage to a creature is -1/-1 counters put on it, which the Aura's replacement stops: the blocker survives
    @Test
    fun witherDamageCantPutCountersOnACreatureThatCantHaveThem() = scenario(players = 3) {
        alice.battlefield(stalker)
        bob.battlefield(wardmoss, shackle)
        attach(shackle, wardmoss)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stalker, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wardmoss to stalker)
        advanceTo(TurnStep.End)
        expect {
            wardmoss.zone shouldBe Zone.Battlefield
            wardmoss.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0
        }
    }

    // ---- Life lost by other routes counts as losing life (LCI: damage through loseLife, CR 120.3a; MH3: life payments under "can't lose
    // life", CR 119.8); siblings: paying life is losing life (CR 119.4), and a player whose life total can't change can't pay life

    private val witness = Cards.invent("Crimson Tithe Witness", "{4}{B}", "Enchantment",
        "Whenever an opponent loses life, you gain that much life.")
    private val ledger = Cards.invent("Whispering Ledger", "{1}", "Artifact", "Pay 2 life: Draw a card.")
    private val vow = Cards.invent("Stillwater Vow", "{1}{W}", "Instant", "Until end of turn, your life total can't change.")

    // CR 119.4, 603.2: the life an opponent pays for a cost is life they lose: the trigger gains alice that much (3 players)
    @Test
    fun payingLifeIsLosingLife() = scenario(players = 3) {
        turn(active = bob)
        alice.battlefield(witness)
        bob.battlefield(ledger)
        bob.library("Island", "Island")
        bob.activates(ledger)
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            carol.life shouldBe 20
        }
    }

    // CR 119.8, 119.4: a player whose life total can't change can't lose life, so a cost of paying life can't be paid
    @Test
    fun aLifeTotalThatCantChangeCantPayLife() = scenario {
        alice.battlefield(ledger)
        alice.hand(vow)
        alice.library("Island", "Island")
        alice.castsWithoutCosts(vow)
        playOut()
        assertFails { alice.activates(ledger) }
        expect { alice.life shouldBe 20 }
    }

    // ---- "Becomes attached" sees an Aura that enters attached (OTJ, CR 303.4f, 603.2e); sibling: an Aura token created attached (WOE Roles)

    private val captain = Cards.invent("Auraward Captain", "{2}{W}", "Creature — Human Soldier",
        "Whenever an Aura you control becomes attached to a creature, you gain 2 life.", "2/2")
    private val crowning = Cards.invent("Stalwart Crowning", "{1}{G}", "Sorcery",
        "Create a Monster Role token attached to target creature you control. (Enchanted creature gets +1/+1 and has trample.)")

    // CR 303.4f, 603.2e, 111.10r: a Role token created attached becomes attached as it enters, so the trigger sees it
    @Test
    fun anAuraTokenCreatedAttachedBecomesAttached() = scenario {
        alice.battlefield(captain)
        alice.hand(crowning)
        alice.castsWithoutCosts(crowning, target = captain)
        playOut()
        expect {
            alice.battlefield has "Monster"
            alice.life shouldBe 22
        }
    }

    // ---- Delayed triggers belong to the effect, not the permanent (DMR: dash and blitz, CR 608.3g); sibling: unearth (CR 702.84a)

    private val revenant = Cards.invent("Ashgrave Revenant", "{2}{R}", "Creature — Zombie Warrior",
        "Unearth {R} ({R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)",
        "3/2")
    private val hush = Cards.invent("Stifling Hush", "{1}{U}", "Instant", "Target creature loses all abilities until end of turn.")

    // CR 702.84a, 603.7: unearth's "exile it at the beginning of the next end step" is a delayed trigger its ability created, so the creature
    // losing all its abilities doesn't stop it
    @Test
    fun anUnearthedCreatureThatLosesItsAbilitiesIsStillExiled() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard(revenant)
        alice.hand(hush)
        alice.activates(revenant, paying = listOf("Mountain"))
        playOut()
        expect { revenant.zone shouldBe Zone.Battlefield }
        alice.castsWithoutCosts(hush, target = revenant)
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.exile has revenant }
    }

    // ---- "Its controller" of a creature that died is its controller as it last existed (ISD, ONE: CR 603.10a, 110.2); sibling: a stolen
    // creature, with three players

    private val collector = Cards.invent("Gravewatch Collector", "{3}{B}", "Creature — Human Warlock",
        "Whenever a creature an opponent controls dies, its controller loses 2 life.", "2/3")
    private val bruiser = Cards.invent("Ironbark Bruiser", "{2}{G}", "Creature — Beast", "", "3/3")
    private val lure = Cards.invent("Turncoat Lure", "{2}{R}", "Sorcery",
        "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.")
    private val slag = Cards.invent("Slagburst Hurl", "{R}", "Instant", "Slagburst Hurl deals 5 damage to target creature.")

    // CR 603.10a, 110.2: bob's creature dies while alice controls it: alice, its controller as it last existed, loses the life
    @Test
    fun itsControllerOfAStolenCreatureThatDied() = scenario(players = 3) {
        carol.battlefield(collector)
        bob.battlefield(bruiser)
        alice.hand(lure, slag)
        alice.castsWithoutCosts(lure, target = bruiser)
        playOut()
        expect { bruiser.controller shouldBe alice.id }
        alice.castsWithoutCosts(slag, target = bruiser)
        playOut()
        expect {
            bob.graveyard has bruiser
            alice.life shouldBe 18
            bob.life shouldBe 20
        }
    }

    // ---- Type-changing wordings (CR 205.1b; ONE, WOE, RVR, MH2 read them differently): "still a land" keeps every prior type and subtype; a
    // "[creature type] artifact creature" keeps its other types but replaces its creature types, with "It's still a land." too

    private val foundry = Cards.invent("Twofold Foundry", null, "Land", """
        {T}: Add {C}.
        {G}: This land becomes a 3/3 Elemental creature until end of turn. It's still a land.
        {R}: This land becomes a 2/2 Assembly-Worker artifact creature until end of turn. It's still a land.
    """)

    // CR 205.1b: an Elemental land creature that becomes an "Assembly-Worker artifact creature" loses its Elemental creature type
    @Test
    fun anArtifactCreatureOfACreatureTypeReplacesCreatureTypesThoughStillALand() = scenario {
        alice.battlefield(foundry, "Forest", "Mountain")
        alice.activates(foundry, ability = 1, paying = listOf("Forest"))
        playOut()
        alice.activates(foundry, ability = 2, paying = listOf("Mountain"))
        playOut()
        expect {
            foundry.subtypes shouldBe listOf(arcanum.api.Subtype("Assembly-Worker"))
            foundry.types.toSet() shouldBe setOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature, arcanum.api.CardType.Land)
        }
    }

    // CR 205.1b: "still a land" keeps all prior subtypes: the Assembly-Worker that becomes an Elemental creature is both
    @Test
    fun stillALandKeepsPriorCreatureTypes() = scenario {
        alice.battlefield(foundry, "Forest", "Mountain")
        alice.activates(foundry, ability = 2, paying = listOf("Mountain"))
        playOut()
        alice.activates(foundry, ability = 1, paying = listOf("Forest"))
        playOut()
        expect { foundry.subtypes.toSet() shouldBe setOf(arcanum.api.Subtype("Assembly-Worker"), arcanum.api.Subtype("Elemental")) }
    }

    // ---- A land played by an effect is a land play (BRO: CR 305.2b, 305.3); sibling: a land put onto the battlefield isn't (CR 305.4)

    private val surveyor = Cards.invent("Rootwalk Surveyor", "{1}{G}", "Sorcery", "Draw a card. You may put a land card from your hand onto the battlefield.")

    // CR 305.4: putting a land onto the battlefield isn't playing it: alice may still play her land for the turn
    @Test
    fun aLandPutOntoTheBattlefieldDoesntUseTheLandPlay() = scenario {
        alice.hand(surveyor, "Forest", "Island")
        alice.library("Swamp")
        alice.castsWithoutCosts(surveyor)
        playOut("Forest")
        expect { alice.battlefield has "Forest" }
        alice.plays("Island")
        expect { alice.battlefield has "Island" }
    }

    // ---- Replacements of putting counters see only their kind (LCI: token multipliers per named kind, CR 614.1a); sibling: counters of two
    // kinds put on one creature at once by proliferate

    private val tallyman = Cards.invent("Vinebound Tallyman", "{1}{G}", "Creature — Elf Druid",
        "If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.", "1/1")
    private val duelist = Cards.invent("Oathsworn Duelist", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val swell = Cards.invent("Quiet Swell", "{G}", "Instant", "Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")

    // CR 701.34a, 614.1a: proliferating a creature with a +1/+1 counter and a flying counter: one more +1/+1 counter from the replacement, the
    // flying counter alone
    @Test
    fun aCounterReplacementOfOneKindLeavesTheOtherKindAlone() = scenario {
        alice.battlefield(tallyman)
        alice.battlefield(duelist, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Keyword(arcanum.api.Keyword.Flying.kind) to 1))
        alice.hand(swell)
        alice.castsWithoutCosts(swell)
        playOut(duelist)
        expect {
            duelist.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            duelist.counters(CounterKind.Keyword(arcanum.api.Keyword.Flying.kind)) shouldBe 2
        }
    }
}
