package arcanum.engine.sets.bro

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
import arcanum.api.Subtype
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Brothers' War chunk 01 in play: Powerstone tokens (CR 111.10h) and the chunk's new wordings. */
class BroChunk01PowerstoneScenariosTest {
    private val powerstoneReminder = "(It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")"

    /** A card with the Powerstone token's own definition, to have one untapped from the start. */
    private val powerstone = Cards.define("Powerstone Proxy", CardDefinition(Layout.Normal,
        listOf(PredefinedTokens.face("Powerstone")!!.copy(name = "Powerstone Proxy"))))

    private val harshLesson = Cards.invent("Harsh Lesson", "{2}{U}", "Instant",
        "Draw two cards, then discard a card. Create a tapped Powerstone token. $powerstoneReminder")
    private val stoneSalvageUnit = Cards.invent("Stone Salvage Unit", "{4}", "Artifact Creature — Construct",
        "When this creature enters, create a tapped Powerstone token. $powerstoneReminder", "2/3")
    private val rockhoundScholar = Cards.invent("Rockhound Scholar", "{3}{U}{U}", "Creature — Human Artificer", """
        At the beginning of your end step, create a tapped Powerstone token. $powerstoneReminder
        {6}: Draw a card and put a +1/+1 counter on this creature.
    """, "2/5")
    private val thicketOpportunist = Cards.invent("Thicket Opportunist", "{2}{G}", "Creature — Human Scout",
        "When this creature enters, create a tapped Powerstone token. $powerstoneReminder", "3/2")
    private val chargedSnare = Cards.invent("Charged Snare", "{3}{W}", "Enchantment", """
        When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.
        When this enchantment enters, you gain 2 life and create a tapped Powerstone token. $powerstoneReminder
    """)
    private val fadedHistories = Cards.invent("Faded Histories", "{2}{G}{G}", "Sorcery",
        "Each player who controls an artifact or enchantment creates a 2/2 green Bear creature token. Then destroy all artifacts and enchantments.")
    private val cleavingTheStone = Cards.invent("Cleaving the Stone", "{2}{U}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact.
        Create two tapped Powerstone tokens. If the sacrificed artifact was legendary, draw a card. (The tokens are artifacts with "{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.")
    """)
    private val sandseaProspector = Cards.invent("Sandsea Prospector", "{4}{W}", "Creature — Human Artificer",
        "When this creature enters, create a tapped Powerstone token for each other creature you control. " +
            "(They're artifacts with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")", "3/2")
    private val ozarPrinceOfKelm = Cards.invent("Ozar, Prince of Kelm", "{2}{W}{U}", "Legendary Creature — Human Artificer", """
        Artifact creatures you control get +2/+2.
        {6}: Create a token that's a copy of target artifact you control, except it's a 1/1 Soldier creature in addition to its other types.
    """, "2/3")
    private val legendaryArtifact = "Opaline Trinket"

    private fun Scenario.powerstones(player: ScenarioPlayer) = player.battlefield.names.count { it == "Powerstone Token" }

    // CR 106.6, 111.10h: Powerstone mana can't pay for a nonartifact spell
    @Test
    fun powerstoneManaCantCastANonartifactSpell() = scenario {
        alice.battlefield(powerstone, "Island", "Island")
        alice.hand(harshLesson)
        alice.casts(harshLesson)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 106.6, 111.10h: it pays for an artifact spell; the creature's enters trigger makes a tapped colorless Powerstone artifact token
    @Test
    fun powerstoneManaCastsAnArtifactSpellWhoseTokenEntersTapped() = scenario {
        alice.battlefield(powerstone, "Island", "Island", "Island")
        alice.hand(stoneSalvageUnit)
        alice.casts(stoneSalvageUnit, paying = listOf(powerstone, "Island", "Island", "Island"))
        allPass()
        allPass()
        expect {
            alice.battlefield has stoneSalvageUnit
            "Powerstone Token".tapped shouldBe true
            "Powerstone Token".types shouldBe listOf(CardType.Artifact)
            "Powerstone Token".subtypes shouldBe listOf(Subtype("Powerstone"))
            "Powerstone Token".colors shouldBe emptyList()
        }
    }

    // CR 106.6: the restriction is on casting nonartifact spells only; an activated ability may use the mana
    @Test
    fun powerstoneManaPaysForAnActivatedAbility() = scenario {
        alice.battlefield(rockhoundScholar, powerstone, "Island", "Island", "Island", "Island", "Island")
        alice.library("Island")
        alice.activates(rockhoundScholar, paying = listOf(powerstone, "Island", "Island", "Island", "Island", "Island"))
        allPass()
        expect {
            alice.hand has "Island"
            rockhoundScholar.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 101.4, 608.2c: with three players, only those who control an artifact or enchantment as it resolves get a Bear; then the
    // artifacts and enchantments are destroyed
    @Test
    fun fadedHistoriesGivesBearsOnlyToArtifactOrEnchantmentControllers() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", stoneSalvageUnit)
        bob.battlefield(chargedSnare)
        carol.battlefield(thicketOpportunist)
        alice.hand(fadedHistories)
        alice.casts(fadedHistories)
        allPass()
        expect {
            alice.battlefield has "Bear Token"
            bob.battlefield has "Bear Token"
            carol.battlefield lacks "Bear Token"
            alice.graveyard has stoneSalvageUnit
            bob.graveyard has chargedSnare
            carol.battlefield has thicketOpportunist
        }
    }

    // CR 608.2h: the sacrificed artifact was legendary: two tapped Powerstones and a card
    @Test
    fun cleavingTheStoneDrawsForALegendaryArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island", legendaryArtifact)
        alice.hand(cleavingTheStone)
        alice.library("Plains")
        alice.casts(cleavingTheStone, choosing = listOf(legendaryArtifact))
        allPass()
        expect {
            alice.graveyard has legendaryArtifact
            alice.hand has "Plains"
            powerstones(alice) shouldBe 2
            alice.battlefield.names.size shouldBe 5
        }
    }

    // CR 608.2h: a nonlegendary sacrificed artifact draws nothing
    @Test
    fun cleavingTheStoneDrawsNothingForANonlegendaryArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island", stoneSalvageUnit)
        alice.hand(cleavingTheStone)
        alice.library("Plains")
        alice.casts(cleavingTheStone, choosing = listOf(stoneSalvageUnit))
        allPass()
        expect {
            alice.graveyard has stoneSalvageUnit
            alice.hand lacks "Plains"
            powerstones(alice) shouldBe 2
        }
    }

    // CR 608.2h: one Powerstone for each other creature you control as the trigger resolves; an opponent's creature doesn't count
    @Test
    fun sandseaProspectorCountsYourOtherCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", thicketOpportunist, rockhoundScholar)
        bob.battlefield(stoneSalvageUnit)
        alice.hand(sandseaProspector)
        alice.casts(sandseaProspector)
        allPass()
        allPass()
        expect { powerstones(alice) shouldBe 2 }
    }

    // CR 707.9b: the copy of a noncreature artifact is also a 1/1 Soldier creature; CR 613.4b-c: then the +2/+2 applies (3/3)
    @Test
    fun ozarPrinceOfKelmCopiesAnArtifactAsASoldierCreature() = scenario {
        alice.battlefield(ozarPrinceOfKelm, powerstone, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(ozarPrinceOfKelm, target = powerstone, paying = listOf("Island", "Island", "Island", "Island", "Island", "Island"))
        allPass()
        expect {
            val copy = card(powerstone, 1)
            copy.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            copy.subtypes shouldBe listOf(Subtype("Powerstone"), Subtype("Soldier"))
            copy.power shouldBe 3
            copy.toughness shouldBe 3
            card(powerstone, 0).power shouldBe null
        }
    }
}
