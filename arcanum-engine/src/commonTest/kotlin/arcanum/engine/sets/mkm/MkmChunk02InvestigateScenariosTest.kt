package arcanum.engine.sets.mkm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MKM chunk 02: investigate (CR 701.16a) and what the chunk's cards do around it, a Case's "To solve" (CR 719.3a) among them. */
class MkmChunk02InvestigateScenariosTest {
    private val perch = Cards.invent("Case of the Plundered Perch", "{U}", "Enchantment — Case", """
        When this Case enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        To solve — You control three or more artifacts. (If unsolved, solve at the beginning of your end step.)
        Solved — {2}{U}, Sacrifice this Case: Put four +1/+1 counters on target noncreature artifact. It becomes a 0/0 Bird creature with flying in addition to its other types.
    """)
    private val wrackage = Cards.inventFaces("Split",
        face("Driftwood", "{1}{G/U}", "Instant", """Mill three cards. Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""", null, null, null),
        face("Wrackage", "{4}{U/B}{U/B}", "Sorcery",
            "Each opponent mills three cards, then you may cast a spell from each opponent's graveyard without paying its mana cost. If a spell cast this way would be put into a graveyard, exile it instead.",
            null, null, null))
    private val tinkerer = Cards.invent("Evidentiary Tinkerer", "{2}{U}", "Creature — Vedalken Artificer Detective", """
        Whenever you cast an artifact spell, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        Activated abilities of artifacts you control cost {1} less to activate. This effect can't reduce the mana in that cost to less than one mana.
    """, "2/3")
    private val inquirers = Cards.invent("Brash Inquirers", "{5}{U}", "Creature — Vedalken Detective",
        """When this creature enters, return up to one other target creature to its owner's hand. If you controlled it, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
        "4/4")
    private val onlooker = Cards.invent("Unlucky Onlooker", "{1}{R}", "Creature — Goblin Citizen",
        """Whenever this creature is dealt 3 or more damage, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""", "2/1")
    private val vessiv = Cards.invent("Vessiv, Wearer of Masks", "{U}{B}", "Legendary Creature — Shapeshifter Detective", """
        Whenever Vessiv attacks, exile target card from a graveyard, then investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        Whenever you sacrifice a Clue, you may have Vessiv become a copy of a creature card exiled with it until end of turn.
    """, "2/3")
    private val silence = Cards.invent("Silence the Witnesses", "{2}{W}{W}", "Sorcery",
        """Each player who controls the most creatures investigates. Then destroy all creatures. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""")
    private val questioning = Cards.invent("Zealous Questioning", "{W}{U}", "Instant", """
        This spell costs {W}{U} more to cast for each target beyond the first.
        Choose any number of target players. Investigate X times, where X is the total number of creatures those players control.
    """)
    private val scorch = Cards.invent("Scorch the Informant", "{X}{R}", "Sorcery",
        """Scorch the Informant deals twice X damage to target creature. If excess damage was dealt to that creature this way, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""")

    private val flare = Cards.invent("Alley Flare", "{R}", "Instant", "Alley Flare deals 3 damage to target creature.")
    private val satchel = Cards.invent("Evidence Satchel", "{1}", "Artifact — Clue", "{2}, Sacrifice this artifact: Draw a card.")
    private val gizmo = Cards.invent("Copperwound Trinket", "{1}", "Artifact", "{1}, {T}: You gain 1 life.")
    private val skiff = Cards.invent("Wrecked Skiff", "{1}", "Artifact", "{1}{U}, {T}: Draw a card.")

    private fun Scenario.clues(): Int = state.objects.values.count { o -> o.zone == arcanum.api.Zone.Battlefield && nameOf(o) == "Clue Token" }

    // CR 719.3a: with fewer than three artifacts the Case stays unsolved at your end step, and its solved ability can't be activated
    // (CR 702.169d); with three it becomes solved and the ability makes a noncreature artifact a 4/4 flying Bird for good (CR 613.1d, f)
    @Test
    fun caseSolvesAtYourEndStepWithThreeArtifacts() = scenario {
        alice.battlefield(perch, Plain.idol, satchel, Plain.golem, "Island", "Island", "Island")
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.activates(perch, target = Plain.idol) }
        advanceTo(TurnStep.End)
        resolveAll()
        alice.activates(perch, target = Plain.idol)
        resolveAll()
        expect {
            perch.zone shouldBe arcanum.api.Zone.Graveyard
            Plain.idol.types.contains(CardType.Creature) shouldBe true
            Plain.idol.subtypes.map { it.name }.contains("Bird") shouldBe true
            Plain.idol.power shouldBe 4
            Plain.idol.toughness shouldBe 4
            Plain.idol hasKeyword Keyword.Flying.kind
        }
    }

    // CR 719.3a: the condition is checked at the beginning of the end step (CR 603.4): two artifacts aren't enough
    @Test
    fun caseWithTwoArtifactsStaysUnsolved() = scenario {
        alice.battlefield(perch, Plain.idol, satchel, "Island", "Island", "Island")
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(state.stack.isEmpty())
        assertFailsWith<IllegalArgumentException> { alice.activates(perch, target = Plain.idol) }
    }

    // CR 608.2g: each opponent mills, then a card of each opponent's graveyard is cast without paying its mana cost (CR 601.2); CR 614.1a:
    // each of them is exiled instead of going to its owner's graveyard
    @Test
    fun wrackageCastsASpellFromEachOpponentsGraveyard() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(wrackage)
        alice.library("Island", "Island")
        bob.library(Plain.study, "Swamp", "Swamp", "Swamp")
        carol.library(Plain.heal, "Plains", "Plains", "Plains")
        alice.casts(wrackage, way = arcanum.engine.input.CastWay(face = 1))
        allPass()
        expect {
            bob.graveyard has Plain.study
            carol.graveyard has Plain.heal
        }
        alice.chooses(Plain.study)
        alice.chooses(Plain.heal)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.hand.size shouldBe 1
            bob.exile has Plain.study
            carol.exile has Plain.heal
            bob.graveyard lacks Plain.study
            carol.graveyard lacks Plain.heal
        }
    }

    // CR 602.2b, 601.2f: {2} costs {1}, {1}{U} costs {U}; CR 118.7: a {1} cost can't fall to no mana at all
    @Test
    fun tinkererReducesArtifactAbilitiesToNoLessThanOneMana() = scenario {
        alice.battlefield(tinkerer, satchel, gizmo, skiff, "Island", "Island")
        alice.library("Island", "Island", "Island")
        alice.activates(satchel)
        resolveAll()
        alice.activates(skiff)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            satchel.zone shouldBe arcanum.api.Zone.Graveyard
        }
        alice.activates(gizmo)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 608.2h: "if you controlled it" is the creature as it last existed: your own returned creature gives a Clue
    @Test
    fun inquirersReturningYourOwnCreatureInvestigates() = scenario(players = 3) {
        alice.battlefield(Plain.bear, "Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(Plain.ogre)
        alice.hand(inquirers)
        alice.casts(inquirers)
        allPass()
        resolveAll()
        targets(Plain.bear)
        resolveAll()
        expect {
            alice.hand has Plain.bear
        }
        assertEquals(1, clues())
    }

    // CR 608.2h: an opponent's creature returned gives no Clue
    @Test
    fun inquirersReturningAnOpponentsCreatureDoesntInvestigate() = scenario(players = 3) {
        alice.battlefield(Plain.bear, "Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(Plain.ogre)
        alice.hand(inquirers)
        alice.casts(inquirers)
        allPass()
        resolveAll()
        targets(Plain.ogre)
        resolveAll()
        expect {
            bob.hand has Plain.ogre
        }
        assertEquals(0, clues())
    }

    // CR 120.4, 603.2c: 3 damage dealt to it triggers even as it dies; 2 doesn't
    @Test
    fun onlookerInvestigatesOnlyForThreeOrMoreDamage() = scenario {
        alice.battlefield(onlooker, "Mountain", "Mountain")
        alice.hand(Plain.shock)
        alice.casts(Plain.shock, target = onlooker)
        resolveAll()
        assertEquals(0, clues())
    }

    @Test
    fun onlookerDealtThreeDamageInvestigates() = scenario {
        alice.battlefield(onlooker, "Mountain")
        alice.hand(flare)
        alice.casts(flare, target = onlooker)
        resolveAll()
        expect { onlooker.zone shouldBe arcanum.api.Zone.Graveyard }
        assertEquals(1, clues())
    }

    // CR 607.2a: the card its attack trigger exiled is "exiled with it"; CR 707.2: sacrificing a Clue makes it a copy of that card until end of
    // turn (CR 514.2)
    @Test
    fun vessivBecomesACopyOfTheCardItExiled() = scenario {
        alice.battlefield(vessiv, "Island", "Island")
        alice.library("Island", "Island")
        bob.graveyard(Plain.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vessiv)
        resolveAll()
        targetsIfAsked(Plain.ogre)
        resolveAll()
        expect { bob.exile has Plain.ogre }
        assertEquals(1, clues())
        alice.activates("Clue Token")
        resolveAll()
        alice.chooses(Plain.ogre)
        resolveAll()
        expect {
            vessiv.power shouldBe 3
            vessiv.toughness shouldBe 3
        }
    }

    // CR 101.4: the players tied for the most creatures each investigate at once; then every creature is destroyed
    @Test
    fun silenceTheWitnessesCluesForThePlayersWithTheMostCreatures() = scenario(players = 3) {
        alice.battlefield(Plain.bear, Plain.elf, "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(Plain.ogre, Plain.giant)
        carol.battlefield(Plain.turtle)
        alice.hand(silence)
        alice.casts(silence)
        resolveAll()
        val owners = state.objects.values.filter { nameOf(it) == "Clue Token" && it.zone == arcanum.api.Zone.Battlefield }.map { it.controller }
        assertEquals(setOf(alice.id, bob.id), owners.toSet())
        assertEquals(2, owners.size)
        expect {
            Plain.bear.zone shouldBe arcanum.api.Zone.Graveyard
            Plain.giant.zone shouldBe arcanum.api.Zone.Graveyard
            Plain.turtle.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 601.2f: two target players cost {W}{U} more; CR 608.2c: the creatures those players control are counted as it resolves
    @Test
    fun zealousQuestioningCountsTheCreaturesOfTheTargetPlayers() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Island", "Island")
        bob.battlefield(Plain.ogre, Plain.giant)
        carol.battlefield(Plain.turtle)
        alice.hand(questioning)
        alice.casts(questioning, targets = listOf("bob", "carol"))
        resolveAll()
        assertEquals(3, clues())
    }

    // CR 601.2f: with a second target, three lands can't pay {W}{U}{W}{U}
    @Test
    fun zealousQuestioningCostsMoreForEachTargetBeyondTheFirst() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Island")
        bob.battlefield(Plain.ogre)
        carol.battlefield(Plain.turtle)
        alice.hand(questioning)
        alice.casts(questioning, targets = listOf("bob", "carol"))
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 107.3a, 120.4a: twice X is 4 for X = 2; 4 damage to a 3/3 is 1 excess, so it investigates; 2 damage to a 2/2 has none
    @Test
    fun scorchInvestigatesOnlyWithExcessDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(Plain.ogre, Plain.bear)
        alice.hand(scorch)
        alice.casts(scorch, target = Plain.bear, x = 1)
        resolveAll()
        assertEquals(0, clues())
        move(scorch, arcanum.api.Zone.Hand)
        alice.casts(scorch, target = Plain.ogre, x = 2)
        resolveAll()
        expect { Plain.ogre.zone shouldBe arcanum.api.Zone.Graveyard }
        assertEquals(1, clues())
    }
}
