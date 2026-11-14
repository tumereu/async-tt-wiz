package arcanum.engine.sets.otj

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Supertype
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dsk.may
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** OTJ chunk 06: tokens with quoted abilities, copying abilities that target, extra mana from artifact tokens. */
class OtjChunk06CreateScenariosTest {
    private val marta = Cards.invent("Marta Vell, Timberline", "{3}{G}{U}{U}", "Legendary Creature — Giant Scout", """
        Reach
        When Marta Vell enters, create Cobb, a legendary blue Ox creature token with "Cobb's power and toughness are each equal to the number of lands you control."
        Whenever you attack, draw a card, then you may put a land card from your hand or graveyard onto the battlefield.
    """, "6/5")
    private val ostra = Cards.invent("Ostra Quill, Range Tutor", "{2}{R}{W}", "Legendary Creature — Kor Advisor", """
        When Ostra Quill enters, create a 1/1 red Mercenary creature token with "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery."
        Whenever you activate an ability that targets a creature or player, copy that ability. You may choose new targets for the copy.
    """, "2/4")
    private val corvina = Cards.invent("Corvina, Skyfall Seer", "{3}{R}{G}", "Legendary Creature — Cat Druid", """
        Whenever Corvina enters or attacks, create a tapped colorless artifact token named Skyrock with "When this token enters, it deals 2 damage to any target" and "{T}: Add one mana of any color."
        Whenever you tap an artifact token for mana, add one mana of any type that artifact token produced.
    """, "4/3")
    private val recruit = Cards.invent("Gunhand Recruit", "{R}", "Creature — Human Mercenary",
        "{T}: Target creature you control gets +1/+0 until end of turn. Activate only as a sorcery.", "1/1")
    private val pistol = Cards.invent("Rustler's Pistol", "{1}", "Artifact", "{T}: Target player loses 1 life.")
    private val ration = Cards.invent("Campfire Ration", "{1}", "Artifact", "{T}: You gain 1 life.")
    private val find = Cards.invent("Prospector's Find", "{R}", "Sorcery", "Create a Treasure token.")
    private val lockbox = Cards.invent("Rusted Lockbox", "{2}", "Artifact", "{T}: Add {C}.")
    private val grizzly = Cards.invent("Mesa Grizzly", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 111.4: the token is named Cobb and legendary; CR 604.3, 201.5: its power and toughness are the lands its controller controls
    @Test
    fun theLegendaryTokenCountsYourLands() = scenario {
        alice.battlefield("Forest", "Forest", "Island")
        alice.hand(marta)
        alice.castsWithoutCosts(marta)
        resolveAll()
        expect {
            "Cobb".power shouldBe 3
            "Cobb".toughness shouldBe 3
            ("Cobb".view.characteristics!!.supertypes.contains(Supertype.Legendary)) shouldBe true
            "Cobb".colors shouldBe listOf(Color.Blue)
        }
    }

    // CR 602.2b, 707.10: the activated ability that targets a creature you control is copied; both resolve
    @Test
    fun copiesAnAbilityThatTargetsACreature() = scenario {
        alice.battlefield(ostra, recruit, grizzly)
        alice.activates(recruit, target = grizzly)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { grizzly.power shouldBe 4 }
    }

    // CR 707.10c: the copy may target another player; with three players, both opponents lose life
    @Test
    fun theCopyMayTargetAnotherPlayer() = scenario(players = 3) {
        alice.battlefield(ostra, pistol)
        alice.activates(pistol, target = "bob")
        resolveAll()
        may(true)
        answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 115.1b: an ability without a target isn't copied; CR 603.2: an opponent's activation doesn't trigger "you activate"
    @Test
    fun abilitiesWithoutTargetsAndOpponentsAbilitiesAreNotCopied() = scenario {
        alice.battlefield(ostra, ration)
        bob.battlefield(pistol)
        alice.activates(ration)
        resolveAll()
        alice.passes()
        bob.activates(pistol, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // CR 111.4: the token enters tapped; CR 603.2: its own enters trigger deals 2 damage
    @Test
    fun theTappedTokenDealsTwoDamageAsItEnters() = scenario {
        alice.hand(corvina)
        alice.castsWithoutCosts(corvina)
        playOut("bob")
        expect {
            bob.life shouldBe 18
            "Skyrock".tapped shouldBe true
            "Skyrock".types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 106.12: a Treasure token tapped (and sacrificed) for mana adds one more of its type; CR 106.12a: a nontoken artifact doesn't
    @Test
    fun artifactTokensTappedForManaAddOneMore() = scenario {
        alice.battlefield(corvina, lockbox)
        alice.hand(find)
        alice.castsWithoutCosts(find)
        resolveAll()
        alice.activates(lockbox)
        resolveAll()
        expect { alice.pool.size shouldBe 1 }
        alice.activates("Treasure Token", color = Color.Red)
        resolveAll()
        expect { alice.pool.size shouldBe 3 }
    }
}
